package com.portfolioos.core.service;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.*;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
import com.portfolioos.core.xirr.CashFlow;
import com.portfolioos.core.xirr.XirrEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class SimulationService {

    private final LedgerCacheService cacheService;
    private final XirrEngine xirrEngine = new XirrEngine();

    public SimulationService(LedgerCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public static record TradeSimulationRequest(
        String isin,
        String schemeName,
        BigDecimal units,
        BigDecimal pricePerUnit,
        String tradeDate,
        String tradeType // DISPOSAL or ACQUISITION
    ) {}

    public static record TradeSimulationResult(
        String isin,
        String schemeName,
        String tradeType,
        BigDecimal units,
        BigDecimal pricePerUnit,
        BigDecimal grossTradeAmount,
        BigDecimal grossCapitalGain,
        BigDecimal ltcgEquity,
        BigDecimal stcgEquity,
        BigDecimal slabRateGain, // Renamed from debtGain to accurately reflect all slab-taxed gains (specified debt, STCG Gold/Intl/SGB)
        BigDecimal sec112aExemptionApplied,
        BigDecimal estimatedTaxLiability,
        BigDecimal postTradeNetWorth,
        BigDecimal postTradeInvestedCost,
        BigDecimal postTradeXirr,
        String taxSummaryNotice
    ) {
        // Backwards compatibility getter alias for legacy callers querying debtGain()
        public BigDecimal debtGain() {
            return slabRateGain;
        }
    }

    public TradeSimulationResult simulateTrade(TradeSimulationRequest req) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> existingEvents = state.events();
        Map<String, BigDecimal> navMap = state.navMap();

        LocalDate tradeDate = (req.tradeDate() != null && !req.tradeDate().isBlank())
            ? LocalDate.parse(req.tradeDate())
            : LocalDate.now();

        String targetFy = TaxRulesLoader.detectFiscalYear(tradeDate);
        TaxRulesConfig rules = TaxRulesLoader.loadRules(targetFy);

        BigDecimal unitsBd = req.units() != null ? req.units().setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal priceBd = req.pricePerUnit() != null ? req.pricePerUnit().setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal grossAmount = unitsBd.multiply(priceBd).setScale(2, RoundingMode.HALF_UP);

        EventType type = "ACQUISITION".equalsIgnoreCase(req.tradeType()) ? EventType.ACQUISITION : EventType.DISPOSAL;
        String isin = (req.isin() != null && !req.isin().isBlank()) ? req.isin() : "SIMULATED_ASSET";
        String name = (req.schemeName() != null && !req.schemeName().isBlank()) ? req.schemeName() : "Simulated Fund";

        TaxEvent simEvent = new TaxEvent(
            "SIM_" + System.currentTimeMillis(),
            isin,
            name,
            isin,
            type,
            tradeDate,
            unitsBd,
            priceBd,
            grossAmount,
            "MANUAL_SIMULATION",
            java.time.Instant.now()
        );

        List<TaxEvent> simEvents = new ArrayList<>(existingEvents);
        simEvents.add(simEvent);

        FifoMatcher matcher = new FifoMatcher();
        FifoMatcher.FifoResult simResult = matcher.processEvents(simEvents);

        BigDecimal ltcgEquity = BigDecimal.ZERO;
        BigDecimal stcgEquity = BigDecimal.ZERO;
        BigDecimal ltcgGoldInternational = BigDecimal.ZERO;
        BigDecimal stcgSlabRateGain = BigDecimal.ZERO;
        BigDecimal totalGain = BigDecimal.ZERO;

        if (type == EventType.DISPOSAL) {
            for (MatchedLot match : simResult.matchedLots()) {
                if (match.disposalEventId().equals(simEvent.id())) {
                    AssetCategory category = match.assetCategory();
                    TaxTerm term = match.taxTerm();
                    BigDecimal gain = match.realizedGain();
                    totalGain = totalGain.add(gain);

                    switch (category) {
                        case EQUITY -> {
                            if (term == TaxTerm.LONG_TERM) {
                                ltcgEquity = ltcgEquity.add(gain);
                            } else {
                                stcgEquity = stcgEquity.add(gain);
                            }
                        }
                        case GOLD_SILVER, INTERNATIONAL, SGB -> {
                            if (term == TaxTerm.LONG_TERM) {
                                ltcgGoldInternational = ltcgGoldInternational.add(gain);
                            } else {
                                stcgSlabRateGain = stcgSlabRateGain.add(gain);
                            }
                        }
                        case DEBT_SPECIFIED_50AA -> {
                            // Specified debt under Sec 50AA is always short term and taxed at SLAB_RATE
                            stcgSlabRateGain = stcgSlabRateGain.add(gain);
                        }
                        default -> throw new IllegalStateException("Unhandled AssetCategory for tax simulation: " + category);
                    }
                }
            }
        }

        // Use ExemptionTracker bound to target fiscal year
        ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(state.fifoResult().matchedLots(), targetFy);
        BigDecimal remainingExemptionLimit = new BigDecimal(exStatus.exemptionRemaining());

        BigDecimal exemptionApplied = BigDecimal.ZERO;
        BigDecimal taxableLtcgEquity = BigDecimal.ZERO;

        if (ltcgEquity.compareTo(BigDecimal.ZERO) > 0) {
            exemptionApplied = ltcgEquity.min(remainingExemptionLimit);
            taxableLtcgEquity = ltcgEquity.subtract(exemptionApplied).max(BigDecimal.ZERO);
        }

        // Calculate tax dynamic from rules object — NO hardcoded BigDecimal literal rates
        BigDecimal ltcgEquityTax = taxableLtcgEquity.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal stcgEquityTax = stcgEquity.max(BigDecimal.ZERO).multiply(rules.equityStcgRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ltcgGoldTax = ltcgGoldInternational.max(BigDecimal.ZERO).multiply(rules.goldInternationalLtcgRate()).setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal estimatedTax = ltcgEquityTax.add(stcgEquityTax).add(ltcgGoldTax);

        // Compute post-trade net worth & XIRR
        BigDecimal postInvested = BigDecimal.ZERO;
        BigDecimal postCurrentVal = BigDecimal.ZERO;

        for (Lot lot : simResult.openLots()) {
            postInvested = postInvested.add(lot.remainingUnits().multiply(lot.costPerUnit()));
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            postCurrentVal = postCurrentVal.add(lot.remainingUnits().multiply(nav));
        }

        List<CashFlow> cashFlows = new ArrayList<>();
        for (TaxEvent ev : simEvents) {
            BigDecimal amt = (ev.eventType() == EventType.ACQUISITION || ev.eventType() == EventType.SIP_INSTALMENT)
                ? ev.grossAmount().negate()
                : ev.grossAmount();
            cashFlows.add(new CashFlow(ev.eventDate(), amt));
        }
        if (postCurrentVal.compareTo(BigDecimal.ZERO) > 0) {
            cashFlows.add(new CashFlow(tradeDate, postCurrentVal));
        }

        double postXirrVal = xirrEngine.calculateXirr(cashFlows);
        BigDecimal postXirr = BigDecimal.valueOf(postXirrVal).setScale(2, RoundingMode.HALF_UP);

        String notice;
        if (type == EventType.DISPOSAL) {
            if (stcgSlabRateGain.compareTo(BigDecimal.ZERO) > 0) {
                notice = String.format("Simulated Sale (FY %s): Estimated Computed Tax Drag ₹%s (LTCG Exemption Used: ₹%s). Additional Gains: ₹%s (SLAB_RATE — not computed without income slab data).",
                    targetFy, estimatedTax.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    exemptionApplied.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    stcgSlabRateGain.setScale(2, RoundingMode.HALF_UP).toPlainString());
            } else {
                notice = String.format("Simulated Sale (FY %s): Estimated Tax Drag ₹%s (LTCG Exemption Used: ₹%s)",
                    targetFy, estimatedTax.setScale(2, RoundingMode.HALF_UP).toPlainString(), exemptionApplied.setScale(2, RoundingMode.HALF_UP).toPlainString());
            }
        } else {
            notice = String.format("Simulated Purchase: Added ₹%s investment to portfolio.", grossAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }

        return new TradeSimulationResult(
            isin,
            name,
            type.name(),
            unitsBd,
            priceBd,
            grossAmount,
            totalGain.setScale(2, RoundingMode.HALF_UP),
            ltcgEquity.setScale(2, RoundingMode.HALF_UP),
            stcgEquity.setScale(2, RoundingMode.HALF_UP),
            stcgSlabRateGain.setScale(2, RoundingMode.HALF_UP),
            exemptionApplied.setScale(2, RoundingMode.HALF_UP),
            estimatedTax.setScale(2, RoundingMode.HALF_UP),
            postCurrentVal.setScale(2, RoundingMode.HALF_UP),
            postInvested.setScale(2, RoundingMode.HALF_UP),
            postXirr,
            notice
        );
    }
}
