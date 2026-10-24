use std::sync::mpsc::Sender;
use std::time::Duration;

use crate::api::{OverlapResponse, Snapshot};
use crate::app::AppMsg;

const POLL_INTERVAL: Duration = Duration::from_secs(5);
const HTTP_TIMEOUT:  Duration = Duration::from_secs(8);

struct Config {
    base_url: String,
    api_token: String,
    fiscal_year: String,
    default_fund_a: String,
    default_fund_b: String,
}

impl Config {
    fn load() -> Self {
        let candidates = [
            "tui/tui_config.yaml",
            "../tui/tui_config.yaml",
            "tui_config.yaml",
        ];
        for path in &candidates {
            if let Ok(text) = std::fs::read_to_string(path) {
                return Self::parse_yaml(&text);
            }
        }
        Self::defaults()
    }

    fn parse_yaml(text: &str) -> Self {
        let get = |key: &str| -> Option<String> {
            text.lines().find_map(|line| {
                let line = line.trim();
                if line.starts_with('#') { return None; }
                let (k, v) = line.split_once(':')?;
                if k.trim() == key {
                    Some(v.trim().trim_matches('"').trim_matches('\'').to_string())
                } else {
                    None
                }
            })
        };

        let token_env_var = get("api_token_env").unwrap_or_else(|| "PORTFOLIO_OS_API_TOKEN".to_string());
        let api_token = std::env::var(&token_env_var)
            .or_else(|_| std::env::var("PORTFOLIO_OS_API_TOKEN"))
            .or_else(|_| std::env::var("API_AUTH_TOKEN"))
            .or_else(|_| std::env::var("API_TOKEN"))
            .unwrap_or_default()
            .trim()
            .to_string();
        if api_token.is_empty() {
            panic!("SECURITY CRITICAL: Set PORTFOLIO_OS_API_TOKEN or API_AUTH_TOKEN before launching TUI.");
        }

        Config {
            base_url:       get("base_url").unwrap_or_else(|| "http://127.0.0.1:8080".to_string()),
            api_token,
            fiscal_year:    get("fiscal_year").unwrap_or_else(Self::detect_fiscal_year),
            default_fund_a: get("default_fund_a").unwrap_or_else(|| "INF109KC13X2".to_string()),
            default_fund_b: get("default_fund_b").unwrap_or_else(|| "INF109KC12U0".to_string()),
        }
    }

    fn defaults() -> Self {
        let api_token = std::env::var("PORTFOLIO_OS_API_TOKEN")
            .or_else(|_| std::env::var("API_AUTH_TOKEN"))
            .unwrap_or_default()
            .trim()
            .to_string();
        if api_token.is_empty() {
            panic!("SECURITY CRITICAL: Set PORTFOLIO_OS_API_TOKEN or API_AUTH_TOKEN before launching TUI.");
        }
        Config {
            base_url:       "http://127.0.0.1:8080".to_string(),
            api_token,
            fiscal_year:    Self::detect_fiscal_year(),
            default_fund_a: "INF109KC13X2".to_string(),
            default_fund_b: "INF109KC12U0".to_string(),
        }
    }

    fn detect_fiscal_year() -> String {
        let secs = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let days = (secs / 86400) as i64 + 719468;
        let era = if days >= 0 { days } else { days - 146096 } / 146097;
        let doe = (days - era * 146097) as u64;
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        let y = (yoe as i64) + era * 400;
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        let mp = (5 * doy + 2) / 153;
        let m = if mp < 10 { mp + 3 } else { mp - 9 };
        let year = if m <= 2 { y + 1 } else { y };
        if m >= 4 {
            format!("{}-{:02}", year, (year + 1) % 100)
        } else {
            format!("{}-{:02}", year - 1, year % 100)
        }
    }
}

fn cache_path() -> Option<std::path::PathBuf> {
    if let Ok(xdg) = std::env::var("XDG_CACHE_HOME") {
        Some(std::path::PathBuf::from(xdg).join("portfolio-os").join("last_snapshot.json"))
    } else if let Ok(home) = std::env::var("HOME") {
        Some(std::path::PathBuf::from(home).join(".cache").join("portfolio-os").join("last_snapshot.json"))
    } else {
        None
    }
}

pub fn save_to_cache(text: &str) {
    if let Some(p) = cache_path() {
        if let Some(parent) = p.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        if std::fs::write(&p, text).is_ok() {
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                let _ = std::fs::set_permissions(&p, std::fs::Permissions::from_mode(0o600));
            }
        }
    }
}

pub fn load_from_cache() -> Option<(Snapshot, String)> {
    let p = cache_path()?;
    let text = std::fs::read_to_string(p).ok()?;
    let snap: Snapshot = serde_json::from_str(&text).ok()?;
    let date = snap.sync_info.generated_at.get(..10).unwrap_or("CACHED").to_string();
    Some((snap, date))
}

fn build_agent(_config: &Config) -> ureq::Agent {
    ureq::AgentBuilder::new()
        .timeout(HTTP_TIMEOUT)
        .build()
}

/// Continuous polling loop — runs forever on its own thread.
pub fn poll_loop(tx: Sender<AppMsg>) {
    let config = Config::load();
    let agent = build_agent(&config);

    // Initial load from offline cache if available
    if let Some((cached_snap, date)) = load_from_cache() {
        let _ = tx.send(AppMsg::OfflineSnapshotLoaded(Box::new(cached_snap), date));
    }

    // Initial network fetch immediately on startup
    do_fetch_all(&agent, &config, &tx);

    loop {
        std::thread::sleep(POLL_INTERVAL);
        do_fetch_all(&agent, &config, &tx);
    }
}

/// Single fetch of all three endpoints. Called by poll_loop and by the [r] shortcut.
pub fn fetch_all_once(tx: &Sender<AppMsg>) {
    let config = Config::load();
    let agent = build_agent(&config);
    do_fetch_all(&agent, &config, tx);
}

fn do_fetch_all(agent: &ureq::Agent, config: &Config, tx: &Sender<AppMsg>) {
    fetch_snapshot(agent, config, tx);
    fetch_overlap(agent, config, tx);
    fetch_hrp(agent, config, tx);
}

fn fetch_snapshot(agent: &ureq::Agent, config: &Config, tx: &Sender<AppMsg>) {
    let url = format!(
        "{}/api/v1/sync/snapshot?fy={}",
        config.base_url, config.fiscal_year
    );
    match get_json_str(agent, &url, &config.api_token) {
        Ok(json_str) => {
            save_to_cache(&json_str);
            if let Ok(snap) = serde_json::from_str::<Snapshot>(&json_str) {
                let _ = tx.send(AppMsg::SnapshotUpdated(Box::new(snap)));
            }
        }
        Err(e) => {
            // Check if we can fallback to cache
            if let Some((cached_snap, date)) = load_from_cache() {
                let _ = tx.send(AppMsg::OfflineSnapshotLoaded(Box::new(cached_snap), date));
            }
            let concise_err = if e.contains("Connection refused") || e.contains("timed out") || e.contains("dns") || e.contains("resolve") {
                "Offline (core-node :8080 unreachable)".to_string()
            } else {
                format!("snapshot: {e}")
            };
            let _ = tx.send(AppMsg::FetchError(concise_err));
        }
    }
}

fn fetch_overlap(agent: &ureq::Agent, config: &Config, tx: &Sender<AppMsg>) {
    let url = format!(
        "{}/api/v1/analytics/overlap?fundA={}&fundB={}&includeUnverified=false",
        config.base_url, config.default_fund_a, config.default_fund_b
    );
    match get_json::<OverlapResponse>(agent, &url, &config.api_token) {
        Ok(resp) => {
            if let Some(summary) = resp.overlap_summary {
                let _ = tx.send(AppMsg::OverlapUpdated(Box::new(summary)));
            }
        }
        Err(e) => {
            let concise_err = if e.contains("Connection refused") || e.contains("timed out") || e.contains("dns") {
                "Offline (core-node :8080 unreachable)".to_string()
            } else {
                format!("overlap: {e}")
            };
            let _ = tx.send(AppMsg::FetchError(concise_err));
        }
    }
}

fn fetch_hrp(agent: &ureq::Agent, config: &Config, tx: &Sender<AppMsg>) {
    let url = format!("{}/api/v1/analytics/hrp-audit", config.base_url);
    if let Ok(hrp) = get_json::<crate::api::HrpAudit>(agent, &url, &config.api_token) {
        let _ = tx.send(AppMsg::HrpUpdated(Box::new(hrp)));
    }
}



fn get_json_str(
    agent: &ureq::Agent,
    url: &str,
    token: &str,
) -> Result<String, String> {
    let mut req = agent.get(url);
    if !token.is_empty() {
        req = req.set("X-Api-Auth-Token", token);
    }
    req.call()
        .map_err(|e| e.to_string())?
        .into_string()
        .map_err(|e| e.to_string())
}

fn get_json<T: serde::de::DeserializeOwned>(
    agent: &ureq::Agent,
    url: &str,
    token: &str,
) -> Result<T, String> {
    let json_str = get_json_str(agent, url, token)?;
    serde_json::from_str::<T>(&json_str).map_err(|e| e.to_string())
}
