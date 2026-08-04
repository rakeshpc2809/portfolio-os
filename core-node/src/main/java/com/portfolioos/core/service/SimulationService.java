package com.portfolioos.core.service;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.*;
import com.portfolioos.core.tax.TaxClassifier;
import com.portfolioos.core.xirr.XirrEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SimulationService {

    private final LedgerCacheService cacheService;

    public SimulationService(LedgerCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public static record TradeSimulationRequest(
        String isin,
        String schemeName,
        double units,
        double pricePerUnit,
        String tradeDate,
        String tradeType // DISPOSAL or ACQUISITION
    ) {}

    public static record TradeSimulationResult(
        String isin,
        String schemeName,
        String tradeType,
        double units,
        double pricePerUnit,
        double grossTradeAmount,
        double grossCapitalGain,
        double ltcgEquity,
        double stcgEquity,
        double debtGain,
        double sec112aExemptionApplied,
        double estimatedTaxLiability,
        double postTradeNetWorth,
        double postTradeInvestedCost,
        double postTradeXirr,
        String taxSummaryNotice
    ) {}

    public TradeSimulationResult simulateTrade(TradeSimulationRequest req) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> existingEvents = state.events();
        Map<String, BigDecimal> navMap = state.navMap();

        LocalDate tradeDate = (req.tradeDate() != null && !req.tradeDate().isBlank())
            ? LocalDate.parse(req.tradeDate())
            : LocalDate.now();

        BigDecimal unitsBd = BigDecimal.valueOf(req.units()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal priceBd = BigDecimal.valueOf(req.pricePerUnit()).setScale(4, RoundingMode.HALF_UP);
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

        double ltcgEquity = 0.0;
        double stcgEquity = 0.0;
        double debtGain = 0.0;
        double totalGain = 0.0;

        if (type == EventType.DISPOSAL) {
            for (MatchedLot match : simResult.matchedLots()) {
                if (match.sellEvent().id().equals(simEvent.id())) {
                    Lot buy = match.buyLot();
                    long days = ChronoUnit.DAYS.between(buy.acquisitionDate(), simEvent.eventDate());
                    AssetCategory category = TaxClassifier.detectCategory(buy.assetId(), buy.assetName());
                    boolean isListed = TaxClassifier.isListed(buy.assetId(), buy.assetName());
                    TaxTerm term = TaxClassifier.classifyTaxTerm(category, days, "2026-27", isListed);

                    BigDecimal matchedUnits = match.matchedUnits();
                    BigDecimal cost = buy.pricePerUnit().multiply(matchedUnits);
                    BigDecimal sale = simEvent.pricePerUnit().multiply(matchedUnits);
                    BigDecimal gain = sale.subtract(cost);
                    totalGain += gain.doubleValue();

                    if (category == AssetCategory.EQUITY || category == AssetCategory.EQUITY_ORIENTED) {
                        if (term == TaxTerm.LONG_TERM) {
                            ltcgEquity += gain.doubleValue();
                        } else {
                            stcgEquity += gain.doubleValue();
                        }
                    } else {
                        debtGain += gain.doubleValue();
                    }
                }
            }
        }

        double previousLtcgRealized = 0.0;
        for (MatchedLot match : state.fifoResult().matchedLots()) {
            Lot buy = match.buyLot();
            TaxEvent sell = match.sellEvent();
            long days = ChronoUnit.DAYS.between(buy.acquisitionDate(), sell.eventDate());
            AssetCategory category = TaxClassifier.detectCategory(buy.assetId(), buy.assetName());
            boolean isListed = TaxClassifier.isListed(buy.assetId(), buy.assetName());
            TaxTerm term = TaxClassifier.classifyTaxTerm(category, days, "2026-27", isListed);
            if ((category == AssetCategory.EQUITY || category == AssetCategory.EQUITY_ORIENTED) && term == TaxTerm.LONG_TERM) {
                BigDecimal matchedUnits = match.matchedUnits();
                BigDecimal cost = buy.pricePerUnit().multiply(matchedUnits);
                BigDecimal sale = sell.pricePerUnit().multiply(matchedUnits);
                previousLtcgRealized += Math.max(0.0, sale.subtract(cost).doubleValue());
            }
        }

        double remainingExemptionLimit = Math.max(0.0, 125000.0 - previousLtcgRealized);
        double exemptionApplied = Math.min(Math.max(0.0, ltcgEquity), remainingExemptionLimit);
        double taxableLtcg = Math.max(0.0, ltcgEquity - exemptionApplied);
        double estimatedTax = (taxableLtcg * 0.125) + (Math.max(0.0, stcgEquity) * 0.20) + (Math.max(0.0, debtGain) * 0.30);

        // Compute post-trade net worth & XIRR
        double postInvested = 0.0;
        double postCurrentVal = 0.0;

        for (Lot lot : simResult.openLots()) {
            postInvested += lot.remainingUnits().multiply(lot.pricePerUnit()).doubleValue();
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.pricePerUnit());
            postCurrentVal += lot.remainingUnits().multiply(nav).doubleValue();
        }

        List<XirrEngine.CashFlow> cashFlows = new ArrayList<>();
        for (TaxEvent ev : simEvents) {
            double amt = (ev.eventType() == EventType.ACQUISITION)
                ? -ev.grossAmount().doubleValue()
                : ev.grossAmount().doubleValue();
            cashFlows.add(new XirrEngine.CashFlow(ev.eventDate(), amt));
        }
        if (postCurrentVal > 0) {
            cashFlows.add(new XirrEngine.CashFlow(tradeDate, postCurrentVal));
        }

        double postXirr = XirrEngine.calculateXirr(cashFlows);

        String notice = (type == EventType.DISPOSAL)
            ? String.format("Simulated Sale: Estimated Tax Drag ₹%,.2f (LTCG Exemption Used: ₹%,.2f)", estimatedTax, exemptionApplied)
            : String.format("Simulated Purchase: Added ₹%,.2f investment to portfolio.", grossAmount.doubleValue());

        return new TradeSimulationResult(
            isin,
            name,
            type.name(),
            req.units(),
            req.pricePerUnit(),
            grossAmount.doubleValue(),
            totalGain,
            ltcgEquity,
            stcgEquity,
            debtGain,
            exemptionApplied,
            estimatedTax,
            postCurrentVal,
            postInvested,
            postXirr,
            notice
        );
    }
}
