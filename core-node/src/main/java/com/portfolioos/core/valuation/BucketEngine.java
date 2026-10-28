package com.portfolioos.core.valuation;

import com.portfolioos.core.common.PortfolioConstants;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BucketEngine {

    private static final Logger log = LoggerFactory.getLogger(BucketEngine.class);

    public enum Bucket {
        EQUITY_CORE,
        EQUITY_SATELLITE,
        GOLD_SILVER,
        LIQUID_BUFFER,
        LEGACY_HOLDINGS,
        SATELLITE_VALUE,
        SATELLITE_MOMENTUM,
        SATELLITE_SMALLCAP,
        HEDGE_COMMODITY,
        LIQUIDITY_ARBITRAGE
    }

    public record BucketTarget(
        Bucket bucket,
        BigDecimal targetPct,
        BigDecimal bandPct
    ) {}

    public record BucketFundConstituent(
        String isin,
        String fundName,
        BigDecimal currentValue,
        BigDecimal investedValue,
        BigDecimal unrealizedGain,
        BigDecimal gainPct,
        BigDecimal units,
        BigDecimal allocationPctInBucket,
        BigDecimal allocationPctInPortfolio,
        BigDecimal targetWeightInBucket,
        boolean isPreferred
    ) {}

    public record BucketStatus(
        Bucket bucket,
        BigDecimal currentValue,
        BigDecimal currentPct,
        BigDecimal targetPct,
        BigDecimal driftPct,
        boolean isDrifted,
        List<BucketFundConstituent> funds
    ) {
        public BucketStatus(
            Bucket bucket,
            BigDecimal currentValue,
            BigDecimal currentPct,
            BigDecimal targetPct,
            BigDecimal driftPct,
            boolean isDrifted
        ) {
            this(bucket, currentValue, currentPct, targetPct, driftPct, isDrifted, List.of());
        }
    }

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
        new BucketTarget(Bucket.EQUITY_SATELLITE, new BigDecimal("30.0"), new BigDecimal("5.0")),
        new BucketTarget(Bucket.GOLD_SILVER, new BigDecimal("10.0"), new BigDecimal("5.0")),
        new BucketTarget(Bucket.LIQUID_BUFFER, new BigDecimal("10.0"), new BigDecimal("5.0"))
    );

    public static Bucket classifyAssetToBucket(String assetId, String assetName) {
        return classifyAssetToBucket(assetId, assetName, java.util.Collections.emptySet());
    }

    public static Bucket classifyAssetToBucket(String assetId, String assetName, java.util.Set<String> activeOrPreferredAssetIds) {
        String nameUpper = assetName != null ? assetName.toUpperCase() : "";
        AssetCategory category = TaxClassifier.detectCategory(assetId, assetName);

        // Step 1: Read preferred fund mapping directly from YAML / BucketConfigLoader
        String mappedBucketName = com.portfolioos.core.rules.BucketConfigLoader.getPreferredBucketForAsset(assetId, assetName);
        if (mappedBucketName != null) {
            try {
                return Bucket.valueOf(mappedBucketName.toUpperCase());
            } catch (IllegalArgumentException e) {
                switch (mappedBucketName.toLowerCase()) {
                    case "core" -> { return Bucket.EQUITY_CORE; }
                    case "satellite_value" -> { return Bucket.SATELLITE_VALUE; }
                    case "satellite_momentum" -> { return Bucket.SATELLITE_MOMENTUM; }
                    case "satellite_smallcap" -> { return Bucket.SATELLITE_SMALLCAP; }
                    case "hedge_commodity" -> { return Bucket.HEDGE_COMMODITY; }
                    case "liquidity_arbitrage" -> { return Bucket.LIQUIDITY_ARBITRAGE; }
                }
            }
        }

        // Step 2: Category / Asset Type match
        if (category == AssetCategory.GOLD_SILVER || category == AssetCategory.SGB) {
            return Bucket.GOLD_SILVER;
        }

        if (nameUpper.contains("ARBITRAGE") || nameUpper.contains("LIQUID") ||
            nameUpper.contains("OVERNIGHT") || nameUpper.contains("TREASURY") ||
            category == AssetCategory.DEBT_SPECIFIED_50AA
        ) {
            return Bucket.LIQUID_BUFFER;
        }

        // Step 3: Equity holdings classification (Core vs Legacy)
        if (activeOrPreferredAssetIds != null && !activeOrPreferredAssetIds.isEmpty()) {
            if (!activeOrPreferredAssetIds.contains(assetId)) {
                return Bucket.LEGACY_HOLDINGS;
            }
            return Bucket.EQUITY_CORE;
        }

        // 2-arg caller or empty active set: check against known preferred funds in active config
        if (com.portfolioos.core.rules.BucketConfigLoader.isPreferredFund(assetId)) {
            throw new IllegalStateException("CRITICAL CONFIG ERROR: Asset '" + assetId + "' (" + assetName + ") is flagged as a preferred fund but has no mapped bucket target in active bucket configuration.");
        }

        return Bucket.LEGACY_HOLDINGS;
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
        return evaluateRebalance(openLots, List.of(), navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh, targets, fiscalYear);
    }

    public static RebalanceEngineResult evaluateRebalance(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketTarget> targets,
        String fiscalYear
    ) {
        return evaluateRebalance(openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh, targets, fiscalYear, java.util.Collections.emptySet());
    }

    public static List<BucketTarget> calculateRegimeShiftedTargets(
        List<BucketTarget> baseTargets,
        double drawdownPct,
        BigDecimal currentLiquidPct
    ) {
        if (baseTargets == null || baseTargets.isEmpty()) {
            return baseTargets;
        }

        BucketTarget liquidBase = null;
        BucketTarget coreBase = null;

        for (BucketTarget t : baseTargets) {
            if (t.bucket() == Bucket.LIQUID_BUFFER || t.bucket() == Bucket.LIQUIDITY_ARBITRAGE) {
                liquidBase = t;
            } else if (t.bucket() == Bucket.EQUITY_CORE) {
                coreBase = t;
            }
        }

        if (liquidBase == null || coreBase == null) {
            return baseTargets;
        }

        BigDecimal baseLiquidPct = liquidBase.targetPct();
        BigDecimal baseCorePct = coreBase.targetPct();

        BigDecimal dynamicLiquidPct;
        BigDecimal dynamicCorePct;

        if (drawdownPct >= 20.0) {
            // Rung 3: Capitulation (deploy 100% of buffer)
            dynamicLiquidPct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            dynamicCorePct = baseCorePct.add(baseLiquidPct).setScale(2, RoundingMode.HALF_UP);
        } else if (drawdownPct >= 15.0) {
            // Rung 2: Bear Market (deploy 50% of buffer)
            dynamicLiquidPct = baseLiquidPct.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
            dynamicCorePct = baseCorePct.add(baseLiquidPct.multiply(new BigDecimal("0.50"))).setScale(2, RoundingMode.HALF_UP);
        } else if (drawdownPct >= 10.0) {
            // Rung 1: Correction (deploy 25% of buffer)
            dynamicLiquidPct = baseLiquidPct.multiply(new BigDecimal("0.75")).setScale(2, RoundingMode.HALF_UP);
            dynamicCorePct = baseCorePct.add(baseLiquidPct.multiply(new BigDecimal("0.25"))).setScale(2, RoundingMode.HALF_UP);
        } else if (drawdownPct >= 5.0) {
            // Hold Zone (Hysteresis between -5% and -10%): freeze liquid buffer at current % if below base target
            BigDecimal curLiquid = currentLiquidPct != null ? currentLiquidPct.max(BigDecimal.ZERO) : baseLiquidPct;
            if (curLiquid.compareTo(baseLiquidPct) < 0) {
                dynamicLiquidPct = curLiquid.setScale(2, RoundingMode.HALF_UP);
                BigDecimal delta = baseLiquidPct.subtract(dynamicLiquidPct);
                dynamicCorePct = baseCorePct.add(delta).setScale(2, RoundingMode.HALF_UP);
            } else {
                dynamicLiquidPct = baseLiquidPct;
                dynamicCorePct = baseCorePct;
            }
        } else {
            // Normal / Recovery (above -5% drawdown): Ladder reset, standard base targets
            dynamicLiquidPct = baseLiquidPct;
            dynamicCorePct = baseCorePct;
        }

        List<BucketTarget> shifted = new ArrayList<>();
        for (BucketTarget t : baseTargets) {
            if (t.bucket() == liquidBase.bucket()) {
                shifted.add(new BucketTarget(t.bucket(), dynamicLiquidPct, t.bandPct()));
            } else if (t.bucket() == coreBase.bucket()) {
                shifted.add(new BucketTarget(t.bucket(), dynamicCorePct, t.bandPct()));
            } else {
                shifted.add(t);
            }
        }
        return shifted;
    }

    public static RebalanceEngineResult evaluateRebalance(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketTarget> targets,
        String fiscalYear,
        java.util.Set<String> activeOrPreferredAssetIds
    ) {
        BigDecimal totalPortfolioValue = BigDecimal.ZERO;
        Map<Bucket, BigDecimal> bucketValues = new HashMap<>();
        Map<Bucket, Map<String, List<Lot>>> bucketAssetLots = new LinkedHashMap<>();

        for (Bucket b : Bucket.values()) {
            bucketValues.put(b, BigDecimal.ZERO);
            bucketAssetLots.put(b, new LinkedHashMap<>());
        }
        if (openLots != null) {
            for (Lot lot : openLots) {
                BigDecimal nav = NavResolver.requireValidNav(navMap, lot, "BucketEngine");
                BigDecimal val = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
                Bucket b = classifyAssetToBucket(lot.assetId(), lot.assetName(), activeOrPreferredAssetIds);
                bucketValues.put(b, bucketValues.get(b).add(val));
                if (b == Bucket.SATELLITE_VALUE || b == Bucket.SATELLITE_MOMENTUM || b == Bucket.SATELLITE_SMALLCAP) {
                    bucketValues.put(Bucket.EQUITY_SATELLITE, bucketValues.get(Bucket.EQUITY_SATELLITE).add(val));
                } else if (b == Bucket.HEDGE_COMMODITY) {
                    bucketValues.put(Bucket.GOLD_SILVER, bucketValues.get(Bucket.GOLD_SILVER).add(val));
                } else if (b == Bucket.LIQUIDITY_ARBITRAGE) {
                    bucketValues.put(Bucket.LIQUID_BUFFER, bucketValues.get(Bucket.LIQUID_BUFFER).add(val));
                } else if (b == Bucket.GOLD_SILVER) {
                    bucketValues.put(Bucket.HEDGE_COMMODITY, bucketValues.get(Bucket.HEDGE_COMMODITY).add(val));
                } else if (b == Bucket.LIQUID_BUFFER) {
                    bucketValues.put(Bucket.LIQUIDITY_ARBITRAGE, bucketValues.get(Bucket.LIQUIDITY_ARBITRAGE).add(val));
                }
                
                totalPortfolioValue = totalPortfolioValue.add(val);
                Map<String, List<Lot>> assetMap = bucketAssetLots.get(b);
                assetMap.computeIfAbsent(lot.assetId(), k -> new ArrayList<>()).add(lot);
            }
        }

        List<Bucket> bucketsToEvaluate = new ArrayList<>();
        List<BucketTarget> targetList = (targets != null && !targets.isEmpty()) ? targets : DEFAULT_TARGETS;
        for (BucketTarget t : targetList) {
            if (!bucketsToEvaluate.contains(t.bucket())) {
                bucketsToEvaluate.add(t.bucket());
            }
        }
        if (!bucketsToEvaluate.contains(Bucket.LEGACY_HOLDINGS)) {
            bucketsToEvaluate.add(Bucket.LEGACY_HOLDINGS);
        }

        BigDecimal totalActiveValue = BigDecimal.ZERO;
        for (Bucket bucket : bucketsToEvaluate) {
            if (bucket != Bucket.LEGACY_HOLDINGS) {
                totalActiveValue = totalActiveValue.add(bucketValues.getOrDefault(bucket, BigDecimal.ZERO));
            }
        }

        // Drawdown trigger - delegates to unified PortfolioConstants disarm logic
        double ddPctVal = PortfolioConstants.calculateDrawdownPct(benchmarkCurrent, benchmarkRollingHigh);
        BigDecimal drawdownPct = BigDecimal.valueOf(ddPctVal).setScale(2, RoundingMode.HALF_UP);

        BigDecimal liquidVal = bucketValues.getOrDefault(Bucket.LIQUID_BUFFER, BigDecimal.ZERO)
            .max(bucketValues.getOrDefault(Bucket.LIQUIDITY_ARBITRAGE, BigDecimal.ZERO));
        BigDecimal currentLiquidPct = totalActiveValue.compareTo(BigDecimal.ZERO) > 0
            ? liquidVal.multiply(new BigDecimal("100")).divide(totalActiveValue, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Apply Regime-Conditional Target Shift
        List<BucketTarget> effectiveTargets = calculateRegimeShiftedTargets(targetList, ddPctVal, currentLiquidPct);

        Map<Bucket, BucketTarget> targetMap = new HashMap<>();
        for (BucketTarget t : effectiveTargets) {
            targetMap.put(t.bucket(), t);
        }

        List<BucketStatus> bucketStatuses = new ArrayList<>();
        boolean calendarTriggerFired = false;

        int month = currentDate.getMonthValue();
        int day = currentDate.getDayOfMonth();
        boolean isCalendarReviewDate = (month == 3 && day >= 10 && day <= 20) || (month == 9 && day >= 10 && day <= 20);

        for (Bucket bucket : bucketsToEvaluate) {
            BigDecimal curVal = bucketValues.getOrDefault(bucket, BigDecimal.ZERO);
            BigDecimal curPct = BigDecimal.ZERO;
            if (bucket == Bucket.LEGACY_HOLDINGS) {
                curPct = BigDecimal.ZERO;
            } else if (totalActiveValue.compareTo(BigDecimal.ZERO) > 0) {
                curPct = curVal.multiply(new BigDecimal("100")).divide(totalActiveValue, 2, RoundingMode.HALF_UP);
            }

            BucketTarget tgt = targetMap.get(bucket);
            BigDecimal targetPct = tgt != null ? tgt.targetPct() : BigDecimal.ZERO;
            BigDecimal bandPct = tgt != null ? tgt.bandPct() : new BigDecimal("5.0");

            BigDecimal drift = curPct.subtract(targetPct);
            boolean isDrifted = (bucket == Bucket.LEGACY_HOLDINGS) ? false : (drift.abs().compareTo(bandPct) > 0);

            if (isCalendarReviewDate && isDrifted) {
                calendarTriggerFired = true;
            }

            Map<String, List<Lot>> assetMap = bucketAssetLots.get(bucket);
            List<BucketFundConstituent> fundConstituents = new ArrayList<>();
            if (assetMap != null) {
                for (Map.Entry<String, List<Lot>> entry : assetMap.entrySet()) {
                    String assetId = entry.getKey();
                    List<Lot> lots = entry.getValue();
                    if (lots.isEmpty()) continue;

                    String fundName = lots.get(0).assetName();
                    BigDecimal units = lots.stream().map(Lot::remainingUnits).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal costBasis = lots.stream().map(Lot::totalCostBasis).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal nav = NavResolver.requireValidNav(navMap, lots.get(0), "BucketEngine");
                    BigDecimal fundVal = units.multiply(nav).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal unrealizedGain = fundVal.subtract(costBasis);
                    BigDecimal gainPct = costBasis.compareTo(BigDecimal.ZERO) > 0
                        ? unrealizedGain.multiply(new BigDecimal("100")).divide(costBasis, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                    BigDecimal allocInBucket = curVal.compareTo(BigDecimal.ZERO) > 0
                        ? fundVal.multiply(new BigDecimal("100")).divide(curVal, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                    BigDecimal allocInPortfolio = totalPortfolioValue.compareTo(BigDecimal.ZERO) > 0
                        ? fundVal.multiply(new BigDecimal("100")).divide(totalPortfolioValue, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                    Double prefWeight = com.portfolioos.core.rules.BucketConfigLoader.getPreferredFundWeight(assetId);
                    BigDecimal targetWeightInBucket = prefWeight != null
                        ? BigDecimal.valueOf(prefWeight * 100.0).setScale(2, RoundingMode.HALF_UP)
                        : null;
                    boolean isPreferred = com.portfolioos.core.rules.BucketConfigLoader.isPreferredFund(assetId);

                    fundConstituents.add(new BucketFundConstituent(
                        assetId, fundName, fundVal, costBasis, unrealizedGain, gainPct, units,
                        allocInBucket, allocInPortfolio, targetWeightInBucket, isPreferred
                    ));
                }
                fundConstituents.sort((a, b) -> b.currentValue().compareTo(a.currentValue()));
            }

            bucketStatuses.add(new BucketStatus(
                bucket, curVal, curPct, targetPct, drift, isDrifted, fundConstituents
            ));
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

        // Deduct statutory Section 112A LTCG exemption
        ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots != null ? matchedLots : List.of(), fiscalYear);
        BigDecimal exemptionRemaining = new BigDecimal(exStatus.exemptionRemaining());

        BigDecimal arbExitLoadFreeVal = BigDecimal.ZERO;
        Map<String, List<Lot>> liquidAssets = bucketAssetLots.get(Bucket.LIQUID_BUFFER);
        if (liquidAssets == null || liquidAssets.isEmpty()) {
            liquidAssets = bucketAssetLots.get(Bucket.LIQUIDITY_ARBITRAGE);
        }
        if (liquidAssets != null) {
            for (Map.Entry<String, List<Lot>> entry : liquidAssets.entrySet()) {
                String aId = entry.getKey();
                List<Lot> aLots = entry.getValue();
                if (aLots.isEmpty()) continue;
                BigDecimal nav = NavResolver.requireValidNav(navMap, aLots.get(0), "BucketEngine.exitLoadFree");
                ExitLoadCalculator.ExitLoadSummary summary = ExitLoadCalculator.calculateForAsset(aId, aLots.get(0).assetName(), aLots, nav, currentDate);
                arbExitLoadFreeVal = arbExitLoadFreeVal.add(summary.exitLoadFreeValue());
            }
        }

        if (drawdownTriggerFired) {
            BigDecimal deployAmount = arbExitLoadFreeVal.multiply(deployPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            if (deployAmount.compareTo(BigDecimal.ZERO) > 0) {
                String arbAssetId = (liquidAssets != null && !liquidAssets.isEmpty()) ? liquidAssets.keySet().iterator().next() : "INVESCO_ARBITRAGE";
                String arbAssetName = (liquidAssets != null && !liquidAssets.isEmpty() && liquidAssets.containsKey(arbAssetId) && !liquidAssets.get(arbAssetId).isEmpty())
                    ? liquidAssets.get(arbAssetId).get(0).assetName() : "Invesco India Arbitrage Fund";

                recommendations.add(new RebalanceRecommendation(
                    arbAssetId,
                    arbAssetName,
                    Bucket.LIQUID_BUFFER,
                    "SELL",
                    deployAmount,
                    "MARKET_DRAWDOWN",
                    BigDecimal.ZERO,
                    "Drain exit-load-free arbitrage buffer during " + drawdownPct + "% market drawdown (Rung deploy: " + deployPct + "%)"
                ));

                List<BucketConfigLoader.PreferredFundConfig> corePreferred = getPreferredFundsForBucket(Bucket.EQUITY_CORE, currentDate);
                if (!corePreferred.isEmpty()) {
                    BigDecimal remDeploy = deployAmount;
                    for (int i = 0; i < corePreferred.size(); i++) {
                        BucketConfigLoader.PreferredFundConfig pfc = corePreferred.get(i);
                        BigDecimal fundDeploy;
                        if (i == corePreferred.size() - 1) {
                            fundDeploy = remDeploy;
                        } else {
                            fundDeploy = deployAmount.multiply(BigDecimal.valueOf(pfc.allocationWeight())).setScale(2, RoundingMode.HALF_UP);
                            remDeploy = remDeploy.subtract(fundDeploy);
                        }
                        if (fundDeploy.compareTo(BigDecimal.ZERO) > 0) {
                            recommendations.add(new RebalanceRecommendation(
                                pfc.fundId(),
                                pfc.fundName(),
                                Bucket.EQUITY_CORE,
                                "BUY",
                                fundDeploy,
                                "MARKET_DRAWDOWN",
                                BigDecimal.ZERO,
                                "Deploy buffer during " + drawdownPct + "% market drawdown (Preferred fund weight: " + Math.round(pfc.allocationWeight() * 100) + "%)"
                            ));
                        }
                    }
                } else {
                    Map<String, List<Lot>> coreAssets = bucketAssetLots.get(Bucket.EQUITY_CORE);
                    String targetAsset = (coreAssets != null && !coreAssets.isEmpty()) ? coreAssets.keySet().iterator().next() : "EQUITY_CORE_INDEX";
                    String assetName = (coreAssets != null && !coreAssets.isEmpty() && coreAssets.containsKey(targetAsset) && !coreAssets.get(targetAsset).isEmpty())
                        ? coreAssets.get(targetAsset).get(0).assetName() : "LargeMidcap 250 Index Fund";

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
        }

        for (BucketStatus status : bucketStatuses) {
            // When drawdown is active or in hold zone, liquid buffer is intentionally drained; never emit conflicting drift buy/sell
            if (status.bucket() == Bucket.LIQUID_BUFFER || status.bucket() == Bucket.LIQUIDITY_ARBITRAGE) {
                if (drawdownTriggerFired || ddPctVal >= 5.0) {
                    continue;
                }
            }
            // When drawdown is active, core equity was already funded via deployAmount; skip duplicate BUY
            if (drawdownTriggerFired && status.bucket() == Bucket.EQUITY_CORE) {
                continue;
            }

            if (status.isDrifted()) {
                BigDecimal targetValue = totalPortfolioValue.multiply(status.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                BigDecimal diffValue = status.currentValue().subtract(targetValue);

                if (diffValue.compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, List<Lot>> bucketLotsMap = bucketAssetLots.get(status.bucket());
                    List<Lot> flatBucketLots = new ArrayList<>();
                    if (bucketLotsMap != null) {
                        for (List<Lot> lotList : bucketLotsMap.values()) {
                            flatBucketLots.addAll(lotList);
                        }
                    }

                    if (!flatBucketLots.isEmpty()) {
                        boolean urgent = PortfolioConstants.isUrgentRebalance(
                            drawdownStatus.drawdownPct().doubleValue()
                        );

                        RebalanceWaterfallEngine.WaterfallResult waterfallResult =
                            RebalanceWaterfallEngine.buildTrimWaterfall(
                                status.bucket(),
                                diffValue.abs(),
                                flatBucketLots,
                                navMap,
                                exemptionRemaining,
                                urgent,
                                currentDate,
                                fiscalYear
                            );

                        exemptionRemaining = exemptionRemaining.subtract(waterfallResult.ltcgExemptionConsumed()).max(BigDecimal.ZERO);

                        if (waterfallResult.steps().isEmpty()) {
                            recommendations.add(new RebalanceRecommendation(
                                "DEFERRED_" + status.bucket().name(),
                                "Deferred Trim (" + status.bucket().name() + ")",
                                status.bucket(),
                                "DEFER",
                                diffValue.abs(),
                                isCalendarReviewDate ? "CALENDAR" : "DRIFT_ALERT",
                                BigDecimal.ZERO,
                                waterfallResult.deferralReason() != null ? waterfallResult.deferralReason() : "No tax-efficient lots available"
                            ));
                        } else {
                            for (RebalanceWaterfallEngine.WaterfallStep step : waterfallResult.steps()) {
                                recommendations.add(new RebalanceRecommendation(
                                    step.assetId(),
                                    step.assetName(),
                                    status.bucket(),
                                    "SELL",
                                    step.proceeds(),
                                    isCalendarReviewDate ? "CALENDAR" : "DRIFT_ALERT",
                                    step.taxDrag(),
                                    "Tier: " + step.tier().name() + " (" + step.taxTerm() + ")"
                                ));
                            }

                            if (waterfallResult.deferredAmount().compareTo(BigDecimal.ZERO) > 0) {
                                recommendations.add(new RebalanceRecommendation(
                                    "DEFERRED_" + status.bucket().name(),
                                    "Partial Deferred Trim (" + status.bucket().name() + ")",
                                    status.bucket(),
                                    "DEFER",
                                    waterfallResult.deferredAmount(),
                                    isCalendarReviewDate ? "CALENDAR" : "DRIFT_ALERT",
                                    BigDecimal.ZERO,
                                    waterfallResult.deferralReason() != null ? waterfallResult.deferralReason() : "Partial STCG deferral"
                                ));
                            }
                        }
                    }
                } else if (diffValue.compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal totalBuyAmount = diffValue.abs();
                    List<BucketConfigLoader.PreferredFundConfig> preferredFunds = getPreferredFundsForBucket(status.bucket(), currentDate);
                    if (!preferredFunds.isEmpty()) {
                        BigDecimal remBuy = totalBuyAmount;
                        for (int i = 0; i < preferredFunds.size(); i++) {
                            BucketConfigLoader.PreferredFundConfig pfc = preferredFunds.get(i);
                            BigDecimal fundBuyAmount;
                            if (i == preferredFunds.size() - 1) {
                                fundBuyAmount = remBuy;
                            } else {
                                fundBuyAmount = totalBuyAmount.multiply(BigDecimal.valueOf(pfc.allocationWeight())).setScale(2, RoundingMode.HALF_UP);
                                remBuy = remBuy.subtract(fundBuyAmount);
                            }
                            if (fundBuyAmount.compareTo(BigDecimal.ZERO) > 0) {
                                recommendations.add(new RebalanceRecommendation(
                                    pfc.fundId(),
                                    pfc.fundName(),
                                    status.bucket(),
                                    "BUY",
                                    fundBuyAmount,
                                    isCalendarReviewDate ? "CALENDAR" : "DRIFT_ALERT",
                                    BigDecimal.ZERO,
                                    "No tax on purchases (Preferred fund weight: " + Math.round(pfc.allocationWeight() * 100) + "%)"
                                ));
                            }
                        }
                    } else {
                        Map<String, List<Lot>> bucketLots = bucketAssetLots.get(status.bucket());
                        String firstAssetId = !bucketLots.isEmpty() ? bucketLots.keySet().iterator().next() : "BUY_" + status.bucket().name();
                        String assetName = (!bucketLots.isEmpty() && bucketLots.containsKey(firstAssetId)) 
                            ? bucketLots.get(firstAssetId).get(0).assetName() : "Core Holding for " + status.bucket().name();

                        recommendations.add(new RebalanceRecommendation(
                            firstAssetId,
                            assetName,
                            status.bucket(),
                            "BUY",
                            totalBuyAmount,
                            isCalendarReviewDate ? "CALENDAR" : "DRIFT_ALERT",
                            BigDecimal.ZERO,
                            "No tax on purchases"
                        ));
                    }
                }
            }
        }

        return new RebalanceEngineResult(
            bucketStatuses, recommendations, drawdownStatus, calendarTriggerFired, drawdownTriggerFired
        );
    }

    private static List<BucketConfigLoader.PreferredFundConfig> getPreferredFundsForBucket(Bucket bucket, LocalDate date) {
        BucketConfigLoader.BucketTargetVersion version = BucketConfigLoader.getActiveVersion(date != null ? date : LocalDate.now());
        if (version != null && version.targets() != null) {
            for (BucketConfigLoader.BucketTargetConfig tc : version.targets()) {
                if (tc.bucket() != null && (tc.bucket().equalsIgnoreCase(bucket.name()) || 
                    ("EQUITY_CORE".equalsIgnoreCase(bucket.name()) && "CORE".equalsIgnoreCase(tc.strategy())) ||
                    ("EQUITY_SATELLITE".equalsIgnoreCase(bucket.name()) && "SATELLITE".equalsIgnoreCase(tc.strategy())) ||
                    ("LIQUID_BUFFER".equalsIgnoreCase(bucket.name()) && ("LIQUIDITY_ARBITRAGE".equalsIgnoreCase(tc.bucket()) || "ARBITRAGE".equalsIgnoreCase(tc.strategy()))))) {
                    if (tc.preferredFunds() != null && !tc.preferredFunds().isEmpty()) {
                        return tc.preferredFunds();
                    }
                }
            }
        }
        return List.of();
    }
}
