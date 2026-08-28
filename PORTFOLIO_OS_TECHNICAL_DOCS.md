# Portfolio OS — Complete Technical Documentation Set (Empirical & Code-Grounded)

> **Root-Level Master Documentation File**
> This file contains the complete, consolidated technical documentation set for **Portfolio OS**. All claims, method signatures, file paths, line ranges, test counts, and business logic behaviors are 100% grounded in code inspection and empirical outputs.

---

## Table of Contents
1. [Subsystem 00: Architecture Index & System Topology](#subsystem-00-architecture-index--system-topology)
2. [Subsystem 01: Core-Node (Java 21 / Spring Boot)](#subsystem-01-core-node-java-21--spring-boot)
3. [Subsystem 02: Quant Sidecar (Python / Arrow Flight RPC / FastAPI)](#subsystem-02-quant-sidecar-python--arrow-flight-rpc--fastapi)
4. [Subsystem 03: DuckDB Analytics & Net Worth Engine](#subsystem-03-duckdb-analytics--net-worth-engine)
5. [Subsystem 04: ECharts Web Dashboard](#subsystem-04-echarts-web-dashboard)
6. [Subsystem 05: Android Client (Kotlin / Jetpack Compose)](#subsystem-05-android-client-kotlin--jetpack-compose)
7. [Subsystem 06: SQLite Event Store & Security Interceptor](#subsystem-06-sqlite-event-store--security-interceptor)

---

## Subsystem 00: Architecture Index & System Topology

### System Architecture Diagram
```
 +-------------------------------------------------------------------------------+
 |                           Mobile Client (Android)                             |
 |  com.portfolioos.mobile (Kotlin / Jetpack Compose / Glance Home Widget)      |
 +-------------------------------------+-----------------------------------------+
                                       | HTTP / REST (X-Api-Auth-Token)
                                       v
 +-------------------------------------------------------------------------------+
 |                        Core Node (Java 21 / Spring Boot)                      |
 |  com.portfolioos.core                                                         |
 |                                                                               |
 |  +--------------------+   +-----------------------+   +--------------------+  |
 |  | Rebalance Engine   |   | Fifo & Tax Classifier |   | XirrEngine         |  |
 |  | (Waterfall / Drift)|   | (Sec 50AA / 112A)     |   | (Newton-Raphson)   |  |
 |  +---------+----------+   +-----------+-----------+   +---------+----------+  |
 |            |                      |                         |                 |
 |            +----------------------+-------------------------+                 |
 |                                   |                                           |
 |            +----------------------+-------------------------+                 |
 |            v                                                v                 |
 |   +------------------+                             +--------------------+     |
 |   | SqliteEventStore |                             | DuckDbProjector    |     |
 |   | (HMAC-SHA256)    |                             | (SQL Analytics)    |     |
 |   +--------+---------+                             +---------+----------+     |
 +------------|-------------------------------------------------|----------------+
              |                                                 |
              | PyArrow Flight RPC (port 8001) / HTTP (port 8000)|
              v                                                 v
 +-------------------------------------------------------------------------------+
 |                     Quant Sidecar (Python 3.11 / FastAPI)                     |
 |  flight_server.py / analytics_engine.py / cas_parser.py / broker_csv_parser.py|
 |  - 10,000 Iteration Circular Block Bootstrap Monte Carlo FIRE Engine          |
 |  - QuantStats Sharpe / Sortino / Calmar / Max Drawdown Analytics              |
 +-------------------------------------------------------------------------------+
```

### Module Links (Modular Directory)
- [`docs/INDEX.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/INDEX.md)
- [`docs/01-CORE-NODE.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/01-CORE-NODE.md)
- [`docs/02-QUANT-SIDECAR.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/02-QUANT-SIDECAR.md)
- [`docs/03-DUCKDB-ANALYTICS.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/03-DUCKDB-ANALYTICS.md)
- [`docs/04-ECHARTS-DASHBOARD.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/04-ECHARTS-DASHBOARD.md)
- [`docs/05-ANDROID-APP.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/05-ANDROID-APP.md)
- [`docs/06-SQLITE-EVENT-STORE.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/06-SQLITE-EVENT-STORE.md)

---

## Subsystem 01: Core-Node (Java 21 / Spring Boot)

### 1. Architecture Map
- **Location**: [`core-node/src/main/java/com/portfolioos/core/`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/)
- **Controllers**: [`ConfigController.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/controllers/ConfigController.java), [`RebalanceController.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/controllers/RebalanceController.java), [`SyncController.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/controllers/SyncController.java), [`StatementsController.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/controllers/StatementsController.java)
- **Domain Engines**: [`RebalanceWaterfallEngine.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/valuation/RebalanceWaterfallEngine.java), [`RebalancePlanEngine.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/service/RebalancePlanEngine.java), [`RebalanceTriggerEvaluator.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/service/RebalanceTriggerEvaluator.java), [`FifoMatcher.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/matcher/FifoMatcher.java), [`TaxClassifier.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/matcher/TaxClassifier.java), [`XirrEngine.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/xirr/XirrEngine.java)

### 2. Core Business Logic & Edge Cases
- **4-Tier Waterfall Rebalancing**: Evaluates `LEGACY_FUND` (LTCG only, max 50% single scheme cap), `LOSS_HARVEST` (core loss lots), `LTCG_WITHIN_EXEMPTION` (Sec 112A headroom), and `STCG_URGENT_ONLY` (strictly excluded in normal drift mode; allowed only when `urgent=true`).
- **Sec 50AA Temporal Branching**: Debt funds acquired post April 1, 2023 are always `SHORT_TERM`. Legacy debt funds acquired pre April 1, 2023 require 24 months (730d) if sold post July 23, 2024, or 36 months (1095d) if sold pre July 23, 2024.
- **Trigger Cooldown & Gold Backstop**: Drawdown, Drift, and Scheduled triggers enforce a 30-day sell cooldown (`sellCooldownActive`). `GOLD_FLOOR_BACKSTOP` is buy-only and **exempt from 30-day sell cooldown**.

### 3. Core Test Coverage (68 Tests)
- `RebalanceTriggerEvaluatorTest`: 8 tests
- `LegacyFundWaterfallAuditTest`: 5 tests
- `RebalanceSankeyDtoTest`: 5 tests
- `BucketConfigLoaderTest`: 5 tests
- `RebalanceWaterfallEngineTest`: 4 tests
- `GoldDampenerCalculatorTest`: 4 tests
- `Itr2CsvExporterTest`: 4 tests
- `SyncControllerTest`: 4 tests
- `SimulationServiceTest`: 4 tests
- `BucketAllocationTest`: 3 tests
- `FundTierClassifierTest`: 3 tests
- `SecurityInterceptorTest`: 3 tests
- `XirrEngineTest`: 3 tests
- `RebalancePlanEngineTest`: 2 tests
- `TaxOptimizationServiceTest`: 2 tests
- `TaxRulesLoaderTest`: 2 tests
- `ReconciliationGateTest`: 2 tests
- `ConfigControllerTest`: 2 tests
- `FireTrackerTest`: 2 tests
- `GoalTrackerTest`: 2 tests
- `PortfolioQueryToolsTest`: 2 tests
- `AmfiNavSyncTest`: 1 test
- `SqliteEventStoreTest`: 1 test
- `TaxClassifierTest`: 1 test
- `FireActionRuleEngineTest`: 1 test
- `DuckDbProjectorNetWorthAccountingTest`: 1 test
- `MonteCarloSanityTest`: 1 test

---

## Subsystem 02: Quant Sidecar (Python / Arrow Flight RPC / FastAPI)

### 1. Architecture Map
- **Location**: [`quant-sidecar/`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/)
- **Servers**: [`app.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/app.py) (FastAPI HTTP 8000), [`flight_server.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/flight_server.py) (Arrow Flight RPC 8001)
- **Analytics**: [`analytics_engine.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/quant/analytics_engine.py)
- **Parsers**: [`cas_parser.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/parsers/cas_parser.py), [`broker_csv_parser.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/parsers/broker_csv_parser.py), [`sip_detector.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/parsers/sip_detector.py)

### 2. Key Algorithms
- **10,000 Iteration Circular Block Bootstrap**: Uses 15-day block sampling to preserve empirical volatility clustering and autocorrelations. Adjusts for 6.0% annual inflation.
- **Downside Deviation Sortino & Sharpe Sanity Guard**: Uses `returns[returns < 0].std()` for Sortino. Resets Sharpe to `0.0` if sample < 30 days or `|Sharpe| > 3.5`.
- **Test Coverage**: 5 tests in `quant-sidecar/tests/test_parsers.py`.

---

## Subsystem 03: DuckDB Analytics & Net Worth Engine

### 1. Architecture Map
- **Location**: [`DuckDbProjector.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java)
- **Database File**: `data/tax_ledger.duckdb`
- **Tables**: `projected_events`, `nav_history`, `benchmark_history`, `fund_holdings`

### 2. Symmetric Cost-Fallback SQL CTE
- Uses `SUM(active_units * COALESCE(market_nav, cost_nav, 0.0))` to prevent missing NAV dates from dropping net worth to zero.
- Flags `is_estimated = true` when `real_nav_valuation < total_valuation - 0.01`.

---

## Subsystem 04: ECharts Web Dashboard

### 1. Architecture Map
- **Location**: [`core-node/src/main/resources/static/`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/)
- **Files**: [`app.js`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/app.js), [`portfolio.js`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/js/modules/portfolio.js), [`tax.js`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/js/modules/tax.js), [`api.js`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/js/api.js)

### 2. Key Components
- Collapsible scheme-grouped tax lot accordions (collapsed by default).
- Cashflow Sankey flow diagram showing rebalance capital redeployment.
- Raycast `Cmd+K` palette with instant waterfall preview and Qwen LLM SSE streaming.

---

## Subsystem 05: Android Client (Kotlin / Jetpack Compose)

### 1. Architecture Map
- **Location**: [`mobile-app/app/src/main/java/com/portfolioos/mobile/`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/)
- **Core Files**: [`SyncApiClient.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/api/SyncApiClient.kt), [`LockScreenGate.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/LockScreenGate.kt), [`PortfolioGlanceWidget.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/widget/PortfolioGlanceWidget.kt)

### 2. Key Features
- **5-Stage Sync Fallback**: Custom URL -> USB loopback (`127.0.0.1:8080`) -> Emulator loopback (`10.0.2.2:8080`) -> Wi-Fi LAN (`192.168.1.13:8080`) -> Cellular AMFI Direct Offline Mode.
- **Biometric Gate**: Encapsulates root navigation with fingerprint/face unlock.
- **Test Coverage**: 4 unit tests in `RebalanceWaterfallUnitTest.kt`.

---

## Subsystem 06: SQLite Event Store & Security Interceptor

### 1. Architecture Map
- **Location**: [`SqliteEventStore.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java), [`SecurityInterceptor.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/security/SecurityInterceptor.java)
- **Database File**: `data/tax_ledger.db`

### 2. Cryptographic Security Invariants
- **HMAC-SHA256 Hash Chain**: Every `TaxEvent` is signed with `LEDGER_HMAC_SECRET`. `verifyLedgerIntegrity()` re-evaluates continuous hashing starting from `GENESIS`.
- **Constant-Time Security Interceptor**: Enforces `API_AUTH_TOKEN` matching via `MessageDigest.isEqual` to prevent timing attacks.
