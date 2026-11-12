// cockpit-gpui/src/views/equilibrium.rs

use gpui::*;
use crate::app::{ActiveView, PortfolioHud};
use crate::models::FundAllocation;
use crate::theme::Theme;

impl PortfolioHud {
    pub fn render_top_bar(&self, theme: &Theme) -> impl IntoElement {
        let (nw_formatted, xirr, status_badge, badge_color, invested_str, gain_str) = if let Some(ref s) = self.snapshot {
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
                s.sync_info.formatted_total_invested.clone(),
                s.sync_info.formatted_unrealized_gain.clone(),
            )
        } else if self.is_loading {
            ("₹ --,--,---".into(), "--%".into(), "CONNECTING...".into(), theme.text_secondary, "₹ --,--,---".into(), "₹ --,--,---".into())
        } else {
            ("₹ --,--,---".into(), "--%".into(), "DISCONNECTED".into(), theme.accent_amber, "₹ --,--,---".into(), "₹ --,--,---".into())
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
                    .gap_3()
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
                        div()
                            .text_xs()
                            .px_2()
                            .py_0p5()
                            .rounded_sm()
                            .bg(theme.bg_base)
                            .text_color(theme.text_secondary)
                            .child(format!("Invested: {invested_str}"))
                    )
                    .child(
                        div()
                            .text_xs()
                            .px_2()
                            .py_0p5()
                            .rounded_sm()
                            .bg(theme.accent_emerald.opacity(0.12))
                            .text_color(theme.accent_emerald)
                            .child(format!("Gain: {gain_str}"))
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
        let (banner_text, banner_border, banner_text_col, bucket_cards, brief_headline, brief_verified, drawdown_status, radar_count) = if let Some(ref snap) = self.snapshot {
            let total_nw = snap.holdings.iter().map(|h| h.current_value).sum::<f64>();
            let post_inflow_nw = (total_nw + self.pending_inflow_rupees).max(1.0);

            let breached = snap.bucket_allocation.iter().any(|b| b.is_drifted);

            // Extract simulated allocations and fund breakdowns from RebalancePlanEngine
            let (sim_allocations, fund_breakdowns): (
                std::collections::HashMap<String, f64>,
                std::collections::HashMap<String, Vec<FundAllocation>>,
            ) = if let Some(ref p) = snap.rebalance_plan {
                if let Some(ref b) = p.buy_side {
                    let mut allocs = std::collections::HashMap::new();
                    let mut funds = std::collections::HashMap::new();
                    for bk in &b.buckets {
                        allocs.insert(bk.bucket.clone(), bk.amount_allocated.unwrap_or(0.0));
                        funds.insert(bk.bucket.clone(), bk.fund_breakdown.clone());
                    }
                    (allocs, funds)
                } else {
                    (Default::default(), Default::default())
                }
            } else {
                (Default::default(), Default::default())
            };

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
                let funds = fund_breakdowns.get(&b.bucket).cloned().unwrap_or_default();
                (
                    b.bucket.clone(),
                    b.current_pct,
                    b.target_pct,
                    b.drift_pct,
                    b.is_drifted,
                    format!("₹ {:.0}", allocated),
                    funds,
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

            let (headline, verified) = snap.daily_brief.as_ref()
                .and_then(|db| db.headline.as_ref().map(|h| (h.clone(), db.verified)))
                .unwrap_or_else(|| ("All quantitative telemetry nominal — equilibrium maintained.".to_string(), true));
            let drawdown = snap.sync_info.drawdown_status.clone().unwrap_or_else(|| "0.0%".to_string());
            let r_count = snap.radar_signals.len();

            (b_text, b_border, b_text_col, cards, headline, verified, drawdown, r_count)
        } else if self.is_loading {
            (
                "◌ CONNECTING TO CORE NODE — POLLING SNAPSHOT...".to_string(),
                theme.border_subtle,
                theme.text_secondary,
                vec![],
                "Connecting to Core Node...".to_string(),
                false,
                "--".to_string(),
                0,
            )
        } else {
            (
                "⚠ CORE NODE DISCONNECTED — VALUATION & REBALANCE STATUS UNKNOWN".to_string(),
                theme.accent_amber.opacity(0.4),
                theme.accent_amber,
                vec![],
                "Core Node disconnected.".to_string(),
                false,
                "--".to_string(),
                0,
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
                    .children(bucket_cards.into_iter().map(|(name, current_pct, target_pct, drift, is_drifted, routing, funds)| {
                        let bucket_color = theme.bucket_accent(&name);
                        let card_border = if is_drifted {
                            theme.accent_amber.opacity(0.5)
                        } else {
                            theme.border_subtle
                        };

                        let curr_frac = (current_pct / 100.0).clamp(0.0, 1.0) as f32;

                        div()
                            .flex()
                            .flex_col()
                            .p_4()
                            .rounded_md()
                            .bg(theme.bg_surface)
                            .border_1()
                            .border_color(card_border)
                            .border_t_2()
                            .child(
                                div().flex().flex_row().justify_between().items_center().child(
                                    div().flex().flex_row().gap_2().items_center().child(
                                        div()
                                            .w(px(8.0))
                                            .h(px(8.0))
                                            .rounded_full()
                                            .bg(bucket_color)
                                    ).child(
                                        div().text_sm().font_weight(FontWeight::BOLD).text_color(theme.text_primary).child(name)
                                    )
                                ).child(
                                    div().text_xs().font_weight(FontWeight::MEDIUM).text_color(if is_drifted { theme.accent_amber } else { theme.text_secondary }).child(
                                        format!("{:+.1}% drift", drift)
                                    )
                                )
                            )
                            .child(
                                // Dual-Track Allocation Progress Bar
                                div()
                                    .flex()
                                    .flex_col()
                                    .gap_1()
                                    .mt_3()
                                    .child(
                                        div().flex().flex_row().justify_between().text_xs().text_color(theme.text_secondary).child(
                                            format!("Allocation: {:.1}% (Target: {:.1}%)", current_pct, target_pct)
                                        ).child(
                                            if is_drifted {
                                                div().text_xs().text_color(theme.accent_amber).child("DRIFT EXCEEDED")
                                            } else {
                                                div().text_xs().text_color(theme.accent_emerald).child("ALIGNED")
                                            }
                                        )
                                    )
                                    .child(
                                        div()
                                            .w_full()
                                            .h(px(6.0))
                                            .rounded_full()
                                            .bg(theme.bg_base)
                                            .relative()
                                            .child(
                                                div()
                                                    .h_full()
                                                    .rounded_full()
                                                    .bg(bucket_color)
                                                    .w(relative(curr_frac))
                                            )
                                    )
                            )
                            .child(
                                div().flex().flex_row().justify_between().items_center().mt_3().child(
                                    div().text_xs().text_color(theme.text_secondary).child(
                                        format!("SIP Inflow Routing (₹{:.0}k total):", self.pending_inflow_rupees / 1000.0)
                                    )
                                ).child(
                                    div().text_sm().font_weight(FontWeight::BOLD).text_color(theme.accent_emerald).child(routing)
                                )
                            )
                            .child(
                                // Fund Routing Pills
                                if !funds.is_empty() {
                                    div()
                                        .flex()
                                        .flex_row()
                                        .flex_wrap()
                                        .gap_1p5()
                                        .mt_2()
                                        .children(funds.into_iter().map(|f| {
                                            let amt_str = f.amount.map(|a| format!(" → ₹{:.0}", a)).unwrap_or_default();
                                            div()
                                                .text_xs()
                                                .px_2()
                                                .py_0p5()
                                                .rounded_sm()
                                                .bg(theme.bg_base)
                                                .text_color(theme.text_secondary)
                                                .child(format!("{}{}", f.fund_name, amt_str))
                                        }))
                                } else {
                                    div()
                                }
                            )
                    }))
            )
            .child(
                // Daily Brief & ATH Drawdown Strip
                div()
                    .w_full()
                    .p_3()
                    .rounded_md()
                    .bg(theme.bg_surface)
                    .border_1()
                    .border_color(theme.border_subtle)
                    .flex()
                    .flex_row()
                    .justify_between()
                    .items_center()
                    .child(
                        div()
                            .flex()
                            .flex_row()
                            .gap_3()
                            .items_center()
                            .child(
                                div()
                                    .text_xs()
                                    .font_weight(FontWeight::BOLD)
                                    .px_2()
                                    .py_0p5()
                                    .rounded_sm()
                                    .bg(if brief_verified { theme.accent_emerald.opacity(0.15) } else { theme.accent_amber.opacity(0.15) })
                                    .text_color(if brief_verified { theme.accent_emerald } else { theme.accent_amber })
                                    .child(if brief_verified { "● BRIEF VERIFIED" } else { "◐ DAILY BRIEF" })
                            )
                            .child(
                                div()
                                    .text_xs()
                                    .text_color(theme.text_primary)
                                    .child(brief_headline)
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
                                    .rounded_sm()
                                    .bg(theme.bg_base)
                                    .text_color(theme.text_secondary)
                                    .child(format!("ATH Drawdown: {drawdown_status}"))
                            )
                            .child(
                                div()
                                    .text_xs()
                                    .px_2()
                                    .py_0p5()
                                    .rounded_sm()
                                    .bg(if radar_count > 0 { theme.accent_cyan.opacity(0.15) } else { theme.bg_base })
                                    .text_color(if radar_count > 0 { theme.accent_cyan } else { theme.text_secondary })
                                    .child(format!("Radar: {radar_count} active"))
                            )
                    )
            )
    }

    pub fn render_palette_overlay(&self, theme: &Theme, cx: &mut Context<Self>) -> impl IntoElement {
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
                                div()
                                    .text_xs()
                                    .text_color(theme.text_secondary)
                                    .cursor_pointer()
                                    .on_mouse_down(MouseButton::Left, cx.listener(|this, _, _, cx| {
                                        this.toggle_palette(cx);
                                    }))
                                    .child("[Esc] Close")
                            )
                    )
                    .child(
                        div()
                            .flex()
                            .flex_col()
                            .gap_2()
                            .child(self.palette_interactive_row(
                                "[Space+k]",
                                "Toggle 4-Bit Karnaugh Map Grid",
                                "Tax friction reduction matrix",
                                theme,
                                cx.listener(|this, _, _, cx| {
                                    this.set_view(ActiveView::KMapMatrix, cx);
                                })
                            ))
                            .child(self.palette_interactive_row(
                                "[Space+h]",
                                "Tax Harvest Isolation View",
                                "Isolate m15 Prime + m7 March lots",
                                theme,
                                cx.listener(|this, _, _, cx| {
                                    this.set_view(ActiveView::HarvestIsolation, cx);
                                })
                            ))
                            .child(self.palette_interactive_row(
                                "[Space+i]",
                                "Adjust Inflow Rung (Live Simulation)",
                                "Cycle & simulate capped water-filling",
                                theme,
                                cx.listener(|this, _, _, cx| {
                                    this.adjust_inflow(cx);
                                })
                            ))
                            .child(self.palette_interactive_row(
                                "[Space+e]",
                                "Export ITR-2 / Schedule CG Zip",
                                "Save official archive to ~/Downloads",
                                theme,
                                cx.listener(|this, _, _, cx| {
                                    this.export_itr2(cx);
                                })
                            ))
                            .child(self.palette_interactive_row(
                                "[r]",
                                "Force Refresh Snapshot",
                                "Poll NAVs & ledger from Core Node",
                                theme,
                                cx.listener(|this, _, _, cx| {
                                    this.fetch_snapshot(cx);
                                })
                            ))
                            .child(self.palette_interactive_row(
                                "[q] / [Esc]",
                                "Back / Quit HUD",
                                "Return to equilibrium or exit",
                                theme,
                                cx.listener(|this, _, _, cx| {
                                    this.toggle_palette(cx);
                                })
                            ))
                    )
            )
    }

    fn palette_interactive_row(
        &self,
        chord: &str,
        title: &str,
        desc: &str,
        theme: &Theme,
        on_click: impl Fn(&MouseDownEvent, &mut Window, &mut App) + 'static,
    ) -> impl IntoElement {
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
            .cursor_pointer()
            .on_mouse_down(MouseButton::Left, on_click)
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
