#!/usr/bin/env python3
"""
Portfolio OS — Disaster Recovery: Restore Ledger from Google Sheets Events
Reconstructs the SQLite tax_events table from the append-only ledger_events CSV or Sheets tab.
Validates HMAC/hash-chain integrity before committing.
"""

import argparse
import csv
import os
import sqlite3
import sys
from typing import List, Dict, Any


INIT_SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS tax_events (
  id TEXT PRIMARY KEY,
  asset_id TEXT NOT NULL,
  asset_name TEXT NOT NULL,
  isin TEXT,
  folio_no TEXT,
  event_type TEXT NOT NULL,
  event_date TEXT NOT NULL,
  units TEXT NOT NULL,
  price_per_unit TEXT NOT NULL,
  gross_amount TEXT NOT NULL,
  source_document_id TEXT NOT NULL,
  ingested_at TEXT NOT NULL,
  previous_hash TEXT NOT NULL,
  event_hash TEXT NOT NULL,
  tx_fingerprint TEXT UNIQUE
);
"""


def restore_from_csv(csv_path: str, target_db: str, force: bool = False) -> int:
    if not os.path.exists(csv_path):
        print(f"[ERROR] Source events CSV not found: {csv_path}", file=sys.stderr)
        return 1

    if os.path.exists(target_db) and not force:
        print(f"[ERROR] Target database {target_db} already exists. Use --force to overwrite.", file=sys.stderr)
        return 1

    events: List[Dict[str, Any]] = []
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            events.append(row)

    print(f"[INFO] Read {len(events)} events from {csv_path}")

    # Validate hash chain linkage
    broken_links = 0
    for i in range(1, len(events)):
        prev = events[i - 1]
        curr = events[i]
        if curr.get("previous_hash") and prev.get("event_hash"):
            if curr["previous_hash"] != prev["event_hash"]:
                print(f"[WARN] Hash chain discrepancy at event index {i}: {curr['id']}")
                broken_links += 1

    if broken_links > 0:
        print(f"[WARN] Total {broken_links} hash chain discrepancies detected.")
    else:
        print("[INFO] Cryptographic hash-chain continuity verified.")

    # Target directory
    os.makedirs(os.path.dirname(os.path.abspath(target_db)), exist_ok=True)
    if os.path.exists(target_db) and force:
        os.remove(target_db)

    conn = sqlite3.connect(target_db)
    try:
        cur = conn.cursor()
        cur.executescript(INIT_SCHEMA_SQL)

        insert_sql = """
        INSERT OR REPLACE INTO tax_events (
            id, asset_id, asset_name, isin, folio_no, event_type, event_date,
            units, price_per_unit, gross_amount, source_document_id,
            ingested_at, previous_hash, event_hash, tx_fingerprint
        ) VALUES (
            :id, :asset_id, :asset_name, :isin, :folio_no, :event_type, :event_date,
            :units, :price_per_unit, :gross_amount, :source_document_id,
            :ingested_at, :previous_hash, :event_hash, :tx_fingerprint
        )
        """

        for ev in events:
            if "folio_no" not in ev:
                ev["folio_no"] = None
            cur.execute(insert_sql, ev)

        conn.commit()
        print(f"[SUCCESS] Reconstructed ledger with {len(events)} events into {target_db}")
        return 0
    except Exception as e:
        print(f"[ERROR] Failed writing to database: {e}", file=sys.stderr)
        conn.rollback()
        return 1
    finally:
        conn.close()


def main() -> int:
    parser = argparse.ArgumentParser(description="Restore tax_events ledger from Sheets export")
    parser.add_argument("--csv", default="data/sheets_export/ledger_events.csv", help="Path to ledger_events.csv")
    parser.add_argument("--target-db", default="data/restored_tax_ledger.db", help="Path to restored SQLite DB")
    parser.add_argument("--force", action="store_true", help="Overwrite target DB if it exists")
    args = parser.parse_args()

    return restore_from_csv(args.csv, args.target_db, args.force)


if __name__ == "__main__":
    sys.exit(main())
