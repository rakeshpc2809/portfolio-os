# Portfolio OS — Technical Documentation Set

This documentation set provides an empirical architectural reference for **Portfolio OS**.

---

## 1. System Architecture Topology

Portfolio OS is a local-first personal investment management and automated portfolio rebalancing system running as a single-process Spring Boot service serving a responsive Svelte 5 PWA.

```
 +-------------------------------------------------------------------------------+
 |                     Web & Mobile PWA (Svelte 5 / Vite)                        |
 |  Responsive Desktop Cockpit + Mobile Bottom-Bar PWA (served on port 8080)     |
 +-------------------------------------+-----------------------------------------+
                                       | HTTP / REST (X-Api-Auth-Token)
                                       v
 +-------------------------------------------------------------------------------+
 |                        Core Node (Java 21 / Spring Boot)                      |
 |  com.portfolioos.core                                                         |
 |                                                                               |
 |  +--------------------+   +-----------------------+   +--------------------+  |
 |  | Rebalance Engine   |   | Fifo & Tax Classifier |   | XirrEngine         |  |
 |  | (v3.0 Waterfall)   |   | (Sec 50AA / 112A/112) |   | (Newton-Raphson)   |  |
 |  +---------+----------+   +-----------+-----------+   +---------+----------+  |
 |            |                      |                         |                 |
 |            +----------------------+-------------------------+                 |
 |                                   v                                           |
 |                        +---------------------+                                |
 |                        | SqliteEventStore    |                                |
 |                        | (HMAC-SHA256)       |                                |
 |                        +---------------------+                                |
 +-------------------------------------------------------------------------------+
                                       |
                                       | ProcessBuilder CLI (on-demand)
                                       v
 +-------------------------------------------------------------------------------+
 |                     Quant Sidecar (Python 3.12 CLI)                           |
 |  parse_cas.py / parsers/ (CAS PDF & Broker CSV ingestion)                     |
 +-------------------------------------------------------------------------------+
```

---

## 2. Master Documentation Index

| Module Document | Subsystem Scope | Core Files & Technologies |
| :--- | :--- | :--- |
| **`01-CORE-NODE.md`** | **Core Java 21 / Spring Boot Backend** | `RebalanceWaterfallEngine.java`, `RebalancePlanEngine.java`, `FifoMatcher.java`, `TaxClassifier.java`, `XirrEngine.java` |
| **`02-QUANT-SIDECAR.md`** | **Python On-Demand CLI Parsers** | `parse_cas.py`, `cas_parser.py`, `broker_csv_parser.py` |
| **`06-SQLITE-EVENT-STORE.md`** | **SQLite Event Store & Persistence** | `SqliteEventStore.java`, `SecurityInterceptor.java` |
