// cockpit-gpui/src/kmap.rs

#![allow(dead_code)]

use crate::models::Snapshot;
use serde::{Deserialize, Serialize};

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
    pub fn is_prime_implicant(&self) -> bool {
        self.minterm == 0b1111 // Band Breached & LTCG & Exit Free & Under 112A Ceiling
    }

    pub fn is_march_harvest(&self) -> bool {
        self.minterm == 0b0111 // In Equilibrium & LTCG & Exit Free & 112A Free
    }
}

pub struct KMapMatrix {
    pub cells: [KMapCell; 16],
    pub total_harvest_room_remaining: f64,
    pub prime_implicant_liquidable_value: f64,
}

// Convert 2-bit Gray code index (0, 1, 2, 3) to binary bits (00, 01, 11, 10)
pub fn gray_to_bits(gray_idx: usize) -> u8 {
    match gray_idx {
        0 => 0b00,
        1 => 0b01,
        2 => 0b11,
        3 => 0b10,
        _ => 0b00,
    }
}

pub fn classify_snapshot(snapshot: &Snapshot, realized_ltcg_ytd: f64) -> KMapMatrix {
    let mut cells: [KMapCell; 16] = Default::default();

    // Initialize 4x4 matrix in Gray-code order
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

    let mut cumulative_ltcg = realized_ltcg_ytd;

    for lot in &snapshot.tax_lots {
        let holding = snapshot.holdings.iter().find(|h| h.isin == lot.isin);
        let fund_name = holding.map(|h| h.fund_name.as_str()).unwrap_or("Unknown Fund");
        let asset_bucket = holding.map(|h| h.asset_bucket.as_str()).unwrap_or("");
        
        // Predicate A: Band Breached (Overweight beyond 5/25 relative band)
        let is_overweight = snapshot.bucket_allocation.iter().any(|b| {
            b.bucket == asset_bucket && b.is_drifted && b.drift_pct > 0.0
        });
        let a = if is_overweight { 1u8 } else { 0u8 };

        // Predicate B: Tax Regime LTCG (Equity > 365 days)
        let b = if lot.is_long_term { 1u8 } else { 0u8 };

        // Predicate C: Exit Load Free (typically >= 365 days for equity MFs)
        let c = if lot.holding_days >= 365 { 1u8 } else { 0u8 };

        // Financial Calculation: Accurate NAV and Cost Basis (Unrealized Capital Gain)
        let nav = holding.map(|h| h.current_nav).unwrap_or(0.0);
        let cost_basis = lot.grandfathered_nav.unwrap_or(lot.cost_per_unit);
        let val = lot.units * nav;
        let lot_gain = val - (lot.units * cost_basis);

        // Predicate D: Only consume ₹1.25L Sec 112A exemption room if LTCG (b == 1) AND Exit-Load Free (c == 1)
        let d = if b == 1 && c == 1 && (cumulative_ltcg + lot_gain <= SEC_112A_EXEMPTION_LIMIT) {
            if lot_gain > 0.0 {
                cumulative_ltcg += lot_gain;
            }
            1u8
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

    let prime_val = cells.iter()
        .filter(|c| c.is_prime_implicant())
        .map(|c| c.total_value)
        .sum();

    KMapMatrix {
        cells,
        total_harvest_room_remaining: (SEC_112A_EXEMPTION_LIMIT - cumulative_ltcg).max(0.0),
        prime_implicant_liquidable_value: prime_val,
    }
}

