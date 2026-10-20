pub mod hero;
pub mod allocation;
pub mod tax;
pub mod modals;
pub mod sparkline;

pub const MUTED: ratatui::style::Color = ratatui::style::Color::Rgb(148, 163, 184);

pub fn format_inr_compact(val: f64) -> String {
    let abs_val = val.abs();
    let sign = if val < 0.0 { "-" } else { "" };
    if abs_val >= 10_000_000.0 {
        format!("{}₹{:.2}Cr", sign, abs_val / 10_000_000.0)
    } else if abs_val >= 100_000.0 {
        format!("{}₹{:.2}L", sign, abs_val / 100_000.0)
    } else if abs_val >= 1_000.0 {
        format!("{}₹{:.1}K", sign, abs_val / 1_000.0)
    } else {
        format!("{}₹{:.0}", sign, abs_val)
    }
}
