# CLAUDE ARCHITECTURE REVIEW INDEX — Portfolio OS v3.0

> **For Claude**: Feed any or all of the three Repomix Markdown bundles into your prompt along with this index file. Total token footprint is optimized under ~44k tokens.

---

## 📄 Compressed Repomix Code Packs

1. **Java Core Node**: [`repomix-core.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-core.md) (**30,283 tokens**)
2. **Python Quant Sidecar**: [`repomix-quant.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-quant.md) (**3,475 tokens**)
3. **Android Companion App**: [`repomix-mobile.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-mobile.md) (**10,425 tokens**)

---

## 🛠️ Summary of System Features & Contracts

- **Append-Only HMAC Ledger**: `SqliteEventStore.java` maintains SHA-256 HMAC event chains for all investment operations.
- **DuckDB Analytical Projection**: `DuckDbProjector.java` projects SQLite events into DuckDB columnar tables for real-time OLAP valuation.
- **Finance Act 2024 FIFO Engine**: `FifoMatcher.java` pairs lots under Sec 112A equity grandfathering rules (31-Jan-2018 FMV) and Sec 50AA debt rules.
- **Arrow Flight RPC**: `flight_server.py` serves Hurst exponent ($H$), OU half-life ($\tau$), and Downside Beta ($\beta_{down}$) vectors via Apache Arrow Flight gRPC port 8001.
- **Android Material 3 Companion App**: `DashboardScreen.kt` & `PortfolioCharts.kt` provide a native Jetpack Compose experience with Canvas Donut charts, ambient gradient cards, scheme-grouped tax lots, and priority AI radar.

---

## 🔗 GitHub Repository
[github.com/rakeshpc2809/portfolio-os](https://github.com/rakeshpc2809/portfolio-os)
