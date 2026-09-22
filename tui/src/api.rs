#![allow(dead_code)]
use serde::Deserialize;


// ---------------------------------------------------------------------------
// /api/v1/sync/snapshot
// ---------------------------------------------------------------------------

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
    pub status: String,
    pub trading_days: Option<i32>,
    pub start_date: Option<String>,
    pub end_date: Option<String>,
    pub allocations: Option<Vec<HrpAllocation>>,
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
