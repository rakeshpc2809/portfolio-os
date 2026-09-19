import unittest
try:
    from tui.daily_summary_helper import inr_format, build_summary_markup, load_hrp_audit
except ImportError:
    from daily_summary_helper import inr_format, build_summary_markup, load_hrp_audit

class TestDailySummaryHelper(unittest.TestCase):

    def test_inr_format(self):
        self.assertEqual(inr_format(100.0), "₹100.00")
        self.assertEqual(inr_format(125000.0), "₹1,25,000.00")
        self.assertEqual(inr_format(4875200.5), "₹48,75,200.50")
        self.assertEqual(inr_format(-24300.0), "-₹24,300.00")

    def test_build_summary_markup_with_empty_snapshot(self):
        markup = build_summary_markup({})
        self.assertIn("PORTFOLIO OS // DAILY EXECUTIVE BRIEF", markup)
        self.assertIn("EXECUTIVE HEALTH", markup)
        self.assertIn("REBALANCE CORRIDORS", markup)
        self.assertIn("HRP CVaR ADVISORY WEIGHTS", markup)
        self.assertIn("TAX & RETIREMENT MILESTONES", markup)

    def test_build_summary_markup_with_hrp_data(self):
        mock_snapshot = {
            "sync_info": {
                "current_value": 4875200.0,
                "total_invested": 3650000.0,
                "formatted_unrealized_gain": "+₹12,25,200.00",
                "portfolio_xirr": "18.42%"
            },
            "rebalance_plan": {
                "buy_side": {
                    "total_to_invest": 0.0,
                    "buckets": [
                        {"bucket": "EQUITY_CORE", "current_pct": 48.0, "target_pct": 50.0},
                        {"bucket": "EQUITY_SATELLITE", "current_pct": 32.0, "target_pct": 30.0},
                        {"bucket": "GOLD_SILVER", "current_pct": 9.0, "target_pct": 10.0},
                        {"bucket": "LIQUID_BUFFER", "current_pct": 11.0, "target_pct": 10.0}
                    ]
                },
                "sell_side": {
                    "tax_summary": {
                        "total_ltcg_taxable_realized": 45000.0,
                        "exemption_headroom_after": 80000.0
                    }
                }
            }
        }
        mock_hrp = {
            "status": "SUCCESS",
            "trading_days": 432,
            "start_date": "2024-12-11",
            "end_date": "2026-09-06",
            "allocations": [
                {"isin": "INF109KC12U0", "name": "ICICI LargeMidcap 250", "intra_bucket_hrp_pct": 37.44, "intra_bucket_target_pct": 60.0, "hrp_pct": 18.72},
                {"isin": "INF879O01027", "name": "Parag Parikh Flexi Cap", "intra_bucket_hrp_pct": 62.56, "intra_bucket_target_pct": 40.0, "hrp_pct": 31.28},
                {"isin": "INF109KC13X2", "name": "ICICI Value 30", "intra_bucket_hrp_pct": 25.05, "intra_bucket_target_pct": 33.33, "hrp_pct": 7.51},
                {"isin": "INF754K01TN5", "name": "Edelweiss Momentum 50", "intra_bucket_hrp_pct": 46.50, "intra_bucket_target_pct": 33.33, "hrp_pct": 13.95},
                {"isin": "INF204K01K15", "name": "Nippon Small Cap", "intra_bucket_hrp_pct": 28.45, "intra_bucket_target_pct": 33.34, "hrp_pct": 8.54}
            ]
        }
        markup = build_summary_markup(mock_snapshot, mock_hrp)
        self.assertIn("₹48,75,200.00", markup)
        self.assertIn("18.42%", markup)
        self.assertIn("NO_OP", markup)
        self.assertIn("Core Bucket (50% Policy)", markup)
        self.assertIn("37.44%", markup)
        self.assertIn("62.56%", markup)
        self.assertIn("Satellite Bucket (30% Policy)", markup)
        self.assertIn("46.50%", markup)
        self.assertIn("₹80,000.00", markup)

    def test_build_summary_markup_with_days_to_ltcg(self):
        mock_snapshot = {
            "sync_info": {"current_value": 100000.0, "total_invested": 90000.0},
            "rebalance_plan": {
                "sell_side": {
                    "tax_summary": {"total_ltcg_taxable_realized": 0.0, "exemption_headroom_after": 100000.0}
                }
            },
            "tax_lots": [
                {
                    "fund_name": "Nippon India Gold ETF",
                    "tax_term": "STCG",
                    "holding_days": 400,
                    "days_to_ltcg": 330,
                    "is_long_term": False
                }
            ]
        }
        markup = build_summary_markup(mock_snapshot)
        self.assertIn("Nippon India Gold ETF", markup)
        self.assertIn("330 days remaining", markup)

    def test_build_summary_markup_fallback_to_tiers_when_tax_lots_absent(self):
        mock_snapshot = {
            "sync_info": {"current_value": 100000.0, "total_invested": 90000.0},
            "rebalance_plan": {
                "sell_side": {
                    "tax_summary": {"total_ltcg_taxable_realized": 0.0, "exemption_headroom_after": 100000.0},
                    "waterfall": [
                        {
                            "tier": "CORE_FUND",
                            "lots": [
                                {
                                    "fundName": "HDFC Balanced Advantage Fund",
                                    "taxTerm": "STCG",
                                    "daysToLtcg": 45
                                }
                            ]
                        }
                    ]
                }
            }
        }
        markup = build_summary_markup(mock_snapshot)
        self.assertIn("HDFC Balanced Advantage Fund", markup)
        self.assertIn("45 days remaining", markup)

if __name__ == "__main__":
    unittest.main()
