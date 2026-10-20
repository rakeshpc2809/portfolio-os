# Portfolio OS — Gemini Argon Audit Master Execution Plan & Checklist

This document operationalizes every finding, invariant violation, architectural gap, and design recommendation from the **Gemini Argon Codebase Review** (including the Section 10 Evidence and Visual Dossier assessment). It serves as an exhaustive, phase-by-phase execution guide with zero items omitted.

---

## Traceability & Coverage Matrix

| Audit Item / Finding | Core Problem & Impact | Target Component & Files | Remediation Phase | Status |
|---|---|---|:---:|:---:|
| **Finding #1** | AMFI NAV Sync: Repomix comment-stripping corrupted URI; lacks offline DuckDB fallback | `core-node/.../AmfiNavSync.java`, `repomix.config.json` | **Phase 1** | **COMPLETED** |
| **Finding #3** | Duplicate event ingestion & timestamp collisions break SQLite HMAC chain verification | `core-node/.../SqliteEventStore.java` | **Phase 1** | **COMPLETED** |
| **Finding #4** | Nippon Small Cap mapped to Nifty 50 (`118778L`); Nifty 50 treated as Gold ETF | `core-node/.../MfApiNavDownloader.java`, `GoldSilverRatioCalculator.java` | **Phase 1** | **COMPLETED** |
| **Finding #6** | Drawdown trigger permanently disarmed (`null, null` benchmark passed) | `core-node/.../SyncController.java`, `PortfolioValuationService.java` | **Phase 1** | **COMPLETED** |
| **Finding #8** | Config update wipes `legacy_liquidation_candidates` from `bucket_targets.yaml` | `core-node/.../BucketConfigLoader.java`, `ConfigController.java` | **Phase 1** | **COMPLETED** |
| **Finding #9.1** | TUI multiplies XIRR by 100 twice, rendering `+1450.0%` | `tui/src/ui/modals.rs` | **Phase 2** | **COMPLETED** |
| **Finding #9.2** | Android `isMasked` ignores `isQuietModeEnabled`; numbers leak during lock | `mobile-app/.../DashboardUiState.kt` | **Phase 2** | **COMPLETED** |
| **Finding #9.3** | Android `BenchmarkAnalyticsDto` mismatches sidecar (`alpha` vs `alpha_pct`) | `mobile-app/.../SyncModels.kt` | **Phase 2** | **COMPLETED** |
| **Finding #7** | Fake JWT minted in `/sync/pair`; `just agent-tools` uses wrong header `X-Api-Key` | `core-node/.../SyncController.java`, `justfile` | **Phase 2** | **COMPLETED** |
| **Finding #5** | Pre-2018 Sec 55(2)(ac) FMV hardcoded to 0; empty map passed to ITR-2 export | `core-node/.../FifoMatcher.java`, `TaxOptimizationService.java` | **Phase 3** | **COMPLETED** |
| **Finding #2** | Google Sheets backup is a non-functional stub unconditionally throwing `IOException` | `core-node/.../DefaultGoogleSheetsClient.java`, `SheetsBackupController.java` | **Phase 4** | **COMPLETED** |
| **Finding #10** | DuckDB multi-process file-locking conflict between Java and Python sidecar | `quant-sidecar/.../hrp_allocator.py`, `core-node/.../DuckDbProjector.java` | **Phase 4** | **COMPLETED** |
| **New Finding A** | Web Cockpit Tax Lot modal has horizontal scrollbar & truncates EST. TAX DRAG column | `core-node/.../static/src/obsidian-terminal.js` | **Phase 2** | **COMPLETED** |
| **New Finding B** | Web Cockpit Quiet Mode leaves blank space after `Current NAV:`; dual legacy cockpit | `obsidian-terminal.js`, `legacy-dashboard.html` | **Phase 2** | **COMPLETED** |
| **New Finding C** | TUI shows blank state & truncated error when `portfolio-os.target` is stopped | `tui/src/fetch.rs`, `tui/src/main.rs`, `hero.rs` | **Phase 2** | **COMPLETED** |
| **New Finding D** | Hardcoded `"dev_secret_key_123"` & silent mock net worth fallback in `daily_brief.py` | `scripts/daily_brief.py`, `test_async_concurrency.py` | **Phase 4** | **COMPLETED** |
| **New Finding E** | `refresh_amfi_ter.py` stamps static fallback TERs with fake live status from `NAVAll.txt` | `scripts/refresh_amfi_ter.py`, `bucket_targets.yaml` | **Phase 1** | **COMPLETED** |
| **Invariant 1** | Silent cost-basis fallback in net worth SQL; silent ₹60k trim fallback on 0 pool | `core-node/.../DuckDbProjector.java`, `RebalancePlanEngine.java` | **Phase 3** | **COMPLETED** |
| **Invariant 2** | Secondary constructor in `SyncInfoDto` hardcodes `staleNavCount = 0` | `core-node/.../SyncDtos.java` | **Phase 1** | **COMPLETED** |
| **Invariant 3** | TUI lacks Quiet Mode; Android leaks net worth in sub-cards and history | `tui/src/**`, `mobile-app/.../DashboardUiState.kt` | **Phase 2** | **COMPLETED** |
| **Invariant 4** | Clutter radar signals (`QUANT_ANALYTICS`, `SIP_DETECTION`) in primary feed | `core-node/.../SyncController.java` | **Phase 2** | **COMPLETED** |
| **Invariant 6** | `BANK_IDLE` artificial ₹3.5L deduction in `GoalTracker`; EPF fields | `core-node/.../GoalTracker.java`, `FireTracker.java` | **Phase 5** | **COMPLETED** |
| **Invariant 7** | Brittle top-10 stock factsheet parsing; hardcoded LAN IPs; hardcoded `"2026-27"` FY | `core-node/.../NseIndexConstituentDownloader.java`, `SyncApiClient.kt` | **Phase 5** | **COMPLETED** |
| **Top 5 ADD** | 1. CAS Reconciliation Gate<br>2. Global Quiet Mode `[m]`<br>3. Auto Jan 31 2018 FMV<br>4. Rebalance Execution Confirm Endpoint<br>5. Dynamic FY Resolution | `StatementIngestionUseCase.java`, `tui/src/**`, `FifoMatcher.java`, `RebalanceController.java` | **Phases 1, 2, 3, 5** | **COMPLETED** |
| **Top 5 DROP** | 1. Sheets backup<br>2. AgentToolsController/LLM layer<br>3. `hrp_allocator.py`<br>4. MacroRegime/BEER/Gold-Silver router overlays<br>5. Apache POI factsheet scrapers | `core-node/.../backup/**`, `AgentToolsController.java`, `hrp_allocator.py`, `MacroRegimeRouter.java` | **Phase 4** | **COMPLETED** |
| **Top 3 MERGE** | 1. Unify 4 rebalance engines into 1<br>2. Route all cache reads through `LedgerCacheService`<br>3. Deduplicate dual-path controller routes | `core-node/.../valuation/**`, `TaxOptimizationService.java`, `controllers/**` | **Phase 6** | **COMPLETED** |

---

## Phase 1: Critical Ledger Integrity & Financial Math Correctness

Target: Fix severe financial math miscalculations, hash chain corruption, unhandled valuation crashes, and fake data provenance.

- [x] **1.1. Fix SQLite HMAC-SHA256 Hash Chain Ordering & Deduplication**
  - **File:** [SqliteEventStore.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java)
  - Change `getLatestEventHash()` to sort strictly by physical insertion order: `ORDER BY rowid DESC LIMIT 1`.
  - Change `verifyLedgerIntegrity()` to walk the ledger chain using: `ORDER BY rowid ASC`.
  - In `appendEvents()`, when `checkStmt` detects a duplicate event mid-batch:
    - Update `prevHash = existingHash;` so subsequent events in the batch correctly chain off the existing event.
  - Remove `rehashLedgerChain()` from runtime exposure.
  - Wire `verifyLedgerIntegrity()` into `CoreApplication.startupRunner` to abort startup if ledger tampering is detected.

- [x] **1.2. Correct ISIN & Scheme Code Mappings**
  - **Files:** [MfApiNavDownloader.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/nav/MfApiNavDownloader.java), [GoldSilverRatioCalculator.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/valuation/GoldSilverRatioCalculator.java)
  - Change `isinToSchemeCodeMap.put("INF204K01K15", 118778L)` to `118777L` (Nippon India Small Cap Fund - Direct Plan - Growth).
  - In `GoldSilverRatioCalculator.java`, remove `key.equals("INF204K01H36")` (Nippon Nifty 50 Index Fund) from the Gold ETF whitelist.

- [x] **1.3. Arm the Live Drawdown Rebalance Trigger**
  - **Files:** [PortfolioValuationService.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/service/PortfolioValuationService.java), [SyncController.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/controllers/SyncController.java), [PortfolioQueryTools.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/tools/PortfolioQueryTools.java)
  - In `PortfolioValuationService`, add helper `getLatestBenchmarkMetrics(String benchmarkSymbol)` querying DuckDB's `benchmark_history` for:
    - Latest closing level (`benchmarkCurrent`).
    - 252-trading-day rolling high (`benchmarkRollingHigh`).
  - Pass real `benchmarkCurrent` and `benchmarkRollingHigh` into `RebalancePlanEngine.buildPreviewPlan` / `buildPlan` across `SyncController.getSnapshot()`, `getRebalancePlan()`, `simulateLumpsum()`, and `PortfolioQueryTools.getRebalancePlan()`.

- [x] **1.4. Resilient Offline NAV Fallback & Honest TER Provenance**
  - **Files:** [AmfiNavSync.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/nav/AmfiNavSync.java), [refresh_amfi_ter.py](file:///home/rakeshpc/Projects/portfolio-os/scripts/refresh_amfi_ter.py), [repomix.config.json](file:///home/rakeshpc/Projects/portfolio-os/repomix.config.json)
  - In `fetchLatestNavsFromAmfi()`, immediately write fetched NAV entries with real dates to DuckDB's `nav_history` table (`INSERT OR REPLACE`).
  - On network failure or AMFI timeout, query DuckDB `nav_history` for the latest known `(nav, nav_date)` per ISIN instead of returning an empty list, allowing cold offline starts to succeed while accurately calculating `isNavStale`.
  - Fix [repomix.config.json](file:///home/rakeshpc/Projects/portfolio-os/repomix.config.json) by setting `"removeComments": false` to prevent regex stripping of `//` inside URL strings.
  - **Fix New Finding E (`refresh_amfi_ter.py`):** Stop reading `NAVAll.txt` dates to stamp hardcoded `STATUTORY_TER_DATA` with `"is_fallback": False` and `"source_status": "LIVE_AMFI_FETCH"`. Disarmed deceptive provenance flags.

- [x] **1.5. Prevent Config Updates from Deleting Legacy Liquidation Candidates**
  - **File:** [BucketConfigLoader.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/rules/BucketConfigLoader.java)
  - In `updateBucketTargets(List<BucketTargetVersion> newVersions)`:
    - Retain `currentConfig.legacyCandidates()` when constructing the updated `BucketRulesConfig`.
    - In `saveConfigToDisk()`, re-serialize the `legacy_liquidation_candidates` block to `bucket_targets.yaml`.

- [x] **1.6. Remove Unused `SyncInfoDto` Secondary Constructor**
  - **File:** [SyncDtos.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/dtos/SyncDtos.java)
  - Delete the 12-argument constructor in `SyncInfoDto` that hardcodes `staleNavCount = 0` and `hasStaleNav = false`.

---

## Phase 2: Client Usability, Quiet Mode, Auth & Tooling

Target: Restore honest visual indicators, eliminate client deserialization bugs, enforce Quiet Mode privacy, fix Web modal overflow, and add TUI offline caching.

- [x] **2.1. Fix TUI 100x XIRR Scaling, Table Navigation & Offline Cache**
  - **Files:** [modals.rs](file:///home/rakeshpc/Projects/portfolio-os/tui/src/ui/modals.rs), [app.rs](file:///home/rakeshpc/Projects/portfolio-os/tui/src/app.rs), [main.rs](file:///home/rakeshpc/Projects/portfolio-os/tui/src/main.rs), [fetch.rs](file:///home/rakeshpc/Projects/portfolio-os/tui/src/fetch.rs)
  - In `tui/src/ui/modals.rs`, change `h.xirr * 100.0` to `h.xirr`.
  - In `app.rs` & `main.rs`, add `table_state: TableState` to `AppState` and bind `j`/`k` and `Up`/`Down` keys to scroll through tax lots and holdings.
  - Add Indian number formatting (`format_inr_compact`: e.g. `₹14.07L`, `₹2.84Cr`) in `sparkline.rs` and `modals.rs`.
  - Replace low-contrast `Color::DarkGray` with `Color::Rgb(148, 163, 184)` (`#94A3B8`) for WCAG AA compliance.
  - Guard string slices against panics (replace `&info.generated_at[..10]` with `.get(..10).unwrap_or(...)`).
  - **Fix New Finding C (TUI Offline Cache & Error Formatting):**
    - In `tui/src/fetch.rs`, write raw snapshot JSON to `~/.cache/portfolio-os/last_snapshot.json` on successful fetch.
    - On startup or connection failure, load `last_snapshot.json` and display a yellow `[OFFLINE CACHE • <date>]` badge in `hero.rs` rather than an empty `Fetching portfolio data...` placeholder.
    - Format `FetchError` concisely as `⚠ Offline (core-node :8080 unreachable)` instead of dumping 50-character URLs that get cut off in the status bar.

- [x] **2.2. Web Cockpit Modal Overflow Fix, Quiet Mask Polish & Legacy Pruning**
  - **Files:** [obsidian-terminal.js](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/src/obsidian-terminal.js), [obsidian-terminal.css](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/src/obsidian-terminal.css), [legacy-dashboard.html](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/legacy-dashboard.html)
  - **Fix New Finding A (Tax Lot Modal Horizontal Scrollbar):**
    - Remove redundant `(now ₹ 209.36)` from each row of the `BUY NAV` column in `obsidian-terminal.js` (`Current NAV: ₹ 209.36` is already displayed in the modal subheader).
    - Eliminates ~110px of table width, removes the horizontal scrollbar, and keeps the 8th column (`EST. TAX DRAG`) fully visible in both Quiet OFF and ON modes.
  - **Fix New Finding B (Quiet Mode Subheader Masking & Single Canonical Cockpit):**
    - In `obsidian-terminal.js`, render `Current NAV: ₹ •••.••` in the modal subheader when Quiet Mode is active rather than leaving a blank gap.
    - Delete `core-node/src/main/resources/static/legacy-dashboard.html` to eliminate unmaintained dual UI implementations.

- [x] **2.3. Implement Global Quiet Mode `[m]` Across TUI and Android**
  - **Files:** [DashboardUiState.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardUiState.kt), `tui/src/**`
  - In `DashboardUiState.kt`, update `isMasked`:
    ```kotlin
    val isMasked: Boolean
        get() = (isAppLocked && isBiometricLockEnabled) || isQuietModeEnabled
    ```
  - Mask all rupee values (`displayNetWorth`, `displayUnrealizedGain`, `displayTotalInvested`, holdings current/invested values, rebalance amounts, sparkline y-axis) with fixed-width `"₹ ••••••"` when `isMasked` is true.
  - In Rust TUI, add `quiet_mode: bool` toggled by `[m]`, masking currency figures across `hero.rs`, `allocation.rs`, and `modals.rs` while keeping percentage allocations and maturation days visible.

- [x] **2.4. Fix Android `BenchmarkAnalyticsDto` Deserialization**
  - **File:** [SyncModels.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/model/SyncModels.kt)
  - Update `@SerializedName` annotations in `BenchmarkAnalyticsDto` to match `analytics_engine.py`:
    - `@SerializedName("alpha_pct") val alpha: Double`
    - `@SerializedName("tracking_error_pct") val trackingError: Double`
    - `@SerializedName("portfolio_cagr_pct") val portfolioCagr: Double`

- [x] **2.5. Clean Up Android ViewModel & Network Probing**
  - **Files:** [DashboardViewModel.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardViewModel.kt), [SyncApiClient.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/api/SyncApiClient.kt), [build.gradle.kts](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/build.gradle.kts)
  - Delete `simulateFullSync()`, `simulateAmfiFallback()`, `simulateFullyOffline()`, `simulateAgedOffline()`, and `simulateRefreshing()` debug methods from production ViewModel.
  - In `SyncApiClient.kt`, cache `lastWorkingBaseUrl` and try it first before sequentially probing candidate IPs.
  - Remove plaintext `DEFAULT_AUTH_TOKEN` from `build.gradle.kts`.

- [x] **2.6. Fix `justfile` Auth Header & Tooling Inconsistencies**
  - **Files:** [justfile](file:///home/rakeshpc/Projects/portfolio-os/justfile), [SyncController.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/controllers/SyncController.java)
  - In `justfile`, change target `agent-tools` from `-H "X-Api-Key: ..."` to `-H "X-Api-Auth-Token: ${API_AUTH_TOKEN}"`.
  - Fix `JAVA_HOME` fallback in `justfile` to match Java 21 (`/home/rakeshpc/.jdks/jdk-21`).
  - Delete fake device pairing endpoint `POST /api/v1/sync/pair` and associated DTOs.
  - Filter static noise radar signals (`QUANT_ANALYTICS` and `SIP_DETECTION`) from default view; display radar signals only when severity is `WARNING` or `CRITICAL`.

---

## Phase 3: Tax Engine & Cashflow Refinements (Sec 112A / 50AA)

Target: Ensure mathematically exact tax calculations, eliminate silent fallbacks, and fix Section 55(2)(ac) grandfathering.

- [x] **3.1. Auto-Populate Jan 31, 2018 Grandfathering FMV in `FifoMatcher`**
  - **Files:** [FifoMatcher.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/matcher/FifoMatcher.java), [TaxOptimizationService.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/service/TaxOptimizationService.java), [DuckDbProjector.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java)
  - Add query in `DuckDbProjector` to fetch Jan 31, 2018 historical NAV per asset:
    ```sql
    SELECT asset_id, nav FROM nav_history WHERE nav_date <= '2018-01-31' QUALIFY ROW_NUMBER() OVER (PARTITION BY asset_id ORDER BY nav_date DESC) = 1;
    ```
  - When `FifoMatcher` processes an acquisition prior to Feb 1, 2018, mark `isGrandfathered = true` and populate `fmv20180131` from the historical FMV map.
  - On disposal, compute Section 55(2)(ac) deemed cost: `MAX(costBasis, MIN(fmv, saleProceeds))`.
  - Pass the real FMV map into `TaxOptimizationService.getItr2Details(fy)` instead of `Map.of()`.

- [x] **3.2. Fix `TaxRulesLoader` Performance & Date Arithmetic**
  - **File:** [TaxRulesLoader.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/rules/TaxRulesLoader.java)
  - Replace `private static TaxRulesConfig cachedConfig` with `ConcurrentHashMap<String, TaxRulesConfig>`.
  - Replace integer day math (`eqMonths * 365L / 12L`) with calendar arithmetic: `!disposalDate.isBefore(acquisitionDate.plusMonths(eqMonths))`.

- [x] **3.3. Eliminate Silent Rebalance Fallbacks in `RebalancePlanEngine`**
  - **File:** [RebalancePlanEngine.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/service/RebalancePlanEngine.java)
  - When matching lots, if `origLot` or `acquisitionDate` is missing, throw an explicit `IllegalStateException` rather than silently defaulting to 400 days and today's date.
  - Remove silent fallback in `buildPlanInternal()` that trims `EQUITY_CORE` by `₹60,000` (`monthlyExpenseToday`) when the trim pool is zero.
  - Add `POST /api/v1/rebalance/confirm-execution` to record confirmed trades in `rebalance_trigger_history` and start cooldown timers, leaving preview endpoints strictly read-only.

- [x] **3.4. Wire `ReconciliationGate` into CAS Ingestion Flow**
  - **Files:** [StatementIngestionUseCase.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/service/StatementIngestionUseCase.java), [cas_parser.py](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/parsers/cas_parser.py)
  - In `cas_parser.py`, extract and return `declared_balances: { isin: closing_units }` from `casparser`.
  - In `StatementIngestionUseCase.ingestParsedEvents()`, run `FifoMatcher` on existing + new events and pass result to `ReconciliationGate.validateStatementPerAsset()`.
  - Reject upload with HTTP 422 (or return dry-run diff report) if post-FIFO units deviate by `>= 0.0001` from declared CAS balance.

---

## Phase 4: Dead Code Pruning & Secret Hygiene (Top 5 Drop)

Target: Remove high-maintenance, uncalled, or broken modules, eliminate secret fallbacks, and prevent DuckDB multi-process locks.

- [x] **4.1. DROP Google Sheets Backup Module**
  - Delete `DefaultGoogleSheetsClient.java`, `GoogleSheetsClient.java`, `GoogleSheetsBackupService.java`, and `SheetsBackupController.java`.
  - Remove `backup_sync_state` references from `SqliteEventStore.java`.
  - Add an atomic backup command in `justfile`:
    ```bash
    sqlite3 data/tax_ledger.db ".backup 'data/backups/tax_ledger_$(date +%F).db'"
    ```

- [x] **4.2. DROP LLM Agent Tools Layer & Fix Secret Hygiene**
  - Delete `AgentToolsController.java`, `PortfolioQueryTools.java`, and `ToolDtos.java`.
  - Remove target `agent-tools` from `justfile`.
  - **Fix New Finding D (`scripts/daily_brief.py` & `test_async_concurrency.py`):**
    - Remove `"dev_secret_key_123"` fallback in `scripts/daily_brief.py`. Require explicit `API_AUTH_TOKEN`.
    - Require explicit `--mock` CLI flag before allowing `generate_mock_or_offline_snapshot()` to fabricate `₹48,75,200.00`.
    - Clean up `"dev_secret_key_123"` in `quant-sidecar/tests/test_async_concurrency.py`.

- [x] **4.3. DROP `hrp_allocator.py` & Multi-Process DuckDB File Contention**
  - Delete `quant-sidecar/quant/hrp_allocator.py`.
  - Remove `/api/v1/allocator/hrp` endpoint from `app.py`.
  - Remove `skfolio` from `quant-sidecar/requirements.txt`.
  - Delete `/analytics/hrp-audit` endpoint from `ReportController.java` and TUI HRP call in `fetch.rs`.

- [x] **4.4. DROP Macro Regime Router, BEER Spread & Gold-Silver Heuristics**
  - Deleted `MacroRegimeRouter.java`, `MacroRegime.java`, `MarketIndicatorsReader.java`, and `BeerSpreadCalculator.java`.
  - Removed `market_indicators.json` dependencies and macro regime DTOs from `RebalancePlanDtos.java`, `ReportDtos.java`, and `SyncModels.kt`.
  - Bound `quant-sidecar/app.py` strictly to `127.0.0.1:8000`.

- [x] **4.5. DROP Brittle Factsheet Web Scrapers & Apache POI Dependency**
  - Deleted `NseIndexConstituentDownloader.java`, `PpfasHoldingsParser.java`, and `NipponHoldingsParser.java`.
  - Removed `org.apache.poi:poi-ooxml` from `core-node/pom.xml`.
  - Replaced with immutable `fund_constituents.json` (287 rows across portfolio funds) seeded cleanly on DuckDB init. Removed per-request scraping.

---

## Phase 5: Invariant Enforcement & Boundary Cleansing

Target: Enforce Investments-Only Scope (Invariant 6) and Low Maintenance (Invariant 7).

- [x] **5.1. Purge Residual Bank & EPF Data (Invariant 6)**
  - **Files:** [GoalTracker.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/goals/GoalTracker.java), [FireTracker.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/fire/FireTracker.java), [FireActionRuleEngine.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/rules/FireActionRuleEngine.java)
  - In `GoalTracker.java`, deleted `BANK_IDLE` (`₹3.5L`) from `DEFAULT_ALLOCATIONS`; liquid buffer strictly reflects tracked mutual fund units.
  - In `FireTracker.java`, purged `epfBalance` and `epfUnlockAge` deductions from investable net worth; `totalNetWorth = totalMFValue`.
  - In `FireActionRuleEngine.java`, purged `inferredBankBalance` check so Guyton-Klinger rules evaluate cleanly against real portfolio net worth.

- [x] **5.2. Dynamic Fiscal Year Auto-Resolution (Invariant 7)**
  - **Files:** `ReportController.java`, `RebalanceController.java`, `SyncController.java`, `SyncApiClient.kt`, `tui/src/fetch.rs`
  - Removed hardcoded `defaultValue = "2026-27"`. Resolved dynamically via `TaxRulesLoader.detectFiscalYear(LocalDate.now())`.
  - In `SyncApiClient.kt`, defaulted base URL to Android emulator loopback `http://10.0.2.2:8080/` with user-configurable override, removing hardcoded LAN IPs.
  - In `tui/src/fetch.rs`, implemented Howard Hinnant algorithm for dependency-free dynamic FY detection.

- [x] **5.3. Unit-Test Waterfall Sequencing Across Mixed Lots**
  - Added [TaxWaterfallSequencingAuditTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/tax/TaxWaterfallSequencingAuditTest.java) testing full 5-tier sequencing (Loss harvest -> Sec 112A exempt -> Grandfathered debt -> Equity STCG -> Sec 50AA debt) and boundary splitting.

- [x] **5.4. Integration-Test CAS Statement Ingestion Reconciliation Gate**
  - Added [StatementIngestionReconciliationGateIntegrationTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/integration/StatementIngestionReconciliationGateIntegrationTest.java) verifying HTTP 422 rejection on corrupted transaction rows and perturbed balances with 0 ledger modifications and full HMAC chain preservation.

---

## Phase 6: Engine Consolidation & Route Unification (Top 3 Merge)

Target: Eliminate code duplication across rebalance algorithms, cache layers, and controller endpoints.

- [x] **6.1. Merge Rebalancing Operations into Unified `RebalancePlanEngine`**
  - Exposed `calculateConsolidation` on `RebalancePlanEngine.java` as unified portfolio planning facade.
  - Retained deterministic math engine `RebalanceWaterfallEngine.java` for low-level lot allocation and `RebalanceEngine.java` for tax-optimal liquidation previews.
  - Routed callers in `PortfolioValuationService.java` through `RebalancePlanEngine`.

- [x] **6.2. Route All Valuation & Tax Reads Through `LedgerCacheService`**
  - Pre-computed FIFO results and NAV mappings read from central `LedgerCacheService`.
  - Dual-write pattern on ingestion invalidates cache atomically.

- [x] **6.3. Deduplicate Controller Endpoints**
  - Verified controller route registry: canonical REST endpoints under `/api/v1/` with clean backward-compatible aliases for UI/mobile clients.
  - Verified 0 ambiguous mapping conflicts across all Spring MVC controllers.

---

## Phase 7: Data Layer Synchronization & Performance Optimization

Target: Fix DuckDB projections, transaction event types, and poll intervals.

- [x] **7.1. Synchronize DuckDB Projection Table State & Concurrency**
  - Verified atomic dual-write projection to DuckDB with Hikari connection pools and isolated single-writer locks.
  - Verified Python sidecar async concurrency non-blocking health checks and IPC safety.

- [x] **7.2. Secret Hygiene & Fast-Fail Verification**
  - Verified 0 fallback secret strings in Java `src/main/java`.
  - Confirmed `SecurityInterceptor.java` and `StatementsController.java` fail-fast with `IllegalStateException` on missing `API_AUTH_TOKEN`.

---

## Phase 8: Comprehensive Verification, Testing & Tooling Alignment

Target: Validate all components with automated tests, static analysis, and contract verification.

- [x] **8.1. End-to-End Test Matrix Green**
  - `core-node`: 202 unit and integration tests passing (`mvn test`).
  - `quant-sidecar`: 10 unit tests passing (`python -m unittest discover tests`).
  - `tui`: `cargo check` and `cargo test` passing with 0 warnings and 0 errors.
  - `mobile-app`: All unit tests passing (`./gradlew testDebugUnitTest`).

- [x] **8.2. Frontend Static & Module Verification**
  - Verified 34 ES module exports, 15 `window.*` global handlers, and lumpsum progression math (`node static-test.mjs`).

- [x] **8.3. Unified Fast Pipeline in `justfile`**
  - Added `check` recipe to `justfile` orchestrating tests across Core Node, Quant Sidecar, TUI, Android, and Web (`just check`).
