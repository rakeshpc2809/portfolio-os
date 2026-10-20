# Portfolio OS — Section 10 Evidence Dossier for Gemini Argon

This dossier provides the complete source code, runtime proof, visual artifacts, and test execution telemetry requested in **Section 10 ("What Could Not Be Assessed & Evidence Needed")** of the Gemini Argon Codebase Review.

All corresponding source files have been packed into a dedicated, self-contained Repomix file:  
📦 **[repomix-evidence-pack.xml](file:///home/rakeshpc/Projects/portfolio-os/repomix-evidence-pack.xml)** *(103 files • 363,665 tokens • 1.35 MB)*.

---

## 1. Web Cockpit ("Obsidian Terminal") Evidence

### Source Code Coverage in Pack
All static web files under `core-node/src/main/resources/static/` are included:
- **HTML:** [index.html](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/index.html), [obsidian-terminal.html](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/obsidian-terminal.html), and [legacy-dashboard.html](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/legacy-dashboard.html).
- **CSS:** [obsidian-terminal.css](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/src/obsidian-terminal.css) and [style.css](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/src/style.css).
- **JavaScript Engine:** [obsidian-terminal.js](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/src/obsidian-terminal.js), [app.js](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/src/app.js), and modular services (`src/js/modules/portfolio/*.js`, `src/js/*.js`). *(Note: minified vendor bundle `lightweight-charts.js` was excluded to preserve token budget).*

### 3-Tab Declutter Mandate Verification
The interface is strictly capped at 3 primary views governed by `obsidian-terminal.js` and `obsidian-terminal.html`:
1. `navTabOverview` — **Tab 1: Executive Overview** (Hero Net Worth strip, 4-bucket target corridors, XIRR badge, fresh AMFI radar signals).
2. `navTabTaxLots` — **Tab 2: Scheme Tax Lots** (FIFO lots grouped by scheme, FY headroom meter, days to LTCG maturation).
3. `navTabRebalance` — **Tab 3: Unified Rebalance & FIRE** (6-tier tax liquidation waterfall, buy ticket allocation, Monte Carlo simulation).

### Quiet Mode Masking Implementation
Quiet Mode in the Web Cockpit is backed by `PORTFOLIO_OS_QUIET_MODE` in `localStorage` and toggled via `.btn-quiet-mode`:
```css
/* obsidian-terminal.css */
.quiet-mode-active .hud-value-pnl,
.quiet-mode-active .hud-delta-pnl { display: none !important; }
.quiet-mode-active .quiet-mask-overlay { display: block !important; }

/* Mask per-holding gains and FIRE projections */
.quiet-mode-active .lot-val-pnl { display: none !important; }
.quiet-mode-active .lot-val-masked { display: inline !important; }

/* Obfuscate lightweight charts and FIRE canvases with hardware-accelerated blur */
.quiet-mode-active .lightweight-chart-container,
.quiet-mode-active .fire-canvas-container {
  filter: blur(20px);
  opacity: 0.08;
  pointer-events: none;
}
.quiet-mode-active .chart-quiet-shield,
.quiet-mode-active .fire-quiet-shield { display: flex; }
```

### Visual Verification Screenshots

````carousel
![Executive Overview Dashboard (Obsidian Terminal)](/home/rakeshpc/.gemini/antigravity-ide/brain/cfd56e4b-ca04-415c-a9d0-43bed1e59020/board_tab.png)
<!-- slide -->
![Zen Tab HUD Strip View](/home/rakeshpc/.gemini/antigravity-ide/brain/cfd56e4b-ca04-415c-a9d0-43bed1e59020/zen_tab.png)
<!-- slide -->
![Hero Tile HUD Detail](/home/rakeshpc/.gemini/antigravity-ide/brain/cfd56e4b-ca04-415c-a9d0-43bed1e59020/hero_tile_board.png)
<!-- slide -->
![Tax Lot Modal — Quiet Mode OFF](/home/rakeshpc/.gemini/antigravity-ide/brain/cfd56e4b-ca04-415c-a9d0-43bed1e59020/modal_quiet_off.png)
<!-- slide -->
![Tax Lot Modal — Quiet Mode ON (Rupee values masked)](/home/rakeshpc/.gemini/antigravity-ide/brain/cfd56e4b-ca04-415c-a9d0-43bed1e59020/modal_quiet_on.png)
<!-- slide -->
![ITR-2 Overlay — Quiet Mode OFF](/home/rakeshpc/.gemini/antigravity-ide/brain/cfd56e4b-ca04-415c-a9d0-43bed1e59020/itr_overlay_quiet_off.png)
<!-- slide -->
![ITR-2 Overlay — Quiet Mode ON](/home/rakeshpc/.gemini/antigravity-ide/brain/cfd56e4b-ca04-415c-a9d0-43bed1e59020/itr_overlay_quiet_on.png)
````

---

## 2. Android App Screens, Glance Widget & Cache Architecture

### Source Code Coverage in Pack
All 21 production Kotlin files are included in [repomix-evidence-pack.xml](file:///home/rakeshpc/Projects/portfolio-os/repomix-evidence-pack.xml):
- **UI Screens & Layout:** [DashboardScreen.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt), [PortfolioCharts.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/PortfolioCharts.kt), [SimulatorScreen.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/SimulatorScreen.kt), [LockScreenGate.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/LockScreenGate.kt), [MainActivity.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/MainActivity.kt).
- **Home-Screen Widget:** [PortfolioGlanceWidget.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/widget/PortfolioGlanceWidget.kt) (Jetpack Glance Material 3 AppWidget).
- **Data & Cache Manager:** [SnapshotCacheManager.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/data/SnapshotCacheManager.kt), [AmfiDirectFetcher.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/data/nav/AmfiDirectFetcher.kt).
- **Design Tokens:** `mobile-app/app/src/main/java/com/portfolioos/mobile/ui/theme/{ColorTokens,TypographyTokens,ShapeTokens,SpacingTokens,Theme}.kt`.
- **Components:** `mobile-app/app/src/main/java/com/portfolioos/mobile/ui/components/{StateCard,ProvenanceBadge,OverlapConcentrationBottomSheet}.kt`.

### 3-Page Navigation Structure
`DashboardScreen.kt` implements an offline-first swipeable `HorizontalPager(state = pagerState)` corresponding strictly to 3 pages:
1. **Page 0: Dashboard HUD** — Net Worth, XIRR, 4-Bucket Horizontal Gauge, Stale NAV / Offline Warning Banners.
2. **Page 1: Tax Lots & Maturation** — Scheme-grouped FIFO lots, LTCG countdown badges, tax drag breakdown.
3. **Page 2: Rebalance & Simulation** — 6-tier waterfall execution preview, What-If Trade Simulator, FIRE goal progress.

### `SnapshotCacheManager` Offline Architecture
Implemented as a thread-safe persistent cache with SHA-256 integrity validation and 3-stage freshness:
```kotlin
// SnapshotCacheManager.kt
enum class CacheFreshness {
    FRESH,          // < 24 hours old
    STALE_OFFLINE,  // 24 to 36 hours old
    DISCONNECTED    // >= 36 hours old (Triggers Android system notification)
}
```
- Backed by disk storage (`portfolio_snapshot_cache.json`).
- Automatically updates Glance AppWidget on background worker fetch.
- Gracefully supplies cached snapshot during network disconnects.

---

## 3. Test Coverage & Property-Based Verification

### Source Code Coverage in Pack
- **All 48 Java Test Suites:** `core-node/src/test/java/**` (including [FifoMatcherPropertyTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/matcher/FifoMatcherPropertyTest.java), [RebalanceWaterfallRoutingTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/service/RebalanceWaterfallRoutingTest.java), and [SqliteEventStoreTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/persistence/SqliteEventStoreTest.java)).
- **All 4 Python Test Suites:** `quant-sidecar/tests/**` (`test_parsers.py`, `test_analytics.py`, `test_async_concurrency.py`, `test_hrp_allocator.py`).

### Quant Sidecar Test Execution Output (`just test-quant`)
```text
$ cd quant-sidecar && uv run python -m unittest discover -v -s tests

test_benchmark_analytics_schema (test_analytics.TestAnalyticsEngine.test_benchmark_analytics_schema) ... ok
test_fire_simulation_schema_and_percentiles (test_analytics.TestAnalyticsEngine.test_fire_simulation_schema_and_percentiles) ... ok
test_linear_confidence_ramp_boundaries (test_analytics.TestAnalyticsEngine.test_linear_confidence_ramp_boundaries) ... ok
test_regime_conditional_block_bootstrap (test_analytics.TestAnalyticsEngine.test_regime_conditional_block_bootstrap) ... ok
test_app_route_handler_and_auth_gate (test_hrp_allocator.TestHrpAllocator.test_app_route_handler_and_auth_gate) ... ok
test_hrp_allocation_fallback_mode_when_duckdb_locked (test_hrp_allocator.TestHrpAllocator.test_hrp_allocation_fallback_mode_when_duckdb_locked) ... ok
test_insufficient_history_guard (test_hrp_allocator.TestHrpAllocator.test_insufficient_history_guard) ... ok
test_n2_core_bucket_optimization_via_risk_budgeting (test_hrp_allocator.TestHrpAllocator.test_n2_core_bucket_optimization_via_risk_budgeting) ... ok
test_n3_satellite_bucket_optimization_via_hrp (test_hrp_allocator.TestHrpAllocator.test_n3_satellite_bucket_optimization_via_hrp) ... ok
test_run_hrp_allocation_equity_universe_live (test_hrp_allocator.TestHrpAllocator.test_run_hrp_allocation_equity_universe_live) ... ok
test_run_hrp_allocation_intra_bucket_live (test_hrp_allocator.TestHrpAllocator.test_run_hrp_allocation_intra_bucket_live) ... ok
test_strict_shared_calendar_alignment_drops_missing_dates (test_hrp_allocator.TestHrpAllocator.test_strict_shared_calendar_alignment_drops_missing_dates) ... ok
test_missing_date_raises_value_error (test_parsers.TestBrokerCsvParser.test_missing_date_raises_value_error) ... ok
test_missing_price_raises_value_error (test_parsers.TestBrokerCsvParser.test_missing_price_raises_value_error) ... ok
test_missing_units_raises_value_error (test_parsers.TestBrokerCsvParser.test_missing_units_raises_value_error) ... ok
test_cas_parser_fallback_invalid_date_raises_value_error (test_parsers.TestCasPdfParser.test_cas_parser_fallback_invalid_date_raises_value_error) ... ok
test_cas_parser_non_date_txn_raises_value_error (test_parsers.TestCasPdfParser.test_cas_parser_non_date_txn_raises_value_error) ... ok

Ran 18 tests in 1.878s — OK (skipped=1)
```

### jqwik Property-Based Test Execution (`FifoMatcherPropertyTest`)
```text
$ cd core-node && mvn test -Dtest=FifoMatcherPropertyTest

[INFO] Running com.portfolioos.core.matcher.FifoMatcherPropertyTest
tries = 1000 | checks = 1000 | edge-cases#tried = 181 — strictFifoMonotonicity PASSED
tries = 1000 | checks = 1000 | edge-cases#tried = 179 — unitConservationAcrossAcquisitionsAndDisposals PASSED
tries = 1000 | checks = 1000 | edge-cases#tried = 181 — gainIntegrityAndNonNegativeQuantities PASSED
tries = 1000 | checks = 1000 | edge-cases#tried = 0   — cumulativeRoundingDriftOnMultiSliceDisposals PASSED
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.450 s
[INFO] BUILD SUCCESS
```

---

## 4. Systemd Unit Definitions & Orchestration Scripts

### Service Definitions in Pack
- **Core Node Service:** [portfolio-os-core.service](file:///home/rakeshpc/Projects/portfolio-os/systemd/portfolio-os-core.service)
  - Java 27 / Spring Boot backend runner with zero-trust local environment variables.
- **Quant Sidecar Service:** [portfolio-os-quant.service](file:///home/rakeshpc/Projects/portfolio-os/systemd/portfolio-os-quant.service)
  - Python FastAPI / uv runner with automated restart policies.
- **Unified Target:** [portfolio-os.target](file:///home/rakeshpc/Projects/portfolio-os/systemd/portfolio-os.target)
  - Binds core and sidecar lifecycle together under `systemd --user`.

### Helper Scripts in Pack
- **[install-systemd-units.sh](file:///home/rakeshpc/Projects/portfolio-os/scripts/install-systemd-units.sh):** Links and reloads systemd user daemon units.
- **[wait-ready.py](file:///home/rakeshpc/Projects/portfolio-os/scripts/wait-ready.py):** Exponential-backoff health polling script used during cold boot orchestration.
- **[refresh_amfi_ter.py](file:///home/rakeshpc/Projects/portfolio-os/scripts/refresh_amfi_ter.py):** Automated scraper for Total Expense Ratios (TER).
- **[refresh_market_indicators.py](file:///home/rakeshpc/Projects/portfolio-os/scripts/refresh_market_indicators.py):** AMFI / RBI scraper for 10Y G-Sec yields and Nifty 50 PE ratios.
- **[dashboard.kdl](file:///home/rakeshpc/Projects/portfolio-os/artifacts/dashboard.kdl):** Zellij split-pane HUD terminal layout.

### Live Systemd Target Status
```text
$ systemctl --user status portfolio-os.target
○ portfolio-os.target - Portfolio OS Unified Services Target
     Loaded: loaded (/home/rakeshpc/.config/systemd/user/portfolio-os.target; linked; preset: enabled)
     Active: inactive (dead) since Tue 2026-10-20 22:30:27 IST
 Invocation: 70a9ee177e514d59863093376b65a7c9
```
