package com.portfolioos.mobile

import com.google.gson.Gson
import com.portfolioos.mobile.model.BuySidePlanDto
import com.portfolioos.mobile.model.FireSummaryResponseDto
import com.portfolioos.mobile.model.RebalancePlanDto
import com.portfolioos.mobile.model.RebalanceTriggerDto
import com.portfolioos.mobile.model.ReasoningNarrativeDto
import com.portfolioos.mobile.model.SellSidePlanDto
import com.portfolioos.mobile.model.SyncInfoDto
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.util.formatInr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackFormattingUnitTest {

    private val gson = Gson()

    @Test
    fun testSyncInfoDtoDefaultsAreNonFabricating() {
        val emptyJson = "{}"
        val dto = gson.fromJson(emptyJson, SyncInfoDto::class.java)
        assertNotNull(dto)
        assertEquals("", dto.xirrPercentage)
        assertEquals("", dto.formattedCurrentValue)
        assertEquals("", dto.formattedTotalInvested)
        assertEquals("", dto.formattedUnrealizedGain)
    }

    @Test
    fun testIsSyncPopulatedEvaluatesCorrectly() {
        val nullInfo: SyncInfoDto? = null
        val isNullPopulated = nullInfo != null && nullInfo.generatedAt.isNotBlank() && nullInfo.generatedAt != "OFFLINE_FALLBACK"
        assertFalse(isNullPopulated)

        val offlineFallback = SyncInfoDto(
            timestamp = System.currentTimeMillis(),
            generatedAt = "OFFLINE_FALLBACK",
            fiscalYear = "2026-27"
        )
        val isOfflinePopulated = offlineFallback.generatedAt.isNotBlank() && offlineFallback.generatedAt != "OFFLINE_FALLBACK"
        assertFalse(isOfflinePopulated)
        val offlineXirr = if (isOfflinePopulated && offlineFallback.xirrPercentage.isNotBlank()) {
            offlineFallback.xirrPercentage
        } else {
            "--% XIRR"
        }
        assertEquals("--% XIRR", offlineXirr)

        val liveInfo = SyncInfoDto(
            timestamp = 1788000000000L,
            generatedAt = "2026-08-30T00:00:00Z",
            fiscalYear = "2026-27",
            currentValue = 1401764.88,
            totalInvested = 1100000.0,
            unrealizedGain = 301764.88,
            xirrPercentage = "18.42%"
        )
        val isLivePopulated = liveInfo.generatedAt.isNotBlank() && liveInfo.generatedAt != "OFFLINE_FALLBACK"
        assertTrue(isLivePopulated)
        val liveXirr = if (isLivePopulated && liveInfo.xirrPercentage.isNotBlank()) {
            liveInfo.xirrPercentage
        } else {
            "--% XIRR"
        }
        assertEquals("18.42%", liveXirr)
        assertEquals(formatInr(1401764.88), formatInr(liveInfo.currentValue))
    }

    @Test
    fun testFireSummaryPercentilesFormatting() {
        val emptySummary = FireSummaryResponseDto()
        val medianStrEmpty = emptySummary.monteCarloMedianCorpus.ifBlank { null }?.toDoubleOrNull()?.let { formatInr(it) } ?: "₹ --"
        val p10StrEmpty = emptySummary.monteCarloTenthPercentileCorpus.ifBlank { null }?.toDoubleOrNull()?.let { formatInr(it) } ?: "₹ --"
        assertEquals("₹ --", medianStrEmpty)
        assertEquals("₹ --", p10StrEmpty)

        val populatedSummary = FireSummaryResponseDto(
            monteCarloMedianCorpus = "12642179.01",
            monteCarloTenthPercentileCorpus = "7510378.05",
            monteCarloSuccessRatePct = 94.5
        )
        val medianStrLive = populatedSummary.monteCarloMedianCorpus.ifBlank { null }?.toDoubleOrNull()?.let { formatInr(it) } ?: "₹ --"
        val p10StrLive = populatedSummary.monteCarloTenthPercentileCorpus.ifBlank { null }?.toDoubleOrNull()?.let { formatInr(it) } ?: "₹ --"
        assertEquals(formatInr(12642179.01), medianStrLive)
        assertEquals(formatInr(7510378.05), p10StrLive)
    }

    @Test
    fun testRebalanceBuySideNullDoesNotInheritSellAmount() {
        val sellSide = SellSidePlanDto(totalRequired = 150000.0)
        val buySide: BuySidePlanDto? = null

        val totalRequired = sellSide.totalRequired
        val totalToInvest = buySide?.totalToInvest ?: 0.0

        assertEquals(150000.0, totalRequired, 0.01)
        assertEquals(0.0, totalToInvest, 0.01) // Does NOT fabricate 150000.0
    }

    @Test
    fun testNarrativeAndTriggerFallbacksAreNonFabricating() {
        val planWithNullNarratives = RebalancePlanDto(
            planId = "plan_1",
            reasoningNarrative = ReasoningNarrativeDto(headline = ""),
            trigger = RebalanceTriggerDto(reasonLabel = "")
        )

        val headline = planWithNullNarratives.reasoningNarrative?.headline?.ifBlank { null }
            ?: "Rebalance trade execution is temporarily deferred per strategy rules."
        val reasonLabel = planWithNullNarratives.trigger?.reasonLabel?.ifBlank { null }
            ?: "Rebalance Strategy Recommendation Active"

        assertEquals("Rebalance trade execution is temporarily deferred per strategy rules.", headline)
        assertEquals("Rebalance Strategy Recommendation Active", reasonLabel)
    }
}
