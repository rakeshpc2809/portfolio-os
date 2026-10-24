import os
import unittest
from pathlib import Path
from quant.disclosure_parser import parse_amc_portfolio_disclosure

REPO_ROOT = Path(__file__).resolve().parent.parent.parent


class TestDisclosureParser(unittest.TestCase):

    def test_parse_ppfas_disclosure(self):
        ppfas_file = REPO_ROOT / "data" / "factsheets" / "ppfas_flexicap_full.xlsx"
        if not ppfas_file.exists():
            self.skipTest(f"Factsheet {ppfas_file} does not exist.")

        holdings = parse_amc_portfolio_disclosure(str(ppfas_file), fund_id="INF879O01027")
        self.assertTrue(len(holdings) > 20)
        first = holdings[0]
        self.assertIn("stock_symbol", first)
        self.assertIn("stock_isin", first)
        self.assertIn("weight_pct", first)
        self.assertTrue(first["stock_isin"].startswith("INE") or first["stock_isin"].startswith("US"))
        self.assertTrue(first["weight_pct"] > 0)

    def test_parse_nippon_smallcap_disclosure(self):
        nippon_file = REPO_ROOT / "data" / "factsheets" / "nippon_smallcap_full.xlsx"
        if not nippon_file.exists():
            self.skipTest(f"Factsheet {nippon_file} does not exist.")

        holdings = parse_amc_portfolio_disclosure(str(nippon_file), fund_id="INF204K01K15")
        self.assertTrue(len(holdings) > 30)
        for h in holdings:
            self.assertEqual(h["source_type"], "FACTSHEET_POI_PARSED")
            self.assertTrue(h["weight_pct"] > 0)


if __name__ == "__main__":
    unittest.main()
