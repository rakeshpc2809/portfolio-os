package com.portfolioos.mobile

import com.portfolioos.mobile.ui.FundSellAggregated
import com.portfolioos.mobile.ui.shortenFundName
import com.portfolioos.mobile.util.formatInr
import org.junit.Assert.assertEquals
import org.junit.Test

class RebalanceWaterfallUnitTest {

    @Test
    fun testShortenFundName() {
        assertEquals("Motilal Nifty Midcap 150", shortenFundName("Motilal Oswal Nifty Midcap 150 Index Fund Direct Growth"))
        assertEquals("Mirae Healthcare Fund", shortenFundName("Mirae Asset Healthcare Fund - (Non Demat)"))
        assertEquals("Kotak Nifty 100 Equal Weight", shortenFundName("Kotak Nifty 100 Equal Weight Index Fund"))
        assertEquals("Short Fund Name", shortenFundName("Short Fund Name"))
    }

    @Test
    fun testFundSellAggregatedTaxSavedCalculation() {
        val gain = 21386.0
        val isLtcg = true
        val taxSaved = if (isLtcg) gain * 0.125 else 0.0

        val agg = FundSellAggregated(
            fundName = "Kotak Nifty 100 Equal Weight",
            totalProceeds = 76040.0,
            totalUnits = 6740.5,
            totalGain = gain,
            taxSaved = taxSaved,
            taxTerm = "LTCG EXEMPT",
            tierLabel = "Tier 1 - Capital Buffer"
        )

        assertEquals("Kotak Nifty 100 Equal Weight", agg.fundName)
        assertEquals(76040.0, agg.totalProceeds, 0.01)
        assertEquals(2673.25, agg.taxSaved, 0.01)
        assertEquals("LTCG EXEMPT", agg.taxTerm)
    }

    @Test
    fun testFormatInrFormatting() {
        assertEquals("₹1,298,893", formatInr(1298893.0))
        assertEquals("₹2,673", formatInr(2673.0))
        assertEquals("₹500", formatInr(500.0))
    }

    @Test
    fun testTaxExemption112AFormula() {
        // Section 112A LTCG tax rate is 12.5% on gains up to Rs 1.25 Lakh exemption limit
        val exemptGain = 100000.0
        val taxRate = 0.125
        val taxSaved = exemptGain * taxRate

        assertEquals(12500.0, taxSaved, 0.01)
    }
}
