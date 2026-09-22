use ratatui::{
    style::{Color, Style},
    text::{Line, Span},
    widgets::{Block, Paragraph},
    Frame,
};

use crate::api::NetWorthPoint;

/// Renders a Braille-encoded sparkline into the given area.
/// Data source: `snapshot.net_worth_history` — no synthetic fallback.
/// If the history slice is empty, renders a "No history" placeholder.
pub fn render_braille_sparkline(
    f: &mut Frame,
    area: ratatui::layout::Rect,
    history: &[NetWorthPoint],
    block: Block<'_>,
) {
    let inner = block.inner(area);
    f.render_widget(block, area);

    if history.is_empty() {
        let msg = Paragraph::new(Span::styled(
            "  No net-worth history available",
            Style::default().fg(Color::DarkGray),
        ));
        f.render_widget(msg, inner);
        return;
    }

    // Downsample to available columns × 2 (Braille columns have 2 data points each)
    let cols = inner.width as usize;
    let rows = inner.height as usize;
    if cols == 0 || rows == 0 {
        return;
    }

    let data: Vec<f64> = history.iter().map(|p| p.valuation).collect();
    let sampled = downsample(&data, cols * 2);

    let min_val = sampled.iter().cloned().fold(f64::INFINITY, f64::min);
    let max_val = sampled.iter().cloned().fold(f64::NEG_INFINITY, f64::max);
    let range = (max_val - min_val).max(1.0);

    // Braille dot offsets for a 2×4 cell (unicode 0x2800 base)
    // Each braille cell encodes 2 columns × 4 rows of dots
    // Dots:  col0: rows 0-3 = bits 0,1,2,6; col1: rows 0-3 = bits 3,4,5,7
    let dot_bits: [[u32; 4]; 2] = [
        [0x01, 0x02, 0x04, 0x40], // left column of braille cell
        [0x08, 0x10, 0x20, 0x80], // right column of braille cell
    ];

    let _braille_rows = (rows * 4).min(sampled.len() * 4 / sampled.len()); // rows * 4 braille dots tall
    let cell_rows = rows;

    let mut lines: Vec<Line> = Vec::with_capacity(cell_rows);

    for row_idx in 0..cell_rows {
        let mut spans = Vec::new();
        for col_pair in 0..cols {
            let mut bits: u32 = 0x2800; // Braille empty base

            for side in 0..2usize {
                let data_idx = col_pair * 2 + side;
                if data_idx >= sampled.len() { continue; }

                let val = sampled[data_idx];
                // Normalize 0.0 (min) → 1.0 (max) → map to which braille dot row to fill
                let normalized = (val - min_val) / range;
                // We fill from bottom up; each row_idx covers 4 braille dot rows
                // Total braille dot rows = cell_rows * 4
                // Bottom row_idx = cell_rows - 1
                // A sample fills all dot rows at and below its normalized position
                let total_dots = cell_rows * 4;
                let fill_up_to_dot = (normalized * total_dots as f64) as usize;

                // This cell covers dots from (cell_rows - 1 - row_idx)*4 to (cell_rows - row_idx)*4
                let dot_base = (cell_rows - 1 - row_idx) * 4;

                for dot_row in 0..4usize {
                    let dot_abs = dot_base + dot_row;
                    // Fill dot if it's within the bar height
                    if dot_abs < fill_up_to_dot {
                        bits |= dot_bits[side][dot_row];
                    }
                }
            }

            let ch = char::from_u32(bits).unwrap_or('⠀');
            // Color the sparkline: green if above midpoint, yellow if below
            let mid = min_val + range / 2.0;
            let data_idx = col_pair * 2;
            let val = if data_idx < sampled.len() { sampled[data_idx] } else { min_val };
            let color = if val >= mid { Color::Green } else { Color::Yellow };
            spans.push(Span::styled(ch.to_string(), Style::default().fg(color)));
        }
        lines.push(Line::from(spans));
    }

    // Overlay min/max labels at corners
    if !lines.is_empty() {
        let bottom_label = format!(" {:.0}k ", min_val / 1000.0);
        let top_label = format!(" {:.0}k ", max_val / 1000.0);
        // Just append to last line as a dim label
        lines.last_mut().unwrap().spans.push(
            Span::styled(format!("  min:{} max:{}", bottom_label.trim(), top_label.trim()),
                Style::default().fg(Color::DarkGray))
        );
    }

    f.render_widget(Paragraph::new(lines), inner);
}

/// Nearest-neighbour downsample (or pad) `data` to exactly `target_len` points.
fn downsample(data: &[f64], target_len: usize) -> Vec<f64> {
    if data.is_empty() || target_len == 0 {
        return vec![0.0; target_len];
    }
    if data.len() == target_len {
        return data.to_vec();
    }
    (0..target_len)
        .map(|i| {
            let src_idx = i * data.len() / target_len;
            data[src_idx]
        })
        .collect()
}
