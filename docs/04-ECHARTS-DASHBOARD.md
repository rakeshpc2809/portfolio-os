# ECharts Web Dashboard Subsystem

The web dashboard is a modern JavaScript application served statically by Spring Boot from `core-node/src/main/resources/static`. It provides real-time portfolio metrics, Apache ECharts allocation charts, interactive Sankey rebalance flow diagrams, scheme-grouped tax lot accordion views, and a Raycast-style command palette (`Cmd+K` / `Ctrl+K`).

---

## 1. Architecture Map

```
core-node/src/main/resources/static/
├── index.html                         # Dashboard HTML layout & modal containers
├── style.css                          # Custom CSS styling (dark mode, glassmorphism)
└── src/
    ├── app.js                         # Main dashboard initializer, event listeners, Raycast action palette
    └── js/
        ├── api.js                     # Centralized fetch wrapper & X-Api-Auth-Token injector
        ├── constants.js               # Global constants & chart color tokens
        ├── domUtils.js                # DOM helper utilities
        ├── state.js                   # Reactive state store object
        ├── utils.js                   # Currency formatters (formatINR) & toast notifications
        └── modules/
            ├── insurance.js           # Emergency fund & health coverage tracker module
            ├── portfolio.js           # ECharts charts, Sankey cashflow diagrams, grouped tax lot accordion
            └── tax.js                 # Exemption meter, tax decision radar, realized log table
```

---

## 2. Data Flow: Scheme-Grouped Tax Lot Accordion Rendering

When portfolio holdings are loaded from `/portfolio/holdings`, the frontend renders collapsible scheme-grouped accordion cards:

```
[app.js:L27] initDashboard()
       |
       v  1. Fetches holdings array from REST API
[api.js:L15] fetchJson('/portfolio/holdings')
       |
       v  2. Stores holdings in global reactive state
[state.js] state.holdings = holdings
       |
       v  3. Renders scheme-grouped tax lots
[portfolio.js:L810] renderSchemeGroupedTaxLotsUI(holdings, containerId)
       |
       |----> 4. Groups open lots by ISIN / Asset Name
       |----> 5. For each fund group:
       |         - Computes group total units, invested value, current value, unrealized gain
       |         - Classifies category & fund tier badges (Core vs Legacy)
       |----> 6. Builds accordion HTML card (`scheme-group-card`)
       |         - Collapsed by default (`collapsed` class)
       |         - Contains inner FIFO open lot table (Acquisition date, days held, tax term)
       |
       v  7. Injects HTML into container DOM element
[domUtils.js] container.innerHTML = html
```

---

## 3. Business Logic Inventory

### 3.1. Scheme-Grouped Tax Lot Accordion
* **Source**: [`portfolio.js:L810-L920`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/js/modules/portfolio.js)
* **Logic**: Groups open tax lots under their respective mutual fund schemes. Cards are rendered **collapsed by default** to keep the dashboard clean. Expanding a card displays individual FIFO tax lot acquisition dates, cost basis, unrealized gain, and holding duration.

### 3.2. Cashflow Sankey Diagram Integration
* **Source**: [`portfolio.js:L650-L750`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/js/modules/portfolio.js)
* **Logic**: Renders an ECharts Sankey flow diagram visualizing rebalance capital flows:
  `[Trimmed Asset Lots] ➔ [Rebalance Capital Pool] ➔ [Target Allocation Buckets] ➔ [Destination Fund Purchases]`

### 3.3. Raycast Command Palette (`Cmd+K`) & AI SSE Streaming
* **Source**: [`app.js:L97-L280`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/app.js#L97-L280)
* **Logic**:
  - Intercepts `"rebalance <amount>"` commands to execute instant tax waterfall previews via `/rebalance/waterfall`.
  - For natural language queries, connects an `EventSource` to `/llm/stream?prompt=...` and streams Qwen LLM tokens into a formatted terminal output box.

### 3.4. API Auth Interceptor
* **Source**: [`api.js:L1-L25`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/resources/static/src/js/api.js)
* **Logic**: Injects `X-Api-Auth-Token` into all outbound HTTP requests using the token saved in `localStorage` or defaulting to `dev_secret_key_123`. Catches `401 Unauthorized` responses and alerts the user to update authentication credentials.

---

## 4. Test Coverage Map

> [!WARNING]
> **Thin/Missing Test Coverage Flag**:
> - Frontend JavaScript code (`app.js`, `portfolio.js`, `tax.js`, `api.js`) relies on manual browser verification and end-to-end REST testing. There are **0 automated JavaScript unit tests** (no Jest/Vitest suite configured).

---

## 5. Known Issues & Historical Fallback Flags

1. **Closing Brace Syntax Fix**: In commit `629d74b`, a missing closing brace in `portfolio.js` caused script parsing errors. The file structure was validated and updated.
2. **Shortened Scheme Name Display**: In commit `f4e995b`, long mutual fund names (e.g. `"Nippon India Large Cap Fund - Direct Plan - Growth Option"`) were truncating chart legends. Helper function `shortenFundName()` was added across all ECharts chart renderers to normalize scheme labels.
