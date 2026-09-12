from pathlib import Path
import logging
import json
from typing import Dict, List, Optional, Any
from pydantic import BaseModel
import polars as pl
import duckdb
from skfolio import RiskMeasure
from skfolio.optimization import HierarchicalRiskParity, RiskBudgeting

logger = logging.getLogger("quant.hrp_allocator")

# Canonical v2.3 Portfolio Metadata
CANONICAL_TARGETS: Dict[str, Dict[str, Any]] = {
    "INF109KC12U0": {"name": "ICICI LargeMidcap 250", "bucket": "CORE", "target_pct": 30.0},
    "INF879O01027": {"name": "Parag Parikh Flexi Cap", "bucket": "CORE", "target_pct": 20.0},
    "INF109KC13X2": {"name": "ICICI Value 30", "bucket": "SATELLITE", "target_pct": 10.0},
    "INF754K01TN5": {"name": "Edelweiss Momentum 50", "bucket": "SATELLITE", "target_pct": 10.0},
    "INF204K01K15": {"name": "Nippon Small Cap", "bucket": "SATELLITE", "target_pct": 10.0},
    "INF247L01BM8": {"name": "Motilal Gold & Silver", "bucket": "GOLD_SILVER", "target_pct": 10.0},
    "INF205K01KR8": {"name": "Invesco Arbitrage", "bucket": "LIQUID_BUFFER", "target_pct": 10.0},
}

class AssetAllocation(BaseModel):
    isin: str
    name: str
    bucket: str
    target_pct: float
    hrp_pct: float
    drift_pct: float
    intra_bucket_target_pct: Optional[float] = None
    intra_bucket_hrp_pct: Optional[float] = None

class BucketRollup(BaseModel):
    bucket: str
    target_pct: float
    hrp_pct: float
    drift_pct: float

class HrpAllocationRequest(BaseModel):
    mode: str = "INTRA_BUCKET"  # INTRA_BUCKET, EQUITY_UNIVERSE, FULL_PORTFOLIO
    risk_measure: str = "CVAR"   # CVAR or VARIANCE
    lookback_days: Optional[int] = None
    dump_cache: bool = True

class HrpAllocationResponse(BaseModel):
    status: str  # SUCCESS, INSUFFICIENT_HISTORY, ERROR
    mode: str
    risk_measure: str
    start_date: Optional[str] = None
    end_date: Optional[str] = None
    trading_days: int = 0
    cvar_tail_observations: float = 0.0
    allocations: List[AssetAllocation] = []
    bucket_summary: Dict[str, BucketRollup] = {}
    message: Optional[str] = None

def load_nav_dataframe() -> pl.DataFrame:
    """
    Loads NAV history prioritizing direct DuckDB access, falling back to Parquet export.
    """
    candidate_db_paths = [
        Path("data/tax_ledger.duckdb"),
        Path("../data/tax_ledger.duckdb"),
        Path(__file__).resolve().parent.parent.parent / "data" / "tax_ledger.duckdb",
    ]
    for db_path in candidate_db_paths:
        if db_path.exists():
            try:
                con = duckdb.connect(str(db_path), read_only=True)
                df = con.execute("SELECT asset_id AS isin, nav_date, nav AS nav_value FROM nav_history").pl()
                con.close()
                if len(df) > 0:
                    logger.info(f"Loaded {len(df)} NAV rows directly from DuckDB at {db_path}")
                    return df.with_columns(pl.col("nav_date").str.to_date(strict=False))
            except Exception as e:
                logger.warning(f"Failed to query DuckDB at {db_path}: {e}")

    candidate_parquet_paths = [
        Path("quant-sidecar/data/nav_export.parquet"),
        Path("data/nav_export.parquet"),
        Path("../data/nav_export.parquet"),
        Path(__file__).resolve().parent.parent / "data" / "nav_export.parquet",
    ]
    for p_path in candidate_parquet_paths:
        if p_path.exists():
            try:
                df = pl.read_parquet(p_path)
                if "asset_id" in df.columns:
                    df = df.rename({"asset_id": "isin"})
                if "nav" in df.columns:
                    df = df.rename({"nav": "nav_value"})
                logger.info(f"Loaded {len(df)} NAV rows from Parquet export at {p_path}")
                return df.with_columns(pl.col("nav_date").str.to_date(strict=False))
            except Exception as e:
                logger.warning(f"Failed to read Parquet at {p_path}: {e}")

    raise FileNotFoundError("Could not locate valid NAV history in DuckDB or Parquet candidates.")

def align_returns(
    df: pl.DataFrame,
    target_isins: List[str],
    lookback_days: Optional[int] = None
) -> tuple[pl.DataFrame, str, str, int]:
    """
    Strict shared-calendar alignment:
    Pivots target ISINs and applies drop_nulls() without forward-fill.
    Any non-trading day or date missing for ANY evaluated fund is dropped.
    """
    wide = (
        df.filter(pl.col("isin").is_in(target_isins))
        .sort("nav_date")
        .pivot(index="nav_date", on="isin", values="nav_value")
        .drop_nulls()
    )

    if len(wide) < 2:
        return pl.DataFrame(), "", "", 0

    start_date = str(wide["nav_date"].min())
    end_date = str(wide["nav_date"].max())

    returns_pl = wide.select([pl.col("nav_date")] + [
        (pl.col(isin) / pl.col(isin).shift(1)).log().alias(isin)
        for isin in target_isins
    ]).drop_nulls()

    if lookback_days is not None and lookback_days > 0 and len(returns_pl) > lookback_days:
        returns_pl = returns_pl.tail(lookback_days)
        start_date = str(returns_pl["nav_date"].min())

    return returns_pl, start_date, end_date, len(returns_pl)

def optimize_weights(
    returns_df: Any,
    risk_measure: RiskMeasure
) -> List[float]:
    """
    Unified CVaR-consistent optimization:
    - N >= 3: HierarchicalRiskParity
    - N == 2: RiskBudgeting (Equal Risk Contribution on CVaR)
    - N == 1: [1.0]
    """
    n_assets = len(returns_df.columns)
    if n_assets == 1:
        return [1.0]
    if n_assets == 2:
        model = RiskBudgeting(risk_measure=risk_measure)
        model.fit(returns_df)
        return [float(w) for w in model.weights_]
    
    model = HierarchicalRiskParity(risk_measure=risk_measure)
    model.fit(returns_df)
    return [float(w) for w in model.weights_]

def run_hrp_allocation(
    mode: str = "INTRA_BUCKET",
    risk_measure_str: str = "CVAR",
    lookback_days: Optional[int] = None,
    dump_cache: bool = True
) -> HrpAllocationResponse:
    """
    Main entry point for HRP Allocation.
    """
    try:
        df = load_nav_dataframe()
    except Exception as e:
        logger.error(f"Error loading NAV data: {e}", exc_info=True)
        return HrpAllocationResponse(
            status="ERROR",
            mode=mode,
            risk_measure=risk_measure_str,
            message=f"NAV data loading failure: {str(e)}"
        )

    rm = RiskMeasure.CVAR if risk_measure_str.upper() == "CVAR" else RiskMeasure.VARIANCE

    if mode == "INTRA_BUCKET":
        # Intra-bucket isolation: optimize within each bucket
        buckets = ["CORE", "SATELLITE", "GOLD_SILVER", "LIQUID_BUFFER"]
        allocations: List[AssetAllocation] = []
        bucket_summary: Dict[str, BucketRollup] = {}
        min_start_date = ""
        max_end_date = ""
        min_trading_days = 999999

        for bucket in buckets:
            bucket_isins = [isin for isin, meta in CANONICAL_TARGETS.items() if meta["bucket"] == bucket]
            bucket_target_total = sum(CANONICAL_TARGETS[isin]["target_pct"] for isin in bucket_isins)

            returns_pl, start_d, end_d, t_days = align_returns(df, bucket_isins, lookback_days)
            if t_days < 126 and len(bucket_isins) > 1:
                return HrpAllocationResponse(
                    status="INSUFFICIENT_HISTORY",
                    mode=mode,
                    risk_measure=risk_measure_str,
                    start_date=start_d,
                    end_date=end_d,
                    trading_days=t_days,
                    message=f"Insufficient aligned trading days ({t_days} < 126) for bucket {bucket}."
                )

            if not min_start_date or (start_d and start_d > min_start_date):
                min_start_date = start_d
            if not max_end_date or (end_d and end_d > max_end_date):
                max_end_date = end_d
            min_trading_days = min(min_trading_days, t_days)

            if len(bucket_isins) == 1:
                sub_weights = [1.0]
            else:
                ret_df = returns_pl.drop("nav_date").to_pandas()
                sub_weights = optimize_weights(ret_df, rm)

            bucket_hrp_total = 0.0
            cum_intra_target = 0.0
            cum_intra_hrp = 0.0
            cum_portfolio_hrp = 0.0
            n_bucket = len(bucket_isins)

            for i, isin in enumerate(bucket_isins):
                meta = CANONICAL_TARGETS[isin]
                if i < n_bucket - 1:
                    intra_target = round((meta["target_pct"] / bucket_target_total) * 100.0, 2)
                    intra_hrp = round(sub_weights[i] * 100.0, 2)
                    portfolio_hrp = round((intra_hrp / 100.0) * bucket_target_total, 2)
                    cum_intra_target += intra_target
                    cum_intra_hrp += intra_hrp
                    cum_portfolio_hrp += portfolio_hrp
                else:
                    intra_target = round(100.0 - cum_intra_target, 2)
                    intra_hrp = round(100.0 - cum_intra_hrp, 2)
                    portfolio_hrp = round(bucket_target_total - cum_portfolio_hrp, 2)

                drift = round(portfolio_hrp - meta["target_pct"], 2)
                bucket_hrp_total += portfolio_hrp

                allocations.append(AssetAllocation(
                    isin=isin,
                    name=meta["name"],
                    bucket=bucket,
                    target_pct=meta["target_pct"],
                    hrp_pct=portfolio_hrp,
                    drift_pct=drift,
                    intra_bucket_target_pct=intra_target,
                    intra_bucket_hrp_pct=intra_hrp
                ))

            bucket_summary[bucket] = BucketRollup(
                bucket=bucket,
                target_pct=round(bucket_target_total, 2),
                hrp_pct=round(bucket_hrp_total, 2),
                drift_pct=round(bucket_hrp_total - bucket_target_total, 2)
            )

        resp = HrpAllocationResponse(
            status="SUCCESS",
            mode=mode,
            risk_measure=risk_measure_str.upper(),
            start_date=min_start_date,
            end_date=max_end_date,
            trading_days=min_trading_days,
            cvar_tail_observations=round(min_trading_days * 0.05, 2),
            allocations=allocations,
            bucket_summary=bucket_summary
        )

    elif mode == "EQUITY_UNIVERSE":
        # 5 risk assets (CORE + SATELLITE), targets renormalized onto 80% equity base
        equity_isins = [isin for isin, meta in CANONICAL_TARGETS.items() if meta["bucket"] in ("CORE", "SATELLITE")]
        returns_pl, start_d, end_d, t_days = align_returns(df, equity_isins, lookback_days)

        if t_days < 126:
            return HrpAllocationResponse(
                status="INSUFFICIENT_HISTORY",
                mode=mode,
                risk_measure=risk_measure_str,
                start_date=start_d,
                end_date=end_d,
                trading_days=t_days,
                message=f"Insufficient aligned trading days ({t_days} < 126) for equity universe."
            )

        ret_df = returns_pl.drop("nav_date").to_pandas()
        weights = optimize_weights(ret_df, rm)

        allocations = []
        bucket_hrp_totals: Dict[str, float] = {}
        cum_equity_target = 0.0
        cum_equity_hrp = 0.0
        n_eq = len(equity_isins)

        for i, isin in enumerate(equity_isins):
            meta = CANONICAL_TARGETS[isin]
            # Renormalize 50/30 onto 80% base -> Core=62.5%, Satellite=37.5%
            if i < n_eq - 1:
                equity_target = round((meta["target_pct"] / 80.0) * 100.0, 2)
                hrp_pct = round(weights[i] * 100.0, 2)
                cum_equity_target += equity_target
                cum_equity_hrp += hrp_pct
            else:
                equity_target = round(100.0 - cum_equity_target, 2)
                hrp_pct = round(100.0 - cum_equity_hrp, 2)

            drift = round(hrp_pct - equity_target, 2)
            b = meta["bucket"]
            bucket_hrp_totals[b] = bucket_hrp_totals.get(b, 0.0) + hrp_pct

            allocations.append(AssetAllocation(
                isin=isin,
                name=meta["name"],
                bucket=b,
                target_pct=equity_target,
                hrp_pct=hrp_pct,
                drift_pct=drift
            ))

        bucket_summary = {
            "CORE": BucketRollup(
                bucket="CORE",
                target_pct=62.50,
                hrp_pct=round(bucket_hrp_totals.get("CORE", 0.0), 2),
                drift_pct=round(bucket_hrp_totals.get("CORE", 0.0) - 62.50, 2)
            ),
            "SATELLITE": BucketRollup(
                bucket="SATELLITE",
                target_pct=37.50,
                hrp_pct=round(bucket_hrp_totals.get("SATELLITE", 0.0), 2),
                drift_pct=round(bucket_hrp_totals.get("SATELLITE", 0.0) - 37.50, 2)
            ),
        }

        resp = HrpAllocationResponse(
            status="SUCCESS",
            mode=mode,
            risk_measure=risk_measure_str.upper(),
            start_date=start_d,
            end_date=end_d,
            trading_days=t_days,
            cvar_tail_observations=round(t_days * 0.05, 2),
            allocations=allocations,
            bucket_summary=bucket_summary
        )

    else:
        # FULL_PORTFOLIO across all 7 assets
        all_isins = list(CANONICAL_TARGETS.keys())
        returns_pl, start_d, end_d, t_days = align_returns(df, all_isins, lookback_days)

        if t_days < 126:
            return HrpAllocationResponse(
                status="INSUFFICIENT_HISTORY",
                mode=mode,
                risk_measure=risk_measure_str,
                start_date=start_d,
                end_date=end_d,
                trading_days=t_days,
                message=f"Insufficient aligned trading days ({t_days} < 126) for full portfolio."
            )

        ret_df = returns_pl.drop("nav_date").to_pandas()
        weights = optimize_weights(ret_df, rm)

        allocations = []
        bucket_hrp_totals = {}
        cum_full_hrp = 0.0
        n_all = len(all_isins)

        for i, isin in enumerate(all_isins):
            meta = CANONICAL_TARGETS[isin]
            if i < n_all - 1:
                hrp_pct = round(weights[i] * 100.0, 2)
                cum_full_hrp += hrp_pct
            else:
                hrp_pct = round(100.0 - cum_full_hrp, 2)

            drift = round(hrp_pct - meta["target_pct"], 2)
            b = meta["bucket"]
            bucket_hrp_totals[b] = bucket_hrp_totals.get(b, 0.0) + hrp_pct

            allocations.append(AssetAllocation(
                isin=isin,
                name=meta["name"],
                bucket=b,
                target_pct=meta["target_pct"],
                hrp_pct=hrp_pct,
                drift_pct=drift
            ))

        bucket_summary = {}
        for b in ["CORE", "SATELLITE", "GOLD_SILVER", "LIQUID_BUFFER"]:
            target_b = sum(m["target_pct"] for m in CANONICAL_TARGETS.values() if m["bucket"] == b)
            hrp_b = bucket_hrp_totals.get(b, 0.0)
            bucket_summary[b] = BucketRollup(
                bucket=b,
                target_pct=round(target_b, 2),
                hrp_pct=round(hrp_b, 2),
                drift_pct=round(hrp_b - target_b, 2)
            )

        resp = HrpAllocationResponse(
            status="SUCCESS",
            mode=mode,
            risk_measure=risk_measure_str.upper(),
            start_date=start_d,
            end_date=end_d,
            trading_days=t_days,
            cvar_tail_observations=round(t_days * 0.05, 2),
            allocations=allocations,
            bucket_summary=bucket_summary
        )

    if dump_cache and resp.status == "SUCCESS":
        try:
            cache_candidates = [
                Path("data/hrp_audit.json"),
                Path("../data/hrp_audit.json"),
                Path(__file__).resolve().parent.parent.parent / "data" / "hrp_audit.json",
            ]
            target_path = next((p for p in cache_candidates if p.parent.exists()), Path("data/hrp_audit.json"))
            with open(target_path, "w") as f:
                json.dump(resp.model_dump(), f, indent=2)
            logger.info(f"Dumped HRP audit cache to {target_path}")
        except Exception as e:
            logger.warning(f"Could not dump HRP cache: {e}")

    return resp
