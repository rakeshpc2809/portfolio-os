import unittest
import unittest.mock
from datetime import datetime, date, timedelta, timezone
from pathlib import Path
import tempfile
import yaml

from scripts.gmail_cas_poller import (
    advance_business_days,
    is_business_day,
    find_next_sip_date,
    compute_units_validity,
    secure_shred
)


class TestSipLockoutAndRtaHeader(unittest.TestCase):

    def setUp(self):
        self.holidays = {
            "2026-01-26",
            "2026-03-06",
            "2026-03-25",
            "2026-04-03",
            "2026-04-14",
            "2026-05-01",
            "2026-08-15",
            "2026-10-02"
        }

    def test_advance_business_days_skips_weekends_and_holidays(self):
        # Wednesday, 2026-01-21
        # Thu (22), Fri (23), Sat (24, skip), Sun (25, skip), Mon (26, Republic Day holiday, skip), Tue (27, day 3), Wed (28, day 4)
        start = date(2026, 1, 21)
        res = advance_business_days(start, 4, self.holidays)
        self.assertEqual(res, date(2026, 1, 28))

    def test_find_next_sip_date(self):
        sips = [
            {"isin": "INF109KC12U0", "sip_day_of_month": 10},
            {"isin": "INF879O01027", "sip_day_of_month": 15},
            {"isin": "INF204K01K15", "sip_day_of_month": 20}
        ]
        # Current date: 2026-10-12 -> next upcoming SIP should be 15th
        cur = date(2026, 10, 12)
        next_sip = find_next_sip_date(sips, cur)
        self.assertEqual(next_sip, date(2026, 10, 15))

        # Current date: 2026-10-25 -> next upcoming SIP should wrap to next month: Nov 10th
        cur2 = date(2026, 10, 25)
        next_sip2 = find_next_sip_date(sips, cur2)
        self.assertEqual(next_sip2, date(2026, 11, 10))

    def test_rta_allotment_header_within_24h_grace_window_remains_healthy(self):
        # Current time: 2026-10-16 14:00 UTC
        cur_time = datetime(2026, 10, 16, 14, 0, tzinfo=timezone.utc)
        last_cas_date = date(2026, 10, 10)  # Old CAS

        # RTA email received 10 hours ago: 2026-10-16 04:00 UTC (newer than last_cas_date)
        rta_headers = [{
            "from": "donotreply@camsonline.com",
            "subject": "Allotment confirmation for your purchase",
            "received_at": cur_time - timedelta(hours=10)
        }]

        # Create temporary mock configs
        with tempfile.NamedTemporaryFile("w+", suffix=".yaml", delete=False) as f_sip, \
             tempfile.NamedTemporaryFile("w+", suffix=".yaml", delete=False) as f_hol:
            yaml.dump({"sips": [{"isin": "INF109KC12U0", "sip_day_of_month": 20}]}, f_sip)
            yaml.dump({"holidays": []}, f_hol)
            sip_p, hol_p = Path(f_sip.name), Path(f_hol.name)

        try:
            res = compute_units_validity(
                last_cas_tx_date=last_cas_date,
                current_time=cur_time,
                rta_headers=rta_headers,
                sip_config_path=sip_p,
                holidays_config_path=hol_p
            )
            # Within 24-hour grace window, status remains HEALTHY
            self.assertEqual(res["status"], "HEALTHY")
        finally:
            sip_p.unlink(missing_ok=True)
            hol_p.unlink(missing_ok=True)

    def test_rta_allotment_header_older_than_24h_flags_stale(self):
        cur_time = datetime(2026, 10, 16, 14, 0, tzinfo=timezone.utc)
        last_cas_date = date(2026, 10, 10)

        # RTA email received 30 hours ago: 2026-10-15 08:00 UTC (age >= 24h)
        rta_headers = [{
            "from": "samfS@kfintech.com",
            "subject": "Purchase Transaction Allotment Advice",
            "received_at": cur_time - timedelta(hours=30)
        }]

        with tempfile.NamedTemporaryFile("w+", suffix=".yaml", delete=False) as f_sip, \
             tempfile.NamedTemporaryFile("w+", suffix=".yaml", delete=False) as f_hol:
            yaml.dump({"sips": [{"isin": "INF109KC12U0", "sip_day_of_month": 20}]}, f_sip)
            yaml.dump({"holidays": []}, f_hol)
            sip_p, hol_p = Path(f_sip.name), Path(f_hol.name)

        try:
            res = compute_units_validity(
                last_cas_tx_date=last_cas_date,
                current_time=cur_time,
                rta_headers=rta_headers,
                sip_config_path=sip_p,
                holidays_config_path=hol_p
            )
            # Exceeded 24h grace window -> must flag UNITS_STALE_REQUEST_CAS
            self.assertEqual(res["status"], "UNITS_STALE_REQUEST_CAS")
            self.assertEqual(res["units_valid_until_date"], "2026-10-15")
        finally:
            sip_p.unlink(missing_ok=True)
            hol_p.unlink(missing_ok=True)

    def test_clears_stale_flag_when_cas_ingests_allotment_date(self):
        cur_time = datetime(2026, 10, 16, 14, 0, tzinfo=timezone.utc)
        # CAS has now been updated to 2026-10-15 (matching or newer than the allotment email date)
        updated_cas_date = date(2026, 10, 15)

        rta_headers = [{
            "from": "samfS@kfintech.com",
            "subject": "Purchase Transaction Allotment Advice",
            "received_at": cur_time - timedelta(hours=30)  # Received on 2026-10-15
        }]

        with tempfile.NamedTemporaryFile("w+", suffix=".yaml", delete=False) as f_sip, \
             tempfile.NamedTemporaryFile("w+", suffix=".yaml", delete=False) as f_hol:
            yaml.dump({"sips": [{"isin": "INF109KC12U0", "sip_day_of_month": 20}]}, f_sip)
            yaml.dump({"holidays": []}, f_hol)
            sip_p, hol_p = Path(f_sip.name), Path(f_hol.name)

        try:
            res = compute_units_validity(
                last_cas_tx_date=updated_cas_date,
                current_time=cur_time,
                rta_headers=rta_headers,
                sip_config_path=sip_p,
                holidays_config_path=hol_p
            )
            # CAS is >= allotment email date -> flag clears back to HEALTHY!
            self.assertEqual(res["status"], "HEALTHY")
        finally:
            sip_p.unlink(missing_ok=True)
            hol_p.unlink(missing_ok=True)

    def test_secure_shred_erases_and_deletes_file(self):
        with tempfile.NamedTemporaryFile("w+", delete=False) as f:
            f.write("CONFIDENTIAL_CAS_PAN_NUMBER_XYZ1234567")
            path = f.name

        self.assertTrue(Path(path).exists())
        secure_shred(path)
        self.assertFalse(Path(path).exists())


class TestImapPolling(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.TemporaryDirectory()
        self.db_path = Path(self.tmp_dir.name) / "test_ledger.db"
        import sqlite3
        conn = sqlite3.connect(str(self.db_path))
        from scripts.gmail_cas_poller import init_ingestion_tables
        init_ingestion_tables(conn)
        conn.close()

    def tearDown(self):
        self.tmp_dir.cleanup()

    def test_decode_mime_header(self):
        from scripts.gmail_cas_poller import decode_mime_header
        raw = "=?UTF-8?B?Q0FTIFN0YXRlbWVudA==?="
        self.assertEqual(decode_mime_header(raw), "CAS Statement")
        self.assertEqual(decode_mime_header("Simple String"), "Simple String")
        self.assertEqual(decode_mime_header(None), "")

    @unittest.mock.patch("imaplib.IMAP4_SSL")
    def test_connect_gmail_imap(self, mock_imap_cls):
        from scripts.gmail_cas_poller import connect_gmail_imap
        mock_instance = unittest.mock.MagicMock()
        mock_imap_cls.return_value = mock_instance

        # Test space stripping and port 993
        mail = connect_gmail_imap("test@gmail.com", "abcd efgh ijkl mnop")
        mock_imap_cls.assert_called_once_with("imap.gmail.com", 993)
        mock_instance.login.assert_called_once_with("test@gmail.com", "abcdefghijklmnop")
        self.assertEqual(mail, mock_instance)

    @unittest.mock.patch("scripts.gmail_cas_poller.ingest_pdf_to_core")
    @unittest.mock.patch("scripts.gmail_cas_poller.connect_gmail_imap")
    def test_poll_gmail_via_imap_downloads_and_deduplicates(self, mock_connect, mock_ingest):
        import sqlite3
        from email.mime.multipart import MIMEMultipart
        from email.mime.application import MIMEApplication
        from email.mime.text import MIMEText
        from scripts.gmail_cas_poller import poll_gmail_via_imap

        mock_mail = unittest.mock.MagicMock()
        mock_connect.return_value = mock_mail
        mock_mail.select.return_value = ("OK", [b"1"])
        mock_mail.uid.side_effect = [
            ("OK", [b"101"]),  # CAS UID search
            # UID header fetch
            ("OK", [(b"101 (BODY[HEADER.FIELDS (MESSAGE-ID DATE FROM SUBJECT)] {10}\r\n", b"Message-ID: <test-cas-msg-001@cams.com>\r\nSubject: CAS Statement\r\nDate: Wed, 21 Oct 2026 10:00:00 +0530\r\n\r\n")]),
            # UID full body fetch
            ("OK", [(b"101 (RFC822 {10}\r\n", None)]), # Will be filled below
            ("OK", [b""])      # RTA UID search
        ]

        # Build mock email with PDF attachment
        msg = MIMEMultipart()
        msg["Message-ID"] = "<test-cas-msg-001@cams.com>"
        msg["Subject"] = "Consolidated Account Statement - October 2026"
        msg["Date"] = "Wed, 21 Oct 2026 10:00:00 +0530"
        msg["From"] = "donotreply@camsonline.com"
        msg.attach(MIMEText("Dear Investor, please find your CAS attached."))

        pdf_part = MIMEApplication(b"%PDF-1.4 mock pdf content", _subtype="pdf")
        pdf_part.add_header("Content-Disposition", "attachment", filename="cams_cas.pdf")
        msg.attach(pdf_part)
        raw_email_bytes = msg.as_bytes()

        hdr_data = (b"101 (BODY[HEADER.FIELDS (MESSAGE-ID DATE FROM SUBJECT)] {%d}\r\n" % len(raw_email_bytes), raw_email_bytes)
        body_data = (b"101 (RFC822 {%d}\r\n" % len(raw_email_bytes), raw_email_bytes)

        mock_mail.uid.side_effect = [
            ("OK", [b"101"]),   # CAS UID search
            ("OK", [hdr_data]), # UID header fetch
            ("OK", [body_data]),# UID body fetch
            ("OK", [b""])       # RTA search
        ]
        mock_ingest.return_value = {"status": "SUCCESS", "ingested_rows": 12}

        conn = sqlite3.connect(str(self.db_path))
        results, rta_headers = poll_gmail_via_imap(
            conn=conn,
            core_node_url="http://127.0.0.1:8080",
            auth_token="dev_token",
            user="user@gmail.com",
            password="pwd"
        )

        self.assertEqual(results["ingested_count"], 1)
        self.assertEqual(len(results["messages_processed"]), 1)
        self.assertEqual(results["messages_processed"][0]["message_id"], "test-cas-msg-001@cams.com")
        self.assertEqual(results["messages_processed"][0]["uid"], "101")
        mock_ingest.assert_called_once()

        # Check that it was inserted into SQLite
        cur = conn.cursor()
        cur.execute("SELECT message_id, file_name FROM ingested_cas_emails")
        rows = cur.fetchall()
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0][0], "test-cas-msg-001@cams.com")

        # Run poller a second time - verify idempotency / deduplication
        mock_mail.uid.side_effect = [
            ("OK", [b"101"]),   # CAS UID search
            ("OK", [hdr_data]), # header fetched
            ("OK", [b""])       # RTA search
        ]
        mock_ingest.reset_mock()

        results2, _ = poll_gmail_via_imap(
            conn=conn,
            core_node_url="http://127.0.0.1:8080",
            auth_token="dev_token",
            user="user@gmail.com",
            password="pwd"
        )
        self.assertEqual(results2["ingested_count"], 0)
        mock_ingest.assert_not_called()
        conn.close()

    def test_fetch_rta_allotment_headers_with_uid(self):
        from scripts.gmail_cas_poller import fetch_rta_allotment_headers
        mock_mail = unittest.mock.MagicMock()
        mock_mail.uid.side_effect = [
            ("OK", [b"501 502"]), # RTA UID search
            ("OK", [(b"502 (BODY[HEADER.FIELDS] {10}\r\n", b"Date: Thu, 22 Oct 2026 14:00:00 +0530\r\nSubject: Allotment Advice\r\nFrom: donotreply@camsonline.com\r\n\r\n")]),
            ("OK", [(b"501 (BODY[HEADER.FIELDS] {10}\r\n", b"Date: Wed, 21 Oct 2026 11:00:00 +0530\r\nSubject: SIP Purchase\r\nFrom: samfS@kfintech.com\r\n\r\n")])
        ]

        headers = fetch_rta_allotment_headers(mock_mail)
        self.assertEqual(len(headers), 2)
        self.assertEqual(headers[0]["uid"], "502")
        self.assertEqual(headers[0]["subject"], "Allotment Advice")
        self.assertEqual(headers[1]["uid"], "501")


if __name__ == "__main__":
    unittest.main()

