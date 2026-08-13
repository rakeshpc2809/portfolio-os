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
}
