// cockpit-gpui/src/kmap.rs

use crate::models::Snapshot;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;

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

    /// In Section 112A tax mapping, Predicate D (112A statutory exemption room)
    /// cannot apply to Short-Term Capital Gains (B = 0). Under Boolean algebra,
    /// these 4 states (m1: 0001, m3: 0011, m9: 1001, m11: 1011) are classical
    /// Don't Care (X) conditions.
    pub fn is_dont_care(&self) -> bool {
        let b = (self.minterm >> 2) & 1;
        let d = self.minterm & 1;
        b == 0 && d == 1
    }
}

pub struct KMapMatrix {
    pub cells: [KMapCell; 16],
    pub statutory_exemption_room_before: f64,
    pub projected_harvest_room_remaining: f64,
    pub prime_implicant_liquidable_value: f64,
    pub march_harvest_liquidable_value: f64,
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
/// - Predicate C: Scheme-specific exit load rules (PPFAS 730d, Small Cap 365d, Arbitrage 15d, Index 0d).
/// - Predicate D: Section 112A room remaining, sourced directly from `ExemptionStatus.exemption_remaining`.
/// - Prioritization: Overweight holdings sorted first, ranked by highest drift percentage descending,
///   preventing alphabetical bias so the most drifted schemes claim 112A headroom first for m15 trims.
/// - RTA FIFO Law (Sec 45(2A)): Within each scheme, lots are evaluated strictly chronologically (buy_date ascending).
///   If an older lot in a scheme exceeds remaining headroom (D=0), all newer lots in that scheme receive D=0
///   (cherry-picking prohibited by law).
pub fn classify_snapshot(snapshot: &Snapshot, available_exemption_room: f64) -> KMapMatrix {
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

    let mut remaining_harvest_room = available_exemption_room;

    // Prioritized Sorting Order:
    // 1. Overweight buckets first (A = 1 lots get first priority to consume 112A room for m15 Prime Implicant).
    // 2. Highest positive drift percentage descending within the overweight group (severely drifted trimmed first).
    // 3. Group by ISIN to maintain scheme coherence.
    // 4. Strict FIFO ascending buy_date within each scheme (RTA FIFO Law Sec 45(2A)).
    let mut sorted_lots = snapshot.tax_lots.clone();
    sorted_lots.sort_by(|a, b| {
        let a_holding = snapshot.holdings.iter().find(|h| h.isin == a.isin);
        let b_holding = snapshot.holdings.iter().find(|h| h.isin == b.isin);
        let a_bucket = a_holding.map(|h| h.asset_bucket.as_str()).unwrap_or("");
        let b_bucket = b_holding.map(|h| h.asset_bucket.as_str()).unwrap_or("");

        let a_b_stat = snapshot.bucket_allocation.iter().find(|bk| bk.bucket == a_bucket);
        let b_b_stat = snapshot.bucket_allocation.iter().find(|bk| bk.bucket == b_bucket);

        let a_overweight = a_b_stat.map(|bk| bk.is_drifted && bk.drift_pct > 0.0).unwrap_or(false);
        let b_overweight = b_b_stat.map(|bk| bk.is_drifted && bk.drift_pct > 0.0).unwrap_or(false);

        let a_drift = a_b_stat.map(|bk| bk.drift_pct).unwrap_or(0.0);
        let b_drift = b_b_stat.map(|bk| bk.drift_pct).unwrap_or(0.0);

        b_overweight.cmp(&a_overweight)
            .then_with(|| b_drift.partial_cmp(&a_drift).unwrap_or(std::cmp::Ordering::Equal))
            .then_with(|| a.isin.cmp(&b.isin))
            .then_with(|| a.buy_date.cmp(&b.buy_date))
    });

    // Track schemes whose headroom is exhausted by an older lot.
    // Under Section 45(2A) FIFO, once an older lot cannot fit into remaining headroom,
    // a newer lot with smaller gain cannot be cherry-picked ahead of it.
    let mut headroom_exhausted_schemes: HashSet<String> = HashSet::new();

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

        // Predicate C: Exit Load Free
        let req_days = required_exit_load_days(&lot.isin, fund_name);
        let is_exit_free = lot.holding_days >= req_days;
        let c = if is_exit_free { 1u8 } else { 0u8 };

        // Financial Calculation: Accurate NAV and Cost Basis
        let nav = holding.map(|h| h.current_nav).unwrap_or(0.0);
        let cost_basis = lot.grandfathered_nav.unwrap_or(lot.cost_per_unit);
        let val = lot.units * nav;
        let lot_gain = val - (lot.units * cost_basis);

        // Predicate D: Section 112A Exemption Room Available
        // Invariants:
        // 1. Only Equity LTCG (b == 1) qualifies for Section 112A statutory exemption room.
        //    Short-Term gains (b == 0) cannot receive 112A exemption (marked Don't Care 'X').
        // 2. FIFO Gating: If an older lot in this scheme already exceeded remaining room,
        //    newer lots cannot cherry-pick D=1.
        // 3. Headroom Consumption: Only exit-load-free lots (c == 1) actually consume remaining room
        //    when harvested. Exit-locked lots (c == 0) may have D=1 if lot_gain fits, enabling
        //    cells m5 (0101) and m13 (1101) for maturation tracking.
        let is_scheme_exhausted = headroom_exhausted_schemes.contains(&lot.isin);

        let d = if b == 1 && !is_scheme_exhausted {
            if lot_gain <= remaining_harvest_room {
                if c == 1 && lot_gain > 0.0 {
                    remaining_harvest_room -= lot_gain;
                }
                1u8
            } else {
                headroom_exhausted_schemes.insert(lot.isin.clone());
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

    let march_val = cells
        .iter()
        .filter(|c| c.is_march_harvest())
        .map(|c| c.total_value)
        .sum();

    KMapMatrix {
        cells,
        statutory_exemption_room_before: available_exemption_room,
        projected_harvest_room_remaining: remaining_harvest_room.max(0.0),
        prime_implicant_liquidable_value: prime_val,
        march_harvest_liquidable_value: march_val,
    }
}

#[cfg(test)]
pub mod tests {
    use super::*;
    use crate::models::*;

    pub fn mock_snapshot() -> Snapshot {
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
                ledger_hash: Some("d705e9b28a1c".to_string()),
                drawdown_status: Some("NORMAL".to_string()),
            },
            holdings: vec![
                FlatHolding {
                    isin: "INF879O01027".to_string(), // PPFAS Flexi Cap (730d exit load)
                    fund_name: "Parag Parikh Flexi Cap Fund".to_string(),
                    asset_bucket: "EQUITY_CORE".to_string(),
                    current_value: 600_000.0,
                    invested_value: 500_000.0,
                    xirr: 18.2,
                    formatted_current_value: "₹ 6,00,000".to_string(),
                    formatted_invested_value: "₹ 5,00,000".to_string(),
                    current_nav: 200.0,
                    expense_ratio: Some(0.65),
                    ter_status: Some("OPTIMAL".to_string()),
                },
                FlatHolding {
                    isin: "INF204K01K15".to_string(), // Nippon Small Cap (365d exit load)
                    fund_name: "Nippon India Small Cap Fund".to_string(),
                    asset_bucket: "EQUITY_SATELLITE".to_string(),
                    current_value: 400_000.0,
                    invested_value: 350_000.0,
                    xirr: 21.0,
                    formatted_current_value: "₹ 4,00,000".to_string(),
                    formatted_invested_value: "₹ 3,50,000".to_string(),
                    current_nav: 150.0,
                    expense_ratio: Some(0.75),
                    ter_status: Some("OPTIMAL".to_string()),
                },
            ],
            tax_lots: Vec::new(),
            radar_signals: Vec::new(),
            net_worth_history: Vec::new(),
            rebalance_plan: None,
            bucket_allocation: vec![
                BucketStatus {
                    bucket: "EQUITY_CORE".to_string(),
                    current_pct: 60.0,
                    target_pct: 50.0,
                    drift_pct: 10.0,
                    is_drifted: true, // Overweight breach -> Predicate A = 1
                },
                BucketStatus {
                    bucket: "EQUITY_SATELLITE".to_string(),
                    current_pct: 40.0,
                    target_pct: 50.0,
                    drift_pct: -10.0,
                    is_drifted: false, // Equilibrium / underweight -> Predicate A = 0
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
    fn test_exit_load_calculator_parity() {
        assert_eq!(required_exit_load_days("INF205K01KR8", ""), 15);
        assert_eq!(required_exit_load_days("INF879O01027", ""), 730);
        assert_eq!(required_exit_load_days("INF204K01K15", ""), 365);
        assert_eq!(required_exit_load_days("INF247L01BM8", ""), 15);

        assert_eq!(required_exit_load_days("UNKNOWN", "Parag Parikh Flexi Cap Fund Direct Growth"), 730);
        assert_eq!(required_exit_load_days("UNKNOWN", "Tata Small Cap Fund Direct Growth"), 365);
        assert_eq!(required_exit_load_days("UNKNOWN", "Nippon India Small Cap Fund Direct Growth"), 365);
        assert_eq!(required_exit_load_days("UNKNOWN", "Nifty Smallcap 250 Index Fund Direct Growth"), 0);
        assert_eq!(required_exit_load_days("UNKNOWN", "Invesco India Arbitrage Fund Direct Growth"), 15);
        assert_eq!(required_exit_load_days("UNKNOWN", "Motilal Oswal Gold and Silver Passive FoF"), 15);
        assert_eq!(required_exit_load_days("UNKNOWN", "ICICI Prudential Liquid Fund Direct Growth"), 7);
        assert_eq!(required_exit_load_days("UNKNOWN", "UTI Overnight Fund Direct Growth"), 7);
        assert_eq!(required_exit_load_days("UNKNOWN", "UTI Nifty 50 Index Fund Direct Growth"), 0);
    }

    #[test]
    fn test_ppfas_730_day_exit_load_and_decoupled_predicates() {
        let mut snap = mock_snapshot();
        snap.tax_lots = vec![FlatTaxLot {
            isin: "INF879O01027".to_string(),
            buy_date: "2025-10-01".to_string(),
            units: 100.0,
            tax_classification: "LTCG".to_string(),
            is_long_term: true,
            cost_per_unit: 80.0,
            grandfathered_nav: None,
            holding_days: 400, // < 730
            days_to_ltcg: 0,
            estimated_tax_drag: None,
            is_harvest_candidate: true,
        }];

        let matrix = classify_snapshot(&snap, 125_000.0);
        let cell_1101 = matrix.cells.iter().find(|c| c.minterm == 0b1101).unwrap();
        assert_eq!(cell_1101.lots.len(), 1);
        assert_eq!(cell_1101.lots[0].bitmask, 0b1101);
        assert_eq!(matrix.statutory_exemption_room_before, 125_000.0);
        assert_eq!(matrix.projected_harvest_room_remaining, 125_000.0);
    }

    #[test]
    fn test_prime_implicant_and_march_harvest() {
        let mut snap = mock_snapshot();
        snap.tax_lots = vec![
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

        let matrix = classify_snapshot(&snap, 125_000.0);
        let prime_cell = matrix.cells.iter().find(|c| c.is_prime_implicant()).unwrap();
        assert_eq!(prime_cell.lots.len(), 1);
        assert_eq!(prime_cell.lots[0].isin, "INF879O01027");

        let march_cell = matrix.cells.iter().find(|c| c.is_march_harvest()).unwrap();
        assert_eq!(march_cell.lots.len(), 1);
        assert_eq!(march_cell.lots[0].isin, "INF204K01K15");
    }

    #[test]
    fn test_fifo_headroom_scheme_gating_prevents_cherry_picking() {
        let mut snap = mock_snapshot();
        snap.tax_lots = vec![
            FlatTaxLot {
                isin: "INF204K01K15".to_string(),
                buy_date: "2024-01-01".to_string(),
                units: 1000.0,
                tax_classification: "LTCG".to_string(),
                is_long_term: true,
                cost_per_unit: 100.0,
                grandfathered_nav: None,
                holding_days: 400,
                days_to_ltcg: 0,
                estimated_tax_drag: None,
                is_harvest_candidate: true,
            },
            FlatTaxLot {
                isin: "INF204K01K15".to_string(),
                buy_date: "2024-06-01".to_string(),
                units: 100.0,
                tax_classification: "LTCG".to_string(),
                is_long_term: true,
                cost_per_unit: 140.0,
                grandfathered_nav: None,
                holding_days: 380,
                days_to_ltcg: 0,
                estimated_tax_drag: None,
                is_harvest_candidate: true,
            },
        ];

        // Available headroom = 20,000 (Lot 1 gain = 50,000 exceeds it)
        let matrix = classify_snapshot(&snap, 20_000.0);

        let march_cell = matrix.cells.iter().find(|c| c.is_march_harvest()).unwrap();
        assert_eq!(march_cell.lots.len(), 0, "Newer lot must not cherry-pick 112A headroom ahead of older lot");

        let cell_0110 = matrix.cells.iter().find(|c| c.minterm == 0b0110).unwrap();
        assert_eq!(cell_0110.lots.len(), 2, "Both lots must reside in 0110 (LTCG, exit-free, headroom-exhausted)");
    }

    #[test]
    fn test_overweight_drift_severity_sort_overrides_alphabetical() {
        let mut snap = mock_snapshot();
        // Setup two overweight buckets:
        // Bucket 1: EQUITY_CORE has +5.0% drift (Scheme: ZZZ_FUND)
        // Bucket 2: EQUITY_SATELLITE has +15.0% drift (Scheme: AAA_FUND)
        // Alphabetically, AAA_FUND < ZZZ_FUND.
        // But EQUITY_SATELLITE is more severely drifted (+15% vs +5%).
        // The classifier must evaluate AAA_FUND first because of drift severity (+15% > +5%).
        snap.bucket_allocation = vec![
            BucketStatus {
                bucket: "EQUITY_CORE".to_string(),
                current_pct: 55.0,
                target_pct: 50.0,
                drift_pct: 5.0,
                is_drifted: true,
            },
            BucketStatus {
                bucket: "EQUITY_SATELLITE".to_string(),
                current_pct: 45.0,
                target_pct: 30.0,
                drift_pct: 15.0,
                is_drifted: true,
            },
        ];
        snap.holdings = vec![
            FlatHolding {
                isin: "ZZZ_ISIN".to_string(),
                fund_name: "ZZZ Fund".to_string(),
                asset_bucket: "EQUITY_CORE".to_string(),
                current_value: 100_000.0,
                invested_value: 50_000.0,
                xirr: 15.0,
                formatted_current_value: "₹ 1,00,000".to_string(),
                formatted_invested_value: "₹ 50,000".to_string(),
                current_nav: 100.0,
                expense_ratio: None,
                ter_status: None,
            },
            FlatHolding {
                isin: "AAA_ISIN".to_string(),
                fund_name: "AAA Fund".to_string(),
                asset_bucket: "EQUITY_SATELLITE".to_string(),
                current_value: 100_000.0,
                invested_value: 50_000.0,
                xirr: 15.0,
                formatted_current_value: "₹ 1,00,000".to_string(),
                formatted_invested_value: "₹ 50,000".to_string(),
                current_nav: 100.0,
                expense_ratio: None,
                ter_status: None,
            },
        ];
        // Both lots have gain = 10,000. Headroom = 15,000.
        // The scheme evaluated first will consume 10,000 room (leaving 5,000),
        // and get into Prime Implicant m15 (1111).
        // The scheme evaluated second will exceed remaining 5,000 room and get D=0 (m14: 1110).
        snap.tax_lots = vec![
            FlatTaxLot {
                isin: "ZZZ_ISIN".to_string(),
                buy_date: "2024-01-01".to_string(),
                units: 200.0,
                tax_classification: "LTCG".to_string(),
                is_long_term: true,
                cost_per_unit: 50.0, // cost 10k, nav 20k -> gain 10k
                grandfathered_nav: None,
                holding_days: 500,
                days_to_ltcg: 0,
                estimated_tax_drag: None,
                is_harvest_candidate: true,
            },
            FlatTaxLot {
                isin: "AAA_ISIN".to_string(),
                buy_date: "2024-01-01".to_string(),
                units: 200.0,
                tax_classification: "LTCG".to_string(),
                is_long_term: true,
                cost_per_unit: 50.0, // cost 10k, nav 20k -> gain 10k
                grandfathered_nav: None,
                holding_days: 500,
                days_to_ltcg: 0,
                estimated_tax_drag: None,
                is_harvest_candidate: true,
            },
        ];

        let matrix = classify_snapshot(&snap, 15_000.0);
        let prime_cell = matrix.cells.iter().find(|c| c.is_prime_implicant()).unwrap();
        assert_eq!(prime_cell.lots.len(), 1);
        // AAA_ISIN belongs to EQUITY_SATELLITE (+15% drift), so it must be selected first over ZZZ_ISIN (+5% drift)
        assert_eq!(prime_cell.lots[0].isin, "AAA_ISIN", "More severely drifted bucket (+15%) must be trimmed ahead of lower drift (+5%)");
    }

    #[test]
    fn test_dont_care_cells() {
        let mut matrix: [KMapCell; 16] = Default::default();
        for r in 0..4 {
            for c in 0..4 {
                let ab = gray_to_bits(r);
                let cd = gray_to_bits(c);
                let minterm = (ab << 2) | cd;
                let cell_idx = r * 4 + c;
                matrix[cell_idx] = KMapCell {
                    row_bits: ab,
                    col_bits: cd,
                    minterm,
                    lots: Vec::new(),
                    total_value: 0.0,
                    total_gain: 0.0,
                };
            }
        }

        for cell in &matrix {
            let b = (cell.minterm >> 2) & 1;
            let d = cell.minterm & 1;
            if b == 0 && d == 1 {
                assert!(cell.is_dont_care(), "Minterm {:04b} must be Don't Care", cell.minterm);
            } else {
                assert!(!cell.is_dont_care(), "Minterm {:04b} must NOT be Don't Care", cell.minterm);
            }
        }
    }
}
