package com.portfolioos.core.fire;

import com.portfolioos.core.model.Lot;
import com.portfolioos.core.goals.GoalTracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FireTracker {

    public record FireScenario(
        String id,
        String label,
        BigDecimal monthlyExpenseToday,
        boolean active
    ) {}

    public static class FireProfile {
        private final int currentAge = 32;
        private final int targetRetirementAge = 45;
        private final BigDecimal swrPercent = new BigDecimal("3.0");
        private final BigDecimal epfBalance = BigDecimal.ZERO;
        private final int epfUnlockAge = 58;
        private final BigDecimal realReturnRatePct = new BigDecimal("6.0");
        private final BigDecimal monthlyContribution = new BigDecimal("75000.00");
        private final LocalDate nextReviewDate = LocalDate.parse("2027-03-31");
        private final List<FireScenario> scenarios = List.of(
            new FireScenario("scen_1", "Primary Expense Target", new BigDecimal("60000.00"), true),
            new FireScenario("scen_2", "Expanded Expense Target", new BigDecimal("90000.00"), false)
        );

        public int currentAge() { return currentAge; }
        public int targetRetirementAge() { return targetRetirementAge; }
        public BigDecimal swrPercent() { return swrPercent; }
        public BigDecimal epfBalance() { return epfBalance; }
        public int epfUnlockAge() { return epfUnlockAge; }
        public BigDecimal realReturnRatePct() { return realReturnRatePct; }
        public BigDecimal monthlyContribution() { return monthlyContribution; }
        public LocalDate nextReviewDate() { return nextReviewDate; }
        public List<FireScenario> scenarios() { return scenarios; }
    }

    public record FireSummary(
        String activeScenarioLabel,
        BigDecimal monthlyExpenseToday,
        BigDecimal annualExpense,
        BigDecimal requiredCorpus,
        BigDecimal totalNetWorth,
        BigDecimal epfBalance,
        BigDecimal nonRetirementGoalAllocations,
        BigDecimal fireInvestableNetWorth,
        BigDecimal projectedCorpusAtTargetAge,
        int yearsRemaining,
        String status, // "ON_TRACK" or "SHORT"
        BigDecimal shortageOrSurplusAmount,
        boolean reviewDatePassed,
        List<FireScenario> scenarios,
        double monteCarloSuccessRatePct,
        BigDecimal monteCarloMedianCorpus,
        BigDecimal monteCarloTenthPercentileCorpus
    ) {}

    public static FireSummary calculateFireSummary(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        FireProfile profile,
        BigDecimal bankBalance,
        double monteCarloSuccessRatePct,
        BigDecimal monteCarloMedianCorpus,
        BigDecimal monteCarloTenthPercentileCorpus
    ) {
        BigDecimal totalMFValue = BigDecimal.ZERO;
        for (Lot lot : openLots) {
            BigDecimal nav = navMap.get(lot.assetId());
            if (nav == null) {
                nav = lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ZERO;
            }
            if (lot.remainingUnits() != null && nav != null) {
                totalMFValue = totalMFValue.add(lot.remainingUnits().multiply(nav));
            }
        }

        BigDecimal totalNetWorth = totalMFValue.add(bankBalance).add(profile.epfBalance());

        GoalTracker.GoalSummary goalSummary = GoalTracker.calculateGoalSummary(
            openLots, navMap, GoalTracker.DEFAULT_ALLOCATIONS, bankBalance
        );
        BigDecimal nonRetirementGoals = goalSummary.allocatedGoalsAmount();

        BigDecimal fireInvestableNetWorth = totalNetWorth.subtract(profile.epfBalance())
                                                     .subtract(nonRetirementGoals)
                                                     .max(BigDecimal.ZERO);

        FireScenario activeScenario = profile.scenarios().stream()
            .filter(FireScenario::active)
            .findFirst()
            .orElse(profile.scenarios().get(0));

        BigDecimal annualExpense = activeScenario.monthlyExpenseToday().multiply(new BigDecimal("12"));
        BigDecimal swrFraction = profile.swrPercent().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        BigDecimal requiredCorpus = BigDecimal.ZERO;
        if (swrFraction.compareTo(BigDecimal.ZERO) > 0) {
            requiredCorpus = annualExpense.divide(swrFraction, 2, RoundingMode.HALF_UP);
        }

        int yearsRemaining = Math.max(0, profile.targetRetirementAge() - profile.currentAge());
        double realRate = profile.realReturnRatePct().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).doubleValue();

        double compoundFactor = Math.pow(1.0 + realRate, yearsRemaining);
        BigDecimal fvInvestable = fireInvestableNetWorth.multiply(BigDecimal.valueOf(compoundFactor));

        BigDecimal fvSips;
        if (realRate > 0.0) {
            double annualContribution = profile.monthlyContribution().multiply(new BigDecimal("12")).doubleValue();
            double fvAnnuity = annualContribution * ((compoundFactor - 1.0) / realRate);
            fvSips = BigDecimal.valueOf(fvAnnuity);
        } else {
            fvSips = profile.monthlyContribution().multiply(new BigDecimal("12")).multiply(BigDecimal.valueOf(yearsRemaining));
        }

        BigDecimal projectedCorpus = fvInvestable.add(fvSips).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = projectedCorpus.subtract(requiredCorpus);
        boolean isOnTrack = diff.compareTo(BigDecimal.ZERO) >= 0;
        String status = isOnTrack ? "ON_TRACK" : "SHORT";

        boolean reviewDatePassed = !currentDate.isBefore(profile.nextReviewDate());

        return new FireSummary(
            activeScenario.label(),
            activeScenario.monthlyExpenseToday(),
            annualExpense,
            requiredCorpus,
            totalNetWorth.setScale(2, RoundingMode.HALF_UP),
            profile.epfBalance(),
            nonRetirementGoals,
            fireInvestableNetWorth.setScale(2, RoundingMode.HALF_UP),
            projectedCorpus,
            yearsRemaining,
            status,
            diff.abs().setScale(2, RoundingMode.HALF_UP),
            reviewDatePassed,
            profile.scenarios(),
            monteCarloSuccessRatePct,
            monteCarloMedianCorpus != null ? monteCarloMedianCorpus : projectedCorpus,
            monteCarloTenthPercentileCorpus != null ? monteCarloTenthPercentileCorpus : projectedCorpus.multiply(new BigDecimal("0.75"))
        );
    }

    public static FireSummary calculateFireSummary(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate
    ) {
        return calculateFireSummary(openLots, navMap, currentDate, new FireProfile(), BigDecimal.ZERO, 95.0, null, null);
    }
}
