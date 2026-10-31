package com.portfolioos.core.valuation;

import com.portfolioos.core.config.BucketTargetsConfig;
import com.portfolioos.core.config.BucketTargetsConfig.BucketType;
import com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class RebalanceInvariantVerifier {

    public record LotOrder(
        String lotId,
        String folioNo,
        String isin,
        LocalDate acquisitionDate,
        BigDecimal netProceedsInr,
        BigDecimal estimatedTaxInr,
        BigDecimal ltcgExemptionUsedInr,
        boolean withinExitLoadWindow
    ) {
        public LotOrder(
            String folioNo,
            String isin,
            LocalDate acquisitionDate,
            BigDecimal netProceedsInr,
            BigDecimal estimatedTaxInr,
            BigDecimal ltcgExemptionUsedInr,
            boolean withinExitLoadWindow
        ) {
            this(null, folioNo, isin, acquisitionDate, netProceedsInr, estimatedTaxInr, ltcgExemptionUsedInr, withinExitLoadWindow);
        }
    }

    public record BuyOrder(
        BucketType bucket,
        String isin,
        BigDecimal amountInr
    ) {}

    public record VerifiablePlan(
        TriggerType triggerType,
        boolean urgent,
        boolean coreTrimmed,
        BigDecimal effectiveActiveCorpusInr,
        List<LotOrder> soldLots,
        Map<String, List<LocalDate>> remainingUnsoldLotDatesByFolioIsin,
        List<BuyOrder> buyOrders,
        Map<BucketType, BigDecimal> postTradeBucketValuesInr,
        BigDecimal cashOverflowInr,
        BigDecimal summaryTaxInr,
        BigDecimal remainingAnnual112aExemptionInr
    ) {}

    public record PreTradeLotSnapshot(
        String lotId,
        String folioNo,
        String isin,
        LocalDate acquisitionDate
    ) {}

    public void assertAllInvariants(VerifiablePlan plan, BucketTargetsConfig config) {
        assertAllInvariants(plan, null, config);
    }

    public void assertAllInvariants(VerifiablePlan plan, List<PreTradeLotSnapshot> preTradeLotsSnapshot, BucketTargetsConfig config) {
        BigDecimal minOrder = config.globalInvariants().minTradeAmountInr();

        // Fix V4: Non-positive effective corpus rejection
        if (plan.effectiveActiveCorpusInr() == null || plan.effectiveActiveCorpusInr().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("INVARIANT_V4_VIOLATION: Effective active corpus must be strictly positive: " + plan.effectiveActiveCorpusInr());
        }

        // Fix V1: Buy Order amount < minTradeAmountInr or negative rejection
        // INVARIANT V1: Reject negative or non-positive / sub-minimum buy orders
        for (BuyOrder buy : plan.buyOrders()) {
            if (buy.amountInr() == null || buy.amountInr().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("INVARIANT_V1_VIOLATION: Negative buy order detected: " + (buy.amountInr() != null ? buy.amountInr() : "null") + " for " + buy.isin());
            }
            if (buy.amountInr().compareTo(minOrder) < 0) {
                throw new IllegalStateException("INVARIANT_V1_VIOLATION: Buy order below min threshold: " + buy.amountInr() + " < " + minOrder + " for " + buy.bucket());
            }
        }

        // INVARIANT 1: Strict Conservation of Money (Down to 1 Paisa)
        if (plan.triggerType() != TriggerType.GOLD_FLOOR_BACKSTOP) {
            BigDecimal totalSells = plan.soldLots().stream()
                .map(LotOrder::netProceedsInr).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalBuys = plan.buyOrders().stream()
                .map(BuyOrder::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal accountingDiff = totalSells.subtract(totalBuys.add(plan.cashOverflowInr())).abs();
            if (accountingDiff.compareTo(new BigDecimal("0.01")) > 0) {
                throw new IllegalStateException(
                    "INVARIANT_1_VIOLATION: Conservation of money failed. Sells=" + totalSells
                    + ", Buys=" + totalBuys + ", CashOverflow=" + plan.cashOverflowInr());
            }
        }

        // INVARIANT 2 & Fix V2: Strict RTA Chronological FIFO per (Folio + ISIN)
        if (preTradeLotsSnapshot != null && !preTradeLotsSnapshot.isEmpty()) {
            Set<String> preTradeLotIds = preTradeLotsSnapshot.stream()
                .filter(l -> l.lotId() != null)
                .map(PreTradeLotSnapshot::lotId)
                .collect(Collectors.toSet());

            for (LotOrder sold : plan.soldLots()) {
                if (sold.lotId() == null || !preTradeLotIds.contains(sold.lotId())) {
                    throw new IllegalStateException("INVARIANT_V2_VIOLATION: Sold lotId " + sold.lotId() + " does not exist in preTradeLotsSnapshot!");
                }
            }

            Set<String> soldLotIds = plan.soldLots().stream()
                .map(LotOrder::lotId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            for (LotOrder sold : plan.soldLots()) {
                for (PreTradeLotSnapshot snap : preTradeLotsSnapshot) {
                    if (!soldLotIds.contains(snap.lotId())
                            && Objects.equals(snap.isin(), sold.isin())
                            && Objects.equals(snap.folioNo(), sold.folioNo())
                            && snap.acquisitionDate() != null && sold.acquisitionDate() != null
                            && snap.acquisitionDate().isBefore(sold.acquisitionDate())) {
                        throw new IllegalStateException("INVARIANT_2_VIOLATION: RTA FIFO breached for ISIN " + sold.isin() + " (folio " + sold.folioNo() + "). Sold lot " + sold.lotId() + " (acquired " + sold.acquisitionDate() + ") was sold while older unsold lot " + snap.lotId() + " (acquired " + snap.acquisitionDate() + ") remains open.");
                    }
                }
            }
        } else {
            for (LotOrder sold : plan.soldLots()) {
                String key = (sold.folioNo() != null ? sold.folioNo() : "DEFAULT") + "::" + sold.isin();
                List<LocalDate> unsoldDates = plan.remainingUnsoldLotDatesByFolioIsin().getOrDefault(key, List.of());
                boolean olderUnsoldExists = unsoldDates.stream()
                    .anyMatch(unsoldDate -> unsoldDate.isBefore(sold.acquisitionDate()));
                if (olderUnsoldExists) {
                    throw new IllegalStateException(
                        "INVARIANT_2_VIOLATION: RTA FIFO breached for " + key
                        + ". Sold lot from " + sold.acquisitionDate() + " while older lot remains unsold.");
                }
            }
        }

        // INVARIANT 3 & Fix R1: Hard Ceiling & Dust Filter Compliance
        BigDecimal postTradeLiquidInr = plan.postTradeBucketValuesInr()
            .getOrDefault(BucketType.LIQUID_BUFFER, BigDecimal.ZERO);
        BigDecimal postTradeLiquidPct = postTradeLiquidInr.multiply(new BigDecimal("100"))
            .divide(plan.effectiveActiveCorpusInr(), 4, RoundingMode.HALF_UP);
        BigDecimal hardCap = config.globalInvariants().liquidHardCapPct();
        BigDecimal liquidBuys = plan.buyOrders().stream()
            .filter(b -> b.bucket() == BucketType.LIQUID_BUFFER)
            .map(BuyOrder::amountInr)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (liquidBuys.compareTo(BigDecimal.ZERO) > 0) {
            if (postTradeLiquidPct.compareTo(hardCap.add(new BigDecimal("0.01"))) > 0) {
                throw new IllegalStateException(
                    "INVARIANT_3_VIOLATION: LIQUID_BUFFER breached 15% hard cap: " + postTradeLiquidPct + "%");
            }
        }

        // INVARIANT 4 & Fix V3 & R2: Cross-Bucket Contamination Firewall
        // Fix V3: Derive coreTrimmed dynamically by inspecting config schemes for EQUITY_CORE
        Set<String> coreIsins = (config != null && config.buckets() != null && config.buckets().containsKey(BucketType.EQUITY_CORE))
            ? config.buckets().get(BucketType.EQUITY_CORE).schemes().stream()
                .map(BucketTargetsConfig.SchemeSpec::isin).collect(Collectors.toSet())
            : Set.of("INF109KC12U0", "INF879O01027");

        boolean derivedCoreTrimmed = plan.soldLots().stream().anyMatch(l -> coreIsins.contains(l.isin()));
        boolean isCoreTrimmed = plan.coreTrimmed() || derivedCoreTrimmed;

        if (isCoreTrimmed) {
            boolean satelliteBought = plan.buyOrders().stream()
                .anyMatch(b -> b.bucket() == BucketType.EQUITY_SATELLITE && b.amountInr().compareTo(BigDecimal.ZERO) > 0);
            if (satelliteBought) {
                throw new IllegalStateException(
                    "INVARIANT_4_VIOLATION: EQUITY_CORE proceeds routed into EQUITY_SATELLITE (Firewall breached)");
            }
        }

        if (plan.triggerType() == TriggerType.DRAWDOWN) {
            boolean goldBought = plan.buyOrders().stream()
                .anyMatch(b -> b.bucket() == BucketType.GOLD_SILVER && b.amountInr().compareTo(BigDecimal.ZERO) > 0);
            if (goldBought) {
                throw new IllegalStateException(
                    "INVARIANT_4_VIOLATION: Crisis DRAWDOWN deployment leaked into GOLD_SILVER");
            }
        }

        // INVARIANT 5: Urgent Drawdown Lockout & Exit-Load Immunity
        if (!plan.urgent()) {
            boolean exitLoadLotSold = plan.soldLots().stream().anyMatch(LotOrder::withinExitLoadWindow);
            if (exitLoadLotSold) {
                throw new IllegalStateException(
                    "INVARIANT_5_VIOLATION: Sold lot within exit load window during non-urgent rebalance");
            }
        }

        // INVARIANT 6: Statutory Tax Law Limits (ITA 2025 Sec 111 & 112A)
        BigDecimal totalLtcgExemptionUsed = plan.soldLots().stream()
            .map(LotOrder::ltcgExemptionUsedInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal effectiveLimit = plan.remainingAnnual112aExemptionInr() != null
            ? plan.remainingAnnual112aExemptionInr().min(config.taxParameters().ltcgExemptionLimitInr())
            : config.taxParameters().ltcgExemptionLimitInr();

        if (totalLtcgExemptionUsed.compareTo(effectiveLimit.add(new BigDecimal("0.01"))) > 0) {
            throw new IllegalStateException(
                "INVARIANT_6_VIOLATION: Section 112A exemption consumed (" + totalLtcgExemptionUsed
                + ") exceeds available headroom (" + effectiveLimit + ")");
        }

        // Tax Proration Check: individual lot estimated tax must sum to summaryTaxInr within 1 INR
        if (!plan.soldLots().isEmpty()) {
            BigDecimal totalLotTax = plan.soldLots().stream()
                .map(LotOrder::estimatedTaxInr).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal taxDiff = totalLotTax.subtract(plan.summaryTaxInr()).abs();
            if (taxDiff.compareTo(new BigDecimal("1.00")) > 0) {
                throw new IllegalStateException(
                    "INVARIANT_6_VIOLATION: Individual lot estimated tax sum (" + totalLotTax
                    + ") diverges from plan summary tax (" + plan.summaryTaxInr() + ") by > 1.00 INR");
            }
        }
    }
}

