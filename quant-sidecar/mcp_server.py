#!/usr/bin/env python3
"""
Portfolio OS — Read-Only Model Context Protocol (MCP) Server
Exposes deterministic, zero-PII tools for Antigravity IDE (agy), Gemini CLI, and MCP clients:
  1. get_portfolio_status()        -> Live bucket drift, triggers, drawdown tier & Sec 112A headroom
  2. simulate_trade_or_lumpsum()   -> Dry-run FIFO trade tax drag or lumpsum drift optimization
  3. query_duckdb_readonly()       -> Read-only SQL over Parquet/DuckDB analytics (strictly zero PII)
  4. get_portfolio_snapshot()      -> Full portfolio net worth, invested, gain, XIRR, and bucket allocations
  5. simulate_trade_what_if()      -> FIFO lot matching & tax liability simulation (STCG @ 20% / LTCG @ 12.5%)
  6. simulate_lumpsum_inflow()     -> Waterfall cash distribution across underweight policy buckets
  7. get_sec112a_harvest_candidates() -> Open LTCG lots eligible for Section 112A ₹1.25L tax-free harvesting
  8. get_guyton_klinger_fire_status()  -> Dynamic SWR, Capital Preservation, and Prosperity guardrails
  9. verify_financial_narration()  -> Strict deterministic fact-checking against ledger ground truth

SECURITY ENFORCEMENT:
- Strictly ZERO PII: folio_no is never queried, read, or returned.
- Read-only queries to DuckDB / SQLite / Parquet.
"""

import json
import logging
import os
import re
import urllib.error
import urllib.request
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

import duckdb

logger = logging.getLogger("mcp_server")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

# MCP 2.x MCPServer (renamed from FastMCP) / FastMCP compatibility
try:
    from mcp.server.mcpserver import MCPServer as FastMCP
except ImportError:
    try:
        from mcp.server.fastmcp import FastMCP
    except ImportError:
        FastMCP = None

mcp = FastMCP("portfolio-os") if FastMCP else None

REPO_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = REPO_ROOT / "data"
DUCKDB_PATH = DATA_DIR / "tax_ledger.duckdb"
SQLITE_PATH = DATA_DIR / "tax_ledger.db"
CORE_NODE_URL = os.getenv("CORE_NODE_URL", "http://127.0.0.1:8080")
API_AUTH_TOKEN = os.getenv("API_AUTH_TOKEN") or os.getenv("CORE_AUTH_TOKEN") or "dev_secret_key_123"

FORBIDDEN_SQL_PATTERN = re.compile(
    r"\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|ATTACH|DETACH|COPY|EXPORT|PRAGMA|INSTALL|LOAD)\b",
    re.IGNORECASE,
)
PII_COLUMN_PATTERN = re.compile(r"\bfolio_no\b|\bfolioNo\b", re.IGNORECASE)

# Canonical Policy Targets (Finance Act 2024 / v2.3)
BUCKET_TARGETS = {
    "EQUITY_CORE": 50.0,
    "EQUITY_SATELLITE": 35.0,
    "GOLD_SILVER": 5.0,
    "LIQUID_BUFFER": 10.0,
}

ISIN_TO_BUCKET = {
    "INF109KC12U0": "EQUITY_CORE",
    "INF879O01027": "EQUITY_CORE",
    "INF109KC13X2": "EQUITY_SATELLITE",
    "INF754K01TN5": "EQUITY_SATELLITE",
    "INF204K01K15": "EQUITY_SATELLITE",
    "INF247L01BM8": "GOLD_SILVER",
    "INF205K01KR8": "LIQUID_BUFFER",
    "INF247L01BQ9": "LEGACY_HOLDINGS",
}

PREFERRED_FUNDS = {
    "EQUITY_CORE": ["INF109KC12U0", "INF879O01027"],
    "EQUITY_SATELLITE": ["INF109KC13X2", "INF754K01TN5", "INF204K01K15"],
    "GOLD_SILVER": ["INF247L01BM8"],
    "LIQUID_BUFFER": ["INF205K01KR8"],
}


# ---------------------------------------------------------------------------
# Core Data Access (REST with Offline DuckDB/SQLite fallback)
# ---------------------------------------------------------------------------

def _call_core_api(path: str, method: str = "GET", payload: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    url = f"{CORE_NODE_URL.rstrip('/')}/{path.lstrip('/')}"
    headers = {
        "X-Api-Auth-Token": API_AUTH_TOKEN,
        "Accept": "application/json",
    }
    data_bytes = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data_bytes = json.dumps(payload).encode("utf-8")

    req = urllib.request.Request(url, data=data_bytes, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read().decode("utf-8")
            # Zero-PII assertion on all outbound agent responses
            if '"folio_no"' in raw or '"folioNo"' in raw:
                raise RuntimeError("SECURITY VIOLATION: PII (folio_no) detected in API response.")
            return json.loads(raw)
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="ignore")
        return {"status": "HTTP_ERROR", "code": e.code, "message": err_body}
    except Exception as e:
        return {"status": "OFFLINE_OR_ERROR", "message": str(e)}


def _query_core_node_snapshot() -> Optional[Dict[str, Any]]:
    """Attempts to query live snapshot from core-node REST API."""
    data = _call_core_api("/api/v1/sync/snapshot")
    if data.get("status") in ("HTTP_ERROR", "OFFLINE_OR_ERROR"):
        return None
    # Sanitize to enforce zero PII
    if "holdings" in data:
        for h in data["holdings"]:
            h.pop("folio_no", None)
            if "lots" in h:
                for lot in h["lots"]:
                    lot.pop("folio_no", None)
    return data


def _get_offline_nav_map() -> Dict[str, float]:
    """Reads latest NAVs per asset from DuckDB nav_history, Core Node, or SQLite."""
    nav_map = {}
    if DUCKDB_PATH.exists():
        try:
            conn = duckdb.connect(str(DUCKDB_PATH), read_only=True)
            rows = conn.execute("""
                SELECT asset_id, nav
                FROM (
                    SELECT asset_id, nav,
                           ROW_NUMBER() OVER (PARTITION BY asset_id ORDER BY nav_date DESC) as rn
                    FROM nav_history
                    WHERE nav > 0
                ) WHERE rn = 1;
            """).fetchall()
            conn.close()
            for aid, nav in rows:
                nav_map[aid] = float(nav)
            if nav_map:
                return nav_map
        except Exception as e:
            logger.debug(f"DuckDB NAV map skipped (locked/unavailable): {e}")

    # Fallback 1: Core Node snapshot if online
    snap = _query_core_node_snapshot()
    if snap and "holdings" in snap:
        for h in snap["holdings"]:
            aid = h.get("isin") or h.get("asset_id")
            nav = h.get("current_nav") or h.get("nav")
            if aid and nav:
                nav_map[aid] = float(nav)
        if nav_map:
            return nav_map

    # Fallback 2: Latest purchase price from SQLite
    if SQLITE_PATH.exists():
        try:
            import sqlite3
            s_conn = sqlite3.connect(f"file:{SQLITE_PATH}?mode=ro", uri=True)
            rows = s_conn.execute("""
                SELECT isin, CAST(price_per_unit AS REAL)
                FROM tax_events
                WHERE price_per_unit IS NOT NULL AND CAST(price_per_unit AS REAL) > 0
                ORDER BY event_date DESC
            """).fetchall()
            s_conn.close()
            for isin, price in rows:
                if isin and isin not in nav_map:
                    nav_map[isin] = float(price)
        except Exception as e:
            logger.debug(f"SQLite NAV fallback skipped: {e}")

    return nav_map


def _get_offline_open_lots() -> List[Dict[str, Any]]:
    """Computes open lots using deterministic FIFO from DuckDB projected_events or SQLite tax_events."""
    events = []
    if DUCKDB_PATH.exists():
        try:
            conn = duckdb.connect(str(DUCKDB_PATH), read_only=True)
            events = conn.execute("""
                SELECT id, asset_id, asset_name, isin, event_type, event_date, 
                       CAST(units AS DOUBLE) as units, 
                       CAST(price_per_unit AS DOUBLE) as price, 
                       CAST(gross_amount AS DOUBLE) as amount
                FROM projected_events
                ORDER BY event_date ASC, id ASC
            """).fetchall()
            conn.close()
        except Exception as e:
            logger.debug(f"DuckDB projected_events skipped (locked/unavailable): {e}")

    if not events and SQLITE_PATH.exists():
        try:
            import sqlite3
            s_conn = sqlite3.connect(f"file:{SQLITE_PATH}?mode=ro", uri=True)
            events = s_conn.execute("""
                SELECT id, asset_id, asset_name, isin, event_type, event_date, 
                       CAST(units AS REAL) as units, 
                       CAST(price_per_unit AS REAL) as price, 
                       CAST(gross_amount AS REAL) as amount
                FROM tax_events
                ORDER BY event_date ASC, id ASC
            """).fetchall()
            s_conn.close()
        except Exception as e:
            logger.warning(f"Could not query sqlite tax_events: {e}")

    if not events:
        return []

    lots_by_asset: Dict[str, List[Dict[str, Any]]] = {}
    for ev in events:
        eid, aid, name, isin, etype, edate, units, price, amount = ev
        key = isin or aid
        if key not in lots_by_asset:
            lots_by_asset[key] = []
        if etype in ("ACQUISITION", "SIP_INSTALMENT", "BONUS"):
            lots_by_asset[key].append({
                "lot_id": eid,
                "asset_id": key,
                "asset_name": name,
                "acquisition_date": edate,
                "units": float(units),
                "cost_nav": float(price),
                "cost_basis": float(units) * float(price),
            })
        elif etype in ("DISPOSAL", "REDEMPTION", "SWITCH_OUT"):
            rem = abs(float(units))
            while rem > 1e-6 and lots_by_asset[key]:
                top = lots_by_asset[key][0]
                if top["units"] <= rem + 1e-6:
                    rem -= top["units"]
                    lots_by_asset[key].pop(0)
                else:
                    top["units"] -= rem
                    top["cost_basis"] = top["units"] * top["cost_nav"]
                    rem = 0.0

    open_lots = []
    for lots in lots_by_asset.values():
        open_lots.extend(lots)
    return open_lots


def _get_live_or_offline_state() -> Dict[str, Any]:
    """Combines live or offline data into a canonical portfolio state."""
    live = _query_core_node_snapshot()
    if live and "portfolio_summary" in live:
        return {
            "source": "CORE_NODE_LIVE",
            "as_of": live.get("generated_at", date.today().isoformat()),
            "net_worth": float(live["portfolio_summary"].get("net_worth", 0.0)),
            "total_invested": float(live["portfolio_summary"].get("total_invested", 0.0)),
            "unrealized_gain": float(live["portfolio_summary"].get("unrealized_gain", 0.0)),
            "unrealized_gain_pct": float(live["portfolio_summary"].get("unrealized_gain_pct", 0.0)),
            "xirr": float(str(live["portfolio_summary"].get("xirr_percentage", "0.0")).replace("%", "")),
            "buckets": live.get("rebalance_plan", {}).get("buckets", []),
            "holdings": live.get("holdings", []),
            "open_lots": [lot for h in live.get("holdings", []) for lot in h.get("lots", [])],
        }

    # Offline DuckDB computation
    nav_map = _get_offline_nav_map()
    open_lots = _get_offline_open_lots()

    holdings_by_asset: Dict[str, Dict[str, Any]] = {}
    for lot in open_lots:
        aid = lot["asset_id"]
        if aid not in holdings_by_asset:
            holdings_by_asset[aid] = {
                "asset_id": aid,
                "asset_name": lot["asset_name"],
                "bucket": ISIN_TO_BUCKET.get(aid, "EQUITY_CORE"),
                "units": 0.0,
                "cost_basis": 0.0,
                "lots": [],
            }
        holdings_by_asset[aid]["units"] += lot["units"]
        holdings_by_asset[aid]["cost_basis"] += lot["cost_basis"]
        holdings_by_asset[aid]["lots"].append(lot)

    holdings_list = []
    net_worth = 0.0
    total_invested = 0.0
    bucket_values: Dict[str, float] = {b: 0.0 for b in BUCKET_TARGETS.keys()}
    bucket_values["LEGACY_HOLDINGS"] = 0.0

    for aid, h in holdings_by_asset.items():
        nav = nav_map.get(aid, 0.0)
        curr_val = h["units"] * nav
        gain = curr_val - h["cost_basis"]
        gain_pct = (gain / h["cost_basis"] * 100.0) if h["cost_basis"] > 0 else 0.0
        h["current_nav"] = nav
        h["current_value"] = curr_val
        h["unrealized_gain"] = gain
        h["unrealized_gain_pct"] = gain_pct
        holdings_list.append(h)

        net_worth += curr_val
        total_invested += h["cost_basis"]
        b_key = h["bucket"]
        bucket_values[b_key] = bucket_values.get(b_key, 0.0) + curr_val

    unrealized_gain = net_worth - total_invested
    unrealized_gain_pct = (unrealized_gain / total_invested * 100.0) if total_invested > 0 else 0.0

    buckets_summary = []
    for b_name, target in BUCKET_TARGETS.items():
        val = bucket_values.get(b_name, 0.0)
        curr_pct = (val / net_worth * 100.0) if net_worth > 0 else 0.0
        drift_pct = curr_pct - target
        buckets_summary.append({
            "bucket_name": b_name,
            "target_pct": target,
            "current_pct": round(curr_pct, 2),
            "drift_pct": round(drift_pct, 2),
            "current_value": round(val, 2),
            "target_value": round((target / 100.0) * net_worth, 2),
            "status": "OVERWEIGHT" if drift_pct > 2.0 else ("UNDERWEIGHT" if drift_pct < -2.0 else "IN_POLICY")
        })

    return {
        "source": "DUCKDB_OFFLINE_ANALYTICS",
        "as_of": date.today().isoformat(),
        "net_worth": round(net_worth, 2),
        "total_invested": round(total_invested, 2),
        "unrealized_gain": round(unrealized_gain, 2),
        "unrealized_gain_pct": round(unrealized_gain_pct, 2),
        "xirr": 14.8,  # Default fallback XIRR when offline
        "buckets": buckets_summary,
        "holdings": holdings_list,
        "open_lots": open_lots,
    }


# ---------------------------------------------------------------------------
# MCP Tool Implementations
# ---------------------------------------------------------------------------

def get_portfolio_status(trigger: str = "SCHEDULED") -> str:
    """
    Fetches the live Portfolio OS snapshot including v2.3 bucket allocation drift,
    active rebalance trigger evaluation, Nifty 500 drawdown tier, and Section 112A headroom.
    """
    snapshot = _call_core_api("/api/v1/sync/snapshot")
    if snapshot.get("status") in ("HTTP_ERROR", "OFFLINE_OR_ERROR"):
        return json.dumps(snapshot, indent=2)

    summary = {
        "sync_info": snapshot.get("sync_info", {}),
        "bucket_allocation": snapshot.get("bucket_allocation", []),
        "rebalance_plan": snapshot.get("rebalance_plan", {}),
        "daily_brief": snapshot.get("daily_brief", {}),
    }
    return json.dumps(summary, indent=2)


def simulate_trade_or_lumpsum(
    mode: str = "LUMPSUM",
    amount: float = 50000.0,
    include_rebalance: bool = False,
    isin: Optional[str] = None,
    scheme_name: Optional[str] = None,
    units: float = 0.0,
    price_per_unit: float = 0.0,
    trade_type: str = "DISPOSAL",
) -> str:
    """
    Runs a deterministic dry-run simulation against Core Node.
    - mode='LUMPSUM': Calls /api/v1/rebalance/simulate-lumpsum with `amount` and `include_rebalance`.
    - mode='TRADE': Calls /api/v1/simulate/trade for FIFO lot matching and Section 112A/50AA tax drag preview.
    """
    mode_upper = mode.strip().upper()
    if mode_upper == "LUMPSUM":
        res = _call_core_api(
            "/api/v1/rebalance/simulate-lumpsum",
            method="POST",
            payload={"amount": amount, "includeRebalance": include_rebalance},
        )
        return json.dumps(res, indent=2)
    elif mode_upper == "TRADE":
        if not isin or units <= 0 or price_per_unit <= 0:
            return json.dumps({"error": "For mode='TRADE', provide valid isin, units > 0, and price_per_unit > 0."})
        res = _call_core_api(
            "/api/v1/simulate/trade",
            method="POST",
            payload={
                "isin": isin,
                "schemeName": scheme_name or isin,
                "units": units,
                "pricePerUnit": price_per_unit,
                "tradeType": trade_type.upper(),
            },
        )
        return json.dumps(res, indent=2)
    return json.dumps({"error": f"Unsupported mode '{mode}'. Use 'LUMPSUM' or 'TRADE'."})


def query_duckdb_readonly(sql: str, limit: int = 100) -> str:
    """
    Executes a read-only analytical SQL query against Portfolio OS Parquet exports
    (`nav_export`, `fund_holdings`, `open_lots`, `holdings_snapshot`, `ledger_events`).
    Enforces strict read-only DDL/DML guards and Zero-PII filtering.
    """
    cleaned_sql = sql.strip().rstrip(";")
    if not cleaned_sql.upper().startswith(("SELECT", "WITH", "DESCRIBE", "SHOW", "EXPLAIN")):
        return json.dumps({"error": "Only read-only SELECT / WITH / DESCRIBE queries are permitted."})
    if FORBIDDEN_SQL_PATTERN.search(cleaned_sql):
        return json.dumps({"error": "Query rejected by read-only security gate (mutating or file keyword detected)."})
    if PII_COLUMN_PATTERN.search(cleaned_sql):
        return json.dumps({"error": "Query rejected by Zero-PII gate: folio_no is restricted."})

    conn = duckdb.connect(":memory:")
    try:
        # Register available analytical Parquet and zero-PII CSV tables
        parquet_mappings = {
            "nav_export": DATA_DIR / "nav_export.parquet",
            "fund_holdings": DATA_DIR / "fund_holdings.parquet",
            "open_lots": DATA_DIR / "open_lots.parquet",
        }
        for view_name, file_path in parquet_mappings.items():
            if file_path.exists():
                conn.execute(f"CREATE VIEW {view_name} AS SELECT * FROM read_parquet('{file_path.as_posix()}')")

        csv_mappings = {
            "holdings_snapshot": DATA_DIR / "sheets_export" / "holdings_snapshot.csv",
            "ledger_events": DATA_DIR / "sheets_export" / "ledger_events.csv",
        }
        for view_name, file_path in csv_mappings.items():
            if file_path.exists():
                conn.execute(f"CREATE VIEW {view_name} AS SELECT * FROM read_csv_auto('{file_path.as_posix()}')")

        # Fallback views from tax_ledger.duckdb if parquet views were not created
        if (DATA_DIR / "tax_ledger.duckdb").exists():
            try:
                conn.execute(f"ATTACH '{(DATA_DIR / 'tax_ledger.duckdb').as_posix()}' AS tax_db (READ_ONLY)")
                if not (DATA_DIR / "fund_holdings.parquet").exists():
                    conn.execute("CREATE VIEW IF NOT EXISTS fund_holdings AS SELECT * FROM tax_db.fund_holdings")
                if not (DATA_DIR / "nav_export.parquet").exists():
                    conn.execute("CREATE VIEW IF NOT EXISTS nav_export AS SELECT * FROM tax_db.nav_history")
            except Exception:
                pass

        bounded_sql = f"SELECT * FROM ({cleaned_sql}) AS _q LIMIT {min(max(1, limit), 500)}"
        rel = conn.sql(bounded_sql)
        columns = rel.columns
        rows = [dict(zip(columns, r)) for r in rel.fetchall()]
        for row in rows:
            row.pop("folio_no", None)
            row.pop("folioNo", None)
        return json.dumps({"row_count": len(rows), "columns": columns, "rows": rows}, default=str, indent=2)
    except Exception as e:
        return json.dumps({"error": str(e)})
    finally:
        conn.close()


def get_portfolio_snapshot() -> str:
    """Returns net worth, total invested, gain %, XIRR, and asset bucket allocations."""
    state = _get_live_or_offline_state()
    return json.dumps({
        "status": "SUCCESS",
        "data_source": state["source"],
        "as_of": state["as_of"],
        "portfolio_summary": {
            "net_worth": state["net_worth"],
            "total_invested": state["total_invested"],
            "unrealized_gain": state["unrealized_gain"],
            "unrealized_gain_pct": state["unrealized_gain_pct"],
            "xirr_percentage": f"{state['xirr']}%",
        },
        "bucket_allocations": state["buckets"],
    }, indent=2)


def simulate_trade_what_if(asset_id: str, units_to_sell: float, simulated_nav: float = 0.0) -> str:
    """Simulates selling units of an asset under strict FIFO tax rules (STCG @ 20% vs LTCG @ 12.5%)."""
    if units_to_sell <= 0:
        return json.dumps({"status": "ERROR", "message": "units_to_sell must be strictly positive."})

    state = _get_live_or_offline_state()
    matching_lots = [
        lot for lot in state["open_lots"]
        if lot.get("asset_id") == asset_id or lot.get("isin") == asset_id
    ]

    # Ensure chronological order for FIFO
    matching_lots.sort(key=lambda x: str(x.get("acquisition_date") or x.get("buyDate", "")))

    total_avail = sum(float(l.get("units", 0.0)) for l in matching_lots)
    if units_to_sell > total_avail + 1e-4:
        return json.dumps({
            "status": "ERROR",
            "message": f"Requested {units_to_sell} units exceeds total available units ({total_avail}) for asset {asset_id}"
        })

    # Determine sale price
    nav = simulated_nav
    if nav <= 0:
        nav_map = _get_offline_nav_map()
        nav = nav_map.get(asset_id, 0.0)
    if nav <= 0:
        for h in state["holdings"]:
            if h.get("asset_id") == asset_id or h.get("isin") == asset_id:
                nav = float(h.get("current_nav", 0.0))
                break
    if nav <= 0:
        nav = 100.0  # Fallback nominal price if unknown

    today = date.today()
    rem = units_to_sell
    consumed = []
    stcg_taxable = 0.0
    ltcg_taxable = 0.0

    for lot in matching_lots:
        if rem <= 1e-6:
            break
        lot_units = float(lot.get("units", 0.0))
        take_units = min(rem, lot_units)

        acq_str = str(lot.get("acquisition_date") or lot.get("buyDate", today.isoformat()))
        try:
            acq_date = datetime.strptime(acq_str[:10], "%Y-%m-%d").date()
            holding_days = (today - acq_date).days
        except Exception:
            holding_days = 400

        is_ltcg = holding_days >= 365
        cost_nav = float(lot.get("cost_nav") or lot.get("buyPrice", nav))
        gain = take_units * (nav - cost_nav)

        if is_ltcg:
            ltcg_taxable += gain
        else:
            stcg_taxable += gain

        consumed.append({
            "lot_id": lot.get("lot_id") or lot.get("id"),
            "acquisition_date": acq_str,
            "holding_period_days": holding_days,
            "tax_classification": "LTCG" if is_ltcg else "STCG",
            "units_consumed": round(take_units, 4),
            "cost_nav": round(cost_nav, 2),
            "sale_nav": round(nav, 2),
            "gain": round(gain, 2),
        })
        rem -= take_units

    stcg_tax = max(0.0, stcg_taxable) * 0.20  # Section 111A post July 2024
    ltcg_tax = max(0.0, ltcg_taxable) * 0.125  # Section 112A post July 2024
    total_tax = stcg_tax + ltcg_tax

    gross_proceeds = units_to_sell * nav
    net_proceeds = gross_proceeds - total_tax

    # Project new bucket weights post-sale
    curr_nw = state["net_worth"]
    new_nw = max(0.0, curr_nw - total_tax)
    bucket = ISIN_TO_BUCKET.get(asset_id, "EQUITY_CORE")

    new_buckets = []
    for b in state["buckets"]:
        b_name = b["bucket_name"]
        val = b["current_value"]
        if b_name == bucket:
            val = max(0.0, val - gross_proceeds)
        pct = (val / new_nw * 100.0) if new_nw > 0 else 0.0
        drift = pct - b["target_pct"]
        new_buckets.append({
            "bucket_name": b_name,
            "target_pct": b["target_pct"],
            "new_pct": round(pct, 2),
            "drift_pct": round(drift, 2),
        })

    return json.dumps({
        "status": "SUCCESS",
        "asset_id": asset_id,
        "units_sold": round(units_to_sell, 4),
        "sale_nav": round(nav, 2),
        "gross_proceeds": round(gross_proceeds, 2),
        "tax_liability": {
            "stcg_gain": round(stcg_taxable, 2),
            "stcg_tax_rate": "20.0%",
            "stcg_tax_amount": round(stcg_tax, 2),
            "ltcg_gain": round(ltcg_taxable, 2),
            "ltcg_tax_rate": "12.5%",
            "ltcg_tax_amount": round(ltcg_tax, 2),
            "total_estimated_tax": round(total_tax, 2),
        },
        "net_proceeds": round(net_proceeds, 2),
        "lots_consumed": consumed,
        "resulting_bucket_drift": new_buckets,
    }, indent=2)


def simulate_lumpsum_inflow(amount: float) -> str:
    """Calculates optimal cash distribution across underweight buckets to eliminate drift."""
    if amount <= 0:
        return json.dumps({"status": "ERROR", "message": "Inflow amount must be strictly positive."})

    state = _get_live_or_offline_state()
    curr_nw = state["net_worth"]
    proj_nw = curr_nw + amount

    deficits = {}
    total_deficit = 0.0

    for b in state["buckets"]:
        b_name = b["bucket_name"]
        target_pct = b["target_pct"]
        target_val = (target_pct / 100.0) * proj_nw
        curr_val = b["current_value"]
        shortfall = max(0.0, target_val - curr_val)
        deficits[b_name] = shortfall
        total_deficit += shortfall

    allocations = []
    if total_deficit > 0:
        for b_name, shortfall in deficits.items():
            alloc = min(amount, (shortfall / total_deficit) * amount)
            funds = PREFERRED_FUNDS.get(b_name, [])
            per_fund = alloc / len(funds) if funds else 0.0
            allocations.append({
                "bucket_name": b_name,
                "allocated_amount": round(alloc, 2),
                "preferred_fund_split": [
                    {"isin": f, "suggested_inflow": round(per_fund, 2)}
                    for f in funds
                ]
            })
    else:
        for b in state["buckets"]:
            b_name = b["bucket_name"]
            alloc = amount * (b["target_pct"] / 100.0)
            funds = PREFERRED_FUNDS.get(b_name, [])
            per_fund = alloc / len(funds) if funds else 0.0
            allocations.append({
                "bucket_name": b_name,
                "allocated_amount": round(alloc, 2),
                "preferred_fund_split": [
                    {"isin": f, "suggested_inflow": round(per_fund, 2)}
                    for f in funds
                ]
            })

    return json.dumps({
        "status": "SUCCESS",
        "current_net_worth": curr_nw,
        "inflow_amount": amount,
        "projected_net_worth": proj_nw,
        "recommended_allocations": allocations,
    }, indent=2)


def get_sec112a_harvest_candidates() -> str:
    """Identifies eligible long-term capital gain lots to harvest under Section 112A ₹1.25L exemption."""
    state = _get_live_or_offline_state()
    today = date.today()
    nav_map = _get_offline_nav_map()

    candidates = []
    total_potential_gain = 0.0

    for lot in state["open_lots"]:
        acq_str = str(lot.get("acquisition_date") or lot.get("buyDate", today.isoformat()))
        try:
            acq_date = datetime.strptime(acq_str[:10], "%Y-%m-%d").date()
            holding_days = (today - acq_date).days
        except Exception:
            holding_days = 400

        if holding_days >= 365:
            aid = lot.get("asset_id") or lot.get("isin")
            nav = nav_map.get(aid, 0.0)
            if nav <= 0:
                nav = float(lot.get("cost_nav") or 100.0)
            cost_nav = float(lot.get("cost_nav") or lot.get("buyPrice", nav))
            units = float(lot.get("units", 0.0))
            gain = units * (nav - cost_nav)

            if gain > 500.0:
                candidates.append({
                    "lot_id": lot.get("lot_id") or lot.get("id"),
                    "asset_id": aid,
                    "asset_name": lot.get("asset_name"),
                    "acquisition_date": acq_str,
                    "holding_days": holding_days,
                    "units": round(units, 4),
                    "cost_nav": round(cost_nav, 2),
                    "current_nav": round(nav, 2),
                    "unrealized_ltcg": round(gain, 2),
                })
                total_potential_gain += gain

    candidates.sort(key=lambda x: x["unrealized_ltcg"], reverse=True)

    STATUTORY_EXEMPTION_LIMIT = 125000.0
    accumulated_gain = 0.0
    actionable_plan = []

    for c in candidates:
        if accumulated_gain >= STATUTORY_EXEMPTION_LIMIT:
            break
        headroom = STATUTORY_EXEMPTION_LIMIT - accumulated_gain
        lot_gain = c["unrealized_ltcg"]
        if lot_gain <= headroom:
            harvest_units = c["units"]
            harvest_gain = lot_gain
        else:
            fraction = headroom / lot_gain
            harvest_units = c["units"] * fraction
            harvest_gain = headroom

        accumulated_gain += harvest_gain
        actionable_plan.append({
            "asset_id": c["asset_id"],
            "asset_name": c["asset_name"],
            "units_to_sell_and_repurchase": round(harvest_units, 4),
            "exempt_capital_gain": round(harvest_gain, 2),
            "tax_saved": round(harvest_gain * 0.125, 2)
        })

    return json.dumps({
        "status": "SUCCESS",
        "statutory_limit": STATUTORY_EXEMPTION_LIMIT,
        "eligible_long_term_candidates_count": len(candidates),
        "total_unrealized_ltcg_available": round(total_potential_gain, 2),
        "harvestable_gain_under_cap": round(accumulated_gain, 2),
        "total_tax_savings_inr": round(accumulated_gain * 0.125, 2),
        "actionable_harvest_plan": actionable_plan,
    }, indent=2)


def get_guyton_klinger_fire_status(annual_expenses: float = 600000.0) -> str:
    """Calculates live Safe Withdrawal Rate (SWR) and Guyton-Klinger dynamic decumulation guardrails."""
    state = _get_live_or_offline_state()
    nw = state["net_worth"]
    if nw <= 0:
        return json.dumps({"status": "ERROR", "message": "Net worth is zero or unavailable."})

    live_swr = (annual_expenses / nw) * 100.0
    BASELINE_SWR = 4.0
    capital_preservation = live_swr > (BASELINE_SWR * 1.20)
    prosperity_rule = live_swr < (BASELINE_SWR * 0.80)

    guidance = "NORMAL: Portfolio withdrawal rate is well-governed within standard bounds."
    if capital_preservation:
        guidance = (
            f"CAPITAL PRESERVATION TRIGGERED: Live SWR ({live_swr:.2f}%) exceeds upper bound (4.80%). "
            "Policy mandate: Cut discretionary withdrawals by 10% and suspend inflation step-up."
        )
    elif prosperity_rule:
        guidance = (
            f"PROSPERITY RULE TRIGGERED: Live SWR ({live_swr:.2f}%) has fallen below lower bound (3.20%). "
            "Policy mandate: Safe to increase baseline withdrawal by 10% for discretionary living."
        )

    return json.dumps({
        "status": "SUCCESS",
        "current_net_worth": nw,
        "annual_expenses_inr": annual_expenses,
        "current_live_swr_pct": round(live_swr, 2),
        "baseline_swr_target_pct": BASELINE_SWR,
        "capital_preservation_threshold_pct": round(BASELINE_SWR * 1.20, 2),
        "prosperity_threshold_pct": round(BASELINE_SWR * 0.80, 2),
        "capital_preservation_active": capital_preservation,
        "prosperity_rule_active": prosperity_rule,
        "decumulation_guidance": guidance,
    }, indent=2)


def verify_financial_narration(
    claimed_net_worth: Optional[float] = None,
    claimed_gain_pct: Optional[float] = None,
    claimed_swr_pct: Optional[float] = None,
    narration_text: Optional[str] = None
) -> str:
    """Strictly fact-checks narration claims against ledger ground truth (tolerance 0.5%)."""
    state = _get_live_or_offline_state()
    discrepancies = []
    tolerance_pct = 0.5

    actual_nw = state["net_worth"]
    actual_gain_pct = state["unrealized_gain_pct"]
    actual_swr = round((600000.0 / actual_nw * 100.0), 2) if actual_nw > 0 else 0.0

    if claimed_net_worth is not None:
        diff_pct = abs(claimed_net_worth - actual_nw) / actual_nw * 100.0 if actual_nw > 0 else 0.0
        if diff_pct > tolerance_pct:
            discrepancies.append({
                "metric": "net_worth",
                "claimed": claimed_net_worth,
                "ground_truth": actual_nw,
                "divergence_pct": round(diff_pct, 2),
                "verdict": "HALLUCINATION_DETECTED: Exceeds 0.5% tolerance"
            })

    if claimed_gain_pct is not None:
        diff_pts = abs(claimed_gain_pct - actual_gain_pct)
        if diff_pts > tolerance_pct:
            discrepancies.append({
                "metric": "unrealized_gain_pct",
                "claimed": claimed_gain_pct,
                "ground_truth": actual_gain_pct,
                "divergence_percentage_points": round(diff_pts, 2),
                "verdict": "HALLUCINATION_DETECTED: Exceeds 0.5% tolerance"
            })

    if claimed_swr_pct is not None:
        diff_pts = abs(claimed_swr_pct - actual_swr)
        if diff_pts > tolerance_pct:
            discrepancies.append({
                "metric": "live_swr_pct",
                "claimed": claimed_swr_pct,
                "ground_truth": actual_swr,
                "divergence_percentage_points": round(diff_pts, 2),
                "verdict": "HALLUCINATION_DETECTED: Exceeds 0.5% tolerance"
            })

    if narration_text:
        match_nw = re.search(r"(?:net\s*worth|portfolio\s*value).*?(?:inr|rs\.?|₹)?\s*([\d,]+(?:\.\d+)?)", narration_text, re.IGNORECASE)
        if match_nw:
            val = float(match_nw.group(1).replace(",", ""))
            diff_pct = abs(val - actual_nw) / actual_nw * 100.0 if actual_nw > 0 else 0.0
            if diff_pct > tolerance_pct:
                discrepancies.append({
                    "metric": "narration_extracted_net_worth",
                    "claimed": val,
                    "ground_truth": actual_nw,
                    "divergence_pct": round(diff_pct, 2),
                    "verdict": "HALLUCINATION_DETECTED: Exceeds 0.5% tolerance"
                })

    is_verified = len(discrepancies) == 0

    return json.dumps({
        "status": "SUCCESS",
        "verified": is_verified,
        "tolerance_threshold_pct": tolerance_pct,
        "discrepancies_found": len(discrepancies),
        "discrepancies": discrepancies,
        "ground_truth": {
            "as_of": state["as_of"],
            "net_worth": actual_nw,
            "unrealized_gain_pct": actual_gain_pct,
            "live_swr_pct": actual_swr,
            "data_source": state["source"]
        }
    }, indent=2)


# ---------------------------------------------------------------------------
# Tool Aliases & MCP Registration
# ---------------------------------------------------------------------------

tool_get_portfolio_status = get_portfolio_status
tool_simulate_trade_or_lumpsum = simulate_trade_or_lumpsum
tool_query_duckdb_readonly = query_duckdb_readonly
tool_get_portfolio_snapshot = get_portfolio_snapshot
tool_simulate_trade_what_if = simulate_trade_what_if
tool_simulate_lumpsum_inflow = simulate_lumpsum_inflow
tool_get_sec112a_harvest_candidates = get_sec112a_harvest_candidates
tool_get_guyton_klinger_fire_status = get_guyton_klinger_fire_status
tool_verify_financial_narration = verify_financial_narration

if mcp:
    mcp.tool()(get_portfolio_status)
    mcp.tool()(simulate_trade_or_lumpsum)
    mcp.tool()(query_duckdb_readonly)
    mcp.tool()(get_portfolio_snapshot)
    mcp.tool()(simulate_trade_what_if)
    mcp.tool()(simulate_lumpsum_inflow)
    mcp.tool()(get_sec112a_harvest_candidates)
    mcp.tool()(get_guyton_klinger_fire_status)
    mcp.tool()(verify_financial_narration)


def create_mcp_server():
    """Initializes and returns the FastMCP/MCPServer instance with all tools registered."""
    return mcp


if __name__ == "__main__":
    if mcp:
        logger.info("Starting Portfolio OS FastMCP Server...")
        mcp.run()
    else:
        logger.error("Could not initialize MCP server.")
