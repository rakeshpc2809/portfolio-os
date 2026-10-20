mod api;
mod app;
mod ui;
mod fetch;

use std::sync::mpsc;
use std::time::Duration;

use crossterm::{
    event::{self, Event, KeyCode, KeyModifiers},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use ratatui::{
    backend::CrosstermBackend,
    layout::{Constraint, Direction, Layout},
    Terminal,
};

use app::{AppMsg, AppState, ModalView};
use ui::MUTED;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    // --- Terminal setup ---
    enable_raw_mode()?;
    let mut stdout = std::io::stdout();
    execute!(stdout, EnterAlternateScreen)?;
    let backend = CrosstermBackend::new(stdout);
    let mut terminal = Terminal::new(backend)?;

    let result = run_app(&mut terminal);

    // --- Terminal teardown (always runs) ---
    disable_raw_mode()?;
    execute!(terminal.backend_mut(), LeaveAlternateScreen)?;
    terminal.show_cursor()?;

    if let Err(e) = result {
        eprintln!("Error: {e}");
    }
    Ok(())
}

fn run_app(terminal: &mut Terminal<CrosstermBackend<std::io::Stdout>>) -> Result<(), Box<dyn std::error::Error>> {
    let mut state = AppState::default();
    let (tx, rx) = mpsc::channel::<AppMsg>();

    // --- Spawn background polling thread ---
    let tx_poll = tx.clone();
    std::thread::spawn(move || {
        fetch::poll_loop(tx_poll);
    });

    // --- Tick thread: sends Tick every 100ms to unblock event poll ---
    let tx_tick = tx.clone();
    std::thread::spawn(move || {
        loop {
            std::thread::sleep(Duration::from_millis(100));
            if tx_tick.send(AppMsg::Tick).is_err() { break; }
        }
    });

    loop {
        // Process all pending messages from the polling thread
        while let Ok(msg) = rx.try_recv() {
            match msg {
                AppMsg::SnapshotUpdated(snap) => {
                    state.snapshot = Some(*snap);
                    state.is_offline_cache = false;
                    state.cache_date = None;
                }
                AppMsg::OfflineSnapshotLoaded(snap, date) => {
                    if state.snapshot.is_none() {
                        state.snapshot = Some(*snap);
                        state.is_offline_cache = true;
                        state.cache_date = Some(date);
                    }
                }
                AppMsg::OverlapUpdated(ov)    => state.overlap_summary = Some(*ov),
                AppMsg::HrpUpdated(hrp)       => state.hrp_audit = Some(*hrp),
                AppMsg::FetchError(e)         => state.last_error = Some(e),
                AppMsg::Tick                  => {}
            }
        }

        if state.quit { break; }

        // --- Draw ---
        terminal.draw(|f| draw(f, &mut state))?;

        // --- Input (non-blocking, 50ms timeout) ---
        if event::poll(Duration::from_millis(50))? {
            if let Event::Key(key) = event::read()? {
                // ESC or q closes modal first; if none open, quit
                if key.code == KeyCode::Esc
                    || (key.code == KeyCode::Char('q') && key.modifiers == KeyModifiers::NONE)
                {
                    if state.modal.is_some() {
                        state.modal = None;
                    } else {
                        state.quit = true;
                    }
                    continue;
                }

                // Global Quiet Mode toggle
                if key.code == KeyCode::Char('m') && key.modifiers == KeyModifiers::NONE {
                    state.quiet_mode = !state.quiet_mode;
                    continue;
                }

                if let Some(ModalView::TaxLots) = state.modal {
                    match key.code {
                        KeyCode::Down | KeyCode::Char('j') => {
                            let count = state.snapshot.as_ref().map(|s| s.tax_lots.len()).unwrap_or(0);
                            if count > 0 {
                                let i = match state.table_state.selected() {
                                    Some(curr) => if curr + 1 >= count { 0 } else { curr + 1 },
                                    None => 0,
                                };
                                state.table_state.select(Some(i));
                            }
                            continue;
                        }
                        KeyCode::Up | KeyCode::Char('k') => {
                            let count = state.snapshot.as_ref().map(|s| s.tax_lots.len()).unwrap_or(0);
                            if count > 0 {
                                let i = match state.table_state.selected() {
                                    Some(curr) => if curr == 0 { count - 1 } else { curr - 1 },
                                    None => 0,
                                };
                                state.table_state.select(Some(i));
                            }
                            continue;
                        }
                        _ => {}
                    }
                }

                if state.modal.is_none() {
                    match key.code {
                        KeyCode::Char('t') => {
                            state.modal = Some(ModalView::TaxLots);
                            state.table_state.select(Some(0));
                        }
                        KeyCode::Char('p') => state.modal = Some(ModalView::RebalancePlan),
                        KeyCode::Char('d') => state.modal = Some(ModalView::DailySummary),
                        KeyCode::Char('r') => {
                            let tx2 = tx.clone();
                            std::thread::spawn(move || fetch::fetch_all_once(&tx2));
                        }
                        _ => {}
                    }
                }
            }
        }
    }
    Ok(())
}

/// Pure render function — reads AppState and stateful widget handles, produces one frame.
fn draw(f: &mut ratatui::Frame, state: &mut AppState) {
    let area = f.area();

    // Main layout: hero (top) | middle row (allocation | tax) | status bar (bottom)
    let rows = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(9),  // hero header
            Constraint::Min(10),    // allocation + tax deck
            Constraint::Length(1),  // status bar
        ])
        .split(area);

    // Hero
    ui::hero::render_hero(f, rows[0], state);

    // Middle: allocation (left) | tax strategy (right)
    let mid_cols = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(45), Constraint::Percentage(55)])
        .split(rows[1]);

    ui::allocation::render_allocation_gauge(f, mid_cols[0], state);
    ui::tax::render_tax_strategy(f, mid_cols[1], state);

    // Status bar
    let status_text = build_status_bar(state);
    f.render_widget(
        ratatui::widgets::Paragraph::new(status_text)
            .style(ratatui::style::Style::default().fg(MUTED)),
        rows[2],
    );

    // Modals (rendered last, on top)
    ui::modals::render_modal(f, state);
}

fn build_status_bar(state: &AppState) -> String {
    let fy = state.snapshot.as_ref()
        .map(|s| format!(" FY {}  ", s.sync_info.fiscal_year))
        .unwrap_or_default();

    let error_part = state.last_error.as_ref()
        .map(|e| format!("  ⚠ {}", &e[..e.len().min(60)]))
        .unwrap_or_default();

    let quiet_indicator = if state.quiet_mode { " [m] quiet:ON " } else { " [m] quiet:OFF " };

    format!(
        "{}{}[d] brief  [t] lots  [p] plan  [r] refresh  [q] quit{}",
        fy, quiet_indicator, error_part
    )
}
