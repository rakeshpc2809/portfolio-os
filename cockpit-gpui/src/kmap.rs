// cockpit-gpui/src/kmap.rs

#![allow(dead_code)]

use crate::models::Snapshot;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;

pub const SEC_112A_EXEMPTION_LIMIT: f64 = 125_000.0;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PredicateState {
    Zero = 0,
    One = 1,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClassifiedLot {
    pub isin: String,
    pub fund_name: String,
    pub units: f64,
    pub current_value: f64,
    pub unrealized_gain: f64,
    pub holding_days: i64,
    pub bitmask: u8, // 0bABCD
}

#[derive(Debug, Clone, Default)]
pub struct KMapCell {
    pub row_bits: u8, // AB: 0..3
    pub col_bits: u8, // CD: 0..3
    pub minterm: u8,  // 0bABCD
    pub lots: Vec<ClassifiedLot>,
    pub total_value: f64,
    pub total_gain: f64,
}

impl KMapCell {
    /// Cell 1111 (m15): Band Breached & LTCG & Exit Load Free & 112A Exemption Room Available
    pub fn is_prime_implicant(&self) -> bool {
        self.minterm == 0b1111
    }

    /// Cell 0111 (m7): Equilibrium & LTCG & Exit Load Free & 112A Exemption Room Available (March Harvest)
    pub fn is_march_harvest(&self) -> bool {
        self.minterm == 0b0111
    }
}

pub struct KMapMatrix {
    pub cells: [KMapCell; 16],
    pub total_harvest_room_remaining: f64,
    pub prime_implicant_liquidable_value: f64,
}

/// Convert 2-bit Gray code index (0, 1, 2, 3) to binary bits (00, 01, 11, 10)
pub fn gray_to_bits(gray_idx: usize) -> u8 {
    match gray_idx {
        0 => 0b00,
        1 => 0b01,
        2 => 0b11,
        3 => 0b10,
        _ => 0b00,
    }
}

/// Computes required exit-load-free holding days per scheme, matching ExitLoadCalculator in core-node.
pub fn required_exit_load_days(isin: &str, fund_name: &str) -> i64 {
    match isin {
        "INF205K01KR8" => 15,  // Invesco India Arbitrage Fund
        "INF879O01027" => 730, // Parag Parikh Flexi Cap Fund (2 years)
        "INF204K01K15" => 365, // Nippon India Small Cap Fund (1 year)
        "INF247L01BM8" => 15,  // Motilal Oswal Gold and Silver Passive FoF
        _ => {
            let upper = fund_name.to_ascii_uppercase();
            if upper.contains("PARAG PARIKH") && upper.contains("FLEXI") {
                730
            } else if upper.contains("SMALL CAP") && !upper.contains("INDEX") {
                365
            } else if upper.contains("ARBITRAGE") {
                15
            } else if upper.contains("GOLD") || upper.contains("SILVER") || upper.contains("FOF") {
                15
            } else if upper.contains("LIQUID") || upper.contains("OVERNIGHT") {
                7
            } else {
                0 // Direct passive index funds (0 exit load)
            }
        }
    }
}

/// Classifies snapshot tax lots into a 4x4 Gray-code state matrix (16 discrete minterms).
///
/// Invariants enforced:
/// - Predicate A: Sourced from backend `b.is_drifted && b.drift_pct > 0.0` (overweight breach under 5/25 relative or liquid ±2.5% absolute band).
/// - Predicate B: Equity LTCG under Section 112A (>365d, >=65% domestic equity). Excludes Gold/Silver FoF and specified debt.
/// - Predicate C: Scheme-specific exit load rules (PPFAS 730d, Small Cap 365d, Arbitrage 15d, Index 0d) with RTA FIFO gating per ISIN.
/// - Predicate D: Section 112A room remaining after backend Section 70 loss netting.
/// - FIFO sorting: Lots sorted strictly by (isin, buy_date) ascending before evaluation.
pub fn classify_snapshot(snapshot: &Snapshot, realized_ltcg_ytd: f64) -> KMapMatrix {
    let mut cells: [KMapCell; 16] = Default::default();

    // Initialize 4x4 matrix in Gray-code order (Rows: AB, Cols: CD)
    for r in 0..4 {
        for c in 0..4 {
            let ab = gray_to_bits(r);
            let cd = gray_to_bits(c);
            let minterm = (ab << 2) | cd;
            let cell_idx = r * 4 + c;
            cells[cell_idx] = KMapCell {
                row_bits: ab,
                col_bits: cd,
                minterm,
                lots: Vec::new(),
                total_value: 0.0,
                total_gain: 0.0,
            };
        }
    }

    // Source Section 112A headroom from backend rebalance plan (which nets losses under Sec 70)
    let initial_headroom = snapshot
        .rebalance_plan
        .as_ref()
        .and_then(|p| p.sell_side.as_ref())
        .and_then(|s| s.tax_summary.as_ref())
        .and_then(|t| t.exemption_headroom_after)
        .unwrap_or_else(|| (SEC_112A_EXEMPTION_LIMIT - realized_ltcg_ytd).max(0.0));

    let mut remaining_harvest_room = initial_headroom;

    // Invariant 2 (RTA FIFO Law Sec 45(2A)):
    // Sort tax lots strictly FIFO by (isin, buy_date) ascending.
    let mut sorted_lots = snapshot.tax_lots.clone();
    sorted_lots.sort_by(|a, b| {
        a.isin.cmp(&b.isin).then_with(|| a.buy_date.cmp(&b.buy_date))
    });

    // Track schemes whose older lots are locked by exit load.
    // Under RTA FIFO, newer lots cannot be redeemed ahead of locked older lots without penalty.
    let mut exit_load_locked_schemes: HashSet<String> = HashSet::new();

    for lot in &sorted_lots {
        let holding = snapshot.holdings.iter().find(|h| h.isin == lot.isin);
        let fund_name = holding.map(|h| h.fund_name.as_str()).unwrap_or("Unknown Fund");
        let asset_bucket = holding.map(|h| h.asset_bucket.as_str()).unwrap_or("");

        // Predicate A: Band Breached (Overweight beyond policy drift band)
        let is_overweight = snapshot.bucket_allocation.iter().any(|b| {
            b.bucket == asset_bucket && b.is_drifted && b.drift_pct > 0.0
        });
        let a = if is_overweight { 1u8 } else { 0u8 };

        // Predicate B: Equity LTCG under Section 112A
        // Motilal Gold & Silver FoF (INF247L01BM8) is unlisted non-equity (LTCG >730d @ 12.5% without 112A exemption)
        // Liquid Buffer is specified debt under Sec 50AA (always short-term slab rate)
        let is_equity_112a = lot.isin != "INF247L01BM8"
            && !asset_bucket.eq_ignore_ascii_case("LIQUID_BUFFER");
        let b = if lot.is_long_term && is_equity_112a { 1u8 } else { 0u8 };

        // Predicate C: Exit Load Free with RTA FIFO scheme gating
        let req_days = required_exit_load_days(&lot.isin, fund_name);
        let lot_exit_free = lot.holding_days >= req_days;
        if !lot_exit_free {
            exit_load_locked_schemes.insert(lot.isin.clone());
        }
        // If an older lot in this scheme is locked, this lot cannot be cherry-picked
        let is_exit_free = lot_exit_free && !exit_load_locked_schemes.contains(&lot.isin);
        let c = if is_exit_free { 1u8 } else { 0u8 };

        // Financial Calculation: Accurate NAV and Cost Basis
        let nav = holding.map(|h| h.current_nav).unwrap_or(0.0);
        let cost_basis = lot.grandfathered_nav.unwrap_or(lot.cost_per_unit);
        let val = lot.units * nav;
        let lot_gain = val - (lot.units * cost_basis);

        // Predicate D: Section 112A Exemption Room Available
        // Only applicable if lot is Equity LTCG (b == 1) and Exit Load Free (c == 1).
        // Under Sec 70, capital losses do not consume headroom.
        let d = if b == 1 && c == 1 {
            if lot_gain <= remaining_harvest_room {
                if lot_gain > 0.0 {
                    remaining_harvest_room -= lot_gain;
                }
                1u8
            } else {
                0u8
            }
        } else {
            0u8
        };

        let minterm = (a << 3) | (b << 2) | (c << 1) | d;

        // Locate cell matching this minterm in the 4x4 Gray-code grid
        if let Some(cell) = cells.iter_mut().find(|cell| cell.minterm == minterm) {
            cell.total_value += val;
            cell.total_gain += lot_gain;
            cell.lots.push(ClassifiedLot {
                isin: lot.isin.clone(),
                fund_name: fund_name.to_string(),
                units: lot.units,
                current_value: val,
                unrealized_gain: lot_gain,
                holding_days: lot.holding_days,
                bitmask: minterm,
            });
        }
    }

    let prime_val = cells
        .iter()
        .filter(|c| c.is_prime_implicant())
        .map(|c| c.total_value)
        .sum();

    KMapMatrix {
        cells,
        total_harvest_room_remaining: remaining_harvest_room.max(0.0),
        prime_implicant_liquidable_value: prime_val,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::*;

    fn mock_snapshot() -> Snapshot {
        Snapshot {
            sync_info: SyncInfo {
                fiscal_year: "2026-2027".to_string(),
                generated_at: "2026-11-11T00:00:00Z".to_string(),
                portfolio_xirr: 16.5,
                xirr_percentage: "16.5%".to_string(),
                total_invested: 1_000_000.0,
                current_value: 1_200_000.0,
                unrealized_gain: 200_000.0,
                formatted_current_value: "₹ 12,00,000".to_string(),
                formatted_total_invested: "₹ 10,00_000".to_string(),
                formatted_unrealized_gain: "₹ 2,00,000".to_string(),
                stale_nav_count: None,
                has_stale_nav: None,
            },
            holdings: vec![
                FlatHolding {
                    isin: "INF879O01027".to_string(), // PPFAS Flexi Cap (730d exit load)
                    fund_name: "Parag Parikh Flexi Cap Fund".to_string(),
                    asset_bucket: "EQUITY_CORE".to_string(),
                    current_value: 600_000.0,
                    invested_value: 500_000.0,
                    xirr: 18.0,
                    formatted_current_value: "₹ 6,00,000".to_string(),
                    formatted_invested_value: "₹ 5,00,000".to_string(),
                    current_nav: 100.0,
                    expense_ratio: Some(0.65),
                    ter_status: Some("OPTIMAL".to_string()),
                },
                FlatHolding {
                    isin: "INF204K01K15".to_string(), // Nippon Small Cap (365d exit load)
                    fund_name: "Nippon India Small Cap Fund".to_string(),
                    asset_bucket: "EQUITY_SATELLITE".to_string(),
                    current_value: 300_000.0,
                    invested_value: 250_000.0,
                    xirr: 20.0,
                    formatted_current_value: "₹ 3,00,000".to_string(),
                    formatted_invested_value: "₹ 2,50,000".to_string(),
                    current_nav: 150.0,
                    expense_ratio: Some(0.75),
                    ter_status: Some("OPTIMAL".to_string()),
                },
                FlatHolding {
                    isin: "INF247L01BM8".to_string(), // Motilal Gold & Silver FoF (non-equity 112)
                    fund_name: "Motilal Oswal Gold and Silver FoF".to_string(),
                    asset_bucket: "GOLD_SILVER".to_string(),
                    current_value: 150_000.0,
                    invested_value: 130_000.0,
                    xirr: 12.0,
                    formatted_current_value: "₹ 1,50,000".to_string(),
                    formatted_invested_value: "₹ 1,30,000".to_string(),
                    current_nav: 20.0,
                    expense_ratio: Some(0.40),
                    ter_status: Some("OPTIMAL".to_string()),
                },
            ],
            tax_lots: vec![],
            radar_signals: vec![],
            net_worth_history: vec![],
            rebalance_plan: Some(RebalancePlan {
                plan_id: "plan-1".to_string(),
                generated_at: "2026-11-11T00:00:00Z".to_string(),
                trigger: None,
                sell_side: Some(SellSidePlan {
                    total_required: Some(50_000.0),
                    tax_summary: Some(TaxSummary {
                        total_ltcg_exempt: Some(0.0),
                        statutory_exemption_cap: Some(125_000.0),
                        exemption_headroom_after: Some(100_000.0), // ₹1,00,000 headroom
                        exemption_utilized_pct: Some(20.0),
                    }),
                }),
                buy_side: None,
                reasoning_narrative: None,
            }),
            bucket_allocation: vec![
                BucketStatus {
                    bucket: "EQUITY_CORE".to_string(),
                    current_pct: 65.0,
                    target_pct: 50.0,
                    drift_pct: 15.0,
                    is_drifted: true, // Overweight breach!
                },
                BucketStatus {
                    bucket: "EQUITY_SATELLITE".to_string(),
                    current_pct: 20.0,
                    target_pct: 20.0,
                    drift_pct: 0.0,
                    is_drifted: false, // Equilibrium
                },
            ],
            daily_brief: None,
        }
    }

    #[test]
    fn test_gray_code_bijection() {
        assert_eq!(gray_to_bits(0), 0b00);
        assert_eq!(gray_to_bits(1), 0b01);
        assert_eq!(gray_to_bits(2), 0b11);
        assert_eq!(gray_to_bits(3), 0b10);
    }

    #[test]
    fn test_exit_load_requirements() {
        assert_eq!(required_exit_load_days("INF879O01027", "Parag Parikh Flexi Cap"), 730);
        assert_eq!(required_exit_load_days("INF204K01K15", "Nippon India Small Cap"), 365);
        assert_eq!(required_exit_load_days("INF205K01KR8", "Invesco India Arbitrage"), 15);
        assert_eq!(required_exit_load_days("INF247L01BM8", "Motilal Gold Silver FoF"), 15);
        assert_eq!(required_exit_load_days("INF204K01H36", "Nippon Nifty 50 Index"), 0);
    }

    #[test]
    fn test_ppfas_730_day_exit_load_gating() {
        let mut snap = mock_snapshot();
        // Lot at 400 days: LTCG (>365d) but NOT exit-load free (requires 730d for PPFAS)
        snap.tax_lots = vec![FlatTaxLot {
            isin: "INF879O01027".to_string(),
            buy_date: "2025-10-01".to_string(),
            units: 100.0,
            tax_classification: "LTCG".to_string(),
            is_long_term: true,
            cost_per_unit: 80.0,
            grandfathered_nav: None,
            holding_days: 400, // < 730!
            days_to_ltcg: 0,
            estimated_tax_drag: None,
            is_harvest_candidate: true,
        }];

        let matrix = classify_snapshot(&snap, 0.0);
        // A=1 (EQUITY_CORE drifted), B=1 (LTCG), C=0 (Exit load locked <730d), D=0 (cannot harvest under 112A if exit load applies)
        // Minterm: 0b1100 = 12
        let cell_1100 = matrix.cells.iter().find(|c| c.minterm == 0b1100).unwrap();
        assert_eq!(cell_1100.lots.len(), 1);
        assert_eq!(cell_1100.lots[0].bitmask, 0b1100);
    }

    #[test]
    fn test_prime_implicant_and_march_harvest() {
        let mut snap = mock_snapshot();
        snap.tax_lots = vec![
            // Lot 1: PPFAS 800 days, Overweight bucket (EQUITY_CORE) -> Prime Implicant (1111)
            FlatTaxLot {
                isin: "INF879O01027".to_string(),
                buy_date: "2024-01-01".to_string(),
                units: 100.0,
                tax_classification: "LTCG".to_string(),
                is_long_term: true,
                cost_per_unit: 80.0,
                grandfathered_nav: None,
                holding_days: 800, // > 730
                days_to_ltcg: 0,
                estimated_tax_drag: None,
                is_harvest_candidate: true,
            },
            // Lot 2: Nippon Small Cap 400 days, Equilibrium bucket (EQUITY_SATELLITE) -> March Harvest (0111)
            FlatTaxLot {
                isin: "INF204K01K15".to_string(),
                buy_date: "2025-01-01".to_string(),
                units: 100.0,
                tax_classification: "LTCG".to_string(),
                is_long_term: true,
                cost_per_unit: 120.0,
                grandfathered_nav: None,
                holding_days: 400, // > 365
                days_to_ltcg: 0,
                estimated_tax_drag: None,
                is_harvest_candidate: true,
            },
        ];

        let matrix = classify_snapshot(&snap, 0.0);
        let prime_cell = matrix.cells.iter().find(|c| c.is_prime_implicant()).unwrap();
        assert_eq!(prime_cell.lots.len(), 1);
        assert_eq!(prime_cell.lots[0].isin, "INF879O01027");

        let march_cell = matrix.cells.iter().find(|c| c.is_march_harvest()).unwrap();
        assert_eq!(march_cell.lots.len(), 1);
        assert_eq!(march_cell.lots[0].isin, "INF204K01K15");
    }

    #[test]
    fn test_fifo_exit_load_scheme_gating() {
        let mut snap = mock_snapshot();
        // Two lots of Nippon Small Cap (365d required):
        // Lot 1 (Older, buy_date 2026-06-01): holding_days = 100 (< 365, locked!)
        // Lot 2 (Newer, buy_date 2026-09-01): holding_days = 40 (locked!)
        // If an older lot is locked, the scheme is locked from exit-load-free redemption.
        snap.tax_lots = vec![
            FlatTaxLot {
                isin: "INF204K01K15".to_string(),
                buy_date: "2026-06-01".to_string(),
                units: 50.0,
                tax_classification: "STCG".to_string(),
                is_long_term: false,
                cost_per_unit: 100.0,
                grandfathered_nav: None,
                holding_days: 100,
                days_to_ltcg: 265,
                estimated_tax_drag: None,
                is_harvest_candidate: false,
            },
            FlatTaxLot {
                isin: "INF204K01K15".to_string(),
                buy_date: "2026-09-01".to_string(),
                units: 50.0,
                tax_classification: "STCG".to_string(),
                is_long_term: false,
                cost_per_unit: 110.0,
                grandfathered_nav: None,
                holding_days: 40,
                days_to_ltcg: 325,
                estimated_tax_drag: None,
                is_harvest_candidate: false,
            },
        ];

        let matrix = classify_snapshot(&snap, 0.0);
        for cell in &matrix.cells {
            for lot in &cell.lots {
                if lot.isin == "INF204K01K15" {
                    // Predicate C must be 0 for both (bit 1 of ABCD is 0)
                    assert_eq!((lot.bitmask >> 1) & 1, 0, "Lot must not be exit-load free when older lot is locked");
                }
            }
        }
    }
}
