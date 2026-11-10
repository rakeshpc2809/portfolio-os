// cockpit-gpui/src/views/kmap_grid.rs

use gpui::*;
use crate::app::PortfolioHud;
use crate::theme::Theme;

impl PortfolioHud {
    pub fn render_kmap_view(&self, theme: &Theme) -> impl IntoElement {
        let kmap = match &self.kmap {
            Some(m) => m,
            None => return div().child("Computing K-Map reduction...").into_any_element(),
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
                        div().text_xs().text_color(theme.accent_emerald).child(
                            format!("Sec 112A Exemption Room: ₹ {:.0} / ₹ 1,25,000", kmap.total_harvest_room_remaining)
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

                        let (border_col, bg_col, label_col) = if is_prime {
                            (theme.accent_emerald, theme.accent_emerald.opacity(0.12), theme.accent_emerald)
                        } else if is_harvest {
                            (theme.accent_amber, theme.accent_amber.opacity(0.10), theme.accent_amber)
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
                                        format!("{:04b}", cell.minterm)
                                    )
                                ).child(
                                    div().text_xs().text_color(theme.text_secondary).child(
                                        format!("{} lots", cell.lots.len())
                                    )
                                )
                            )
                            .child(
                                div().flex().flex_col().child(
                                    div().text_xs().font_weight(FontWeight::BOLD).child(
                                        format!("₹ {:.0}", cell.total_value)
                                    )
                                ).child(
                                    div().text_xs().text_color(theme.text_secondary).child(
                                        format!("Gain: ₹ {:.0}", cell.total_gain)
                                    )
                                )
                            )
                            .child(
                                if is_prime {
                                    div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_emerald).child("⚡ PRIME ACTION")
                                } else if is_harvest {
                                    div().text_xs().font_weight(FontWeight::BOLD).text_color(theme.accent_amber).child("🌱 112A HARVEST")
                                } else {
                                    div().text_xs().text_color(theme.text_secondary.opacity(0.5)).child("INACTIVE")
                                }
                            )
                    }))
            )
            .into_any_element()
    }
}
