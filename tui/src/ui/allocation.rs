use ratatui::{
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Gauge, Paragraph},
    Frame,
};

use crate::app::AppState;

/// Renders 5 bucket status badges driven by `snapshot.bucket_allocation[].is_drifted`.
/// No threshold computation here — is_drifted comes from the API.
#[allow(dead_code)]
pub fn render_allocation(f: &mut Frame, area: ratatui::layout::Rect, state: &AppState) {
    let buckets = state.snapshot.as_ref()
        .map(|s| s.bucket_allocation.as_slice())
        .unwrap_or(&[]);

    let block = Block::default()
        .borders(Borders::ALL)
        .title(Span::styled(" Bucket Allocation ", Style::default().fg(Color::Cyan)));
    f.render_widget(block.clone(), area);

    let inner = block.inner(area);
    if buckets.is_empty() {
        let msg = Paragraph::new("  Awaiting allocation data…")
            .style(Style::default().fg(Color::DarkGray));
        f.render_widget(msg, inner);
        return;
    }

    let rows = Layout::default()
        .direction(Direction::Vertical)
        .constraints(vec![Constraint::Length(2); buckets.len()])
        .split(inner);

    for (i, bucket) in buckets.iter().enumerate() {
        if i >= rows.len() { break; }

        let (badge_color, badge_icon) = if bucket.is_drifted {
            (Color::Yellow, "⚠")
        } else {
            (Color::Green, "✓")
        };

        let drift_sign = if bucket.drift_pct >= 0.0 { "+" } else { "" };
        let label_line = Line::from(vec![
            Span::styled(format!(" {} ", badge_icon), Style::default().fg(badge_color)),
            Span::styled(
                format!("{:<18}", bucket_label(&bucket.bucket)),
                Style::default().fg(Color::White).add_modifier(Modifier::BOLD),
            ),
            Span::styled(
                format!("{:5.1}%  target {:.1}%  drift {}{:.1}%",
                    bucket.current_pct, bucket.target_pct, drift_sign, bucket.drift_pct),
                Style::default().fg(if bucket.is_drifted { Color::Yellow } else { Color::DarkGray }),
            ),
        ]);
        f.render_widget(Paragraph::new(label_line), rows[i]);
    }
}

/// Gauge variant — percentage fill bar per bucket.
pub fn render_allocation_gauge(f: &mut Frame, area: ratatui::layout::Rect, state: &AppState) {
    let buckets = state.snapshot.as_ref()
        .map(|s| s.bucket_allocation.as_slice())
        .unwrap_or(&[]);

    let block = Block::default()
        .borders(Borders::ALL)
        .title(Span::styled(" Bucket Allocation ", Style::default().fg(Color::Cyan)));
    f.render_widget(block.clone(), area);
    let inner = block.inner(area);

    let row_h = 2u16;
    let max_rows = (inner.height / row_h) as usize;

    for (i, bucket) in buckets.iter().take(max_rows).enumerate() {
        let row_area = ratatui::layout::Rect {
            x: inner.x,
            y: inner.y + (i as u16) * row_h,
            width: inner.width,
            height: row_h,
        };

        let cols = Layout::default()
            .direction(Direction::Horizontal)
            .constraints([Constraint::Length(20), Constraint::Min(10)])
            .split(row_area);

        let (badge_color, badge_icon) = if bucket.is_drifted {
            (Color::Yellow, "⚠")
        } else {
            (Color::Green, "✓")
        };

        let label = Paragraph::new(Line::from(vec![
            Span::styled(format!(" {} ", badge_icon), Style::default().fg(badge_color)),
            Span::styled(
                format!("{:<16}", bucket_label(&bucket.bucket)),
                Style::default().fg(Color::White),
            ),
        ]));
        f.render_widget(label, cols[0]);

        let pct = (bucket.current_pct / 100.0).clamp(0.0, 1.0);
        let gauge = Gauge::default()
            .gauge_style(Style::default().fg(if bucket.is_drifted { Color::Yellow } else { Color::Blue }))
            .ratio(pct)
            .label(format!("{:.1}% / {:.1}%", bucket.current_pct, bucket.target_pct));
        f.render_widget(gauge, cols[1]);
    }
}

fn bucket_label(raw: &str) -> &str {
    match raw {
        "EQUITY_CORE"   => "Equity Core",
        "EQUITY_SAT"    => "Equity Satellite",
        "GOLD_SILVER"   => "Gold / Silver",
        "LIQUID_BUFFER" => "Liquid Buffer",
        "DEBT"          => "Debt",
        "CORE"          => "Core",
        "SATELLITE"     => "Satellite",
        other           => other,
    }
}
