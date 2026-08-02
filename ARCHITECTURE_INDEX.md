# Architecture Index - Portfolio OS v3.0

> **System Overview**: Portfolio OS is a local-first investment management system combining an append-only cryptographic event sourcing tax ledger (Java Core Node), a high-performance quantitative analytics sidecar (Python PyArrow Flight), a Vue 3 Vapor SPA Web Cockpit, and an offline-first Jetpack Compose Android Companion App.

---

## 📦 Repomix Source Bundles for AI Code Review

For fast, context-efficient code review, the codebase is packed into three minimal Repomix markdown bundles:

| Component | Target Directory | Repomix Pack | Token Count | Main Tech Stack |
| :--- | :--- | :--- | :---: | :--- |
| **Java Core Node** | `core-node/` | [`repomix-core.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-core.md) | ~56,200 | Java 21, Spring Boot 3.2.5, SQLite, DuckDB |
| **Python Quant Sidecar** | `quant-sidecar/` | [`repomix-quant.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-quant.md) | ~5,600 | Python 3.12, FastAPI, Polars, PyArrow Flight |
| **Android Mobile App** | `mobile-app/` | [`repomix-mobile.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-mobile.md) | ~4,400 | Kotlin, Jetpack Compose, Retrofit 2 |

---

## 🏛️ System Architecture & Subsystem Index

```
+-----------------------------------------------------------------------------------+
|                                 CLIENT LAYER                                      |
|  +-------------------------------------+  +------------------------------------+  |
|  | Web Cockpit (Vue 3 Vapor Mode SPA)  |  | Android Mobile App (Jetpack Compose|  |
|  +-------------------------------------+  +------------------------------------+  |
+------------------------------------------+----------------------------------------+
                                           |
                              HTTP / REST  | (X-Api-Auth-Token)
                                           v
+-----------------------------------------------------------------------------------+
|                        JAVA CORE NODE (Spring Boot 3.2.5)                         |
|  +---------------------------+  +----------------------+  +--------------------+  |
|  | SQLite HMAC-SHA256 Ledger |  | DuckDB Analytical DB |  | FIFO Tax Matcher   |  |
|  +---------------------------+  +----------------------+  +--------------------+  |
+-----------------------------------------------------------------------------------+
                                           |
                         Arrow Flight RPC  | (Zero-Copy gRPC Port 8001)
                                           v
+-----------------------------------------------------------------------------------+
|                     PYTHON QUANT SIDECAR (FastAPI + Polars)                       |
|  +---------------------------+  +----------------------+  +--------------------+  |
|  | CAMS / KFin CAS PDF Parser|  | Broker CSV Ingest    |  | Hurst / HMM Quant  |  |
|  +---------------------------+  +----------------------+  +--------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 🔑 Key Source File Map

### 1. Java Core Node (`core-node/`)
- [`SqliteEventStore.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java): SHA-256 HMAC append-only ledger with boot-time chain validation.
- [`DuckDbProjector.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java): Automatic SQLite-to-DuckDB analytical materializer.
- [`FifoMatcher.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/matcher/FifoMatcher.java): FIFO lot pairing engine supporting bonus, corporate splits, and grandfathered FMV (31-Jan-2018).
- [`TaxClassifier.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/matcher/TaxClassifier.java): Indian tax law classifier (Equity Sec 112A, Debt Sec 50AA, Gold, SGB, International).
- [`SyncController.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/controllers/SyncController.java): Endpoint `/api/v1/sync/snapshot` providing mobile synchronization payload.
- [`SecurityInterceptor.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/security/SecurityInterceptor.java): Pre-shared `X-Api-Auth-Token` and `?token=` parameter authorization guard.

### 2. Python Quant Sidecar (`quant-sidecar/`)
- [`flight_server.py`](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/flight_server.py): Apache Arrow Flight RPC server implementation listening on gRPC port 8001.
- [`quant_engine.py`](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/quant/quant_engine.py): Vectorized Hurst Exponent, Ornstein-Uhlenbeck (OU) half-life, and Gaussian HMM regime solver.
- [`cas_parser.py`](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/parsers/cas_parser.py): Dual-mode CAS PDF parser (`casparser` library with `pdfplumber` line-regex fallback).

### 3. Android Mobile App (`mobile-app/`)
- [`DashboardScreen.kt`](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt): Obsidian dark theme Compose interface.
- [`SyncApiClient.kt`](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/api/SyncApiClient.kt): Retrofit network client syncing with `/api/v1/sync/snapshot`.

---

## ⚙️ Execution & Review Commands

- **Build and Run Containers**: `podman compose up -d`
- **Re-pack Repomix Bundles**:
  ```bash
  npx -y repomix core-node -o repomix-core.md --style markdown --remove-empty-lines -i "target/**,.mvn/**"
  npx -y repomix quant-sidecar -o repomix-quant.md --style markdown --remove-empty-lines -i "__pycache__/**"
  npx -y repomix mobile-app -o repomix-mobile.md --style markdown --remove-empty-lines -i "build/**,.gradle/**"
  ```
