# Quant-Sidecar Subsystem (Python 3.11 / Arrow Flight RPC / FastAPI)

The `quant-sidecar` is a Python-based quantitative analytics and statement parsing microservice. It provides high-throughput Apache Arrow Flight gRPC RPC endpoints for fund risk metrics and 10,000-iteration Monte Carlo FIRE simulations, alongside statement ingestion parsers for CAMS/KFintech CAS PDFs and Zerodha/Groww CSV broker statements.

---

## 1. Architecture Map

```
quant-sidecar/
├── Dockerfile                         # Python 3.11 slim container image
├── app.py                             # FastAPI HTTP server (Port 8000) & Flight server thread launcher
├── flight_server.py                   # Apache Arrow Flight RPC server (Port 8001)
├── requirements.txt                   # PyArrow, Polars, FastAPI, PyMuPDF, Pandas, NumPy, QuantStats
├── parsers/
│   ├── broker_csv_parser.py           # Zerodha / Groww CSV trade statement parser
│   ├── cas_parser.py                  # CAMS / KFintech CAS PDF statement parser (pdfplumber/fitz)
│   ├── models.py                      # Pydantic schemas (TaxEventSchema)
│   └── sip_detector.py                # 3+ match recurring SIP auto-detection algorithm
├── quant/
│   └── analytics_engine.py            # Monte Carlo FIRE engine & QuantStats risk metrics wrapper
└── tests/
    └── test_parsers.py                # Pytest unit tests for statement parsers
```

---

## 2. Data Flow: Monte Carlo FIRE Simulation

The FIRE Monte Carlo simulation can be invoked via HTTP POST `/api/v1/simulate_fire` or via PyArrow Flight RPC `do_action("fire_simulation")`:

```
[Core-Node / FlightRpcClient.java]
       |
       v  1. gRPC Action Call "fire_simulation" (port 8001)
[flight_server.py:L18] do_action()
       |
       |  2. Deserializes JSON payload (current_corpus, annual_expense, monthly_contrib, etc.)
       v
[flight_server.py:L33] run_monte_carlo_fire_simulation()
       |
       v
[analytics_engine.py:L115] run_monte_carlo_fire_simulation()
       |
       |----> 3. Checks empirical data sufficiency
       |      - If len(daily_returns_list) >= 750 (3+ years):
       |            Sets data_source = "EMPIRICAL_PORTFOLIO"
       |      - Else (len < 750):
       |            Generates Nifty benchmark returns (loc=0.00045, scale=0.011)
       |            Sets data_source = "SYNTHETIC_MARKET_BENCHMARK"
       |
       |----> 4. Applies Circular Block Bootstrapping
       |      - Block size = min(15, n_returns) (15-day block sampling)
       |      - Samples random 15-day blocks across 10,000 parallel simulation streams
       |
       |----> 5. Simulates day-by-day accumulation & withdrawal
       |      - Accumulation phase: corpus = corpus * (1 + real_ret) + daily_sip
       |      - Retirement phase: corpus = corpus * (1 + real_ret) - daily_expense
       |      - Subtracts 6.0% annual inflation (0.06 / 252 daily)
       |
       v  6. Calculates percentiles (P10, P25, P50, P75, P90) & success rate
[analytics_engine.py:L198] return { "status": "OK", "success_rate_pct": ..., "fan_chart_trajectories": ... }
```

---

## 3. Business Logic Inventory

### 3.1. QuantFlightServer (Arrow Flight gRPC Server)
* **Source**: [`flight_server.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/flight_server.py#L10-L112)
* **Logic**:
  - `do_action`: Listens for action type `"fire_simulation"`. Deserializes params, invokes `run_monte_carlo_fire_simulation`, and returns serialized byte results.
  - `do_exchange`: High-throughput streaming RPC for batch fund analytics. Accepts an Arrow Table containing `amfi_code`, `nav_value`, and `nav_date`. Converts to Polars DataFrame, computes risk analytics for each scheme, and streams an Arrow Table back with `sharpe`, `sortino`, `calmar`, `max_drawdown`, `volatility_annual`, `var_95`, `cvar_95`, and `beta`.

### 3.2. Monte Carlo FIRE Simulation Engine & Block Bootstrapping
* **Source**: [`analytics_engine.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/quant/analytics_engine.py#L115-L212)
* **Logic**:
  - **Empirical Check**: Requires >= 750 daily returns (3 years).
  - **15-Day Circular Block Bootstrap**: Preserves empirical volatility clustering and autocorrelations by sampling 15-day contiguous blocks rather than single independent days ([`analytics_engine.py:L143-L151`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/quant/analytics_engine.py#L143-L151)).
  - **Daily Real Return Adjustment**: Net returns are adjusted for a **6.0% annual inflation rate** (`daily_inflation = 0.06 / 252.0`).

### 3.3. Downside Deviation Sortino & Benchmark Analytics
* **Source**: [`analytics_engine.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/quant/analytics_engine.py#L71-L85) & [`analytics_engine.py:L215-L269`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/quant/analytics_engine.py#L215-L269)
* **Logic**:
  - Computes Sharpe, Sortino, Calmar, and Value-at-Risk. If `QuantStats` library is absent, executes vectorized NumPy fallbacks.
  - **Sortino Fallback**: Specifically calculates true downside deviation using `returns[returns < 0].std()` ([`analytics_engine.py:L76-L78`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/quant/analytics_engine.py#L76-L78)).
  - **Sharpe Sanity Guard**: In [`analytics_engine.py:L244-L247`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/quant/analytics_engine.py#L244-L247), if sample size < 30 days OR calculated Sharpe ratio exceeds `|3.5|`, the Sharpe ratio is reset to `0.0` and flagged as `SANITY_BOUND_REJECTED` or `PROVISIONAL_UNSTABLE_SAMPLE`.

### 3.4. Recurring SIP Auto-Detection Algorithm
* **Source**: [`sip_detector.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/parsers/sip_detector.py)
* **Logic**: Groups parsed transactions by asset ISIN/name. Detects recurring monthly transactions by searching for >= 3 transactions occurring at 25–35 day intervals with amounts matching within a 2.0% tolerance. Tags matching transactions with `eventType = SIP_INSTALMENT`.

---

## 4. Test Coverage Map

Empirical test count obtained via `grep -rc "def test_" quant-sidecar/tests`:

| Test File | Path | Real `def test_` Count | Verified Behaviors |
| :--- | :--- | :---: | :--- |
| `test_parsers.py` | [`test_parsers.py`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/quant-sidecar/tests/test_parsers.py) | **5** | CAS PDF transaction extraction, broker CSV header parsing, SIP detector 3-match tagging, Pydantic schema validation |

> [!WARNING]
> **Thin/Missing Test Coverage Flag**:
> - `flight_server.py`: No unit test file exists (`test_flight_server.py` does not exist). gRPC Arrow Flight RPC endpoints are tested only manually or via Core Node integration tests.
> - `analytics_engine.py`: No dedicated unit test file (`test_analytics_engine.py` does not exist).

---

## 5. Known Issues & Historical Fallback Flags

1. **Synthetic Return Model Fallback**: When portfolio history is under 3 years (< 750 points), Monte Carlo simulation uses a synthetic Nifty distribution (`loc=0.00045, scale=0.011`). The API response explicitly sets `data_source = "SYNTHETIC_MARKET_BENCHMARK"` to prevent false reporting of empirical data.
2. **QuantStats Dependency Fallback**: `QuantStats` is optional. If `ImportError` occurs, `analytics_engine.py` falls back to pure NumPy/Pandas math.
3. **API Auth Token Header**: Requires `X-Api-Auth-Token` matching `API_AUTH_TOKEN` environment variable via `secrets.compare_digest` in `app.py`.
