package com.portfolioos.mobile

import com.google.gson.Gson
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.model.BenchmarkAnalyticsDto
import com.portfolioos.mobile.model.FireSummaryResponseDto
import com.portfolioos.mobile.model.OverlapReportDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SyncModelParsingUnitTest {

    private val gson = Gson()

    @Test
    fun testSyncSnapshotDeserialization() {
        val json = """
            {
                "sync_info": {
                    "generated_at": "2026-08-28T21:00:00Z",
                    "fiscal_year": "2026-27",
                    "current_value": 1298893.00
                },
                "holdings": [],
                "tax_lots": []
            }
        """.trimIndent()

        val snapshot = gson.fromJson(json, SyncSnapshot::class.java)
        assertNotNull(snapshot)
        assertEquals("2026-08-28T21:00:00Z", snapshot.syncInfo?.generatedAt)
        assertEquals("2026-27", snapshot.syncInfo?.fiscalYear)
        assertEquals(1298893.00, snapshot.syncInfo?.currentValue ?: 0.0, 0.01)
    }

    @Test
    fun testBenchmarkAnalyticsDtoParsing() {
        val json = """
            {
                "alpha": 1.5,
                "beta": 0.92,
                "sharpe_ratio": 1.8,
                "tracking_error": 2.1,
                "r_squared": 0.95,
                "benchmark_name": "NIFTY_50_TRI"
            }
        """.trimIndent()

        val bench = gson.fromJson(json, BenchmarkAnalyticsDto::class.java)
        assertNotNull(bench)
        assertEquals("NIFTY_50_TRI", bench.benchmarkName)
        assertEquals(0.92, bench.beta, 0.001)
        assertEquals(1.5, bench.alpha, 0.001)
        assertEquals(1.8, bench.sharpeRatio, 0.001)
        assertEquals(2.1, bench.trackingError, 0.001)
        assertEquals(0.95, bench.rSquared, 0.001)
    }

    @Test
    fun testFireSummaryResponseDtoParsing() {
        val json = """
            {
                "active_scenario_label": "Standard FIRE",
                "monthly_expense_today": "₹1,00,000",
                "annual_expense": "₹12,00,000",
                "required_corpus": "₹3,00,000",
                "total_net_worth": "₹1,29,88,930",
                "status": "ON_TRACK",
                "monte_carlo_success_rate_pct": 94.5,
                "scenarios": [
                    {
                        "id": "std",
                        "label": "Standard FIRE",
                        "monthly_expense_today": "₹1,00,000",
                        "active": true
                    }
                ]
            }
        """.trimIndent()

        val fire = gson.fromJson(json, FireSummaryResponseDto::class.java)
        assertNotNull(fire)
        assertEquals("Standard FIRE", fire.activeScenarioLabel)
        assertEquals("ON_TRACK", fire.status)
        assertEquals(94.5, fire.monteCarloSuccessRatePct, 0.01)
        assertEquals(1, fire.scenarios.size)
        assertEquals(true, fire.scenarios[0].active)
    }

    @Test
    fun testOverlapReportDtoParsing() {
        val json = """
            {
                "coverage_type": "EXACT_ISIN_LOOKUP",
                "pairwise_matrix": [
                    {
                        "fund_a": "Fund A",
                        "fund_b": "Fund B",
                        "overlap_percentage": 18.5,
                        "common_stock_count": 12
                    }
                ]
            }
        """.trimIndent()

        val overlap = gson.fromJson(json, OverlapReportDto::class.java)
        assertNotNull(overlap)
        assertEquals("EXACT_ISIN_LOOKUP", overlap.coverageType)
        assertEquals(1, overlap.pairwiseMatrix.size)
        assertEquals(18.5, overlap.pairwiseMatrix[0].overlapPercentage, 0.01)
        assertEquals(12, overlap.pairwiseMatrix[0].commonHoldingsCount)
    }
}
