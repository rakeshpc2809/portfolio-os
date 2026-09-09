package com.portfolioos.core.matcher;

import com.portfolioos.core.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class FifoMatcherPropertyTest {

    private final FifoMatcher matcher = new FifoMatcher();

    // =========================================================================
    // PROPERTY 1: Unit Conservation (Acquisitions, SIPs, Bonuses, Disposals)
    // =========================================================================
    @Property(tries = 1000)
    void unitConservationAcrossAcquisitionsAndDisposals(
        @ForAll("inboundEvents") List<TaxEvent> inboundEvents,
        @ForAll @DoubleRange(min = 0.05, max = 0.95) double disposalFraction
    ) {
        // Calculate total inbound units
        BigDecimal totalInboundUnits = inboundEvents.stream()
            .map(TaxEvent::units)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Derive disposal units that are strictly <= totalInboundUnits
        BigDecimal disposalUnits = totalInboundUnits
            .multiply(BigDecimal.valueOf(disposalFraction))
            .setScale(4, RoundingMode.DOWN);

        if (disposalUnits.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        LocalDate lastInboundDate = inboundEvents.stream()
            .map(TaxEvent::eventDate)
            .max(Comparator.naturalOrder())
            .orElse(LocalDate.of(2025, 1, 1));

        TaxEvent disposal = new TaxEvent(
            UUID.randomUUID().toString(),
            "INF109K018C5",
            "Parag Parikh Flexi Cap Fund",
            "INF109K018C5",
            EventType.DISPOSAL,
            lastInboundDate.plusDays(30),
            disposalUnits,
            new BigDecimal("75.5000"),
            disposalUnits.multiply(new BigDecimal("75.5000")),
            "test-doc",
            Instant.now()
        );

        List<TaxEvent> allEvents = new ArrayList<>(inboundEvents);
        allEvents.add(disposal);

        FifoMatcher.FifoResult result = matcher.processEvents(allEvents);

        BigDecimal totalOpenUnits = result.openLots().stream()
            .map(Lot::remainingUnits)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMatchedUnits = result.matchedLots().stream()
            .map(MatchedLot::unitsMatched)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Conservation: Inbound == Open + Matched
        assertEquals(
            totalInboundUnits.stripTrailingZeros(),
            totalOpenUnits.add(totalMatchedUnits).stripTrailingZeros(),
            "Total inbound units must equal total open units plus matched units"
        );

        // All disposal units were matched
        assertEquals(
            disposalUnits.stripTrailingZeros(),
            totalMatchedUnits.stripTrailingZeros(),
            "All disposal units must be matched when disposal <= total inbound"
        );
    }

    // =========================================================================
    // PROPERTY 2: Strict FIFO Monotonicity
    // =========================================================================
    @Property(tries = 1000)
    void strictFifoMonotonicity(
        @ForAll("chronologicalAcquisitions") List<TaxEvent> acquisitions,
        @ForAll @DoubleRange(min = 0.20, max = 0.80) double disposalFraction
    ) {
        BigDecimal totalUnits = acquisitions.stream()
            .map(TaxEvent::units)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unitsToSell = totalUnits.multiply(BigDecimal.valueOf(disposalFraction)).setScale(4, RoundingMode.DOWN);
        if (unitsToSell.compareTo(BigDecimal.ZERO) <= 0) return;

        LocalDate maxAcqDate = acquisitions.stream()
            .map(TaxEvent::eventDate)
            .max(Comparator.naturalOrder())
            .orElse(LocalDate.of(2025, 1, 1));

        TaxEvent disposal = new TaxEvent(
            UUID.randomUUID().toString(),
            "INF204K01H36",
            "Nippon India Nifty 50 Index Fund",
            "INF204K01H36",
            EventType.DISPOSAL,
            maxAcqDate.plusDays(15),
            unitsToSell,
            new BigDecimal("50.0000"),
            unitsToSell.multiply(new BigDecimal("50.0000")),
            "test-doc",
            Instant.now()
        );

        List<TaxEvent> events = new ArrayList<>(acquisitions);
        events.add(disposal);

        FifoMatcher.FifoResult result = matcher.processEvents(events);

        // Invariant: Matched lots must have monotonically non-decreasing acquisition dates
        List<MatchedLot> matched = result.matchedLots();
        for (int i = 1; i < matched.size(); i++) {
            LocalDate prevAcq = matched.get(i - 1).acquisitionDate();
            LocalDate currAcq = matched.get(i).acquisitionDate();
            assertFalse(
                currAcq.isBefore(prevAcq),
                String.format("FIFO violation: Lot %d acquired at %s consumed after lot acquired at %s", i, currAcq, prevAcq)
            );
        }
    }

    // =========================================================================
    // PROPERTY 3: Stock Split & Cost Basis Conservation
    // =========================================================================
    @Property(tries = 1000)
    void stockSplitConservesTotalCostBasisAndScalesUnits(
        @ForAll("chronologicalAcquisitions") List<TaxEvent> acquisitions,
        @ForAll @IntRange(min = 2, max = 10) int splitFactor
    ) {
        BigDecimal initialUnits = acquisitions.stream()
            .map(TaxEvent::units)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal splitRatio = BigDecimal.valueOf(splitFactor);

        LocalDate maxAcqDate = acquisitions.stream()
            .map(TaxEvent::eventDate)
            .max(Comparator.naturalOrder())
            .orElse(LocalDate.of(2025, 1, 1));

        String assetId = acquisitions.get(0).assetId();

        TaxEvent splitEvent = new TaxEvent(
            UUID.randomUUID().toString(),
            assetId,
            acquisitions.get(0).assetName(),
            assetId,
            EventType.SPLIT,
            maxAcqDate.plusDays(10),
            splitRatio, // units carries splitRatio
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "split-doc",
            Instant.now()
        );

        List<TaxEvent> events = new ArrayList<>(acquisitions);
        events.add(splitEvent);

        FifoMatcher.FifoResult result = matcher.processEvents(events);

        BigDecimal expectedTotalUnits = initialUnits.multiply(splitRatio);
        BigDecimal actualTotalUnits = result.openLots().stream()
            .map(Lot::remainingUnits)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(
            expectedTotalUnits.stripTrailingZeros(),
            actualTotalUnits.stripTrailingZeros(),
            "Total units after split must scale by split ratio"
        );

        // Invariant: Total cost basis across lots is strictly conserved
        BigDecimal preSplitCostBasis = acquisitions.stream()
            .map(TaxEvent::grossAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal postSplitCostBasis = result.openLots().stream()
            .map(Lot::totalCostBasis)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(
            preSplitCostBasis.stripTrailingZeros(),
            postSplitCostBasis.stripTrailingZeros(),
            "Total cost basis must be conserved across stock split"
        );
    }

    // =========================================================================
    // PROPERTY 4: Corporate Merger Asset Remapping & Cost Conservation
    // =========================================================================
    @Property(tries = 1000)
    void corporateMergerRemapsAssetIdAndPreservesCostBasis(
        @ForAll("chronologicalAcquisitions") List<TaxEvent> acquisitions,
        @ForAll @DoubleRange(min = 0.5, max = 3.0) double swapRatioDouble
    ) {
        BigDecimal swapRatio = BigDecimal.valueOf(swapRatioDouble).setScale(4, RoundingMode.HALF_UP);
        String oldAssetId = acquisitions.get(0).assetId();
        String newAssetId = "INF_MERGED_NEW_ISIN";
        String newAssetName = "Merged Target Entity Fund";

        LocalDate maxAcqDate = acquisitions.stream()
            .map(TaxEvent::eventDate)
            .max(Comparator.naturalOrder())
            .orElse(LocalDate.of(2025, 1, 1));

        TaxEvent mergerEvent = new TaxEvent(
            UUID.randomUUID().toString(),
            oldAssetId,
            newAssetName,
            newAssetId,
            EventType.MERGER,
            maxAcqDate.plusDays(10),
            swapRatio, // units = swapRatio
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "merger-doc",
            Instant.now()
        );

        List<TaxEvent> events = new ArrayList<>(acquisitions);
        events.add(mergerEvent);

        FifoMatcher.FifoResult result = matcher.processEvents(events);

        // Invariant 1: All lots have been remapped to newAssetId
        for (Lot lot : result.openLots()) {
            assertEquals(newAssetId, lot.assetId(), "All open lots must be remapped to target ISIN");
            assertEquals(newAssetName, lot.assetName(), "All open lots must have updated asset name");
        }

        // Invariant 2: Total cost basis is preserved
        BigDecimal preMergerCostBasis = acquisitions.stream()
            .map(TaxEvent::grossAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal postMergerCostBasis = result.openLots().stream()
            .map(Lot::totalCostBasis)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(
            preMergerCostBasis.stripTrailingZeros(),
            postMergerCostBasis.stripTrailingZeros(),
            "Total cost basis must be conserved across corporate merger"
        );

        // Invariant 3: Subsequent disposal on newAssetId succeeds and matches merged lots
        BigDecimal totalNewUnits = result.openLots().stream()
            .map(Lot::remainingUnits)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sellSlice = totalNewUnits.multiply(new BigDecimal("0.50")).setScale(4, RoundingMode.DOWN);
        if (sellSlice.compareTo(BigDecimal.ZERO) > 0) {
            TaxEvent postMergerDisposal = new TaxEvent(
                UUID.randomUUID().toString(),
                newAssetId,
                newAssetName,
                newAssetId,
                EventType.DISPOSAL,
                maxAcqDate.plusDays(20),
                sellSlice,
                new BigDecimal("100.0000"),
                sellSlice.multiply(new BigDecimal("100.0000")),
                "post-merger-doc",
                Instant.now()
            );

            events.add(postMergerDisposal);
            FifoMatcher.FifoResult postDisposalResult = matcher.processEvents(events);

            assertEquals(
                sellSlice.stripTrailingZeros(),
                postDisposalResult.matchedLots().stream().map(MatchedLot::unitsMatched).reduce(BigDecimal.ZERO, BigDecimal::add).stripTrailingZeros(),
                "Disposal against remapped assetId must match merged lots"
            );
        }
    }

    // =========================================================================
    // PROPERTY 5: SGB Maturity Tax Exemption vs Disposal
    // =========================================================================
    @Property(tries = 1000)
    void sgbMaturityYieldsExemptTaxTermRegardlessOfHoldingDays(
        @ForAll @IntRange(min = 30, max = 3500) int holdingDays,
        @ForAll @DoubleRange(min = 1.0, max = 100.0) double unitsDouble
    ) {
        BigDecimal units = BigDecimal.valueOf(unitsDouble).setScale(4, RoundingMode.HALF_UP);
        LocalDate acqDate = LocalDate.of(2016, 11, 17);
        LocalDate maturityDate = acqDate.plusDays(holdingDays);

        String sgbIsin = "IN0020160010";
        String sgbName = "Sovereign Gold Bond 2016-17 Series I";

        TaxEvent acq = new TaxEvent(
            UUID.randomUUID().toString(),
            sgbIsin,
            sgbName,
            sgbIsin,
            EventType.ACQUISITION,
            acqDate,
            units,
            new BigDecimal("3000.0000"),
            units.multiply(new BigDecimal("3000.0000")),
            "sgb-acq-doc",
            Instant.now()
        );

        TaxEvent maturity = new TaxEvent(
            UUID.randomUUID().toString(),
            sgbIsin,
            sgbName,
            sgbIsin,
            EventType.SGB_MATURITY,
            maturityDate,
            units,
            new BigDecimal("6500.0000"),
            units.multiply(new BigDecimal("6500.0000")),
            "sgb-mat-doc",
            Instant.now()
        );

        FifoMatcher.FifoResult result = matcher.processEvents(List.of(acq, maturity));

        assertEquals(1, result.matchedLots().size(), "SGB maturity must generate 1 matched lot");
        MatchedLot matched = result.matchedLots().get(0);

        // INVARIANT: SGB_MATURITY must unconditionally produce TaxTerm.EXEMPT
        assertEquals(TaxTerm.EXEMPT, matched.taxTerm(), "SGB_MATURITY must always be classified as TaxTerm.EXEMPT");
        assertEquals(AssetCategory.SGB, matched.assetCategory(), "SGB name must be detected as AssetCategory.SGB");
        assertEquals(0, result.openLots().size(), "All units must be fully redeemed on maturity");
    }

    // =========================================================================
    // PROPERTY 6: Non-Negative Units & Gain Integrity
    // =========================================================================
    @Property(tries = 1000)
    void gainIntegrityAndNonNegativeQuantities(
        @ForAll("chronologicalAcquisitions") List<TaxEvent> acquisitions,
        @ForAll @DoubleRange(min = 0.10, max = 0.90) double disposalFraction,
        @ForAll @DoubleRange(min = 10.0, max = 200.0) double salePriceDouble
    ) {
        BigDecimal totalUnits = acquisitions.stream()
            .map(TaxEvent::units)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unitsToSell = totalUnits.multiply(BigDecimal.valueOf(disposalFraction)).setScale(4, RoundingMode.DOWN);
        if (unitsToSell.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal salePrice = BigDecimal.valueOf(salePriceDouble).setScale(4, RoundingMode.HALF_UP);
        LocalDate maxDate = acquisitions.stream().map(TaxEvent::eventDate).max(Comparator.naturalOrder()).orElse(LocalDate.of(2025, 1, 1));

        TaxEvent disposal = new TaxEvent(
            UUID.randomUUID().toString(),
            acquisitions.get(0).assetId(),
            acquisitions.get(0).assetName(),
            acquisitions.get(0).assetId(),
            EventType.DISPOSAL,
            maxDate.plusDays(10),
            unitsToSell,
            salePrice,
            unitsToSell.multiply(salePrice),
            "disposal-doc",
            Instant.now()
        );

        List<TaxEvent> events = new ArrayList<>(acquisitions);
        events.add(disposal);

        FifoMatcher.FifoResult result = matcher.processEvents(events);

        // Check all matched lots
        for (MatchedLot m : result.matchedLots()) {
            assertTrue(m.unitsMatched().compareTo(BigDecimal.ZERO) > 0, "Matched units must be positive");
            assertTrue(m.costBasis().compareTo(BigDecimal.ZERO) >= 0, "Cost basis must be non-negative");
            assertTrue(m.saleProceeds().compareTo(BigDecimal.ZERO) >= 0, "Sale proceeds must be non-negative");

            // Realized gain identity: gain == proceeds - costBasis
            BigDecimal expectedGain = m.saleProceeds().subtract(m.costBasis());
            assertEquals(
                expectedGain.stripTrailingZeros(),
                m.realizedGain().stripTrailingZeros(),
                "Realized gain must exactly equal saleProceeds - costBasis"
            );
        }

        // Check all open lots
        for (Lot lot : result.openLots()) {
            assertTrue(lot.remainingUnits().compareTo(BigDecimal.ZERO) > 0, "Open lot remaining units must be positive");
            assertTrue(lot.remainingUnits().compareTo(lot.originalUnits()) <= 0, "Remaining units cannot exceed original units");
            assertTrue(lot.costPerUnit().compareTo(BigDecimal.ZERO) >= 0, "Cost per unit must be non-negative");
        }
    }

    // =========================================================================
    // PROPERTY 7: Cumulative Rounding-Drift Conservation on Multi-Slice Disposals
    // =========================================================================
    @Property(tries = 1000)
    void cumulativeRoundingDriftOnMultiSliceDisposals(
        @ForAll @DoubleRange(min = 10.0, max = 500.0) double initialUnitsDouble,
        @ForAll @DoubleRange(min = 10.0, max = 250.0) double acquisitionPriceDouble,
        @ForAll @Size(min = 2, max = 6) List<@DoubleRange(min = 0.05, max = 0.20) Double> sliceFractions,
        @ForAll @DoubleRange(min = 15.0, max = 300.0) double salePriceDouble
    ) {
        BigDecimal initialUnits = BigDecimal.valueOf(initialUnitsDouble).setScale(4, RoundingMode.HALF_UP);
        BigDecimal acquisitionPrice = BigDecimal.valueOf(acquisitionPriceDouble).setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalCostBasisOriginal = initialUnits.multiply(acquisitionPrice).setScale(2, RoundingMode.HALF_UP);

        LocalDate baseDate = LocalDate.of(2025, 1, 1);
        TaxEvent acquisition = new TaxEvent(
            UUID.randomUUID().toString(),
            "INF109K018C5",
            "Parag Parikh Flexi Cap Fund",
            "INF109K018C5",
            EventType.ACQUISITION,
            baseDate,
            initialUnits,
            acquisitionPrice,
            totalCostBasisOriginal,
            "acq-doc-1",
            Instant.now()
        );

        List<TaxEvent> events = new ArrayList<>();
        events.add(acquisition);

        BigDecimal salePrice = BigDecimal.valueOf(salePriceDouble).setScale(4, RoundingMode.HALF_UP);
        BigDecimal cumulativeDisposedUnits = BigDecimal.ZERO;

        int dayOffset = 15;
        for (Double fraction : sliceFractions) {
            BigDecimal sliceUnits = initialUnits.multiply(BigDecimal.valueOf(fraction)).setScale(4, RoundingMode.DOWN);
            if (sliceUnits.compareTo(BigDecimal.ZERO) <= 0) continue;

            if (cumulativeDisposedUnits.add(sliceUnits).compareTo(initialUnits) > 0) {
                sliceUnits = initialUnits.subtract(cumulativeDisposedUnits);
            }
            if (sliceUnits.compareTo(BigDecimal.ZERO) <= 0) break;

            cumulativeDisposedUnits = cumulativeDisposedUnits.add(sliceUnits);

            TaxEvent sliceDisposal = new TaxEvent(
                UUID.randomUUID().toString(),
                "INF109K018C5",
                "Parag Parikh Flexi Cap Fund",
                "INF109K018C5",
                EventType.DISPOSAL,
                baseDate.plusDays(dayOffset),
                sliceUnits,
                salePrice,
                sliceUnits.multiply(salePrice),
                "disp-doc-" + dayOffset,
                Instant.now()
            );
            events.add(sliceDisposal);
            dayOffset += 15;
        }

        FifoMatcher.FifoResult result = matcher.processEvents(events);

        // 1. Unit conservation: sum of matched units + sum of open remaining units == initialUnits
        BigDecimal totalMatchedUnits = result.matchedLots().stream()
            .map(MatchedLot::unitsMatched)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRemainingUnits = result.openLots().stream()
            .map(Lot::remainingUnits)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(
            initialUnits.setScale(4, RoundingMode.HALF_UP),
            totalMatchedUnits.add(totalRemainingUnits).setScale(4, RoundingMode.HALF_UP),
            "Total units across all matched slices and remaining open lots must exactly equal initial acquisition units"
        );

        // 2. Cost basis conservation without cumulative rounding drift:
        // sum(matchedLot.costBasis()) + sum(openLot.totalCostBasis()) must equal original cost basis within strict +/-0.05 tolerance
        BigDecimal totalMatchedCostBasis = result.matchedLots().stream()
            .map(MatchedLot::costBasis)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRemainingCostBasis = result.openLots().stream()
            .map(Lot::totalCostBasis)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal reconstitutedCostBasis = totalMatchedCostBasis.add(totalRemainingCostBasis);
        BigDecimal drift = reconstitutedCostBasis.subtract(totalCostBasisOriginal).abs();

        assertTrue(
            drift.compareTo(new BigDecimal("0.05")) <= 0,
            "Cumulative cost basis rounding drift across multiple partial slices must not exceed ₹0.05. Reconstituted: "
                + reconstitutedCostBasis + ", Original: " + totalCostBasisOriginal + ", Drift: " + drift
        );

        // 3. Ensure no negative balances
        for (MatchedLot m : result.matchedLots()) {
            assertTrue(m.costBasis().compareTo(BigDecimal.ZERO) >= 0, "Matched cost basis must be non-negative");
            assertTrue(m.unitsMatched().compareTo(BigDecimal.ZERO) > 0, "Matched units must be positive");
        }
        for (Lot l : result.openLots()) {
            assertTrue(l.totalCostBasis().compareTo(BigDecimal.ZERO) >= 0, "Open lot cost basis must be non-negative");
            assertTrue(l.remainingUnits().compareTo(BigDecimal.ZERO) >= 0, "Open lot remaining units must be non-negative");
        }
    }

    // =========================================================================
    // ARBITRARY PROVIDERS
    // =========================================================================
    @Provide
    Arbitrary<List<TaxEvent>> inboundEvents() {
        return Arbitraries.integers().between(1, 5).flatMap(count -> {
            List<Arbitrary<TaxEvent>> eventArbs = new ArrayList<>();
            LocalDate baseDate = LocalDate.of(2025, 4, 1);
            for (int i = 0; i < count; i++) {
                final int dayOffset = i * 30;
                Arbitrary<EventType> typeArb = Arbitraries.of(EventType.ACQUISITION, EventType.SIP_INSTALMENT, EventType.BONUS);
                Arbitrary<Double> unitsArb = Arbitraries.doubles().between(1.0, 200.0);
                Arbitrary<Double> priceArb = Arbitraries.doubles().between(10.0, 100.0);

                eventArbs.add(Combinators.combine(typeArb, unitsArb, priceArb).as((type, u, p) -> {
                    BigDecimal units = BigDecimal.valueOf(u).setScale(4, RoundingMode.HALF_UP);
                    BigDecimal price = type == EventType.BONUS ? BigDecimal.ZERO : BigDecimal.valueOf(p).setScale(4, RoundingMode.HALF_UP);
                    BigDecimal gross = units.multiply(price);
                    return new TaxEvent(
                        UUID.randomUUID().toString(),
                        "INF109K018C5",
                        "Parag Parikh Flexi Cap Fund",
                        "INF109K018C5",
                        type,
                        baseDate.plusDays(dayOffset),
                        units,
                        price,
                        gross,
                        "doc-" + dayOffset,
                        Instant.now()
                    );
                }));
            }
            return Combinators.combine(eventArbs).as(ArrayList::new);
        });
    }

    @Provide
    Arbitrary<List<TaxEvent>> chronologicalAcquisitions() {
        return Arbitraries.integers().between(2, 6).flatMap(count -> {
            List<Arbitrary<TaxEvent>> eventArbs = new ArrayList<>();
            LocalDate baseDate = LocalDate.of(2025, 4, 1);
            for (int i = 0; i < count; i++) {
                final int dayOffset = (i + 1) * 45;
                Arbitrary<Double> unitsArb = Arbitraries.doubles().between(5.0, 150.0);
                Arbitrary<Double> priceArb = Arbitraries.doubles().between(20.0, 80.0);

                eventArbs.add(Combinators.combine(unitsArb, priceArb).as((u, p) -> {
                    BigDecimal units = BigDecimal.valueOf(u).setScale(4, RoundingMode.HALF_UP);
                    BigDecimal price = BigDecimal.valueOf(p).setScale(4, RoundingMode.HALF_UP);
                    return new TaxEvent(
                        UUID.randomUUID().toString(),
                        "INF204K01H36",
                        "Nippon India Nifty 50 Index Fund",
                        "INF204K01H36",
                        EventType.ACQUISITION,
                        baseDate.plusDays(dayOffset),
                        units,
                        price,
                        units.multiply(price),
                        "doc-" + dayOffset,
                        Instant.now()
                    );
                }));
            }
            return Combinators.combine(eventArbs).as(ArrayList::new);
        });
    }
}
