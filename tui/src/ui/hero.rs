use ratatui::{
    layout::{Alignment, Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Paragraph},
    Frame,
};

use crate::app::AppState;
use crate::ui::sparkline::render_braille_sparkline;

/// Top hero panel: net worth, unrealised P&L, XIRR, Braille sparkline.
/// Pure display — all values read directly from `AppState.snapshot`.
pub fn render_hero(f: &mut Frame, area: ratatui::layout::Rect, state: &AppState) {
    let snap = match &state.snapshot {
        Some(s) => s,
        None => {
            let loading = Paragraph::new("⟳  Fetching portfolio data…")
                .block(Block::default().borders(Borders::ALL).title(" Portfolio OS "))
                .style(Style::default().fg(Color::DarkGray));
            f.render_widget(loading, area);
            return;
        }
    };

    let info = &snap.sync_info;

    // Layout: left column (headline numbers) | right column (sparkline)
    let cols = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(55), Constraint::Percentage(45)])
        .split(area);

    // --- LEFT: numbers ---
    let gain_color = if info.unrealized_gain >= 0.0 { Color::Green } else { Color::Red };
    let gain_prefix = if info.unrealized_gain >= 0.0 { "▲" } else { "▼" };
    let xirr_color = if info.portfolio_xirr >= 0.0 { Color::Green } else { Color::Red };

    let stale_badge = if info.has_stale_nav.unwrap_or(false) {
        let count = info.stale_nav_count.unwrap_or(0);
        format!("  ⚠ {} stale NAV", count)
    } else {
        String::new()
    };

    let lines = vec![
        Line::from(vec![
            Span::styled("  NET WORTH  ", Style::default().fg(Color::DarkGray)),
            Span::styled(&info.formatted_current_value, Style::default()
                .fg(Color::White)
                .add_modifier(Modifier::BOLD)),
            Span::styled(&stale_badge, Style::default().fg(Color::Yellow)),
        ]),
        Line::from(""),
        Line::from(vec![
            Span::styled("  INVESTED   ", Style::default().fg(Color::DarkGray)),
            Span::styled(&info.formatted_total_invested, Style::default().fg(Color::Cyan)),
        ]),
        Line::from(vec![
            Span::styled("  GAIN       ", Style::default().fg(Color::DarkGray)),
            Span::styled(gain_prefix, Style::default().fg(gain_color)),
            Span::styled(&info.formatted_unrealized_gain, Style::default().fg(gain_color)),
        ]),
        Line::from(vec![
            Span::styled("  XIRR       ", Style::default().fg(Color::DarkGray)),
            Span::styled(&info.xirr_percentage, Style::default()
                .fg(xirr_color)
                .add_modifier(Modifier::BOLD)),
        ]),
        Line::from(""),
        Line::from(vec![
            Span::styled(
                format!("  FY {}  •  {}", info.fiscal_year, &info.generated_at[..10]),
                Style::default().fg(Color::DarkGray),
            ),
        ]),
    ];

    let left_block = Paragraph::new(lines)
        .block(Block::default()
            .borders(Borders::ALL)
            .title(Span::styled(" ◈ Portfolio OS ", Style::default()
                .fg(Color::Cyan)
                .add_modifier(Modifier::BOLD))))
        .alignment(Alignment::Left);

    f.render_widget(left_block, cols[0]);

    // --- RIGHT: Braille net-worth sparkline ---
    let spark_title = format!(
        " Net Worth — {} pts ",
        snap.net_worth_history.len()
    );
    let spark_block = Block::default()
        .borders(Borders::ALL)
        .title(Span::styled(spark_title, Style::default().fg(Color::DarkGray)));

    render_braille_sparkline(f, cols[1], &snap.net_worth_history, spark_block);
}
