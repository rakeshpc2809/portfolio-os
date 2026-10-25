package com.portfolioos.mobile

import com.portfolioos.mobile.data.nav.LocalPortfolioValuator
import com.portfolioos.mobile.data.nav.LocalTradeSimulator
import com.portfolioos.mobile.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LocalValuationAndSimulationUnitTest {

    @Test
    fun testLocalPortfolioValuator_advancesTimeAndRevalues() {
        val today = LocalDate.of(2026, 10, 24)

        // Lot 1: Bought 800 days ago (2024-08-15) -> Should be LTCG (>365d) and exit-load free (>=730d for PPFAS)
        val lot1 = FlatTaxLotDto(
            isin = "INF879O01027",
            buyDate = "2024-08-15",
            units = 100.0,
            taxClassification = "EQUITY",
            costPerUnit = 50.0
        )
        // Lot 2: Bought 100 days ago (2026-07-16) -> Should be STCG (<365d) and exit-load locked
        val lot2 = FlatTaxLotDto(
            isin = "INF879O01027",
            buyDate = "2026-07-16",
            units = 50.0,
            taxClassification = "EQUITY",
            costPerUnit = 60.0
        )
        // Lot 3: Debt fund bought 500 days ago -> Debt LTCG threshold is 730d -> Should be STCG (<730d)
        val lot3 = FlatTaxLotDto(
            isin = "INF209K01157",
            buyDate = "2025-06-11",
            units = 200.0,
            taxClassification = "DEBT",
            costPerUnit = 100.0
        )

        val holding1 = FlatHoldingDto(
            isin = "INF879O01027",
            fundName = "Parag Parikh Flexi Cap",
            totalUnits = 150.0,
            avgCost = 53.33,
            currentNav = 60.0
        )
        val holding2 = FlatHoldingDto(
            isin = "INF209K01157",
            fundName = "Aditya Birla Liquid",
            totalUnits = 200.0,
            avgCost = 100.0,
            currentNav = 100.0
        )

        val snapshot = SyncSnapshot(
            syncInfo = SyncInfoDto(
                generatedAt = "2026-10-14T10:00:00",
                currentValue = 29000.0,
                totalInvested = 28000.0
            ),
            holdings = listOf(holding1, holding2),
            taxLots = listOf(lot1, lot2, lot3)
        )

        // Revalue with new NAV for INF879O01027 = 70.0 and INF209K01157 = 105.0
        val navMap = mapOf(
            "INF879O01027" to 70.0,
            "INF209K01157" to 105.0
        )

        val revalued = LocalPortfolioValuator.revalueSnapshot(
            snapshot = snapshot,
            navMap = navMap,
            evaluationDate = today
        )

        // 1. Verify Tax Lots Time Advancement
        val revaluedLots = revalued.taxLots!!
        val rLot1 = revaluedLots.first { it.buyDate == "2024-08-15" }
        assertTrue("Lot 1 held > 365 days must be long term", rLot1.isLongTerm)
        assertEquals(0L, rLot1.daysToLtcg)
        assertTrue(rLot1.holdingDays >= 400L)

        val rLot2 = revaluedLots.first { it.buyDate == "2026-07-16" }
        assertFalse("Lot 2 held < 365 days must be short term", rLot2.isLongTerm)
        assertTrue(rLot2.daysToLtcg > 0L)

        val rLot3 = revaluedLots.first { it.isin == "INF209K01157" }
        assertFalse("Debt lot held 500 days (<730d) must be short term", rLot3.isLongTerm)
        assertEquals(230L, rLot3.daysToLtcg)

        // 2. Verify Holdings Revaluation & Exit Load Free Units
        val revaluedHoldings = revalued.holdings!!
        val rHold1 = revaluedHoldings.first { it.isin == "INF879O01027" }
        assertEquals(70.0, rHold1.currentNav, 0.001)
        assertEquals(150.0 * 70.0, rHold1.currentValue, 0.001) // 10500.0
        assertEquals(100.0, rHold1.exitLoadFreeUnits, 0.001)   // Only Lot 1 (100 units) is >= 365d

        val rHold2 = revaluedHoldings.first { it.isin == "INF209K01157" }
        assertEquals(105.0, rHold2.currentNav, 0.001)
        assertEquals(200.0 * 105.0, rHold2.currentValue, 0.001) // 21000.0

        // 3. Verify Total Net Worth and Ledger Age
        val syncInfo = revalued.syncInfo!!
        assertEquals(31500.0, syncInfo.currentValue, 0.001)
        assertEquals(10L, syncInfo.ledgerAgeDays) // 2026-10-14 to 2026-10-24
    }

    @Test
    fun testLocalTradeSimulator_fifoDisposalAndTaxCalculation() {
        val today = LocalDate.of(2026, 10, 24)

        // Lot 1: 50 units @ ₹50 bought 400 days ago (LTCG)
        val lot1 = FlatTaxLotDto(
            isin = "INF879O01027",
            buyDate = "2025-09-19",
            units = 50.0,
            taxClassification = "EQUITY",
            costPerUnit = 50.0
        )
        // Lot 2: 50 units @ ₹60 bought 100 days ago (STCG)
        val lot2 = FlatTaxLotDto(
            isin = "INF879O01027",
            buyDate = "2026-07-16",
            units = 50.0,
            taxClassification = "EQUITY",
            costPerUnit = 60.0
        )

        val snapshot = SyncSnapshot(
            syncInfo = SyncInfoDto(currentValue = 10000.0, totalInvested = 5500.0),
            taxLots = listOf(lot1, lot2)
        )

        // Sell 60 units @ ₹100
        // FIFO:
        // - 50 units from Lot 1 @ ₹50 cost: proceeds = 5000, cost = 2500, LTCG = 2500
        // - 10 units from Lot 2 @ ₹60 cost: proceeds = 1000, cost = 600, STCG = 400
        // Total gross proceeds = 6000
        // Total gain = 2900 (LTCG 2500, STCG 400)
        // LTCG is under ₹1.25L exemption, so LTCG tax = 0.
        // STCG = 400 * 20% = ₹80.00 (aligned with backend SimulationService without cess multiplier)
        val request = TradeSimulationRequestDto(
            isin = "INF879O01027",
            schemeName = "PPFCF",
            units = 60.0,
            pricePerUnit = 100.0,
            tradeType = "DISPOSAL"
        )

        val result = LocalTradeSimulator.simulateTradeLocally(snapshot, request, today)

        assertEquals(6000.0, result.grossTradeAmount, 0.001)
        assertEquals(2900.0, result.grossCapitalGain, 0.001)
        assertEquals(2500.0, result.ltcgEquity, 0.001)
        assertEquals(400.0, result.stcgEquity, 0.001)
        assertEquals(2500.0, result.sec112aExemptionApplied, 0.001)
        assertEquals(80.00, result.estimatedTaxLiability, 0.01)
        assertEquals(9920.00, result.postTradeNetWorth, 0.01) // 10000 - 80.00 tax liability
    }

    @Test
    fun testLocalTradeSimulator_grandfatheredFmvSec55_2_ac() {
        val today = LocalDate.of(2026, 10, 24)

        // Pre-2018 Equity Lot: Bought at ₹50, FMV as of Jan 31, 2018 was ₹80.
        // Selling at ₹100. Under Sec 55(2)(ac), effective cost floor is ₹80, so gain is only ₹20/unit instead of ₹50/unit.
        val lot = FlatTaxLotDto(
            isin = "INF879O01027",
            buyDate = "2017-06-15",
            units = 100.0,
            taxClassification = "EQUITY",
            costPerUnit = 50.0,
            grandfatheredNav = 80.0
        )

        val snapshot = SyncSnapshot(
            syncInfo = SyncInfoDto(currentValue = 10000.0, totalInvested = 5000.0),
            taxLots = listOf(lot)
        )

        val request = TradeSimulationRequestDto(
            isin = "INF879O01027",
            schemeName = "PPFCF",
            units = 50.0,
            pricePerUnit = 100.0,
            tradeType = "DISPOSAL"
        )

        val result = LocalTradeSimulator.simulateTradeLocally(snapshot, request, today)

        assertEquals(5000.0, result.grossTradeAmount, 0.001) // 50 * 100
        // Effective cost = 50 * 80 = 4000
        // Capital gain = 5000 - 4000 = 1000 (NOT 5000 - 2500 = 2500)
        assertEquals(1000.0, result.grossCapitalGain, 0.001)
        assertEquals(1000.0, result.ltcgEquity, 0.001)
        assertEquals(1000.0, result.sec112aExemptionApplied, 0.001) // Fully exempt under 1.25L
        assertEquals(0.0, result.estimatedTaxLiability, 0.001)
        assertEquals(10000.0, result.postTradeNetWorth, 0.001) // 0 tax liability, full net worth preserved
    }

    @Test
    fun testLocalTradeSimulator_lumpsumAllocationAcrossUnderweights() {
        val bucket1 = RebalanceBucketAllocationDto(
            bucket = "Mid Cap",
            targetPct = 25.0,
            currentPct = 15.0 // Deficit = 10%
        )
        val bucket2 = RebalanceBucketAllocationDto(
            bucket = "Small Cap",
            targetPct = 15.0,
            currentPct = 10.0 // Deficit = 5%
        )
        val bucket3 = RebalanceBucketAllocationDto(
            bucket = "Large Cap",
            targetPct = 60.0,
            currentPct = 75.0 // Overweight, deficit = 0
        )

        val plan = RebalancePlanDto(
            buySide = BuySidePlanDto(
                buckets = listOf(bucket1, bucket2, bucket3)
            )
        )

        val allocations = LocalTradeSimulator.allocateLumpsum(150000.0, plan)

        // Total deficit = 10 + 5 = 15%
        // Mid Cap share: 10/15 * 150000 = ₹100,000
        // Small Cap share: 5/15 * 150000 = ₹50,000
        assertEquals(100000.0, allocations["Mid Cap"] ?: 0.0, 0.01)
        assertEquals(50000.0, allocations["Small Cap"] ?: 0.0, 0.01)
        assertNull("Overweight bucket should receive no lumpsum inflow", allocations["Large Cap"])
    }

    @Test
    fun testLocalTradeSimulator_remainingHeadroomFromSnapshot() {
        val today = LocalDate.of(2026, 10, 24)

        // Equity Lot: 100 units @ ₹50 cost bought 500 days ago (LTCG)
        val lot = FlatTaxLotDto(
            isin = "INF879O01027",
            buyDate = "2025-06-11",
            units = 100.0,
            taxClassification = "EQUITY",
            costPerUnit = 50.0
        )

        // Snapshot already utilized 105,000 of exemption, leaving only 20,000 headroom
        val taxSummary = TaxSummaryDto(
            totalRealizedGain = 105000.0,
            totalLtcgExempt = 105000.0,
            totalStcgTaxable = 0.0,
            totalTaxEstimate = 0.0,
            exemptionHeadroomBefore = 125000.0,
            exemptionHeadroomAfter = 20000.0
        )
        val plan = RebalancePlanDto(
            sellSide = SellSidePlanDto(taxSummary = taxSummary)
        )
        val snapshot = SyncSnapshot(
            syncInfo = SyncInfoDto(currentValue = 20000.0, totalInvested = 5000.0),
            taxLots = listOf(lot),
            rebalancePlan = plan
        )

        // Sell 100 units @ ₹100 -> Proceeds = 10,000, Cost = 5,000, LTCG = 5,000
        // But suppose sell 100 units @ ₹100 -> Gain = 5,000. Let's test with Gain = 50,000
        // 100 units @ ₹550 price -> Proceeds = 55,000, Cost = 5,000, LTCG = 50,000
        val request = TradeSimulationRequestDto(
            isin = "INF879O01027",
            schemeName = "PPFCF",
            units = 100.0,
            pricePerUnit = 550.0,
            tradeType = "DISPOSAL"
        )

        val result = LocalTradeSimulator.simulateTradeLocally(snapshot, request, today)

        assertEquals(50000.0, result.grossCapitalGain, 0.001)
        assertEquals(50000.0, result.ltcgEquity, 0.001)
        // Under remaining headroom awareness, only 20,000 is exempt (NOT 50,000 or 125,000!)
        assertEquals(20000.0, result.sec112aExemptionApplied, 0.001)
        // Taxable LTCG = 50,000 - 20,000 = 30,000 * 12.5% = ₹3,750
        assertEquals(3750.0, result.estimatedTaxLiability, 0.01)
    }

    @Test
    fun testLocalTradeSimulator_intraSimulationLossNetting() {
        val today = LocalDate.of(2026, 10, 24)

        // Lot 1 (STCL): 50 units @ ₹120 cost bought 30 days ago -> Loss = -20/unit (-1000 total)
        val lot1 = FlatTaxLotDto(
            isin = "INF879O01027",
            buyDate = "2026-09-24",
            units = 50.0,
            taxClassification = "EQUITY",
            costPerUnit = 120.0
        )
        // Lot 2 (STCG): 50 units @ ₹70 cost bought 20 days ago -> Gain = +30/unit (+1500 total)
        val lot2 = FlatTaxLotDto(
            isin = "INF879O01027",
            buyDate = "2026-10-04",
            units = 50.0,
            taxClassification = "EQUITY",
            costPerUnit = 70.0
        )

        val snapshot = SyncSnapshot(
            syncInfo = SyncInfoDto(currentValue = 10000.0, totalInvested = 9500.0),
            taxLots = listOf(lot1, lot2)
        )

        // Sell all 100 units @ ₹100
        val request = TradeSimulationRequestDto(
            isin = "INF879O01027",
            schemeName = "PPFCF",
            units = 100.0,
            pricePerUnit = 100.0,
            tradeType = "DISPOSAL"
        )

        val result = LocalTradeSimulator.simulateTradeLocally(snapshot, request, today)

        // Gross Gain = -1000 + 1500 = +500
        assertEquals(500.0, result.grossCapitalGain, 0.001)
        assertEquals(500.0, result.stcgEquity, 0.001)
        // Tax = 500 * 20% = ₹100.00 (loss netted against gain before tax calculation)
        assertEquals(100.0, result.estimatedTaxLiability, 0.01)
    }

    @Test
    fun testLocalTradeSimulator_goldFloorBackstopAndDrawdownLumpsumAllocation() {
        val goldBucket = RebalanceBucketAllocationDto(
            bucket = "GOLD_SILVER",
            targetPct = 15.0,
            currentPct = 10.0 // Deficit = 5%
        )
        val coreBucket = RebalanceBucketAllocationDto(
            bucket = "EQUITY_CORE",
            targetPct = 60.0,
            currentPct = 50.0 // Deficit = 10%
        )
        val liquidBucket = RebalanceBucketAllocationDto(
            bucket = "LIQUID_BUFFER",
            targetPct = 25.0,
            currentPct = 20.0 // Deficit = 5%
        )

        // Case 1: GOLD_FLOOR_BACKSTOP trigger -> routes up to 50% of the Gold gap first
        val backstopPlan = RebalancePlanDto(
            trigger = RebalanceTriggerDto(type = "GOLD_FLOOR_BACKSTOP"),
            buySide = BuySidePlanDto(
                totalToInvest = 100000.0,
                buckets = listOf(goldBucket, coreBucket, liquidBucket)
            )
        )
        val backstopAlloc = LocalTradeSimulator.allocateLumpsum(100000.0, backstopPlan)
        // Gold gap = 5% of 100,000 = 5,000. 50% fill = 2,500 priority fill.
        // Remaining 97,500 allocated across remaining deficits (Gold 5%, Core 10%, Liquid 5% -> total 20%)
        // Gold gets 2,500 + 97,500 * (5/20) = 2,500 + 24,375 = 26,875
        assertTrue((backstopAlloc["GOLD_SILVER"] ?: 0.0) >= 2500.0)

        // Case 2: Market Drawdown >= 10% -> LIQUID_BUFFER receives ZERO fresh lumpsum cash
        val drawdownPlan = RebalancePlanDto(
            drawdownContext = DrawdownContextDto(currentDrawdownPct = 12.5),
            buySide = BuySidePlanDto(
                buckets = listOf(goldBucket, coreBucket, liquidBucket)
            )
        )
        val drawdownAlloc = LocalTradeSimulator.allocateLumpsum(100000.0, drawdownPlan)
        assertNull("LIQUID_BUFFER must not receive fresh lumpsum cash during market drawdown >= 10%", drawdownAlloc["LIQUID_BUFFER"])
        assertTrue((drawdownAlloc["EQUITY_CORE"] ?: 0.0) > 0.0)
        assertTrue((drawdownAlloc["GOLD_SILVER"] ?: 0.0) > 0.0)
    }
}
