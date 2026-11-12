// cockpit-gpui/src/views/equilibrium.rs

use gpui::*;
use crate::app::PortfolioHud;
use crate::theme::Theme;

impl PortfolioHud {
    pub fn render_top_bar(&self, theme: &Theme) -> impl IntoElement {
        let (nw_formatted, xirr, status_badge, badge_color) = if let Some(ref s) = self.snapshot {
            let (badge, color) = if s.sync_info.has_stale_nav == Some(true) {
                (format!("NAV STALE ({})", s.sync_info.stale_nav_count.unwrap_or(1)), theme.accent_amber)
            } else if let Some(ref hash) = s.sync_info.ledger_hash {
                let short = if hash.len() >= 8 { &hash[..8] } else { hash.as_str() };
                if s.sync_info.has_stale_nav.is_none() {
                    (format!("LEDGER {} (NAV UNKNOWN)", short), theme.text_secondary)
                } else {
                    (format!("LEDGER {}", short), theme.accent_emerald)
                }
            } else if s.sync_info.has_stale_nav.is_none() {
                ("UNKNOWN".to_string(), theme.text_secondary)
            } else {
                ("LEDGER UNKNOWN".to_string(), theme.text_secondary)
            };
            (
                s.sync_info.formatted_current_value.clone(),
                s.sync_info.xirr_percentage.clone(),
                badge,
                color,
            )
        } else if self.is_loading {
            ("₹ --,--,---".into(), "--%".into(), "CONNECTING...".into(), theme.text_secondary)
        } else {
            ("₹ --,--,---".into(), "--%".into(), "DISCONNECTED".into(), theme.accent_amber)
        };

        div()
            .flex()
            .flex_row()
            .h(px(56.0))
            .w_full()
            .px_6()
            .items_center()
            .justify_between()
            .border_b_1()
            .border_color(theme.border_subtle)
            .bg(theme.bg_surface)
            .child(
                div()
                    .flex()
                    .flex_row()
                    .gap_4()
                    .items_center()
                    .child(
                        div()
                            .text_xl()
                            .font_weight(FontWeight::BOLD)
                            .text_color(theme.accent_emerald)
                            .child(nw_formatted)
                    )
                    .child(
                        div()
                            .text_xs()
                            .px_2()
                            .py_0p5()
                            .rounded_sm()
                            .bg(theme.bg_base)
                            .text_color(theme.text_secondary)
                            .child(format!("XIRR: {xirr}"))
                    )
                    .child(
                        if let Some(ref status) = self.export_status {
                            div()
                                .text_xs()
                                .px_2()
                                .py_0p5()
                                .rounded_sm()
                                .bg(theme.accent_emerald.opacity(0.15))
                                .text_color(theme.accent_emerald)
                                .child(status.clone())
                        } else if let Some(ref err) = self.error_msg {
                            div()
                                .text_xs()
                                .px_2()
                                .py_0p5()
                                .rounded_sm()
                                .bg(theme.accent_amber.opacity(0.15))
                                .text_color(theme.accent_amber)
                                .max_w(px(380.0))
                                .truncate()
                                .child(err.clone())
                        } else {
                            div()
                        }
                    )
            )
            .child(
                div()
                    .flex()
                    .flex_row()
                    .gap_3()
                    .items_center()
                    .child(
                        div()
                            .text_xs()
                            .px_2()
                            .py_0p5()
                            .rounded_full()
                            .bg(badge_color.opacity(0.15))
                            .text_color(badge_color)
                            .child(status_badge)
                    )
            )
    }

    pub fn render_equilibrium_view(&self, theme: &Theme) -> impl IntoElement {
        let (banner_text, banner_border, banner_text_col, bucket_cards) = if let Some(ref snap) = self.snapshot {
            let total_nw = snap.holdings.iter().map(|h| h.current_value).sum::<f64>();
            let post_inflow_nw = (total_nw + self.pending_inflow_rupees).max(1.0);

            let breached = snap.bucket_allocation.iter().any(|b| b.is_drifted);

            // Extract simulated allocations from live RebalancePlanEngine if available
            let sim_allocations: std::collections::HashMap<String, f64> = snap.rebalance_plan.as_ref()
                .and_then(|p| p.buy_side.as_ref())
                .map(|b| {
                    b.buckets.iter().map(|bk| (bk.bucket.clone(), bk.amount_allocated.unwrap_or(0.0))).collect()
                })
                .unwrap_or_default();

            // Compute fallback underweight rupee deficits if no simulation plan is active
            let deficits: Vec<f64> = snap.bucket_allocation.iter().map(|b| {
                let target_val = (b.target_pct / 100.0) * post_inflow_nw;
                let current_val = (b.current_pct / 100.0) * total_nw;
                (target_val - current_val).max(0.0)
            }).collect();
            let total_deficit = deficits.iter().sum::<f64>().max(1.0);

            let cards: Vec<_> = snap.bucket_allocation.iter().zip(deficits).map(|(b, deficit)| {
                let allocated = if let Some(amt) = sim_allocations.get(&b.bucket) {
                    *amt
                } else {
                    (deficit / total_deficit) * self.pending_inflow_rupees
                };
                (
                    b.bucket.clone(),
                    b.drift_pct,
                    b.is_drifted,
                    format!("₹ {:.0}", allocated),
                )
            }).collect();

            let (b_text, b_border, b_text_col) = if let Some(ref sim_err) = self.simulation_error {
                (
                    format!("⚠ INFLOW SIMULATION FAILED: {} — SHOWING BASE PORTFOLIO", sim_err),
                    theme.accent_amber.opacity(0.4),
                    theme.accent_amber,
                )
            } else if breached {
                (
                    "⚠ POLICY DRIFT DETECTED — REBALANCE ACTION RECOMMENDED IN K-MAP".to_string(),
                    theme.accent_amber.opacity(0.4),
                    theme.accent_amber,
                )
            } else {
                (
                    "● ALL BUCKETS WITHIN 5/25 DRIFT BAND — ZERO REBALANCE FRICTION".to_string(),
                    theme.accent_emerald.opacity(0.3),
                    theme.accent_emerald,
                )
            };
            (b_text, b_border, b_text_col, cards)
        } else if self.is_loading {
            (
                "◌ CONNECTING TO CORE NODE — POLLING SNAPSHOT...".to_string(),
                theme.border_subtle,
                theme.text_secondary,
                vec![],
            )
        } else {
            (
                "⚠ CORE NODE DISCONNECTED — VALUATION & REBALANCE STATUS UNKNOWN".to_string(),
                theme.accent_amber.opacity(0.4),
                theme.accent_amber,
                vec![],
            )
        };

        div()
            .flex()
            .flex_col()
            .gap_4()
            .size_full()
            .child(
                // Stealth Equilibrium Banner
                div()
                    .w_full()
                    .py_2()
                    .px_4()
                    .rounded_md()
                    .bg(theme.bg_surface)
                    .border_1()
                    .border_color(banner_border)
                    .flex()
                    .flex_row()
                    .justify_center()
                    .items_center()
                    .text_xs()
                    .font_weight(FontWeight::MEDIUM)
                    .text_color(banner_text_col)
                    .child(banner_text)
            )
            .child(
                // 4-Bucket Capped Water-Filling Grid
                div()
                    .grid()
                    .grid_cols(2)
                    .gap_4()
                    .children(bucket_cards.into_iter().map(|(name, drift, is_drifted, routing)| {
                        let card_border = if is_drifted {
                            theme.accent_amber.opacity(0.4)
                        } else {
                            theme.border_subtle
                        };

                        div()
                            .flex()
                            .flex_col()
                            .p_4()
                            .rounded_md()
                            .bg(theme.bg_surface)
                            .border_1()
                            .border_color(card_border)
                            .child(
                                div().flex().flex_row().justify_between().child(
                                    div().text_sm().font_weight(FontWeight::BOLD).child(name)
                                ).child(
                                    div().text_xs().text_color(if is_drifted { theme.accent_amber } else { theme.text_secondary }).child(
                                        format!("{:+.1}% drift", drift)
                                    )
                                )
                            )
                            .child(
                                div().flex().flex_row().justify_between().mt_2().child(
                                    div().text_xs().text_color(theme.text_secondary).child(
                                        format!("SIP Inflow Routing (₹{:.0}k total):", self.pending_inflow_rupees / 1000.0)
                                    )
                                ).child(
                                    div().text_sm().font_weight(FontWeight::BOLD).text_color(theme.accent_emerald).child(routing)
                                )
                            )
                    }))
            )
    }

    pub fn render_palette_overlay(&self, theme: &Theme) -> impl IntoElement {
        div()
            .absolute()
            .inset_0()
            .bg(theme.bg_base.opacity(0.85))
            .flex()
            .items_center()
            .justify_center()
            .child(
                div()
                    .w(px(540.0))
                    .p_6()
                    .rounded_lg()
                    .bg(theme.bg_surface)
                    .border_1()
                    .border_color(theme.accent_emerald.opacity(0.5))
                    .flex()
                    .flex_col()
                    .gap_4()
                    .child(
                        div()
                            .flex()
                            .flex_row()
                            .justify_between()
                            .items_center()
                            .child(
                                div().text_sm().font_weight(FontWeight::BOLD).text_color(theme.accent_emerald).child(
                                    "COMMAND PALETTE / MODAL CHORDS"
                                )
                            )
                            .child(
                                div().text_xs().text_color(theme.text_secondary).child("[Esc] Close")
                            )
                    )
                    .child(
                        div()
                            .flex()
                            .flex_col()
                            .gap_2()
                            .child(self.palette_row("[Space+k]", "Toggle 4-Bit Karnaugh Map Grid", "Tax friction reduction matrix", theme))
                            .child(self.palette_row("[Space+h]", "Tax Harvest Isolation View", "Isolate m15 Prime + m7 March lots", theme))
                            .child(self.palette_row("[Space+i]", "Adjust Inflow Rung (Live Simulation)", "Cycle & simulate capped water-filling", theme))
                            .child(self.palette_row("[Space+e]", "Export ITR-2 / Schedule CG Zip", "Save official archive to ~/Downloads", theme))
                            .child(self.palette_row("[r]", "Force Refresh Snapshot", "Poll NAVs & ledger from Core Node", theme))
                            .child(self.palette_row("[q] / [Esc]", "Back / Quit HUD", "Return to equilibrium or exit", theme))
                    )
            )
    }

    fn palette_row(&self, chord: &str, title: &str, desc: &str, theme: &Theme) -> impl IntoElement {
        div()
            .flex()
            .flex_row()
            .justify_between()
            .items_center()
            .p_2()
            .rounded_sm()
            .bg(theme.bg_base)
            .border_1()
            .border_color(theme.border_subtle)
            .child(
                div().flex().flex_col().child(
                    div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.text_primary).child(title.to_string())
                ).child(
                    div().text_xs().text_color(theme.text_secondary.opacity(0.7)).child(desc.to_string())
                )
            )
            .child(
                div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_amber).child(chord.to_string())
            )
    }

    pub fn render_footer_chord_bar(&self, theme: &Theme) -> impl IntoElement {
        div()
            .flex()
            .flex_row()
            .h(px(32.0))
            .w_full()
            .px_4()
            .items_center()
            .justify_between()
            .bg(theme.bg_surface)
            .border_t_1()
            .border_color(theme.border_subtle)
            .text_xs()
            .text_color(theme.text_secondary)
            .child(
                div()
                    .flex()
                    .flex_row()
                    .gap_4()
                    .child("[Space+k] 4x4 K-Map")
                    .child("[Space+h] 112A Harvest")
                    .child("[Space+i] Adjust Inflow")
                    .child("[Space+e] ITR-2 Export")
                    .child("[Space+c] Palette")
                    .child("[Esc] Back / Quit")
            )
            .child("144Hz Vulkan | Wayland")
    }
}
