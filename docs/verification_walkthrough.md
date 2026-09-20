# Walkthrough: FIFO Tax Lot Inspector & ITR-2 Capital Gains Screen

## Summary of Completed Changes

1. **Interactive FIFO Tax Lot Inspector Modal**:
   - Replaced placeholder `alert(...)` in Tab 2 with `openTaxLotInspector(assetId)`.
   - Built a high-density, terminal-themed modal dialog ([obsidian-terminal.html](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/resources/static/obsidian-terminal.html)) that displays:
     - Holding header with scheme name, category badge, ISIN, NAV, and AMFI TER.
     - Summary KPI strip: Total Units, Cost Basis, Current Valuation, Unrealized P&L, and Estimated Tax Drag.
     - Interactive filter chips: All Lots, LTCG Mature, STCG Locked, and Tax-Loss Candidates.
     - Detailed lot-by-lot FIFO table: Acquired Date, Units, Buy NAV vs Current NAV, Cost Basis, Current Value, Unrealized P&L, Holding Days, Tax Status (`LTCG 12.5%` vs `STCG 20% · X days to LTCG`), and Estimated Tax Drag.
     - Keyboard accessibility: `Esc` key and backdrop click close the modal.

2. **ITR-2 Capital Gains Filing Drill-Down Overlay (Preserving 3-Tab Invariant)**:
   - Preserved the daily-driver 3-tab navigation bar (`Overview`, `Tax Lots`, `Rebalance & FIRE`).
   - Upgraded the top header action to **"📑 ITR-2 Filing & Export"**, opening a dedicated full-screen overlay for statutory tax filing.
   - Built interactive sub-views:
     - **Schedule 112A**: Long-Term Capital Gains on Equities/MFs with Section 55(2)(ac) Grandfathering validation status, FMV as of 31-Jan-2018, and deemed cost deductions.
     - **Schedule STCG**: Short-Term Capital Gains under Section 111A (20% flat tax rate).
     - **Matched Disposals Audit Log**: Complete trade-by-trade matched log from the FIFO matching engine.
   - Integrated live ZIP download action to fetch `itr2_schedule_cg_{fy}.zip` directly from `/api/v1/tax/export/itr2/zip`.

3. **Strict Quiet Mode Masking Across Both New Surfaces**:
   - Both surfaces implement dual-layer `.lot-val-pnl` / `.lot-val-masked` structures.
   - When Quiet Mode is **ON**:
     - All rupee amounts (cost bases, valuations, realized/unrealized gains, tax drag, sale proceeds) are masked with `₹ •••,•••` or `Gain Suppressed`.
     - Statutory/relative metadata (holding days, units count, tax classifications, Grandfathering badges) remain visible so statutory compliance verification can be conducted without leaking wealth figures.
   - When Quiet Mode is **OFF**:
     - Exact unmasked numbers and percentage gains render dynamically.

4. **Typed Numeric DTOs with Fallback Honesty**:
   - Added `Schedule112aEntryDto`, `ScheduleStcgEntryDto`, and `Itr2DetailsDto` in [TaxReportExporter.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/reporting/TaxReportExporter.java).
   - Added `getItr2Details(fy)` in [TaxOptimizationService.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/service/TaxOptimizationService.java) and exposed `@GetMapping({"/reports/tax/itr2/details", "/tax/reports/itr2/details"})` in [ReportController.java](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/controllers/ReportController.java).

5. **Fixed Dead Export Button Bug**:
   - Resolved the pre-existing bug where `#terminalExportBtn` had no event listener.

---

## Verification & Test Results

### 1. Automated Unit Tests
Executed all tests in `Itr2CsvExporterTest`:
```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk env -u _JAVA_OPTIONS /home/rakeshpc/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test -Dtest=Itr2CsvExporterTest
```
**Result**: 7/7 tests passed cleanly (including the new regression test `testStructuredEntriesPreserveDimensionalIntegrityAndHonesty`).

---

### 2. Quiet Mode Verification (Screenshot Diff Pairs)

#### Surface 1: FIFO Tax Lot Inspector Modal

````carousel
![Tax Lot Modal - Quiet Mode OFF](/home/rakeshpc/.gemini/antigravity-ide/brain/bf63e073-8ee4-40e2-b53d-c9a5fe1d0950/modal_quiet_off.png)
<!-- slide -->
![Tax Lot Modal - Quiet Mode ON](/home/rakeshpc/.gemini/antigravity-ide/brain/bf63e073-8ee4-40e2-b53d-c9a5fe1d0950/modal_quiet_on.png)
````

- **Quiet Mode OFF**: Displays exact unmasked figures: Cost Basis `₹ 1,06,382`, Valuation `₹ 1,18,974`, Unrealized P&L `+₹ 12,592 (11.84%)`, Est. Tax Drag `₹ 2,169`, Header NAV `Current NAV: ₹ 209.36`, and table Buy NAV `₹ 159.01 (now ₹ 209.36)`.
- **Quiet Mode ON**:
  - **Zero reconstructible rupee leak**: All 4 summary cards (`Cost Basis`, `Current Valuation`, `Tax Drag`, and `Unrealized P&L`) are masked.
  - **Eliminated Arithmetic Reconstruction Gap**: Both the modal header `Current NAV` and the table column `BUY NAV` are strictly masked with `₹ •••.••`. Multiplying visible units (`15.332`) by price is completely prevented because no per-unit rupee price is exposed.
  - Relative metrics (`Units: 15.332`, `Holding Days: 552 days`, `Tax Status: LTCG (12.5%)`) remain visible for auditability.

---

#### Surface 2: ITR-2 Capital Gains Filing Overlay

````carousel
![ITR-2 Overlay - Quiet Mode OFF](/home/rakeshpc/.gemini/antigravity-ide/brain/bf63e073-8ee4-40e2-b53d-c9a5fe1d0950/itr_overlay_quiet_off.png)
<!-- slide -->
![ITR-2 Overlay - Quiet Mode ON](/home/rakeshpc/.gemini/antigravity-ide/brain/bf63e073-8ee4-40e2-b53d-c9a5fe1d0950/itr_overlay_quiet_on.png)
````

- **Quiet Mode OFF**: Displays exact unmasked figures: Sale Consideration `₹ 2,18,574`, Cost of Acquisition `₹ 2,13,408`, Short-Term Gains `₹ 5,603`, Long-Term Gains `-₹ 436`, and Schedule 112A breakdown rows (`₹ 6,635`, `₹ 7,000`, `₹ -365`).
- **Quiet Mode ON**: Every single sale consideration, cost basis, FMV 2018, and balance capital gain is masked with `₹ •••,•••` and `Gain Suppressed`. ISIN codes, scheme names, units count, and Grandfathering badges (`Post-2018 (No FMV)`, `Sec 55(2)(ac) Validated`) remain visible for compliance auditing.
