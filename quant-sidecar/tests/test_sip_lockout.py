import unittest
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


if __name__ == "__main__":
    unittest.main()
