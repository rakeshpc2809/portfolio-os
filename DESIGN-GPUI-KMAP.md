# Portfolio OS: GPUI K-Map Cockpit Architecture & Implementation Blueprint
**Document ID:** `DESIGN-GPUI-KMAP.md`  
**Author:** Principal Rust Systems Architect & Quantitative UI Designer  
**Target Host:** Noctalia Shell v5 + Umbriel Wayland Compositor on Intel Core Ultra 5 125H (Intel Arc iGPU)  
**Target Crate:** `cockpit-gpui/` (Standalone Rust Crate, GPUI Native Wayland HUD)  
**Status:** Draft / Implementation in Progress (Runtime Validation Active)  

---

## Executive Summary: Multi-Dimensional Histogram & UI Reduction

Portfolio OS previously maintained two divergent frontend interfaces:
1. `web/`: Svelte 5 + Vite + DuckDB-Wasm + Lightweight Charts (~300 MB RAM, multi-second cold startup, DOM overhead).
2. `tui/`: Ratatui / Textual Rust & Python Terminal UI (~15 MB RAM, character-cell constraints, no sub-pixel rendering).

### What the "K-Map" Actually Is
In classical digital logic design, a Karnaugh Map (K-Map) is an algebraic minimization tool used to calculate minimal Sum of Products (SOP) expressions. In `cockpit-gpui`, **no Boolean algebra is minimized at runtime**. Rather, the K-Map concept serves as an architectural design philosophy and visual representation:
- Personal wealth telemetry is classified across a **16-state discrete multi-dimensional histogram**.
- State space is arranged on a **4-bit Gray-code grid ($2\times 2$ bits: AB rows, CD columns)**, where adjacent cells differ by exactly one bit.
- **Cell $m_{15}$ (`1111`)**: Represents the state where all 4 conditions are met (Band Breached, LTCG, Exit-Load Free, and Section 112A Exemption Room Available)—the primary target for rebalance trim harvesting.
- **Cell $m_7$ (`0111`)**: Represents the state in equilibrium where tax room can be harvested without drift (March 112A Tax Harvesting).
- 90% of conventional portfolio dashboard widgets (candlestick timeframes, multi-line return charts, cosmetic gauges) represent **Don't Care ($X$)** states during daily operations.

### Target Performance Budgets (Pending Profile Instrumentation)
The presentation layer is designed to run as a single, lightweight Wayland HUD written in **Rust with GPUI 0.2.2**:
- **Memory Footprint Budget**: $\sim 40\text{ MB RSS}$
- **Frame Rate Target**: $144\text{ Hz}$ on Wayland / Vulkan (Intel Arc iGPU)
- **Frame Latency Budget**: $< 1\text{ ms}$ input-to-render latency

---

# PART A: Architecture & Legacy Retirement Plan

```
LEGACY ARCHITECTURE (Dual Frontends):
┌───────────────────────────────┐      ┌───────────────────────────────┐
│     web/ (Svelte 5 + Vite)    │      │    tui/ (Ratatui / Textual)   │
│   ~300 MB RAM | WebKit/V8     │      │     ~15 MB RAM | Char-Grid    │
└───────────────┬───────────────┘      └───────────────┬───────────────┘
                │ REST (:8080)                         │ REST (:8080)
                │                                      │ X-Api-Auth-Token
                └──────────────────────┬───────────────┘
                                       ▼
                      ┌──────────────────────────────────┐
                      │ Core Node (Spring Boot :8080)    │
                      └──────────────────────────────────┘

TARGET ARCHITECTURE (Unified Native GPU HUD):
┌──────────────────────────────────────────────────────────────────────┐
│ cockpit-gpui/ (Rust + GPUI 0.2.2 + Vulkan/Wayland)                   │
│ Target: ~40 MB RAM | 144 Hz Native GPU | Mod+P Umbriel Scratchpad    │
└──────────────────────────────────────┬───────────────────────────────┘
                                       │ REST JSON (HTTP GET/POST :8080)
                                       │ Header: X-Api-Auth-Token: <token>
                                       ▼
                      ┌──────────────────────────────────┐
                      │ Core Node (Spring Boot :8080)    │
                      └──────────────────────────────────┘
```

> [!NOTE]
> There is no Arrow Flight (:8001) dependency in `cockpit-gpui`. Communication is strictly REST JSON over HTTP to Core Node (`:8080`), authenticated via `X-Api-Auth-Token`.

---

## 1. Authentication & Configuration Architecture

Core Node enforces token authentication on protected endpoints. The TUI interface previously loaded this via `tui/tui_config.yaml` and environment variables. `cockpit-gpui` replicates this exact security model.

### Resolution Hierarchy (`get_api_config`)
When initializing or polling endpoints, `cockpit-gpui/src/app.rs` queries configuration in this strict priority order:
1. **Base URL**: `PORTFOLIO_OS_API_BASE_URL` (defaults to `http://127.0.0.1:8080`).
2. **Token Search**:
   - `PORTFOLIO_OS_API_TOKEN` environment variable.
   - `API_AUTH_TOKEN` environment variable.
   - `API_TOKEN` environment variable.
   - Fallback config search: parses `tui/tui_config.yaml`, `../tui/tui_config.yaml`, or `tui_config.yaml` for `api_token_env`, then reads the referenced environment variable.
3. **HTTP Header**:
   If a token is present, every outbound `ureq` request attaches:
   ```
   X-Api-Auth-Token: <token>
   ```

---

## 2. Capabilities Removed & Trade-Offs Matrix

Moving from the dual `web/` + `tui/` stack to `cockpit-gpui` deliberately trades exploratory analysis widgets for zero-overhead, keyboard-driven operational speed.

| Feature / Capability | Legacy Location | Status in `cockpit-gpui` | Architectural Justification |
| :--- | :--- | :--- | :--- |
| **In-Browser DuckDB-Wasm SQL** | `web/` | **Removed** | Ad-hoc SQL exploration across Parquet buffers belongs in DuckDB CLI or python notebooks, not a personal rebalancer HUD. |
| **TradingView Candlestick / Area Charts** | `web/` | **Removed** | Portfolio OS enforces long-term index & factor investing. Intraday and multi-line time series induce noise and behavioral bias. |
| **Factor Benchmarks (Alpha, Beta, TE)** | `web/` | **Removed** | Computed by backend DuckDB and quant sidecar; static factor profiles do not alter daily rebalance decisions. |
| **Quiet Mode (Number Masking)** | `web/`, `tui/` | **Deferred** | HUD is mapped to an on-demand floating scratchpad (`Mod+P`), automatically hidden from view when not actively being toggled. |
| **Fund Overlap Matrix** | `tui/` | **Deferred** | Overlap calculations (287 constituent stock baseline) change monthly, not intraday; accessible via backend audit endpoints. |
| **HRP Risk / Inverse-Variance Audit** | `tui/` | **Deferred** | Portfolio target weights are governed by Policy v2.3; tactical covariance audits are run during quarterly rebalances via CLI. |
| **Offline Local Disk Cache** | `tui/` | **Removed** | `cockpit-gpui` runs locally on the same host as `core-node` (`127.0.0.1:8080`). If backend is down, HUD reports an error rather than showing stale valuations. |
| **Browser CSV Export** | `web/` | **Replaced** | Key chord `[Space+e]` sends an authenticated request to `core-node` to generate and download official ITR-2 / Schedule CG archives. |

---

## 3. Financial Invariants & Classifier Specification

The classifier in `cockpit-gpui/src/kmap.rs` must strictly enforce all quant and tax rules established in `CONVENTIONS.md`.

### Predicate Definitions

#### 1. Predicate $A$: Band Breached (`is_overweight`)
- Sourced directly from backend `BucketStatus`:
  $$A = 1 \iff \exists b \in \text{bucket\_allocation} : b.\text{bucket} == \text{holding.asset\_bucket} \land b.\text{is\_drifted} \land b.\text{drift\_pct} > 0.0$$
- Invariant: Core Node computes `is_drifted` using **5/25 relative drift bands** for equity buckets, and **$\pm 2.5\%$ absolute drift** for `LIQUID_BUFFER`.
- Selling/harvesting only applies to *overweight* drifted buckets ($b.\text{drift\_pct} > 0.0$). Underweight buckets require fresh inflow water-filling, not trims.

#### 2. Predicate $B$: Tax Regime Equity LTCG under Section 112A
- Applies strictly to equity-oriented mutual fund schemes ($\ge 65\%$ domestic equity) held for $> 365\text{ days}$:
  $$B = 1 \iff \text{lot.is\_long\_term} \land (\text{lot.isin} \neq \text{"INF247L01BM8"}) \land (\text{asset\_bucket} \neq \text{"LIQUID\_BUFFER"})$$
- **Motilal Oswal Gold & Silver FoF (`INF247L01BM8`)**: Non-equity unlisted mutual fund. LTCG threshold is $> 730\text{ days}$ (24 months) taxed at $12.5\%$ without Section 112A exemption. STCG is taxed at the marginal slab rate ($30\%$). $B = 0$ for this scheme under 112A.
- **Liquid Buffer**: Classified as specified debt under Section 50AA (post-April 1, 2023 acquisitions), always treated as short-term capital gains at slab rate. $B = 0$.

#### 3. Predicate $C$: Exit Load Free (`holding_days >= required_days`)
- Hardcoding 365 days is financially dangerous (e.g. Parag Parikh Flexi Cap charges exit load up to 730 days). The classifier enforces exact scheme thresholds matching `ExitLoadCalculator.java`:
  - **Parag Parikh Flexi Cap (`INF879O01027`)**: $730\text{ days}$ (2 years)
  - **Nippon India Small Cap (`INF204K01K15`)**: $365\text{ days}$ (1 year)
  - **Invesco India Arbitrage (`INF205K01KR8`)**: $15\text{ days}$
  - **Motilal Oswal Gold and Silver FoF (`INF247L01BM8`)**: $15\text{ days}$
  - **Liquid / Overnight Funds**: $7\text{ days}$
  - **Direct Passive Index Funds & ETFs**: $0\text{ days}$
- **Parity with `core-node`**: Verified by unit test `test_exit_load_calculator_parity` against `ExitLoadCalculator.java`.

#### 4. Predicate $D$: Section 112A Exemption Room Available & FIFO Headroom Gating
- Applies strictly to equity LTCG lots ($B = 1$). Under statutory law (Income-tax Act, Section 112A), exemption room is inapplicable to Short-Term Capital Gains ($B = 0$).
- **Zero Fabrication**: Sourced directly from `GET /api/v1/tax/exemption-status` (`net_ltcg_before_exemption` and `exemption_remaining`), never hardcoding fallback figures.
- **Section 70 Statutory Loss Netting**: Headroom calculation accounts for intra-head capital loss netting. Loss lots ($\text{lot\_gain} < 0.0$) do not consume headroom.
- **Decoupled $C$ and $D$ Predicates**: Exit load status ($C$) and exemption room eligibility ($D$) are orthogonal. A lot can be LTCG with available headroom but exit-locked ($m_5$ `0101` and $m_{13}$ `1101`, such as PPFAS between 365 and 730 days). Exit-locked lots do not consume the actionable harvest room today, but enable maturation tracking.
- **RTA FIFO Law (`Sec 45(2A)` Anti-Cherry-Picking Invariant)**:
  Mutual fund RTAs redeem strictly FIFO per Folio/ISIN. If an older lot has capital gain exceeding the remaining Section 112A headroom ($D = 0$), newer lots in the same scheme with smaller gains cannot be cherry-picked ($D = 0$). Once a scheme exhausts available headroom, all subsequent lots in that scheme receive $D = 0$.
- **Prioritization Order**:
  Lots are prioritized:
  1. Overweight buckets first ($A = 1$ lots claim 112A headroom first for $m_{15}$ Prime Implicant).
  2. Grouped by ISIN.
  3. Strictly ascending `buy_date` (FIFO).

#### 5. Rebalance Waterfall Routing (Invariant 4)
- Cell $m_{15}$ (`1111`) indicates units that are structurally ready to harvest without tax friction or exit load.
- However, **proceeds cannot be liquidated arbitrarily or routed into `EQUITY_SATELLITE`**. Trims must follow `RebalanceWaterfallEngine`:
  $$\text{Trim Proceeds} \to \text{Gold Deficit} \to \text{Arbitrage 10\% Target} \to \text{Core Deficit} \to \text{Arbitrage 15\% Cap} \to \text{Cash Overflow}$$
- Trims are constrained by the global **30-day sell cooldown** (`REBALANCE_COOLDOWN_DAYS = 30`).

---

## 4. The 4x4 Gray-Code Grid Mapping & Don't Care ($X$) States

Rows are defined by predicates $AB$ and columns by predicates $CD$:

| $AB \backslash CD$ | **00** | **01** | **11** | **10** |
| :---: | :---: | :---: | :---: | :---: |
| **00** (Equilibrium, STCG) | $m_0$ (`0000`) | $m_1$ (`[X] Don't Care`)* | $m_3$ (`[X] Don't Care`)* | $m_2$ (`0010`) |
| **01** (Equilibrium, LTCG) | $m_4$ (`0100`) | $m_5$ (`0101: Exit Locked`) | **$m_7$ (`0111: March Harvest`)** | $m_6$ (`0110: Room Exhausted`) |
| **11** (Breached, LTCG) | $m_{12}$ (`1100`) | $m_{13}$ (`1101: Exit Locked`) | **$m_{15}$ (`1111: PRIME IMPLICANT`)** | $m_{14}$ (`1110: Room Exhausted`) |
| **10** (Breached, STCG) | $m_8$ (`1000`) | $m_9$ (`[X] Don't Care`)* | $m_{11}$ (`[X] Don't Care`)* | $m_{10}$ (`1010`) |

\* **Don't Care ($X$) Minterms**: Because Section 112A statutory exemption applies strictly to Equity LTCG ($B = 1$), states where $B = 0$ (STCG) and $D = 1$ ($m_1, m_3, m_9, m_{11}$) are classical Boolean Don't Care conditions. The HUD renders these cells subdued with `[X]` indicators.

---

## 5. Decommissioning & Retirement Strategy

### Step 5.1: Purge Frontend Workspace
```bash
# Remove Svelte 5 application and Node modules
rm -rf web/
```
> [!IMPORTANT]
> **DO NOT** delete root `biome.json`. It is required by repository formatting scripts (`just format` and `biome format --write *.json`).

### Step 5.2: Clean Static Serving Configuration in `core-node`
In Spring Boot, static web assets are served from `core-node/src/main/resources/static/` via configuration in `core-node/src/main/resources/application.yml`.
1. Delete static web build artifacts:
   ```bash
   rm -rf core-node/src/main/resources/static/*
   ```
2. In `core-node/src/main/resources/application.yml`, remove static web paths:
   ```diff
   -  mvc:
   -    static-path-pattern: /**
   -  resources:
   -    static-locations: classpath:/static/
   ```
3. Remove static resource patterns from `core-node/src/main/resources/META-INF/native-image/resource-config.json`.

### Step 5.3: Update Zellij Dashboard Layout
In `artifacts/dashboard.kdl` line 21, the hero tile references the legacy TUI binary:
```kdl
// Change line 21 in artifacts/dashboard.kdl:
pane size="43%" name="💼 portfolio-os" command="/home/rakeshpc/.local/bin/portfolio-gpui" focus=true
```

### Step 5.4: Update `justfile` Recipes
The `justfile` includes tasks for building, testing, and installing `cockpit-gpui`:
```makefile
# Fast type-check GPUI Cockpit
check-cockpit:
    cargo check --manifest-path cockpit-gpui/Cargo.toml

# Run GPUI Cockpit unit test suite
test-cockpit:
    cargo test --manifest-path cockpit-gpui/Cargo.toml

# Build GPUI Cockpit release binary (stripped)
build-cockpit:
    cargo build --release --manifest-path cockpit-gpui/Cargo.toml

# Install Cockpit binary to ~/.cargo/bin and ~/.local/bin
install-cockpit:
    cargo install --path cockpit-gpui
    mkdir -p ~/.local/bin
    ln -sf ~/.cargo/bin/portfolio-gpui ~/.local/bin/portfolio-gpui

# Launch GPUI Cockpit directly
cockpit-gpui:
    cargo run --release --manifest-path cockpit-gpui/Cargo.toml
```

When `web/` and `tui/` are formally retired, update `just check`:
```diff
- check: test-core test-quant test-tui test-mobile test-web
+ check: test-core test-quant test-cockpit test-mobile
```

---

# PART B: Linux / Wayland Desktop Integration

Portfolio OS runs on Arch Linux / CachyOS under **Noctalia Shell v5** on the **Umbriel** Wayland compositor, powered by an **Intel Core Ultra 5 125H (Intel Arc iGPU)**.

### 1. Required System Packages
```bash
sudo pacman -S --needed \
    vulkan-icd-loader \
    vulkan-intel \        # Intel Arc iGPU (Core Ultra 5 125H)
    intel-media-driver \  # Hardware media acceleration
    wayland \
    wayland-protocols \
    libxkbcommon \
    libxkbcommon-x11 \
    fontconfig \
    freetype2 \
    ttf-jetbrains-mono-nerd
```

### 2. Umbriel Scratchpad Rule (`~/.config/umbriel/config.toml`)
Configure `cockpit-gpui` as a floating stealth scratchpad that auto-spawns on `Mod+P`:

```toml
# 1. Scratchpad definition
[[scratchpad]]
name = "portfolio"
spawn_when_empty = "portfolio-gpui"

# 2. Window rule targeting the GPUI app_id
[[window_rule]]
match.app_id = "^portfolio-gpui$"
default_scratchpad = "portfolio"
default_floating = true
default_floating_size_px = { width = 1180, height = 720 }

# 3. Scratchpad toggle shortcut (under [keybinds])
[keybinds]
"Mod+P" = { action = "scratchpad-toggle portfolio", repeat = false }
```

When `Mod+P` is pressed:
- If `portfolio-gpui` is not running, Umbriel launches it automatically.
- If it is running, Umbriel instantly surfaces or hides the floating HUD without process teardown.

---

# PART C: Implementation Details (`cockpit-gpui/`)

## 1. `Cargo.toml` Specification

```toml
[package]
name = "portfolio-gpui"
version = "0.1.0"
edition = "2021"
authors = ["Portfolio OS Architects"]
license = "MIT"

[dependencies]
# GPUI Native Framework (Wayland / Vulkan accelerated)
gpui = "0.2.2"

# Data Serialization & HTTP Networking
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
ureq = { version = "2.10", features = ["json"] }

# Asynchronous Runtime & Channel Orchestration
smol = "2.0"

[profile.release]
opt-level = 3
lto = "fat"
codegen-units = 1
panic = "abort"
strip = true

# Local patch for libc ENOATTR compatibility on Linux glibc
[patch.crates-io]
xattr = { path = "patches/xattr" }
```

> [!NOTE]
> The crate vendors a small local patch in `cockpit-gpui/patches/xattr` for `xattr 0.2.3` to alias `ENOATTR` to `libc::ENODATA` on modern Linux glibc systems.

---

## 2. Source File Hierarchy

```
cockpit-gpui/
├── Cargo.toml
├── patches/
│   └── xattr/                 # Local vendored patch for xattr 0.2.3
└── src/
    ├── main.rs                # Wayland window options, Bounds, WindowContext focus
    ├── app.rs                 # PortfolioHud state, smol fetch loop, auth headers, key actions
    ├── models.rs              # FlatHolding, FlatTaxLot, Snapshot, RebalancePlan serde types
    ├── kmap.rs                # 4-bit Gray-code matrix, ExitLoadCalculator, FIFO scheme gating
    ├── theme.rs               # Stealth Obsidian color palette & typography
    └── views/
        ├── mod.rs             # View module exports
        ├── equilibrium.rs     # Net worth banner, Capped Water-Filling SIP allocations
        └── kmap_grid.rs       # 4x4 Gray-code matrix rendering, prime implicant badges
```

---

# PART D: Verification & Rollout Plan

1. **Phase 1: Binary & Dependency Checks (`just check-cockpit`)**:
   Ensure `cockpit-gpui` type-checks cleanly with zero warnings.
2. **Phase 2: Predicate & FIFO Unit Testing (`just test-cockpit`)**:
   Verify unit tests in `kmap.rs`:
   - Gray-code bijection (`gray_to_bits`).
   - Scheme exit load requirements (PPFAS 730d, Nippon Small Cap 365d, Arbitrage 15d).
   - RTA FIFO per-scheme gating (older locked lots prevent newer lot cherry-picking).
   - Non-equity exclusions (Gold/Silver FoF and Liquid Buffer excluded from Section 112A).
   - Cell $m_{15}$ (`1111`) and $m_7$ (`0111`) minterm classification.
3. **Phase 3: Visual & Wayland Verification (`just cockpit-gpui`)**:
   - Launch binary via `cargo run --release --manifest-path cockpit-gpui/Cargo.toml` under Umbriel compositor.
   - Verify Wayland window floating rules, 1180x720 window dimensions.
   - Confirm keyboard action shortcuts: `[Space+k]` toggles 4x4 K-Map grid, `[Space+h]` isolates Section 112A harvest lots, `[Space+i]` simulates SIP inflow rungs, `[Space+e]` exports ITR-2 zip to `~/Downloads`, `[Space+c]` opens command palette, `[r]` refreshes NAVs, `[Esc]` navigates back or quits.
4. **Phase 4: Decommissioning Execution**:
   Once operational confidence is established, execute Steps 5.1–5.4 to decommission `web/` and `tui/`.
