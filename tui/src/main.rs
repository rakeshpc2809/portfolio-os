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
                AppMsg::SnapshotUpdated(snap) => state.snapshot = Some(*snap),
                AppMsg::OverlapUpdated(ov)    => state.overlap_summary = Some(*ov),
                AppMsg::HrpUpdated(hrp)       => state.hrp_audit = Some(*hrp),
                AppMsg::FetchError(e)         => state.last_error = Some(e),
                AppMsg::Tick                  => {}
            }
        }

        if state.quit { break; }

        // --- Draw ---
        terminal.draw(|f| draw(f, &state))?;

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

                if state.modal.is_none() {
                    match key.code {
                        KeyCode::Char('t') => state.modal = Some(ModalView::TaxLots),
                        KeyCode::Char('p') => state.modal = Some(ModalView::RebalancePlan),
                        KeyCode::Char('d') => state.modal = Some(ModalView::DailySummary),
                        KeyCode::Char('r') => {
                            // Manual refresh: send a fresh fetch on a background thread
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

/// Pure render function — reads immutable AppState, produces one frame.
fn draw(f: &mut ratatui::Frame, state: &AppState) {
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
            .style(ratatui::style::Style::default().fg(ratatui::style::Color::DarkGray)),
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

    format!(
        "{} [d] brief  [t] lots  [p] plan  [r] refresh  [q] quit{}",
        fy, error_part
    )
}
