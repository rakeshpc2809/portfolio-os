# Walkthrough: Windows 8 Live-Tile Hero Board

The Zellij dashboard has been completely re-architected from multi-tab panes into an authentic, asymmetric **Windows 8 Live-Tile Hero Board**.

---

## 1. Architectural Changes

### Single-Tab Hero Grid (`~/.config/zellij/layouts/dashboard.kdl`)
- Eliminated sequential tabs in favor of a single glanceable live board (`⚡ board`).
- **Left Column (38%) — Hardware Pillar**:
  - Top: `📊 btop` (65% height, system telemetry)
  - Bottom: `🎮 gputop` (35% height, Intel Meteor Lake Xe iGPU engine profiling)
- **Center Column (43%) — HERO TILE**:
  - `💼 portfolio-os-tui` (Rust release binary), standing unsplit and full height as the central focus.
- **Right Column (19%) — Ambient & Fleet Stack**:
  - Top (85%): `⏱️ hud` ([`noctalia-hud.py`](file:///home/rakeshpc/.config/niri/scripts/noctalia-hud.py))
  - Bottom (15%): `🛡️ fleet` ([`fleet-strip.sh`](file:///home/rakeshpc/.config/niri/scripts/fleet-strip.sh))
- **Bottom Strip (10%, Full Width)**:
  - `🎵 cava` audio visualizer spanning the entire display.

### True Tile Appearance (Border Elimination & Color Blocking)
- **Global `pane_frames false`**: Configured in [`~/.config/zellij/config.kdl`](file:///home/rakeshpc/.config/zellij/config.kdl) to remove box-drawing window frames entirely. The subtle cell gap now functions as clean tile spacing.
- **Solid Color Tile Blocks**:
  - `noctalia-hud.py`: Clears and paints the full pane in solid Matugen surface (`#181b24` / `\033[48;2;24;27;36m`) with a Windows 8 bottom-left tile badge: `⏱️ HUD`.
  - `fleet-strip.sh`: Clears and paints the full pane in solid Matugen container (`#222530` / `\033[48;2;34;37;48m`) with bottom-left badge: `🛡️ FLEET`.

### Zoom Interaction
- **Windows 8 "Tap-to-Zoom"**: Zellij's native `pane-fullscreen-toggle` acts as the tile-zoom gesture. Focusing any tile (e.g. portfolio or btop) and toggling fullscreen provides an instant deep-dive, then pops right back to the board.

---

## 2. Live Verification

### Layout & Check
- Checked syntax via `zellij --layout dashboard setup --check` (exited code 0).
- Successfully attached via `toggle-cockpit.sh` to ensure layout instantiation and single-instance retention.

### Real Screenshot
![Windows 8 Hero Tile Board](/home/rakeshpc/.gemini/antigravity-cli/brain/938b5f1a-ed3f-4ef7-b6c5-37530b8f1ebd/hero_tile_board.png)
