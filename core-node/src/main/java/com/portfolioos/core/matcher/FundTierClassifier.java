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

    public static boolean isLegacyFund(String assetId, Set<String> activeAssetIds) {
        if (assetId == null || activeAssetIds == null) return false;
        return !activeAssetIds.contains(assetId);
    }
}
