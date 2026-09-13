"""Offline one-time backfill script for historical mutual fund NAVs via MFAPI.

Populates `nav_history` in `data/tax_ledger.duckdb` and exports the aligned
series to `quant-sidecar/data/nav_export.parquet`.
"""

from datetime import datetime
import json
from pathlib import Path
import sys
import urllib.request
import duckdb
import polars as pl

# Canonical portfolio target schemes and their MFAPI scheme codes
SCHEMES = {
    "INF879O01027": (122639, "Parag Parikh Flexi Cap"),
    "INF109KC12U0": (152482, "ICICI LargeMidcap 250"),
    "INF109KC13X2": (152936, "ICICI Value 30"),
    "INF754K01TN5": (153096, "Edelweiss Multicap Momentum 50"),
    "INF204K01K15": (118778, "Nippon Small Cap"),
    "INF247L01BM8": (150642, "Motilal Gold and Silver FoF"),
    "INF205K01KR8": (120401, "Invesco Arbitrage"),
}

def fetch_nav_series(scheme_code: int, isin: str, name: str) -> list[dict]:
    url = f"https://api.mfapi.in/mf/{scheme_code}"
    req = urllib.request.Request(url, headers={"User-Agent": "PortfolioOS-Quant/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode())
            nav_data = data.get("data", [])
            records = []
            for row in nav_data:
                try:
                    raw_date = row.get("date")
                    nav_val = float(row.get("nav"))
                    if nav_val <= 0:
                        continue
                    iso_date = datetime.strptime(raw_date, "%d-%m-%Y").strftime("%Y-%m-%d")
                    records.append({
                        "asset_id": isin,
                        "nav_date": iso_date,
                        "nav": nav_val,
                    })
                except Exception:
                    continue
            return records
    except Exception as e:
        print(f"[-] Error fetching {name} ({isin}, code {scheme_code}): {e}", file=sys.stderr)
        return []

def main():
    repo_root = Path(__file__).resolve().parent.parent.parent
    duckdb_path = repo_root / "data" / "tax_ledger.duckdb"
    parquet_paths = [
        repo_root / "quant-sidecar" / "data" / "nav_export.parquet",
        repo_root / "data" / "nav_export.parquet",
    ]
    for p in parquet_paths:
        p.parent.mkdir(parents=True, exist_ok=True)

    print("=" * 72)
    print("MFAPI HISTORICAL NAV BACKFILL (OFFLINE BATCH JOB)")
    print("=" * 72)

    all_records = []
    summary = []

    for isin, (code, name) in SCHEMES.items():
        print(f"[*] Fetching historical NAV for {name} ({isin}, scheme {code})...")
        records = fetch_nav_series(code, isin, name)
        if records:
            all_records.extend(records)
            dates = [r["nav_date"] for r in records]
            summary.append({
                "isin": isin,
                "name": name,
                "records": len(records),
                "min_date": min(dates),
                "max_date": max(dates),
            })
            print(f"    -> Received {len(records)} records ({min(dates)} to {max(dates)})")
        else:
            print(f"    -> WARNING: No records returned for {name}!")

    if not all_records:
        print("[-] No records fetched. Aborting.", file=sys.stderr)
        sys.exit(1)

    # 1. Update DuckDB if write lock is free
    duckdb_updated = False
    try:
        conn = duckdb.connect(str(duckdb_path), read_only=False)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS nav_history (
                asset_id VARCHAR,
                nav_date VARCHAR,
                nav DOUBLE,
                PRIMARY KEY (asset_id, nav_date)
            )
        """)
        # Insert records in batch
        batch_df = pl.DataFrame(all_records)
        conn.register("batch_df", batch_df.to_arrow())
        conn.execute("""
            INSERT INTO nav_history (asset_id, nav_date, nav)
            SELECT asset_id, nav_date, nav FROM batch_df
            ON CONFLICT (asset_id, nav_date) DO UPDATE SET nav = EXCLUDED.nav
        """)
        conn.close()
        duckdb_updated = True
        print(f"[+] Successfully upserted {len(all_records)} records into {duckdb_path}")
    except Exception as e:
        print(f"[!] Could not acquire exclusive DuckDB write lock ({e}). Bypassing DB write.")

    # 2. Write/Overwrite Parquet export directly
    # Even if DuckDB had an active lock from running core-node, we write direct parquet
    export_df = pl.DataFrame(all_records).select([
        pl.col("nav_date"),
        pl.col("asset_id").alias("isin"),
        pl.col("nav").alias("nav_value"),
    ]).sort(["isin", "nav_date"])
    
    for p in parquet_paths:
        export_df.write_parquet(p)
        print(f"[+] Wrote {len(export_df)} total records to {p}")

    # 3. Print Intersection / Common Alignment Window Report
    print("\n" + "=" * 72)
    print("SCHEME HISTORY SUMMARY & COMMON ALIGNMENT INTERSECTION")
    print("=" * 72)
    min_common_start = max(s["min_date"] for s in summary)
    max_common_end = min(s["max_date"] for s in summary)

    for s in summary:
        print(f"  {s['isin']} | {s['name'][:30]:<30} | {s['records']:>5} records | {s['min_date']} to {s['max_date']}")

    print("-" * 72)
    print(f"Common Intersection Window: {min_common_start} to {max_common_end}")
    
    # Calculate how many rows survive intersection across all 7 funds
    pivot_check = (
        export_df
        .filter((pl.col("nav_date") >= min_common_start) & (pl.col("nav_date") <= max_common_end))
        .pivot(index="nav_date", on="isin", values="nav_value")
        .fill_null(strategy="forward")
        .drop_nulls()
    )
    print(f"Common Aligned Trading Days:  {len(pivot_check)} days")
    print(f"DuckDB Persistence Status:   {'UPDATED' if duckdb_updated else 'BYPASSED (Direct Parquet Write)'}")
    print("=" * 72)

if __name__ == "__main__":
    main()
