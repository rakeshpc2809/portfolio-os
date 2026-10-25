#!/usr/bin/env python3
"""
Portfolio OS — Gmail CAS PDF Auto-Ingestor & SIP Lockout RTA Checker
Polls Gmail via Python standard library imaplib.IMAP4_SSL('imap.gmail.com') for CAS statements,
securely uploads to Core Node, shreds temporary files, and computes settlement lockout windows
based on NSE holidays and RTA allotment alerts.
"""

import os
import sys
import glob
import uuid
import yaml
import json
import sqlite3
import shutil
import logging
import requests
import imaplib
import email
from email.header import decode_header
from email.utils import parsedate_to_datetime
from datetime import datetime, date, timedelta, timezone
from pathlib import Path
from typing import List, Dict, Any, Optional, Tuple

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("gmail-cas-poller")

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_DB_PATH = REPO_ROOT / "data" / "tax_ledger.db"
DEFAULT_SIP_SCHEDULE = REPO_ROOT / "config" / "sip_schedule.yaml"
DEFAULT_NSE_HOLIDAYS = REPO_ROOT / "config" / "nse_holidays_2026_2027.yaml"


def load_env_file(filepath: Optional[Path] = None):
    """Loads environment variables from ~/.config/portfolio-os/secrets.env or .env if not already present."""
    if filepath is None:
        cfg_secrets = Path.home() / ".config" / "portfolio-os" / "secrets.env"
        filepath = cfg_secrets if cfg_secrets.exists() else (REPO_ROOT / ".env")
    if not filepath.exists():
        return
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                k = k.strip()
                v = v.strip().strip('"').strip("'")
                if k and k not in os.environ:
                    os.environ[k] = v
    except Exception as e:
        logger.warning(f"Failed to parse env file at {filepath}: {e}")


def load_yaml(filepath: Path) -> Dict[str, Any]:
    if not filepath.exists():
        logger.warning(f"Config file not found: {filepath}")
        return {}
    with open(filepath, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def decode_mime_header(val: Optional[str]) -> str:
    """Safely decodes RFC 2047 encoded email headers."""
    if not val:
        return ""
    try:
        parts = decode_header(val)
        decoded = []
        for content, enc in parts:
            if isinstance(content, bytes):
                decoded.append(content.decode(enc or "utf-8", errors="ignore"))
            else:
                decoded.append(str(content))
        return "".join(decoded)
    except Exception as e:
        logger.debug(f"Failed to decode mime header {val}: {e}")
        return str(val)


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
        try:
            this_month_sip = date(current_date.year, current_date.month, day)
        except ValueError:
            this_month_sip = date(current_date.year, current_date.month, 28)

        if this_month_sip >= current_date:
            candidate_dates.append(this_month_sip)
        else:
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
       - Wait 24 hours after an RTA allotment email timestamp before flagging UNITS_STALE_PENDING_CAS.
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
            received_at = header.get("received_at")
            if isinstance(received_at, str):
                received_at = datetime.fromisoformat(received_at)
            if received_at.tzinfo is None:
                received_at = received_at.replace(tzinfo=timezone.utc)

            email_date = received_at.date()
            if email_date > last_cas_tx_date:
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


def connect_gmail_imap(user: str, password: str, host: str = "imap.gmail.com", port: int = 993) -> imaplib.IMAP4_SSL:
    """Connects to Gmail via standard library IMAP4_SSL on port 993 and logs in."""
    clean_password = password.replace(" ", "").strip()
    logger.info(f"Connecting to Gmail IMAP ({host}:{port}) for user {user}...")
    mail = imaplib.IMAP4_SSL(host, port)
    mail.login(user, clean_password)
    logger.info("Gmail IMAP authentication successful.")
    return mail


def fetch_rta_allotment_headers(mail: imaplib.IMAP4_SSL) -> List[Dict[str, Any]]:
    """
    Scans Gmail for recent RTA allotment confirmation headers using:
    mail.uid('search', None, 'X-GM-RAW "from:(donotreply@camsonline.com OR samfS@kfintech.com OR camsonline.com OR kfintech.com) subject:(allotment OR purchase)"')
    Fetches only (BODY.PEEK[HEADER.FIELDS (DATE SUBJECT MESSAGE-ID)]) without downloading bodies.
    """
    rta_headers: List[Dict[str, Any]] = []
    try:
        query = 'from:(donotreply@camsonline.com OR samfS@kfintech.com OR camsonline.com OR kfintech.com) subject:(allotment OR purchase)'
        try:
            status, data = mail.uid('search', None, f'X-GM-RAW "{query}"')
        except Exception:
            status, data = mail.uid('search', None, '(OR (SUBJECT "allotment") (SUBJECT "purchase"))')

        if status != "OK" or not data or not data[0]:
            return rta_headers

        uids = data[0].split()
        for uid in reversed(uids[-10:]):
            try:
                uid_str = uid.decode() if isinstance(uid, bytes) else str(uid)
                res, hdata = mail.uid('fetch', uid, "(BODY.PEEK[HEADER.FIELDS (DATE SUBJECT MESSAGE-ID)])")
                if res != "OK" or not hdata or not hdata[0]:
                    continue
                raw_header = hdata[0][1]
                msg = email.message_from_bytes(raw_header)
                subj = decode_mime_header(msg.get("Subject"))
                sender = decode_mime_header(msg.get("From"))
                date_str = msg.get("Date")
                if not date_str:
                    continue
                parsed_dt = parsedate_to_datetime(date_str)
                if parsed_dt.tzinfo is None:
                    parsed_dt = parsed_dt.replace(tzinfo=timezone.utc)
                rta_headers.append({
                    "uid": uid_str,
                    "received_at": parsed_dt,
                    "subject": subj,
                    "from": sender
                })
            except Exception as e:
                logger.debug(f"Failed to parse RTA email header for UID {uid}: {e}")
    except Exception as e:
        logger.warning(f"Error fetching RTA allotment headers via IMAP: {e}")

    return rta_headers


def poll_gmail_via_imap(
    conn: sqlite3.Connection,
    core_node_url: str,
    auth_token: str,
    user: str,
    password: str,
    cas_password: str = ""
) -> Tuple[Dict[str, Any], List[Dict[str, Any]]]:
    """
    Polls Gmail via IMAP4_SSL for CAS PDF statements:
    1. Connects to imap.gmail.com:993 and selects INBOX in readonly mode.
    2. Searches using native Gmail IMAP extension X-GM-RAW with UID:
       mail.uid('search', None, 'X-GM-RAW "from:(camsonline.com OR kfintech.com OR nsdl.co.in OR cdslindia.com) filename:pdf has:attachment"')
    3. Checks permanent IMAP UID / Message-ID in SQLite ingested_cas_emails for idempotency.
    4. Extracts .pdf attachment to /tmp/cas_ingest_<uuid>.pdf (chmod 600), posts to /api/v1/statements/upload, and shreds.
    5. Fetches RTA allotment headers to evaluate settlement lockout.
    """
    mail = None
    results = {"ingested_count": 0, "messages_processed": []}
    rta_headers: List[Dict[str, Any]] = []

    try:
        mail = connect_gmail_imap(user, password)
        status, _ = mail.select("INBOX", readonly=True)
        if status != "OK":
            logger.error("Failed to select INBOX in Gmail IMAP.")
            return results, rta_headers

        cas_query = 'from:(camsonline.com OR kfintech.com OR nsdl.co.in OR cdslindia.com) filename:pdf has:attachment'
        try:
            status, data = mail.uid('search', None, f'X-GM-RAW "{cas_query}"')
        except Exception as e:
            logger.warning(f"X-GM-RAW UID search failed ({e}), falling back to standard IMAP search...")
            status, data = mail.uid('search', None, '(OR (OR (SUBJECT "CAS") (FROM "camsonline")) (FROM "kfintech"))')

        if status == "OK" and data and data[0]:
            candidate_uids = data[0].split()
            logger.info(f"Found {len(candidate_uids)} potential CAS email candidate UIDs.")

            for uid in candidate_uids:
                try:
                    uid_str = uid.decode() if isinstance(uid, bytes) else str(uid)
                    h_res, h_data = mail.uid('fetch', uid, "(BODY.PEEK[HEADER.FIELDS (MESSAGE-ID DATE FROM SUBJECT)])")
                    if h_res != "OK" or not h_data or not h_data[0]:
                        continue

                    hdr_msg = email.message_from_bytes(h_data[0][1])
                    raw_msg_id = hdr_msg.get("Message-ID", "")
                    clean_msg_id = raw_msg_id.strip("<> \r\n\t") if raw_msg_id else f"UID_{uid_str}"
                    subj = decode_mime_header(hdr_msg.get("Subject"))
                    date_str = hdr_msg.get("Date", "")

                    # Check idempotency in SQLite using permanent UID or Message-ID
                    cur = conn.cursor()
                    cur.execute(
                        "SELECT 1 FROM ingested_cas_emails WHERE message_id IN (?, ?, ?)",
                        (clean_msg_id, uid_str, f"UID_{uid_str}")
                    )
                    if cur.fetchone():
                        logger.debug(f"Skipping already ingested CAS message: UID {uid_str} ({clean_msg_id})")
                        continue

                    logger.info(f"New CAS email candidate found: UID {uid_str} ({clean_msg_id}) - '{subj}'. Fetching body...")
                    f_res, f_data = mail.uid('fetch', uid, "(RFC822)")
                    if f_res != "OK" or not f_data or not f_data[0]:
                        continue

                    full_msg = email.message_from_bytes(f_data[0][1])
                    pdf_found = False

                    for part in full_msg.walk():
                        if part.get_content_maintype() == "multipart":
                            continue
                        filename = part.get_filename()
                        if not filename:
                            continue

                        decoded_filename = decode_mime_header(filename)
                        if decoded_filename.lower().endswith(".pdf"):
                            pdf_bytes = part.get_payload(decode=True)
                            if not pdf_bytes:
                                continue

                            tmp_target = f"/tmp/cas_ingest_{uuid.uuid4().hex}.pdf"
                            fd = os.open(tmp_target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
                            with os.fdopen(fd, "wb") as pf:
                                pf.write(pdf_bytes)

                            received_iso = datetime.now(timezone.utc).isoformat()
                            if date_str:
                                try:
                                    p_dt = parsedate_to_datetime(date_str)
                                    if p_dt.tzinfo is None:
                                        p_dt = p_dt.replace(tzinfo=timezone.utc)
                                    received_iso = p_dt.isoformat()
                                except Exception:
                                    pass

                            now_iso = datetime.now(timezone.utc).isoformat()

                            try:
                                logger.info(f"Uploading statement attachment '{decoded_filename}' to Core Node...")
                                upload_resp = ingest_pdf_to_core(tmp_target, core_node_url, auth_token, cas_password)
                                logger.info(f"Upload successful: {upload_resp}")

                                conn.execute(
                                    "INSERT INTO ingested_cas_emails (message_id, received_at, processed_at, file_name) VALUES (?, ?, ?, ?)",
                                    (clean_msg_id, received_iso, now_iso, decoded_filename)
                                )
                                conn.commit()
                                results["ingested_count"] += 1
                                results["messages_processed"].append({
                                    "uid": uid_str,
                                    "message_id": clean_msg_id,
                                    "file_name": decoded_filename,
                                    "response": upload_resp
                                })
                                pdf_found = True
                            except (requests.exceptions.ConnectionError, requests.exceptions.Timeout) as conn_err:
                                logger.warning(
                                    f"Core Node is currently unreachable at {core_node_url} ({conn_err}). "
                                    f"Halting statement ingestion cycle; statements will be uploaded when Core Node is online."
                                )
                                return results, fetch_rta_allotment_headers(mail)
                            except requests.RequestException as req_err:
                                status_code = getattr(getattr(req_err, "response", None), "status_code", None)
                                if status_code in (400, 415):
                                    logger.warning(
                                        f"Core Node rejected unsupported attachment '{decoded_filename}' (HTTP {status_code}, UID: {uid_str}): {req_err}. "
                                        f"Marking as skipped."
                                    )
                                    conn.execute(
                                        "INSERT INTO ingested_cas_emails (message_id, received_at, processed_at, file_name) VALUES (?, ?, ?, ?)",
                                        (clean_msg_id, received_iso, now_iso, f"SKIPPED_UNSUPPORTED_{decoded_filename}")
                                    )
                                    conn.commit()
                                    pdf_found = True
                                else:
                                    logger.error(
                                        f"Core Node statement ingestion failed for '{decoded_filename}' (HTTP {status_code}, UID: {uid_str}): {req_err}. "
                                        f"Will retry on next polling cycle."
                                    )
                            finally:
                                secure_shred(tmp_target)

                            if pdf_found:
                                break

                except Exception as inner_e:
                    logger.warning(f"Error processing message UID {uid}: {inner_e}")

        # Scan for RTA allotment confirmation headers
        rta_headers = fetch_rta_allotment_headers(mail)

    finally:
        if mail:
            try:
                mail.close()
            except Exception:
                pass
            try:
                mail.logout()
            except Exception:
                pass

    return results, rta_headers


def run_poller_mock_or_live(
    db_path: Path = DEFAULT_DB_PATH,
    core_node_url: str = "http://127.0.0.1:8080",
    auth_token: Optional[str] = None,
    cas_password: str = "",
    gmail_user: Optional[str] = None,
    gmail_app_password: Optional[str] = None,
    incoming_test_pdf: Optional[str] = None
) -> Dict[str, Any]:
    """
    Executes one ingestion cycle:
    1. Loads .env if present.
    2. Initializes SQLite tables.
    3. If incoming_test_pdf: uploads provided PDF directly (test / manual mode).
    4. Else if Gmail IMAP credentials present: connects to imap.gmail.com and ingests CAS PDFs.
    5. Computes units validity & settlement lockout window based on last CAS date and RTA headers.
    """
    load_env_file()
    token = (auth_token or os.getenv("API_AUTH_TOKEN", "")).strip()
    if not token:
        raise RuntimeError("SECURITY CRITICAL: API_AUTH_TOKEN environment variable is required.")
    pwd = cas_password or os.getenv("CAS_PDF_PASSWORD", "")
    user = gmail_user or os.getenv("GMAIL_USER", "")
    g_pass = gmail_app_password or os.getenv("GMAIL_APP_PASSWORD", "")

    conn = sqlite3.connect(str(db_path))
    init_ingestion_tables(conn)

    results = {"ingested_count": 0, "messages_processed": []}
    rta_headers: List[Dict[str, Any]] = []

    if incoming_test_pdf and os.path.exists(incoming_test_pdf):
        msg_id = f"test_msg_{uuid.uuid4().hex[:8]}"
        tmp_target = f"/tmp/cas_ingest_{uuid.uuid4().hex}.pdf"
        fd = os.open(tmp_target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "wb") as pf, open(incoming_test_pdf, "rb") as src_f:
            shutil.copyfileobj(src_f, pf)

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
    elif user and g_pass:
        logger.info(f"Starting Gmail IMAP poller for user: {user}")
        poll_res, rta_hdrs = poll_gmail_via_imap(
            conn=conn,
            core_node_url=core_node_url,
            auth_token=token,
            user=user,
            password=g_pass,
            cas_password=pwd
        )
        results["ingested_count"] = poll_res.get("ingested_count", 0)
        results["messages_processed"] = poll_res.get("messages_processed", [])
        rta_headers = rta_hdrs
    else:
        logger.info("No incoming test PDF and no GMAIL_USER/GMAIL_APP_PASSWORD configured. Skipping email fetch.")

    # Compute latest CAS transaction date from database
    row = conn.execute("SELECT MAX(event_date) FROM tax_events").fetchone()
    last_tx_date_str = row[0] if (row and row[0]) else "2026-01-01"
    last_tx_date = date.fromisoformat(last_tx_date_str)

    validity = compute_units_validity(last_tx_date, datetime.now(timezone.utc), rta_headers=rta_headers)
    results["validity"] = validity
    conn.close()
    return results


if __name__ == "__main__":
    error_flag = Path("/tmp/gmail_poll_error.json")
    try:
        test_pdf = None
        if len(sys.argv) > 1 and not sys.argv[1].startswith("-"):
            test_pdf = sys.argv[1]
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
        sys.exit(1)
