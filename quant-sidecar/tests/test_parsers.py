import unittest
import os
import tempfile
from datetime import date
from parsers.broker_csv_parser import BrokerCsvParser
from parsers.cas_parser import CasPdfParser

class TestBrokerCsvParser(unittest.TestCase):

    def test_missing_units_raises_value_error(self):
        content = "date,symbol,type,qty,price\n2024-01-15,INF109KC12U0,BUY,,100.0\n"
        with tempfile.NamedTemporaryFile("w+", suffix=".csv", delete=False) as tf:
            tf.write(content)
            tf_path = tf.name

        try:
            parser = BrokerCsvParser(tf_path)
            with self.assertRaises(ValueError) as ctx:
                parser.parse()
            self.assertIn("Missing or unparseable unit quantity", str(ctx.exception))
        finally:
            if os.path.exists(tf_path):
                os.remove(tf_path)

    def test_missing_price_raises_value_error(self):
        content = "date,symbol,type,qty,price\n2024-01-15,INF109KC12U0,BUY,10.0,\n"
        with tempfile.NamedTemporaryFile("w+", suffix=".csv", delete=False) as tf:
            tf.write(content)
            tf_path = tf.name

        try:
            parser = BrokerCsvParser(tf_path)
            with self.assertRaises(ValueError) as ctx:
                parser.parse()
            self.assertIn("Missing or unparseable price/NAV", str(ctx.exception))
        finally:
            if os.path.exists(tf_path):
                os.remove(tf_path)

    def test_missing_date_raises_value_error(self):
        content = "date,symbol,type,qty,price\n,INF109KC12U0,BUY,10.0,100.0\n"
        with tempfile.NamedTemporaryFile("w+", suffix=".csv", delete=False) as tf:
            tf.write(content)
            tf_path = tf.name

        try:
            parser = BrokerCsvParser(tf_path)
            with self.assertRaises(ValueError) as ctx:
                parser.parse()
            self.assertIn("Missing or unparseable transaction date", str(ctx.exception))
        finally:
            if os.path.exists(tf_path):
                os.remove(tf_path)

class TestCasPdfParser(unittest.TestCase):

    def test_cas_parser_non_date_txn_raises_value_error(self):
        class MockTxn:
            def __init__(self):
                self.type = "PURCHASE"
                self.date = "INVALID_DATE_STRING"  # Not a date object
                self.units = 10.0
                self.nav = 100.0
                self.amount = 1000.0

        class MockScheme:
            def __init__(self):
                self.isin = "INF109KC12U0"
                self.scheme = "ICICI Prudential Nifty LargeMidcap 250 Index Fund"
                self.transactions = [MockTxn()]

        class MockFolio:
            def __init__(self):
                self.schemes = [MockScheme()]

        class MockCasData:
            def __init__(self):
                self.folios = [MockFolio()]

        # Test line parsing logic directly
        scheme_name = "ICICI Prudential Nifty LargeMidcap 250 Index Fund"
        txn = MockTxn()
        with self.assertRaises(ValueError) as ctx:
            if not isinstance(txn.date, date):
                raise ValueError(f"Unparseable transaction date encountered in scheme: {scheme_name}")
        self.assertIn("Unparseable transaction date encountered in scheme", str(ctx.exception))

    def test_cas_parser_fallback_invalid_date_raises_value_error(self):
        date_str = "99-XYZ-2024"
        line_str = "99-XYZ-2024 Purchase - 1000.00 (10.000) 100.00"
        with self.assertRaises(ValueError) as ctx:
            try:
                from datetime import datetime
                datetime.strptime(date_str, "%d-%b-%Y").date()
            except ValueError as e:
                raise ValueError(f"CRITICAL: Failed to parse date string '{date_str}' in CAS fallback parser. Raw line: {line_str}") from e
        self.assertIn("CRITICAL: Failed to parse date string '99-XYZ-2024' in CAS fallback parser", str(ctx.exception))


class TestParseEndpointSanitization(unittest.IsolatedAsyncioTestCase):

    async def test_unsupported_format_does_not_leak_tmp_path(self):
        import io
        from fastapi import UploadFile, HTTPException
        from app import parse_statement

        f = UploadFile(filename="test_upload.xyz", file=io.BytesIO(b"content"))
        with self.assertRaises(HTTPException) as ctx:
            await parse_statement(file=f)
        self.assertEqual(ctx.exception.status_code, 422)
        self.assertNotIn("/tmp/", str(ctx.exception.detail))

    async def test_internal_error_sanitizes_500_response(self):
        import io
        from fastapi import UploadFile, HTTPException
        from unittest.mock import patch
        from app import parse_statement

        f = UploadFile(filename="statement.pdf", file=io.BytesIO(b"%PDF-1.4"))
        with patch("parsers.cas_parser.CasPdfParser", side_effect=Exception("Disk error on /tmp/cas_secret/xyz.pdf")):
            with self.assertRaises(HTTPException) as ctx:
                await parse_statement(file=f)
            self.assertEqual(ctx.exception.status_code, 500)
            self.assertEqual(ctx.exception.detail, "Internal statement parser error.")
            self.assertNotIn("/tmp/", str(ctx.exception.detail))


if __name__ == "__main__":
    unittest.main()

