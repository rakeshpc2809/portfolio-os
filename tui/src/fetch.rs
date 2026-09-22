use std::sync::mpsc::Sender;
use std::time::Duration;

use crate::api::{HrpAudit, OverlapResponse, Snapshot};
use crate::app::AppMsg;

const POLL_INTERVAL: Duration = Duration::from_secs(5);
const HTTP_TIMEOUT:  Duration = Duration::from_secs(8);

// Config is loaded once at startup from tui_config.yaml.
// Falls back to safe defaults so the binary works without a config file.
struct Config {
    base_url: String,
    api_token: String,
    fiscal_year: String,
    default_fund_a: String,
    default_fund_b: String,
}

impl Config {
    fn load() -> Self {
        // Try the same three-path probe used by core-node for consistency.
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
        // Hardcoded safe defaults — never a secret key
        Self::defaults()
    }

    fn parse_yaml(text: &str) -> Self {
        // Manual key extraction to avoid pulling in a YAML crate.
        // tui_config.yaml is a simple flat map — one key: value per line.
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

        // Read API token from environment variable named in config (or env directly)
        let token_env_var = get("api_token_env").unwrap_or_else(|| "PORTFOLIO_OS_API_TOKEN".to_string());
        let api_token = std::env::var(&token_env_var)
            .or_else(|_| std::env::var("PORTFOLIO_OS_API_TOKEN"))
            .or_else(|_| std::env::var("API_TOKEN"))
            .unwrap_or_default();

        Config {
            base_url:       get("base_url").unwrap_or_else(|| "http://127.0.0.1:8080".to_string()),
            api_token,
            fiscal_year:    get("fiscal_year").unwrap_or_else(|| "2026-27".to_string()),
            default_fund_a: get("default_fund_a").unwrap_or_else(|| "INF109KC13X2".to_string()),
            default_fund_b: get("default_fund_b").unwrap_or_else(|| "INF109KC12U0".to_string()),
        }
    }

    fn defaults() -> Self {
        Config {
            base_url:       "http://127.0.0.1:8080".to_string(),
            api_token:      std::env::var("PORTFOLIO_OS_API_TOKEN").unwrap_or_default(),
            fiscal_year:    "2026-27".to_string(),
            default_fund_a: "INF109KC13X2".to_string(),
            default_fund_b: "INF109KC12U0".to_string(),
        }
    }
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

    // Initial fetch immediately on startup
    do_fetch_all(&agent, &config, &tx);

    loop {
        std::thread::sleep(POLL_INTERVAL);
        do_fetch_all(&agent, &config, &tx);
    }
}

/// Single fetch of all three endpoints.  Called by poll_loop and by the [r] shortcut.
pub fn fetch_all_once(tx: &Sender<AppMsg>) {
    let config = Config::load();
    let agent = build_agent(&config);
    do_fetch_all(&agent, &config, tx);
}

fn do_fetch_all(agent: &ureq::Agent, config: &Config, tx: &Sender<AppMsg>) {
    fetch_snapshot(agent, config, tx);
    fetch_overlap(agent, config, tx);
    fetch_hrp_audit(agent, config, tx);
}

fn fetch_snapshot(agent: &ureq::Agent, config: &Config, tx: &Sender<AppMsg>) {
    let url = format!(
        "{}/api/v1/sync/snapshot?fy={}",
        config.base_url, config.fiscal_year
    );
    match get_json::<Snapshot>(agent, &url, &config.api_token) {
        Ok(snap) => { let _ = tx.send(AppMsg::SnapshotUpdated(Box::new(snap))); }
        Err(e)   => { let _ = tx.send(AppMsg::FetchError(format!("snapshot: {e}"))); }
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
        Err(e) => { let _ = tx.send(AppMsg::FetchError(format!("overlap: {e}"))); }
    }
}

fn fetch_hrp_audit(agent: &ureq::Agent, config: &Config, tx: &Sender<AppMsg>) {
    let url = format!("{}/api/v1/analytics/hrp-audit", config.base_url);
    match get_json::<HrpAudit>(agent, &url, &config.api_token) {
        Ok(hrp) => { let _ = tx.send(AppMsg::HrpUpdated(Box::new(hrp))); }
        Err(e)  => { let _ = tx.send(AppMsg::FetchError(format!("hrp-audit: {e}"))); }
    }
}

fn get_json<T: serde::de::DeserializeOwned>(
    agent: &ureq::Agent,
    url: &str,
    token: &str,
) -> Result<T, String> {
    let mut req = agent.get(url);
    if !token.is_empty() {
        req = req.set("X-Api-Auth-Token", token);
    }
    req.call()
        .map_err(|e| e.to_string())?
        .into_json::<T>()
        .map_err(|e| e.to_string())
}
