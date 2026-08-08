package com.portfolioos.core.persistence;

import com.portfolioos.core.model.TaxEvent;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

public class DuckDbProjector {

    private final String dbPath;
    private final String jdbcUrl;
    private final HikariDataSource dataSource;

    public static record NavHistorySeriesEntry(
        List<Double> navs,
        List<String> dates
    ) {}

    public DuckDbProjector() {
        this(System.getenv("DUCKDB_PATH") != null && !System.getenv("DUCKDB_PATH").isBlank()
             ? System.getenv("DUCKDB_PATH") : "data/tax_ledger.duckdb");
    }

    public DuckDbProjector(String dbPath) {
        this.dbPath = dbPath;
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("DuckDB JDBC driver not found", e);
        }

        if (":memory:".equals(dbPath)) {
            jdbcUrl = "jdbc:duckdb:";
        } else {
            File file = new File(dbPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            jdbcUrl = "jdbc:duckdb:" + file.getAbsolutePath();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.duckdb.DuckDBDriver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setPoolName("DuckDbProjectorPool");

        this.dataSource = new HikariDataSource(config);
        initReadSchema();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initReadSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS projected_events (" +
                "  id VARCHAR PRIMARY KEY," +
                "  asset_id VARCHAR NOT NULL," +
                "  asset_name VARCHAR NOT NULL," +
                "  isin VARCHAR," +
                "  event_type VARCHAR NOT NULL," +
                "  event_date VARCHAR NOT NULL," +
                "  units VARCHAR NOT NULL," +
                "  price_per_unit VARCHAR NOT NULL," +
                "  gross_amount VARCHAR NOT NULL," +
                "  source_document_id VARCHAR NOT NULL," +
                "  ingested_at VARCHAR NOT NULL" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS nav_history (" +
                "  asset_id VARCHAR NOT NULL," +
                "  nav_date VARCHAR NOT NULL," +
                "  nav DOUBLE NOT NULL," +
                "  PRIMARY KEY (asset_id, nav_date)" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS benchmark_history (" +
                "  benchmark_id VARCHAR NOT NULL," +
                "  nav_date VARCHAR NOT NULL," +
                "  level DOUBLE NOT NULL," +
                "  PRIMARY KEY (benchmark_id, nav_date)" +
                ")"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DuckDB schema", e);
        }
    }

    public void saveBenchmarkLevels(String benchmarkId, Map<String, Double> dateToLevel) {
        if (dateToLevel == null || dateToLevel.isEmpty()) return;
        String sql = "INSERT OR REPLACE INTO benchmark_history (benchmark_id, nav_date, level) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int batchSize = 0;
            for (Map.Entry<String, Double> entry : dateToLevel.entrySet()) {
                pstmt.setString(1, benchmarkId);
                pstmt.setString(2, entry.getKey());
                pstmt.setDouble(3, entry.getValue());
                pstmt.addBatch();
                batchSize++;
                if (batchSize % 1000 == 0) {
                    pstmt.executeBatch();
                }
            }
            if (batchSize % 1000 != 0) {
                pstmt.executeBatch();
            }
        } catch (SQLException e) {
            System.err.println("Failed to save benchmark levels: " + e.getMessage());
        }
    }

    public Map<String, Object> getAlignedPortfolioAndBenchmarkReturns(String benchmarkId) {
        List<Double> portfolioReturns = new ArrayList<>();
        List<Double> benchmarkReturns = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = """
                WITH nav_dates AS (
                    SELECT DISTINCT nav_date FROM nav_history WHERE nav > 0
                ),
                unit_changes AS (
                    SELECT asset_id, event_date,
                           SUM(CASE WHEN event_type IN ('ACQUISITION', 'SIP_INSTALMENT', 'BONUS') THEN CAST(units AS DOUBLE) ELSE -CAST(units AS DOUBLE) END) AS change_units
                    FROM projected_events
                    GROUP BY asset_id, event_date
                ),
                asset_daily_units AS (
                    SELECT n.nav_date, u.asset_id, SUM(u.change_units) AS units_held
                    FROM nav_dates n
                    JOIN unit_changes u ON u.event_date <= n.nav_date
                    GROUP BY n.nav_date, u.asset_id
                    HAVING units_held > 0
                ),
                fund_daily_returns AS (
                    SELECT asset_id, nav_date, nav,
                           LAG(nav_date) OVER (PARTITION BY asset_id ORDER BY nav_date ASC) AS prev_date,
                           LAG(nav) OVER (PARTITION BY asset_id ORDER BY nav_date ASC) AS prev_nav
                    FROM nav_history WHERE nav > 0
                ),
                valid_weighted_returns AS (
                    SELECT f.asset_id, f.nav_date, f.prev_date,
                           du.units_held * f.prev_nav AS weight,
                           (f.nav - f.prev_nav) / f.prev_nav AS fund_ret
                    FROM fund_daily_returns f
                    JOIN asset_daily_units du ON f.asset_id = du.asset_id AND du.nav_date = f.prev_date
                    WHERE f.prev_nav > 0 AND f.prev_date IS NOT NULL AND du.units_held > 0
                ),
                daily_portfolio_returns AS (
                    SELECT nav_date, prev_date,
                           SUM(weight * fund_ret) / SUM(weight) AS blended_ret
                    FROM valid_weighted_returns
                    GROUP BY nav_date, prev_date
                    HAVING SUM(weight) > 0
                ),
                benchmark_daily_returns AS (
                    SELECT nav_date, level,
                           LAG(nav_date) OVER (ORDER BY nav_date ASC) AS prev_date,
                           LAG(level) OVER (ORDER BY nav_date ASC) AS prev_level
                    FROM benchmark_history
                    WHERE benchmark_id = ? AND level > 0
                ),
                valid_benchmark_returns AS (
                    SELECT nav_date, prev_date,
                           (level - prev_level) / prev_level AS b_ret
                    FROM benchmark_daily_returns
                    WHERE prev_level > 0 AND prev_date IS NOT NULL
                )
                SELECT p.nav_date, p.prev_date, p.blended_ret, b.b_ret
                FROM daily_portfolio_returns p
                JOIN valid_benchmark_returns b ON p.nav_date = b.nav_date AND p.prev_date = b.prev_date
                ORDER BY p.nav_date ASC;
            """;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, benchmarkId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String dateStr = rs.getString("nav_date");
                        String prevDateStr = rs.getString("prev_date");
                        double pRet = rs.getDouble("blended_ret");
                        double bRet = rs.getDouble("b_ret");
                        java.time.LocalDate currDate = null;
                        java.time.LocalDate prevDate = null;
                        try {
                            currDate = java.time.LocalDate.parse(dateStr);
                            prevDate = java.time.LocalDate.parse(prevDateStr);
                        } catch (Exception ignored) {}

                        if (currDate != null && prevDate != null) {
                            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(prevDate, currDate);
                            if (daysBetween >= 1 && daysBetween <= 5) {
                                if (Math.abs(pRet) < 0.08 * daysBetween && Math.abs(bRet) < 0.08 * daysBetween) {
                                    double pDaily = Math.pow(1.0 + pRet, 1.0 / daysBetween) - 1.0;
                                    double bDaily = Math.pow(1.0 + bRet, 1.0 / daysBetween) - 1.0;
                                    portfolioReturns.add(pDaily);
                                    benchmarkReturns.add(bDaily);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching aligned benchmark returns: " + e.getMessage());
        }

        Map<String, Object> res = new HashMap<>();
        res.put("portfolio_returns", portfolioReturns);
        res.put("benchmark_returns", benchmarkReturns);
        return res;
    }

    public void checkpoint() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CHECKPOINT;");
        } catch (SQLException e) {
            System.err.println("DuckDB checkpoint error: " + e.getMessage());
        }
    }

    public void projectEvents(List<TaxEvent> events) {
        if (events == null || events.isEmpty()) return;

        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                String insertSql = "INSERT INTO projected_events (id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id, ingested_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    Set<String> processedIds = new HashSet<>();
                    for (TaxEvent event : events) {
                        if (processedIds.contains(event.id())) {
                            continue;
                        }
                        processedIds.add(event.id());

                        insertStmt.setString(1, event.id());
                        insertStmt.setString(2, event.assetId());
                        insertStmt.setString(3, event.assetName());
                        insertStmt.setString(4, event.isin());
                        insertStmt.setString(5, event.eventType().name());
                        insertStmt.setString(6, event.eventDate().toString());
                        insertStmt.setString(7, event.units().toPlainString());
                        insertStmt.setString(8, event.pricePerUnit().toPlainString());
                        insertStmt.setString(9, event.grossAmount().toPlainString());
                        insertStmt.setString(10, event.sourceDocumentId());
                        insertStmt.setString(11, event.ingestedAt().toString());
                        insertStmt.executeUpdate();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to project events in DuckDB", e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("DuckDB transaction failure", e);
        }
    }

    public void saveNavHistoryBatchForHeldAssets(Map<String, BigDecimal> navMap, Set<String> heldIsins, LocalDate date) {
        if (navMap == null || navMap.isEmpty() || heldIsins == null || heldIsins.isEmpty()) return;

        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                String dateStr = date.toString();
                String sql = "INSERT INTO nav_history (asset_id, nav_date, nav) VALUES (?, ?, ?) ON CONFLICT (asset_id, nav_date) DO NOTHING";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (String isin : heldIsins) {
                        BigDecimal nav = navMap.get(isin);
                        if (nav != null) {
                            stmt.setString(1, isin);
                            stmt.setString(2, dateStr);
                            stmt.setDouble(3, nav.doubleValue());
                            stmt.executeUpdate();
                        }
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("DuckDB nav_history save failure: " + e.getMessage());
        }
    }

    public Map<String, List<Double>> getNavHistorySeries(Set<String> assetIds) {
        Map<String, NavHistorySeriesEntry> full = getNavHistorySeriesWithDates(assetIds);
        Map<String, List<Double>> result = new HashMap<>();
        for (Map.Entry<String, NavHistorySeriesEntry> entry : full.entrySet()) {
            result.put(entry.getKey(), entry.getValue().navs());
        }
        return result;
    }

    public Map<String, NavHistorySeriesEntry> getNavHistorySeriesWithDates(Set<String> assetIds) {
        Map<String, NavHistorySeriesEntry> result = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty()) return result;

        try (Connection conn = getConnection()) {
            String sql = "SELECT asset_id, nav_date, nav FROM nav_history WHERE asset_id = ? ORDER BY nav_date ASC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (String assetId : assetIds) {
                    stmt.setString(1, assetId);
                    List<Double> navs = new ArrayList<>();
                    List<String> dates = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            dates.add(rs.getString("nav_date"));
                            navs.add(rs.getDouble("nav"));
                        }
                    }
                    if (!navs.isEmpty()) {
                        result.put(assetId, new NavHistorySeriesEntry(navs, dates));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch NAV history series with dates from DuckDB: " + e.getMessage());
        }
        return result;
    }

    public static record NetWorthPoint(
        String date,
        double valuation,
        double invested
    ) {}

    public List<NetWorthPoint> getDailyNetWorthTrend() {
        List<NetWorthPoint> trend = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = """
                SELECT 
                    nh.nav_date,
                    SUM(CAST(pe.units AS DOUBLE) * nh.nav) AS total_valuation,
                    SUM(CAST(pe.gross_amount AS DOUBLE)) AS total_invested
                FROM nav_history nh
                JOIN projected_events pe ON nh.asset_id = pe.asset_id
                WHERE pe.event_date <= nh.nav_date
                  AND pe.event_type IN ('ACQUISITION', 'SIP_INSTALMENT')
                GROUP BY nh.nav_date
                ORDER BY nh.nav_date ASC
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String d = rs.getString("nav_date");
                    double val = rs.getDouble("total_valuation");
                    double inv = rs.getDouble("total_invested");
                    trend.add(new NetWorthPoint(d, val, inv));
                }
            }

            if (trend.size() < 10) {
                String fallbackSql = """
                    SELECT 
                        event_date,
                        SUM(CASE WHEN event_type IN ('ACQUISITION', 'SIP_INSTALMENT', 'BONUS') THEN CAST(gross_amount AS DOUBLE) ELSE -CAST(gross_amount AS DOUBLE) END) OVER (ORDER BY event_date ASC) AS cumulative_invested,
                        SUM(CASE WHEN event_type IN ('ACQUISITION', 'SIP_INSTALMENT', 'BONUS') THEN CAST(units AS DOUBLE) * CAST(price_per_unit AS DOUBLE) ELSE -CAST(units AS DOUBLE) * CAST(price_per_unit AS DOUBLE) END) OVER (ORDER BY event_date ASC) AS cumulative_valuation
                    FROM projected_events
                    GROUP BY event_date, event_type, gross_amount, units, price_per_unit
                    ORDER BY event_date ASC
                """;
                List<NetWorthPoint> fallbackTrend = new ArrayList<>();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(fallbackSql)) {
                    while (rs.next()) {
                        String d = rs.getString("event_date");
                        double cVal = rs.getDouble("cumulative_valuation");
                        double cInv = rs.getDouble("cumulative_invested");
                        fallbackTrend.add(new NetWorthPoint(d, cVal, cInv));
                    }
                }
                if (!fallbackTrend.isEmpty()) {
                    trend = fallbackTrend;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch daily net worth trend: " + e.getMessage());
        }
        return trend;
    }

    public List<Double> getHistoricalDailyReturns() {
        List<Double> returns = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = """
                WITH nav_dates AS (
                    SELECT DISTINCT nav_date
                    FROM nav_history
                    WHERE nav > 0
                ),
                unit_changes AS (
                    SELECT asset_id,
                           event_date,
                           SUM(CASE WHEN event_type IN ('ACQUISITION', 'SIP_INSTALMENT', 'BONUS') THEN CAST(units AS DOUBLE) ELSE -CAST(units AS DOUBLE) END) AS change_units
                    FROM projected_events
                    GROUP BY asset_id, event_date
                ),
                asset_daily_units AS (
                    SELECT n.nav_date,
                           u.asset_id,
                           SUM(u.change_units) AS units_held
                    FROM nav_dates n
                    JOIN unit_changes u ON u.event_date <= n.nav_date
                    GROUP BY n.nav_date, u.asset_id
                    HAVING units_held > 0
                ),
                fund_daily_returns AS (
                    SELECT asset_id,
                           nav_date,
                           nav,
                           LAG(nav_date) OVER (PARTITION BY asset_id ORDER BY nav_date ASC) AS prev_date,
                           LAG(nav) OVER (PARTITION BY asset_id ORDER BY nav_date ASC) AS prev_nav
                    FROM nav_history
                    WHERE nav > 0
                ),
                valid_weighted_returns AS (
                    SELECT f.asset_id,
                           f.nav_date,
                           f.prev_date,
                           du.units_held * f.prev_nav AS weight,
                           (f.nav - f.prev_nav) / f.prev_nav AS fund_ret
                    FROM fund_daily_returns f
                    JOIN asset_daily_units du ON f.asset_id = du.asset_id AND du.nav_date = f.prev_date
                    WHERE f.prev_nav > 0 AND f.prev_date IS NOT NULL AND du.units_held > 0
                ),
                daily_portfolio_returns AS (
                    SELECT nav_date,
                           prev_date,
                           SUM(weight * fund_ret) / SUM(weight) AS blended_ret,
                           COUNT(DISTINCT asset_id) AS active_assets
                    FROM valid_weighted_returns
                    GROUP BY nav_date, prev_date
                    HAVING SUM(weight) > 0
                    ORDER BY nav_date ASC
                )
                SELECT nav_date, prev_date, blended_ret, active_assets
                FROM daily_portfolio_returns;
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String dateStr = rs.getString("nav_date");
                    String prevDateStr = rs.getString("prev_date");
                    double ret = rs.getDouble("blended_ret");
                    java.time.LocalDate currDate = null;
                    java.time.LocalDate prevDate = null;
                    try {
                        currDate = java.time.LocalDate.parse(dateStr);
                        prevDate = java.time.LocalDate.parse(prevDateStr);
                    } catch (Exception ignored) {}

                    if (currDate != null && prevDate != null) {
                        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(prevDate, currDate);
                        if (daysBetween >= 1 && daysBetween <= 5) {
                            if (Math.abs(ret) < 0.08 * daysBetween) {
                                double dailyRet = Math.pow(1.0 + ret, 1.0 / daysBetween) - 1.0;
                                returns.add(dailyRet);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}


        System.out.println("Extracted " + returns.size() + " historical daily returns: min=" +
            (returns.isEmpty() ? "N/A" : returns.stream().min(Double::compare).get()) + ", max=" +
            (returns.isEmpty() ? "N/A" : returns.stream().max(Double::compare).get()) + ", avg=" +
            (returns.isEmpty() ? "N/A" : returns.stream().mapToDouble(Double::doubleValue).average().getAsDouble()));
        return returns;
    }
}
