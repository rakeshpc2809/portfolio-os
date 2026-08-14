package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BucketAllocationTest {

    @Test
    @DisplayName("Classification order test: Gold/Silver and Liquid Buffer match category FIRST and bypass LEGACY_HOLDINGS")
    void testBucketClassificationOrderAndLegacyExclusion() {
        Set<String> activeOrPreferred = Set.of("INF109KC12U0"); // Only ICICI LargeMidcap 250 is preferred

        // Gold FoF: should match GOLD_SILVER category FIRST despite not being in activeOrPreferred set
        BucketEngine.Bucket goldBucket = BucketEngine.classifyAssetToBucket("INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", activeOrPreferred);
        assertEquals(BucketEngine.Bucket.GOLD_SILVER, goldBucket, "Gold/Silver category match must take priority over legacy check");

        // Arbitrage: should match LIQUID_BUFFER category FIRST
        BucketEngine.Bucket liquidBucket = BucketEngine.classifyAssetToBucket("INF209K01157", "Invesco India Arbitrage Fund", activeOrPreferred);
        assertEquals(BucketEngine.Bucket.LIQUID_BUFFER, liquidBucket, "Liquid/Arbitrage keyword match must take priority over legacy check");

        // Preferred Core Fund: matches EQUITY_CORE
        BucketEngine.Bucket coreBucket = BucketEngine.classifyAssetToBucket("INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", activeOrPreferred);
        assertEquals(BucketEngine.Bucket.EQUITY_CORE, coreBucket, "Preferred equity fund must map to active equity bucket");

        // Inactive Non-Preferred Equity Fund: maps to LEGACY_HOLDINGS
        BucketEngine.Bucket legacyBucket = BucketEngine.classifyAssetToBucket("INF109K01234", "Nifty 100 Equal Weight Index Fund", activeOrPreferred);
        assertEquals(BucketEngine.Bucket.LEGACY_HOLDINGS, legacyBucket, "Inactive non-preferred equity fund must map to LEGACY_HOLDINGS");
    }

    @Test
    @DisplayName("Exact valuation, percentage, drift, and isDrifted assertions for all 5 buckets")
    void testExactValuationAndDriftAssertions() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        BigDecimal nav = new BigDecimal("100.00");

        // Fixture: Total Corpus = ₹1,000,000
        Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI LargeMidcap 250", date, new BigDecimal("4500"), new BigDecimal("4500"), nav, new BigDecimal("450000.00"), false, null);
        Lot satLot = new Lot("lot-2", "INF204K01K15", "Motilal Oswal Smallcap Fund", date, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
        Lot goldLot = new Lot("lot-3", "INF247L01BM8", "Motilal Gold Silver FoF", date, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
        Lot liqLot = new Lot("lot-4", "INF209K01157", "Invesco Arbitrage Fund", date, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
        Lot legLot = new Lot("lot-5", "INF109K01234", "Nifty 100 EW Fund", date, new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);

        List<Lot> openLots = List.of(coreLot, satLot, goldLot, liqLot, legLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF204K01K15", nav,
            "INF247L01BM8", nav,
            "INF209K01157", nav,
            "INF109K01234", nav
        );

        List<BucketEngine.BucketTarget> targets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("50.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("20.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("15.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("15.00"), new BigDecimal("5.00"))
        );

        Set<String> activeOrPreferred = Set.of("INF109KC12U0", "INF204K01K15", "INF247L01BM8", "INF209K01157");

        BucketEngine.RebalanceEngineResult result = BucketEngine.evaluateRebalance(
            openLots, List.of(), navMap, date, BigDecimal.ZERO, BigDecimal.ZERO, targets, "2026-27", activeOrPreferred
        );

        assertEquals(5, result.bucketStatuses().size(), "Engine must return status for all 5 buckets");

        Map<BucketEngine.Bucket, BucketEngine.BucketStatus> statusMap = new HashMap<>();
        for (BucketEngine.BucketStatus s : result.bucketStatuses()) {
            statusMap.put(s.bucket(), s);
        }

        // EQUITY_CORE: 450,000 (45.00%), Target 50.00%, Drift -5.00%, isDrifted = false (exact boundary -5.00% is NOT > 5.00%)
        BucketEngine.BucketStatus coreStatus = statusMap.get(BucketEngine.Bucket.EQUITY_CORE);
        assertNotNull(coreStatus);
        assertEquals(new BigDecimal("450000.00"), coreStatus.currentValue());
        assertEquals(new BigDecimal("45.00"), coreStatus.currentPct());
        assertEquals(new BigDecimal("50.00"), coreStatus.targetPct());
        assertEquals(new BigDecimal("-5.00"), coreStatus.driftPct());
        assertFalse(coreStatus.isDrifted(), "Boundary match |-5.00%| is NOT > 5.00% threshold");

        // EQUITY_SATELLITE: 150,000 (15.00%), Target 20.00%, Drift -5.00%, isDrifted = false
        BucketEngine.BucketStatus satStatus = statusMap.get(BucketEngine.Bucket.EQUITY_SATELLITE);
        assertNotNull(satStatus);
        assertEquals(new BigDecimal("150000.00"), satStatus.currentValue());
        assertEquals(new BigDecimal("15.00"), satStatus.currentPct());
        assertEquals(new BigDecimal("20.00"), satStatus.targetPct());
        assertEquals(new BigDecimal("-5.00"), satStatus.driftPct());
        assertFalse(satStatus.isDrifted());

        // GOLD_SILVER: 150,000 (15.00%), Target 15.00%, Drift 0.00%, isDrifted = false
        BucketEngine.BucketStatus goldStatus = statusMap.get(BucketEngine.Bucket.GOLD_SILVER);
        assertNotNull(goldStatus);
        assertEquals(new BigDecimal("150000.00"), goldStatus.currentValue());
        assertEquals(new BigDecimal("15.00"), goldStatus.currentPct());
        assertEquals(new BigDecimal("15.00"), goldStatus.targetPct());
        assertEquals(new BigDecimal("0.00"), goldStatus.driftPct());
        assertFalse(goldStatus.isDrifted());

        // LIQUID_BUFFER: 150,000 (15.00%), Target 15.00%, Drift 0.00%, isDrifted = false
        BucketEngine.BucketStatus liqStatus = statusMap.get(BucketEngine.Bucket.LIQUID_BUFFER);
        assertNotNull(liqStatus);
        assertEquals(new BigDecimal("150000.00"), liqStatus.currentValue());
        assertEquals(new BigDecimal("15.00"), liqStatus.currentPct());
        assertEquals(new BigDecimal("15.00"), liqStatus.targetPct());
        assertEquals(new BigDecimal("0.00"), liqStatus.driftPct());
        assertFalse(liqStatus.isDrifted());

        // LEGACY_HOLDINGS: 100,000 (10.00%), Target 0.00%, Drift +10.00%, isDrifted = false (forced false for legacy)
        BucketEngine.BucketStatus legStatus = statusMap.get(BucketEngine.Bucket.LEGACY_HOLDINGS);
        assertNotNull(legStatus);
        assertEquals(0, new BigDecimal("100000.00").compareTo(legStatus.currentValue()));
        assertEquals(0, new BigDecimal("10.00").compareTo(legStatus.currentPct()));
        assertEquals(0, BigDecimal.ZERO.compareTo(legStatus.targetPct()), "Target % for LEGACY_HOLDINGS must be 0");
        assertEquals(0, new BigDecimal("10.00").compareTo(legStatus.driftPct()));
        assertFalse(legStatus.isDrifted(), "LEGACY_HOLDINGS isDrifted must be forced false");
    }
}
