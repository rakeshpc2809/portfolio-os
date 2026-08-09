package com.portfolioos.core.reconciliation;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.TaxEvent;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class ReconciliationGate {

    public record ReconciliationResult(
        boolean isMatched,
        BigDecimal calculatedClosingUnits,
        BigDecimal declaredClosingUnits,
        BigDecimal delta,
        String errorMessage
    ) {}

    public record AssetReconciliationResult(
        String assetId,
        boolean isMatched,
        BigDecimal calculatedUnits,
        BigDecimal declaredUnits,
        BigDecimal delta
    ) {}

    public record MultiAssetReconciliationResult(
        boolean allMatched,
        List<AssetReconciliationResult> assetResults,
        String summaryMessage
    ) {}



    /**
     * Validates aggregate total portfolio closing units post-FIFO execution.
     */
    public static ReconciliationResult validateStatement(FifoMatcher.FifoResult fifoResult, BigDecimal declaredClosingUnits) {
        BigDecimal calculatedClosingUnits = fifoResult.openLots().stream()
            .map(Lot::remainingUnits)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal delta = calculatedClosingUnits.subtract(declaredClosingUnits).abs();
        boolean isMatched = delta.compareTo(new BigDecimal("0.0001")) < 0;

        String errorMessage = null;
        if (!isMatched) {
            errorMessage = "Reconciliation Gate Failure: Post-FIFO calculated closing units (" + calculatedClosingUnits +
                           ") does not match declared closing units (" + declaredClosingUnits + "). Delta: " + delta;
        }

        return new ReconciliationResult(isMatched, calculatedClosingUnits, declaredClosingUnits, delta, errorMessage);
    }

    /**
     * Validates closing units PER ASSET post-FIFO execution against declared AMC statement balances per asset.
     * Prevents cross-fund unit discrepancy masking.
     */
    public static MultiAssetReconciliationResult validateStatementPerAsset(
        FifoMatcher.FifoResult fifoResult,
        Map<String, BigDecimal> declaredAssetBalances
    ) {
        Map<String, BigDecimal> calculatedMap = fifoResult.openLots().stream()
            .collect(Collectors.groupingBy(
                Lot::assetId,
                Collectors.reducing(BigDecimal.ZERO, Lot::remainingUnits, BigDecimal::add)
            ));

        Set<String> allAssetIds = new HashSet<>(calculatedMap.keySet());
        if (declaredAssetBalances != null) {
            allAssetIds.addAll(declaredAssetBalances.keySet());
        }

        List<AssetReconciliationResult> assetResults = new ArrayList<>();
        boolean allMatched = true;

        for (String assetId : allAssetIds) {
            BigDecimal calcUnits = calculatedMap.getOrDefault(assetId, BigDecimal.ZERO);
            BigDecimal declUnits = declaredAssetBalances != null ? declaredAssetBalances.getOrDefault(assetId, BigDecimal.ZERO) : BigDecimal.ZERO;
            BigDecimal delta = calcUnits.subtract(declUnits).abs();
            boolean isMatched = delta.compareTo(new BigDecimal("0.0001")) < 0;

            if (!isMatched) {
                allMatched = false;
            }
            assetResults.add(new AssetReconciliationResult(assetId, isMatched, calcUnits, declUnits, delta));
        }

        String summary = allMatched
            ? "✓ All " + assetResults.size() + " asset balances matched declared statement units perfectly."
            : "⚠️ Reconciliation Gate Failure: " + assetResults.stream().filter(a -> !a.isMatched()).count() + " asset balance discrepancies detected.";

        return new MultiAssetReconciliationResult(allMatched, assetResults, summary);
    }
}
