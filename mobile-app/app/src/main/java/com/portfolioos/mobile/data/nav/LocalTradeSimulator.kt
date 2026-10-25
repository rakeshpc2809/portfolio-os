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
    private const val CESS_MULTIPLIER = 1.04           // 4% Health & Education Cess

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
        var ltcgEquity = 0.0
        var stcgEquity = 0.0
        var ltcgNonEquity = 0.0
        var slabRateGain = 0.0

        for (lot in matchingLots) {
            if (remainingUnitsToSell <= 0.0) break

            val unitsFromLot = min(remainingUnitsToSell, lot.units)
            val effectiveCostPerUnit = if (lot.grandfatheredNav != null && lot.grandfatheredNav > 0.0) {
                val fmvFloor = max(lot.costPerUnit, lot.grandfatheredNav)
                if (request.pricePerUnit >= lot.costPerUnit) {
                    min(request.pricePerUnit, fmvFloor)
                } else {
                    lot.costPerUnit
                }
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
                    ltcgNonEquity += gainForSlice
                } else {
                    slabRateGain += gainForSlice
                }
            } else if (isGoldOrIntl) {
                if (holdingDays >= 730L) {
                    ltcgNonEquity += gainForSlice
                } else {
                    slabRateGain += gainForSlice
                }
            } else if (holdingDays >= 365L) {
                ltcgEquity += gainForSlice
            } else {
                stcgEquity += gainForSlice
            }
        }

        val grossProceeds = actualUnitsSold * request.pricePerUnit
        val grossGain = (ltcgEquity + stcgEquity + ltcgNonEquity + slabRateGain)

        // Tax calculation with Section 112A ₹1.25L exemption
        val exemptionApplied = if (ltcgEquity > 0.0) min(ltcgEquity, LTCG_EXEMPTION_LIMIT) else 0.0
        val taxableLtcg = max(0.0, ltcgEquity - exemptionApplied)
        val ltcgTax = (taxableLtcg + max(0.0, ltcgNonEquity)) * LTCG_EQUITY_TAX_RATE

        val taxableStcg = max(0.0, stcgEquity)
        val stcgTax = taxableStcg * STCG_EQUITY_TAX_RATE

        val taxableSlab = max(0.0, slabRateGain)
        val slabTax = taxableSlab * DEBT_SLAB_TAX_RATE

        val baseTax = ltcgTax + stcgTax + slabTax
        val totalTaxWithCess = baseTax * CESS_MULTIPLIER

        val postNetWorth = max(0.0, currentNetWorth - totalTaxWithCess)
        val postInvested = max(0.0, currentInvested - totalCostBasisSold)

        return TradeSimulationResultDto(
            isin = request.isin,
            schemeName = request.schemeName,
            tradeType = "DISPOSAL",
            units = actualUnitsSold,
            pricePerUnit = request.pricePerUnit,
            grossTradeAmount = grossProceeds,
            grossCapitalGain = grossGain,
            ltcgEquity = ltcgEquity,
            stcgEquity = stcgEquity,
            slabRateGain = slabRateGain,
            sec112aExemptionApplied = exemptionApplied,
            estimatedTaxLiability = totalTaxWithCess,
            postTradeNetWorth = postNetWorth,
            postTradeInvestedCost = postInvested,
            postTradeXirr = currentXirr,
            taxSummaryNotice = "Simulated Sell (FIFO): Realized ₹${grossGain} gain with ₹${totalTaxWithCess} tax liability."
        )
    }

    /**
     * Allocates available lumpsum cash across underweight asset buckets.
     */
    fun allocateLumpsum(
        availableCash: Double,
        rebalancePlan: RebalancePlanDto?
    ): Map<String, Double> {
        val buckets = rebalancePlan?.buySide?.buckets ?: emptyList()
        if (availableCash <= 0.0 || buckets.isEmpty()) return emptyMap()

        val underweights = buckets.mapNotNull { b ->
            val deficit = b.targetPct - b.currentPct
            if (deficit > 0.0) b.bucket to deficit else null
        }

        if (underweights.isEmpty()) {
            // If no deficit, allocate strictly by target percentages
            val totalTarget = buckets.sumOf { it.targetPct }
            if (totalTarget <= 0.0) return emptyMap()
            return buckets.associate { b ->
                b.bucket to (availableCash * (b.targetPct / totalTarget))
            }
        }

        val totalDeficit = underweights.sumOf { it.second }
        return underweights.associate { (bucket, deficit) ->
            bucket to (availableCash * (deficit / totalDeficit))
        }
    }
}
