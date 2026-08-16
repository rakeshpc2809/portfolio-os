package com.portfolioos.core.matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FundTierClassifierTest {

    @Test
    @DisplayName("FundStatus classification: ACCUMULATOR strategy returns ACCUMULATOR status")
    void testAccumulatorStatusClassification() {
        FundTierClassifier.FundStatus status = FundTierClassifier.getFundStatus(
            "INF247L01BM8", "ACCUMULATOR", Set.of()
        );
        assertEquals(FundTierClassifier.FundStatus.ACCUMULATOR, status, "Strategy ACCUMULATOR must yield ACCUMULATOR status");
    }

    @Test
    @DisplayName("FundStatus classification: ACTIVE_SIP vs LEGACY_HOLDING")
    void testActiveSipAndLegacyStatusClassification() {
        Set<String> activeSips = Set.of("INF109K018C5");

        FundTierClassifier.FundStatus activeStatus = FundTierClassifier.getFundStatus(
            "INF109K018C5", "CORE", activeSips
        );
        assertEquals(FundTierClassifier.FundStatus.ACTIVE_SIP, activeStatus);

        FundTierClassifier.FundStatus legacyStatus = FundTierClassifier.getFundStatus(
            "INF205K01KR8", "CORE", activeSips
        );
        assertEquals(FundTierClassifier.FundStatus.LEGACY_HOLDING, legacyStatus);
    }

    @Test
    @DisplayName("FundTier classification: Parag Parikh Flexi Cap (INF879O01027) is explicitly CORE_SATELLITE")
    void testParagParikhClassificationIsCoreSatellite() {
        FundTierClassifier.FundTier tier = FundTierClassifier.classify("INF879O01027");
        assertEquals(FundTierClassifier.FundTier.CORE_SATELLITE, tier,
            "Parag Parikh Flexi Cap Fund (INF879O01027) must classify as CORE_SATELLITE (not LEGACY)");

        boolean isLegacy = FundTierClassifier.isLegacyFund("INF879O01027", Set.of());
        assertFalse(isLegacy, "Parag Parikh Flexi Cap must NEVER be classified as a legacy fund even with 0 active SIPs!");
    }
}
