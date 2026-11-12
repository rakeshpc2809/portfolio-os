#![allow(dead_code)]
use serde::{Deserialize, Serialize};

// ---------------------------------------------------------------------------
// /api/v1/tax/exemption-status
// ---------------------------------------------------------------------------

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct ExemptionStatus {
    pub fiscal_year: String,
    pub gross_ltcg: String,
    pub gross_ltcl: String,
    pub gross_stcg: String,
    pub gross_stcl: String,
    pub net_stcg: String,
    pub net_ltcg_before_exemption: String,
    pub exemption_limit: String,
    pub exemption_used: String,
    pub exemption_remaining: String,
    pub taxable_ltcg: String,
}

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
    #[serde(default)]
    pub ledger_hash: Option<String>,
    #[serde(default)]
    pub drawdown_status: Option<String>,
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
    #[serde(default)]
    pub current_nav: f64,
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
    #[serde(default)]
    pub cost_per_unit: f64,
    pub grandfathered_nav: Option<f64>,
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



#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_simulate_lumpsum_dto_deserialization() {
        let raw_json = r#"{
            "plan_id": "ac2e15e6-7e8f-4ad9-b649-372d167461b7",
            "generated_at": "2026-11-12T00:00:00",
            "trigger": {
                "type": "MANUAL_LUMPSUM",
                "reason_label": "Manual Lump-Sum Entry"
            },
            "sell_side": {
                "total_required": 0.0,
                "tax_summary": {
                    "total_ltcg_exempt": 0.0,
                    "statutory_exemption_cap": 125000.0,
                    "exemption_headroom_after": 92758.90,
                    "exemption_utilized_pct": 0.0
                }
            },
            "buy_side": {
                "total_to_invest": 50000.0,
                "buckets": [
                    {
                        "bucket": "EQUITY_CORE",
                        "target_pct": 50.37,
                        "current_pct": 49.54,
                        "post_rebalance_pct": 49.6,
                        "amount_allocated": 25374.06,
                        "fund_breakdown": [
                            {
                                "fund_id": "INF109KC12U0",
                                "fund_name": "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
                                "amount": 15224.44
                            }
                        ]
                    }
                ]
            },
            "reasoning_narrative": {
                "headline": "Manual Lump-Sum Inflow (₹50,000)",
                "paragraphs": ["Entered manual capital inflow of ₹50,000"]
            }
        }"#;

        let plan: Result<RebalancePlan, _> = serde_json::from_str(raw_json);
        assert!(plan.is_ok(), "Failed to deserialize RebalancePlan: {:?}", plan.err());
        let plan = plan.unwrap();
        assert_eq!(plan.plan_id, "ac2e15e6-7e8f-4ad9-b649-372d167461b7");
        assert_eq!(plan.buy_side.as_ref().unwrap().total_to_invest, Some(50000.0));
        assert_eq!(plan.buy_side.as_ref().unwrap().buckets.len(), 1);
        assert_eq!(plan.buy_side.as_ref().unwrap().buckets[0].amount_allocated, Some(25374.06));
    }
}
