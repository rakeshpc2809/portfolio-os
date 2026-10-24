import json
import re
import unittest
from pathlib import Path
from unittest.mock import patch

from mcp_server import (
    create_mcp_server,
    tool_get_guyton_klinger_fire_status,
    tool_get_portfolio_snapshot,
    tool_get_sec112a_harvest_candidates,
    tool_simulate_lumpsum_inflow,
    tool_simulate_trade_what_if,
    tool_verify_financial_narration,
)

FOLIO_PATTERN = re.compile(r"\b\d{6,12}/\d{2}\b|\bfolio(?:[_\s]*(?:no|num|number))?\b", re.IGNORECASE)
PAN_PATTERN = re.compile(r"\b[A-Z]{5}[0-9]{4}[A-Z]{1}\b")


class TestMcpServer(unittest.TestCase):

    def test_mcp_server_initialization(self):
        srv = create_mcp_server()
        self.assertIsNotNone(srv)

    def test_get_portfolio_snapshot_structure_and_zero_pii(self):
        res_str = tool_get_portfolio_snapshot()
        data = json.loads(res_str)
        self.assertEqual(data["status"], "SUCCESS")
        self.assertIn("portfolio_summary", data)
        self.assertIn("bucket_allocations", data)
        self.assertGreater(data["portfolio_summary"]["net_worth"], 0)

        # Zero PII Check
        self.assertIsNone(FOLIO_PATTERN.search(res_str))
        self.assertIsNone(PAN_PATTERN.search(res_str))

    def test_simulate_trade_what_if_fifo_and_tax(self):
        # PPFAS Flexi Cap
        res_str = tool_simulate_trade_what_if("INF879O01027", 100.0)
        data = json.loads(res_str)
        self.assertEqual(data["status"], "SUCCESS")
        self.assertEqual(data["asset_id"], "INF879O01027")
        self.assertEqual(data["units_sold"], 100.0)
        self.assertGreater(data["gross_proceeds"], 0)
        self.assertIn("lots_consumed", data)
        self.assertGreater(len(data["lots_consumed"]), 0)
        self.assertIn("resulting_bucket_drift", data)

        # Exceeding units should fail gracefully
        err_str = tool_simulate_trade_what_if("INF879O01027", 99999999.0)
        err_data = json.loads(err_str)
        self.assertEqual(err_data["status"], "ERROR")

    def test_simulate_lumpsum_inflow_conservation(self):
        amount = 100000.0
        res_str = tool_simulate_lumpsum_inflow(amount)
        data = json.loads(res_str)
        self.assertEqual(data["status"], "SUCCESS")
        self.assertEqual(data["inflow_amount"], amount)
        self.assertEqual(data["projected_net_worth"], data["current_net_worth"] + amount)
        allocs = data["recommended_allocations"]
        total_allocated = sum(a["allocated_amount"] for a in allocs)
        self.assertAlmostEqual(total_allocated, amount, delta=1.0)

    def test_get_sec112a_harvest_candidates(self):
        res_str = tool_get_sec112a_harvest_candidates()
        data = json.loads(res_str)
        self.assertEqual(data["status"], "SUCCESS")
        self.assertEqual(data["statutory_limit"], 125000.0)
        self.assertIn("actionable_harvest_plan", data)
        # All suggested harvests must be within statutory limit
        total_harvested = sum(p["exempt_capital_gain"] for p in data["actionable_harvest_plan"])
        self.assertLessEqual(total_harvested, 125000.0 + 1e-4)

    def test_get_guyton_klinger_fire_status(self):
        res_str = tool_get_guyton_klinger_fire_status(annual_expenses=600000.0)
        data = json.loads(res_str)
        self.assertEqual(data["status"], "SUCCESS")
        self.assertGreater(data["current_net_worth"], 0)
        self.assertIn("current_live_swr_pct", data)
        self.assertIn("capital_preservation_active", data)
        self.assertIn("prosperity_rule_active", data)
        self.assertIn("decumulation_guidance", data)

    def test_verify_financial_narration_truth_vs_hallucination(self):
        snapshot = json.loads(tool_get_portfolio_snapshot())
        true_nw = snapshot["portfolio_summary"]["net_worth"]
        true_gain_pct = snapshot["portfolio_summary"]["unrealized_gain_pct"]

        # Exact match should verify
        res_str = tool_verify_financial_narration(claimed_net_worth=true_nw, claimed_gain_pct=true_gain_pct)
        data = json.loads(res_str)
        self.assertTrue(data["verified"])
        self.assertEqual(data["discrepancies_found"], 0)

        # Hallucination (+10% drift) should be flagged
        fake_nw = true_nw * 1.10
        fake_res_str = tool_verify_financial_narration(claimed_net_worth=fake_nw)
        fake_data = json.loads(fake_res_str)
        self.assertFalse(fake_data["verified"])
        self.assertEqual(fake_data["discrepancies_found"], 1)
        self.assertIn("HALLUCINATION_DETECTED", fake_data["discrepancies"][0]["verdict"])


if __name__ == "__main__":
    unittest.main()
