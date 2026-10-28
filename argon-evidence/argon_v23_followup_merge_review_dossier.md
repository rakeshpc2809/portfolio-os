# Portfolio OS — Merge Review Evidence Dossier for Gemini Argon

## Overview

This dossier and associated Repomix review packs provide the complete source code, test suites, mathematical specifications, and execution telemetry for merging branch **`feat/v23-followup-tasks`** (HEAD: `f82fb48`) into **`main`** (parent: `d5acd07`).

### 📦 Review Pack Artifacts
* **XML Review Pack:** [repomix-merge-review.xml](file:///home/rakeshpc/Projects/portfolio-os/repomix-merge-review.xml) *(25 files • 177,622 tokens • 656,027 chars)*
* **Markdown Review Pack:** [repomix-merge-review.md](file:///home/rakeshpc/Projects/portfolio-os/repomix-merge-review.md) *(25 files • 160,419 tokens • 612,167 chars)*
* **Repomix Config:** [repomix-merge-review.config.json](file:///home/rakeshpc/Projects/portfolio-os/repomix-merge-review.config.json)

---

## 1. Commit Trajectory (6 Atomic Commits)

```text
* f82fb48 test(mobile): audit LocalTradeSimulator against core node with 20-scenario parity test suite
* e32c478 refactor(persistence): clean trigger history overloads and inject repository into controllers
* 99f156a feat(tax): align lot-level estimated tax drag with Section 70 netting (F5)
* 0eb25fe feat(valuation): fixed arbitrage baseline for drawdown deployment sizing (F7)
* f84d294 feat(rules): versioned drift_trigger_pct for gold bucket targets (F11)
* 9bd5aa2 feat(valuation): fail loudly on null or non-positive benchmark inputs (F10)
* d5acd07 (origin/main, main) Merge branch 'audit/v23-rebalance-fixes' into main
```

---

## 2. Technical Implementation Breakdown

### Task 1: F7 — Fixed Arbitrage Baseline for Drawdown Deployment Sizing
* **Commit:** `0eb25fe`
* **Problem Solved:** When a drawdown escalated or refired (e.g. Tier 1 at -10% $\to$ Tier 2 at -20%), calculating tranche percentages against a diminished active balance resulted in undersized subsequent tranches (e.g. ₹66,660 instead of ₹1,00,000 from an initial ₹3,00,000 baseline).
* **Implementation:**
  * Added `active_drawdown_baseline` table and accessors (`getActiveDrawdownArbitrageBaseline()`, `setActiveDrawdownArbitrageBaseline(...)`, `clearActiveDrawdownArbitrageBaseline()`) in [TriggerHistoryRepository.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/TriggerHistoryRepository.java).
  * In [BucketEngine.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/valuation/BucketEngine.java), `calculateDrawdownDeploymentPlan(...)` retrieves the fixed baseline or records it upon initial Tier 1 arming. Sizing calculates:
    $$\text{trancheAmount} = \min\big(\text{baselineArbitrageValue} \times \text{rungPercentage}, \text{currentExitLoadFreeArbitrageValue}\big)$$
  * The baseline automatically clears once benchmark drawdown recovers above $-5.0\%$.
* **Tests:** [BucketEngineTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/valuation/BucketEngineTest.java) (`testCalculateDrawdownDeploymentPlanFixedBaselineCompounding`), [TriggerHistoryRepositoryTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/persistence/TriggerHistoryRepositoryTest.java).

---

### Task 2: F10 — Fail Loudly on Null/Missing Benchmark Inputs
* **Commit:** `9bd5aa2`
* **Problem Solved:** Returning `0.0` drawdown on null or non-positive 52-week peak or live NAV silently masked broken upstream data pipelines as "calm markets."
* **Implementation:**
  * [PortfolioConstants.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/common/PortfolioConstants.java): Added `requireValidDrawdownPct(BigDecimal currentVal, BigDecimal rollingHigh, String benchmarkContext)`. Throws `IllegalStateException("CRITICAL BENCHMARK FAILURE: ...")` when either input is null or $\le 0$.
  * [RebalanceTriggerEvaluator.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/service/RebalanceTriggerEvaluator.java): Replaced silent disarm with `requireValidDrawdownPct`.
  * [GlobalExceptionHandler.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/exceptions/GlobalExceptionHandler.java): Mapped critical benchmark failure exceptions to HTTP 500 `"PIPELINE_DEGRADED"`.
* **Tests:** [RebalanceTriggerEvaluatorTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/service/RebalanceTriggerEvaluatorTest.java), [SyncControllerTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/controllers/SyncControllerTest.java).

---

### Task 3: F5 — Align DTO Lot-Level Tax Estimates with Section 70 Netting
* **Commit:** `99f156a`
* **Problem Solved:** Individual lot objects in `WaterfallStep` and `RebalancePlanDto` showed un-netted gross tax drag ($\text{gain} \times \text{rate}$), diverging from `summary.totalTaxEstimate()`.
* **Implementation:**
  * In [RebalanceWaterfallEngine.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/valuation/RebalanceWaterfallEngine.java), introduced `prorateSection70Netting(...)` second-pass. Lots offset by capital losses are updated to `estimatedTaxDrag = BigDecimal.ZERO` and annotated with `lossOffsetApplied: true`. Remaining post-netting statutory liabilities are prorated across unabsorbed lots.
  * Added `lossOffsetApplied` to `WaterfallStep`, [RebalancePlanDtos.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/dtos/RebalancePlanDtos.java) (`LotTaxImpactDto`, `RebalanceLotImpactDto`).
  * [RebalancePlanEngine.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/service/RebalancePlanEngine.java) wires the proration into plan generation, ensuring $\sum \text{lot.estimatedTaxDrag} \equiv \text{summary.totalTaxEstimate()}$.
* **Tests:** [RebalanceWaterfallEngineTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/valuation/RebalanceWaterfallEngineTest.java), [RebalancePlanEngineTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/service/RebalancePlanEngineTest.java).

---

### Task 4: F11 — Config-Driven Gold Drift Threshold
* **Commit:** `f84d294`
* **Problem Solved:** Hardcoded drift fallback replaced with versioned rules YAML configuration.
* **Implementation:**
  * [bucket_targets.yaml](file:///home/rakeshpc/Projects/portfolio-os/core-node/rules/bucket_targets.yaml): Added `band_pct: 2.0` and `drift_trigger_pct: 5.0` to `GOLD_SILVER` bucket target in schema v2.3.
  * [BucketConfigLoader.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/rules/BucketConfigLoader.java): Added `driftTriggerPct` property to `BucketTargetConfig` record.
  * [RebalanceTriggerEvaluator.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/service/RebalanceTriggerEvaluator.java): Replaced hardcoded constants with `targetConfig.driftTriggerPct()`.
* **Tests:** [ProductionConfigBootSmokeTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/integration/ProductionConfigBootSmokeTest.java), [backtest_allocations.py](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/scripts/backtest_allocations.py).

---

### Tasks 5 & 6: Trigger History Overload Derivation & Controller Injection
* **Commit:** `e32c478`
* **Problem Solved:** Brittle JSON string parsing for execution flags replaced with explicit booleans; per-request repository allocations eliminated.
* **Implementation:**
  * [RebalanceController.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/controllers/RebalanceController.java): Mandatory constructor injection of `TriggerHistoryRepository`; removed single-arg fallback constructor.
  * [SyncController.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/controllers/SyncController.java): Eliminated `new TriggerHistoryRepository()` heap fallback allocations.
  * [TriggerHistoryRepository.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/TriggerHistoryRepository.java): Deprecated legacy 7-argument overload in favor of the explicit 8-argument method.
* **Tests:** [RebalanceControllerTest.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/test/java/com/portfolioos/core/controllers/RebalanceControllerTest.java).

---

### Task 7: Mobile Parity Audit (`LocalTradeSimulator.kt`)
* **Commit:** `f82fb48`
* **Problem Solved:** Assured bit-for-bit parity between on-device Android Kotlin trade simulation and Core Node Java engine.
* **Implementation:**
  * In [LocalTradeSimulator.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/data/nav/LocalTradeSimulator.kt), unified grandfathered cost basis with Section 55(2)(ac) formula matching Core Node [Lot.java:35](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/model/Lot.java#L35):
    $$\text{effectiveCostPerUnit} = \max\big(\text{costPerUnit}, \min(\text{grandfatheredNav}, \text{saleNav})\big)$$
  * Verified Section 70 intra-head loss netting order (STCL against STCG, unabsorbed STCL against LTCG before Section 112A exemption, LTCL against LTCG before exemption).
* **Tests:** Added [LocalTradeSimulatorParityTest.kt](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/test/java/com/portfolioos/mobile/LocalTradeSimulatorParityTest.kt) with 20 distinct parameterized trade scenarios:
  1. Acquisition buy SIP (adds to cost, zero gain/tax)
  2. Pure equity STCG (@ 20% tax)
  3. Pure equity LTCG within ₹1.25L exemption (zero tax)
  4. Equity LTCG exceeding ₹1.25L exemption (excess @ 12.5%)
  5. Equity LTCG straddling reduced exemption headroom (40k headroom, excess @ 12.5%)
  6. STCL completely offsetting STCG (zero tax)
  7. STCL partially offsetting STCG (net gain @ 20%)
  8. Unabsorbed STCL offsetting Equity LTCG before exemption (Sec 70 cross-head set-off)
  9. LTCL offsetting Equity LTCG before Section 112A exemption
  10. Non-equity LTCG (Gold FoF > 730d) @ 12.5% without Section 112A exemption
  11. Non-equity STCG (Gold FoF < 730d) @ 30% slab rate
  12. Specified Debt (Sec 50AA) STCG @ 30% slab rate
  13. Grandfathered lot: Cost 100, FMV 150, Sale 180 $\to$ Basis 150, Gain 30
  14. Grandfathered lot: Cost 100, FMV 150, Sale 120 $\to$ Basis 120, Gain 0
  15. Grandfathered lot: Cost 100, FMV 150, Sale 90 $\to$ Basis 100, Loss -10
  16. Grandfathered lot: Cost 150, FMV 120, Sale 180 $\to$ Basis 150, Gain 30
  17. Grandfathered lot: Cost 150, FMV 120, Sale 100 $\to$ Basis 150, Loss -50
  18. Multi-lot FIFO partial liquidation across 2 lots
  19. Full liquidation of entire lot pool
  20. Oversold request capped at available lot inventory

---

## 3. Monorepo Gate Execution Telemetry

```text
=== Monorepo Gate Run: just check ===
Core Node (Java 21):   260 tests passed, 0 failures, 0 errors
Quant Sidecar (Py312):  47 tests passed, 0 failures
Android App (Kotlin):   68 tests passed, 0 failures (24 actionable tasks: 24 executed)
Cargo TUI (Rust):       All unit & integration tests passed
Web UI (Svelte 5):      Vite production build clean (4,365 modules transformed), static ARIA contracts verified

>>> ALL GATES PASSED CLEANLY WITH ZERO WARNINGS OR ROLLBACKS <<<
```
