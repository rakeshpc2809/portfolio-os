import unittest
import os
import tempfile
from parsers.broker_csv_parser import BrokerCsvParser

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

if __name__ == "__main__":
    unittest.main()
