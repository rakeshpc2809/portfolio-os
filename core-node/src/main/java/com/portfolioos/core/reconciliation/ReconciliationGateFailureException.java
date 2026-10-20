package com.portfolioos.core.reconciliation;

public class ReconciliationGateFailureException extends RuntimeException {
    private final ReconciliationGate.MultiAssetReconciliationResult reconciliationResult;

    public ReconciliationGateFailureException(ReconciliationGate.MultiAssetReconciliationResult reconciliationResult) {
        super(reconciliationResult != null ? reconciliationResult.summaryMessage() : "Reconciliation Gate Failure");
        this.reconciliationResult = reconciliationResult;
    }

    public ReconciliationGate.MultiAssetReconciliationResult getReconciliationResult() {
        return reconciliationResult;
    }
}
