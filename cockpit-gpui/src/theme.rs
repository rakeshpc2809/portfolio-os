// cockpit-gpui/src/theme.rs
#![allow(dead_code)]

use gpui::{rgb, Hsla};

pub struct Theme {
    pub bg_base: Hsla,        // #0B0D13 — Obsidian OLED canvas
    pub bg_surface: Hsla,     // #131620 — Elevated card surface
    pub bg_elevated: Hsla,    // #1C2030 — Inner pill / bar track surface
    pub border_subtle: Hsla,  // #262B3D — Hairline structural border
    pub text_primary: Hsla,   // #ECEFF4 — Crisp Frost white
    pub text_secondary: Hsla, // #8892B0 — Muted slate caption
    pub accent_emerald: Hsla, // #73DAC6 — Acid Sage / Prime Action (m15)
    pub accent_violet: Hsla,  // #9D7CD8 — Iris Violet (EQUITY_SATELLITE / Room Exhausted)
    pub accent_cyan: Hsla,    // #7DCFFF — Cyan Frost (EQUITY_CORE / March Harvest m7)
    pub accent_amber: Hsla,   // #E0AF68 — Warm Gold (GOLD_SILVER / Exit Locked m5, m13)
    pub accent_crimson: Hsla, // #F7768E — Coral Rose (Breach / Alert)
}

impl Theme {
    pub fn stealth() -> Self {
        Self {
            bg_base: rgb(0x0B0D13).into(),
            bg_surface: rgb(0x131620).into(),
            bg_elevated: rgb(0x1C2030).into(),
            border_subtle: rgb(0x262B3D).into(),
            text_primary: rgb(0xECEFF4).into(),
            text_secondary: rgb(0x8892B0).into(),
            accent_emerald: rgb(0x73DAC6).into(),
            accent_violet: rgb(0x9D7CD8).into(),
            accent_cyan: rgb(0x7DCFFF).into(),
            accent_amber: rgb(0xE0AF68).into(),
            accent_crimson: rgb(0xF7768E).into(),
        }
    }

    /// Returns a distinct signature color per portfolio bucket
    pub fn bucket_accent(&self, bucket: &str) -> Hsla {
        match bucket {
            "EQUITY_CORE" => self.accent_cyan,
            "EQUITY_SATELLITE" => self.accent_violet,
            "GOLD_SILVER" => self.accent_amber,
            "LIQUID_BUFFER" => self.accent_emerald,
            _ => self.accent_cyan,
        }
    }
}
