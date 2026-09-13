package com.portfolioos.mobile

import com.google.gson.Gson
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.model.BenchmarkAnalyticsDto
import com.portfolioos.mobile.model.FireSummaryResponseDto
import com.portfolioos.mobile.model.OverlapReportDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
                "holding_coverage_type": "EXACT_ISIN_LOOKUP",
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

    @Test
    fun testOverlapReportDtoWithConcentrationsAndZeroOverlap() {
        val json = """
            {
                "status": "OK",
                "holding_coverage_type": "FACTSHEET_POI_PARSED",
                "pairwise_matrix": [
                    {
                        "fund_a": "Parag Parikh Flexi Cap Fund",
                        "fund_b": "UTI Nifty 50 Index Fund",
                        "source_type_a": "FACTSHEET_POI_PARSED",
                        "source_type_b": "NSE_INDEX_CONSTITUENTS",
                        "is_unverified_estimate": false,
                        "overlap_percentage": 27.42,
                        "common_stock_count": 18
                    },
                    {
                        "fund_a": "Parag Parikh Flexi Cap Fund",
                        "fund_b": "Motilal Oswal Midcap Fund",
                        "source_type_a": "FACTSHEET_POI_PARSED",
                        "source_type_b": "MANUAL_ESTIMATE_UNVERIFIED",
                        "is_unverified_estimate": true,
                        "overlap_percentage": 0.0,
                        "common_stock_count": 0
                    }
                ],
                "portfolio_top_stock_concentrations": [
                    {
                        "stock_symbol": "HDFCBANK",
                        "company_name": "HDFC Bank Ltd.",
                        "rupee_exposure": 142500.0,
                        "portfolio_percentage": 8.15,
                        "is_audited": true
                    },
                    {
                        "stock_symbol": "RELIANCE",
                        "company_name": "Reliance Industries Ltd.",
                        "rupee_exposure": 112000.0,
                        "portfolio_percentage": 6.41,
                        "is_audited": false
                    }
                ],
                "coverage_telemetry": {
                    "total_equity_aum": 1748250.0,
                    "audited_aum": 1739500.0,
                    "unverified_aum": 8750.0,
                    "audited_coverage_pct": 99.5,
                    "include_unverified": false
                }
            }
        """.trimIndent()

        val report = gson.fromJson(json, OverlapReportDto::class.java)
        assertNotNull(report)
        assertEquals("OK", report.status)
        assertEquals("FACTSHEET_POI_PARSED", report.coverageType)
        assertEquals(2, report.pairwiseMatrix.size)

        // Pairwise matrix assertions
        val p1 = report.pairwiseMatrix[0]
        assertEquals("Parag Parikh Flexi Cap Fund", p1.fundA)
        assertEquals(27.42, p1.overlapPercentage, 0.001)
        assertFalse(p1.isUnverifiedEstimate)

        val p2 = report.pairwiseMatrix[1]
        assertEquals(0.0, p2.overlapPercentage, 0.001)
        assertTrue(p2.isUnverifiedEstimate)

        // Stock concentrations assertions
        assertEquals(2, report.stockConcentrations.size)
        val s1 = report.stockConcentrations[0]
        assertEquals("HDFCBANK", s1.stockSymbol)
        assertEquals(142500.0, s1.rupeeExposure, 0.01)
        assertTrue(s1.isAudited)

        val s2 = report.stockConcentrations[1]
        assertEquals("RELIANCE", s2.stockSymbol)
        assertFalse(s2.isAudited)

        // Telemetry assertions
        assertNotNull(report.coverageTelemetry)
        assertEquals(99.5, report.coverageTelemetry?.auditedCoveragePct ?: 0.0, 0.01)
        assertEquals(1739500.0, report.coverageTelemetry?.auditedAum ?: 0.0, 0.01)
    }

    @Test
    fun testTradeSimulationResultDtoSnakeCaseDeserialization() {
        val json = """
            {
                "isin": "INF209K01165",
                "scheme_name": "Aditya Birla Sun Life Frontline Equity Fund",
                "trade_type": "DISPOSAL",
                "units": 100.0,
                "price_per_unit": 52.45,
                "gross_trade_amount": 5245.0,
                "gross_capital_gain": 1245.0,
                "ltcg_equity": 1000.0,
                "stcg_equity": 0.0,
                "slab_rate_gain": 245.0,
                "sec112a_exemption_applied": 125000.0,
                "estimated_tax_liability": 0.0,
                "post_trade_net_worth": 1254300.0,
                "post_trade_invested_cost": 850000.0,
                "post_trade_xirr": 14.82,
                "tax_summary_notice": "Zero liability after Sec 112A exemption."
            }
        """.trimIndent()

        val res = gson.fromJson(json, com.portfolioos.mobile.model.TradeSimulationResultDto::class.java)
        assertNotNull(res)
        assertEquals("INF209K01165", res.isin)
        assertEquals("Aditya Birla Sun Life Frontline Equity Fund", res.schemeName)
        assertEquals("DISPOSAL", res.tradeType)
        assertEquals(100.0, res.units, 0.001)
        assertEquals(52.45, res.pricePerUnit, 0.001)
        assertEquals(5245.0, res.grossTradeAmount, 0.01)
        assertEquals(1245.0, res.grossCapitalGain, 0.01)
        assertEquals(1000.0, res.ltcgEquity, 0.01)
        assertEquals(0.0, res.stcgEquity, 0.01)
        assertEquals(245.0, res.slabRateGain, 0.01)
        assertEquals(245.0, res.debtGain, 0.01) // Backward compatibility alias
        assertEquals(125000.0, res.sec112aExemptionApplied, 0.01)
        assertEquals(0.0, res.estimatedTaxLiability, 0.01)
        assertEquals(1254300.0, res.postTradeNetWorth, 0.01)
        assertEquals(850000.0, res.postTradeInvestedCost, 0.01)
        assertEquals(14.82, res.postTradeXirr, 0.01)
        assertEquals("Zero liability after Sec 112A exemption.", res.taxSummaryNotice)
    }

    @Test
    fun testTradeSimulationResultDtoCamelCaseBackwardCompatibility() {
        val json = """
            {
                "isin": "INF209K01165",
                "schemeName": "Legacy Camel Fund",
                "tradeType": "DISPOSAL",
                "units": 50.0,
                "pricePerUnit": 100.0,
                "grossTradeAmount": 5000.0,
                "grossCapitalGain": 500.0,
                "ltcgEquity": 500.0,
                "stcgEquity": 0.0,
                "debtGain": 150.0,
                "sec112aExemptionApplied": 500.0,
                "estimatedTaxLiability": 0.0,
                "postTradeNetWorth": 200000.0,
                "postTradeInvestedCost": 150000.0,
                "postTradeXirr": 12.5,
                "taxSummaryNotice": "Legacy OK"
            }
        """.trimIndent()

        val res = gson.fromJson(json, com.portfolioos.mobile.model.TradeSimulationResultDto::class.java)
        assertNotNull(res)
        assertEquals("Legacy Camel Fund", res.schemeName)
        assertEquals("DISPOSAL", res.tradeType)
        assertEquals(100.0, res.pricePerUnit, 0.001)
        assertEquals(150.0, res.slabRateGain, 0.01)
        assertEquals(150.0, res.debtGain, 0.01)
        assertEquals(200000.0, res.postTradeNetWorth, 0.01)
        assertEquals(12.5, res.postTradeXirr, 0.01)
    }

    @Test
    fun testTradeSimulationRequestDtoSerialization() {
        val req = com.portfolioos.mobile.model.TradeSimulationRequestDto(
            isin = "INF209K01165",
            schemeName = "Aditya Birla",
            units = 25.5,
            pricePerUnit = 48.2,
            tradeDate = "2026-09-13",
            tradeType = "DISPOSAL"
        )
        val json = gson.toJson(req)
        assertTrue(json.contains("\"scheme_name\":\"Aditya Birla\""))
        assertTrue(json.contains("\"price_per_unit\":48.2"))
        assertTrue(json.contains("\"trade_date\":\"2026-09-13\""))
        assertTrue(json.contains("\"trade_type\":\"DISPOSAL\""))
    }
}

