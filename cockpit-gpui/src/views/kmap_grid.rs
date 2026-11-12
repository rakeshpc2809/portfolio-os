// cockpit-gpui/src/views/kmap_grid.rs

use gpui::*;
use crate::app::{ActiveView, PortfolioHud};
use crate::theme::Theme;

impl PortfolioHud {
    pub fn render_kmap_view(&self, theme: &Theme, cx: &mut Context<Self>) -> impl IntoElement {
        let kmap = match &self.kmap {
            Some(m) => m,
            None => {
                let msg = match &self.exemption_status {
                    Err(e) => format!("Statutory exemption status unavailable: {e}"),
                    Ok(_) => "Computing K-Map reduction...".to_string(),
                };
                return div().p_6().rounded_md().bg(theme.bg_surface).border_1().border_color(theme.accent_amber.opacity(0.4)).text_xs().text_color(theme.accent_amber).child(msg).into_any_element();
            }
        };

        let statutory_room = kmap.statutory_exemption_room_before;
        let post_harvest_room = kmap.projected_harvest_room_remaining;
        let room_used = (statutory_room - post_harvest_room).max(0.0);
        let util_frac = if statutory_room > 0.0 {
            (room_used / statutory_room).clamp(0.0, 1.0) as f32
        } else {
            0.0
        };
        let util_pct = util_frac * 100.0;

        div()
            .flex()
            .flex_col()
            .size_full()
            .gap_3()
            .child(
                // Header with Statutory 112A Utilization Meter
                div()
                    .flex()
                    .flex_row()
                    .justify_between()
                    .items_center()
                    .child(
                        div().text_sm().font_weight(FontWeight::BOLD).child(
                            "4-BIT KARNAUGH MAP (TAX LOT FRICTION REDUCTION)"
                        )
                    )
                    .child(
                        div()
                            .flex()
                            .flex_col()
                            .gap_1()
                            .items_end()
                            .child(
                                div().flex().flex_row().gap_3().items_center().child(
                                    div().text_xs().text_color(theme.text_secondary).child("Sec 112A Headroom:")
                                ).child(
                                    div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_cyan).child(
                                        format!("₹ {:.0} / ₹ {:.0} ({:.1}% Used)", room_used, statutory_room, util_pct)
                                    )
                                ).child(
                                    div().text_xs().text_color(theme.accent_emerald).child(
                                        format!("Remaining: ₹ {:.0}", post_harvest_room)
                                    )
                                ).child(
                                    div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_amber).child(
                                        format!("Prime: ₹ {:.0}", kmap.prime_implicant_liquidable_value)
                                    )
                                )
                            )
                            .child(
                                // Dual-Track Headroom Utilization Meter
                                div()
                                    .w(px(280.0))
                                    .h(px(6.0))
                                    .rounded_full()
                                    .bg(theme.bg_base)
                                    .border_1()
                                    .border_color(theme.border_subtle)
                                    .relative()
                                    .child(
                                        div()
                                            .h_full()
                                            .rounded_full()
                                            .bg(theme.accent_cyan)
                                            .w(relative(util_frac))
                                    )
                            )
                    )
            )
            .child(
                // 4x4 Gray-Code Matrix with Perimeter Column & Row Axis Headers
                div()
                    .flex()
                    .flex_col()
                    .gap_2()
                    .p_3()
                    .rounded_md()
                    .bg(theme.bg_surface)
                    .border_1()
                    .border_color(theme.border_subtle)
                    .child(
                        // Top Perimeter Column Axis Headers
                        div()
                            .flex()
                            .flex_row()
                            .w_full()
                            .gap_2()
                            .child(
                                // Corner spacer for Row Headers
                                div()
                                    .w(px(140.0))
                                    .p_2()
                                    .rounded_sm()
                                    .bg(theme.bg_base)
                                    .border_1()
                                    .border_color(theme.border_subtle)
                                    .flex()
                                    .flex_col()
                                    .items_center()
                                    .justify_center()
                                    .child(
                                        div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_violet).child("AB \\ CD")
                                    )
                                    .child(
                                        div().text_xs().text_color(theme.text_secondary.opacity(0.6)).child("Row \\ Col")
                                    )
                            )
                            .child(
                                // 4 Column Headers
                                div()
                                    .flex_1()
                                    .grid()
                                    .grid_cols(4)
                                    .gap_2()
                                    .child(self.col_header("CD = 00", "Locked · STCG", "Exit locked, non-112A", theme.text_secondary, theme))
                                    .child(self.col_header("CD = 01", "Locked · 112A", "Exit locked, room avail", theme.accent_amber, theme))
                                    .child(self.col_header("CD = 11", "Free · Actionable", "Exit free 112A room", theme.accent_emerald, theme))
                                    .child(self.col_header("CD = 10", "Free · Exhausted", "Room exhausted / STCG", theme.accent_violet, theme))
                            )
                    )
                    .children((0..4).map(|r| {
                        let (ab_title, ab_sub, ab_color) = match r {
                            0 => ("AB = 00", "Eq · STCG", theme.text_secondary),
                            1 => ("AB = 01", "Eq · LTCG", theme.accent_cyan),
                            2 => ("AB = 11", "Breached · LTCG", theme.accent_emerald),
                            3 => ("AB = 10", "Breached · STCG", theme.accent_amber),
                            _ => ("AB = ??", "", theme.text_secondary),
                        };

                        div()
                            .flex()
                            .flex_row()
                            .w_full()
                            .gap_2()
                            .child(
                                // Row Header
                                div()
                                    .w(px(140.0))
                                    .h(px(112.0))
                                    .p_2()
                                    .rounded_sm()
                                    .bg(theme.bg_base)
                                    .border_1()
                                    .border_color(theme.border_subtle)
                                    .flex()
                                    .flex_col()
                                    .justify_center()
                                    .gap_1()
                                    .child(
                                        div().text_xs().font_weight(FontWeight::BOLD).text_color(ab_color).child(ab_title)
                                    )
                                    .child(
                                        div().text_xs().text_color(theme.text_secondary).child(ab_sub)
                                    )
                            )
                            .child(
                                // 4 Cells for this row
                                div()
                                    .flex_1()
                                    .grid()
                                    .grid_cols(4)
                                    .gap_2()
                                    .children((0..4).map(|c| {
                                        let cell_idx = r * 4 + c;
                                        let cell = &kmap.cells[cell_idx];
                                        let is_prime = cell.is_prime_implicant();
                                        let is_harvest = cell.is_march_harvest();
                                        let is_dont_care = cell.is_dont_care();

                                        let (border_col, bg_col, label_col) = if is_prime {
                                            (theme.accent_emerald, theme.accent_emerald.opacity(0.14), theme.accent_emerald)
                                        } else if is_harvest {
                                            (theme.accent_cyan, theme.accent_cyan.opacity(0.12), theme.accent_cyan)
                                        } else if cell.minterm == 0b0101 || cell.minterm == 0b1101 {
                                            (theme.accent_amber.opacity(0.6), theme.accent_amber.opacity(0.08), theme.accent_amber)
                                        } else if cell.minterm == 0b0110 || cell.minterm == 0b1110 {
                                            (theme.accent_violet.opacity(0.6), theme.accent_violet.opacity(0.08), theme.accent_violet)
                                        } else if is_dont_care {
                                            (theme.border_subtle.opacity(0.3), theme.bg_base.opacity(0.4), theme.text_secondary.opacity(0.4))
                                        } else {
                                            (theme.border_subtle, theme.bg_base, theme.text_secondary)
                                        };

                                        let badge_element = if is_prime {
                                            div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_emerald).child("⚡ PRIME ACTION ↗")
                                        } else if is_harvest {
                                            div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_cyan).child("🌱 112A HARVEST ↗")
                                        } else if is_dont_care {
                                            div().text_xs().text_color(theme.text_secondary.opacity(0.3)).child("[X] DON'T CARE")
                                        } else if cell.minterm == 0b1101 {
                                            div().text_xs().text_color(theme.accent_amber).child("⏳ EXIT LOCKED (M13)")
                                        } else if cell.minterm == 0b0101 {
                                            div().text_xs().text_color(theme.accent_amber).child("⏳ EXIT LOCKED (M5)")
                                        } else if cell.minterm == 0b1110 {
                                            div().text_xs().text_color(theme.accent_violet).child("🛑 ROOM EXHAUSTED (M14)")
                                        } else if cell.minterm == 0b0110 {
                                            div().text_xs().text_color(theme.accent_violet).child("🛑 ROOM EXHAUSTED (M6)")
                                        } else {
                                            div().text_xs().text_color(theme.text_secondary.opacity(0.5)).child("INACTIVE")
                                        };

                                        let is_interactive = is_prime || is_harvest;
                                        let base_div = div()
                                            .flex()
                                            .flex_col()
                                            .h(px(112.0))
                                            .p_2()
                                            .rounded_sm()
                                            .bg(bg_col)
                                            .border_1()
                                            .border_color(border_col)
                                            .justify_between()
                                            .child(
                                                div().flex().flex_row().justify_between().child(
                                                    div().text_xs().font_weight(FontWeight::BOLD).text_color(label_col).child(
                                                        format!("{:04b} ({:02b}|{:02b})", cell.minterm, cell.row_bits, cell.col_bits)
                                                    )
                                                ).child(
                                                    div().text_xs().text_color(theme.text_secondary).child(
                                                        if is_dont_care {
                                                            "X (Don't Care)".to_string()
                                                        } else {
                                                            format!("{} lots", cell.lots.len())
                                                        }
                                                    )
                                                )
                                            )
                                            .child(
                                                if is_dont_care {
                                                    div().flex().flex_col().child(
                                                        div().text_xs().text_color(theme.text_secondary.opacity(0.4)).child("STCG (No 112A)")
                                                    )
                                                } else {
                                                    let gross_gain: f64 = cell.lots.iter().filter(|l| l.unrealized_gain > 0.0).map(|l| l.unrealized_gain).sum();
                                                    div().flex().flex_col().child(
                                                        div().text_xs().font_weight(FontWeight::BOLD).child(
                                                            format!("₹ {:.0}", cell.total_value)
                                                        )
                                                    ).child(
                                                        div().text_xs().text_color(theme.text_secondary).child(
                                                            if (gross_gain - cell.total_gain).abs() > 1.0 && (is_prime || is_harvest) {
                                                                format!("Net: ₹ {:.0} (Gross: ₹ {:.0})", cell.total_gain, gross_gain)
                                                            } else {
                                                                format!("Gain: ₹ {:.0}", cell.total_gain)
                                                            }
                                                        )
                                                    )
                                                }
                                            )
                                            .child(badge_element);

                                        if is_interactive {
                                            base_div
                                                .cursor_pointer()
                                                .on_mouse_down(MouseButton::Left, cx.listener(|this, _, _, cx| {
                                                    this.set_view(ActiveView::HarvestIsolation, cx);
                                                }))
                                        } else {
                                            base_div
                                        }
                                    }))
                            )
                    }))
            )
            .into_any_element()
    }

    fn col_header(
        &self,
        cd_code: &str,
        title: &str,
        sub: &str,
        accent: Hsla,
        theme: &Theme,
    ) -> impl IntoElement {
        div()
            .flex()
            .flex_col()
            .p_2()
            .rounded_sm()
            .bg(theme.bg_base)
            .border_1()
            .border_color(theme.border_subtle)
            .justify_between()
            .gap_1()
            .child(
                div().text_xs().font_weight(FontWeight::BOLD).text_color(accent).child(cd_code.to_string())
            )
            .child(
                div().text_xs().font_weight(FontWeight::MEDIUM).text_color(theme.text_primary).child(title.to_string())
            )
            .child(
                div().text_xs().text_color(theme.text_secondary.opacity(0.7)).child(sub.to_string())
            )
    }

    pub fn render_harvest_isolation_view(&self, theme: &Theme) -> impl IntoElement {
        let kmap = match &self.kmap {
            Some(m) => m,
            None => {
                let msg = match &self.exemption_status {
                    Err(e) => format!("Statutory exemption status unavailable: {e}"),
                    Ok(_) => "Computing Harvest Isolation...".to_string(),
                };
                return div().p_6().rounded_md().bg(theme.bg_surface).border_1().border_color(theme.accent_amber.opacity(0.4)).text_xs().text_color(theme.accent_amber).child(msg).into_any_element();
            }
        };

        let prime_cell = kmap.cells.iter().find(|c| c.is_prime_implicant());
        let march_cell = kmap.cells.iter().find(|c| c.is_march_harvest());

        let prime_lots = prime_cell.map(|c| c.lots.as_slice()).unwrap_or(&[]);
        let march_lots = march_cell.map(|c| c.lots.as_slice()).unwrap_or(&[]);

        let _total_harvest_val = kmap.prime_implicant_liquidable_value + kmap.march_harvest_liquidable_value;
        let total_harvest_gain: f64 = prime_lots.iter().chain(march_lots.iter()).map(|l| l.unrealized_gain).sum();

        div()
            .flex()
            .flex_col()
            .size_full()
            .gap_4()
            .child(
                div()
                    .flex()
                    .flex_row()
                    .justify_between()
                    .items_center()
                    .child(
                        div().text_sm().font_weight(FontWeight::BOLD).text_color(theme.accent_emerald).child(
                            "SECTION 112A TAX HARVEST ISOLATION (m15 PRIME + m7 MARCH HARVEST)"
                        )
                    )
                    .child(
                        div().flex().flex_row().gap_4().items_center().child(
                            div().text_xs().text_color(theme.text_secondary).child(
                                format!("Statutory Room: ₹ {:.0}", kmap.statutory_exemption_room_before)
                            )
                        ).child(
                            div().text_xs().text_color(theme.accent_emerald).child(
                                format!("Post-Harvest Headroom: ₹ {:.0}", kmap.projected_harvest_room_remaining)
                            )
                        ).child(
                            div().text_xs().text_color(theme.accent_amber).child(
                                format!("Harvestable Gain: ₹ {:.0} (Gross)", total_harvest_gain)
                            )
                        )
                    )
            )
            .child(
                div().text_xs().text_color(theme.text_secondary).child(
                    format!("Total Qualifying Lots: {} (Prime Trims: {}, March Harvest: {}) — Scrollable list",
                        prime_lots.len() + march_lots.len(),
                        prime_lots.len(),
                        march_lots.len()
                    )
                )
            )
            .child(
                if prime_lots.is_empty() && march_lots.is_empty() {
                    div()
                        .id("harvest-empty")
                        .p_6()
                        .rounded_md()
                        .bg(theme.bg_surface)
                        .border_1()
                        .border_color(theme.border_subtle)
                        .flex()
                        .flex_col()
                        .items_center()
                        .justify_center()
                        .gap_2()
                        .child(
                            div().text_sm().font_weight(FontWeight::BOLD).text_color(theme.text_secondary).child(
                                "Zero Friction: No Tax Lots Currently Require Harvesting"
                            )
                        )
                        .child(
                            div().text_xs().text_color(theme.text_secondary.opacity(0.8)).child(
                                "All holdings are within drift policy, locked by exit loads, or Section 112A headroom is optimal."
                            )
                        )
                } else {
                    div()
                        .id("harvest-lots-scroll")
                        .flex_1()
                        .overflow_y_scroll()
                        .flex()
                        .flex_col()
                        .gap_3()
                        .pr_2()
                        .children(
                            prime_lots.iter().map(|lot| {
                                div()
                                    .flex()
                                    .flex_row()
                                    .justify_between()
                                    .items_center()
                                    .p_3()
                                    .rounded_md()
                                    .bg(theme.bg_surface)
                                    .border_1()
                                    .border_color(theme.accent_emerald.opacity(0.4))
                                    .child(
                                        div().flex().flex_col().child(
                                            div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_emerald).child(
                                                format!("⚡ PRIME TRIM: {}", lot.fund_name)
                                            )
                                        ).child(
                                            div().text_xs().text_color(theme.text_secondary).child(
                                                format!("ISIN: {} | {}d held", lot.isin, lot.holding_days)
                                            )
                                        )
                                    )
                                    .child(
                                        div().flex().flex_col().items_end().child(
                                            div().text_xs().font_weight(FontWeight::BOLD).child(
                                                format!("Value: ₹ {:.0}", lot.current_value)
                                            )
                                        ).child(
                                            div().text_xs().text_color(theme.accent_emerald).child(
                                                format!("Gain: +₹ {:.0}", lot.unrealized_gain)
                                            )
                                        )
                                    )
                            }).chain(
                                march_lots.iter().map(|lot| {
                                    div()
                                        .flex()
                                        .flex_row()
                                        .justify_between()
                                        .items_center()
                                        .p_3()
                                        .rounded_md()
                                        .bg(theme.bg_surface)
                                        .border_1()
                                        .border_color(theme.accent_amber.opacity(0.4))
                                        .child(
                                            div().flex().flex_col().child(
                                                div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_amber).child(
                                                    format!("🌱 MARCH HARVEST: {}", lot.fund_name)
                                                )
                                            ).child(
                                                div().text_xs().text_color(theme.text_secondary).child(
                                                    format!("ISIN: {} | {}d held", lot.isin, lot.holding_days)
                                                )
                                            )
                                        )
                                        .child(
                                            div().flex().flex_col().items_end().child(
                                                div().text_xs().font_weight(FontWeight::BOLD).child(
                                                    format!("Value: ₹ {:.0}", lot.current_value)
                                                )
                                            ).child(
                                                div().text_xs().text_color(theme.accent_emerald).child(
                                                    format!("Gain: +₹ {:.0}", lot.unrealized_gain)
                                                )
                                            )
                                        )
                                })
                            )
                        )
                }
            )
            .into_any_element()
    }
}
