import unittest
import json
from scripts.sheets_snapshot_publisher import (
    scan_for_pii,
    sanitize_holding_item,
    PiiViolationException,
    ALLOWED_HOLDING_FIELDS
)

class TestSheetsPiiGuard(unittest.TestCase):

    def test_high_precision_numbers_and_timestamps_do_not_trigger_pii_violation(self):
        """
        Fix #4 Verification:
        Confirm that high-precision decimal unit balances (e.g. 50996.30456789)
        and unix millisecond timestamps (e.g. 1730000000000) do NOT trigger false-positive
        PII violations, since \\b\\d{9,18}\\b was deliberately removed.
        """
        payload = {
            "isin": "INF109KC12U0",
            "fund_name": "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            "total_units": 50996.30456789,
            "timestamp_ms": 1730000000000,
            "large_id_number": 987654321012345,
            "as_of_date": "2026-10-23T16:14:04.308581Z"
        }
        text = json.dumps(payload)
        violations = scan_for_pii(text)
        self.assertEqual(len(violations), 0, f"False-positive PII detected on valid numbers: {violations}")

    def test_real_pan_triggers_pii_violation(self):
        """Confirm that a 10-character Indian PAN pattern [A-Z]{5}[0-9]{4}[A-Z]{1} is strictly detected."""
        text = json.dumps({"note": "Investor PAN is ABCDE1234F"})
        violations = scan_for_pii(text)
        self.assertGreater(len(violations), 0)
        self.assertTrue(any("PAN detected" in v for v in violations))

    def test_email_triggers_pii_violation(self):
        """Confirm that email addresses are strictly detected."""
        text = json.dumps({"contact": "user.investor@gmail.com"})
        violations = scan_for_pii(text)
        self.assertGreater(len(violations), 0)
        self.assertTrue(any("Email address detected" in v for v in violations))

    def test_folio_pattern_triggers_pii_violation(self):
        """Confirm that standard AMC folio numbers like 12345678/90 or 'folio' text are strictly detected."""
        text1 = json.dumps({"account": "12345678/90"})
        violations1 = scan_for_pii(text1)
        self.assertGreater(len(violations1), 0)
        self.assertTrue(any("Folio pattern detected" in v for v in violations1))

        text2 = json.dumps({"details": "Folio No: 9102345"})
        violations2 = scan_for_pii(text2)
        self.assertGreater(len(violations2), 0)
        self.assertTrue(any("Folio pattern detected" in v for v in violations2))

    def test_sanitize_holding_item_enforces_allowlist(self):
        """Confirm that sanitize_holding_item strips any un-allowlisted or sensitive fields."""
        raw_holding = {
            "isin": "INF879O01018",
            "fund_name": "Parag Parikh Flexi Cap Fund",
            "asset_bucket": "Core Equity",
            "total_units": 100.5555,
            "exit_load_free_units": 80.0,
            "locked_units": 20.5555,
            "avg_cost": 50.1234,
            "current_nav": 75.5,
            "peak_52w_nav": 80.0,
            "drawdown_from_peak_pct": -5.63,
            "current_value": 7591.94,
            "invested_value": 5040.16,
            "xirr": 18.5,
            "expense_ratio": 0.65,
            # Injected sensitive fields that MUST be stripped:
            "folio_no": "99988877/11",
            "pan": "ABCDE1234F",
            "investor_email": "test@domain.com"
        }
        sanitized = sanitize_holding_item(raw_holding, "2026-10-23")
        self.assertEqual(set(sanitized.keys()), set(ALLOWED_HOLDING_FIELDS))
        self.assertNotIn("folio_no", sanitized)
        self.assertNotIn("pan", sanitized)
        self.assertNotIn("investor_email", sanitized)

        # Scanning sanitized output must produce 0 violations
        self.assertEqual(len(scan_for_pii(json.dumps(sanitized))), 0)

if __name__ == "__main__":
    unittest.main()
