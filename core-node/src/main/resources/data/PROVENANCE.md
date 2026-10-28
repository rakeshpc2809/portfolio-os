# Fund Constituents Dataset Provenance (`fund_constituents.json`)

## 1. Overview & Purpose
This dataset provides an immutable static baseline of portfolio mutual fund and ETF constituent stock holdings (287 rows). It is seeded during DuckDB initialization (`DuckDbProjector.seedStandardConstituents()`) to calculate pairwise fund overlap and top equity stock concentrations across the portfolio.

This bundled asset replaced brittle runtime scrapers (`NseIndexConstituentDownloader`, `PpfasHoldingsParser`, `NipponHoldingsParser`) and removed Apache POI from the production classpath during the Phase 4 engine modernization.

---

## 2. Dataset Classification & Sources

All constituent holdings carry an effective disclosure date of **`2026-03-31`** (end of Q4 FY 2025-26).

### A. Factsheet Holdings (`FACTSHEET_POI_PARSED` — 67 rows)
Directly extracted from official monthly portfolio disclosure workbooks published by the respective Asset Management Companies (AMCs):

| ISIN | Fund Name | Row Count | Source Document | Publisher / AMC | Disclosure Date |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `INF204K01K15` | **Nippon India Small Cap Fund** | 38 | `data/factsheets/nippon_smallcap_full.xlsx` | Nippon Life India Asset Management Ltd. | 2026-03-31 |
| `INF879O01027` | **Parag Parikh Flexi Cap Fund** | 29 | `data/factsheets/ppfas_flexicap_full.xlsx` | PPFAS Mutual Fund | 2026-03-31 |

### B. Index Constituents (`NSE_INDEX_CONSTITUENTS` — 160 rows)
Top 10 to 20 constituent stock weightages for passive index funds and ETFs, extracted from official National Stock Exchange of India (NSE) index weightage disclosures published on **2026-03-31** (semi-annual index reconstitution):

| Fund ID / ISIN | Fund Name | Holdings | Underlying NSE Index |
| :--- | :--- | :--- | :--- |
| `118741` | Nippon India ETF Nifty 50 BeES | 10 | Nifty 50 |
| `140088` | Nippon India ETF Nifty Next 50 Junior BeES | 10 | Nifty Next 50 |
| `147702` | Motilal Oswal Nifty 500 Index Fund | 10 | Nifty 500 |
| `148943` | Motilal Oswal Nifty Midcap 150 Index Fund | 10 | Nifty Midcap 150 |
| `150642` | ICICI Prudential Nifty 200 Value 30 ETF | 20 | Nifty 200 Value 30 |
| `151814` | UTI Nifty 50 Index Fund | 10 | Nifty 50 |
| `152985` | HDFC Nifty Smallcap 250 Index Fund | 10 | Nifty Smallcap 250 |
| `INF109KC12U0` | ICICI Prudential Nifty LargeMidcap 250 Index Fund | 10 | Nifty LargeMidcap 250 |
| `INF109KC13X2` | ICICI Prudential Nifty200 Value 30 Index Fund | 20 | Nifty 200 Value 30 |
| `INF174KA1TY2` | Kotak Nifty 50 Index Fund | 10 | Nifty 50 |
| `INF204K01H36` | Nippon India Index Fund Nifty 50 Plan | 10 | Nifty 50 |
| `INF247L01916` | Motilal Oswal Nifty Midcap 150 Index Fund | 10 | Nifty Midcap 150 |
| `INF247L01BQ9` | Motilal Oswal Nifty Microcap 250 Index Fund | 10 | Nifty Microcap 250 |
| `INF754K01TN5` | Edelweiss Nifty500 Multicap Mom Qual 50 Index Fund | 10 | Nifty500 Multicap 50:25:25 |

### C. Unverified Manual Estimates (`MANUAL_ESTIMATE_UNVERIFIED` — 60 rows)
Top 10 approximate holdings for specialized sectoral and international funds where automated RTA feeds were unavailable:

| ISIN | Fund Name | Holdings | Disclosure Date |
| :--- | :--- | :--- | :--- |
| `INF109K018M4` | ICICI Prudential US Bluechip Equity Fund | 10 | 2026-03-31 |
| `INF200K01RA0` | SBI Technology Opportunities Fund | 10 | 2026-03-31 |
| `INF200K01UJ5` | SBI Small Cap Fund | 10 | 2026-03-31 |
| `INF204K01G52` | Nippon India Small Cap Fund (Alt Plan) | 10 | 2026-03-31 |
| `INF277K011O1` | Tata Digital India Fund | 10 | 2026-03-31 |
| `INF769K01ED6` | Mirae Asset Healthcare Fund | 10 | 2026-03-31 |

---

## 3. Architecture & Strict Audited Isolation
Portfolio overlap and stock concentration calculations reside in `DuckDbProjector.java`, coordinated through `PortfolioValuationService.java` and exposed by `ReportController.java`.

1. **Audited-Only Isolation by Default**:
   In `ReportController.java` (`getPortfolioConcentrations`), `includeUnverified` defaults to `false`. In `DuckDbProjector.getPortfolioStockConcentrations()`, any fund with `source_type == "MANUAL_ESTIMATE_UNVERIFIED"` is completely skipped from stock aggregations unless explicitly opted in by the user (`includeUnverified = true`).
2. **Deterministic Point Assertions**:
   `PortfolioOverlapAuditTest.testStockConcentrationsAuditedOnlyVsIncludeUnverified` asserts that unverified funds are excluded from audited telemetry (e.g. ₹2,50,000 audited AUM vs ₹12,50,000 total equity AUM).

---

## 4. Fresh Clone Failure Diagnostics (`PortfolioOverlapAuditTest`)
When `fund_constituents.json` was missing due to `.gitignore` directory suppression, `PortfolioOverlapAuditTest` failed on 3 assertions:

* **Line 75 (`testPairwiseOverlapExactMathematicalValues`)**:
  `expected: <23.56> but was: <0.0>`. With `fund_holdings` empty, the inner join on common stock symbols produced zero rows, resulting in 0.0% overlap.
* **Line 155 (`testPairwiseOverlapProvenanceTags`)**:
  `expected: <FACTSHEET_POI_PARSED> but was: <NSE_INDEX_CONSTITUENTS>`. In `DuckDbProjector.getPairwiseFundOverlap()`, `String srcA = "NSE_INDEX_CONSTITUENTS"` is the fallback default when `SELECT MAX(source_type)` returns `NULL`.
* **Line 126 (`testStockConcentrationsAuditedOnlyVsIncludeUnverified`)**:
  `expected: <250000.0> but was: <1250000.0>`. In `DuckDbProjector.getPortfolioStockConcentrations()`, `String srcType = "NSE_INDEX_CONSTITUENTS"` is the default fallback when `SELECT MAX(source_type)` returns `NULL`. Consequently, all funds (including the ₹10L unverified Tata Small Cap) were treated as audited NSE index funds (`isUnverified = false`), erroneously summing all ₹12,50,000 into `totalAuditedValuation`.
