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
