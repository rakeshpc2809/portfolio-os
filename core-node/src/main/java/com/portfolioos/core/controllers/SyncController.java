package com.portfolioos.core.controllers;

import com.portfolioos.core.dtos.SyncDtos.*;
import com.portfolioos.core.fire.FireTracker;
import com.portfolioos.core.goals.GoalTracker;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.rpc.FlightRpcClient;
import com.portfolioos.core.service.LedgerCacheService;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.HarvestAdvisor;
import com.portfolioos.core.xirr.CashFlow;
import com.portfolioos.core.xirr.XirrEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final LedgerCacheService cacheService;
    private final XirrEngine xirrEngine = new XirrEngine();
    private final DuckDbProjector duckDbProjector = new DuckDbProjector();
    private final FlightRpcClient flightRpcClient = new FlightRpcClient();

    public SyncController(LedgerCacheService cacheService) {
        this.cacheService = cacheService;
    }

    private static String detectFineBucket(String assetName) {
        String upper = assetName.toUpperCase();
        if (upper.contains("FLEXI")) return "Flexi Cap";
        if (upper.contains("LARGE") && upper.contains("MID")) return "Large & Midcap";
        if (upper.contains("MICROCAP") || upper.contains("MICRO")) return "Microcap";
        if (upper.contains("SMALL")) return "Small Cap";
        if (upper.contains("MIDCAP") || upper.contains("MID CAP")) return "Midcap";
        if (upper.contains("VALUE")) return "Factor Value Index";
        if (upper.contains("MOMENTUM") || upper.contains("QUALITY")) return "Factor Momentum Index";
        if (upper.contains("EQUAL WEIGHT") || upper.contains("EQUAL")) return "Equal Weight Index";
        if (upper.contains("HEALTHCARE") || upper.contains("TECH") || upper.contains("SECTOR")) return "Sectoral/Thematic";
        if (upper.contains("GOLD") || upper.contains("SGB") || upper.contains("SILVER")) return "Gold & Commodities";
        if (upper.contains("DEBT") || upper.contains("LIQUID") || upper.contains("BOND")) return "Debt & Liquid";
        return "Core Equity";
    }

    @GetMapping("/snapshot")
    public ResponseEntity<UnidirectionalSyncSnapshot> getSnapshot(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> allEvents = state.events();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();
        String ledgerHash = state.ledgerHash();

        LocalDate today = LocalDate.now();
        Locale inLocale = new Locale("en", "IN");
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(inLocale);

        // Collect held ISINs and persist daily NAV history strictly for held assets
        Set<String> heldIsins = openLots.stream().map(Lot::assetId).collect(Collectors.toSet());
        duckDbProjector.saveNavHistoryBatchForHeldAssets(navMap, heldIsins, today);

        // Calculate overall XIRR & Totals
        List<CashFlow> portfolioCashflows = new ArrayList<>();
        BigDecimal totalPortfolioCurrentVal = BigDecimal.ZERO;
        BigDecimal totalPortfolioInvested = BigDecimal.ZERO;

        for (Lot lot : openLots) {
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            totalPortfolioCurrentVal = totalPortfolioCurrentVal.add(lot.remainingUnits().multiply(nav));
            totalPortfolioInvested = totalPortfolioInvested.add(lot.totalCostBasis());
        }

        for (TaxEvent event : allEvents) {
            if (event.eventType() == EventType.ACQUISITION || event.eventType() == EventType.SIP_INSTALMENT) {
                portfolioCashflows.add(new CashFlow(event.eventDate(), event.grossAmount().negate()));
            } else if (event.eventType() == EventType.DISPOSAL) {
                portfolioCashflows.add(new CashFlow(event.eventDate(), event.grossAmount()));
            }
        }
        portfolioCashflows.add(new CashFlow(today, totalPortfolioCurrentVal));
        double overallXirr = portfolioCashflows.size() >= 2 ? xirrEngine.calculateXirr(portfolioCashflows) : 0.0;
        BigDecimal unrealizedGain = totalPortfolioCurrentVal.subtract(totalPortfolioInvested);

        // Group open lots by asset for FlatHoldingDto
        Map<String, List<Lot>> groupedByAsset = openLots.stream().collect(Collectors.groupingBy(Lot::assetId));
        List<FlatHoldingDto> holdings = new ArrayList<>();

        for (Map.Entry<String, List<Lot>> entry : groupedByAsset.entrySet()) {
            String assetId = entry.getKey();
            List<Lot> lots = entry.getValue();

            String assetName = lots.get(0).assetName();
            BigDecimal totalUnits = lots.stream().map(Lot::remainingUnits).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCost = lots.stream().map(Lot::totalCostBasis).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgCost = totalUnits.compareTo(BigDecimal.ZERO) > 0 
                ? totalCost.divide(totalUnits, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            String bucket = detectFineBucket(assetName);

            // Holding XIRR calculation
            List<TaxEvent> assetEvents = allEvents.stream().filter(e -> e.assetId().equals(assetId)).toList();
            List<CashFlow> holdingCashflows = new ArrayList<>();
            for (TaxEvent event : assetEvents) {
                if (event.eventType() == EventType.ACQUISITION || event.eventType() == EventType.SIP_INSTALMENT) {
                    holdingCashflows.add(new CashFlow(event.eventDate(), event.grossAmount().negate()));
                } else if (event.eventType() == EventType.DISPOSAL) {
                    holdingCashflows.add(new CashFlow(event.eventDate(), event.grossAmount()));
                }
            }
            BigDecimal nav = navMap.getOrDefault(assetId, lots.get(0).costPerUnit());
            BigDecimal holdingCurVal = totalUnits.multiply(nav);
            holdingCashflows.add(new CashFlow(today, holdingCurVal));

            double holdingXirr = holdingCashflows.size() >= 2 ? xirrEngine.calculateXirr(holdingCashflows) : 0.0;

            holdings.add(new FlatHoldingDto(
                assetId,
                assetName,
                totalUnits.doubleValue(),
                avgCost.doubleValue(),
                BigDecimal.valueOf(holdingXirr).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                bucket,
                holdingCurVal.doubleValue(),
                totalCost.doubleValue(),
                currencyFormat.format(holdingCurVal),
                currencyFormat.format(totalCost)
            ));
        }

        // Construct FlatTaxLotDto
        List<FlatTaxLotDto> taxLots = new ArrayList<>();
        for (Lot lot : openLots) {
            AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            com.portfolioos.core.model.TaxTerm taxTerm = TaxClassifier.classifyTaxTerm(category, holdingDays, fy, isListed);
            boolean isLongTerm = taxTerm == com.portfolioos.core.model.TaxTerm.LONG_TERM;

            String classification = switch (category) {
                case DEBT_SPECIFIED_50AA -> "SEC_50AA_DEBT";
                case EQUITY -> "SEC_112A_EQUITY";
                default -> category.name();
            };

            long daysToLtcg = isLongTerm ? 0L : Math.max(0L, 365L - holdingDays);

            taxLots.add(new FlatTaxLotDto(
                lot.assetId(),
                lot.acquisitionDate().toString(),
                lot.remainingUnits().doubleValue(),
                classification,
                isLongTerm,
                lot.isGrandfathered() ? lot.fmv20180131().doubleValue() : null,
                lot.costPerUnit().doubleValue(),
                holdingDays,
                daysToLtcg
            ));
        }

        // Generate Verified Priority AI Radar Signals
        List<RadarSignalDto> radarSignals = new ArrayList<>();

        // 1. Priority Tax Loss Harvesting Signals
        ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
        HarvestAdvisor.TaxHarvestResult harvestPlan = HarvestAdvisor.generateHarvestPlan(
            openLots, navMap, new BigDecimal(exStatus.exemptionUsed()), fy
        );

        Map<String, List<HarvestAdvisor.TaxHarvestRecommendation>> harvestByScheme = harvestPlan.recommendations().stream()
            .collect(Collectors.groupingBy(HarvestAdvisor.TaxHarvestRecommendation::assetName));

        List<RadarSignalDto> harvestSignals = new ArrayList<>();
        for (Map.Entry<String, List<HarvestAdvisor.TaxHarvestRecommendation>> entry : harvestByScheme.entrySet()) {
            String schemeName = entry.getKey();
            List<HarvestAdvisor.TaxHarvestRecommendation> recs = entry.getValue();
            BigDecimal totalHarvestGain = recs.stream()
                .map(HarvestAdvisor.TaxHarvestRecommendation::unrealizedLtcgGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalUnitsToSell = recs.stream()
                .map(HarvestAdvisor.TaxHarvestRecommendation::unitsToHarvest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            harvestSignals.add(new RadarSignalDto(
                "HARVEST",
                schemeName,
                "TAX-LOSS HARVEST OPPORTUNITY",
                "Harvest " + currencyFormat.format(totalHarvestGain) + " tax-free LTCG gain across " + recs.size() + " lots (" + totalUnitsToSell.setScale(2, RoundingMode.HALF_UP) + " units) before Mar 31.",
                "WARNING",
                "Priority Action"
            ));
        }
        harvestSignals.sort((a, b) -> b.description().compareTo(a.description()));
        radarSignals.addAll(harvestSignals.stream().limit(3).toList());

        // 2. PyArrow Flight RPC Quant Intelligence (from real DuckDB NAV time-series)
        Map<String, List<Double>> navHistorySeries = duckDbProjector.getNavHistorySeries(heldIsins);
        if (!navHistorySeries.isEmpty()) {
            Map<String, Map<String, Object>> quantMetrics = flightRpcClient.computeQuantMetrics(navHistorySeries);
            Map<String, String> isinToNameMap = holdings.stream().collect(Collectors.toMap(FlatHoldingDto::isin, FlatHoldingDto::fundName, (a, b) -> a));

            for (Map.Entry<String, Map<String, Object>> entry : quantMetrics.entrySet()) {
                String isin = entry.getKey();
                Map<String, Object> metrics = entry.getValue();
                String schemeName = isinToNameMap.getOrDefault(isin, isin);

                Object hurstObj = metrics.get("hurst");
                Object regimeObj = metrics.get("hurst_regime");
                Object halfLifeObj = metrics.get("ou_half_life");

                if (hurstObj instanceof Number hurst && regimeObj != null) {
                    radarSignals.add(new RadarSignalDto(
                        "QUANT_HURST",
                        schemeName,
                        "QUANT SIDE-CAR: " + regimeObj.toString(),
                        schemeName + " displays Hurst Exponent H = " + String.format("%.2f", hurst.doubleValue()) + " (" + regimeObj.toString() + ").",
                        "INFO",
                        "H = " + String.format("%.2f", hurst.doubleValue())
                    ));
                }

                if (halfLifeObj instanceof Number halfLife && halfLife.doubleValue() > 0) {
                    radarSignals.add(new RadarSignalDto(
                        "QUANT_OU",
                        schemeName,
                        "QUANT SIDE-CAR: OU MEAN REVERSION",
                        schemeName + " valuation drift half-life τ = " + String.format("%.1f", halfLife.doubleValue()) + " days.",
                        "INFO",
                        "τ = " + String.format("%.1f", halfLife.doubleValue()) + "d"
                    ));
                }
            }
        }

        // 3. LTCG Maturation Ladder Signal
        Lot maturingLot = null;
        long minDaysToLtcg = 9999L;

        for (Lot lot : openLots) {
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            long daysToLtcg = Math.max(0L, 365L - holdingDays);
            if (daysToLtcg > 0 && daysToLtcg <= 120 && daysToLtcg < minDaysToLtcg) {
                minDaysToLtcg = daysToLtcg;
                maturingLot = lot;
            }
        }

        if (maturingLot != null) {
            radarSignals.add(0, new RadarSignalDto(
                "MATURATION",
                maturingLot.assetName(),
                "LTCG MATURATION LADDER",
                maturingLot.assetName() + " (Lot " + maturingLot.lotId() + ") matures under Sec 112A in " + minDaysToLtcg + " days.",
                "INFO",
                minDaysToLtcg + " Days"
            ));
        }

        // 4. Asset Allocation Drift Signal
        BucketEngine.RebalanceEngineResult bucketStatus = BucketEngine.evaluateRebalance(
            openLots, navMap, today, new BigDecimal("24000.00"), new BigDecimal("25000.00"), BucketEngine.DEFAULT_TARGETS, fy
        );

        BucketEngine.BucketStatus driftedBucket = bucketStatus.bucketStatuses().stream()
            .filter(BucketEngine.BucketStatus::isDrifted)
            .findFirst()
            .orElse(null);

        if (driftedBucket != null) {
            radarSignals.add(new RadarSignalDto(
                "REBALANCE",
                "Bucket " + driftedBucket.bucket().name(),
                "ALLOCATION DRIFT ALERT",
                "Current allocation is " + driftedBucket.currentPct() + "% vs target " + driftedBucket.targetPct() + "%. Rebalance recommended.",
                "WARNING",
                "Rebalance"
            ));
        }

        long now = System.currentTimeMillis();
        SyncInfoDto syncInfo = new SyncInfoDto(
            now / 1000,
            ledgerHash,
            LocalDate.now().atStartOfDay().toString(),
            fy,
            BigDecimal.valueOf(overallXirr).setScale(2, RoundingMode.HALF_UP).doubleValue(),
            String.format("%.2f%%", overallXirr),
            totalPortfolioInvested.doubleValue(),
            totalPortfolioCurrentVal.doubleValue(),
            unrealizedGain.doubleValue(),
            currencyFormat.format(totalPortfolioCurrentVal),
            currencyFormat.format(totalPortfolioInvested),
            (unrealizedGain.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + currencyFormat.format(unrealizedGain)
        );

        return ResponseEntity.ok(new UnidirectionalSyncSnapshot(
            syncInfo, holdings, taxLots, radarSignals
        ));
    }

    @PostMapping("/pair")
    public ResponseEntity<PairResponseDto> pairDevice(
        @RequestBody PairRequestDto req
    ) {
        String token = "fintracker_jwt_" + req.deviceId() + "_" + System.currentTimeMillis();
        return ResponseEntity.ok(new PairResponseDto(
            "SUCCESS",
            token,
            "my-fintracker-core"
        ));
    }
}
