package com.portfolioos.core.dtos;

import java.math.BigDecimal;

public record ParsedEventDto(
    String id,
    String assetId,
    String assetName,
    String isin,
    String folioNo,
    String eventType,
    String eventDate,
    BigDecimal units,
    BigDecimal pricePerUnit,
    BigDecimal grossAmount,
    String sourceDocumentId,
    BigDecimal declaredClosingUnits,
    String txFingerprint
) {
    public ParsedEventDto(
        String id,
        String assetId,
        String assetName,
        String isin,
        String eventType,
        String eventDate,
        BigDecimal units,
        BigDecimal pricePerUnit,
        BigDecimal grossAmount,
        String sourceDocumentId,
        BigDecimal declaredClosingUnits
    ) {
        this(id, assetId, assetName, isin, null, eventType, eventDate, units, pricePerUnit, grossAmount, sourceDocumentId, declaredClosingUnits, null);
    }

    public ParsedEventDto(
        String id,
        String assetId,
        String assetName,
        String isin,
        String eventType,
        String eventDate,
        BigDecimal units,
        BigDecimal pricePerUnit,
        BigDecimal grossAmount,
        String sourceDocumentId
    ) {
        this(id, assetId, assetName, isin, null, eventType, eventDate, units, pricePerUnit, grossAmount, sourceDocumentId, null, null);
    }
}
