// cockpit-gpui/src/app.rs

use gpui::*;
use std::io::Read;
use crate::models::{Snapshot, ExemptionStatus, RebalancePlan};
use crate::kmap::{classify_snapshot, KMapMatrix};
use crate::theme::Theme;

actions!(portfolio, [
    ToggleKMap,
    TriggerInflowModal,
    IsolateHarvest,
    ExportItr2,
    RefreshSnapshot,
    ToggleCommandPalette,
    BackOrQuit,
    Quit
]);

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum ActiveView {
    Equilibrium,
    KMapMatrix,
    HarvestIsolation,
}

pub struct PortfolioHud {
    pub focus_handle: FocusHandle,
    pub snapshot: Option<Snapshot>,
    pub exemption_status: Option<ExemptionStatus>,
    pub kmap: Option<KMapMatrix>,
    pub active_view: ActiveView,
    pub is_loading: bool,
    pub error_msg: Option<String>,
    pub export_status: Option<String>,
    pub pending_inflow_rupees: f64,
    pub show_palette: bool,
}

pub fn get_api_config() -> (String, Option<String>) {
    let base_url = std::env::var("PORTFOLIO_OS_API_BASE_URL")
        .unwrap_or_else(|_| "http://127.0.0.1:8080".to_string());

    let mut token = std::env::var("PORTFOLIO_OS_API_TOKEN")
        .or_else(|_| std::env::var("API_AUTH_TOKEN"))
        .or_else(|_| std::env::var("API_TOKEN"))
        .ok()
        .filter(|t| !t.trim().is_empty());

    if token.is_none() {
        let candidates = ["cockpit-gpui/config.yaml", "config.yaml"];
        for path in &candidates {
            if let Ok(text) = std::fs::read_to_string(path) {
                for line in text.lines() {
                    let line = line.trim();
                    if line.starts_with('#') {
                        continue;
                    }
                    if let Some((k, v)) = line.split_once(':') {
                        let key = k.trim();
                        let val = v.trim().trim_matches('"').trim_matches('\'');
                        if key == "api_token_env" {
                            if let Ok(env_val) = std::env::var(val) {
                                if !env_val.trim().is_empty() {
                                    token = Some(env_val.trim().to_string());
                                    break;
                                }
                            }
                        } else if key == "api_token" && !val.is_empty() {
                            token = Some(val.to_string());
                            break;
                        }
                    }
                }
                if token.is_some() {
                    break;
                }
            }
        }
    }

    if token.is_none() {
        // Fallback: search ~/.config/portfolio-os/secrets.env
        if let Ok(home) = std::env::var("HOME") {
            let path = format!("{}/.config/portfolio-os/secrets.env", home);
            if let Ok(text) = std::fs::read_to_string(&path) {
                for line in text.lines() {
                    let line = line.trim();
                    if let Some((k, v)) = line.split_once('=') {
                        let key = k.trim();
                        let val = v.trim().trim_matches('"').trim_matches('\'');
                        if (key == "API_AUTH_TOKEN" || key == "PORTFOLIO_OS_API_TOKEN") && !val.is_empty() {
                            token = Some(val.to_string());
                            break;
                        }
                    }
                }
            }
        }
    }

    (base_url, token)
}

impl PortfolioHud {
    pub fn new(window: &mut Window, cx: &mut Context<Self>) -> Self {
        let focus_handle = cx.focus_handle();
        window.focus(&focus_handle);

        // Schedule immediate initial snapshot fetch
        cx.spawn(async move |this, cx| {
            this.update(cx, |hud, cx| hud.fetch_snapshot(cx)).ok();
        }).detach();

        // Schedule periodic background polling loop (every 15s)
        cx.spawn(async move |this, cx| {
            loop {
                smol::Timer::after(std::time::Duration::from_secs(15)).await;
                let poll_result = this.update(cx, |hud, cx| {
                    hud.fetch_snapshot(cx);
                });
                if poll_result.is_err() {
                    break; // Entity or window destroyed
                }
            }
        }).detach();

        Self {
            focus_handle,
            snapshot: None,
            exemption_status: None,
            kmap: None,
            active_view: ActiveView::Equilibrium,
            is_loading: true,
            error_msg: None,
            export_status: None,
            pending_inflow_rupees: 50_000.0, // Default monthly SIP allocation
            show_palette: false,
        }
    }

    pub fn fetch_snapshot(&mut self, cx: &mut Context<Self>) {
        self.is_loading = true;
        cx.notify();

        cx.spawn(async move |this, cx| {
            let res = smol::unblock(|| {
                let (base_url, token) = get_api_config();
                let clean_base = base_url.trim_end_matches('/');

                // 1. Fetch live snapshot
                let snap_url = format!("{clean_base}/api/v1/sync/snapshot");
                let mut snap_req = ureq::get(&snap_url).timeout(std::time::Duration::from_millis(2500));
                if let Some(ref tok) = token {
                    snap_req = snap_req.set("X-Api-Auth-Token", tok);
                }
                let snap = snap_req.call()
                    .map_err(|e| format!("Snapshot call failed: {e}"))?
                    .into_json::<Snapshot>()
                    .map_err(|e| format!("Snapshot deserialization failed: {e}"))?;

                // 2. Fetch live statutory exemption status (Zero Fabrication)
                let ex_url = format!("{clean_base}/api/v1/tax/exemption-status");
                let mut ex_req = ureq::get(&ex_url).timeout(std::time::Duration::from_millis(2000));
                if let Some(ref tok) = token {
                    ex_req = ex_req.set("X-Api-Auth-Token", tok);
                }
                let ex_status = ex_req.call()
                    .ok()
                    .and_then(|r| r.into_json::<ExemptionStatus>().ok());

                Ok::<(Snapshot, Option<ExemptionStatus>), String>((snap, ex_status))
            }).await;

            this.update(cx, |hud, cx| {
                hud.is_loading = false;
                match res {
                    Ok((snap, ex_status)) => {
                        // Sourced directly from ExemptionStatus (or fallback to 0.0, never fabricated 25,000)
                        let realized_ltcg = ex_status.as_ref()
                            .and_then(|e| e.net_ltcg_before_exemption.parse::<f64>().ok())
                            .unwrap_or(0.0);

                        hud.kmap = Some(classify_snapshot(&snap, realized_ltcg));
                        hud.snapshot = Some(snap);
                        hud.exemption_status = ex_status;
                        hud.error_msg = None;
                    }
                    Err(err) => {
                        hud.error_msg = Some(format!("Core Node error: {err}"));
                    }
                }
                cx.notify();
            }).ok();
        }).detach();
    }

    pub fn adjust_inflow(&mut self, cx: &mut Context<Self>) {
        // Cycle SIP inflow test rungs: ₹25k -> ₹50k -> ₹75k -> ₹100k -> ₹150k -> ₹25k
        self.pending_inflow_rupees = match self.pending_inflow_rupees as u64 {
            25_000 => 50_000.0,
            50_000 => 75_000.0,
            75_000 => 100_000.0,
            100_000 => 150_000.0,
            _ => 25_000.0,
        };

        let inflow_amt = self.pending_inflow_rupees;
        cx.notify();

        // Perform live simulation with Core Node's RebalancePlanEngine
        cx.spawn(async move |this, cx| {
            let sim_res = smol::unblock(move || {
                let (base_url, token) = get_api_config();
                let clean_base = base_url.trim_end_matches('/');
                let url = format!("{clean_base}/api/v1/sync/rebalance/simulate-lumpsum");
                let mut req = ureq::post(&url).timeout(std::time::Duration::from_millis(2500));
                if let Some(ref tok) = token {
                    req = req.set("X-Api-Auth-Token", tok);
                }
                req.send_json(serde_json::json!({
                    "amount": inflow_amt,
                    "include_rebalance": false
                }))
                .map_err(|e| e.to_string())?
                .into_json::<RebalancePlan>()
                .map_err(|e| e.to_string())
            }).await;

            this.update(cx, |hud, cx| {
                if let Ok(sim_plan) = sim_res {
                    if let Some(ref mut snap) = hud.snapshot {
                        snap.rebalance_plan = Some(sim_plan);
                        let realized_ltcg = hud.exemption_status.as_ref()
                            .and_then(|e| e.net_ltcg_before_exemption.parse::<f64>().ok())
                            .unwrap_or(0.0);
                        hud.kmap = Some(classify_snapshot(snap, realized_ltcg));
                    }
                }
                cx.notify();
            }).ok();
        }).detach();
    }

    pub fn export_itr2(&mut self, cx: &mut Context<Self>) {
        self.export_status = Some("Exporting ITR-2...".to_string());
        cx.notify();

        cx.spawn(async move |this, cx| {
            let res = smol::unblock(|| {
                let (base_url, token) = get_api_config();
                let clean_base = base_url.trim_end_matches('/');
                let url = format!("{clean_base}/api/v1/tax/export/itr2/zip");
                let mut req = ureq::get(&url).timeout(std::time::Duration::from_secs(10));
                if let Some(ref tok) = token {
                    req = req.set("X-Api-Auth-Token", tok);
                }
                let response = req.call().map_err(|e| e.to_string())?;

                let mut reader = response.into_reader();
                let mut bytes = Vec::new();
                reader.read_to_end(&mut bytes).map_err(|e| e.to_string())?;

                let home = std::env::var("HOME").unwrap_or_else(|_| ".".to_string());
                let downloads = std::path::Path::new(&home).join("Downloads");
                let target_dir = if downloads.exists() { downloads } else { std::path::PathBuf::from(&home) };

                let ts = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .map(|d| d.as_secs())
                    .unwrap_or(0);
                let filename = format!("itr2_schedule_cg_{ts}.zip");
                let dest = target_dir.join(&filename);

                std::fs::write(&dest, &bytes).map_err(|e| e.to_string())?;
                Ok::<String, String>(dest.to_string_lossy().to_string())
            }).await;

            this.update(cx, |hud, cx| {
                match res {
                    Ok(path) => {
                        hud.export_status = Some(format!("Saved: {path}"));
                    }
                    Err(err) => {
                        hud.export_status = Some(format!("Export failed: {err}"));
                    }
                }
                cx.notify();
            }).ok();
        }).detach();
    }
}

impl Render for PortfolioHud {
    fn render(&mut self, _window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let theme = Theme::stealth();

        div()
            .track_focus(&self.focus_handle)
            .flex()
            .flex_col()
            .size_full()
            .bg(theme.bg_base)
            .text_color(theme.text_primary)
            .font_family("JetBrains Mono")
            // Keybinding action handlers
            .on_action(cx.listener(|this, _: &ToggleKMap, _window, cx| {
                this.active_view = match this.active_view {
                    ActiveView::KMapMatrix => ActiveView::Equilibrium,
                    _ => ActiveView::KMapMatrix,
                };
                cx.notify();
            }))
            .on_action(cx.listener(|this, _: &IsolateHarvest, _window, cx| {
                this.active_view = match this.active_view {
                    ActiveView::HarvestIsolation => ActiveView::Equilibrium,
                    _ => ActiveView::HarvestIsolation,
                };
                cx.notify();
            }))
            .on_action(cx.listener(|this, _: &TriggerInflowModal, _window, cx| {
                this.adjust_inflow(cx);
            }))
            .on_action(cx.listener(|this, _: &ExportItr2, _window, cx| {
                this.export_itr2(cx);
            }))
            .on_action(cx.listener(|this, _: &RefreshSnapshot, _window, cx| {
                this.fetch_snapshot(cx);
            }))
            .on_action(cx.listener(|this, _: &ToggleCommandPalette, _window, cx| {
                this.show_palette = !this.show_palette;
                cx.notify();
            }))
            .on_action(cx.listener(|this, _: &BackOrQuit, _window, cx| {
                if this.show_palette {
                    this.show_palette = false;
                    cx.notify();
                } else {
                    match this.active_view {
                        ActiveView::KMapMatrix | ActiveView::HarvestIsolation => {
                            this.active_view = ActiveView::Equilibrium;
                            cx.notify();
                        }
                        ActiveView::Equilibrium => {
                            cx.quit();
                        }
                    }
                }
            }))
            .on_action(cx.listener(|_, _: &Quit, _window, cx| {
                cx.quit();
            }))
            .child(self.render_top_bar(&theme))
            .child(
                div()
                    .flex_1()
                    .p_4()
                    .relative()
                    .child(match self.active_view {
                        ActiveView::Equilibrium => self.render_equilibrium_view(&theme).into_any_element(),
                        ActiveView::KMapMatrix => self.render_kmap_view(&theme).into_any_element(),
                        ActiveView::HarvestIsolation => self.render_harvest_isolation_view(&theme).into_any_element(),
                    })
                    .child(if self.show_palette {
                        self.render_palette_overlay(&theme).into_any_element()
                    } else {
                        div().into_any_element()
                    })
            )
            .child(self.render_footer_chord_bar(&theme))
    }
}
