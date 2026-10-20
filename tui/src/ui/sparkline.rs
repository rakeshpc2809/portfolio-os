use ratatui::{
    style::{Color, Style},
    text::{Line, Span},
    widgets::{Block, Paragraph},
    Frame,
};

use crate::api::NetWorthPoint;
use crate::ui::{format_inr_compact, MUTED};

/// Renders a Braille-encoded sparkline into the given area.
/// Data source: `snapshot.net_worth_history` — no synthetic fallback.
/// If the history slice is empty, renders a "No history" placeholder.
pub fn render_braille_sparkline(
    f: &mut Frame,
    area: ratatui::layout::Rect,
    history: &[NetWorthPoint],
    block: Block<'_>,
    quiet_mode: bool,
) {
    let inner = block.inner(area);
    f.render_widget(block, area);

    if history.is_empty() {
        let msg = Paragraph::new(Span::styled(
            "  No net-worth history available",
            Style::default().fg(MUTED),
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
    let dot_bits: [[u32; 4]; 2] = [
        [0x01, 0x02, 0x04, 0x40], // left column of braille cell
        [0x08, 0x10, 0x20, 0x80], // right column of braille cell
    ];

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
                let normalized = (val - min_val) / range;
                let total_dots = cell_rows * 4;
                let fill_up_to_dot = (normalized * total_dots as f64) as usize;
                let dot_base = (cell_rows - 1 - row_idx) * 4;

                for dot_row in 0..4usize {
                    let dot_abs = dot_base + dot_row;
                    if dot_abs < fill_up_to_dot {
                        bits |= dot_bits[side][dot_row];
                    }
                }
            }

            let ch = char::from_u32(bits).unwrap_or('⠀');
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
        let label_text = if quiet_mode {
            "  min:₹ •••••• max:₹ ••••••".to_string()
        } else {
            let bottom_label = format_inr_compact(min_val);
            let top_label = format_inr_compact(max_val);
            format!("  min:{} max:{}", bottom_label, top_label)
        };
        lines.last_mut().unwrap().spans.push(
            Span::styled(label_text, Style::default().fg(MUTED))
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
