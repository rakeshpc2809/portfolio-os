package com.portfolioos.core.valuation;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BucketEngine {

    public enum Bucket {
        EQUITY_CORE,
        EQUITY_SATELLITE,
        GOLD_SILVER,
        LIQUID_BUFFER
    }

    public record BucketTarget(
        Bucket bucket,
        BigDecimal targetPct,
        BigDecimal bandPct
    ) {}

    public record BucketStatus(
        Bucket bucket,
        BigDecimal currentValue,
        BigDecimal currentPct,
        BigDecimal targetPct,
        BigDecimal driftPct,
        boolean isDrifted
    ) {}

    public record RebalanceRecommendation(
        String assetId,
        String assetName,
        Bucket bucket,
        String action, // "BUY" or "SELL"
        BigDecimal amount,
        String triggerType,
        BigDecimal estimatedTaxDrag,
        String taxTermSummary
    ) {}

    public record DrawdownStatus(
        String benchmarkName,
        BigDecimal currentLevel,
        BigDecimal rollingHigh,
        BigDecimal drawdownPct,
        List<Integer> activeRungsFired,
        BigDecimal recommendedBufferDeployPct
    ) {}

    public record RebalanceEngineResult(
        List<BucketStatus> bucketStatuses,
        List<RebalanceRecommendation> recommendations,
        DrawdownStatus drawdownStatus,
        boolean calendarTriggerFired,
        boolean drawdownTriggerFired
    ) {}

    public static final List<BucketTarget> DEFAULT_TARGETS = List.of(
        new BucketTarget(Bucket.EQUITY_CORE, new BigDecimal("50.0"), new BigDecimal("5.0")),
        new BucketTarget(Bucket.EQUITY_SATELLITE, new BigDecimal("20.0"), new BigDecimal("5.0")),
        new BucketTarget(Bucket.GOLD_SILVER, new BigDecimal("15.0"), new BigDecimal("5.0")),
        new BucketTarget(Bucket.LIQUID_BUFFER, new BigDecimal("15.0"), new BigDecimal("5.0"))
    );

    public static Bucket classifyAssetToBucket(String assetId, String assetName) {
        String nameUpper = assetName.toUpperCase();
        AssetCategory category = TaxClassifier.detectCategory(assetId, assetName);

        if (category == AssetCategory.GOLD_SILVER || category == AssetCategory.SGB) {
            return Bucket.GOLD_SILVER;
        }

        if (nameUpper.contains("ARBITRAGE") || nameUpper.contains("LIQUID") ||
            nameUpper.contains("OVERNIGHT") || nameUpper.contains("TREASURY") ||
            category == AssetCategory.DEBT_SPECIFIED_50AA
        ) {
            return Bucket.LIQUID_BUFFER;
        }

        if (nameUpper.contains("SMALL") || nameUpper.contains("MICRO") || nameUpper.contains("SMALLCAP")) {
            return Bucket.EQUITY_SATELLITE;
        }

        return Bucket.EQUITY_CORE;
    }

    public static RebalanceEngineResult evaluateRebalance(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketTarget> targets,
        String fiscalYear
    ) {
        BigDecimal totalPortfolioValue = BigDecimal.ZERO;
        Map<Bucket, BigDecimal> bucketValues = new HashMap<>();
        Map<Bucket, Map<String, List<Lot>>> bucketAssetLots = new HashMap<>();

        for (Bucket b : Bucket.values()) {
            bucketValues.put(b, BigDecimal.ZERO);
            bucketAssetLots.put(b, new HashMap<>());
        }

        for (Lot lot : openLots) {
            Bucket bucket = classifyAssetToBucket(lot.assetId(), lot.assetName());
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            BigDecimal lotValue = lot.remainingUnits().multiply(nav);
            
            totalPortfolioValue = totalPortfolioValue.add(lotValue);
            bucketValues.put(bucket, bucketValues.get(bucket).add(lotValue));

            Map<String, List<Lot>> assetMap = bucketAssetLots.get(bucket);
            assetMap.computeIfAbsent(lot.assetId(), k -> new ArrayList<>()).add(lot);
        }

        Map<Bucket, BucketTarget> targetMap = new HashMap<>();
        for (BucketTarget t : targets) {
            targetMap.put(t.bucket(), t);
        }

        List<BucketStatus> bucketStatuses = new ArrayList<>();
        boolean calendarTriggerFired = false;

        int month = currentDate.getMonthValue();
        int day = currentDate.getDayOfMonth();
        boolean isCalendarReviewDate = (month == 3 && day >= 10 && day <= 20) || (month == 9 && day >= 10 && day <= 20);

        for (Bucket bucket : Bucket.values()) {
            BigDecimal curVal = bucketValues.get(bucket);
            BigDecimal curPct = BigDecimal.ZERO;
            if (totalPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                curPct = curVal.multiply(new BigDecimal("100")).divide(totalPortfolioValue, 2, RoundingMode.HALF_UP);
            }

            BucketTarget tgt = targetMap.getOrDefault(bucket, new BucketTarget(bucket, new BigDecimal("25.0"), new BigDecimal("5.0")));
            BigDecimal drift = curPct.subtract(tgt.targetPct());
            boolean isDrifted = drift.abs().compareTo(tgt.bandPct()) > 0;

            if (isCalendarReviewDate && isDrifted) {
                calendarTriggerFired = true;
            }

            bucketStatuses.add(new BucketStatus(
                bucket, curVal, curPct, tgt.targetPct(), drift, isDrifted
            ));
        }

        // Drawdown trigger
        BigDecimal drawdownPct = BigDecimal.ZERO;
        if (benchmarkRollingHigh.compareTo(BigDecimal.ZERO) > 0) {
            drawdownPct = benchmarkRollingHigh.subtract(benchmarkCurrent)
                .multiply(new BigDecimal("100"))
                .divide(benchmarkRollingHigh, 2, RoundingMode.HALF_UP);
        }

        List<Integer> activeRungs = new ArrayList<>();
        BigDecimal deployPct = BigDecimal.ZERO;

        if (drawdownPct.compareTo(new BigDecimal("20.0")) >= 0) {
            activeRungs.addAll(List.of(10, 15, 20));
            deployPct = new BigDecimal("100.0");
        } else if (drawdownPct.compareTo(new BigDecimal("15.0")) >= 0) {
            activeRungs.addAll(List.of(10, 15));
            deployPct = new BigDecimal("50.0");
        } else if (drawdownPct.compareTo(new BigDecimal("10.0")) >= 0) {
            activeRungs.add(10);
            deployPct = new BigDecimal("25.0");
        }

        boolean drawdownTriggerFired = !activeRungs.isEmpty();
        DrawdownStatus drawdownStatus = new DrawdownStatus(
            "Nifty 500", benchmarkCurrent, benchmarkRollingHigh, drawdownPct, activeRungs, deployPct
        );

        List<RebalanceRecommendation> recommendations = new ArrayList<>();
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);

        if (drawdownTriggerFired) {
            BigDecimal liquidVal = bucketValues.get(Bucket.LIQUID_BUFFER);
            BigDecimal deployAmount = liquidVal.multiply(deployPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            if (deployAmount.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, List<Lot>> coreAssets = bucketAssetLots.get(Bucket.EQUITY_CORE);
                String targetAsset = !coreAssets.isEmpty() ? coreAssets.keySet().iterator().next() : "EQUITY_CORE_INDEX";
                String assetName = !coreAssets.isEmpty() ? coreAssets.get(targetAsset).get(0).assetName() : "LargeMidcap 250 Index Fund";

                recommendations.add(new RebalanceRecommendation(
                    targetAsset,
                    assetName,
                    Bucket.EQUITY_CORE,
                    "BUY",
                    deployAmount,
                    "MARKET_DRAWDOWN",
                    BigDecimal.ZERO,
                    "Deploy buffer during " + drawdownPct + "% market drawdown (Rungs: 10%, 15%, 20%)"
                ));
            }
        }

        for (BucketStatus status : bucketStatuses) {
            if (status.isDrifted()) {
                BigDecimal targetValue = totalPortfolioValue.multiply(status.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                BigDecimal diffValue = status.currentValue().subtract(targetValue);

                if (diffValue.compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, List<Lot>> bucketLots = bucketAssetLots.get(status.bucket());
                    if (bucketLots.isEmpty()) continue;
                    String firstAssetId = bucketLots.keySet().iterator().next();
                    List<Lot> firstLots = bucketLots.get(firstAssetId);
                    String assetName = firstLots.get(0).assetName();

                    BigDecimal estTaxDrag = BigDecimal.ZERO;
                    List<String> taxTerms = new ArrayList<>();
                    BigDecimal nav = navMap.getOrDefault(firstAssetId, firstLots.get(0).costPerUnit());

                    for (Lot lot : firstLots) {
                        AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
                        // Assume 365 holding threshold days to classify tax term for simple tax drag estimation
                        boolean isLtcg = TaxClassifier.classifyTaxTerm(category, 365, fiscalYear, true) == TaxTerm.LONG_TERM;
                        BigDecimal gain = nav.subtract(lot.costPerUnit()).multiply(lot.remainingUnits()).max(BigDecimal.ZERO);

                        if (gain.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal rate = isLtcg ? rules.equityLtcgRate() : rules.equityStcgRate();
                            estTaxDrag = estTaxDrag.add(gain.multiply(rate));
                            taxTerms.add(isLtcg ? "LTCG @ " + rules.equityLtcgRate().multiply(new BigDecimal("100")) + "%" 
                                                 : "STCG @ " + rules.equityStcgRate().multiply(new BigDecimal("100")) + "%");
                        }
                    }

                    recommendations.add(new RebalanceRecommendation(
                        firstAssetId,
                        assetName,
                        status.bucket(),
                        "SELL",
                        diffValue.abs(),
                        isCalendarReviewDate ? "CALENDAR" : "DRIFT_ALERT",
                        estTaxDrag.setScale(2, RoundingMode.HALF_UP),
                        taxTerms.stream().distinct().collect(Collectors.joining(", "))
                    ));
                } else if (diffValue.compareTo(BigDecimal.ZERO) < 0) {
                    Map<String, List<Lot>> bucketLots = bucketAssetLots.get(status.bucket());
                    String firstAssetId = !bucketLots.isEmpty() ? bucketLots.keySet().iterator().next() : "BUY_" + status.bucket().name();
                    String assetName = (!bucketLots.isEmpty() && bucketLots.containsKey(firstAssetId)) 
                        ? bucketLots.get(firstAssetId).get(0).assetName() : "Core Holding for " + status.bucket().name();

                    recommendations.add(new RebalanceRecommendation(
                        firstAssetId,
                        assetName,
                        status.bucket(),
                        "BUY",
                        diffValue.abs(),
                        isCalendarReviewDate ? "CALENDAR" : "DRIFT_ALERT",
                        BigDecimal.ZERO,
                        "No tax on purchases"
                    ));
                }
            }
        }

        return new RebalanceEngineResult(
            bucketStatuses, recommendations, drawdownStatus, calendarTriggerFired, drawdownTriggerFired
        );
    }
}
