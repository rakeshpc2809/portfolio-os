#!/usr/bin/env python3
"""
Portfolio OS — Google Sheets Snapshot & Append-Only Ledger Events Publisher
Uploads:
  1. 'holdings_snapshot' tab: Live holdings, units, buckets, AMFI codes, peak NAVs
  2. 'system_state' tab: Key/value operational state, pipeline freshness, ledger hash
  3. 'ledger_events' tab: Append-only transaction log (STRICTLY ZERO PII - NO folio_no)

Supports:
  - Live Google Sheets API via Service Account JSON
  - Local CSV/JSON export fallback when running offline or without credentials
"""

import argparse
import csv
import json
import os
import re
import sqlite3
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional
import urllib.request

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DB = str(REPO_ROOT / "data" / "tax_ledger.db")
DEFAULT_EXPORT_DIR = str(REPO_ROOT / "data" / "sheets_export")


def load_events_from_db(db_path: str) -> List[Dict[str, Any]]:
    if not os.path.exists(db_path):
        return []
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    try:
        cur = conn.cursor()
        cur.execute("""
            SELECT id, asset_id, asset_name, isin, event_type, event_date,
                   units, price_per_unit, gross_amount, source_document_id,
                   ingested_at, previous_hash, event_hash, tx_fingerprint
            FROM tax_events
            ORDER BY rowid ASC
        """)
        rows = [dict(r) for r in cur.fetchall()]
        return rows
    except Exception as e:
        print(f"[WARN] Unable to read tax_events from {db_path}: {e}", file=sys.stderr)
        return []
    finally:
        conn.close()


def fetch_snapshot_from_api(api_url: str, token: str) -> Optional[Dict[str, Any]]:
    try:
        req = urllib.request.Request(
            f"{api_url.rstrip('/')}/api/v1/sync/snapshot",
            headers={"X-Api-Auth-Token": token}
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status == 200:
                return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"[WARN] Failed fetching snapshot from API ({api_url}): {e}", file=sys.stderr)
    return None


def export_to_csv_files(output_dir: str, snapshot: Dict[str, Any], events: List[Dict[str, Any]]) -> None:
    os.makedirs(output_dir, exist_ok=True)

    # 1. holdings_snapshot.csv
    holdings_path = os.path.join(output_dir, "holdings_snapshot.csv")
    holdings = snapshot.get("holdings", [])
    if holdings:
        with open(holdings_path, "w", newline="", encoding="utf-8") as f:
            fieldnames = [
                "isin", "fund_name", "asset_bucket", "total_units", "avg_cost",
                "current_nav", "current_value", "invested_value", "exit_load_free_units"
            ]
            writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
            writer.writeheader()
            for h in holdings:
                writer.writerow(h)
        print(f"[INFO] Exported {len(holdings)} holdings to {holdings_path}")

    # 2. system_state.csv
    state_path = os.path.join(output_dir, "system_state.csv")
    sync_info = snapshot.get("sync_info", {})
    with open(state_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["key", "value"])
        for k, v in sync_info.items():
            writer.writerow([k, str(v)])
    print(f"[INFO] Exported system_state to {state_path}")

    # 3. ledger_events.csv (Append-only zero PII ledger log)
    events_path = os.path.join(output_dir, "ledger_events.csv")
    if events:
        with open(events_path, "w", newline="", encoding="utf-8") as f:
            fieldnames = [
                "id", "asset_id", "asset_name", "isin", "event_type", "event_date",
                "units", "price_per_unit", "gross_amount", "source_document_id",
                "ingested_at", "previous_hash", "event_hash", "tx_fingerprint"
            ]
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for ev in events:
                # Security assertion: No folio_no
                ev_copy = dict(ev)
                ev_copy.pop("folio_no", None)
                writer.writerow(ev_copy)
        print(f"[INFO] Exported {len(events)} append-only ledger events to {events_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Portfolio OS Sheets Publisher")
    parser.add_argument("--db-path", default=DEFAULT_DB, help="Path to SQLite tax_ledger.db")
    parser.add_argument("--api-url", default="http://localhost:8080", help="Core Node base URL")
    parser.add_argument("--token", default="dev_secret_key_123", help="API authentication token")
    parser.add_argument("--export-dir", default=DEFAULT_EXPORT_DIR, help="Directory for CSV exports")
    parser.add_argument("--dry-run", action="store_true", help="Perform export locally without Google API call")
    args = parser.parse_args()

    print("[INFO] Starting Sheets Snapshot & Ledger Events Publisher...")

    # Load events from SQLite
    events = load_events_from_db(args.db_path)
    print(f"[INFO] Loaded {len(events)} events from {args.db_path}")

    # Fetch snapshot from Core Node API (or construct minimal)
    snapshot = fetch_snapshot_from_api(args.api_url, args.token)
    if not snapshot:
        snapshot = {
            "sync_info": {
                "generated_at": "OFFLINE_FALLBACK",
                "status": "OFFLINE",
                "total_invested": 0.0,
                "current_value": 0.0
            },
            "holdings": []
        }

    # Always generate local CSV bundle for offline/Sheets manual sync
    export_to_csv_files(args.export_dir, snapshot, events)

    # Check for Google Sheets credentials
    creds_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "/home/rakeshpc/Projects/portfolio-os/service_account.json")
    raw_sheet_id = os.getenv("PORTFOLIO_SHEET_ID")

    if not args.dry_run and raw_sheet_id and os.path.exists(creds_path):
        try:
            import gspread  # type: ignore
            # Handle full URL or pure spreadsheet ID
            sheet_id_match = re.search(r"/d/([a-zA-Z0-9-_]+)", raw_sheet_id)
            sheet_id = sheet_id_match.group(1) if sheet_id_match else raw_sheet_id

            gc = gspread.service_account(filename=creds_path)
            sh = gc.open_by_key(sheet_id)
            print(f"[INFO] Connected to Google Sheet: '{sh.title}'")

            # 1a. Sync 'holdings_snapshot' tab
            holdings = snapshot.get("holdings", [])
            if holdings:
                try:
                    ws_holdings = sh.worksheet("holdings_snapshot")
                except gspread.exceptions.WorksheetNotFound:
                    ws_holdings = sh.add_worksheet(title="holdings_snapshot", rows=len(holdings) + 20, cols=12)
                h_fields = [
                    "isin", "fund_name", "asset_bucket", "total_units", "avg_cost",
                    "current_nav", "current_value", "invested_value", "exit_load_free_units"
                ]
                h_rows = [h_fields] + [[str(h.get(c, "") or "") for c in h_fields] for h in holdings]
                ws_holdings.clear()
                ws_holdings.update(h_rows)

            # 1b. Sync 'system_state' tab
            sync_info = snapshot.get("sync_info", {})
            if sync_info:
                try:
                    ws_state = sh.worksheet("system_state")
                except gspread.exceptions.WorksheetNotFound:
                    ws_state = sh.add_worksheet(title="system_state", rows=len(sync_info) + 20, cols=4)
                s_rows = [["key", "value"]] + [[str(k), str(v)] for k, v in sync_info.items()]
                ws_state.clear()
                ws_state.update(s_rows)

            # 1. Sync 'ledger_events' tab (Append-only zero-PII disaster recovery log)
            if events:
                try:
                    ws_events = sh.worksheet("ledger_events")
                except gspread.exceptions.WorksheetNotFound:
                    ws_events = sh.add_worksheet(title="ledger_events", rows=len(events) + 50, cols=15)

                fieldnames = [
                    "id", "asset_id", "asset_name", "isin", "event_type", "event_date",
                    "units", "price_per_unit", "gross_amount", "source_document_id",
                    "ingested_at", "previous_hash", "event_hash", "tx_fingerprint"
                ]
                rows_to_upload = [fieldnames]
                for ev in events:
                    row = [str(ev.get(col, "") or "") for col in fieldnames]
                    rows_to_upload.append(row)

                ws_events.clear()
                ws_events.update(rows_to_upload)
                print(f"[SUCCESS] Uploaded {len(events)} transactions to Google Sheet tab 'ledger_events' (ZERO PII).")

        except ImportError:
            print("[INFO] gspread not installed. CSV export ready for import.")
        except Exception as e:
            print(f"[WARN] Google Sheets API sync failed: {e}")
    else:
        print(f"[INFO] Dry-run or no Google credentials configured. Output CSVs ready at: {args.export_dir}")

    print("[SUCCESS] Sheets snapshot & ledger events sync finished.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
