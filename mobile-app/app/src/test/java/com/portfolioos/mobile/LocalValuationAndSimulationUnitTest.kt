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
        // STCG = 400 * 20% = 80 + 4% cess = ₹83.20
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
        assertEquals(83.20, result.estimatedTaxLiability, 0.01)
        assertEquals(9916.80, result.postTradeNetWorth, 0.01) // 10000 - 83.20 tax liability
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
}
