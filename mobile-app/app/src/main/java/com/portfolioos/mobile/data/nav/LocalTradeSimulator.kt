package com.portfolioos.mobile.data.nav

import com.portfolioos.mobile.model.FlatTaxLotDto
import com.portfolioos.mobile.model.RebalancePlanDto
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.model.TradeSimulationRequestDto
import com.portfolioos.mobile.model.TradeSimulationResultDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

object LocalTradeSimulator {

    private const val LTCG_EXEMPTION_LIMIT = 125000.0 // Sec 112A ₹1.25L exemption (Finance Act 2024)
    private const val LTCG_EQUITY_TAX_RATE = 0.125     // 12.5%
    private const val STCG_EQUITY_TAX_RATE = 0.20      // 20%
    private const val DEBT_SLAB_TAX_RATE = 0.30        // 30% nominal slab

    /**
     * Executes an on-device FIFO trade simulation against local snapshot tax lots.
     */
    fun simulateTradeLocally(
        snapshot: SyncSnapshot,
        request: TradeSimulationRequestDto,
        evaluationDate: LocalDate = LocalDate.now()
    ): TradeSimulationResultDto {
        val currentNetWorth = snapshot.syncInfo?.currentValue ?: 0.0
        val currentInvested = snapshot.syncInfo?.totalInvested ?: 0.0
        val currentXirr = snapshot.syncInfo?.portfolioXirr ?: 0.0

        if (request.tradeType.equals("ACQUISITION", ignoreCase = true)) {
            val grossAmount = request.units * request.pricePerUnit
            return TradeSimulationResultDto(
                isin = request.isin,
                schemeName = request.schemeName,
                tradeType = "ACQUISITION",
                units = request.units,
                pricePerUnit = request.pricePerUnit,
                grossTradeAmount = grossAmount,
                grossCapitalGain = 0.0,
                ltcgEquity = 0.0,
                stcgEquity = 0.0,
                slabRateGain = 0.0,
                sec112aExemptionApplied = 0.0,
                estimatedTaxLiability = 0.0,
                postTradeNetWorth = currentNetWorth + grossAmount,
                postTradeInvestedCost = currentInvested + grossAmount,
                postTradeXirr = currentXirr,
                taxSummaryNotice = "Simulated Buy (SIP): Invested ₹${grossAmount} added to portfolio cost basis."
            )
        }

        // Disposal: FIFO lot matching
        val matchingLots = (snapshot.taxLots ?: emptyList())
            .filter { it.isin.equals(request.isin, ignoreCase = true) }
            .sortedBy { it.buyDate }

        var remainingUnitsToSell = request.units
        var actualUnitsSold = 0.0
        var totalCostBasisSold = 0.0

        var grossLtcgEquity = 0.0
        var grossLtclEquity = 0.0
        var grossStcgEquity = 0.0
        var grossStclEquity = 0.0
        var grossLtcgNonEquity = 0.0
        var grossLtclNonEquity = 0.0
        var grossSlabGain = 0.0
        var grossSlabLoss = 0.0

        for (lot in matchingLots) {
            if (remainingUnitsToSell <= 0.0) break

            val unitsFromLot = min(remainingUnitsToSell, lot.units)
            val effectiveCostPerUnit = if (lot.grandfatheredNav != null && lot.grandfatheredNav > 0.0) {
                max(lot.costPerUnit, min(lot.grandfatheredNav, request.pricePerUnit))
            } else {
                lot.costPerUnit
            }

            val costForSlice = unitsFromLot * effectiveCostPerUnit
            val proceedsForSlice = unitsFromLot * request.pricePerUnit
            val gainForSlice = proceedsForSlice - costForSlice

            totalCostBasisSold += costForSlice
            remainingUnitsToSell -= unitsFromLot
            actualUnitsSold += unitsFromLot

            // Check if lot is long-term as of evaluationDate
            val buyDate = try {
                LocalDate.parse(lot.buyDate.take(10))
            } catch (_: Exception) {
                evaluationDate
            }
            val holdingDays = max(0L, ChronoUnit.DAYS.between(buyDate, evaluationDate))
            val upperClass = lot.taxClassification.uppercase()
            val isSpecifiedDebt = upperClass.contains("DEBT") || upperClass.contains("50AA")
            val isGoldOrIntl = upperClass.contains("GOLD") || upperClass.contains("SILVER") ||
                    upperClass.contains("INTERNATIONAL") || upperClass.contains("UNLISTED")

            if (isSpecifiedDebt) {
                val isLegacyDebt = buyDate.isBefore(LocalDate.of(2023, 4, 1))
                if (isLegacyDebt && holdingDays >= 730L) {
                    if (gainForSlice >= 0.0) grossLtcgNonEquity += gainForSlice else grossLtclNonEquity += -gainForSlice
                } else {
                    if (gainForSlice >= 0.0) grossSlabGain += gainForSlice else grossSlabLoss += -gainForSlice
                }
            } else if (isGoldOrIntl) {
                if (holdingDays >= 730L) {
                    if (gainForSlice >= 0.0) grossLtcgNonEquity += gainForSlice else grossLtclNonEquity += -gainForSlice
                } else {
                    if (gainForSlice >= 0.0) grossSlabGain += gainForSlice else grossSlabLoss += -gainForSlice
                }
            } else if (holdingDays >= 365L) {
                if (gainForSlice >= 0.0) grossLtcgEquity += gainForSlice else grossLtclEquity += -gainForSlice
            } else {
                if (gainForSlice >= 0.0) grossStcgEquity += gainForSlice else grossStclEquity += -gainForSlice
            }
        }

        val grossProceeds = actualUnitsSold * request.pricePerUnit
        val netLtcgEquity = grossLtcgEquity - grossLtclEquity
        val netStcgEquity = grossStcgEquity - grossStclEquity
        val netLtcgNonEquity = grossLtcgNonEquity - grossLtclNonEquity
        val netSlabGain = grossSlabGain - grossSlabLoss
        val grossGain = netLtcgEquity + netStcgEquity + netLtcgNonEquity + netSlabGain

        // Read remaining FY exemption headroom from snapshot instead of resetting to 1.25L
        val remainingHeadroom = snapshot.rebalancePlan?.sellSide?.taxSummary?.let { ts ->
            if (ts.exemptionHeadroomAfter < LTCG_EXEMPTION_LIMIT || ts.totalLtcgExempt > 0.0) {
                ts.exemptionHeadroomAfter
            } else {
                (ts.exemptionHeadroomBefore - ts.totalLtcgExempt).coerceAtLeast(0.0)
            }
        } ?: LTCG_EXEMPTION_LIMIT

        // Income-tax Act, 2025 Section 108 Intra-head loss set-off:
        // STCL sets off against STCG first
        val taxableStcg = max(0.0, netStcgEquity)
        val unabsorbedStcl = max(0.0, -netStcgEquity)

        // LTCL sets off against LTCG; remaining unabsorbed STCL sets off against LTCG
        val netLtcgEquityBeforeExemption = max(0.0, grossLtcgEquity - grossLtclEquity - unabsorbedStcl)

        // Section 112A exemption applies to net equity LTCG up to remaining FY headroom
        val exemptionApplied = if (netLtcgEquityBeforeExemption > 0.0) min(netLtcgEquityBeforeExemption, remainingHeadroom) else 0.0
        val taxableLtcgEquity = max(0.0, netLtcgEquityBeforeExemption - exemptionApplied)

        val taxableLtcgNonEquity = max(0.0, netLtcgNonEquity)
        val ltcgTax = (taxableLtcgEquity + taxableLtcgNonEquity) * LTCG_EQUITY_TAX_RATE
        val stcgTax = taxableStcg * STCG_EQUITY_TAX_RATE
        val slabTax = max(0.0, netSlabGain) * DEBT_SLAB_TAX_RATE

        // Cess alignment: Aligned directly with SimulationService without secondary 1.04 multiplication
        val estimatedTaxLiability = ltcgTax + stcgTax + slabTax

        val postNetWorth = max(0.0, currentNetWorth - estimatedTaxLiability)
        val postInvested = max(0.0, currentInvested - totalCostBasisSold)

        return TradeSimulationResultDto(
            isin = request.isin,
            schemeName = request.schemeName,
            tradeType = "DISPOSAL",
            units = actualUnitsSold,
            pricePerUnit = request.pricePerUnit,
            grossTradeAmount = grossProceeds,
            grossCapitalGain = grossGain,
            ltcgEquity = netLtcgEquity,
            stcgEquity = netStcgEquity,
            slabRateGain = netSlabGain,
            sec112aExemptionApplied = exemptionApplied,
            estimatedTaxLiability = estimatedTaxLiability,
            postTradeNetWorth = postNetWorth,
            postTradeInvestedCost = postInvested,
            postTradeXirr = currentXirr,
            taxSummaryNotice = "Simulated Sell (FIFO): Realized ₹${grossGain} gain with ₹${estimatedTaxLiability} tax liability."
        )
    }

    /**
     * Allocates available lumpsum cash across underweight asset buckets.
     * Enforces GOLD_FLOOR_BACKSTOP priority fill and drawdown regime-shifted protection.
     */
    fun allocateLumpsum(
        availableCash: Double,
        rebalancePlan: RebalancePlanDto?
    ): Map<String, Double> {
        val buckets = rebalancePlan?.buySide?.buckets ?: emptyList()
        if (availableCash <= 0.0 || buckets.isEmpty()) return emptyMap()

        var remainingCash = availableCash
        val allocations = mutableMapOf<String, Double>()

        val triggerType = rebalancePlan?.trigger?.type
        val drawdownPct = rebalancePlan?.drawdownContext?.currentDrawdownPct
            ?: rebalancePlan?.trigger?.drawdownContext?.currentDrawdownPct ?: 0.0

        // 1. Gold Floor Backstop: When trigger is GOLD_FLOOR_BACKSTOP, route up to 50% of the Gold gap first
        if ("GOLD_FLOOR_BACKSTOP".equals(triggerType, ignoreCase = true)) {
            val goldBucket = buckets.firstOrNull {
                it.bucket.contains("GOLD", ignoreCase = true) ||
                it.bucket.contains("COMMODITY", ignoreCase = true) ||
                it.bucket.contains("HEDGE", ignoreCase = true)
            }
            if (goldBucket != null) {
                val goldGapPct = max(0.0, goldBucket.targetPct - goldBucket.currentPct)
                if (goldGapPct > 0.0) {
                    val baseCorpus = rebalancePlan?.buySide?.totalToInvest?.takeIf { it > 0.0 } ?: availableCash
                    val goldTargetAmount = (goldGapPct / 100.0) * baseCorpus
                    val backstopFill = min(remainingCash, goldTargetAmount * 0.50).takeIf { it > 0.0 } ?: min(remainingCash, availableCash * 0.50)
                    allocations[goldBucket.bucket] = backstopFill
                    remainingCash -= backstopFill
                }
            }
        }

        if (remainingCash <= 0.0) return allocations

        // 2. Drawdown Regime-Shifted Target Awareness:
        // During market crash (drawdown >= 10%), never route fresh lumpsum cash into LIQUID_BUFFER / ARBITRAGE!
        val effectiveBuckets = if (drawdownPct >= 10.0) {
            buckets.filterNot {
                it.bucket.contains("LIQUID", ignoreCase = true) ||
                it.bucket.contains("ARBITRAGE", ignoreCase = true)
            }
        } else {
            buckets
        }

        val underweights = effectiveBuckets.mapNotNull { b ->
            val deficit = b.targetPct - b.currentPct
            if (deficit > 0.0) b.bucket to deficit else null
        }

        if (underweights.isEmpty()) {
            val totalTarget = effectiveBuckets.sumOf { it.targetPct }
            if (totalTarget > 0.0) {
                for (b in effectiveBuckets) {
                    val share = remainingCash * (b.targetPct / totalTarget)
                    allocations[b.bucket] = (allocations[b.bucket] ?: 0.0) + share
                }
            }
            return allocations
        }

        val totalDeficit = underweights.sumOf { it.second }
        for ((bucket, deficit) in underweights) {
            val share = remainingCash * (deficit / totalDeficit)
            allocations[bucket] = (allocations[bucket] ?: 0.0) + share
        }

        return allocations
    }
}
