#![allow(dead_code)]
use serde::Deserialize;


// ---------------------------------------------------------------------------
// /api/v1/sync/snapshot
// ---------------------------------------------------------------------------

#[derive(Debug, Deserialize, Clone)]
pub struct DailyBrief {
    pub status: Option<String>,
    pub headline: Option<String>,
    pub narration: Option<String>,
    #[serde(default)]
    pub verified: bool,
    pub generated_at: Option<String>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct Snapshot {
    pub sync_info: SyncInfo,
    pub holdings: Vec<FlatHolding>,
    pub tax_lots: Vec<FlatTaxLot>,
    pub radar_signals: Vec<RadarSignal>,
    #[serde(default)]
    pub net_worth_history: Vec<NetWorthPoint>,
    pub rebalance_plan: Option<RebalancePlan>,
    #[serde(default)]
    pub bucket_allocation: Vec<BucketStatus>,
    #[serde(default)]
    pub daily_brief: Option<DailyBrief>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct SyncInfo {
    pub fiscal_year: String,
    pub generated_at: String,
    pub portfolio_xirr: f64,
    pub xirr_percentage: String,
    pub total_invested: f64,
    pub current_value: f64,
    pub unrealized_gain: f64,
    pub formatted_current_value: String,
    pub formatted_total_invested: String,
    pub formatted_unrealized_gain: String,
    pub stale_nav_count: Option<i32>,
    pub has_stale_nav: Option<bool>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct FlatHolding {
    pub isin: String,
    pub fund_name: String,
    pub asset_bucket: String,
    pub current_value: f64,
    pub invested_value: f64,
    pub xirr: f64,
    pub formatted_current_value: String,
    pub formatted_invested_value: String,
    pub expense_ratio: Option<f64>,
    pub ter_status: Option<String>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct FlatTaxLot {
    pub isin: String,
    pub buy_date: String,
    pub units: f64,
    pub tax_classification: String,
    pub is_long_term: bool,
    pub holding_days: i64,
    pub days_to_ltcg: i64,
    pub estimated_tax_drag: Option<f64>,
    pub is_harvest_candidate: bool,
}

#[derive(Debug, Deserialize, Clone)]
pub struct RadarSignal {
    pub signal_type: String,
    pub title: String,
    pub subtitle: String,
    pub description: String,
    pub severity: String,
    pub badge_text: Option<String>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct NetWorthPoint {
    pub date: String,
    pub valuation: f64,
    pub invested: f64,
}

#[derive(Debug, Deserialize, Clone)]
pub struct BucketStatus {
    pub bucket: String,
    pub current_pct: f64,
    pub target_pct: f64,
    pub drift_pct: f64,
    pub is_drifted: bool,
}

// ---------------------------------------------------------------------------
// Rebalance Plan (embedded in snapshot)
// ---------------------------------------------------------------------------

#[derive(Debug, Deserialize, Clone)]
pub struct RebalancePlan {
    pub plan_id: String,
    pub generated_at: String,
    pub trigger: Option<RebalanceTrigger>,
    pub sell_side: Option<SellSidePlan>,
    pub buy_side: Option<BuySidePlan>,
    pub reasoning_narrative: Option<ReasoningNarrative>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct RebalanceTrigger {
    #[serde(rename = "type")]
    pub trigger_type: String,
    pub reason_label: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct SellSidePlan {
    pub total_required: Option<f64>,
    pub tax_summary: Option<TaxSummary>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct TaxSummary {
    pub total_ltcg_exempt: Option<f64>,
    pub statutory_exemption_cap: Option<f64>,
    pub exemption_headroom_after: Option<f64>,
    pub exemption_utilized_pct: Option<f64>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct BuySidePlan {
    pub total_to_invest: Option<f64>,
    pub buckets: Vec<RebalanceBucketAllocation>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct RebalanceBucketAllocation {
    pub bucket: String,
    pub target_pct: f64,
    pub current_pct: f64,
    pub post_rebalance_pct: f64,
    pub amount_allocated: Option<f64>,
    pub fund_breakdown: Vec<FundAllocation>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct FundAllocation {
    pub fund_id: String,
    pub fund_name: String,
    pub amount: Option<f64>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct ReasoningNarrative {
    pub headline: String,
    pub paragraphs: Vec<String>,
}

// ---------------------------------------------------------------------------
// /api/v1/analytics/overlap  (overlap_summary sub-field)
// ---------------------------------------------------------------------------

#[derive(Debug, Deserialize, Clone, Default)]
pub struct OverlapSummary {
    pub max_overlap_percentage: f64,
    pub fund_a_name: String,
    pub fund_b_name: String,
    pub threshold_breached: bool,
}

#[derive(Debug, Deserialize, Clone)]
pub struct OverlapResponse {
    pub overlap_summary: Option<OverlapSummary>,
}

// ---------------------------------------------------------------------------
// /api/v1/analytics/hrp-audit
// ---------------------------------------------------------------------------

#[derive(Debug, Deserialize, Clone)]
pub struct HrpAudit {
    #[serde(default = "default_hrp_status")]
    pub status: String,
    pub trading_days: Option<i32>,
    pub start_date: Option<String>,
    pub end_date: Option<String>,
    pub allocations: Option<Vec<HrpAllocation>>,
}

fn default_hrp_status() -> String {
    "SUCCESS".to_string()
}

#[derive(Debug, Deserialize, Clone)]
pub struct HrpAllocation {
    pub isin: String,
    pub name: String,
    pub bucket: String,
    pub target_pct: f64,
    pub hrp_pct: f64,
    pub drift_pct: f64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_deserialize_minimal_snapshot() {
        let json = r#"{
            "sync_info": {
                "fiscal_year": "2026-2027",
                "generated_at": "2026-11-01T10:00:00",
                "portfolio_xirr": 14.52,
                "xirr_percentage": "14.52%",
                "total_invested": 1000000.0,
                "current_value": 1250000.0,
                "unrealized_gain": 250000.0,
                "formatted_current_value": "₹ 12,50,000",
                "formatted_total_invested": "₹ 10,00,000",
                "formatted_unrealized_gain": "₹ 2,50,000"
            },
            "holdings": [],
            "tax_lots": [],
            "radar_signals": [],
            "net_worth_history": [],
            "rebalance_plan": null,
            "bucket_allocation": []
        }"#;

        let snapshot: Result<Snapshot, _> = serde_json::from_str(json);
        assert!(snapshot.is_ok(), "Failed to deserialize minimal snapshot: {:?}", snapshot.err());
        let s = snapshot.unwrap();
        assert_eq!(s.sync_info.fiscal_year, "2026-2027");
        assert_eq!(s.sync_info.portfolio_xirr, 14.52);
        assert!(s.holdings.is_empty());
    }

    #[test]
    fn test_deserialize_hrp_audit_default_status() {
        let json = r#"{
            "trading_days": 750,
            "start_date": "2023-10-01",
            "end_date": "2026-10-01",
            "allocations": []
        }"#;

        let audit: Result<HrpAudit, _> = serde_json::from_str(json);
        assert!(audit.is_ok());
        let a = audit.unwrap();
        assert_eq!(a.status, "SUCCESS");
        assert_eq!(a.trading_days, Some(750));
    }

    #[test]
    fn test_deserialize_daily_brief_verified() {
        let json = r#"{
            "status": "HEALTHY",
            "headline": "Portfolio all-time high",
            "narration": "Net worth breached target milestone with disciplined rebalancing.",
            "verified": true,
            "generated_at": "2026-11-01T08:00:00"
        }"#;

        let brief: Result<DailyBrief, _> = serde_json::from_str(json);
        assert!(brief.is_ok());
        let b = brief.unwrap();
        assert!(b.verified);
        assert_eq!(b.status.as_deref(), Some("HEALTHY"));
    }
}
