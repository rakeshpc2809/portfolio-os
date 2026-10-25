package com.portfolioos.mobile.data.nav

import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.FlatTaxLotDto
import com.portfolioos.mobile.model.SyncInfoDto
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.util.formatInr
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

object LocalPortfolioValuator {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Advances calendar time on-device and revalues the portfolio snapshot using
     * the latest available NAV map (e.g. from AMFI direct feed or local cache).
     *
     * @param snapshot The existing cached or synchronized snapshot
     * @param navMap Map of ISIN to latest NAV per unit
     * @param evaluationDate Target evaluation date (defaults to LocalDate.now())
     * @param navAsOfTime Timestamp when the NAVs were fetched (defaults to LocalDateTime.now())
     */
    fun revalueSnapshot(
        snapshot: SyncSnapshot,
        navMap: Map<String, Double>,
        evaluationDate: LocalDate = LocalDate.now(),
        navAsOfTime: LocalDateTime = LocalDateTime.now()
    ): SyncSnapshot {
        val oldTaxLots = snapshot.taxLots ?: emptyList()
        val oldHoldings = snapshot.holdings ?: emptyList()
        val oldSyncInfo = snapshot.syncInfo

        // 1. Advance holding days and LTCG status on individual tax lots
        val updatedTaxLots = oldTaxLots.map { lot ->
            advanceTaxLot(lot, evaluationDate)
        }

        // 2. Group exit load free units and compute revalued holding values
        val lotsByIsin = updatedTaxLots.groupBy { it.isin }

        var totalCurrentValue = 0.0
        var totalInvestedValue = 0.0

        val updatedHoldings = oldHoldings.map { holding ->
            val latestNav = navMap[holding.isin]
                ?: holding.currentNav.takeIf { it > 0.0 }
                ?: (if (holding.totalUnits > 0.0) holding.currentValue / holding.totalUnits else 0.0)
            val revaluedCurrentVal = if (latestNav > 0.0) holding.totalUnits * latestNav else holding.currentValue
            val investedVal = if (holding.investedValue > 0.0) holding.investedValue else holding.totalUnits * holding.avgCost

            totalCurrentValue += revaluedCurrentVal
            totalInvestedValue += investedVal

            // Calculate exit load free units using scheme-specific exit-load rules
            val isinLots = lotsByIsin[holding.isin] ?: emptyList()
            val reqDays = requiredExitLoadDays(holding.isin)
            val exitLoadFreeUnits = if (isinLots.isNotEmpty()) {
                isinLots.filter { it.holdingDays >= reqDays }.sumOf { it.units }
            } else {
                holding.exitLoadFreeUnits
            }

            holding.copy(
                currentNav = latestNav,
                currentValue = revaluedCurrentVal,
                investedValue = investedVal,
                formattedCurrentValue = formatInr(revaluedCurrentVal),
                formattedInvestedValue = formatInr(investedVal),
                exitLoadFreeUnits = exitLoadFreeUnits
            )
        }

        // 3. Compute portfolio weight percentages post-revaluation
        val finalHoldings = if (totalCurrentValue > 0.0) {
            updatedHoldings.map { h ->
                val weightPct = (h.currentValue / totalCurrentValue) * 100.0
                h.copy(
                    portfolioPercentage = weightPct,
                    portfolioWeightPct = weightPct
                )
            }
        } else {
            updatedHoldings
        }

        val totalUnrealizedGain = totalCurrentValue - totalInvestedValue

        // 4. Calculate Ledger Age (days) vs Valuation Freshness (hours)
        val ledgerDate = parseDateOrFallback(oldSyncInfo?.generatedAt, evaluationDate)
        val ledgerAgeDays = max(0L, ChronoUnit.DAYS.between(ledgerDate, evaluationDate))

        val navFetchDate = parseDateTimeOrFallback(oldSyncInfo?.generatedAt, navAsOfTime)
        val valuationFreshnessHours = max(0L, ChronoUnit.HOURS.between(navFetchDate, navAsOfTime))

        val updatedSyncInfo = (oldSyncInfo ?: SyncInfoDto()).copy(
            currentValue = totalCurrentValue,
            totalInvested = totalInvestedValue,
            unrealizedGain = totalUnrealizedGain,
            formattedCurrentValue = formatInr(totalCurrentValue),
            formattedTotalInvested = formatInr(totalInvestedValue),
            formattedUnrealizedGain = formatInr(totalUnrealizedGain),
            ledgerAgeDays = ledgerAgeDays,
            valuationFreshnessHours = valuationFreshnessHours
        )

        return snapshot.copy(
            syncInfo = updatedSyncInfo,
            holdings = finalHoldings,
            taxLots = updatedTaxLots
        )
    }

    fun advanceTaxLot(lot: FlatTaxLotDto, asOfDate: LocalDate): FlatTaxLotDto {
        val buyDate = try {
            LocalDate.parse(lot.buyDate.take(10), DATE_FORMATTER)
        } catch (_: Exception) {
            asOfDate
        }

        val holdingDays = max(0L, ChronoUnit.DAYS.between(buyDate, asOfDate))

        // Equity / Hybrid: 365 days; Debt / Unlisted / Gold / International: 730 days
        val is24MonthLtcg = lot.taxClassification.contains("DEBT", ignoreCase = true) ||
                lot.taxClassification.contains("UNLISTED", ignoreCase = true) ||
                lot.taxClassification.contains("GOLD_SILVER", ignoreCase = true) ||
                lot.taxClassification.contains("INTERNATIONAL", ignoreCase = true)
        val ltcgThresholdDays = if (is24MonthLtcg) 730L else 365L

        val isLongTerm = holdingDays >= ltcgThresholdDays
        val daysToLtcg = if (isLongTerm) 0L else (ltcgThresholdDays - holdingDays)

        return lot.copy(
            holdingDays = holdingDays,
            isLongTerm = isLongTerm,
            daysToLtcg = daysToLtcg
        )
    }

    private fun requiredExitLoadDays(isin: String): Long = when (isin.uppercase()) {
        "INF205K01KR8", "INF247L01BM8" -> 15L  // Invesco Arbitrage & Motilal Gold/Silver FoF
        "INF879O01027" -> 730L                 // PPFAS Flexi Cap (2-year exit load window)
        "INF204K01K15" -> 365L                 // Nippon Small Cap (1-year exit load window)
        else -> 0L                             // Direct Index Funds (0 exit load)
    }

    private fun parseDateOrFallback(dateStr: String?, fallback: LocalDate): LocalDate {
        if (dateStr.isNullOrBlank()) return fallback
        return try {
            LocalDate.parse(dateStr.take(10), DATE_FORMATTER)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseDateTimeOrFallback(dateTimeStr: String?, fallback: LocalDateTime): LocalDateTime {
        if (dateTimeStr.isNullOrBlank()) return fallback
        return try {
            LocalDateTime.parse(dateTimeStr.take(19))
        } catch (_: Exception) {
            fallback
        }
    }
}
