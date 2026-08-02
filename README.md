# Portfolio OS (v3.0 HLD Architecture)

Portfolio OS merges the core tax ledger of `my-fintracker` and the quantitative analytics engine of `portfolio-tracker-v2` under a decoupled, high-performance polyglot architecture.

---

## 🏗️ Architecture Overview

```
                        ┌───────────────────────────────┐
                        │      Vue 3 Web Cockpit        │
                        │    (Desktop & Responsive)     │
                        └──────────────┬────────────────┘
                                       │ REST / JSON (HTTP 8080)
                                       ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                          Core Node (Java 26 / Spring Boot)                    │
│                                                                               │
│  - FIFO Matching Engine             - Rule Engine (YAML Hot-Reload)           │
│  - HMAC-SHA256 Cryptographic Ledger  - Statutory Offset Engine (Sec 112A/50AA)│
│  - Rebalancing & Decumulation       - Maturation Ladder & Schedule FA Export  │
└──────────────────────┬────────────────────────────────┬───────────────────────┘
                       │                                │
      SQLite Event Store│                                │ Arrow Flight RPC (gRPC 8001)
     (Cryptographic Log)│                                │ Fast Zero-Copy Data Passing
                        ▼                                ▼
              ┌──────────────────┐            ┌──────────────────────────────────┐
              │ DuckDB Projector │            │     Quant Sidecar (Python)       │
              │(Analytical Query)│            │  - Polars + PyArrow Flight       │
              └──────────────────┘            │  - Hurst Exponent Vectorization  │
                                              │  - HMM Market Regimes & OU Math  │
                                              │  - CAS PDF / Broker CSV Parsers  │
                                              └──────────────────────────────────┘
```

---

## ⚡ Key Features

1. **Cryptographic Event Sourcing Ledger**: SQLite storage protected by HMAC-SHA256 hash-chaining to ensure append-only tamper evidence.
2. **DuckDB Analytical Projection**: Automated projection of ledger events into local DuckDB for analytical queries.
3. **Apache Arrow Flight RPC**: Inter-process communication between Java Core and Python Quant Sidecar passing vector memory with zero serialization overhead.
4. **Dynamic YAML Tax Rules**: Dynamic rule loading for Indian Income Tax Act changes (Section 112A equity exemption, Section 50AA specified debt, Section 55(2)(ac) grandfathering).
5. **Decumulation & Rebalancing Advisors**:
   - FIRE Decumulation Runway & SWR Planner
   - Flat Bucket Allocation Rebalancer & Drawdown Trigger Rungs
   - Tax-Loss Harvesting Opportunity Scanner
   - Disciplined Portfolio Consolidation Plan

---

## 🚀 Quickstart & Deployment

### Prerequisites
- Docker & Docker Compose or Podman & Podman Compose
- JDK 21+ / OpenJDK 26 (for local development outside containers)
- Python 3.12+ (for sidecar local development outside containers)

### Running with Docker Compose / Podman Compose

```bash
# Build and start services
podman compose up --build -d

# View logs
podman compose logs -f
```

The Web Cockpit and REST API will be accessible at:
`http://localhost:8080/`

---

## 📡 API Sitemap

### Core Ledger & Sync
- `GET /api/v1/sync/snapshot` — Unidirectional snapshot containing holdings, tax lots, and radar signals
- `POST /api/v1/sync/pair` — Device pairing endpoint

### Statement Ingestion
- `POST /api/v1/statements/upload` — Multipart upload for CAMS/KFintech CAS PDFs or Broker CSVs

### Reports & Tax Optimization
- `GET /api/v1/portfolio/summary` — Net worth, total unrealized gain, active scheme count, XIRR
- `GET /api/v1/portfolio/holdings` — Grouped open holdings with FIFO lot details
- `GET /api/v1/portfolio/allocation` — Asset allocation distribution
- `GET /api/v1/portfolio/category-allocation` — Risk exposure allocation by tax category
- `GET /api/v1/tax/exemption-status` — Section 112A LTCG exemption headroom meter
- `GET /api/v1/tax/reports/itr2` — Schedule CG summary
- `GET /api/v1/tax/harvest-opportunities` — Tax-loss harvesting recommendations
- `GET /api/v1/tax/maturation-ladder` — LTCG maturation timeline
- `GET /api/v1/tax/realized-log` — Fiscal year realized gain/loss audit log
- `GET /api/v1/tax/export/itr2/zip` — ZIP bundle download containing Schedule 112A, Schedule STCG, and Schedule FA CSVs

### Valuation & Advisors
- `GET /api/v1/portfolio/buckets/rebalance` — Bucket drift and market drawdown triggers
- `GET /api/v1/portfolio/rebalance-preview` — Target redemption tax drag estimator
- `GET /api/v1/portfolio/consolidation-preview` — Phased asset exit and core fund redeployment plan
- `GET /api/v1/portfolio/goals` — Liquid buffer goal tag allocations
- `GET /api/v1/portfolio/fire` — FIRE decumulation runway calculations
