# Fund Constituents Dataset Provenance (`fund_constituents.json`)

## 1. Overview & Purpose
This dataset provides an immutable static baseline of portfolio mutual fund and ETF constituent stock holdings (287 rows). It is seeded during DuckDB initialization (`DuckDbProjector.seedStandardConstituents()`) to calculate pairwise fund overlap and top equity stock concentrations across the portfolio.

This bundled asset replaced brittle runtime scrapers (`NseIndexConstituentDownloader`, `PpfasHoldingsParser`, `NipponHoldingsParser`) and removed Apache POI from the production classpath during the Phase 4 engine modernization.

---

## 2. Dataset Classification & Sources

All constituent holdings carry an effective disclosure date of **`2026-03-31`** (end of Q4 FY 2025-26).

### A. Factsheet Holdings (`FACTSHEET_POI_PARSED` — 67 rows)
Extracted directly from official monthly portfolio disclosure workbooks published by the respective Asset Management Companies (AMCs). 

> **Local Source Notice**: The source `.xlsx` workbooks reside in the local development directory `data/factsheets/` (which is excluded from Git via `.gitignore` to prevent tracking binary vendor artifacts). Their authenticity can be verified against the SHA-256 checksums and official AMC disclosure portals below:

| ISIN | Fund Name | Row Count | Local Workbook | File Size | SHA-256 Checksum | Official AMC Portal |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `INF204K01K15` | **Nippon India Small Cap Fund** | 38 | `data/factsheets/nippon_smallcap_full.xlsx` | 6,731 bytes | `7f21d3617b7f1dc29f989b121fb444b2d9004887d387e9b1979b5d3c5864c834` | [Nippon Life India AMC Disclosures](https://mf.nipponindiaim.com) |
| `INF879O01027` | **Parag Parikh Flexi Cap Fund** | 29 | `data/factsheets/ppfas_flexicap_full.xlsx` | 6,244 bytes | `6554ed1611fd69520a9a28735e09d90a362b0e35e0b50a7e0854eb2aa514e658` | [PPFAS AMC Monthly Portfolios](https://amc.ppfas.com) |

*Retrieval Timestamp*: 2026-08-08 16:50 IST (Nippon), 2026-08-08 16:40 IST (PPFAS).

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

> **Top-N Lower-Bound Characteristic**: For all 14 passive index schemes, the static dataset captures the top 10 to 20 constituent stock holdings representing the majority of index capitalization. Pairwise overlap and aggregate portfolio stock concentration percentages derived from index holdings represent verified **mathematical lower bounds**. Tail-end holdings with minuscule weights (< 1%) are omitted to conserve storage and query execution budgets.

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

## 3. Architecture & Fail-Closed Provenance Defaults
Portfolio overlap and stock concentration calculations reside in `DuckDbProjector.java`, coordinated through `PortfolioValuationService.java` and exposed by `ReportController.java`.

1. **Fail-Closed Default (`source_type = "UNKNOWN"`)**:
   Any fund with zero rows in `fund_holdings` strictly defaults to `source_type = "UNKNOWN"`. Only `FACTSHEET_POI_PARSED` and `NSE_INDEX_CONSTITUENTS` are recognized as audited. Funds with `UNKNOWN` or `MANUAL_ESTIMATE_UNVERIFIED` provenance are classified as unverified AUM and skipped by default in audited-only mode.
2. **Audited-Only Isolation by Default**:
   In `ReportController.java` (`getPortfolioConcentrations`), `includeUnverified` defaults to `false`. In `DuckDbProjector.getPortfolioStockConcentrations()`, unverified funds are excluded from top stock concentration aggregations unless explicitly opted in by the user (`includeUnverified = true`).
3. **Deterministic Point Assertions**:
   `PortfolioOverlapAuditTest.testStockConcentrationsAuditedOnlyVsIncludeUnverified` asserts that unverified funds are excluded from audited telemetry (e.g. ₹2,50,000 audited AUM vs ₹12,50,000 total equity AUM).
   `PortfolioOverlapAuditTest.testNewlyHeldFundWithNoHoldingsDefaultsToUnknownAndIsExcludedFromAuditedMetrics` verifies that an unseeded fund cannot inflate audited AUM.

---

## 4. Fresh Clone Failure Diagnostics (`PortfolioOverlapAuditTest`)
When `fund_constituents.json` was missing due to `.gitignore` directory suppression, `PortfolioOverlapAuditTest` failed on 3 assertions:

* **Line 75 (`testPairwiseOverlapExactMathematicalValues`)**:
  `expected: <23.56> but was: <0.0>`. With `fund_holdings` empty, the inner join on common stock symbols produced zero rows, resulting in 0.0% overlap.
* **Line 155 (`testPairwiseOverlapProvenanceTags`)**:
  `expected: <FACTSHEET_POI_PARSED> but was: <NSE_INDEX_CONSTITUENTS>`. In earlier revisions, `DuckDbProjector.getPairwiseFundOverlap()` defaulted `srcA` to `"NSE_INDEX_CONSTITUENTS"` when `SELECT MAX(source_type)` returned `NULL`. Now resolved with `UNKNOWN` fail-closed default.
* **Line 126 (`testStockConcentrationsAuditedOnlyVsIncludeUnverified`)**:
  `expected: <250000.0> but was: <1250000.0>`. In earlier revisions, `DuckDbProjector.getPortfolioStockConcentrations()` defaulted `srcType` to `"NSE_INDEX_CONSTITUENTS"` when `metaSql` returned `NULL`, treating unverified funds as audited. Now resolved by checking `isAudited` against allowed types.
