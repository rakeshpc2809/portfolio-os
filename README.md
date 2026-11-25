# Portfolio OS (v4.0)

Portfolio OS is a local-first personal investment management, statutory Indian tax-lot FIFO engine, and automated portfolio rebalancing system under a single-process, single-store architecture.

---

## Architecture Overview

```
                        ┌───────────────────────────────┐
                        │  Svelte 5 Responsive PWA      │
                        │  (Desktop Cockpit & Mobile)   │
                        └──────────────┬────────────────┘
                                       │ REST / JSON (HTTP 8080)
                                       ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                          Core Node (Java 21 / Spring Boot)                    │
│                                                                               │
│  - FIFO Matching Engine             - Policy v3.0 YAML Rules (Hot-Reload)     │
│  - HMAC-SHA256 Cryptographic Ledger - Statutory Tax Engine (112A / 112 / 50AA)│
│  - Water-Filling Rebalancer         - Drawdown Rungs & ITR-2 Schedule CG ZIP  │
└──────────────────────┬────────────────────────────────┬───────────────────────┘
                       │                                │
       SQLite Event Store (HMAC-SHA256)        On-Demand Python CLI (ProcessBuilder)
       data/tax_ledger.db                      quant-sidecar/parse_cas.py
```

---

## Key Features

1. **Cryptographic Event Sourcing Ledger**: SQLite storage (`data/tax_ledger.db`) protected by HMAC-SHA256 hash-chaining to ensure append-only tamper evidence.
2. **Dynamic Policy v3.0 Rules**: YAML-driven target allocations, asymmetric tolerance corridors, drawdown deployment rungs, and Indian Income Tax Act rules (Section 112A equity exemption, Section 112 non-equity, Section 50AA specified debt, Section 55(2)(ac) grandfathering).
3. **Decision-First Web & Mobile PWA**:
   - **Overview**: Verdict Strip, Bucket Drift Matrix with tolerance-band bullet bars, and collapsible Net Worth Timeline.
   - **Rebalancer**: Config-driven Nifty 500 Drawdown Ladder, 3-column Capital Flow Ledger with 2-step `Confirm Execution` (`plan_digest`), inline Fresh Bank Cash Router, and deterministic FIRE trajectory.
   - **Tax & Lots**: Regime-aware FIFO lot inspector, LTCG/STCG composition bars, `Maturing ≤30d` filter, `.ics` maturation calendar, and ITR-2 Schedule CG ZIP export.

---

## Quickstart

```bash
# Build & verify all suites (Core Node, Quant CLI, Web)
just check

# Package executable JAR and restart systemd user service
just package-core
systemctl --user restart portfolio-os-core.service
```

The Web Cockpit PWA and REST API are served at `http://127.0.0.1:8080/`.
