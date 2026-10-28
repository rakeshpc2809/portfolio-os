package com.portfolioos.mobile

import com.portfolioos.mobile.data.nav.LocalTradeSimulator
import com.portfolioos.mobile.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate

@RunWith(Parameterized::class)
class LocalTradeSimulatorParityTest(
    private val scenarioNumber: Int,
    private val scenarioDescription: String,
    private val snapshot: SyncSnapshot,
    private val request: TradeSimulationRequestDto,
    private val evaluationDate: LocalDate,
    private val expectedTradeAmount: Double,
    private val expectedGain: Double,
    private val expectedExemption: Double,
    private val expectedTax: Double
) {

    @Test
    fun testTradeScenarioMatchesParity() {
        val result = LocalTradeSimulator.simulateTradeLocally(snapshot, request, evaluationDate)

        assertEquals("Scenario $scenarioNumber: $scenarioDescription - Gross Trade Amount",
            expectedTradeAmount, result.grossTradeAmount, 0.01)

        assertEquals("Scenario $scenarioNumber: $scenarioDescription - Gross Capital Gain",
            expectedGain, result.grossCapitalGain, 0.01)

        assertEquals("Scenario $scenarioNumber: $scenarioDescription - Exemption Applied",
            expectedExemption, result.sec112aExemptionApplied, 0.01)

        assertEquals("Scenario $scenarioNumber: $scenarioDescription - Estimated Tax Liability",
            expectedTax, result.estimatedTaxLiability, 0.01)
    }

    companion object {

        private fun lot(
            isin: String,
            buyDate: String,
            units: Double,
            taxClassification: String,
            costPerUnit: Double,
            grandfatheredNav: Double? = null
        ): FlatTaxLotDto = FlatTaxLotDto(
            isin = isin,
            buyDate = buyDate,
            units = units,
            taxClassification = taxClassification,
            costPerUnit = costPerUnit,
            grandfatheredNav = grandfatheredNav
        )

        private fun req(
            isin: String,
            schemeName: String,
            units: Double,
            pricePerUnit: Double,
            tradeType: String
        ): TradeSimulationRequestDto = TradeSimulationRequestDto(
            isin = isin,
            schemeName = schemeName,
            units = units,
            pricePerUnit = pricePerUnit,
            tradeType = tradeType
        )

        private fun makeSnapshot(
            lots: List<FlatTaxLotDto>,
            headroomAfter: Double = 125000.0,
            headroomBefore: Double = 125000.0,
            exemptUsed: Double = 0.0
        ): SyncSnapshot {
            return SyncSnapshot(
                syncInfo = SyncInfoDto(
                    generatedAt = "2026-10-24T10:00:00",
                    currentValue = 1000000.0,
                    totalInvested = 800000.0
                ),
                taxLots = lots,
                rebalancePlan = RebalancePlanDto(
                    planId = "PLAN-PARITY-TEST",
                    sellSide = SellSidePlanDto(
                        totalRequired = 0.0,
                        waterfall = emptyList(),
                        taxSummary = TaxSummaryDto(
                            totalRealizedGain = 0.0,
                            totalLtcgExempt = exemptUsed,
                            totalStcgTaxable = 0.0,
                            totalTaxEstimate = 0.0,
                            exemptionHeadroomBefore = headroomBefore,
                            exemptionHeadroomAfter = headroomAfter
                        )
                    )
                )
            )
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}: {1}")
        fun data(): Collection<Array<Any>> {
            val evalDate = LocalDate.of(2026, 10, 24)

            return listOf(
                // Scenario 1: ACQUISITION trade (Buy SIP adds cost, zero gain/tax)
                arrayOf(
                    1,
                    "Acquisition buy SIP adds to cost with zero tax",
                    makeSnapshot(emptyList()),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 100.0, 50.0, "ACQUISITION"),
                    evalDate,
                    5000.0, 0.0, 0.0, 0.0
                ),

                // Scenario 2: Pure Equity STCG (100 days old, 20% tax)
                arrayOf(
                    2,
                    "Pure Equity STCG taxed @ 20%",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2026-07-16", 100.0, "EQUITY", 50.0) // buy: 50, sell: 80 -> gain 3,000
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 100.0, 80.0, "DISPOSAL"),
                    evalDate,
                    8000.0, 3000.0, 0.0, 600.0 // 3000 * 20% = 600
                ),

                // Scenario 3: Pure Equity LTCG within ₹1.25L exemption (500 days old, 0 tax)
                arrayOf(
                    3,
                    "Equity LTCG completely within 1.25L exemption (zero tax)",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2025-06-11", 1000.0, "EQUITY", 100.0) // buy: 100, sell: 150 -> gain 50,000
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 1000.0, 150.0, "DISPOSAL"),
                    evalDate,
                    150000.0, 50000.0, 50000.0, 0.0 // Exemption 50,000, Tax 0.0
                ),

                // Scenario 4: Equity LTCG exceeding ₹1.25L exemption (excess @ 12.5%)
                arrayOf(
                    4,
                    "Equity LTCG exceeding 1.25L exemption (excess @ 12.5%)",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2025-01-01", 1000.0, "EQUITY", 100.0) // buy: 100, sell: 300 -> gain 200,000
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 1000.0, 300.0, "DISPOSAL"),
                    evalDate,
                    300000.0, 200000.0, 125000.0, 9375.0 // (200,000 - 125,000) * 12.5% = 9,375
                ),

                // Scenario 5: Equity LTCG straddling reduced exemption headroom (headroom = 40,000, gain = 100,000)
                arrayOf(
                    5,
                    "Equity LTCG straddling partial FY headroom (headroom 40k, excess @ 12.5%)",
                    makeSnapshot(
                        listOf(lot("INF109KC12U0", "2025-01-01", 1000.0, "EQUITY", 100.0)),
                        headroomAfter = 40000.0, headroomBefore = 125000.0, exemptUsed = 85000.0
                    ),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 1000.0, 200.0, "DISPOSAL"),
                    evalDate,
                    200000.0, 100000.0, 40000.0, 7500.0 // (100,000 - 40,000) * 12.5% = 7,500
                ),

                // Scenario 6: STCL completely offsetting STCG (gain 5k, loss 10k -> 0 tax)
                arrayOf(
                    6,
                    "STCL completely offsets STCG (zero tax)",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2026-07-01", 100.0, "EQUITY", 200.0), // Loss: (100 - 200)*100 = -10,000
                        lot("INF109KC12U0", "2026-08-01", 100.0, "EQUITY", 50.0)   // Gain: (100 - 50)*100 = +5,000
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 200.0, 100.0, "DISPOSAL"),
                    evalDate,
                    20000.0, -5000.0, 0.0, 0.0
                ),

                // Scenario 7: STCL partially offsetting STCG (gain 10k, loss 5k -> net 5k @ 20%)
                arrayOf(
                    7,
                    "STCL partially offsets STCG (net 5k @ 20% = 1,000 tax)",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2026-07-01", 100.0, "EQUITY", 150.0), // Loss: (100 - 150)*100 = -5,000
                        lot("INF109KC12U0", "2026-08-01", 100.0, "EQUITY", 0.0)    // Gain: (100 - 0)*100 = +10,000
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 200.0, 100.0, "DISPOSAL"),
                    evalDate,
                    20000.0, 5000.0, 0.0, 1000.0 // Net STCG 5,000 @ 20% = 1,000
                ),

                // Scenario 8: STCL offsetting LTCG after wiping out STCG (Sec 70 cross-head set-off)
                arrayOf(
                    8,
                    "Unabsorbed STCL offsets Equity LTCG before exemption",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2026-07-01", 100.0, "EQUITY", 150.0), // STCL: (100 - 150)*100 = -5,000
                        lot("INF109KC12U0", "2024-01-01", 100.0, "EQUITY", 50.0)   // LTCG: (100 - 50)*100 = +5,000
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 200.0, 100.0, "DISPOSAL"),
                    evalDate,
                    20000.0, 0.0, 0.0, 0.0
                ),

                // Scenario 9: LTCL offsetting Equity LTCG before Section 112A exemption
                arrayOf(
                    9,
                    "LTCL offsets Equity LTCG before Section 112A exemption",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2024-01-01", 1000.0, "EQUITY", 200.0), // LTCL: (100 - 200)*1000 = -100,000
                        lot("INF109KC12U0", "2024-06-01", 1000.0, "EQUITY", 50.0)   // LTCG: (100 - 50)*1000 = +50,000
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 2000.0, 100.0, "DISPOSAL"),
                    evalDate,
                    200000.0, -50000.0, 0.0, 0.0
                ),

                // Scenario 10: Non-Equity LTCG (Gold/International Fund > 730d) taxed @ 12.5% without 112A exemption
                arrayOf(
                    10,
                    "Non-equity LTCG (Gold FoF > 730d) taxed @ 12.5% without exemption",
                    makeSnapshot(listOf(
                        lot("INF205K01QU7", "2024-01-01", 1000.0, "GOLD", 100.0) // buy: 100, sell: 150 -> gain 50,000
                    )),
                    req("INF205K01QU7", "Invesco Gold Fund", 1000.0, 150.0, "DISPOSAL"),
                    evalDate,
                    150000.0, 50000.0, 0.0, 6250.0 // 50,000 * 12.5% = 6,250
                ),

                // Scenario 11: Non-Equity STCG (Gold/International Fund < 730d) taxed @ 30% slab rate
                arrayOf(
                    11,
                    "Non-equity STCG (Gold FoF < 730d) taxed @ 30% slab rate",
                    makeSnapshot(listOf(
                        lot("INF205K01QU7", "2026-01-01", 1000.0, "GOLD", 100.0) // buy: 100, sell: 120 -> gain 20,000
                    )),
                    req("INF205K01QU7", "Invesco Gold Fund", 1000.0, 120.0, "DISPOSAL"),
                    evalDate,
                    120000.0, 20000.0, 0.0, 6000.0 // 20,000 * 30% = 6,000
                ),

                // Scenario 12: Specified Debt (Sec 50AA) STCG taxed @ 30% nominal slab
                arrayOf(
                    12,
                    "Specified Debt (Sec 50AA) STCG taxed @ 30% slab rate",
                    makeSnapshot(listOf(
                        lot("INF209K01157", "2025-06-11", 500.0, "DEBT_SPECIFIED_50AA", 100.0) // buy: 100, sell: 110 -> gain 5,000
                    )),
                    req("INF209K01157", "Aditya Birla Liquid", 500.0, 110.0, "DISPOSAL"),
                    evalDate,
                    55000.0, 5000.0, 0.0, 1500.0 // 5,000 * 30% = 1,500
                ),

                // Scenario 13: Grandfathered lot: Cost 100, FMV 150, Sale 180 -> Effective Cost 150, Gain 30
                arrayOf(
                    13,
                    "Grandfathered lot: Cost 100, FMV 150, Sale 180 -> Effective Cost 150, Gain 30/unit",
                    makeSnapshot(listOf(
                        lot("INF879O01027", "2017-06-01", 1000.0, "EQUITY", 100.0, grandfatheredNav = 150.0)
                    )),
                    req("INF879O01027", "Parag Parikh Flexi Cap", 1000.0, 180.0, "DISPOSAL"),
                    evalDate,
                    180000.0, 30000.0, 30000.0, 0.0 // Gain: 180,000 - 150,000 = 30,000 (within 125k exemption)
                ),

                // Scenario 14: Grandfathered lot: Cost 100, FMV 150, Sale 120 -> Effective Cost 120, Gain 0
                arrayOf(
                    14,
                    "Grandfathered lot: Cost 100, FMV 150, Sale 120 -> Effective Cost 120, Zero gain/loss",
                    makeSnapshot(listOf(
                        lot("INF879O01027", "2017-06-01", 1000.0, "EQUITY", 100.0, grandfatheredNav = 150.0)
                    )),
                    req("INF879O01027", "Parag Parikh Flexi Cap", 1000.0, 120.0, "DISPOSAL"),
                    evalDate,
                    120000.0, 0.0, 0.0, 0.0 // max(100, min(150, 120)) = 120 -> Gain 0.0
                ),

                // Scenario 15: Grandfathered lot: Cost 100, FMV 150, Sale 90 -> Effective Cost 100, Loss -10
                arrayOf(
                    15,
                    "Grandfathered lot: Cost 100, FMV 150, Sale 90 -> Effective Cost 100, Loss -10/unit",
                    makeSnapshot(listOf(
                        lot("INF879O01027", "2017-06-01", 1000.0, "EQUITY", 100.0, grandfatheredNav = 150.0)
                    )),
                    req("INF879O01027", "Parag Parikh Flexi Cap", 1000.0, 90.0, "DISPOSAL"),
                    evalDate,
                    90000.0, -10000.0, 0.0, 0.0 // max(100, min(150, 90)) = 100 -> Loss -10,000
                ),

                // Scenario 16: Grandfathered lot: Cost 150, FMV 120, Sale 180 -> Effective Cost 150, Gain 30
                arrayOf(
                    16,
                    "Grandfathered lot: Cost 150, FMV 120, Sale 180 -> Effective Cost 150, Gain 30/unit",
                    makeSnapshot(listOf(
                        lot("INF879O01027", "2017-06-01", 1000.0, "EQUITY", 150.0, grandfatheredNav = 120.0)
                    )),
                    req("INF879O01027", "Parag Parikh Flexi Cap", 1000.0, 180.0, "DISPOSAL"),
                    evalDate,
                    180000.0, 30000.0, 30000.0, 0.0 // max(150, min(120, 180)) = 150 -> Gain 30,000
                ),

                // Scenario 17: Grandfathered lot: Cost 150, FMV 120, Sale 100 -> Effective Cost 150, Loss -50
                arrayOf(
                    17,
                    "Grandfathered lot: Cost 150, FMV 120, Sale 100 -> Effective Cost 150, Loss -50/unit",
                    makeSnapshot(listOf(
                        lot("INF879O01027", "2017-06-01", 1000.0, "EQUITY", 150.0, grandfatheredNav = 120.0)
                    )),
                    req("INF879O01027", "Parag Parikh Flexi Cap", 1000.0, 100.0, "DISPOSAL"),
                    evalDate,
                    100000.0, -50000.0, 0.0, 0.0 // max(150, min(120, 100)) = 150 -> Loss -50,000
                ),

                // Scenario 18: Multi-lot FIFO partial liquidation across 2 lots
                arrayOf(
                    18,
                    "Multi-lot FIFO partial liquidation (100 units from Lot 1, 50 units from Lot 2)",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2024-01-01", 100.0, "EQUITY", 50.0), // 100 units @ 50 (LTCG)
                        lot("INF109KC12U0", "2026-07-01", 100.0, "EQUITY", 80.0)  // 50 units @ 80 (STCG)
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 150.0, 100.0, "DISPOSAL"),
                    evalDate,
                    15000.0, 6000.0, 5000.0, 200.0 // Lot 1 LTCG: (100-50)*100=5000 (exempt); Lot 2 STCG: (100-80)*50=1000 @ 20% = 200
                ),

                // Scenario 19: Full liquidation of lot pool
                arrayOf(
                    19,
                    "Full liquidation of entire lot pool",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2024-01-01", 50.0, "EQUITY", 50.0),
                        lot("INF109KC12U0", "2024-06-01", 50.0, "EQUITY", 50.0)
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 100.0, 100.0, "DISPOSAL"),
                    evalDate,
                    10000.0, 5000.0, 5000.0, 0.0
                ),

                // Scenario 20: Oversold request capped at available units (request 500 units, only 200 available)
                arrayOf(
                    20,
                    "Oversold request: requested units capped at available inventory",
                    makeSnapshot(listOf(
                        lot("INF109KC12U0", "2024-01-01", 200.0, "EQUITY", 50.0)
                    )),
                    req("INF109KC12U0", "ICICI LargeMidcap 250", 500.0, 100.0, "DISPOSAL"),
                    evalDate,
                    20000.0, 10000.0, 10000.0, 0.0 // Only 200 units sold, 20k proceeds, 10k exempt gain, 0 tax
                )
            )
        }
    }
}
