use ratatui::{
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Gauge, Paragraph},
    Frame,
};

use crate::app::AppState;

/// Tax Strategy deck:
///   - Sec 112A exemption progress bar (reads `exemption_utilized_pct` from API)
///   - Overlap warning badge (reads `overlap_summary.threshold_breached` from API)
/// Zero client-side arithmetic — all values pre-computed by core-node.
pub fn render_tax_strategy(f: &mut Frame, area: ratatui::layout::Rect, state: &AppState) {
    let block = Block::default()
        .borders(Borders::ALL)
        .title(Span::styled(" Tax Strategy ", Style::default().fg(Color::Cyan)));
    f.render_widget(block.clone(), area);
    let inner = block.inner(area);

    let rows = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(3), // 112A gauge
            Constraint::Length(1), // spacer
            Constraint::Length(2), // overlap badge
            Constraint::Min(0),
        ])
        .split(inner);

    // --- Sec 112A Exemption Utilization ---
    let (utilized_pct, headroom_label) = state.snapshot.as_ref()
        .and_then(|s| s.rebalance_plan.as_ref())
        .and_then(|p| p.sell_side.as_ref())
        .and_then(|ss| ss.tax_summary.as_ref())
        .map(|ts| {
            let pct = ts.exemption_utilized_pct.unwrap_or(0.0);
            let cap = ts.statutory_exemption_cap.unwrap_or(125_000.0);
            let used = ts.total_ltcg_exempt.unwrap_or(0.0);
            let label = format!("₹{:.0} / ₹{:.0} ({:.1}%)", used, cap, pct);
            (pct / 100.0, label)
        })
        .unwrap_or((0.0, "No rebalance plan loaded".to_string()));

    let gauge_color = if utilized_pct > 0.85 { Color::Red }
        else if utilized_pct > 0.5 { Color::Yellow }
        else { Color::Green };

    let gauge = Gauge::default()
        .block(Block::default().title(" Sec 112A Exemption "))
        .gauge_style(Style::default().fg(gauge_color))
        .ratio(utilized_pct.clamp(0.0, 1.0))
        .label(headroom_label);
    f.render_widget(gauge, rows[0]);

    // --- Overlap Warning ---
    let (overlap_icon, overlap_color, overlap_text) = match &state.overlap_summary {
        Some(ov) if ov.threshold_breached => (
            "⚠",
            Color::Red,
            format!(
                "HIGH OVERLAP: {} × {} ({:.1}%)",
                shorten(&ov.fund_a_name, 22),
                shorten(&ov.fund_b_name, 22),
                ov.max_overlap_percentage
            ),
        ),
        Some(ov) => (
            "✓",
            Color::Green,
            format!("Max overlap {:.1}% — within threshold", ov.max_overlap_percentage),
        ),
        None => ("·", Color::DarkGray, "Overlap data loading…".to_string()),
    };

    let overlap_line = Line::from(vec![
        Span::styled(format!(" {} ", overlap_icon), Style::default()
            .fg(overlap_color)
            .add_modifier(Modifier::BOLD)),
        Span::styled("Fund Overlap  ", Style::default().fg(Color::White)),
        Span::styled(overlap_text, Style::default().fg(overlap_color)),
    ]);
    f.render_widget(Paragraph::new(overlap_line), rows[2]);

    // --- Radar signals (remainder area) ---
    if let Some(snap) = &state.snapshot {
        let signals: Vec<Line> = snap.radar_signals.iter().map(|sig| {
            let sig_color = match sig.severity.as_str() {
                "CRITICAL" => Color::Red,
                "WARN"     => Color::Yellow,
                _          => Color::DarkGray,
            };
            Line::from(vec![
                Span::styled(format!(" [{}] ", &sig.severity[..4.min(sig.severity.len())]),
                    Style::default().fg(sig_color)),
                Span::styled(sig.title.clone(), Style::default().fg(Color::White)),
                Span::styled(format!("  {}", sig.subtitle), Style::default().fg(Color::DarkGray)),
            ])
        }).collect();

        if !signals.is_empty() && rows.len() > 3 {
            let signals_block = Block::default()
                .borders(Borders::TOP)
                .title(Span::styled(" Radar Signals ", Style::default().fg(Color::DarkGray)));
            let sig_para = Paragraph::new(signals).block(signals_block);
            f.render_widget(sig_para, rows[3]);
        }
    }
}

fn shorten(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        s.to_string()
    } else {
        format!("{}…", &s[..s.char_indices().nth(max - 1).map(|(i, _)| i).unwrap_or(s.len())])
    }
}
