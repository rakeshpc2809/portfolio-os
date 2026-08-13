package com.portfolioos.core.matcher;

import com.portfolioos.core.model.Lot;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class FundTierClassifier {

    public static final int ACTIVE_SIP_THRESHOLD_MONTHS = 3;

    public static Set<String> findActiveAssetIds(Collection<Lot> lots, LocalDate currentDate) {
        return findActiveAssetIds(lots, currentDate, ACTIVE_SIP_THRESHOLD_MONTHS);
    }

    public static Set<String> findActiveAssetIds(Collection<Lot> lots, LocalDate currentDate, int thresholdMonths) {
        if (currentDate == null) currentDate = LocalDate.now();
        LocalDate cutoffDate = currentDate.minusMonths(thresholdMonths);
        Set<String> activeIds = new HashSet<>();

        if (lots != null) {
            for (Lot lot : lots) {
                if (lot.acquisitionDate() != null && !lot.acquisitionDate().isBefore(cutoffDate)) {
                    activeIds.add(lot.assetId());
                }
            }
        }
        return activeIds;
    }

    public enum FundStatus {
        ACTIVE_SIP,
        ACCUMULATOR,
        LEGACY_HOLDING
    }

    public static FundStatus getFundStatus(String assetId, String bucketStrategy, Set<String> sipActiveIds) {
        if ("ACCUMULATOR".equalsIgnoreCase(bucketStrategy)) {
            return FundStatus.ACCUMULATOR;
        }
        if (sipActiveIds != null && sipActiveIds.contains(assetId)) {
            return FundStatus.ACTIVE_SIP;
        }
        return FundStatus.LEGACY_HOLDING;
    }

    public static boolean isLegacyFund(String assetId, Set<String> activeAssetIds) {
        if (assetId == null || activeAssetIds == null) return false;
        return !activeAssetIds.contains(assetId);
    }
}
