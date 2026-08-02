package com.portfolioos.core.dtos;

import java.util.List;

public class SyncDtos {

    public record SyncInfoDto(
        long timestamp,
        String ledger_hash,
        String generated_at,
        String fiscal_year,
        double portfolio_xirr,
        String xirr_percentage,
        double total_invested,
        double current_value,
        double unrealized_gain,
        String formatted_current_value,
        String formatted_total_invested,
        String formatted_unrealized_gain
    ) {}

    public record FlatHoldingDto(
        String isin,
        String fund_name,
        double total_units,
        double avg_cost,
        double xirr,
        String asset_bucket,
        double current_value,
        double invested_value,
        String formatted_current_value,
        String formatted_invested_value
    ) {}

    public record FlatTaxLotDto(
        String isin,
        String buy_date,
        double units,
        String tax_classification,
        boolean is_long_term,
        Double grandfathered_nav,
        double cost_per_unit,
        long holding_days,
        long days_to_ltcg
    ) {}

    public record RadarSignalDto(
        String signal_type,
        String title,
        String subtitle,
        String description,
        String severity,
        String badge_text
    ) {}

    public record UnidirectionalSyncSnapshot(
        SyncInfoDto sync_info,
        List<FlatHoldingDto> holdings,
        List<FlatTaxLotDto> tax_lots,
        List<RadarSignalDto> radar_signals
    ) {}

    public record PairRequestDto(
        String device_id,
        String device_name
    ) {}

    public record PairResponseDto(
        String status,
        String token,
        String server_name
    ) {}
}
