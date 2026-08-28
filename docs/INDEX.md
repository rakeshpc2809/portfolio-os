# Portfolio OS — Technical Documentation Set

This documentation set provides an empirical, code-grounded architectural reference for the entire **Portfolio OS** system. Every claim, data flow, algorithm, test count, and known issue documented across these pages is anchored directly in source code file paths, method signatures, line ranges, and real execution outputs.

---

## 1. System Architecture Topology

Portfolio OS is built as a multi-tier, event-driven portfolio management and tax-aware rebalancing engine.

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

---

## 2. Master Documentation Index

| Module Document | Subsystem Scope | Core Files & Technologies |
| :--- | :--- | :--- |
| **[`01-CORE-NODE.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/01-CORE-NODE.md)** | **Core Java 21 / Spring Boot Backend** | [`RebalanceWaterfallEngine.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/valuation/RebalanceWaterfallEngine.java), [`RebalancePlanEngine.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/service/RebalancePlanEngine.java), [`FifoMatcher.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/matcher/FifoMatcher.java), [`TaxClassifier.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/matcher/TaxClassifier.java), [`XirrEngine.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/xirr/XirrEngine.java) |
| **[`02-QUANT-SIDECAR.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/02-QUANT-SIDECAR.md)** | **Python / Arrow Flight RPC Sidecar** | [`app.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/app.py), [`flight_server.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/flight_server.py), [`analytics_engine.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/quant/analytics_engine.py), [`cas_parser.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/parsers/cas_parser.py) |
| **[`03-DUCKDB-ANALYTICS.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/03-DUCKDB-ANALYTICS.md)** | **DuckDB Analytics & Net Worth Engine** | [`DuckDbProjector.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java) |
| **[`04-ECHARTS-DASHBOARD.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/04-ECHARTS-DASHBOARD.md)** | **ECharts Modern Web Dashboard** | [`app.js`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/app.js), [`portfolio.js`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/js/modules/portfolio.js), [`tax.js`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/js/modules/tax.js) |
| **[`05-ANDROID-APP.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/05-ANDROID-APP.md)** | **Kotlin / Jetpack Compose Android Client** | [`MainActivity.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/MainActivity.kt), [`SyncApiClient.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/api/SyncApiClient.kt), [`LockScreenGate.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/LockScreenGate.kt), [`PortfolioGlanceWidget.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/widget/PortfolioGlanceWidget.kt) |
| **[`06-SQLITE-EVENT-STORE.md`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/docs/06-SQLITE-EVENT-STORE.md)** | **SQLite Event Store & Persistence** | [`SqliteEventStore.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java), [`SecurityInterceptor.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/security/SecurityInterceptor.java) |

---

## 3. Cross-Cutting System Invariants

1. **Dual-Write Consistency**: Every financial event ingested via [`StatementIngestionUseCase.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/service/StatementIngestionUseCase.java#L57-L66) is written first to the immutable SQLite ledger [`SqliteEventStore`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java#L147) and immediately re-projected into embedded DuckDB [`DuckDbProjector`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java#L255).
2. **Zero Hardcoded Returns in Live Simulation**: Monte Carlo FIRE simulations in [`analytics_engine.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/quant/analytics_engine.py#L124-L134) require >= 750 daily return data points (3 years). If fewer points exist, the system explicitly labels the run `SYNTHETIC_MARKET_BENCHMARK` instead of masking it as empirical.
3. **Cryptographic Event Chaining**: Every `TaxEvent` in SQLite carries an HMAC-SHA256 signature calculated over its canonical fields and the previous event's hash (`GENESIS` for the initial node), verified by [`verifyLedgerIntegrity()`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java#L253).
4. **Section 50AA Tax Temporal Branching**: Debt mutual fund tax classification in [`TaxClassifier.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/matcher/TaxClassifier.java#L70-L88) strictly branches based on whether acquisition occurred pre/post April 1, 2023, and whether disposal occurred pre/post July 23, 2024 (Finance Act 2024 amendments).
