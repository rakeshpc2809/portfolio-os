// cockpit-gpui/src/theme.rs

#![allow(dead_code)]

use gpui::{rgb, Hsla};

pub struct Theme {
    pub bg_base: Hsla,        // #0D0F12 — Pitch OLED backdrop
    pub bg_surface: Hsla,     // #161920 — Dark Gunmetal elevation
    pub border_subtle: Hsla,  // #242933 — Micro-borders
    pub text_primary: Hsla,   // #ECEFF4 — Crisp white typography
    pub text_secondary: Hsla, // #8892B0 — Subdued caption text
    pub accent_emerald: Hsla, // #10B981 — Prime Implicant / Zero-Tax Green
    pub accent_amber: Hsla,   // #F59E0B — Section 112A Window Amber
    pub accent_crimson: Hsla, // #EF4444 — Firewall Lock / Breach Red
}

impl Theme {
    pub fn stealth() -> Self {
        Self {
            bg_base: rgb(0x0D0F12).into(),
            bg_surface: rgb(0x161920).into(),
            border_subtle: rgb(0x242933).into(),
            text_primary: rgb(0xECEFF4).into(),
            text_secondary: rgb(0x8892B0).into(),
            accent_emerald: rgb(0x10B981).into(),
            accent_amber: rgb(0xF59E0B).into(),
            accent_crimson: rgb(0xEF4444).into(),
        }
    }
}
