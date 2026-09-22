# Implementation Plan: Zellij Live-Tile Cockpit (Final Ratified Spec)

## Goal Description
Implement the complete, final specification for the Zellij Live-Tile Cockpit:
1. **Structure: 1 Home Board + 2 Occasional Rooms**:
   - **Tab 1: `⚡ board`** (`focus=true`): Full glanceable tile grid.
     - Left (40%): `📊` btop
     - Top-Right (16%): `⏱️` `noctalia-hud.py` (borderless)
     - Center-Right (78%): `💼` `portfolio-os-tui` (star pane, native Rust release binary)
     - Bottom-Right (6%): `🛡️` fleet status single-line dot strip (borderless)
     - Bottom (8%): `🎵` `cava` (borderless)
   - **Tab 2: `🖥 hardware`**: btop (50%) + `gputop` (50%, Intel Xe iGPU profiling).
   - **Tab 3: `🌙 zen`**: Ambient/away resting state with:
     - `🎋` `cbonsai` (slow ambient ASCII bonsai growth animation)
     - `🕒` `tty-clock` (blocky digital clock themed to accent color)
     - `🎵` `cava` (subtle audio motion, borderless)
2. **Fleet Status Strip**: Single-line collapsed reactive dot strip:
   `● core   ● quant   ● ollama   ● power`
3. **Theming & Aesthetics (Confirmed Pre-Flight Finding)**:
   - **Confirmed Theme Author**: Matugen is the active template engine (`~/.config/matugen/config.toml` outputs to `~/.config/zellij/themes/matugen.kdl`).
   - To make sure the OLED true-black (`#000000`) background and color accents are never overwritten on wallpaper changes:
     - In `~/.config/matugen/templates/zellij.kdl`, ensure `bg` is permanently hard-coded to `#000000` so dynamic regeneration retains OLED pitch black while adopting palette accents.
     - Zellij config (`~/.config/zellij/config.kdl`) uses `theme "matugen"` (or "noctalia").
     - Add rounded pane corners to `~/.config/zellij/config.kdl`:
       ```kdl
       ui {
           pane_frames {
               rounded_corners true
           }
       }
       ```
   - Icon-only pane titles where appropriate; `borderless=true` on HUD, Cava, Fleet, and Zen panes.
4. **Patched `toggle-cockpit.sh`**:
   - Strictly patch the existing script: add `--class="cockpit-dashboard"` alongside `--title=ghostty-dashboard`, and add a retry wrapper while preserving lines 6-12 (`niri msg windows` check) and lines 20-33 (workspace 1 latching and focus).

---

## Fact-Check & Verification Status

1. **Matugen Output Check (`grep -rn "zellij\|noctalia.kdl" ~/.config/matugen/config.toml`)**:
   - Confirmed: Matugen directly generates `~/.config/zellij/themes/matugen.kdl`.
   - Fixing `~/.config/matugen/templates/zellij.kdl` ensures `bg "#000000"` survives all automated wallpaper recolors.
2. **`cbonsai` and `tty-clock`**:
   - `/usr/bin/cbonsai` (1.4.2-1) and `/usr/bin/tty-clock` (2.3-2) are installed and verified.
3. **Zellij UI Rounded Corners**:
   - `ui { pane_frames { rounded_corners true } }` verified valid with `zellij setup --check`.
4. **`toggle-cockpit.sh`**:
   - Verified that `Mod+Z` relies on `niri msg windows | grep -B 2 'ghostty-dashboard'`. The patch maintains this exact contract.

---

## Proposed Changes

### Component 1: Fleet Dot-Strip Script
#### [NEW] `~/.config/niri/scripts/fleet-strip.sh`
- A minimal, flicker-free Bash script that outputs:
  ```
  ● core   ● quant   ● ollama   ● power
  ```
- Green dot (`\033[1;32m●\033[0m`) when `systemctl --user is-active <service>` returns `active`.
- Red dot (`\033[1;31m●\033[0m`) when inactive/failed.
- Monitored units: `portfolio-os-core`, `portfolio-os-quant`, `ollama`, `power-monitor`.
- Refresh loop every 2 seconds, hidden cursor, clean exit trap.

### Component 2: Zellij Dashboard Layout
#### [MODIFY] `~/.config/zellij/layouts/dashboard.kdl`
- Structure:
  - Tab 1: `⚡ board` (`btop`, `noctalia-hud.py`, `portfolio-os-tui`, `fleet-strip.sh`, `cava`)
  - Tab 2: `🖥 hardware` (`btop`, `gputop`)
  - Tab 3: `🌙 zen` (`cbonsai -l -m "zen & vibe"`, `tty-clock -c -s -C 5`, `cava`)
- Set `borderless=true` on all ambient, strip, and visualizer panes.

### Component 3: Theming & Zellij Configuration
#### [MODIFY] `~/.config/matugen/templates/zellij.kdl`
- Set `bg "#000000"` so template regeneration preserves OLED black backgrounds.
#### [MODIFY] `~/.config/zellij/config.kdl`
- Enable `theme "matugen"`
- Add UI block for rounded pane corners:
  ```kdl
  ui {
      pane_frames {
          rounded_corners true
      }
  }
  ```

### Component 4: Cockpit Toggle Script
#### [MODIFY] `~/.config/niri/scripts/toggle-cockpit.sh`
- Add `--class="cockpit-dashboard"` to `ghostty` launch flags.
- Wrap launch in a crash-retry loop without disturbing the Niri IPC toggle check.

---

## Verification Plan

### Automated & Sanity Checks
1. Layout syntax check:
   ```bash
   zellij --layout ~/.config/zellij/layouts/dashboard.kdl setup --check
   ```
2. Single-shot fleet dot strip test:
   ```bash
   ~/.config/niri/scripts/fleet-strip.sh --once
   ```
3. Reactive dot-strip test:
   ```bash
   systemctl --user stop power-monitor
   ~/.config/niri/scripts/fleet-strip.sh --once  # Confirm power is RED
   systemctl --user start power-monitor
   ~/.config/niri/scripts/fleet-strip.sh --once  # Confirm power is GREEN
   ```
4. Syntax check on `toggle-cockpit.sh`:
   ```bash
   bash -n ~/.config/niri/scripts/toggle-cockpit.sh
   ```

### Live Visual Verification (Proof)
1. Launch cockpit via `toggle-cockpit.sh` and capture real screenshots of:
   - Tab 1: `⚡ board`
   - Tab 3: `🌙 zen`
   using `grim`.
2. Test `Mod+Z` / toggle command while cockpit is open to prove it focuses Workspace 1 and brings the window to the front without spawning duplicate instances.
