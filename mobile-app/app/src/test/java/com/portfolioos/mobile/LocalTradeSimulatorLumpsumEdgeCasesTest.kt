package com.portfolioos.mobile

import com.portfolioos.mobile.data.nav.LocalTradeSimulator
import com.portfolioos.mobile.model.BuySidePlanDto
import com.portfolioos.mobile.model.DrawdownContextDto
import com.portfolioos.mobile.model.RebalanceBucketAllocationDto
import com.portfolioos.mobile.model.RebalancePlanDto
import com.portfolioos.mobile.model.RebalanceTriggerDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTradeSimulatorLumpsumEdgeCasesTest {

    @Test
    fun testScenario24_allocateLumpsumGoldFloorBackstopPriority() {
        val plan = RebalancePlanDto(
            trigger = RebalanceTriggerDto(type = "GOLD_FLOOR_BACKSTOP"),
            buySide = BuySidePlanDto(
                totalToInvest = 100000.0,
                buckets = listOf(
                    RebalanceBucketAllocationDto("GOLD_SILVER", targetPct = 15.0, currentPct = 10.0),
                    RebalanceBucketAllocationDto("EQUITY_CORE", targetPct = 60.0, currentPct = 50.0),
                    RebalanceBucketAllocationDto("LIQUID_BUFFER", targetPct = 25.0, currentPct = 20.0)
                )
            )
        )
        val alloc = LocalTradeSimulator.allocateLumpsum(100000.0, plan)
        val goldAlloc = alloc["GOLD_SILVER"] ?: 0.0
        assertTrue("Gold must receive at least 50% gap priority fill (>= 2500)", goldAlloc >= 2500.0)
        assertEquals(100000.0, alloc.values.sum(), 0.01)
    }

    @Test
    fun testScenario25_allocateLumpsumCrashProtectionLiquidBufferExclusion() {
        val plan = RebalancePlanDto(
            drawdownContext = DrawdownContextDto(currentDrawdownPct = 15.0),
            buySide = BuySidePlanDto(
                totalToInvest = 100000.0,
                buckets = listOf(
                    RebalanceBucketAllocationDto("GOLD_SILVER", targetPct = 15.0, currentPct = 10.0),
                    RebalanceBucketAllocationDto("EQUITY_CORE", targetPct = 60.0, currentPct = 50.0),
                    RebalanceBucketAllocationDto("LIQUID_BUFFER", targetPct = 15.0, currentPct = 10.0),
                    RebalanceBucketAllocationDto("ARBITRAGE", targetPct = 10.0, currentPct = 5.0)
                )
            )
        )
        val alloc = LocalTradeSimulator.allocateLumpsum(100000.0, plan)
        assertNull("Liquid buffer must be excluded during 15% drawdown", alloc["LIQUID_BUFFER"])
        assertNull("Arbitrage must be excluded during 15% drawdown", alloc["ARBITRAGE"])
        assertTrue("Equity Core must receive allocation", (alloc["EQUITY_CORE"] ?: 0.0) > 0.0)
        assertTrue("Gold must receive allocation", (alloc["GOLD_SILVER"] ?: 0.0) > 0.0)
        assertEquals(100000.0, alloc.values.sum(), 0.01)
    }
}
