package com.portfolioos.core.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TaxEvent(
    String id,
    String assetId,
    String assetName,
    String isin,
    EventType eventType,
    LocalDate eventDate,
    BigDecimal units,
    BigDecimal pricePerUnit,
    BigDecimal grossAmount,
    String sourceDocumentId,
    Instant ingestedAt
) {
    public BigDecimal unitDelta() {
        return switch (eventType) {
            case DISPOSAL, SGB_MATURITY -> units.negate();
            case SGB_INTEREST -> BigDecimal.ZERO;
            default -> units;
        };
    }
}
