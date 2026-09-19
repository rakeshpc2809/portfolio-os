import unittest
import asyncio
import polars as pl
import pandas as pd
from fastapi import HTTPException
from skfolio import RiskMeasure

from quant.hrp_allocator import (
    align_returns,
    optimize_weights,
    run_hrp_allocation,
    HrpAllocationRequest,
    HrpAllocationResponse
)
from app import allocate_hrp, verify_auth_token

class TestHrpAllocator(unittest.TestCase):

    def test_strict_shared_calendar_alignment_drops_missing_dates(self):
        # Create synthetic NAVs where Fund B is missing on 2026-01-03
        data = [
            {"isin": "F1", "nav_date": "2026-01-01", "nav_value": 100.0},
            {"isin": "F2", "nav_date": "2026-01-01", "nav_value": 50.0},
            {"isin": "F1", "nav_date": "2026-01-02", "nav_value": 101.0},
            {"isin": "F2", "nav_date": "2026-01-02", "nav_value": 51.0},
            {"isin": "F1", "nav_date": "2026-01-03", "nav_value": 102.0}, # F2 missing on this date
            {"isin": "F1", "nav_date": "2026-01-04", "nav_value": 103.0},
            {"isin": "F2", "nav_date": "2026-01-04", "nav_value": 52.0},
        ]
        df = pl.DataFrame(data).with_columns(pl.col("nav_date").str.to_date())

        returns_pl, start_d, end_d, t_days = align_returns(df, ["F1", "F2"])

        # 2026-01-03 must be dropped entirely. Surviving dates: Jan 01, Jan 02, Jan 04 (3 dates -> 2 return observations)
        self.assertEqual(t_days, 2)
        self.assertEqual(start_d, "2026-01-01")
        self.assertEqual(end_d, "2026-01-04")
        
        # Ensure zero-return days were NOT fabricated
        f1_rets = returns_pl["F1"].to_list()
        f2_rets = returns_pl["F2"].to_list()
        self.assertNotIn(0.0, f1_rets)
        self.assertNotIn(0.0, f2_rets)

    def test_n2_core_bucket_optimization_via_risk_budgeting(self):
        # 2 assets must use RiskBudgeting(CVAR) and NOT crash on empty clustering range
        dates = pd.date_range("2025-01-01", periods=200, freq="B")
        df_rets = pd.DataFrame({
            "A": [0.001 * (i % 5 - 2) for i in range(200)],
            "B": [0.002 * (i % 7 - 3) for i in range(200)]
        }, index=dates)

        weights = optimize_weights(df_rets, RiskMeasure.CVAR)
        self.assertEqual(len(weights), 2)
        self.assertAlmostEqual(sum(weights), 1.0, places=4)
        self.assertGreater(weights[0], 0.0)
        self.assertGreater(weights[1], 0.0)

    def test_n3_satellite_bucket_optimization_via_hrp(self):
        # 3 assets must use HierarchicalRiskParity(CVAR)
        dates = pd.date_range("2025-01-01", periods=200, freq="B")
        df_rets = pd.DataFrame({
            "A": [0.001 * (i % 5 - 2) for i in range(200)],
            "B": [0.002 * (i % 7 - 3) for i in range(200)],
            "C": [0.0015 * (i % 3 - 1) for i in range(200)]
        }, index=dates)

        weights = optimize_weights(df_rets, RiskMeasure.CVAR)
        self.assertEqual(len(weights), 3)
        self.assertAlmostEqual(sum(weights), 1.0, places=4)
        for w in weights:
            self.assertGreater(w, 0.0)

    def test_insufficient_history_guard(self):
        # When lookback is less than 126 trading days, allocator must refuse to run
        resp = run_hrp_allocation(mode="INTRA_BUCKET", lookback_days=50, dump_cache=False)
        self.assertEqual(resp.status, "INSUFFICIENT_HISTORY")
        self.assertIn("Insufficient aligned trading days", resp.message)

    def test_run_hrp_allocation_intra_bucket_live(self):
        resp = run_hrp_allocation(mode="INTRA_BUCKET", dump_cache=False)
        self.assertEqual(resp.status, "SUCCESS")
        self.assertEqual(resp.mode, "INTRA_BUCKET")
        self.assertGreaterEqual(resp.trading_days, 420)
        self.assertGreaterEqual(resp.cvar_tail_observations, 21.0)
        self.assertEqual(len(resp.allocations), 7)

        # Full portfolio weights must sum to exactly 100.0%
        total_hrp = sum(a.hrp_pct for a in resp.allocations)
        self.assertEqual(round(total_hrp, 2), 100.0)

        # Verify fallback and data source transparency
        self.assertIn(resp.data_source, ["DUCKDB_LIVE", "PARQUET_STATIC"])
        self.assertIsNotNone(resp.data_as_of_date)
        if resp.data_source == "DUCKDB_LIVE":
            self.assertFalse(resp.is_fallback)
        else:
            self.assertTrue(resp.is_fallback)

        # Intra-bucket Core weights (N=2 RiskBudgeting CVaR): LargeMid ~37.5%, PPFAS ~62.5%
        core_allocs = {a.isin: a for a in resp.allocations if a.bucket == "CORE"}
        self.assertIn("INF109KC12U0", core_allocs)
        self.assertIn("INF879O01027", core_allocs)
        self.assertAlmostEqual(core_allocs["INF109KC12U0"].intra_bucket_hrp_pct, 37.47, delta=1.5)
        self.assertAlmostEqual(core_allocs["INF879O01027"].intra_bucket_hrp_pct, 62.53, delta=1.5)
        # Verify algebraic residual plug guarantees exact 100.0% intra-bucket sum
        self.assertEqual(round(sum(a.intra_bucket_hrp_pct for a in core_allocs.values()), 2), 100.0)
        self.assertEqual(round(sum(a.intra_bucket_target_pct for a in core_allocs.values()), 2), 100.0)
        self.assertEqual(round(sum(a.hrp_pct for a in core_allocs.values()), 2), 50.0)

        # Intra-bucket Satellite weights (N=3 HRP CVaR): Value ~25.5%, Momentum ~45.5%, SmallCap ~29.0%
        sat_allocs = {a.isin: a for a in resp.allocations if a.bucket == "SATELLITE"}
        self.assertIn("INF109KC13X2", sat_allocs)
        self.assertIn("INF754K01TN5", sat_allocs)
        self.assertIn("INF204K01K15", sat_allocs)
        self.assertAlmostEqual(sat_allocs["INF109KC13X2"].intra_bucket_hrp_pct, 25.50, delta=2.5)
        self.assertAlmostEqual(sat_allocs["INF754K01TN5"].intra_bucket_hrp_pct, 45.50, delta=2.5)
        self.assertAlmostEqual(sat_allocs["INF204K01K15"].intra_bucket_hrp_pct, 29.00, delta=2.5)
        # Verify algebraic residual plug guarantees exact 100.0% intra-bucket sum
        self.assertEqual(round(sum(a.intra_bucket_hrp_pct for a in sat_allocs.values()), 2), 100.0)
        self.assertEqual(round(sum(a.intra_bucket_target_pct for a in sat_allocs.values()), 2), 100.0)
        self.assertEqual(round(sum(a.hrp_pct for a in core_allocs.values()), 2), 50.0)

        # Bucket summaries match macro policy targets exactly
        self.assertEqual(resp.bucket_summary["CORE"].target_pct, 50.0)
        self.assertEqual(resp.bucket_summary["CORE"].hrp_pct, 50.0)
        self.assertEqual(resp.bucket_summary["CORE"].drift_pct, 0.0)
        self.assertEqual(resp.bucket_summary["SATELLITE"].target_pct, 30.0)
        self.assertEqual(resp.bucket_summary["SATELLITE"].hrp_pct, 30.0)
        self.assertEqual(resp.bucket_summary["SATELLITE"].drift_pct, 0.0)
        self.assertEqual(resp.bucket_summary["GOLD_SILVER"].target_pct, 10.0)
        self.assertEqual(resp.bucket_summary["LIQUID_BUFFER"].target_pct, 10.0)

    def test_hrp_allocation_fallback_mode_when_duckdb_locked(self):
        from unittest.mock import patch
        import duckdb
        with patch("duckdb.connect", side_effect=duckdb.IOException("Lock conflict simulating core-node holding flock")):
            resp = run_hrp_allocation(mode="INTRA_BUCKET", dump_cache=False)
            self.assertEqual(resp.status, "SUCCESS")
            self.assertTrue(resp.is_fallback)
            self.assertEqual(resp.data_source, "PARQUET_STATIC")
            self.assertIsNotNone(resp.data_as_of_date)
            self.assertEqual(len(resp.allocations), 7)
            self.assertEqual(round(sum(a.hrp_pct for a in resp.allocations), 2), 100.0)

    def test_run_hrp_allocation_equity_universe_live(self):
        resp = run_hrp_allocation(mode="EQUITY_UNIVERSE", dump_cache=False)
        self.assertEqual(resp.status, "SUCCESS")
        self.assertEqual(resp.mode, "EQUITY_UNIVERSE")
        self.assertEqual(len(resp.allocations), 5)

        total_hrp = sum(a.hrp_pct for a in resp.allocations)
        self.assertEqual(round(total_hrp, 2), 100.0)
        self.assertEqual(round(sum(a.target_pct for a in resp.allocations), 2), 100.0)

        # Renormalized targets onto 80% equity universe
        self.assertEqual(resp.bucket_summary["CORE"].target_pct, 62.50)
        self.assertEqual(resp.bucket_summary["SATELLITE"].target_pct, 37.50)

    def test_app_route_handler_and_auth_gate(self):
        from unittest.mock import patch
        with patch.dict("os.environ", {"API_AUTH_TOKEN": "test_secret_key"}):
            # 1. Test unauthenticated request raises 401
            with self.assertRaises(HTTPException) as ctx:
                verify_auth_token(None)
            self.assertEqual(ctx.exception.status_code, 401)

            # 2. Test invalid token raises 401
            with self.assertRaises(HTTPException) as ctx_inv:
                verify_auth_token("wrong_token_xyz")
            self.assertEqual(ctx_inv.exception.status_code, 401)

        # 3. Test allocate_hrp endpoint handler returns valid Pydantic response
        req = HrpAllocationRequest(mode="INTRA_BUCKET", risk_measure="CVAR", dump_cache=False)
        endpoint_res = asyncio.run(allocate_hrp(req))
        self.assertEqual(endpoint_res.status, "SUCCESS")
        self.assertEqual(len(endpoint_res.allocations), 7)
        self.assertAlmostEqual(sum(a.hrp_pct for a in endpoint_res.allocations), 100.0, delta=0.05)

if __name__ == "__main__":
    unittest.main()
