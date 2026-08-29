# Core-Node Subsystem (Java 21 / Spring Boot)

The `core-node` subsystem is the central domain authority of Portfolio OS. It handles statement ingestion, FIFO tax-lot matching, capital gains classification, XIRR calculations, bucket allocation evaluation, rebalance trigger monitoring, and tax-aware waterfall rebalancing.

---

## 1. Architecture Map

```
core-node/src/main/java/com/portfolioos/core/
├── CoreApplication.java                          # Spring Boot Bootstrapper (Port 8080)
├── config/
│   └── AppConfig.java                            # Beans & CORS config
├── controllers/
│   ├── ConfigController.java                     # /config/bucket-targets REST endpoints
│   ├── LlmQueryController.java                   # /llm/stream SSE endpoints
│   ├── RebalanceController.java                  # /rebalance/bucket, /rebalance/preview, /rebalance/waterfall
│   ├── ReportController.java                     # /reports/tax, /reports/itr2
│   ├── SimulatorController.java                  # /simulate/trade, /simulate/fire
│   ├── StatementsController.java                 # /api/v1/statements/upload
│   └── SyncController.java                       # /sync/snapshot, /sync/rebalance/plan
├── matcher/
│   ├── FifoMatcher.java                          # First-In First-Out transaction matcher
│   ├── FundTierClassifier.java                   # Active SIP detection & Core/Legacy tiering
│   └── TaxClassifier.java                        # Sec 50AA temporal branching & Sec 112A classification
├── model/
│   ├── AssetCategory.java                        # EQUITY, DEBT_SPECIFIED_50AA, GOLD_SILVER, SGB, INTERNATIONAL
│   ├── EventType.java                           # ACQUISITION, DISPOSAL, SIP_INSTALMENT, BONUS, SPLIT, MERGER
│   ├── Lot.java                                  # Open lot state representation
│   ├── MatchedLot.java                           # Realized disposal tax lot pair
│   ├── TaxEvent.java                             # Raw transactional event representation
│   └── TaxTerm.java                              # LONG_TERM, SHORT_TERM, EXEMPT
├── nav/
│   ├── AmfiNavSync.java                          # Live AMFI NAV feed parser & 8-column fallback
│   ├── MfApiNavDownloader.java                   # mfapi.in historical NAV backfiller
│   └── NseIndexConstituentDownloader.java        # Nifty index composition downloader
├── persistence/
│   ├── DuckDbProjector.java                      # Embedded DuckDB analytical query engine
│   ├── SqliteEventStore.java                     # Append-only HMAC-SHA256 event store
│   └── TriggerHistoryRepository.java             # Rebalance execution cooldown store
├── rules/
│   ├── BucketConfigLoader.java                   # Point-in-time versioned YAML loader (bucket_targets.yaml)
│   ├── FireActionRuleEngine.java                 # Emergency FIRE withdrawal rule evaluator
│   └── TaxRulesLoader.java                       # Temporal fiscal year rules loader (FY2025-26.yaml)
├── security/
│   ├── SecurityConfig.java                       # WebSecurityInterceptor registration
│   └── SecurityInterceptor.java                  # API_AUTH_TOKEN constant-time validator
├── service/
│   ├── LedgerCacheService.java                   # Central in-memory ledger state cache
│   ├── PortfolioValuationService.java            # Net worth & portfolio summary computer
│   ├── RebalancePlanEngine.java                  # Unified rebalance plan builder
│   ├── RebalanceTriggerEvaluator.java            # DRAWDOWN, DRIFT, SCHEDULED, GOLD_FLOOR priority evaluator
│   ├── SimulationService.java                    # Trade simulation proxy & Arrow Flight bridge
│   ├── StatementIngestionUseCase.java            # Dual-write orchestrator (SQLite + DuckDB)
│   └── TaxOptimizationService.java               # Sec 112A exemption & loss harvesting advisor
├── valuation/
│   ├── BucketEngine.java                         # Asset-to-bucket classifier & drift evaluator
│   ├── ConsolidationRebalanceEngine.java         # Legacy fund consolidation preview engine
│   ├── FundTrendDampenerCalculator.java          # Volatility trim dampening math
│   ├── GoldDampenerCalculator.java               # Gold floor backstop allocation calculator
│   └── RebalanceWaterfallEngine.java             # 4-tier tax-aware trim waterfall solver
└── xirr/
    ├── CashFlow.java                             # Dated cash flow record
    └── XirrEngine.java                           # Hybrid Newton-Raphson / Bracketed Bisection solver
```

---

## 2. Data Flow: Rebalance Plan Generation

When a user requests a rebalance plan (e.g. via `GET /sync/rebalance/plan?trigger=DRIFT`), the execution path traverses the following method chain:

```
[REST Client / Android]
       |
       v  1. GET /sync/rebalance/plan
[SyncController.java:L107] getRebalancePlan()
       |
       |  2. Fetches cached open lots & matched lots
       v
[LedgerCacheService.java:L51] getCachedOpenLots()
       |
       |  3. Fetches live NAV map from AMFI parser
       v
[AmfiNavSync.java:L106] getNavMap()
       |
       |  4. Calls unified plan builder
       v
[RebalancePlanEngine.java:L83] buildPlanInternal()
       |
       |----> 5. Evaluates trigger priorities & 30-day cooldown
       |      [RebalanceTriggerEvaluator.java:L40] getCurrentStatus()
       |
       |----> 6. Evaluates bucket target drifts & drawdown tier
       |      [BucketEngine.java:L154] evaluateRebalance()
       |
       |----> 7. Executes tax-aware 4-tier trim waterfall
       |      [RebalanceWaterfallEngine.java:L59] buildTrimWaterfall()
       |         |-- Tier 1: Legacy Fund Lots (LTCG only, max 50% scheme cap)
       |         |-- Tier 2: Loss Harvest (Core lots with negative unrealized gain)
       |         |-- Tier 3: Core LTCG Lots (Utilizing Sec 112A headroom)
       |         `-- Tier 4: Core STCG Lots (Exempt during DRIFT/SCHEDULED; allowed if urgent=true)
       |
       |----> 8. Computes statutory exemption headroom remaining
       |      [ExemptionTracker.java:L23] calculateExemptionStatus()
       |
       v  9. Constructs RebalancePlanDto with BuySide, SellSide, & Narrative
[RebalancePlanEngine.java:L623] return new RebalancePlanDto(...)
```

---

## 3. Business Logic Inventory

### 3.1. RebalanceWaterfallEngine
* **Source**: [`RebalanceWaterfallEngine.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/valuation/RebalanceWaterfallEngine.java#L59-L164)
* **Logic**: Evaluates open lots to satisfy a required trim amount while minimizing tax drag across four prioritized strategies:
  1. `LEGACY_FUND`: Trims phased-out funds. In [`RebalanceWaterfallEngine.java:L122-L129`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/valuation/RebalanceWaterfallEngine.java#L122-L129), single-scheme trim is capped at **50% of the scheme's total value** in a single rebalance session to avoid excessive tax impact. Strictly restricted to LTCG lots.
  2. `LOSS_HARVEST`: Trims core lots with negative unrealized gains (`nav < costPerUnit`) to harvest losses.
  3. `LTCG_WITHIN_EXEMPTION`: Trims core lots held beyond statutory LTCG thresholds, utilizing available Section 112A exemption headroom.
  4. `STCG_URGENT_ONLY`: Trims short-term lots **only when `urgent == true`** (e.g. during drawdown >= 15% or drift >= 10%). In normal scheduled/drift rebalances, STCG lots are **100% excluded** ([`RebalanceWaterfallEngine.java:L199-L201`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/valuation/RebalanceWaterfallEngine.java#L199-L201)).
* **Hardened Valuation Fallback**: In [`RebalanceWaterfallEngine.java:L97-L103`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/valuation/RebalanceWaterfallEngine.java#L97-L103), if an ISIN is missing from `navMap`, it logs `AMFI_NAV_SYNC_ALERT` and falls back to `lot.costPerUnit()`. If both live NAV and cost basis are missing, it throws an `IllegalStateException`.

### 3.2. TaxClassifier & Sec 50AA Temporal Branching
* **Source**: [`TaxClassifier.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/matcher/TaxClassifier.java#L67-L113)
* **Logic**: Classifies asset categories via registry lookup or regex heuristics (`detectCategory`), and evaluates tax term (`LONG_TERM`, `SHORT_TERM`, `EXEMPT`).
* **Finance Act (No. 2) 2024 Sec 50AA Temporal Rules**:
  - Debt mutual funds acquired **post April 1, 2023** -> Always `SHORT_TERM` (taxed at investor's slab rate).
  - Legacy debt mutual funds acquired **pre April 1, 2023**:
    - Disposed **on/after July 23, 2024**: `>= 730 days` (24 months) holding required for `LONG_TERM` (taxed at 12.5% without indexation).
    - Disposed **before July 23, 2024**: `>= 1095 days` (36 months) holding required for `LONG_TERM` (taxed at 20% with indexation).

### 3.3. FifoMatcher
* **Source**: [`FifoMatcher.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/matcher/FifoMatcher.java#L25-L158)
* **Logic**: Sorts events chronologically (`eventDate`, `ingestedAt`), maintains an open lot queue, and matches disposal events against oldest acquired lots. Supports split ratio adjustments, corporate mergers, bonus zero-cost lots, and SGB maturity exemption.

### 3.4. RebalanceTriggerEvaluator
* **Source**: [`RebalanceTriggerEvaluator.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/service/RebalanceTriggerEvaluator.java#L136-L216)
* **Logic**: Enforces strict trigger priority ordering:
  1. `DRAWDOWN`: Triggered when Nifty 500 market drawdown crosses 10%, 15%, or 20%. Subject to 30-day sell cooldown ([`L147`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/service/RebalanceTriggerEvaluator.java#L147)).
  2. `DRIFT`: Triggered when bucket weight allocation exceeds drift threshold. Subject to 30-day sell cooldown.
  3. `SCHEDULED`: March/September reconstitution window. Subject to 30-day sell cooldown.
  4. `GOLD_FLOOR_BACKSTOP`: Buy-side only. Triggered when gold bucket has been idle for >= 6 months AND is >= 2.0 percentage points underweight. **Exempt from 30-day sell cooldown** ([`L191-L200`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/service/RebalanceTriggerEvaluator.java#L191-L200)).

### 3.5. XirrEngine
* **Source**: [`XirrEngine.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/xirr/XirrEngine.java#L13-L120)
* **Logic**: Calculates annualized internal rate of return using a 3-stage solver:
  1. Primary: 100-iteration Newton-Raphson solver (`npv`, `dNpv`).
  2. Secondary: Bracketed Bisection Fallback with dynamic search bounds (`low = -0.95`, `high = 50.0`) and step probing when Newton-Raphson fails to converge.
  3. Tertiary: Compound Annual Growth Rate (CAGR) fallback when root cannot be bracketed.

---

## 4. Test Coverage Map

Empirical `@Test` method counts obtained via `grep -rc "@Test" core-node/src/test/java`:

| Test Class | Path | Real `@Test` Count | Verified Behaviors |
| :--- | :--- | :---: | :--- |
| `RebalanceTriggerEvaluatorTest` | [`RebalanceTriggerEvaluatorTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/service/RebalanceTriggerEvaluatorTest.java) | **8** | Drawdown tier arming, 30-day sell cooldown enforcement, Gold floor backstop exemption |
| `LegacyFundWaterfallAuditTest` | [`LegacyFundWaterfallAuditTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/service/LegacyFundWaterfallAuditTest.java) | **5** | 50% legacy scheme trim cap, LTCG priority order |
| `RebalanceSankeyDtoTest` | [`RebalanceSankeyDtoTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/service/RebalanceSankeyDtoTest.java) | **5** | Sankey diagram cashflow node/link generation |
| `BucketConfigLoaderTest` | [`BucketConfigLoaderTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/rules/BucketConfigLoaderTest.java) | **5** | Point-in-time YAML target loading & preferred fund ISIN matching |
| `RebalanceWaterfallEngineTest` | [`RebalanceWaterfallEngineTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/valuation/RebalanceWaterfallEngineTest.java) | **4** | Loss harvesting tier selection, STCG exclusion in non-urgent mode |
| `GoldDampenerCalculatorTest` | [`GoldDampenerCalculatorTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/valuation/GoldDampenerCalculatorTest.java) | **4** | Sized allocation math for Gold floor top-up |
| `Itr2CsvExporterTest` | [`Itr2CsvExporterTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/reporting/Itr2CsvExporterTest.java) | **4** | Schedule CG CSV formatting for ITR-2 filing |
| `SyncControllerTest` | [`SyncControllerTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/controllers/SyncControllerTest.java) | **4** | `/sync/snapshot` DTO key structure & header validation |
| `SimulationServiceTest` | [`SimulationServiceTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/service/SimulationServiceTest.java) | **4** | Trade simulation execution & Arrow Flight RPC invocation |
| `BucketAllocationTest` | [`BucketAllocationTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/valuation/BucketAllocationTest.java) | **3** | Asset-to-bucket classification heuristics |
| `FundTierClassifierTest` | [`FundTierClassifierTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/matcher/FundTierClassifierTest.java) | **3** | Active SIP detection window & legacy classification |
| `SecurityInterceptorTest` | [`SecurityInterceptorTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/security/SecurityInterceptorTest.java) | **3** | Token match validation & 401 response handling |
| `XirrEngineTest` | [`XirrEngineTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/xirr/XirrEngineTest.java) | **3** | XIRR convergence on cash flows |
| `RebalancePlanEngineTest` | [`RebalancePlanEngineTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/service/RebalancePlanEngineTest.java) | **2** | End-to-end plan generation |
| `TaxOptimizationServiceTest` | [`TaxOptimizationServiceTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/service/TaxOptimizationServiceTest.java) | **2** | Sec 112A exemption harvesting recommendations |
| `TaxRulesLoaderTest` | [`TaxRulesLoaderTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/rules/TaxRulesLoaderTest.java) | **2** | YAML tax rules parsing |
| `ReconciliationGateTest` | [`ReconciliationGateTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/reconciliation/ReconciliationGateTest.java) | **2** | Unit balance reconciliation gate check |
| `ConfigControllerTest` | [`ConfigControllerTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/controllers/ConfigControllerTest.java) | **2** | REST endpoint response mapping |
| `FireTrackerTest` | [`FireTrackerTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/fire/FireTrackerTest.java) | **2** | FIRE metric computation |
| `GoalTrackerTest` | [`GoalTrackerTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/goals/GoalTrackerTest.java) | **2** | Goal progress computation |
| `PortfolioQueryToolsTest` | [`PortfolioQueryToolsTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/tools/PortfolioQueryToolsTest.java) | **2** | LLM Tool helper queries |
| `AmfiNavSyncTest` | [`AmfiNavSyncTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/nav/AmfiNavSyncTest.java) | **1** | AMFI text feed parsing |
| `SqliteEventStoreTest` | [`SqliteEventStoreTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/persistence/SqliteEventStoreTest.java) | **1** | HMAC hash chain verification |
| `TaxClassifierTest` | [`TaxClassifierTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/matcher/TaxClassifierTest.java) | **1** | Category detection heuristics |
| `FireActionRuleEngineTest` | [`FireActionRuleEngineTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/rules/FireActionRuleEngineTest.java) | **1** | Emergency rule triggers |
| `DuckDbProjectorNetWorthAccountingTest` | [`DuckDbProjectorNetWorthAccountingTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/service/DuckDbProjectorNetWorthAccountingTest.java) | **1** | DuckDB net worth query execution |
| `MonteCarloSanityTest` | [`MonteCarloSanityTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/valuation/MonteCarloSanityTest.java) | **1** | Simulation sanity check |
| **TOTAL TEST COUNT** | — | **68** | — |

> [!WARNING]
> **Thin/Missing Test Coverage Flag**:
> - `StatementIngestionUseCase.java`: Has no dedicated unit test class (`StatementIngestionUseCaseTest.java` does not exist). Ingestion dual-write error handling is tested only via integration flows in `SyncControllerTest`.
> - `ConsolidationRebalanceEngine.java`: Has no dedicated unit test file.
> - `FundTrendDampenerCalculator.java`: Has no dedicated unit test file.
> - `NipponHoldingsParser.java` & `PpfasHoldingsParser.java`: No unit tests exist for XML/PDF scheme holding disclosure parsing.

---

## 5. Known Issues & Historical Fallback Flags

1. **AMFI Feed Format Instability**: AMFI text files occasionally omit ISIN codes or shift column positions between 6, 7, and 8 columns. In commit `7ffe854`, `AmfiNavSync.java` was modified to scan dynamically from index 4 onwards. Missing NAVs log `AMFI_NAV_SYNC_ALERT` and fall back to cost basis.
2. **Strict Exemption Enforcement**: `RebalanceWaterfallEngine` was updated in commit `b7dd4e4` to throw an `IllegalStateException` if an asset is missing both live NAV and `costPerUnit`. Prior versions silently substituted `0.0`.
3. **Hardcoded Security Token Requirement**: `SecurityInterceptor` requires the `API_AUTH_TOKEN` environment variable to be explicitly defined. If unset or empty, the application throws an `IllegalStateException` at request time.
