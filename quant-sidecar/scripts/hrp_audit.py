from pathlib import Path
import polars as pl
from skfolio import RiskMeasure
from skfolio.optimization import HierarchicalRiskParity

# 1. Canonical portfolio metadata and targets
V23_TARGETS = {
    "INF109KC12U0": {"name": "ICICI LargeMidcap 250", "bucket": "CORE", "target_pct": 30.0},
    "INF879O01027": {"name": "Parag Parikh Flexi Cap", "bucket": "CORE", "target_pct": 20.0},
    "INF109KC13X2": {"name": "ICICI Value 30", "bucket": "SATELLITE", "target_pct": 10.0},
    "INF754K01TN5": {"name": "Edelweiss Momentum 50", "bucket": "SATELLITE", "target_pct": 10.0},
    "INF204K01K15": {"name": "Nippon Small Cap", "bucket": "SATELLITE", "target_pct": 10.0},
    "INF247L01BM8": {"name": "Motilal Gold & Silver", "bucket": "GOLD_SILVER", "target_pct": 10.0},
    "INF205K01KR8": {"name": "Invesco Arbitrage", "bucket": "LIQUID_BUFFER", "target_pct": 10.0},
}

# 2. Load and pivot Parquet export
parquet_candidates = [
    Path("data/nav_export.parquet"),
    Path("quant-sidecar/data/nav_export.parquet"),
    Path(__file__).resolve().parent.parent / "data" / "nav_export.parquet",
]
parquet_path = next((p for p in parquet_candidates if p.exists()), Path("data/nav_export.parquet"))

if not parquet_path.exists():
    raise FileNotFoundError(
        f"NAV parquet export not found at {parquet_path}. "
        "Run the DuckDB export command first."
    )

df = pl.read_parquet(parquet_path)

# Normalize column names if raw DuckDB table names (asset_id, nav) were exported
if "asset_id" in df.columns and "isin" not in df.columns:
    df = df.rename({"asset_id": "isin"})
if "nav" in df.columns and "nav_value" not in df.columns:
    df = df.rename({"nav": "nav_value"})

# Strict chronological parsing: ensure nav_date is a real Date, avoiding lexicographical string sort traps
df = df.with_columns(pl.col("nav_date").str.to_date(strict=False))

# Filter, pivot and forward-fill weekend/holiday NAV gaps
target_isins = list(V23_TARGETS.keys())
wide_df = (
    df.filter(pl.col("isin").is_in(target_isins))
    .sort("nav_date")
    .pivot(index="nav_date", on="isin", values="nav_value")
    .fill_null(strategy="forward")
    .drop_nulls()
)

# 3. Compute daily log returns
active_isins = [isin for isin in target_isins if isin in wide_df.columns]
returns_pl = (
    wide_df.select([pl.col("nav_date")] + [
        (pl.col(isin) / pl.col(isin).shift(1)).log().alias(isin)
        for isin in active_isins
    ])
    .drop_nulls()
)

returns_df = returns_pl.to_pandas().set_index("nav_date")
n_obs = len(returns_df)
min_date = str(wide_df["nav_date"].min())
max_date = str(wide_df["nav_date"].max())

# 4. Diagnostic Sample Integrity Summary
print("=" * 72)
print("SAMPLE SIZE & ALIGNMENT AUDIT")
print("=" * 72)
print(f"Alignment Window:             {min_date} to {max_date}")
print(f"Calendar Days in Window:      {(wide_df['nav_date'].max() - wide_df['nav_date'].min()).days + 1}")
print(f"Aligned NAV Days (survived):  {len(wide_df)}")
print(f"Daily Return Observations:    {n_obs}")

# Calculate non-zero return days (filtering out holiday / forward-fill flat days)
non_zero_days = (returns_df.abs().sum(axis=1) > 1e-6).sum()
print(f"Non-Zero Trading Days:        {non_zero_days}")
cvar_tail_count = n_obs * 0.05
print(f"Expected 95% CVaR Tail Obs:   {cvar_tail_count:.2f} observations (N * 5%)")

if n_obs < 252 or non_zero_days < 20:
    print("\n" + "!" * 72)
    print("WARNING: EXTREMELY THIN SAMPLE SIZE!")
    print(f"  Only {n_obs} daily return observations ({non_zero_days} active trading days) survived.")
    print("  Estimating 95% CVaR on fewer than ~250 trading days produces highly")
    print("  unstable covariance estimates and arbitrary tail measures.")
    print("  Treat HRP allocations as PROOF-OF-CONCEPT ONLY, not decision-grade.")
    print("!" * 72)

# 5. Fit Hierarchical Risk Parity with CVaR
# Note on skfolio v1.0.x: HierarchicalRiskParity uses cvar_beta=0.95 from Portfolio's
# default parameter. Passing portfolio_params={'cvar_beta': ...} is ignored by
# skfolio internal _risk() calculations during recursive bisection.
model = HierarchicalRiskParity(
    risk_measure=RiskMeasure.CVAR,
)
model.fit(returns_df)
weights = model.weights_

# 6. Emit Markdown audit report
print("\n### HRP (CVaR) vs v2.3 Manual Allocation Audit\n")
print("| ISIN | Asset Name | Bucket | Target % | HRP % | Drift (Δ) |")
print("| :--- | :--- | :--- | :---: | :---: | :---: |")

bucket_hrp_totals = {}
for i, isin in enumerate(returns_df.columns):
    target = V23_TARGETS[isin]
    hrp_pct = round(float(weights[i]) * 100.0, 2)
    drift = round(hrp_pct - target["target_pct"], 2)
    bucket = target["bucket"]
    bucket_hrp_totals[bucket] = bucket_hrp_totals.get(bucket, 0.0) + hrp_pct
    print(f"| `{isin}` | {target['name']} | {bucket} | {target['target_pct']:.1f}% | {hrp_pct:.1f}% | {drift:+.2f}% |")

print("\n**Bucket-Level Aggregate Comparison:**")
bucket_summary = {}
for bucket, hrp_total in bucket_hrp_totals.items():
    v23_total = sum(t["target_pct"] for t in V23_TARGETS.values() if t["bucket"] == bucket)
    diff = round(hrp_total - v23_total, 2)
    bucket_summary[bucket] = {
        "target_pct": round(v23_total, 2),
        "hrp_pct": round(hrp_total, 2),
        "drift_pct": diff,
    }
    print(f"- **{bucket}**: Target {v23_total:.1f}% vs HRP {hrp_total:.1f}% (Δ {diff:+.2f}%)")

# 7. Dump lightweight JSON cache for TUI / UI read-if-present display
import json

repo_root = Path(__file__).resolve().parent.parent.parent
json_path = repo_root / "data" / "hrp_audit.json"
json_path.parent.mkdir(parents=True, exist_ok=True)

audit_payload = {
    "audit_as_of": max_date,
    "alignment_window": {
        "start": min_date,
        "end": max_date,
    },
    "trading_days": n_obs,
    "cvar_tail_observations": round(cvar_tail_count, 2),
    "allocations": [
        {
            "isin": isin,
            "name": V23_TARGETS[isin]["name"],
            "bucket": V23_TARGETS[isin]["bucket"],
            "target_pct": V23_TARGETS[isin]["target_pct"],
            "hrp_pct": round(float(weights[i]) * 100.0, 2),
            "drift_pct": round(round(float(weights[i]) * 100.0, 2) - V23_TARGETS[isin]["target_pct"], 2),
        }
        for i, isin in enumerate(returns_df.columns)
    ],
    "bucket_summary": bucket_summary,
}

with open(json_path, "w") as f:
    json.dump(audit_payload, f, indent=2)

print(f"\n[+] Dumped offline audit cache to {json_path}")
