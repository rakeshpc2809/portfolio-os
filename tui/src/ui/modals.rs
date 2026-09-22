use ratatui::{
    layout::{Constraint, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Cell, Clear, Paragraph, Row, Table},
    Frame,
};

use crate::app::{AppState, ModalView};

/// Renders the active modal if one is open, clearing behind it.
pub fn render_modal(f: &mut Frame, state: &AppState) {
    let active = match &state.modal {
        Some(m) => m,
        None => return,
    };

    let area = modal_rect(f.area(), 80, 80);
    f.render_widget(Clear, area);

    match active {
        ModalView::TaxLots => render_tax_lots_modal(f, area, state),
        ModalView::RebalancePlan => render_rebalance_modal(f, area, state),
        ModalView::DailySummary => render_daily_summary_modal(f, area, state),
    }
}

// ---------------------------------------------------------------------------
// [t] — Tax lots modal
// ---------------------------------------------------------------------------
fn render_tax_lots_modal(f: &mut Frame, area: Rect, state: &AppState) {
    let lots = state.snapshot.as_ref()
        .map(|s| s.tax_lots.as_slice())
        .unwrap_or(&[]);

    let block = Block::default()
        .borders(Borders::ALL)
        .title(Span::styled(
            " [t] Tax Lots  •  ESC / q to close ",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        ));

    let header = Row::new(vec![
        Cell::from("Date").style(Style::default().fg(Color::DarkGray).add_modifier(Modifier::BOLD)),
        Cell::from("ISIN").style(Style::default().fg(Color::DarkGray).add_modifier(Modifier::BOLD)),
        Cell::from("Units").style(Style::default().fg(Color::DarkGray).add_modifier(Modifier::BOLD)),
        Cell::from("Days").style(Style::default().fg(Color::DarkGray).add_modifier(Modifier::BOLD)),
        Cell::from("→LTCG").style(Style::default().fg(Color::DarkGray).add_modifier(Modifier::BOLD)),
        Cell::from("Type").style(Style::default().fg(Color::DarkGray).add_modifier(Modifier::BOLD)),
        Cell::from("Harvest").style(Style::default().fg(Color::DarkGray).add_modifier(Modifier::BOLD)),
    ]);

    let rows: Vec<Row> = lots.iter().map(|lot| {
        let type_color = if lot.is_long_term { Color::Green } else { Color::Yellow };
        let harvest_icon = if lot.is_harvest_candidate { "🌾" } else { "" };
        Row::new(vec![
            Cell::from(lot.buy_date.clone()),
            Cell::from(lot.isin.clone()),
            Cell::from(format!("{:.2}", lot.units)),
            Cell::from(lot.holding_days.to_string()),
            Cell::from(lot.days_to_ltcg.to_string()),
            Cell::from(Span::styled(lot.tax_classification.clone(), Style::default().fg(type_color))),
            Cell::from(harvest_icon),
        ])
    }).collect();

    let table = Table::new(
        rows,
        [
            Constraint::Length(12), // date
            Constraint::Length(14), // isin
            Constraint::Length(10), // units
            Constraint::Length(6),  // days
            Constraint::Length(6),  // dtlcg
            Constraint::Length(12), // type
            Constraint::Length(8),  // harvest
        ],
    )
    .header(header)
    .block(block)
    .row_highlight_style(Style::default().add_modifier(Modifier::REVERSED));

    f.render_widget(table, area);
}

// ---------------------------------------------------------------------------
// [p] — Rebalance waterfall modal
// ---------------------------------------------------------------------------
fn render_rebalance_modal(f: &mut Frame, area: Rect, state: &AppState) {
    let block = Block::default()
        .borders(Borders::ALL)
        .title(Span::styled(
            " [p] Rebalance Plan  •  ESC / q to close ",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        ));

    let plan = state.snapshot.as_ref()
        .and_then(|s| s.rebalance_plan.as_ref());

    let mut lines: Vec<Line> = Vec::new();

    if let Some(p) = plan {
        // Trigger
        if let Some(trig) = &p.trigger {
            lines.push(Line::from(vec![
                Span::styled("  Trigger: ", Style::default().fg(Color::DarkGray)),
                Span::styled(trig.reason_label.clone(), Style::default().fg(Color::White).add_modifier(Modifier::BOLD)),
                Span::styled(format!("  ({})", trig.trigger_type), Style::default().fg(Color::DarkGray)),
            ]));
            lines.push(Line::from(""));
        }

        // Sell side
        if let Some(ss) = &p.sell_side {
            if let Some(total) = ss.total_required {
                lines.push(Line::from(vec![
                    Span::styled("  Sell Required: ", Style::default().fg(Color::DarkGray)),
                    Span::styled(format!("₹{:.0}", total), Style::default().fg(Color::Red)),
                ]));
            }
            if let Some(ts) = &ss.tax_summary {
                let pct = ts.exemption_utilized_pct.unwrap_or(0.0);
                let cap = ts.statutory_exemption_cap.unwrap_or(125_000.0);
                let used = ts.total_ltcg_exempt.unwrap_or(0.0);
                lines.push(Line::from(vec![
                    Span::styled("  Sec 112A:  ", Style::default().fg(Color::DarkGray)),
                    Span::styled(format!("₹{:.0} exempt / ₹{:.0} cap  ({:.1}% used)",
                        used, cap, pct), Style::default().fg(Color::Green)),
                ]));
            }
            lines.push(Line::from(""));
        }

        // Buy side breakdown
        if let Some(bs) = &p.buy_side {
            lines.push(Line::from(Span::styled(
                format!("  ─── Buy Side: ₹{:.0} ───", bs.total_to_invest.unwrap_or(0.0)),
                Style::default().fg(Color::Blue).add_modifier(Modifier::BOLD),
            )));
            for bucket in &bs.buckets {
                lines.push(Line::from(vec![
                    Span::styled(format!("  {:18}", bucket.bucket), Style::default().fg(Color::White)),
                    Span::styled(
                        format!("{:.1}% → {:.1}%   ₹{:.0}",
                            bucket.current_pct, bucket.post_rebalance_pct,
                            bucket.amount_allocated.unwrap_or(0.0)),
                        Style::default().fg(Color::Cyan),
                    ),
                ]));
                for fund in &bucket.fund_breakdown {
                    lines.push(Line::from(vec![
                        Span::styled(format!("      {:<30}", shorten_fund(&fund.fund_name, 30)),
                            Style::default().fg(Color::DarkGray)),
                        Span::styled(format!("₹{:.0}", fund.amount.unwrap_or(0.0)),
                            Style::default().fg(Color::White)),
                    ]));
                }
            }
        }

        // Narrative headline
        if let Some(narr) = &p.reasoning_narrative {
            lines.push(Line::from(""));
            lines.push(Line::from(Span::styled(
                format!("  {}", narr.headline),
                Style::default().fg(Color::Yellow).add_modifier(Modifier::ITALIC),
            )));
        }
    } else {
        lines.push(Line::from(Span::styled(
            "  No rebalance plan in current snapshot.",
            Style::default().fg(Color::DarkGray),
        )));
    }

    let para = Paragraph::new(lines).block(block);
    f.render_widget(para, area);
}

// ---------------------------------------------------------------------------
// [d] — Daily summary modal (HRP audit + holdings)
// ---------------------------------------------------------------------------
fn render_daily_summary_modal(f: &mut Frame, area: Rect, state: &AppState) {
    let block = Block::default()
        .borders(Borders::ALL)
        .title(Span::styled(
            " [d] Daily Executive Brief  •  ESC / q to close ",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        ));

    let mut lines: Vec<Line> = Vec::new();

    // HRP audit section
    if let Some(hrp) = &state.hrp_audit {
        let status_color = if hrp.status == "SUCCESS" { Color::Green } else { Color::Yellow };
        lines.push(Line::from(vec![
            Span::styled("  HRP CVaR Audit: ", Style::default().fg(Color::DarkGray)),
            Span::styled(&hrp.status, Style::default().fg(status_color).add_modifier(Modifier::BOLD)),
        ]));
        if let (Some(days), Some(start), Some(end)) = (&hrp.trading_days, &hrp.start_date, &hrp.end_date) {
            lines.push(Line::from(Span::styled(
                format!("  {} trading days  ({} → {})", days, start, end),
                Style::default().fg(Color::DarkGray),
            )));
        }
        lines.push(Line::from(""));

        if let Some(allocs) = &hrp.allocations {
            lines.push(Line::from(Span::styled(
                "  Fund            Bucket        Target   HRP      Drift",
                Style::default().fg(Color::DarkGray).add_modifier(Modifier::BOLD),
            )));
            for a in allocs {
                let drift_color = if a.drift_pct.abs() > 5.0 { Color::Yellow } else { Color::DarkGray };
                let sign = if a.drift_pct >= 0.0 { "+" } else { "" };
                lines.push(Line::from(vec![
                    Span::styled(format!("  {:<18}", shorten_fund(&a.name, 18)),
                        Style::default().fg(Color::White)),
                    Span::styled(format!("{:<14}", a.bucket),
                        Style::default().fg(Color::DarkGray)),
                    Span::styled(format!("{:6.1}%  {:6.1}%  ", a.target_pct, a.hrp_pct),
                        Style::default().fg(Color::Cyan)),
                    Span::styled(format!("{}{:.1}%", sign, a.drift_pct),
                        Style::default().fg(drift_color)),
                ]));
            }
            lines.push(Line::from(""));
        }
    } else {
        lines.push(Line::from(Span::styled(
            "  HRP audit not yet generated by quant-sidecar.",
            Style::default().fg(Color::DarkGray),
        )));
        lines.push(Line::from(""));
    }

    // Top holdings
    if let Some(snap) = &state.snapshot {
        lines.push(Line::from(Span::styled(
            "  Top Holdings",
            Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD),
        )));
        let mut sorted = snap.holdings.clone();
        sorted.sort_by(|a, b| b.current_value.partial_cmp(&a.current_value).unwrap_or(std::cmp::Ordering::Equal));
        for h in sorted.iter().take(8) {
            let xirr_color = if h.xirr >= 0.0 { Color::Green } else { Color::Red };
            lines.push(Line::from(vec![
                Span::styled(format!("  {:<28}", shorten_fund(&h.fund_name, 28)),
                    Style::default().fg(Color::White)),
                Span::styled(format!("{:>14}", h.formatted_current_value),
                    Style::default().fg(Color::Cyan)),
                Span::styled(format!("  XIRR {:+.1}%", h.xirr * 100.0),
                    Style::default().fg(xirr_color)),
            ]));
        }
    }

    let para = Paragraph::new(lines).block(block);
    f.render_widget(para, area);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

fn shorten_fund(s: &str, max: usize) -> String {
    if s.chars().count() <= max { return s.to_string(); }
    format!("{}…", &s[..s.char_indices().nth(max - 1).map(|(i, _)| i).unwrap_or(s.len())])
}

/// Returns a centred modal rect occupying `pct_w`% width and `pct_h`% height.
fn modal_rect(r: Rect, pct_w: u16, pct_h: u16) -> Rect {
    let w = r.width * pct_w / 100;
    let h = r.height * pct_h / 100;
    let x = r.x + (r.width.saturating_sub(w)) / 2;
    let y = r.y + (r.height.saturating_sub(h)) / 2;
    Rect::new(x, y, w, h)
}
