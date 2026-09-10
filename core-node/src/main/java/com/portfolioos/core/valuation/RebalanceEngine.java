package com.portfolioos.core.valuation;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class RebalanceEngine {

    public record RebalanceLotSelection(
        String lotId,
        String assetId,
        String assetName,
        BigDecimal unitsToSell,
        BigDecimal redemptionProceeds,
        BigDecimal costBasis,
        BigDecimal estimatedGain,
        String taxTerm,
        BigDecimal estimatedTaxDrag,
        LiquidationTier tier
    ) {
        public RebalanceLotSelection(
            String lotId,
            String assetId,
            String assetName,
            BigDecimal unitsToSell,
            BigDecimal redemptionProceeds,
            BigDecimal costBasis,
            BigDecimal estimatedGain,
            String taxTerm,
            BigDecimal estimatedTaxDrag
        ) {
            this(lotId, assetId, assetName, unitsToSell, redemptionProceeds, costBasis, estimatedGain, taxTerm, estimatedTaxDrag, LiquidationTier.TIER_4_TAXABLE_112A_LTCG);
        }
    }

    public record RebalancePreviewResult(
        BigDecimal targetRedemptionAmount,
        BigDecimal actualRedemptionAmount,
        BigDecimal deferredAmount,
        BigDecimal totalEstimatedGain,
        BigDecimal totalTaxDrag,
        BigDecimal effectiveTaxRatePct,
        BigDecimal ltcgExemptionHarvested,
        List<RebalanceLotSelection> selectedLots,
        BigDecimal reservedExemption,
        String exemptionHeadroomCaveat
    ) {
        public RebalancePreviewResult(
            BigDecimal targetRedemptionAmount,
            BigDecimal actualRedemptionAmount,
            BigDecimal deferredAmount,
            BigDecimal totalEstimatedGain,
            BigDecimal totalTaxDrag,
            BigDecimal effectiveTaxRatePct,
            BigDecimal ltcgExemptionHarvested,
            List<RebalanceLotSelection> selectedLots
        ) {
            this(targetRedemptionAmount, actualRedemptionAmount, deferredAmount, totalEstimatedGain, totalTaxDrag, effectiveTaxRatePct, ltcgExemptionHarvested, selectedLots, BigDecimal.ZERO, null);
        }
    }

    public static RebalancePreviewResult calculateRebalancePreview(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal targetAmount,
        BigDecimal remainingExemption,
        String fiscalYear
    ) {
        return calculateRebalancePreview(openLots, navMap, targetAmount, remainingExemption, BigDecimal.ZERO, fiscalYear, true);
    }

    public static RebalancePreviewResult calculateRebalancePreview(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal targetAmount,
        BigDecimal remainingExemption,
        String fiscalYear,
        boolean allowStcg
    ) {
        return calculateRebalancePreview(openLots, navMap, targetAmount, remainingExemption, BigDecimal.ZERO, fiscalYear, allowStcg);
    }

    public static RebalancePreviewResult calculateRebalancePreview(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal targetAmount,
        BigDecimal remainingExemption,
        BigDecimal reservedExemption,
        String fiscalYear
    ) {
        return calculateRebalancePreview(openLots, navMap, targetAmount, remainingExemption, reservedExemption, fiscalYear, true);
    }

    public static RebalancePreviewResult calculateRebalancePreview(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal targetAmount,
        BigDecimal remainingExemption,
        BigDecimal reservedExemption,
        String fiscalYear,
        boolean allowStcg
    ) {
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        BigDecimal remainingTarget = targetAmount;
        BigDecimal reserved = reservedExemption != null ? reservedExemption : BigDecimal.ZERO;
        BigDecimal effectiveHeadroom = remainingExemption != null
            ? remainingExemption.subtract(reserved).max(BigDecimal.ZERO)
            : BigDecimal.ZERO;
        BigDecimal unusedExemption = effectiveHeadroom;
        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal totalTaxDrag = BigDecimal.ZERO;
        BigDecimal actualRedemption = BigDecimal.ZERO;

        List<RebalanceLotSelection> selected = new ArrayList<>();
        LocalDate today = LocalDate.now();

        List<Lot> candidateLots = new ArrayList<>(openLots);

        if (!allowStcg) {
            // Drop positive-gain short-term lots (Cohorts 5 and 6)
            candidateLots = candidateLots.stream().filter(l -> {
                int c = determineCohort(l, navMap, today, fiscalYear, rules);
                return c <= 4;
            }).toList();
        }

        List<Lot> sortedLots = new ArrayList<>(candidateLots);
        sortedLots.sort((l1, l2) -> {
            int c1 = determineCohort(l1, navMap, today, fiscalYear, rules);
            int c2 = determineCohort(l2, navMap, today, fiscalYear, rules);
            if (c1 != c2) {
                return Integer.compare(c1, c2);
            }

            BigDecimal nav1 = NavResolver.requireValidNav(navMap, l1, "RebalanceEngine");
            BigDecimal nav2 = NavResolver.requireValidNav(navMap, l2, "RebalanceEngine");
            BigDecimal gainPerUnit1 = nav1.subtract(l1.costPerUnit());
            BigDecimal gainPerUnit2 = nav2.subtract(l2.costPerUnit());

            if (c1 == 1) {
                // Largest loss first
                return gainPerUnit1.compareTo(gainPerUnit2);
            }

            // Lowest gain ratio (highest cost basis ratio) first
            BigDecimal gainRatio1 = nav1.compareTo(BigDecimal.ZERO) > 0
                ? gainPerUnit1.divide(nav1, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal gainRatio2 = nav2.compareTo(BigDecimal.ZERO) > 0
                ? gainPerUnit2.divide(nav2, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            int ratioCmp = gainRatio1.compareTo(gainRatio2);
            if (ratioCmp != 0) return ratioCmp;

            return l1.lotId().compareTo(l2.lotId());
        });

        for (Lot lot : sortedLots) {
            if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal nav = NavResolver.requireValidNav(navMap, lot, "RebalanceEngine");
            BigDecimal lotValue = lot.remainingUnits().multiply(nav);
            BigDecimal redemptionFromLot = lotValue.min(remainingTarget);

            BigDecimal unitsToSell = BigDecimal.ZERO;
            if (nav.compareTo(BigDecimal.ZERO) > 0) {
                unitsToSell = redemptionFromLot.divide(nav, 4, RoundingMode.HALF_UP);
            }
            BigDecimal costBasisSlice = unitsToSell.multiply(lot.costPerUnit());
            BigDecimal gainSlice = redemptionFromLot.subtract(costBasisSlice);

            int cohort = determineCohort(lot, navMap, today, fiscalYear, rules);

            if (cohort == 1) {
                // Tier 1: Capital Loss Harvesting
                selected.add(new RebalanceLotSelection(
                    lot.lotId(),
                    lot.assetId(),
                    lot.assetName(),
                    unitsToSell,
                    redemptionFromLot,
                    costBasisSlice,
                    gainSlice,
                    "LOSS",
                    BigDecimal.ZERO,
                    LiquidationTier.TIER_1_LOSS_HARVEST
                ));
            } else if (cohort == 2) {
                // Tier 2 vs Tier 4: Dynamic Headroom Check
                if (unusedExemption.compareTo(BigDecimal.ZERO) > 0) {
                    if (gainSlice.compareTo(unusedExemption) <= 0) {
                        unusedExemption = unusedExemption.subtract(gainSlice).max(BigDecimal.ZERO);
                        selected.add(new RebalanceLotSelection(
                            lot.lotId(),
                            lot.assetId(),
                            lot.assetName(),
                            unitsToSell,
                            redemptionFromLot,
                            costBasisSlice,
                            gainSlice,
                            "LONG_TERM",
                            BigDecimal.ZERO,
                            LiquidationTier.TIER_2_LTCG_EXEMPT_HEADROOM
                        ));
                    } else {
                        // Straddles boundary: split into exempt slice and taxable slice
                        BigDecimal exemptGain = unusedExemption;
                        BigDecimal taxableGain = gainSlice.subtract(exemptGain);
                        unusedExemption = BigDecimal.ZERO;

                        BigDecimal proportionExempt = gainSlice.compareTo(BigDecimal.ZERO) > 0
                            ? exemptGain.divide(gainSlice, 6, RoundingMode.HALF_UP)
                            : BigDecimal.ONE;

                        BigDecimal unitsExempt = unitsToSell.multiply(proportionExempt).setScale(4, RoundingMode.HALF_UP);
                        BigDecimal unitsTaxable = unitsToSell.subtract(unitsExempt);

                        BigDecimal proceedsExempt = redemptionFromLot.multiply(proportionExempt).setScale(2, RoundingMode.HALF_UP);
                        BigDecimal proceedsTaxable = redemptionFromLot.subtract(proceedsExempt);

                        BigDecimal costExempt = costBasisSlice.multiply(proportionExempt).setScale(2, RoundingMode.HALF_UP);
                        BigDecimal costTaxable = costBasisSlice.subtract(costExempt);

                        BigDecimal taxDrag = taxableGain.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);

                        selected.add(new RebalanceLotSelection(
                            lot.lotId(),
                            lot.assetId(),
                            lot.assetName(),
                            unitsExempt,
                            proceedsExempt,
                            costExempt,
                            exemptGain,
                            "LONG_TERM",
                            BigDecimal.ZERO,
                            LiquidationTier.TIER_2_LTCG_EXEMPT_HEADROOM
                        ));

                        selected.add(new RebalanceLotSelection(
                            lot.lotId(),
                            lot.assetId(),
                            lot.assetName(),
                            unitsTaxable,
                            proceedsTaxable,
                            costTaxable,
                            taxableGain,
                            "LONG_TERM",
                            taxDrag,
                            LiquidationTier.TIER_4_TAXABLE_112A_LTCG
                        ));
                        totalTaxDrag = totalTaxDrag.add(taxDrag);
                    }
                } else {
                    BigDecimal taxDrag = gainSlice.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
                    selected.add(new RebalanceLotSelection(
                        lot.lotId(),
                        lot.assetId(),
                        lot.assetName(),
                        unitsToSell,
                        redemptionFromLot,
                        costBasisSlice,
                        gainSlice,
                        "LONG_TERM",
                        taxDrag,
                        LiquidationTier.TIER_4_TAXABLE_112A_LTCG
                    ));
                    totalTaxDrag = totalTaxDrag.add(taxDrag);
                }
            } else if (cohort == 3) {
                // Tier 3: Grandfathered Debt LTCG (12.5%)
                BigDecimal rate = rules.debtLegacyLtcgRate() != null ? rules.debtLegacyLtcgRate() : new BigDecimal("0.125");
                BigDecimal taxDrag = gainSlice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                selected.add(new RebalanceLotSelection(
                    lot.lotId(),
                    lot.assetId(),
                    lot.assetName(),
                    unitsToSell,
                    redemptionFromLot,
                    costBasisSlice,
                    gainSlice,
                    "LONG_TERM",
                    taxDrag,
                    LiquidationTier.TIER_3_GRANDFATHERED_DEBT_LTCG
                ));
                totalTaxDrag = totalTaxDrag.add(taxDrag);
            } else if (cohort == 5) {
                // Tier 5: Equity STCG (20%)
                BigDecimal rate = rules.equityStcgRate() != null ? rules.equityStcgRate() : new BigDecimal("0.20");
                BigDecimal taxDrag = gainSlice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                selected.add(new RebalanceLotSelection(
                    lot.lotId(),
                    lot.assetId(),
                    lot.assetName(),
                    unitsToSell,
                    redemptionFromLot,
                    costBasisSlice,
                    gainSlice,
                    "SHORT_TERM",
                    taxDrag,
                    LiquidationTier.TIER_5_EQUITY_STCG
                ));
                totalTaxDrag = totalTaxDrag.add(taxDrag);
            } else {
                // Tier 6: Section 50AA Debt & Non-Equity STCG (Slab Rate, default 30%)
                BigDecimal rate = rules.slabRate() != null ? rules.slabRate() : new BigDecimal("0.30");
                BigDecimal taxDrag = gainSlice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                selected.add(new RebalanceLotSelection(
                    lot.lotId(),
                    lot.assetId(),
                    lot.assetName(),
                    unitsToSell,
                    redemptionFromLot,
                    costBasisSlice,
                    gainSlice,
                    "SHORT_TERM",
                    taxDrag,
                    LiquidationTier.TIER_6_SPECIFIED_50AA_DEBT
                ));
                totalTaxDrag = totalTaxDrag.add(taxDrag);
            }

            actualRedemption = actualRedemption.add(redemptionFromLot);
            totalGain = totalGain.add(gainSlice);
            remainingTarget = remainingTarget.subtract(redemptionFromLot);
        }

        BigDecimal ltcgHarvested = effectiveHeadroom.subtract(unusedExemption);
        BigDecimal effTaxRate = BigDecimal.ZERO;
        if (actualRedemption.compareTo(BigDecimal.ZERO) > 0) {
            effTaxRate = totalTaxDrag.multiply(new BigDecimal("100")).divide(actualRedemption, 2, RoundingMode.HALF_UP);
        }

        BigDecimal deferredAmount = targetAmount.subtract(actualRedemption).max(BigDecimal.ZERO);

        String caveat = String.format(
            "Exemption headroom calculated against ledger state (₹%s available, ₹%s reserved for active harvest). Concurrent uncoordinated harvest and liquidation plans will breach statutory Section 112A limits.",
            remainingExemption != null ? remainingExemption.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00",
            reserved.setScale(2, RoundingMode.HALF_UP).toPlainString()
        );

        return new RebalancePreviewResult(
            targetAmount,
            actualRedemption,
            deferredAmount,
            totalGain,
            totalTaxDrag,
            effTaxRate,
            ltcgHarvested,
            selected,
            reserved,
            caveat
        );
    }

    public static int determineCohort(Lot lot, Map<String, BigDecimal> navMap, LocalDate today, String fiscalYear, TaxRulesConfig rules) {
        BigDecimal nav = NavResolver.requireValidNav(navMap, lot, "RebalanceEngine");
        BigDecimal gainPerUnit = nav.subtract(lot.costPerUnit());

        // Cohort 1: Capital Loss Harvesting across all asset categories
        if (gainPerUnit.compareTo(BigDecimal.ZERO) < 0) {
            return 1;
        }

        AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
        long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
        boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
        TaxTerm term = TaxClassifier.classifyTaxTerm(category, holdingDays, fiscalYear, isListed, lot.acquisitionDate(), today);
        boolean isLtcg = term == TaxTerm.LONG_TERM;

        if (category == AssetCategory.DEBT_SPECIFIED_50AA) {
            LocalDate apr2023Cutoff = LocalDate.of(2023, 4, 1);
            if (lot.acquisitionDate() != null && lot.acquisitionDate().isBefore(apr2023Cutoff) && holdingDays >= 730) {
                return 3; // Grandfathered Debt LTCG (12.5% rate)
            } else {
                return 6; // Section 50AA Debt (Slab Rate)
            }
        }

        if (category == AssetCategory.EQUITY) {
            return isLtcg ? 2 : 5; // Cohort 2: Equity LTCG (Exempt/Taxable 112A), Cohort 5: Equity STCG (20%)
        }

        // GOLD_SILVER, INTERNATIONAL, SGB
        if (isLtcg) {
            return 3; // Taxed at 12.5% LTCG alongside Grandfathered Debt
        } else {
            return 6; // Non-Equity STCG taxed at marginal slab rate (same as 50AA)
        }
    }
}
