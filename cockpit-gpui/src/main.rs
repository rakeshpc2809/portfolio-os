// cockpit-gpui/src/main.rs

#![recursion_limit = "512"]

mod app;
mod kmap;
mod models;
mod theme;
mod views;

use app::*;
use gpui::*;

fn main() {
    Application::new().run(|cx: &mut App| {
        // Register top-level key chords matching Helix / modal paradigms
        cx.bind_keys([
            KeyBinding::new("space k", ToggleKMap, None),
            KeyBinding::new("space i", TriggerInflowModal, None),
            KeyBinding::new("space h", IsolateHarvest, None),
            KeyBinding::new("space e", ExportItr2, None),
            KeyBinding::new("space c", ToggleCommandPalette, None),
            KeyBinding::new("r", RefreshSnapshot, None),
            KeyBinding::new("q", Quit, None),
            KeyBinding::new("escape", BackOrQuit, None),
        ]);

        let bounds = Bounds::centered(None, size(px(1180.0), px(720.0)), cx);
        cx.open_window(
            WindowOptions {
                window_bounds: Some(WindowBounds::Windowed(bounds)),
                titlebar: None,
                app_id: Some("portfolio-gpui".to_string()),
                ..Default::default()
            },
            |window, cx| cx.new(|cx| PortfolioHud::new(window, cx)),
        )
        .unwrap();
    });
}
