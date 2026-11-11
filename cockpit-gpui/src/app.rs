// cockpit-gpui/src/app.rs

use gpui::*;
use crate::models::Snapshot;
use crate::kmap::{classify_snapshot, KMapMatrix};
use crate::theme::Theme;

actions!(portfolio, [
    ToggleKMap,
    TriggerInflowModal,
    IsolateHarvest,
    ExportItr2,
    RefreshSnapshot,
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
    pub kmap: Option<KMapMatrix>,
    pub active_view: ActiveView,
    pub is_loading: bool,
    pub error_msg: Option<String>,
    pub pending_inflow_rupees: f64,
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
        let candidates = ["tui/tui_config.yaml", "../tui/tui_config.yaml", "tui_config.yaml"];
        for path in &candidates {
            if let Ok(text) = std::fs::read_to_string(path) {
                for line in text.lines() {
                    let line = line.trim();
                    if line.starts_with('#') {
                        continue;
                    }
                    if let Some((k, v)) = line.split_once(':') {
                        if k.trim() == "api_token_env" {
                            let env_name = v.trim().trim_matches('"').trim_matches('\'');
                            if let Ok(val) = std::env::var(env_name) {
                                if !val.trim().is_empty() {
                                    token = Some(val.trim().to_string());
                                    break;
                                }
                            }
                        }
                    }
                }
                if token.is_some() {
                    break;
                }
            }
        }
    }

    (base_url, token)
}

impl PortfolioHud {
    pub fn new(window: &mut Window, cx: &mut Context<Self>) -> Self {
        let focus_handle = cx.focus_handle();
        // Immediately acquire keyboard focus from the active window
        window.focus(&focus_handle);

        // Schedule asynchronous snapshot poll on smol runtime
        cx.spawn(async move |this, cx| {
            this.update(cx, |hud, cx| hud.fetch_snapshot(cx)).ok();
        }).detach();

        Self {
            focus_handle,
            snapshot: None,
            kmap: None,
            active_view: ActiveView::Equilibrium,
            is_loading: true,
            error_msg: None,
            pending_inflow_rupees: 50_000.0, // Default monthly SIP allocation
        }
    }

    pub fn fetch_snapshot(&mut self, cx: &mut Context<Self>) {
        self.is_loading = true;
        cx.notify();

        cx.spawn(async move |this, cx| {
            let res = smol::unblock(|| {
                let (base_url, token) = get_api_config();
                let url = format!("{}/api/v1/sync/snapshot", base_url.trim_end_matches('/'));
                let mut req = ureq::get(&url).timeout(std::time::Duration::from_millis(1500));
                if let Some(ref tok) = token {
                    req = req.set("X-Api-Auth-Token", tok);
                }
                req.call()
                    .map_err(|e| e.to_string())?
                    .into_json::<Snapshot>()
                    .map_err(|e| e.to_string())
            }).await;

            this.update(cx, |hud, cx| {
                hud.is_loading = false;
                match res {
                    Ok(snap) => {
                        hud.kmap = Some(classify_snapshot(&snap, 25_000.0));
                        hud.snapshot = Some(snap);
                        hud.error_msg = None;
                    }
                    Err(err) => {
                        hud.error_msg = Some(format!("Core Node unreachable: {err}"));
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
                this.active_view = ActiveView::HarvestIsolation;
                cx.notify();
            }))
            .on_action(cx.listener(|this, _: &TriggerInflowModal, _window, cx| {
                // Cycle SIP inflow test rungs: ₹25k -> ₹50k -> ₹100k
                this.pending_inflow_rupees = match this.pending_inflow_rupees as u64 {
                    25_000 => 50_000.0,
                    50_000 => 100_000.0,
                    _ => 25_000.0,
                };
                cx.notify();
            }))
            .on_action(cx.listener(|_, _: &ExportItr2, _window, cx| {
                cx.spawn(async move |this, cx| {
                    let _ = smol::unblock(|| {
                        let (base_url, token) = get_api_config();
                        let url = format!("{}/api/v1/tax/export/itr2/zip", base_url.trim_end_matches('/'));
                        let mut req = ureq::get(&url).timeout(std::time::Duration::from_secs(5));
                        if let Some(ref tok) = token {
                            req = req.set("X-Api-Auth-Token", tok);
                        }
                        req.call()
                    }).await;
                    this.update(cx, |_, cx| cx.notify()).ok();
                }).detach();
            }))
            .on_action(cx.listener(|this, _: &RefreshSnapshot, _window, cx| {
                this.fetch_snapshot(cx);
            }))
            .on_action(cx.listener(|_, _: &Quit, _window, cx| {
                cx.quit();
            }))
            .child(self.render_top_bar(&theme))
            .child(
                div()
                    .flex_1()
                    .p_4()
                    .child(match self.active_view {
                        ActiveView::Equilibrium => self.render_equilibrium_view(&theme).into_any_element(),
                        ActiveView::KMapMatrix | ActiveView::HarvestIsolation => {
                            self.render_kmap_view(&theme).into_any_element()
                        }
                    })
            )
            .child(self.render_footer_chord_bar(&theme))
    }
}
