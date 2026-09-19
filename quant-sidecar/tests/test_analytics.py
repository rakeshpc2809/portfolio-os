import unittest
import numpy as np
from quant.analytics_engine import (
    run_monte_carlo_fire_simulation,
    compute_benchmark_analytics,
    FireSimulationResponse,
    BenchmarkAnalyticsResponse
)

class TestAnalyticsEngine(unittest.TestCase):
    def test_fire_simulation_schema_and_percentiles(self):
        np.random.seed(42)
        daily_rets = list(np.random.normal(0.0005, 0.01, 500))
        result = run_monte_carlo_fire_simulation(
            daily_returns_list=daily_rets,
            current_corpus=1000000.0,
            annual_expense=600000.0,
            monthly_contribution=50000.0,
            years_to_retirement=10,
            num_simulations=500
        )
        # Validate Pydantic model
        validated = FireSimulationResponse(**result)
        self.assertEqual(validated.status, "OK")
        self.assertEqual(validated.years_to_retirement, 10)
        self.assertEqual(validated.retirement_duration_years, 30)
        self.assertGreaterEqual(validated.success_rate_pct, 0.0)
        self.assertLessEqual(validated.success_rate_pct, 100.0)

        # Invariance check: median_ending_corpus equals retirement start median
        self.assertEqual(validated.median_ending_corpus, validated.median_retirement_start_corpus)
        self.assertEqual(validated.tenth_percentile_corpus, validated.tenth_percentile_retirement_start_corpus)

        # Trajectories check
        self.assertEqual(len(validated.fan_chart_trajectories), 41) # Year 0 to 40 inclusive
        ret_traj = validated.fan_chart_trajectories[10]
        self.assertEqual(validated.median_retirement_start_corpus, ret_traj.p50)
        self.assertEqual(validated.tenth_percentile_retirement_start_corpus, ret_traj.p10)

        final_traj = validated.fan_chart_trajectories[-1]
        self.assertEqual(validated.median_final_ending_corpus, final_traj.p50)
        self.assertEqual(validated.tenth_percentile_final_ending_corpus, final_traj.p10)

    def test_linear_confidence_ramp_boundaries(self):
        np.random.seed(42)
        # N = 0
        res_0 = run_monte_carlo_fire_simulation(daily_returns_list=[], num_simulations=100)
        self.assertEqual(res_0["data_source"], "SYNTHETIC_GAUSSIAN_PRIOR")
        self.assertEqual(res_0["confidence_ramp_weight"], 0.0)

        # N = 375 (exactly 50% ramp weight)
        rets_375 = list(np.random.normal(0.0005, 0.01, 375))
        res_375 = run_monte_carlo_fire_simulation(daily_returns_list=rets_375, num_simulations=100)
        self.assertEqual(res_375["data_source"], "BLENDED_CONFIDENCE_RAMP")
        self.assertAlmostEqual(res_375["confidence_ramp_weight"], 0.5, places=3)
        self.assertIn("50.0% Empirical", res_375["data_source_label"])

        # N = 750 (100% ramp weight, boundary)
        rets_750 = list(np.random.normal(0.0005, 0.01, 750))
        res_750 = run_monte_carlo_fire_simulation(daily_returns_list=rets_750, num_simulations=100)
        self.assertEqual(res_750["data_source"], "EMPIRICAL_PORTFOLIO")
        self.assertEqual(res_750["confidence_ramp_weight"], 1.0)

        # N = 1000 (> 750, capped at 1.0)
        rets_1000 = list(np.random.normal(0.0005, 0.01, 1000))
        res_1000 = run_monte_carlo_fire_simulation(daily_returns_list=rets_1000, num_simulations=100)
        self.assertEqual(res_1000["data_source"], "EMPIRICAL_PORTFOLIO")
        self.assertEqual(res_1000["confidence_ramp_weight"], 1.0)

    def test_regime_conditional_block_bootstrap(self):
        # Generate bimodal market regime history (bull blocks + crash blocks)
        np.random.seed(123)
        rets = list(np.random.normal(0.0010, 0.008, 400)) + list(np.random.normal(-0.0008, 0.022, 400))

        # Seed same for reproducibility
        np.random.seed(42)
        res_risk_off = run_monte_carlo_fire_simulation(
            daily_returns_list=rets,
            num_simulations=1000,
            regime="EXPANSION_RISK_OFF"
        )

        np.random.seed(42)
        res_risk_on = run_monte_carlo_fire_simulation(
            daily_returns_list=rets,
            num_simulations=1000,
            regime="ACCUMULATION_RISK_ON"
        )

        self.assertEqual(res_risk_off["regime_applied"], "EXPANSION_RISK_OFF")
        self.assertEqual(res_risk_on["regime_applied"], "ACCUMULATION_RISK_ON")
        self.assertIn("EXPANSION_RISK_OFF", res_risk_off["data_source_label"])
        self.assertIn("ACCUMULATION_RISK_ON", res_risk_on["data_source_label"])

        # Under risk-off (high valuation/volatility), median ending corpus should reflect conservative drag compared to risk-on recovery
        self.assertLess(
            res_risk_off["median_final_ending_corpus"],
            res_risk_on["median_final_ending_corpus"]
        )

    def test_benchmark_analytics_schema(self):
        np.random.seed(42)
        p_rets = list(np.random.normal(0.0006, 0.012, 252))
        b_rets = list(np.random.normal(0.0005, 0.010, 252))
        result = compute_benchmark_analytics(p_rets, b_rets, "NIFTY_50_TRI")
        
        validated = BenchmarkAnalyticsResponse(**result)
        self.assertEqual(validated.status, "OK")
        self.assertEqual(validated.benchmark_name, "NIFTY_50_TRI")
        self.assertEqual(validated.sample_days, 252)
        self.assertTrue(validated.is_provisional) # < 750 days
        self.assertIsInstance(validated.beta, float)
        self.assertIsInstance(validated.alpha_pct, float)
        self.assertIsInstance(validated.sharpe_ratio, float)

if __name__ == "__main__":
    unittest.main()
