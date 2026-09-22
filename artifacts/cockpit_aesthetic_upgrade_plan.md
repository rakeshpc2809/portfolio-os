# Implementation Plan: Zellij Live-Tile Cockpit Upgrade & Refinement

## Goal Description
Analyze, review, and refine the two files provided by Claude in `~/Projects/portfolio-os/artifacts/`:
1. `dashboard.kdl` — Zellij multi-tab layout (`⚡ cockpit`, `🖥 hardware`, `🌙 zen`).
2. `toggle-cockpit.sh` — Niri/Ghostty launch & attach script.

The goal is to preserve the best aspects of Claude's proposals (clean multi-tab separation, `gputop` integration, native Rust `portfolio-os-tui` binary) while fixing critical operational flaws (Niri toggling regression, `systemctl status` terminal truncation, missing swap layouts, and window-rule mismatches).

---

## Detailed Analysis of Claude's Implementation

### 1. `dashboard.kdl` Analysis

#### Strengths:
- **Clean Tab Architecture**: Uses 3 well-defined tabs: `⚡ cockpit` (mission control), `🖥 hardware` (Xe iGPU + CPU), and `🌙 zen` (ambient resting state for OLED anti-burn-in).
- **Correct Native TUI Path**: Accurately targets the native Rust binary (`/home/rakeshpc/Projects/portfolio-os/tui/target/release/portfolio-os-tui`) instead of the obsolete Python script.
- **Accurate Meteor Lake GPU Tooling**: Correctly selects `/usr/bin/gputop` for Intel Xe iGPU profiling.
- **Borderless Aesthetic**: Applies `borderless=true` on the status/ambient panes to maximize OLED black space.

#### Weaknesses & Gaps:
1. **Fleet Pane Output Truncation**:
   ```kdl
   args "-c" "watch -n2 --color 'systemctl --user status portfolio-os-core portfolio-os-quant ollama power-monitor 2>&1 | head -n 32'"
   ```
   - In a pane of ~30% height, `systemctl status` for 4 services exceeds 32 lines. `portfolio-os-core` alone prints 15+ lines of logs, meaning subsequent services (`portfolio-os-quant`, `ollama`, `power-monitor`) will be completely cut off from view.
   - **Solution**: Replace raw `systemctl status | head` with a lightweight formatted watcher script (`fleet-hud.sh`) that outputs a compact, colorized status for each service along with memory and CPU usage.
2. **Missing `swap_tiled_layout`**:
   - Claude used static tabs rather than Zellij's dynamic `swap_tiled_layout`. Adding swap layouts allows toggling pane arrangements dynamically within a tab without losing terminal state.
3. **Ghostty Font Sizing in Dense Panes**:
   - The right column is split into 3 stacked panes (`hud` 18%, `portfolio-os` 52%, `fleet` 30%). For `noctalia-hud.py` to fit without clipping in an 18% vertical slice, padding and box borders must be strictly managed.

---

## 2. `toggle-cockpit.sh` Analysis

#### Strengths:
- Adds `--class="cockpit-dashboard"` for explicit window rule tagging.
- Includes a retry loop if Ghostty exits abnormally.

#### Critical Bugs & Regressions:
1. **Destroys "Toggle" Semantics**:
   - The existing script (`~/.config/niri/scripts/toggle-cockpit.sh`) is bound to `Mod+Z` in `~/.config/niri/cfg/keybinds.kdl` and spawned at startup in `~/.config/niri/cfg/autostart.kdl`.
   - The existing script checks if `ghostty-dashboard` is already open:
     - If open: it focuses Workspace 1 and the window, bringing it to the front.
     - If closed: it cleans up stale Zellij sessions and launches Ghostty in fullscreen on Workspace 1.
   - Claude's script unconditionally executes `ghostty ...` inside a blocking while-loop. Pressing `Mod+Z` when the cockpit is already open would either do nothing or spawn redundant, conflicting Ghostty instances!
2. **Niri Rules Mismatch**:
   - `~/.config/niri/cfg/rules.kdl` line 52 specifies:
     ```kdl
     match title="ghostty-dashboard"
     open-on-workspace "1"
     open-fullscreen true
     ```
   - Claude's script added `--class="cockpit-dashboard"`. In Wayland/Niri, window rules match on `app-id` (not `class`). If we want Niri to match by app-id, the flag in Ghostty is `--class=...` which sets `app-id`, but `rules.kdl` currently matches `title="ghostty-dashboard"`.
3. **Session Zombie Recovery**:
   - Claude's script removes `zellij delete-session dashboard --force >/dev/null 2>&1 || true`. When Ghostty is closed abruptly, Zellij often leaves a detached session in a corrupted terminal size. Cleaning or handling detached sessions cleanly is necessary.

---

## Proposed Changes

### Component 1: Zellij Layout Configuration
#### [MODIFY] `~/.config/zellij/layouts/dashboard.kdl`
- Retain the 3-tab structure (`⚡ cockpit`, `🖥 hardware`, `🌙 zen`).
- Update the `🛡️ fleet` pane to run a dedicated compact dashboard script (`~/.config/niri/scripts/fleet-hud.sh`) showing clean status indicators for `portfolio-os-core`, `portfolio-os-quant`, `ollama`, `power-monitor`, and `niri-focused-booster`.
- Keep `borderless=true` on Cava and HUD for maximum OLED aesthetic.

### Component 2: Fleet Status Helper Script
#### [NEW] `~/.config/niri/scripts/fleet-hud.sh`
- A lightweight Bash script that renders a high-density, ANSI-colored live tile showing:
  - Systemd user service statuses (`● ACTIVE` / `○ INACTIVE`).
  - Active PID, CPU %, and Memory footprint for each service.
  - Re-checks every 2 seconds via `watch` or a simple sleep loop with zero terminal flicker.

### Component 3: Niri Cockpit Toggle Script
#### [MODIFY] `~/.config/niri/scripts/toggle-cockpit.sh`
- Combine the best of both worlds:
  - Keep the fast Niri IPC window detection (`niri msg windows`) to enable instant `Mod+Z` toggling between workspaces/windows.
  - Set `--title="ghostty-dashboard"` and `--class="ghostty-dashboard"` to maintain 100% compatibility with `~/.config/niri/cfg/rules.kdl`.
  - Add clean session attachment and orphan recovery.

---

## Verification Plan

### Automated / Command Verification
1. Validate Zellij layout syntax:
   ```bash
   zellij --layout ~/.config/zellij/layouts/dashboard.kdl setup --check
   ```
2. Verify `fleet-hud.sh` execution and ANSI output:
   ```bash
   ~/.config/niri/scripts/fleet-hud.sh --once
   ```
3. Test Niri window detection logic in `toggle-cockpit.sh`:
   ```bash
   bash -n ~/.config/niri/scripts/toggle-cockpit.sh
   ```

### Manual Verification
1. Press `Mod+Z` to trigger the cockpit and ensure it opens fullscreen on Workspace 1.
2. Cycle tabs (`Alt+h`/`Alt+l` or `Ctrl+t`) to test switching between `⚡ cockpit`, `🖥 hardware`, and `🌙 zen`.
3. Confirm `gputop` properly plots Meteor Lake Xe iGPU metrics in the `🖥 hardware` tab.
4. Confirm `fleet` pane displays all services with live status and memory without truncation.
