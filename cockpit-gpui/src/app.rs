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

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ActiveView {
    Equilibrium,
    KMapMatrix,
    HarvestIsolation,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum EscAction {
    DismissPalette,
    ReturnToEquilibrium,
    QuitApplication,
}

#[derive(Debug, Clone)]
pub struct ApiConfig {
    pub base_url: String,
    pub token: Option<String>,
    pub poll_interval_secs: u64,
}

pub struct PortfolioHud {
    pub focus_handle: FocusHandle,
    pub snapshot: Option<Snapshot>,
    pub exemption_status: Result<ExemptionStatus, String>,
    pub kmap: Option<KMapMatrix>,
    pub active_view: ActiveView,
    pub is_loading: bool,
    pub error_msg: Option<String>,
    pub simulation_error: Option<String>,
    pub export_status: Option<String>,
    pub pending_inflow_rupees: f64,
    pub is_simulation_active: bool,
    pub show_palette: bool,
}

pub fn get_api_config() -> ApiConfig {
    let mut base_url = "http://127.0.0.1:8080".to_string();
    let mut token = None;
    let mut poll_interval_secs = 15;

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
                    if key == "base_url" && !val.is_empty() {
                        base_url = val.to_string();
                    } else if token.is_none() && key == "api_token_env" {
                        if let Ok(env_val) = std::env::var(val) {
                            if !env_val.trim().is_empty() {
                                token = Some(env_val.trim().to_string());
                            }
                        }
                    } else if token.is_none() && key == "api_token" && !val.is_empty() {
                        token = Some(val.to_string());
                    } else if key == "poll_interval_secs" {
                        if let Ok(secs) = val.parse::<u64>() {
                            poll_interval_secs = secs;
                        }
                    }
                }
            }
            if token.is_some() {
                break;
            }
        }
    }

    // Explicit environment variable overrides
    if let Ok(url) = std::env::var("PORTFOLIO_OS_API_BASE_URL") {
        if !url.trim().is_empty() {
            base_url = url;
        }
    }

    if let Some(t) = std::env::var("PORTFOLIO_OS_API_TOKEN")
        .or_else(|_| std::env::var("API_AUTH_TOKEN"))
        .or_else(|_| std::env::var("API_TOKEN"))
        .ok()
        .filter(|t| !t.trim().is_empty())
    {
        token = Some(t);
    }

    if let Ok(secs) = std::env::var("PORTFOLIO_OS_POLL_INTERVAL_SECS") {
        if let Ok(s) = secs.parse::<u64>() {
            poll_interval_secs = s;
        }
    }

    if token.is_none() {
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

    ApiConfig {
        base_url,
        token,
        poll_interval_secs,
    }
}

impl PortfolioHud {
    pub fn new(window: &mut Window, cx: &mut Context<Self>) -> Self {
        let focus_handle = cx.focus_handle();
        window.focus(&focus_handle);

        let config = get_api_config();
        let poll_interval = config.poll_interval_secs;

        // Schedule immediate initial snapshot fetch
        cx.spawn(async move |this, cx| {
            this.update(cx, |hud, cx| hud.fetch_snapshot(cx)).ok();
        }).detach();

        // Schedule periodic background polling loop using configured interval
        cx.spawn(async move |this, cx| {
            loop {
                smol::Timer::after(std::time::Duration::from_secs(poll_interval)).await;
                let poll_result = this.update(cx, |hud, cx| {
                    hud.fetch_snapshot(cx);
                });
                if poll_result.is_err() {
                    break;
                }
            }
        }).detach();

        let active_view = match std::env::var("PORTFOLIO_VIEW").as_deref() {
            Ok("kmap") => ActiveView::KMapMatrix,
            Ok("harvest") => ActiveView::HarvestIsolation,
            _ => ActiveView::Equilibrium,
        };
        let show_palette = std::env::var("PORTFOLIO_PALETTE").map(|v| v == "1" || v == "true").unwrap_or(false);
        let (pending_inflow_rupees, is_simulation_active) = if let Ok(val) = std::env::var("PORTFOLIO_INFLOW") {
            if let Ok(inflow) = val.parse::<f64>() {
                (inflow, true)
            } else {
                (50_000.0, false)
            }
        } else {
            (50_000.0, false)
        };

        Self {
            focus_handle,
            snapshot: None,
            exemption_status: Err("Initializing statutory exemption tracking...".to_string()),
            kmap: None,
            active_view,
            is_loading: true,
            error_msg: None,
            simulation_error: None,
            export_status: None,
            pending_inflow_rupees,
            is_simulation_active,
            show_palette,
        }
    }

    pub fn process_esc_key(&mut self) -> EscAction {
        Self::eval_esc_key(&mut self.active_view, &mut self.show_palette)
    }

    pub fn eval_esc_key(active_view: &mut ActiveView, show_palette: &mut bool) -> EscAction {
        if *show_palette {
            *show_palette = false;
            EscAction::DismissPalette
        } else {
            match *active_view {
                ActiveView::KMapMatrix | ActiveView::HarvestIsolation => {
                    *active_view = ActiveView::Equilibrium;
                    EscAction::ReturnToEquilibrium
                }
                ActiveView::Equilibrium => EscAction::QuitApplication,
            }
        }
    }

    pub fn resolve_poll_simulation(
        active_inflow: Option<f64>,
        sim_result: Option<Result<RebalancePlan, String>>,
        mut base_snapshot: Snapshot,
    ) -> Result<Snapshot, String> {
        match (active_inflow, sim_result) {
            (Some(_), Some(Ok(plan))) => {
                base_snapshot.rebalance_plan = Some(plan);
                Ok(base_snapshot)
            }
            (Some(inflow), Some(Err(err))) => {
                Err(format!("Re-simulation for ₹{:.0} failed during poll: {}", inflow, err))
            }
            (Some(inflow), None) => {
                Err(format!("Re-simulation for ₹{:.0} was active but simulation call was skipped", inflow))
            }
            (None, _) => Ok(base_snapshot),
        }
    }

    pub fn fetch_snapshot(&mut self, cx: &mut Context<Self>) {
        self.is_loading = true;
        let active_simulation_inflow = if self.is_simulation_active {
            Some(self.pending_inflow_rupees)
        } else {
            None
        };
        cx.notify();

        cx.spawn(async move |this, cx| {
            let res = smol::unblock(move || {
                let config = get_api_config();
                let clean_base = config.base_url.trim_end_matches('/');

                // 1. Fetch live snapshot
                let snap_url = format!("{clean_base}/api/v1/sync/snapshot");
                let mut snap_req = ureq::get(&snap_url).timeout(std::time::Duration::from_millis(2500));
                if let Some(ref tok) = config.token {
                    snap_req = snap_req.set("X-Api-Auth-Token", tok);
                }
                let snap = snap_req.call()
                    .map_err(|e| format!("Snapshot fetch failed: {e}"))?
                    .into_json::<Snapshot>()
                    .map_err(|e| format!("Snapshot deserialization failed: {e}"))?;

                // 2. Fetch live statutory exemption status (Fails Closed)
                let ex_url = format!("{clean_base}/api/v1/tax/exemption-status");
                let mut ex_req = ureq::get(&ex_url).timeout(std::time::Duration::from_millis(2000));
                if let Some(ref tok) = config.token {
                    ex_req = ex_req.set("X-Api-Auth-Token", tok);
                }
                let ex_status = ex_req.call()
                    .map_err(|e| format!("Exemption status endpoint unreachable: {e}"))?
                    .into_json::<ExemptionStatus>()
                    .map_err(|e| format!("Exemption status deserialization failed: {e}"))?;

                // 3. If an inflow simulation is currently active, re-simulate without silent revert
                let sim_result = if let Some(inflow_amt) = active_simulation_inflow {
                    let sim_url = format!("{clean_base}/api/v1/sync/rebalance/simulate-lumpsum");
                    let mut sim_req = ureq::post(&sim_url).timeout(std::time::Duration::from_millis(2500));
                    if let Some(ref tok) = config.token {
                        sim_req = sim_req.set("X-Api-Auth-Token", tok);
                    }
                    let res = sim_req.send_json(serde_json::json!({
                        "amount": inflow_amt,
                        "include_rebalance": false
                    }))
                    .map_err(|e| format!("Network error: {e}"))
                    .and_then(|resp| resp.into_json::<RebalancePlan>()
                        .map_err(|e| format!("Deserialization error: {e}")));
                    Some(res)
                } else {
                    None
                };

                let base_snap_copy = snap.clone();
                let (resolved_snap, sim_err) = match Self::resolve_poll_simulation(active_simulation_inflow, sim_result, snap) {
                    Ok(s) => (s, None),
                    Err(e) => (base_snap_copy, Some(e)),
                };

                if std::env::var("PORTFOLIO_OS_DEBUG_POLL").map(|v| v == "1" || v == "true").unwrap_or(false) {
                    let ts = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_secs();
                    eprintln!("[POLL_TICK ts={ts}] Snapshot fetched and resolved. Inflow active: {:?}. Plan attached: {}",
                        active_simulation_inflow, resolved_snap.rebalance_plan.is_some());
                }

                Ok::<(Snapshot, ExemptionStatus, Option<String>), String>((resolved_snap, ex_status, sim_err))
            }).await;

            this.update(cx, |hud, cx| {
                hud.is_loading = false;
                match res {
                    Ok((snap, ex_status, sim_err)) => {
                        hud.simulation_error = sim_err;
                        let remaining_res = ex_status.exemption_remaining.parse::<f64>()
                            .map_err(|e| format!("Invalid exemption_remaining: {e}"));

                        match remaining_res {
                            Ok(headroom) => {
                                hud.kmap = Some(classify_snapshot(&snap, headroom));
                                hud.snapshot = Some(snap);
                                hud.exemption_status = Ok(ex_status);
                                hud.error_msg = None;
                            }
                            Err(e) => {
                                hud.kmap = None;
                                hud.snapshot = Some(snap);
                                hud.exemption_status = Err(e.clone());
                                hud.error_msg = Some(format!("Statutory exemption invalid: {e}"));
                            }
                        }
                    }
                    Err(err) => {
                        hud.kmap = None;
                        hud.snapshot = None;
                        hud.exemption_status = Err(err.clone());
                        hud.simulation_error = None;
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
        self.is_simulation_active = true;
        let inflow_amt = self.pending_inflow_rupees;
        cx.notify();

        // Perform live simulation with Core Node's RebalancePlanEngine
        cx.spawn(async move |this, cx| {
            let sim_res = smol::unblock(move || {
                let config = get_api_config();
                let clean_base = config.base_url.trim_end_matches('/');
                let url = format!("{clean_base}/api/v1/sync/rebalance/simulate-lumpsum");
                let mut req = ureq::post(&url).timeout(std::time::Duration::from_millis(2500));
                if let Some(ref tok) = config.token {
                    req = req.set("X-Api-Auth-Token", tok);
                }
                req.send_json(serde_json::json!({
                    "amount": inflow_amt,
                    "include_rebalance": false
                }))
                .map_err(|e| format!("Simulation network call failed: {e}"))?
                .into_json::<RebalancePlan>()
                .map_err(|e| format!("Simulation deserialization failed: {e}"))
            }).await;

            this.update(cx, |hud, cx| {
                match sim_res {
                    Ok(sim_plan) => {
                        if let Some(ref mut snap) = hud.snapshot {
                            snap.rebalance_plan = Some(sim_plan);
                            if let Ok(ref ex_stat) = hud.exemption_status {
                                if let Ok(headroom) = ex_stat.exemption_remaining.parse::<f64>() {
                                    hud.kmap = Some(classify_snapshot(snap, headroom));
                                }
                            }
                        }
                        hud.simulation_error = None;
                        hud.error_msg = None;
                    }
                    Err(err) => {
                        hud.simulation_error = Some(format!("Inflow simulation failed: {err}"));
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
                let config = get_api_config();
                let clean_base = config.base_url.trim_end_matches('/');
                let url = format!("{clean_base}/api/v1/tax/export/itr2/zip");
                let mut req = ureq::get(&url).timeout(std::time::Duration::from_secs(10));
                if let Some(ref tok) = config.token {
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
                match this.process_esc_key() {
                    EscAction::DismissPalette | EscAction::ReturnToEquilibrium => {
                        cx.notify();
                    }
                    EscAction::QuitApplication => {
                        cx.quit();
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

#[cfg(test)]
mod tests {
    use super::{ActiveView, EscAction, PortfolioHud, get_api_config, RebalancePlan};

    #[core::prelude::v1::test]
    fn test_esc_navigation_state_machine() {
        let mut active_view = ActiveView::KMapMatrix;
        let mut show_palette = true;

        // 1. When palette is open, Esc dismisses palette and preserves active view
        assert_eq!(PortfolioHud::eval_esc_key(&mut active_view, &mut show_palette), EscAction::DismissPalette);
        assert!(!show_palette);
        assert_eq!(active_view, ActiveView::KMapMatrix);

        // 2. When in KMapMatrix view, Esc transitions back to Equilibrium
        assert_eq!(PortfolioHud::eval_esc_key(&mut active_view, &mut show_palette), EscAction::ReturnToEquilibrium);
        assert_eq!(active_view, ActiveView::Equilibrium);

        // 3. When in Equilibrium, Esc triggers quit
        assert_eq!(PortfolioHud::eval_esc_key(&mut active_view, &mut show_palette), EscAction::QuitApplication);

        // 4. In HarvestIsolation, Esc transitions to Equilibrium
        active_view = ActiveView::HarvestIsolation;
        assert_eq!(PortfolioHud::eval_esc_key(&mut active_view, &mut show_palette), EscAction::ReturnToEquilibrium);
        assert_eq!(active_view, ActiveView::Equilibrium);
    }

    #[core::prelude::v1::test]
    fn test_config_poll_interval_and_token_parsing() {
        let config = get_api_config();
        assert!(!config.base_url.is_empty());
        assert_eq!(config.poll_interval_secs, 15);
    }

    #[core::prelude::v1::test]
    fn test_poll_merge_applies_simulated_plan_when_active() {
        use crate::kmap::tests::mock_snapshot;
        let base_snap = mock_snapshot();
        assert!(base_snap.rebalance_plan.is_none());

        let simulated_plan = RebalancePlan {
            plan_id: "test-sim-id".to_string(),
            generated_at: "2026-11-12T00:00:00Z".to_string(),
            trigger: None,
            sell_side: None,
            buy_side: Some(crate::models::BuySidePlan {
                total_to_invest: Some(75_000.0),
                buckets: vec![],
            }),
            reasoning_narrative: None,
        };

        // Call production resolve_poll_simulation with active simulation
        let merged = PortfolioHud::resolve_poll_simulation(
            Some(75_000.0),
            Some(Ok(simulated_plan.clone())),
            base_snap,
        );

        assert!(merged.is_ok());
        let snap = merged.unwrap();
        assert!(snap.rebalance_plan.is_some());
        assert_eq!(snap.rebalance_plan.as_ref().unwrap().plan_id, "test-sim-id");
        assert_eq!(snap.rebalance_plan.as_ref().unwrap().buy_side.as_ref().unwrap().total_to_invest, Some(75_000.0));
    }

    #[core::prelude::v1::test]
    fn test_poll_merge_fails_closed_when_resimulation_fails() {
        use crate::kmap::tests::mock_snapshot;
        let base_snap = mock_snapshot();

        // Call production resolve_poll_simulation when re-simulation returns an error
        let merged = PortfolioHud::resolve_poll_simulation(
            Some(50_000.0),
            Some(Err("timeout contacting quant engine".to_string())),
            base_snap,
        );

        // Must FAIL CLOSED: return Err rather than silently reverting to base snapshot
        assert!(merged.is_err(), "Must return Err when re-simulation fails");
        let err = merged.err().unwrap();
        assert!(err.contains("Re-simulation for ₹50000 failed during poll"));
        assert!(err.contains("timeout contacting quant engine"));
    }

    #[core::prelude::v1::test]
    fn test_poll_merge_preserves_base_snapshot_when_no_simulation_active() {
        use crate::kmap::tests::mock_snapshot;
        let base_snap = mock_snapshot();

        let merged = PortfolioHud::resolve_poll_simulation(
            None,
            None,
            base_snap,
        );

        assert!(merged.is_ok());
        let snap = merged.unwrap();
        assert!(snap.rebalance_plan.is_none());
    }

    #[core::prelude::v1::test]
    fn test_simulation_failure_does_not_corrupt_kmap_or_exemption_blast_radius() {
        use crate::kmap::tests::mock_snapshot;
        use crate::kmap::classify_snapshot;

        let base_snap = mock_snapshot();
        let sim_err = "Core Node timeout".to_string();

        // 1. Simulation failure gives an Err from resolve_poll_simulation
        let merged = PortfolioHud::resolve_poll_simulation(
            Some(50_000.0),
            Some(Err(sim_err.clone())),
            base_snap.clone(),
        );
        assert!(merged.is_err());

        // 2. Error blast radius isolation:
        // Even when simulation fails, base snapshot and statutory headroom are intact.
        // K-Map classifies cleanly without crashing or blanking.
        let kmap = classify_snapshot(&base_snap, 92_758.90);
        assert_eq!(kmap.cells.len(), 16);
        assert_eq!(kmap.statutory_exemption_room_before, 92_758.90);
    }
}
