# Portfolio OS v3.0 — AI System Design & Review Index

> **Notice for Claude / Reviewing AI Models**: This document serves as the master architectural index for **Portfolio OS v3.0**, a local-first investment management system built with an append-only SHA-256 HMAC event-sourcing ledger (Java Core Node), a quantitative factor engine (Python PyArrow Flight), a Vue 3 Vapor web cockpit, and an offline-first Jetpack Compose Android companion app.

---

## 📦 Ultra-Compressed Repomix Codebase Bundles

The entire repository source code has been packed into three minimal, token-compressed Markdown bundles for low-cost context ingestion:

| Component | Repository Path | Repomix Output Pack | Token Count | Main Architecture / Tech Stack |
| :--- | :--- | :--- | :---: | :--- |
| **Java Core Node** | `core-node/` | [`repomix-core.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-core.md) | **30,283** | Java 21, Spring Boot 3.2.5, SQLite HMAC, DuckDB, Arrow Flight |
| **Python Quant Sidecar** | `quant-sidecar/` | [`repomix-quant.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-quant.md) | **3,475** | Python 3.12, PyArrow Flight RPC, Polars, Hurst/HMM |
| **Android Companion App** | `mobile-app/` | [`repomix-mobile.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-mobile.md) | **10,425** | Kotlin, Jetpack Compose M3 Expressive, Retrofit 2 |
| **TOTAL SYSTEM** | Repository Root | **3 Repomix Bundles** | **~44,183** | **Unified Architecture** |

---

## 🏛️ System Component Breakdown for Review

### 1. Java Core Node ([`repomix-core.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-core.md))
- [`SqliteEventStore.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java): Append-only event store with SHA-256 HMAC cryptographic chain verification.
- [`DuckDbProjector.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java): Automatic DuckDB columnar projection store for fast OLAP queries.
- [`FifoMatcher.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/matcher/FifoMatcher.java): Indian IT Act FIFO tax lot pairing engine supporting bonus, corporate splits, and 31-Jan-2018 grandfathering FMV rules.
- [`TaxClassifier.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/matcher/TaxClassifier.java): Indian tax law classifier (Equity Sec 112A, Debt Sec 50AA, Gold/SGB).
- [`SyncController.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/controllers/SyncController.java): Mobile synchronization endpoint `/api/v1/sync/snapshot` providing XIRR, Net Worth in Rupees, scheme-grouped tax lots, and aggregated priority AI Radar signals.

### 2. Python Quant Sidecar ([`repomix-quant.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-quant.md))
- [`flight_server.py`](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/flight_server.py): Apache Arrow Flight RPC server on gRPC port 8001.
- [`quant_engine.py`](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/quant/quant_engine.py): Hurst Exponent ($H$), Ornstein-Uhlenbeck (OU) half-life ($\tau$), Gaussian HMM regime solver, and Downside Beta ($\beta_{down}$).
- [`cas_parser.py`](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/parsers/cas_parser.py): Dual-engine CAMS/KFintech CAS PDF parser.

### 3. Native Android App ([`repomix-mobile.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-mobile.md))
- [`DashboardScreen.kt`](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt): Material 3 Expressive UI with ambient gradient hero metric card, scheme-grouped tax lots, and priority AI radar.
- [`PortfolioCharts.kt`](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/PortfolioCharts.kt): Native Jetpack Compose `Canvas` Donut Allocation Ring Chart and XIRR Performance Bar Chart.

---

## 💡 Review Instructions for Claude / AI Reviewers

When reviewing this system design, focus on:
1. **Cryptographic Event Ledger Integrity**: Validate that `SqliteEventStore.java` enforces immutable append-only event constraints and SHA-256 HMAC verification.
2. **Indian Tax Compliance**: Inspect `FifoMatcher.java` and `TaxClassifier.java` for Finance Act 2024 compliance (Sec 112A ₹1.25L exemption, Sec 50AA debt classification).
3. **Apache Arrow Zero-Copy Protocol**: Review `flight_server.py` and `ArrowFlightClient.java` for zero-copy memory vector transfer.
4. **Android Material 3 UI**: Review `DashboardScreen.kt` and `PortfolioCharts.kt` for native Compose architecture and `@SerializedName` Jackson compatibility.
