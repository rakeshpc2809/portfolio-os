// cockpit-gpui/src/views/kmap_grid.rs

use gpui::*;
use crate::app::PortfolioHud;
use crate::theme::Theme;

impl PortfolioHud {
    pub fn render_kmap_view(&self, theme: &Theme) -> impl IntoElement {
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

        div()
            .flex()
            .flex_col()
            .size_full()
            .gap_3()
            .child(
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
                        div().flex().flex_row().gap_4().items_center().child(
                            div().text_xs().text_color(theme.accent_emerald).child(
                                format!("Sec 112A Exemption Room: ₹ {:.0} / ₹ 1,25,000", kmap.total_harvest_room_remaining)
                            )
                        ).child(
                            div().text_xs().text_color(theme.accent_amber).child(
                                format!("Prime Value: ₹ {:.0}", kmap.prime_implicant_liquidable_value)
                            )
                        )
                    )
            )
            .child(
                // 4x4 Gray-Code Matrix Container
                div()
                    .grid()
                    .grid_cols(4)
                    .gap_2()
                    .p_3()
                    .rounded_md()
                    .bg(theme.bg_surface)
                    .border_1()
                    .border_color(theme.border_subtle)
                    .children((0..16).map(|idx| {
                        let cell = &kmap.cells[idx];
                        let is_prime = cell.is_prime_implicant();
                        let is_harvest = cell.is_march_harvest();
                        let is_dont_care = cell.is_dont_care();

                        let (border_col, bg_col, label_col) = if is_prime {
                            (theme.accent_emerald, theme.accent_emerald.opacity(0.12), theme.accent_emerald)
                        } else if is_harvest {
                            (theme.accent_amber, theme.accent_amber.opacity(0.10), theme.accent_amber)
                        } else if is_dont_care {
                            (theme.border_subtle.opacity(0.4), theme.bg_base.opacity(0.5), theme.text_secondary.opacity(0.4))
                        } else {
                            (theme.border_subtle, theme.bg_base, theme.text_secondary)
                        };

                        div()
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
                                    div().flex().flex_col().child(
                                        div().text_xs().font_weight(FontWeight::BOLD).child(
                                            format!("₹ {:.0}", cell.total_value)
                                        )
                                    ).child(
                                        div().text_xs().text_color(theme.text_secondary).child(
                                            format!("Gain: ₹ {:.0}", cell.total_gain)
                                        )
                                    )
                                }
                            )
                            .child(
                                if is_prime {
                                    div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_emerald).child("⚡ PRIME ACTION")
                                } else if is_harvest {
                                    div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_amber).child("🌱 112A HARVEST")
                                } else if is_dont_care {
                                    div().text_xs().text_color(theme.text_secondary.opacity(0.3)).child("[X] DON'T CARE")
                                } else if cell.minterm == 0b1101 {
                                    div().text_xs().text_color(theme.accent_amber.opacity(0.8)).child("⏳ EXIT LOCKED (M13)")
                                } else if cell.minterm == 0b0101 {
                                    div().text_xs().text_color(theme.accent_amber.opacity(0.8)).child("⏳ EXIT LOCKED (M5)")
                                } else {
                                    div().text_xs().text_color(theme.text_secondary.opacity(0.5)).child("INACTIVE")
                                }
                            )
                    }))
            )
            .into_any_element()
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

        let total_harvest_val = kmap.prime_implicant_liquidable_value + kmap.march_harvest_liquidable_value;
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
                            div().text_xs().text_color(theme.accent_emerald).child(
                                format!("Headroom: ₹ {:.0}", kmap.total_harvest_room_remaining)
                            )
                        ).child(
                            div().text_xs().text_color(theme.text_primary).child(
                                format!("Harvestable: ₹ {:.0}", total_harvest_val)
                            )
                        ).child(
                            div().text_xs().text_color(theme.accent_amber).child(
                                format!("Tax-Free Gain: ₹ {:.0}", total_harvest_gain)
                            )
                        )
                    )
            )
            .child(
                if prime_lots.is_empty() && march_lots.is_empty() {
                    div()
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
                        .flex()
                        .flex_col()
                        .gap_3()
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
                                            div().text_sm().font_weight(FontWeight::BOLD).child(lot.fund_name.clone())
                                        ).child(
                                            div().text_xs().text_color(theme.text_secondary).child(
                                                format!("ISIN: {} | Units: {:.3} | Age: {} days", lot.isin, lot.units, lot.holding_days)
                                            )
                                        )
                                    )
                                    .child(
                                        div().flex().flex_col().items_end().child(
                                            div().text_sm().font_weight(FontWeight::BOLD).text_color(theme.accent_emerald).child(
                                                format!("Value: ₹ {:.0}", lot.current_value)
                                            )
                                        ).child(
                                            div().text_xs().text_color(theme.accent_emerald).child(
                                                format!("Gain: ₹ {:.0} (⚡ PRIME REBALANCE)", lot.unrealized_gain)
                                            )
                                        )
                                    )
                            })
                        )
                        .children(
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
                                            div().text_sm().font_weight(FontWeight::BOLD).child(lot.fund_name.clone())
                                        ).child(
                                            div().text_xs().text_color(theme.text_secondary).child(
                                                format!("ISIN: {} | Units: {:.3} | Age: {} days", lot.isin, lot.units, lot.holding_days)
                                            )
                                        )
                                    )
                                    .child(
                                        div().flex().flex_col().items_end().child(
                                            div().text_sm().font_weight(FontWeight::BOLD).text_color(theme.accent_amber).child(
                                                format!("Value: ₹ {:.0}", lot.current_value)
                                            )
                                        ).child(
                                            div().text_xs().text_color(theme.accent_amber).child(
                                                format!("Gain: ₹ {:.0} (🌱 MARCH 112A HARVEST)", lot.unrealized_gain)
                                            )
                                        )
                                    )
                            })
                        )
                }
            )
            .into_any_element()
    }
}
