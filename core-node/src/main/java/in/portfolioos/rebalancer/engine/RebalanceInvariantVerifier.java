package in.portfolioos.rebalancer.engine;

import in.portfolioos.rebalancer.config.BucketTargetsConfig;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.BucketType;
import in.portfolioos.rebalancer.engine.RebalanceTriggerEvaluator.TriggerType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class RebalanceInvariantVerifier {

    public record LotOrder(
        String folioNo,
        String isin,
        LocalDate acquisitionDate,
        BigDecimal netProceedsInr,
        BigDecimal estimatedTaxInr,
        BigDecimal ltcgExemptionUsedInr,
        boolean withinExitLoadWindow
    ) {}

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

    public void assertAllInvariants(VerifiablePlan plan, BucketTargetsConfig config) {
        BigDecimal minOrder = config.globalInvariants().minTradeAmountInr();

        // INVARIANT 1: Strict Conservation of Money (Down to 1 Paisa)
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

        // INVARIANT 2: Strict RTA Chronological FIFO per (Folio + ISIN)
        for (LotOrder sold : plan.soldLots()) {
            String key = sold.folioNo() + "::" + sold.isin();
            List<LocalDate> unsoldDates = plan.remainingUnsoldLotDatesByFolioIsin().getOrDefault(key, List.of());
            boolean olderUnsoldExists = unsoldDates.stream()
                .anyMatch(unsoldDate -> unsoldDate.isBefore(sold.acquisitionDate()));
            if (olderUnsoldExists) {
                throw new IllegalStateException(
                    "INVARIANT_2_VIOLATION: RTA FIFO breached for " + key
                    + ". Sold lot from " + sold.acquisitionDate() + " while older lot remains unsold.");
            }
        }

        // INVARIANT 3: Hard Ceiling & Dust Filter Compliance
        BigDecimal postTradeLiquidInr = plan.postTradeBucketValuesInr()
            .getOrDefault(BucketType.LIQUID_BUFFER, BigDecimal.ZERO);
        BigDecimal postTradeLiquidPct = postTradeLiquidInr.multiply(new BigDecimal("100"))
            .divide(plan.effectiveActiveCorpusInr(), 4, RoundingMode.HALF_UP);
        if (postTradeLiquidPct.compareTo(config.globalInvariants().liquidHardCapPct().add(new BigDecimal("0.01"))) > 0) {
            throw new IllegalStateException(
                "INVARIANT_3_VIOLATION: LIQUID_BUFFER breached 15% hard cap: " + postTradeLiquidPct + "%");
        }
        for (BuyOrder buy : plan.buyOrders()) {
            if (buy.amountInr().compareTo(BigDecimal.ZERO) > 0 && buy.amountInr().compareTo(minOrder) < 0) {
                throw new IllegalStateException(
                    "INVARIANT_3_VIOLATION: Micro-buy order below ₹1,000 dust filter for " + buy.isin());
            }
        }

        // INVARIANT 4: Asymmetric Routing Isolation (Core -> No Satellite; Drawdown -> No Gold)
        BigDecimal satelliteBuys = plan.buyOrders().stream()
            .filter(b -> b.bucket() == BucketType.EQUITY_SATELLITE)
            .map(BuyOrder::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (plan.coreTrimmed() && satelliteBuys.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                "INVARIANT_4_VIOLATION: Core trim proceeds leaked into EQUITY_SATELLITE: ₹" + satelliteBuys);
        }
        BigDecimal goldBuys = plan.buyOrders().stream()
            .filter(b -> b.bucket() == BucketType.GOLD_SILVER)
            .map(BuyOrder::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (plan.triggerType() == TriggerType.DRAWDOWN && goldBuys.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                "INVARIANT_4_VIOLATION: Crisis DRAWDOWN deployment leaked into GOLD_SILVER: ₹" + goldBuys);
        }

        // INVARIANT 5: Tax Proration & Section 112A Exemption Cap Identity
        BigDecimal sumLotTax = plan.soldLots().stream()
            .map(LotOrder::estimatedTaxInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumLotTax.subtract(plan.summaryTaxInr()).abs().compareTo(new BigDecimal("1.00")) > 0) {
            throw new IllegalStateException(
                "INVARIANT_5_VIOLATION: Lot tax sum (" + sumLotTax + ") != Summary tax (" + plan.summaryTaxInr() + ")");
        }
        BigDecimal exemptionUsed = plan.soldLots().stream()
            .map(LotOrder::ltcgExemptionUsedInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (exemptionUsed.compareTo(plan.remainingAnnual112aExemptionInr().add(new BigDecimal("0.01"))) > 0) {
            throw new IllegalStateException(
                "INVARIANT_5_VIOLATION: Section 112A exemption double-spent: used=" + exemptionUsed);
        }

        // INVARIANT 6: Non-Urgent Exit-Load Immunity
        if (!plan.urgent()) {
            boolean exitLoadLotSold = plan.soldLots().stream().anyMatch(LotOrder::withinExitLoadWindow);
            if (exitLoadLotSold) {
                throw new IllegalStateException(
                    "INVARIANT_6_VIOLATION: Sold lot within exit-load window during non-urgent rebalance");
            }
        }
    }
}

