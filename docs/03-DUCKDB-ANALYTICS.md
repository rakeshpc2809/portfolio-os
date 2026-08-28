# DuckDB Analytics & Net Worth Engine Subsystem

The DuckDB analytics layer provides in-memory and persistent analytical projections for Portfolio OS. It handles high-speed daily net worth time series computations, historical portfolio vs. benchmark return alignments, fund overlap matrix calculations, and portfolio stock concentration analysis.

---

## 1. Architecture Map

The DuckDB engine is embedded directly inside `core-node` via `DuckDbProjector.java` using HikariCP connection pooling (`HikariDataSource`):

```
core-node/src/main/java/com/portfolioos/core/persistence/
└── DuckDbProjector.java                          # Embedded DuckDB JDBC Projector & SQL Engine

DuckDB Tables (data/tax_ledger.duckdb):
├── projected_events                             # Re-projected transaction ledger events
├── nav_history                                  # Daily asset NAV time series
├── benchmark_history                            # Daily benchmark index levels (e.g. Nifty 500)
└── fund_holdings                                # Monthly underlying stock portfolio disclosures
```

---

## 2. Data Flow: Daily Net Worth Trend Query Execution

When the web dashboard or Android app requests net worth history (`GET /portfolio/net-worth-trend`), the query executes inside DuckDB:

```
[REST Client / Frontend]
       |
       v  1. GET /portfolio/net-worth-trend
[PortfolioValuationService.java]
       |
       v  2. Invokes DuckDB projector query
[DuckDbProjector.java:L402] getDailyNetWorthTrend()
       |
       v  3. Executes Symmetric Cost-Fallback SQL CTE
[DuckDbProjector.java:L405-L461]
       |
       |----> CTE 1: daily_dates (UNION of nav_history dates & projected_events dates)
       |----> CTE 2: active_units_per_asset (cumulative units held & latest NAV on/before date)
       |              - Computes market_nav (subquery from nav_history)
       |              - Computes cost_nav (AVG(price_per_unit) from projected_events)
       |----> CTE 3: daily_valuation
       |              - total_valuation = SUM(units * COALESCE(market_nav, cost_nav, 0.0))
       |              - real_nav_valuation = SUM(units * market_nav)
       |----> CTE 4: daily_invested (cumulative gross_amount of acquisitions - disposals)
       |
       v  4. Evaluates estimation flag
       |  is_estimated = (real_nav_valuation < total_valuation - 0.01)
       |
       v  5. Returns List<NetWorthPoint>
[DuckDbProjector.java:L471] return trend
```

---

## 3. Business Logic Inventory & SQL Query Reference

### 3.1. Cost-Fallback Symmetric Net Worth SQL Query
* **Source**: [`DuckDbProjector.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java#L405-L461)
* **Problem Addressed**: On dates where an asset lacks a recorded NAV in `nav_history`, multiplying units by `0.0` or `null` causes artificial net worth dips in historical charts.
* **SQL Solution**:
```sql
WITH daily_dates AS (
    SELECT DISTINCT nav_date FROM nav_history
    UNION
    SELECT DISTINCT event_date AS nav_date FROM projected_events
),
active_units_per_asset AS (
    SELECT 
        d.nav_date,
        pe.asset_id,
        SUM(CASE 
                WHEN pe.event_type IN ('ACQUISITION', 'SIP_INSTALMENT') THEN CAST(pe.units AS DOUBLE)
                WHEN pe.event_type = 'DISPOSAL' THEN -CAST(pe.units AS DOUBLE)
                ELSE 0.0 
            END) AS active_units,
        (
            SELECT nh.nav 
            FROM nav_history nh 
            WHERE nh.asset_id = pe.asset_id AND nh.nav_date <= d.nav_date 
            ORDER BY nh.nav_date DESC 
            LIMIT 1
        ) AS market_nav,
        AVG(CAST(pe.price_per_unit AS DOUBLE)) AS cost_nav
    FROM daily_dates d
    JOIN projected_events pe ON pe.event_date <= d.nav_date
    GROUP BY d.nav_date, pe.asset_id
),
daily_valuation AS (
    SELECT 
        nav_date,
        SUM(active_units * COALESCE(market_nav, cost_nav, 0.0)) AS total_valuation,
        SUM(CASE WHEN market_nav IS NOT NULL THEN active_units * market_nav ELSE 0.0 END) AS real_nav_valuation
    FROM active_units_per_asset a
    WHERE active_units > 0
    GROUP BY nav_date
)
...
```

### 3.2. Portfolio vs. Benchmark Return Alignment SQL
* **Source**: [`DuckDbProjector.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java#L151-L205)
* **Logic**: Computes unit-weighted portfolio daily returns across all held funds and aligns them against benchmark daily returns (e.g. Nifty 500) for identical trading date pairs. Restricts date gaps to `1 <= daysBetween <= 5` to filter out non-trading holidays.

### 3.3. Pairwise Fund Overlap & UpSet Multi-Fund Analytics
* **Source**: [`DuckDbProjector.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java#L607-L676) & [`L735-L800`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java#L735-L800)
* **Logic**: Joins underlying stock holdings across mutual funds based on latest disclosure dates. Calculates total overlap percentage `SUM(LEAST(weight_a, weight_b))` and groups stock intersections into UpSet set combinations.

---

## 4. Test Coverage Map

Empirical test count obtained via `grep -rc "@Test" core-node/src/test/java`:

| Test Class | Path | Real `@Test` Count | Verified Behaviors |
| :--- | :--- | :---: | :--- |
| `DuckDbProjectorNetWorthAccountingTest` | [`DuckDbProjectorNetWorthAccountingTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/service/DuckDbProjectorNetWorthAccountingTest.java) | **1** | Cost-fallback symmetric SQL execution & net worth point generation |

---

## 5. Known Issues & Historical Fallback Flags

1. **Cost Fallback Indicator**: When `real_nav_valuation < total_valuation - 0.01`, the returned `NetWorthPoint` sets `is_estimated = true`. The UI uses this flag to display an amber alert banner indicating that historical NAVs were partially estimated using purchase cost.
2. **DuckDB Database File Locking**: DuckDB uses single-writer process file locking. HikariCP connection pooling (`DuckDbProjectorPool`) is configured with `maximumPoolSize = 10` to handle multi-threaded REST requests smoothly.
