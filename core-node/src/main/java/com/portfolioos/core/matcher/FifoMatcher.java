package com.portfolioos.core.matcher;

import org.springframework.stereotype.Component;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.rules.TaxRulesLoader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class FifoMatcher {

    private static final java.time.LocalDate GRANDFATHER_CUTOFF = java.time.LocalDate.of(2018, 1, 31);

    public record FifoResult(List<Lot> openLots, List<MatchedLot> matchedLots) {}

    public FifoResult processEvents(List<TaxEvent> events) {
        return processEvents(events, java.util.Map.of());
    }

    public FifoResult processEvents(List<TaxEvent> events, java.util.Map<String, BigDecimal> fmv2018Map) {
        List<TaxEvent> sortedEvents = new ArrayList<>(events);
        sortedEvents.sort(Comparator.comparing(TaxEvent::eventDate).thenComparing(TaxEvent::ingestedAt));

        List<Lot> openLotsQueue = new ArrayList<>();
        List<MatchedLot> matchedLots = new ArrayList<>();

        for (TaxEvent event : sortedEvents) {
            switch (event.eventType()) {
                case ACQUISITION, SIP_INSTALMENT, DIVIDEND_REINVEST -> {
                    boolean isPre2018 = !event.eventDate().isAfter(GRANDFATHER_CUTOFF);
                    AssetCategory category = TaxClassifier.detectCategory(event.assetId(), event.assetName());
                    boolean isGrandfathered = isPre2018 && (category == AssetCategory.EQUITY);
                    BigDecimal fmv = (isGrandfathered && fmv2018Map != null) ? fmv2018Map.getOrDefault(event.assetId(), BigDecimal.ZERO) : BigDecimal.ZERO;

                    openLotsQueue.add(new Lot(
                        UUID.randomUUID().toString(),
                        event.assetId(),
                        event.assetName(),
                        event.eventDate(),
                        event.units(),
                        event.units(),
                        event.pricePerUnit(),
                        event.grossAmount(),
                        isGrandfathered,
                        fmv
                    ));
                }
                case BONUS -> {
                    boolean isPre2018 = !event.eventDate().isAfter(GRANDFATHER_CUTOFF);
                    AssetCategory category = TaxClassifier.detectCategory(event.assetId(), event.assetName());
                    boolean isGrandfathered = isPre2018 && (category == AssetCategory.EQUITY);
                    BigDecimal fmv = (isGrandfathered && fmv2018Map != null) ? fmv2018Map.getOrDefault(event.assetId(), BigDecimal.ZERO) : BigDecimal.ZERO;

                    openLotsQueue.add(new Lot(
                        UUID.randomUUID().toString(),
                        event.assetId(),
                        event.assetName(),
                        event.eventDate(),
                        event.units(),
                        event.units(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        isGrandfathered,
                        fmv
                    ));
                }
                case SPLIT -> {
                    BigDecimal splitRatio = event.units();
                    if (splitRatio.compareTo(BigDecimal.ZERO) > 0) {
                        for (int i = 0; i < openLotsQueue.size(); i++) {
                            Lot current = openLotsQueue.get(i);
                            if (current.assetId().equals(event.assetId())) {
                                BigDecimal newOriginal = current.originalUnits().multiply(splitRatio);
                                BigDecimal newRemaining = current.remainingUnits().multiply(splitRatio);
                                BigDecimal newCostPerUnit = BigDecimal.ZERO;
                                if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
                                    newCostPerUnit = current.totalCostBasis().divide(newRemaining, 4, RoundingMode.HALF_UP);
                                }
                                BigDecimal newFmv = current.fmv20180131();
                                if (newFmv != null && newFmv.compareTo(BigDecimal.ZERO) > 0) {
                                    newFmv = newFmv.divide(splitRatio, 4, RoundingMode.HALF_UP);
                                }
                                openLotsQueue.set(i, new Lot(
                                    current.lotId(), current.assetId(), current.assetName(),
                                    current.acquisitionDate(), newOriginal, newRemaining,
                                    newCostPerUnit, current.totalCostBasis(),
                                    current.isGrandfathered(), newFmv
                                ));
                            }
                        }
                    }
                }
                case DISPOSAL, SGB_MATURITY -> {
                    BigDecimal unitsToMatch = event.units();
                    boolean isSgbMaturity = event.eventType() == EventType.SGB_MATURITY;
                    int i = 0;

                    while (i < openLotsQueue.size() && unitsToMatch.compareTo(BigDecimal.ZERO) > 0) {
                        Lot currentLot = openLotsQueue.get(i);
                        if (!currentLot.assetId().equals(event.assetId()) || currentLot.remainingUnits().compareTo(BigDecimal.ZERO) <= 0) {
                            i++;
                            continue;
                        }

                        BigDecimal matchedUnits = unitsToMatch.min(currentLot.remainingUnits());
                        BigDecimal costBasisSlice = matchedUnits.multiply(currentLot.costPerUnit());
                        BigDecimal saleProceedsSlice = matchedUnits.multiply(event.pricePerUnit());
                        
                        BigDecimal effectiveCostBasis = costBasisSlice;
                        if (currentLot.isGrandfathered() && currentLot.fmv20180131() != null && currentLot.fmv20180131().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal lotFmv = matchedUnits.multiply(currentLot.fmv20180131());
                            BigDecimal lotLowerBound = lotFmv.min(saleProceedsSlice);
                            effectiveCostBasis = costBasisSlice.max(lotLowerBound);
                        }
                        BigDecimal realizedGain = saleProceedsSlice.subtract(effectiveCostBasis);
                        
                        long holdingDays = ChronoUnit.DAYS.between(currentLot.acquisitionDate(), event.eventDate());
                        AssetCategory category = TaxClassifier.detectCategory(event.assetId(), event.assetName());
                        boolean isListed = TaxClassifier.isListed(event.assetId(), event.assetName());

                        TaxTerm taxTerm = isSgbMaturity ? TaxTerm.EXEMPT 
                            : TaxClassifier.classifyTaxTerm(category, holdingDays, TaxRulesLoader.detectFiscalYear(event.eventDate()), isListed, currentLot.acquisitionDate(), event.eventDate());

                        matchedLots.add(new MatchedLot(
                            UUID.randomUUID().toString(),
                            event.id(),
                            currentLot.lotId(),
                            event.assetId(),
                            currentLot.acquisitionDate(),
                            event.eventDate(),
                            matchedUnits,
                            effectiveCostBasis,
                            saleProceedsSlice,
                            realizedGain,
                            holdingDays,
                            taxTerm,
                            category
                        ));

                        unitsToMatch = unitsToMatch.subtract(matchedUnits);
                        BigDecimal updatedRemaining = currentLot.remainingUnits().subtract(matchedUnits);

                        if (updatedRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                            openLotsQueue.remove(i);
                        } else {
                            BigDecimal updatedRemainingCost = currentLot.totalCostBasis().subtract(costBasisSlice).max(BigDecimal.ZERO);
                            openLotsQueue.set(i, currentLot.withRemainingUnitsAndCost(updatedRemaining, currentLot.costPerUnit(), updatedRemainingCost));
                            i++;
                        }
                    }
                }
                case MERGER -> {
                    // Corporate merger event
                    BigDecimal swapRatio = event.pricePerUnit().compareTo(BigDecimal.ZERO) > 0 ? event.pricePerUnit() : event.units();
                    for (int j = 0; j < openLotsQueue.size(); j++) {
                        Lot current = openLotsQueue.get(j);
                        if (current.assetId().equals(event.assetId())) {
                            BigDecimal newOriginal = swapRatio.compareTo(BigDecimal.ZERO) > 0 ? current.originalUnits().multiply(swapRatio) : current.originalUnits();
                            BigDecimal newRemaining = swapRatio.compareTo(BigDecimal.ZERO) > 0 ? current.remainingUnits().multiply(swapRatio) : current.remainingUnits();
                            BigDecimal newCostPerUnit = BigDecimal.ZERO;
                            if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
                                newCostPerUnit = current.totalCostBasis().divide(newRemaining, 4, RoundingMode.HALF_UP);
                            }

                            String newAssetId = (event.isin() != null && !event.isin().isBlank()) ? event.isin() : current.assetId();
                            String newAssetName = (event.assetName() != null && !event.assetName().isBlank()) ? event.assetName() : current.assetName();

                            openLotsQueue.set(j, current.withAssetDetails(newAssetId, newAssetName, newOriginal, newRemaining, newCostPerUnit));
                        }
                    }
                }
                case SGB_INTEREST -> {
                    // cash income, doesn't impact stock lots
                }
            }
        }

        return new FifoResult(openLotsQueue, matchedLots);
    }
}
