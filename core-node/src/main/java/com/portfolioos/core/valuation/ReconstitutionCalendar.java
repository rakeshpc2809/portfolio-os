package com.portfolioos.core.valuation;

import com.portfolioos.core.dtos.RebalancePlanDtos.ReconstitutionContextDto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ReconstitutionCalendar {

    public static ReconstitutionContextDto calculateReconstitutionStatus(LocalDate asOfDate) {
        LocalDate today = asOfDate != null ? asOfDate : LocalDate.now();
        int year = today.getYear();

        LocalDate mar31 = LocalDate.of(year, 3, 31);
        LocalDate sep30 = LocalDate.of(year, 9, 30);

        LocalDate nextReconDate;
        if (!today.isAfter(mar31)) {
            nextReconDate = mar31;
        } else if (!today.isAfter(sep30)) {
            nextReconDate = sep30;
        } else {
            nextReconDate = LocalDate.of(year + 1, 3, 31);
        }

        long daysToRecon = ChronoUnit.DAYS.between(today, nextReconDate);
        boolean isWindowActive = daysToRecon <= 2 && daysToRecon >= 0;
        String recommendation = isWindowActive ? "REBALANCE_PAUSED_48H_RECONSTITUTION" : "PROCEED";

        return new ReconstitutionContextDto(
            nextReconDate.toString(),
            daysToRecon,
            isWindowActive,
            recommendation
        );
    }
}
