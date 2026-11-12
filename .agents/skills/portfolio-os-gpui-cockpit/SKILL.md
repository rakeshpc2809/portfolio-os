---
name: portfolio-os-gpui-cockpit
description: Architecture, quantitative tax invariants, and development guidelines for cockpit-gpui (Portfolio OS GPUI-native 144Hz desktop HUD).
disable-model-invocation: false
---

# portfolio-os-gpui-cockpit

This skill codifies the quantitative finance rules, fail-closed tax constraints, and architectural invariants for **`cockpit-gpui`**, the GPU-accelerated Wayland desktop cockpit for **Portfolio OS**.

---

## 1. Quantitative Core Architecture

`cockpit-gpui` connects to the local Portfolio OS Core Node (`http://127.0.0.1:8080`) over loopback HTTP with bearer authentication (`X-Api-Auth-Token`).

### Key Views
1. **Equilibrium View (`ActiveView::Equilibrium`)**:
   - 4-Bucket Asset Allocation cards (Large Cap, Mid Cap, Small Cap, Arbitrage/Debt).
   - Real-time policy drift indicator (±5% drift bands).
   - Dual-track GPU progress bars indicating current allocation vs target policy.
   - Interactive Capped Water-Filling SIP inflow routing with scheme-level breakdown.
   - ATH Drawdown indicator, Daily Brief headline, and active radar signals strip.
2. **4-Bit Karnaugh Map Grid (`ActiveView::KMapMatrix`)**:
   - 16-cell Gray-code reduction matrix ($AB \times CD$).
   - Minimizes friction by identifying actionable tax lots for Section 112A harvesting.
   - Dual-track Statutory Headroom Utilization gauge.
   - Perimeter row ($AB$) and column ($CD$) axis headers.
   - Direct click-through drill-down into Harvest Isolation.
3. **Harvest Isolation View (`ActiveView::HarvestIsolation`)**:
   - Filtered, actionable tax lots ($m_{15} \cup m_7$).
   - Scrollable card view showing units, ISIN, purchase date, holding days, and net/gross unrealized gain.
4. **Command Palette (`show_palette`)**:
   - Modal chord overlay invoked with `Space + c`.
   - Clickable action rows and keyboard shortcuts.

---

## 2. Quantitative Tax Invariants & Legal Contracts

### Section 112A Fail-Closed Contract
- Statutory LTCG exemption headroom must **never be fabricated or assumed**.
- Always query `/tax/exemption-status`.
- If the endpoint fails or returns an error, set `exemption_status = Err(...)` and disable the K-Map matrix with:
  `"Statutory exemption status unavailable: <error>"`.
- **Zero Fabrication**: Never inject a hardcoded ₹25,000 or ₹1,25,000 fallback into live rebalance decisions.

### Section 45(2A) RTA FIFO Anti-Cherry-Picking Gate
- Indian Income Tax Law (Section 45(2A)) mandates that mutual fund and equity lot sales follow strict First-In First-Out (FIFO) ordering per ISIN.
- A later lot with a smaller gain cannot skip past an earlier lot whose gain exceeds the remaining Section 112A headroom.
- `kmap.rs` enforces `headroom_exhausted_schemes: HashSet<String>`. Once an older lot in a scheme fails condition D (gain > remaining room), all subsequent lots in that scheme are locked from consuming headroom, preventing illegal cherry-picking.

### 4-Bit Karnaugh Map Truth Table
- **$A$ (Overweight / Breached)**: 1 if asset bucket drift exceeds target policy.
- **$B$ (LTCG Holding Period)**: 1 if held $\ge 365$ days (equity).
- **$C$ (Exit Load Free)**: 1 if holding days exceed exit load threshold (e.g. 730d for PPFAS, 365d for Small Cap, 15d for Arbitrage).
- **$D$ (Headroom Available)**: 1 if lot unrealized gain $\le$ remaining Section 112A exemption headroom.

#### Minterm Classifications
- **$m_{15} = 1111$ (Prime Implicant)**: Breached overweight bucket, LTCG, exit-load free, within 112A headroom. **Action: Immediate Trim**.
- **$m_7 = 0111$ (March Harvest Loop)**: Balanced bucket, LTCG, exit-load free, within 112A headroom. **Action: March Tax Harvest**.
- **$m_5 (0101)$ & $m_{13} (1101)$**: LTCG within headroom, but locked by AMC exit load penalty.
- **$m_6 (0110)$ & $m_{14} (1110)$**: LTCG and exit-load free, but 112A statutory headroom is exhausted.
- **Don't Care Cells ($m_1, m_3, m_9, m_{11}$)**: $B = 0$ (STCG), meaning Section 112A headroom ($D$) does not apply.

---

## 3. Background Polling & Generation Guards

### Polling & Re-Simulation Isolation
1. **Simulation Generation Guard (`sim_generation: u64`)**:
   - Prevents background poll ticks from clobbering active user inflow adjustments.
   - When the user presses `Space + i`, `sim_generation` increments.
   - When a poll tick finishes, it only applies simulated rebalance plans if `hud.sim_generation == poll_gen`.
2. **Blast Radius Isolation (`resolve_poll_simulation`)**:
   - Extracted pure function for poll-simulation merging.
   - A failure in re-simulating inflow will mark `simulation_error`, but will **not** invalidate or blank out the snapshot, K-Map, or statutory exemption status.

---

## 4. Obsidian Bloom Theme & Aesthetic Standards

`cockpit-gpui` uses the **Obsidian Bloom + Matugen** palette:
- `bg_base`: `#13141C` (Dark obsidian canvas)
- `bg_surface`: `#181B26` (Card container surfaces)
- `bg_elevated`: `#1E2333` (Hover and elevated modals)
- `accent_emerald`: `#73DAC6` ($m_{15}$ Prime Implicant, Net Worth, positive gains)
- `accent_cyan`: `#7DCFFF` ($m_7$ March Harvest, Section 112A headroom meter)
- `accent_amber`: `#E0AF68` ($m_5/m_{13}$ Exit locked, policy drift warnings)
- `accent_violet`: `#9D7CD8` ($m_6/m_{14}$ Exhausted headroom, axis labels)
- `accent_crimson`: `#F7768E` (Severe drawdown / critical alerts)

### Bucket Accents (`theme.bucket_accent(&str)`)
- `LARGE_CAP`: `#7DCFFF` (Cyan)
- `MID_CAP`: `#9D7CD8` (Violet)
- `SMALL_CAP`: `#E0AF68` (Amber)
- `ARBITRAGE` / `DEBT`: `#73DAC6` (Emerald)

---

## 5. Development & Verification Commands

```bash
# Static check & unit tests
just check-cockpit
just test-cockpit

# Build optimized binary
cargo build --release --manifest-path cockpit-gpui/Cargo.toml

# Regenerate repomix for external AI review
just repomix-gpui

# Launch HUD on Wayland
./cockpit-gpui/target/release/portfolio-gpui
```
