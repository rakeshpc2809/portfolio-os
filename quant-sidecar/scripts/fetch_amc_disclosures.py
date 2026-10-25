"""Ingestion script for AMC monthly statutory portfolio disclosures.

Scans data/factsheets for disclosure spreadsheets, parses holdings,
and pushes them to Core Node's DuckDB store with audit timestamps.
"""

import os
import sys
import json
import urllib.request
from datetime import datetime
from pathlib import Path

# Add quant sidecar to sys.path
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
sys.path.insert(0, str(SCRIPT_DIR.parent))

from quant.disclosure_parser import parse_amc_portfolio_disclosure

SCHEME_FACTSHEET_MAP = {
    "INF879O01027": ("ppfas_flexicap_full.xlsx", "Parag Parikh Flexi Cap Fund"),
    "INF204K01K15": ("nippon_smallcap_full.xlsx", "Nippon India Small Cap Fund"),
}


def ingest_disclosures(core_node_url: str = "http://127.0.0.1:8080"):
    factsheets_dir = REPO_ROOT / "data" / "factsheets"
    if not factsheets_dir.exists():
        print(f"[-] Factsheets directory not found at {factsheets_dir}", file=sys.stderr)
        return

    token = os.getenv("API_AUTH_TOKEN", "dev_secret_key_123")
    print("=" * 70)
    print("PORTFOLIO OS - AUTOMATED AMC DISCLOSURE INGESTION")
    print(f"Factsheets: {factsheets_dir}")
    print(f"Core Node:  {core_node_url}")
    print("=" * 70)

    for isin, (filename, fund_name) in SCHEME_FACTSHEET_MAP.items():
        file_path = factsheets_dir / filename
        if not file_path.exists():
            print(f"[!] Factsheet file not found: {filename} for {fund_name}. Skipping.")
            continue

        try:
            holdings = parse_amc_portfolio_disclosure(str(file_path), fund_id=isin)
            print(f"[+] Parsed {len(holdings)} holdings from {filename} ({fund_name})")
            mtime_date = datetime.fromtimestamp(file_path.stat().st_mtime).strftime("%Y-%m-%d")

            payload = {
                "fund_id": isin,
                "disclosure_date": os.getenv("DISCLOSURE_DATE", mtime_date),
                "source_type": "FACTSHEET_POI_PARSED",
                "holdings": holdings
            }

            req = urllib.request.Request(
                f"{core_node_url}/api/v1/analytics/holdings/ingest",
                data=json.dumps(payload).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "X-Api-Auth-Token": token
                },
                method="POST"
            )

            try:
                with urllib.request.urlopen(req, timeout=5) as resp:
                    res = json.loads(resp.read().decode())
                    print(f"    -> Ingestion Response: {res}")
            except Exception as e:
                print(f"    [!] Core Node online ingest skipped ({e}). Holdings verified parsed locally.")

        except Exception as e:
            print(f"[-] Failed to parse {filename}: {e}", file=sys.stderr)


if __name__ == "__main__":
    ingest_disclosures()
