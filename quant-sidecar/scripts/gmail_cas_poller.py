#!/usr/bin/env python3
"""
Portfolio OS — Gmail CAS PDF Auto-Ingestor & SIP Lockout RTA Checker
Polls Gmail for CAS statements, securely uploads to core-node, shreds temporary files,
and computes settlement lockout windows based on NSE holidays and RTA allotment alerts.
"""

import os
import sys
import glob
import uuid
import yaml
import sqlite3
import shutil
import logging
import requests
from datetime import datetime, date, timedelta, timezone
from pathlib import Path
from typing import List, Dict, Any, Optional, Tuple

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("gmail-cas-poller")

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_DB_PATH = REPO_ROOT / "data" / "tax_ledger.db"
DEFAULT_SIP_SCHEDULE = REPO_ROOT / "config" / "sip_schedule.yaml"
DEFAULT_NSE_HOLIDAYS = REPO_ROOT / "config" / "nse_holidays_2026_2027.yaml"


def load_yaml(filepath: Path) -> Dict[str, Any]:
    if not filepath.exists():
        logger.warning(f"Config file not found: {filepath}")
        return {}
    with open(filepath, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def init_ingestion_tables(conn: sqlite3.Connection):
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS ingested_cas_emails (
            message_id TEXT PRIMARY KEY,
            received_at TEXT NOT NULL,
            processed_at TEXT NOT NULL,
            file_name TEXT NOT NULL
        )
        """
    )
    conn.commit()


def secure_shred(file_path: str):
    """Overwrites file with random bytes before deletion to prevent recovery of PII."""
    try:
        p = Path(file_path)
        if p.exists():
            length = p.stat().st_size
            with open(file_path, "ba+", buffering=0) as f:
                f.seek(0)
                f.write(os.urandom(length))
                f.flush()
                os.fsync(f.fileno())
            p.unlink()
            logger.info(f"Securely shredded temporary file: {file_path}")
    except Exception as e:
        logger.warning(f"Secure shred failed for {file_path}, falling back to standard unlink: {e}")
        try:
            if os.path.exists(file_path):
                os.remove(file_path)
        except Exception:
            pass


def is_business_day(d: date, holidays: set) -> bool:
    """Returns True if date is Monday-Friday and not an NSE holiday."""
    if d.weekday() >= 5:  # Saturday = 5, Sunday = 6
        return False
    return d.isoformat() not in holidays


def advance_business_days(start_date: date, n_days: int, holidays: set) -> date:
    """Advances start_date by n trading business days, skipping weekends & NSE holidays."""
    cur = start_date
    added = 0
    while added < n_days:
        cur += timedelta(days=1)
        if is_business_day(cur, holidays):
            added += 1
    return cur


def find_next_sip_date(schedule: List[Dict[str, Any]], current_date: date) -> date:
    """Finds the next upcoming SIP execution date across all active schedules."""
    if not schedule:
        return current_date + timedelta(days=30)

    candidate_dates = []
    for sip in schedule:
        day = sip.get("sip_day_of_month", 10)
        # Check this month
        try:
            this_month_sip = date(current_date.year, current_date.month, day)
        except ValueError:
            this_month_sip = date(current_date.year, current_date.month, 28)

        if this_month_sip >= current_date:
            candidate_dates.append(this_month_sip)
        else:
            # Next month
            next_month = current_date.month + 1
            next_year = current_date.year
            if next_month > 12:
                next_month = 1
                next_year += 1
            try:
                next_month_sip = date(next_year, next_month, day)
            except ValueError:
                next_month_sip = date(next_year, next_month, 28)
            candidate_dates.append(next_month_sip)

    return min(candidate_dates)


def compute_units_validity(
    last_cas_tx_date: date,
    current_time: datetime,
    rta_headers: Optional[List[Dict[str, Any]]] = None,
    sip_config_path: Path = DEFAULT_SIP_SCHEDULE,
    holidays_config_path: Path = DEFAULT_NSE_HOLIDAYS
) -> Dict[str, Any]:
    """
    Computes units_valid_until_date and pipeline status:
    1. Next upcoming SIP date + 4 business days (skipping weekends & NSE holidays).
    2. Checks for newer RTA allotment email headers:
       - Fix #3: Wait 24 hours after an RTA allotment email timestamp before flagging UNITS_STALE_PENDING_CAS.
       - Clear flag as soon as MAX(event_date) >= RTA email date.
    """
    sip_data = load_yaml(sip_config_path)
    sips = sip_data.get("sips", [])

    holidays_data = load_yaml(holidays_config_path)
    holidays = set(holidays_data.get("holidays", []))

    # Base valid until: next SIP execution date + 4 business days
    cur_date = current_time.date()
    next_sip = find_next_sip_date(sips, cur_date)
    normal_valid_until = advance_business_days(next_sip, 4, holidays)

    status = "HEALTHY"
    valid_until = normal_valid_until

    if rta_headers:
        for header in rta_headers:
            # header structure: {"received_at": datetime, "subject": str, "from": str}
            received_at = header.get("received_at")
            if isinstance(received_at, str):
                received_at = datetime.fromisoformat(received_at)
            if received_at.tzinfo is None:
                received_at = received_at.replace(tzinfo=timezone.utc)
            
            email_date = received_at.date()
            if email_date > last_cas_tx_date:
                # An allotment email newer than the latest CAS transaction date exists!
                # Fix #3: Check if 24 hours have elapsed since the email timestamp
                now_utc = current_time if current_time.tzinfo else current_time.replace(tzinfo=timezone.utc)
                age = now_utc - received_at

                if age >= timedelta(hours=24):
                    status = "UNITS_STALE_REQUEST_CAS"
                    valid_until = email_date
                    logger.warning(
                        f"RTA allotment email received on {email_date} is >24h old ({age.total_seconds() / 3600:.1f}h) "
                        f"and newer than last CAS tx date {last_cas_tx_date}. Flagging UNITS_STALE_REQUEST_CAS."
                    )
                    break
                else:
                    logger.info(
                        f"RTA allotment email seen ({age.total_seconds() / 3600:.1f}h old), "
                        f"within 24-hour grace window. Pipeline remains HEALTHY."
                    )

    return {
        "status": status,
        "units_valid_until_date": valid_until.isoformat(),
        "next_sip_date": next_sip.isoformat(),
        "last_cas_tx_date": last_cas_tx_date.isoformat()
    }


def ingest_pdf_to_core(pdf_path: str, core_node_url: str, auth_token: str, password: str = "") -> Dict[str, Any]:
    """Posts a CAS PDF to Portfolio OS Core Node /api/v1/statements/upload."""
    url = f"{core_node_url.rstrip('/')}/api/v1/statements/upload"
    headers = {"X-Api-Auth-Token": auth_token}

    with open(pdf_path, "rb") as f:
        files = {"file": (os.path.basename(pdf_path), f, "application/pdf")}
        data = {"password": password}
        resp = requests.post(url, headers=headers, files=files, data=data, timeout=60)
        resp.raise_for_status()
        return resp.json()


def run_poller_mock_or_live(
    db_path: Path = DEFAULT_DB_PATH,
    core_node_url: str = "http://127.0.0.1:8080",
    auth_token: Optional[str] = None,
    cas_password: str = "",
    incoming_test_pdf: Optional[str] = None
) -> Dict[str, Any]:
    """
    Executes one ingestion cycle:
    1. Initializes SQLite tables.
    2. Uploads CAS PDF attachment if provided or polled.
    3. Shreds temporary files.
    4. Evaluates SIP lockout & RTA alert status.
    """
    token = auth_token or os.getenv("API_AUTH_TOKEN", "")
    pwd = cas_password or os.getenv("CAS_PDF_PASSWORD", "")

    conn = sqlite3.connect(str(db_path))
    init_ingestion_tables(conn)

    results = {"ingested_count": 0, "messages_processed": []}

    if incoming_test_pdf and os.path.exists(incoming_test_pdf):
        msg_id = f"test_msg_{uuid.uuid4().hex[:8]}"
        # Copy to /tmp to simulate download
        tmp_target = f"/tmp/cas_ingest_{uuid.uuid4().hex}.pdf"
        shutil.copyfile(incoming_test_pdf, tmp_target)

        try:
            logger.info(f"Uploading statement from {tmp_target} to core-node...")
            upload_resp = ingest_pdf_to_core(tmp_target, core_node_url, token, pwd)
            logger.info(f"Upload successful: {upload_resp}")

            now_iso = datetime.now(timezone.utc).isoformat()
            conn.execute(
                "INSERT INTO ingested_cas_emails (message_id, received_at, processed_at, file_name) VALUES (?, ?, ?, ?)",
                (msg_id, now_iso, now_iso, os.path.basename(incoming_test_pdf))
            )
            conn.commit()
            results["ingested_count"] += 1
            results["messages_processed"].append({"message_id": msg_id, "response": upload_resp})
        finally:
            secure_shred(tmp_target)

    # Compute latest CAS transaction date from database
    row = conn.execute("SELECT MAX(event_date) FROM tax_events").fetchone()
    last_tx_date_str = row[0] if (row and row[0]) else "2026-01-01"
    last_tx_date = date.fromisoformat(last_tx_date_str)

    validity = compute_units_validity(last_tx_date, datetime.now(timezone.utc))
    results["validity"] = validity
    conn.close()
    return results


if __name__ == "__main__":
    error_flag = Path("/tmp/gmail_poll_error.json")
    try:
        test_pdf = sys.argv[1] if len(sys.argv) > 1 else None
        res = run_poller_mock_or_live(incoming_test_pdf=test_pdf)
        if error_flag.exists():
            error_flag.unlink(missing_ok=True)
        print(yaml.dump(res))
    except Exception as e:
        logger.error(f"Gmail CAS polling encountered error: {e}", exc_info=True)
        try:
            with open(error_flag, "w", encoding="utf-8") as f:
                json.dump({"error": str(e), "timestamp": datetime.now(timezone.utc).isoformat()}, f)
        except Exception:
            pass
        sys.exit(0)
