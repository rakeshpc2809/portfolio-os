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
        private final LocalDate birthDate;
        private final int targetRetirementAge;
        private final BigDecimal swrPercent;
        private final BigDecimal epfBalance;
        private final int epfUnlockAge;
        private final BigDecimal realReturnRatePct;
        private final BigDecimal monthlyContribution;
        private final LocalDate nextReviewDate;
        private final List<FireScenario> scenarios;

        public FireProfile() {
            LocalDate defBirth = LocalDate.of(1994, 9, 28);
            int defAge = 45;
            BigDecimal defSwr = new BigDecimal("3.0");
            BigDecimal defEpf = BigDecimal.ZERO;
            int defEpfUnlock = 58;
            BigDecimal defReturn = new BigDecimal("6.0");
            BigDecimal defContrib = new BigDecimal("75000.00");
            LocalDate defReview = LocalDate.parse("2027-03-31");
            List<FireScenario> defScenarios = List.of(
                new FireScenario("scen_1", "Primary Expense Target", new BigDecimal("60000.00"), true),
                new FireScenario("scen_2", "Expanded Expense Target", new BigDecimal("90000.00"), false)
            );

            java.io.File profileFile = null;
            String rulesDirEnv = System.getenv("RULES_DIR");
            List<java.io.File> fileLocations = new ArrayList<>();
            if (rulesDirEnv != null && !rulesDirEnv.isBlank()) {
                fileLocations.add(new java.io.File(rulesDirEnv, "fire_profile.yaml"));
            }
            fileLocations.add(new java.io.File("rules/fire_profile.yaml"));
            fileLocations.add(new java.io.File("../rules/fire_profile.yaml"));
            fileLocations.add(new java.io.File("../../rules/fire_profile.yaml"));
            fileLocations.add(new java.io.File("/app/rules/fire_profile.yaml"));

            for (java.io.File f : fileLocations) {
                if (f.exists()) {
                    profileFile = f;
                    break;
                }
            }

            if (profileFile != null) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
                    Map<?, ?> data = mapper.readValue(profileFile, Map.class);
                    if (data != null) {
                        if (data.get("birth_date") != null) defBirth = LocalDate.parse(data.get("birth_date").toString());
                        if (data.get("target_retirement_age") != null) defAge = Integer.parseInt(data.get("target_retirement_age").toString());
                        if (data.get("swr_percent") != null) defSwr = new BigDecimal(data.get("swr_percent").toString());
                        if (data.get("real_return_rate_pct") != null) defReturn = new BigDecimal(data.get("real_return_rate_pct").toString());
                        if (data.get("monthly_contribution") != null) defContrib = new BigDecimal(data.get("monthly_contribution").toString());
                        if (data.get("next_review_date") != null) defReview = LocalDate.parse(data.get("next_review_date").toString());
                        if (data.get("scenarios") instanceof List<?> list && !list.isEmpty()) {
                            List<FireScenario> loadedScenarios = new ArrayList<>();
                            for (Object item : list) {
                                if (item instanceof Map<?, ?> m) {
                                    String id = String.valueOf(m.get("id"));
                                    String label = String.valueOf(m.get("label"));
                                    BigDecimal exp = new BigDecimal(String.valueOf(m.get("monthly_expense_today")));
                                    boolean active = Boolean.parseBoolean(String.valueOf(m.get("active")));
                                    loadedScenarios.add(new FireScenario(id, label, exp, active));
                                }
                            }
                            if (!loadedScenarios.isEmpty()) {
                                defScenarios = loadedScenarios;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Warning: failed to load rules/fire_profile.yaml: " + e.getMessage() + ", using defaults.");
                }
            }

            this.birthDate = defBirth;
            this.targetRetirementAge = defAge;
            this.swrPercent = defSwr;
            this.epfBalance = defEpf;
            this.epfUnlockAge = defEpfUnlock;
            this.realReturnRatePct = defReturn;
            this.monthlyContribution = defContrib;
            this.nextReviewDate = defReview;
            this.scenarios = defScenarios;
        }

        public LocalDate birthDate() { return birthDate; }
        public int currentAge() { return java.time.Period.between(birthDate, LocalDate.now()).getYears(); }
        public int targetRetirementAge() { return targetRetirementAge; }
        public BigDecimal swrPercent() { return swrPercent; }
        public BigDecimal epfBalance() { return epfBalance; }
        public BigDecimal realReturnRatePct() { return realReturnRatePct; }
        public BigDecimal monthlyContribution() { return monthlyContribution; }
        public LocalDate nextReviewDate() { return nextReviewDate; }
        public List<FireScenario> scenarios() { return scenarios; }
    }

    public record FireSummary(
        String activeScenarioLabel,
        BigDecimal monthlyExpenseToday,
        BigDecimal monthlyContribution,
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
            BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "FireTracker");
            if (lot.remainingUnits() != null) {
                totalMFValue = totalMFValue.add(lot.remainingUnits().multiply(nav));
            }
        }

        // Strict Mutual Fund Boundary (Invariant 1): Total and investable net worth reflect tracked MF units only
        BigDecimal totalNetWorth = totalMFValue;

        GoalTracker.GoalSummary goalSummary = GoalTracker.calculateGoalSummary(
            openLots, navMap, GoalTracker.DEFAULT_ALLOCATIONS, BigDecimal.ZERO
        );
        BigDecimal nonRetirementGoals = goalSummary.allocatedGoalsAmount();

        BigDecimal fireInvestableNetWorth = totalNetWorth.subtract(nonRetirementGoals)
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
            profile.monthlyContribution(),
            annualExpense,
            requiredCorpus,
            totalNetWorth.setScale(2, RoundingMode.HALF_UP),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
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
