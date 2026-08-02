package com.portfolioos.core.reconciliation;

import com.portfolioos.core.model.TaxEvent;

import java.math.BigDecimal;
import java.util.List;

public class ReconciliationGate {

    public record ReconciliationResult(
        boolean isMatched,
        BigDecimal calculatedClosingUnits,
        BigDecimal declaredClosingUnits,
        BigDecimal delta,
        String errorMessage
    ) {}

    public static ReconciliationResult validateStatement(List<TaxEvent> events, BigDecimal declaredClosingUnits) {
        BigDecimal calculatedClosingUnits = BigDecimal.ZERO;
        for (TaxEvent event : events) {
            calculatedClosingUnits = calculatedClosingUnits.add(event.unitDelta());
        }

        BigDecimal delta = calculatedClosingUnits.subtract(declaredClosingUnits).abs();
        boolean isMatched = delta.compareTo(new BigDecimal("0.0001")) < 0;

        String errorMessage = null;
        if (!isMatched) {
            errorMessage = "Reconciliation Gate Failure: Calculated closing units (" + calculatedClosingUnits +
                           ") does not match declared closing units (" + declaredClosingUnits + "). Delta: " + delta;
        }

        return new ReconciliationResult(isMatched, calculatedClosingUnits, declaredClosingUnits, delta, errorMessage);
    }
}
