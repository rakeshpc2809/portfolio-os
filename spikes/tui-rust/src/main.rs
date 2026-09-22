use std::env;
use std::io::stdout;
use std::sync::mpsc;
use std::thread;
use std::time::{Duration, Instant};

use crossterm::{
    event::{self, Event, KeyCode},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use ratatui::{
    backend::CrosstermBackend,
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Paragraph},
    Terminal,
};
use serde::Deserialize;

#[derive(Deserialize, Debug, Default, Clone)]
struct SyncInfo {
    #[serde(default)]
    current_value: f64,
    #[serde(default)]
    total_invested: f64,
    #[serde(default)]
    formatted_unrealized_gain: String,
    #[serde(default)]
    xirr_percentage: String,
}

#[derive(Deserialize, Debug, Default, Clone)]
struct Bucket {
    #[serde(default)]
    bucket: String,
    #[serde(default)]
    current_allocation_pct: f64,
    #[serde(default)]
    target_allocation_pct: f64,
}

#[derive(Deserialize, Debug, Default, Clone)]
struct BuySide {
    #[serde(default)]
    buckets: Vec<Bucket>,
}

#[derive(Deserialize, Debug, Default, Clone)]
struct RebalancePlan {
    #[serde(default)]
    buy_side: BuySide,
}

#[derive(Deserialize, Debug, Default, Clone)]
struct SnapshotResponse {
    #[serde(default)]
    sync_info: SyncInfo,
    #[serde(default)]
    rebalance_plan: RebalancePlan,
}

enum AppMsg {
    SnapshotLoaded(SnapshotResponse, Duration),
    FetchError(String),
}

fn spawn_fetch(tx: mpsc::Sender<AppMsg>) {
    thread::spawn(move || {
        let t0 = Instant::now();
        let token = env::var("API_AUTH_TOKEN").unwrap_or_else(|_| "dev_secret_key_123".to_string());
        let url = "http://127.0.0.1:8080/api/v1/sync/snapshot?fy=2026-27";

        match ureq::get(url).header("X-Api-Auth-Token", &token).call() {
            Ok(resp) => {
                let mut reader = resp.into_body().into_reader();
                match serde_json::from_reader::<_, SnapshotResponse>(reader) {
                    Ok(snap) => {
                        let _ = tx.send(AppMsg::SnapshotLoaded(snap, t0.elapsed()));
                    }
                    Err(e) => {
                        let _ = tx.send(AppMsg::FetchError(format!("JSON error: {e}")));
                    }
                }
            }
            Err(e) => {
                let _ = tx.send(AppMsg::FetchError(format!("HTTP error: {e}")));
            }
        }
    });
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let (tx, rx) = mpsc::channel();

    // Spawn initial fetch asynchronously
    spawn_fetch(tx.clone());

    enable_raw_mode()?;
    let mut stdout = stdout();
    execute!(stdout, EnterAlternateScreen)?;
    let backend = CrosstermBackend::new(stdout);
    let mut terminal = Terminal::new(backend)?;

    let mut current_snapshot: Option<SnapshotResponse> = None;
    let mut current_latency = Duration::from_millis(0);
    let mut status_msg = "Connecting to core-node:8080...".to_string();
    let mut last_fetch = Instant::now();

    loop {
        // Drain incoming messages
        while let Ok(msg) = rx.try_recv() {
            match msg {
                AppMsg::SnapshotLoaded(snap, lat) => {
                    current_latency = lat;
                    status_msg = format!("● core:8080 ({}ms)", lat.as_millis());
                    current_snapshot = Some(snap);
                }
                AppMsg::FetchError(err) => {
                    status_msg = format!("● core:offline ({err})");
                }
            }
        }

        terminal.draw(|f| {
            let chunks = Layout::default()
                .direction(Direction::Vertical)
                .margin(1)
                .constraints([
                    Constraint::Length(4),
                    Constraint::Min(5),
                    Constraint::Length(1),
                ])
                .split(f.area());

            let (header_lines, alloc_lines) = match &current_snapshot {
                Some(snap) => {
                    let si = &snap.sync_info;
                    let h = vec![
                        Line::from(vec![
                            Span::styled("PORTFOLIO OS ", Style::default().fg(Color::Rgb(236, 192, 147)).add_modifier(Modifier::BOLD)),
                            Span::styled("· REAL-TIME CORPUS TELEMETRY", Style::default().fg(Color::Rgb(110, 115, 141))),
                        ]),
                        Line::from(vec![
                            Span::styled(format!("₹{:.2}   ", si.current_value), Style::default().fg(Color::White).add_modifier(Modifier::BOLD)),
                            Span::styled(format!("▲ {}   ", si.formatted_unrealized_gain), Style::default().fg(Color::Rgb(166, 227, 161))),
                            Span::styled(format!("XIRR {}", si.xirr_percentage), Style::default().fg(Color::Rgb(203, 166, 247))),
                        ]),
                    ];
                    let mut a = vec![
                        Line::from(Span::styled("ASSET ALLOCATION & TARGET DRIFT", Style::default().fg(Color::Rgb(236, 192, 147)).add_modifier(Modifier::BOLD))),
                    ];
                    for b in &snap.rebalance_plan.buy_side.buckets {
                        a.push(Line::from(format!(
                            "  {:<15} {:>5.1}% (target {:>4.1}%)",
                            b.bucket, b.current_allocation_pct, b.target_allocation_pct
                        )));
                    }
                    (h, a)
                }
                None => {
                    (
                        vec![
                            Line::from(Span::styled("PORTFOLIO OS", Style::default().fg(Color::Rgb(236, 192, 147)).add_modifier(Modifier::BOLD))),
                            Line::from(Span::styled("Initializing...", Style::default().fg(Color::Rgb(110, 115, 141)))),
                        ],
                        vec![Line::from(Span::styled("Loading allocations...", Style::default().fg(Color::Rgb(110, 115, 141))))],
                    )
                }
            };

            let header = Paragraph::new(header_lines)
                .block(Block::default().borders(Borders::ALL).border_style(Style::default().fg(Color::Rgb(39, 39, 58))));
            f.render_widget(header, chunks[0]);

            let alloc = Paragraph::new(alloc_lines)
                .block(Block::default().borders(Borders::ALL).border_style(Style::default().fg(Color::Rgb(39, 39, 58))));
            f.render_widget(alloc, chunks[1]);

            let footer_text = Line::from(vec![
                Span::styled(format!("{status_msg}  "), Style::default().fg(Color::Rgb(166, 227, 161))),
                Span::styled("[r] refresh  [q] quit", Style::default().fg(Color::Rgb(110, 115, 141))),
            ]);
            f.render_widget(Paragraph::new(footer_text), chunks[2]);
        })?;

        if event::poll(Duration::from_millis(50))? {
            if let Event::Key(key) = event::read()? {
                if key.code == KeyCode::Char('q') || key.code == KeyCode::Esc {
                    break;
                } else if key.code == KeyCode::Char('r') {
                    spawn_fetch(tx.clone());
                }
            }
        }

        if last_fetch.elapsed() >= Duration::from_secs(5) {
            spawn_fetch(tx.clone());
            last_fetch = Instant::now();
        }
    }

    disable_raw_mode()?;
    execute!(terminal.backend_mut(), LeaveAlternateScreen)?;
    terminal.show_cursor()?;
    Ok(())
}
