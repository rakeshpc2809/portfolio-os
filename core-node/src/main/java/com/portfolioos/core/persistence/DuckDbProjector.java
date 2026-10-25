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

public class DuckDbProjector implements AutoCloseable {

    private final String dbPath;
    private final String jdbcUrl;
    private final HikariDataSource dataSource;
    private final java.util.concurrent.atomic.AtomicReference<List<NetWorthPoint>> cachedDailyTrend = new java.util.concurrent.atomic.AtomicReference<>(null);
    private final java.util.concurrent.atomic.AtomicLong lastTrendTime = new java.util.concurrent.atomic.AtomicLong(0L);

    public void invalidateTrendCache() {
        cachedDailyTrend.set(null);
    }

    public static record NavHistorySeriesEntry(
        List<Double> navs,
        List<String> dates
    ) {}

    public DuckDbProjector() {
        this(com.portfolioos.core.config.DbPathResolver.resolveDatabasePath("data/tax_ledger.duckdb", "DUCKDB_PATH"));
    }

    public static DuckDbProjector noOpForTesting() {
        return new DuckDbProjector(true);
    }

    protected DuckDbProjector(boolean testNoOp) {
        this.dbPath = ":memory:";
        this.jdbcUrl = "jdbc:duckdb:";
        this.dataSource = null;
    }

    public DuckDbProjector(String dbPath) {
        String resolvedPath = com.portfolioos.core.config.DbPathResolver.resolveDatabasePath(dbPath, "DUCKDB_PATH");
        this.dbPath = resolvedPath;
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("DuckDB JDBC driver not found", e);
        }

        if (":memory:".equals(resolvedPath)) {
            jdbcUrl = "jdbc:duckdb:";
        } else if (resolvedPath != null && resolvedPath.startsWith("jdbc:duckdb:")) {
            jdbcUrl = resolvedPath;
        } else {
            File file = new File(resolvedPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            jdbcUrl = "jdbc:duckdb:" + file.getAbsolutePath();
        }

        int duckDbThreads = 1;
        String envThreads = System.getenv("DUCKDB_THREADS");
        if (envThreads != null && !envThreads.isBlank()) {
            try {
                duckDbThreads = Integer.parseInt(envThreads.trim());
            } catch (NumberFormatException ignored) {}
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.duckdb.DuckDBDriver");
        config.setMaximumPoolSize(6);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        String memLimit = System.getenv("DUCKDB_MEMORY_LIMIT");
        if (memLimit == null || memLimit.isBlank()) {
            memLimit = "1024MB";
        }
        config.setConnectionInitSql("SET memory_limit = '" + memLimit.trim() + "'; SET threads = 4;");

        this.dataSource = new HikariDataSource(config);
        initReadSchema(duckDbThreads);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DuckDbProjector initialized in no-op testing mode; no DataSource available.");
        }
        return dataSource.getConnection();
    }

    private void initReadSchema(int duckDbThreads) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET threads = " + duckDbThreads + ";");
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

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS fund_holdings (" +
                "  fund_id VARCHAR NOT NULL," +
                "  stock_symbol VARCHAR NOT NULL," +
                "  stock_isin VARCHAR," +
                "  weight_pct DOUBLE NOT NULL," +
                "  disclosure_date VARCHAR NOT NULL," +
                "  market VARCHAR DEFAULT 'IN'," +
                "  source_type VARCHAR DEFAULT 'NSE_INDEX_CONSTITUENTS'," +
                "  PRIMARY KEY (fund_id, stock_symbol, disclosure_date)" +
                ")"
            );
            try {
                stmt.execute("ALTER TABLE fund_holdings ADD COLUMN IF NOT EXISTS market VARCHAR DEFAULT 'IN'");
                stmt.execute("ALTER TABLE fund_holdings ADD COLUMN IF NOT EXISTS source_type VARCHAR DEFAULT 'NSE_INDEX_CONSTITUENTS'");
            } catch (SQLException ignored) {}

            stmt.execute(
                "CREATE OR REPLACE VIEW open_lots AS " +
                "SELECT id, asset_id, asset_name, isin, event_date, " +
                "TRY_CAST(units AS DOUBLE) AS units, " +
                "TRY_CAST(price_per_unit AS DOUBLE) AS price_per_unit, " +
                "TRY_CAST(gross_amount AS DOUBLE) AS gross_amount " +
                "FROM projected_events " +
                "WHERE event_type IN ('ACQUISITION', 'SIP_INSTALMENT', 'BONUS')"
            );

            seedStandardConstituents();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DuckDB schema", e);
        }
    }

    public void seedStandardConstituents() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM fund_holdings")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Already populated
            }
        } catch (Exception ignored) {}

        try (java.io.InputStream is = getClass().getResourceAsStream("/data/fund_constituents.json")) {
            if (is == null) {
                java.io.File fallbackFile = new java.io.File("src/main/resources/data/fund_constituents.json");
                if (!fallbackFile.exists()) {
                    fallbackFile = new java.io.File("core-node/src/main/resources/data/fund_constituents.json");
                }
                if (!fallbackFile.exists()) {
                    return;
                }
                try (java.io.InputStream fis = new java.io.FileInputStream(fallbackFile)) {
                    loadAndInsertConstituents(fis);
                }
            } else {
                loadAndInsertConstituents(is);
            }
        } catch (Exception e) {
            System.err.println("Failed to seed fund constituents from JSON: " + e.getMessage());
        }
    }

    private void loadAndInsertConstituents(java.io.InputStream is) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<Map<String, Object>> rows = mapper.readValue(is, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        if (rows == null || rows.isEmpty()) return;

        String sql = "INSERT OR REPLACE INTO fund_holdings (fund_id, stock_symbol, stock_isin, weight_pct, disclosure_date, market, source_type) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int batchSize = 0;
            for (Map<String, Object> row : rows) {
                pstmt.setString(1, (String) row.get("fund_id"));
                pstmt.setString(2, (String) row.get("stock_symbol"));
                pstmt.setString(3, (String) row.get("stock_isin"));
                Object w = row.get("weight_pct");
                double weight = w instanceof Number num ? num.doubleValue() : Double.parseDouble(String.valueOf(w));
                pstmt.setDouble(4, weight);
                pstmt.setString(5, (String) row.get("disclosure_date"));
                pstmt.setString(6, (String) row.getOrDefault("market", "IN"));
                pstmt.setString(7, (String) row.getOrDefault("source_type", "NSE_INDEX_CONSTITUENTS"));
                pstmt.addBatch();
                batchSize++;
                if (batchSize % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            pstmt.executeBatch();
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

    public boolean hasBenchmarkData(String benchmarkId) {
        if (benchmarkId == null || benchmarkId.isBlank()) return false;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM benchmark_history WHERE benchmark_id = ? AND level > 0")) {
            pstmt.setString(1, benchmarkId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) >= 20;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> getAlignedPortfolioAndBenchmarkReturns(String benchmarkId) {
        List<Double> portfolioReturns = new ArrayList<>();
        List<Double> benchmarkReturns = new ArrayList<>();
        List<String> dates = new ArrayList<>();
        List<Double> portfolioCumulative = new ArrayList<>();
        List<Double> benchmarkCumulative = new ArrayList<>();
        double pCum = 100.0;
        double bCum = 100.0;
        String targetBenchmark = (benchmarkId != null && !benchmarkId.isBlank()) ? benchmarkId : "NIFTY_500_TRI";
        if (targetBenchmark.equalsIgnoreCase("NIFTY_50") || targetBenchmark.equalsIgnoreCase("NIFTY50")) {
            targetBenchmark = "NIFTY_50_TRI";
        } else if (targetBenchmark.equalsIgnoreCase("NIFTY_500") || targetBenchmark.equalsIgnoreCase("NIFTY500")) {
            targetBenchmark = "NIFTY_500_TRI";
        }
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
                pstmt.setString(1, targetBenchmark);
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
                                    dates.add(dateStr);
                                    pCum *= (1.0 + pDaily);
                                    bCum *= (1.0 + bDaily);
                                    portfolioCumulative.add(Math.round(pCum * 100.0) / 100.0);
                                    benchmarkCumulative.add(Math.round(bCum * 100.0) / 100.0);
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
        res.put("dates", dates);
        res.put("portfolio_cumulative", portfolioCumulative);
        res.put("benchmark_cumulative", benchmarkCumulative);
        return res;
    }

    public Map<String, Object> getAlignedFundAndBenchmarkReturns(String assetId, String benchmarkId) {
        List<Double> fundReturns = new ArrayList<>();
        List<Double> benchmarkReturns = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String targetBenchmark = (benchmarkId != null && !benchmarkId.isBlank()) ? benchmarkId : "NIFTY_500_TRI";
            try (PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM benchmark_history WHERE benchmark_id = ? AND level > 0")) {
                check.setString(1, targetBenchmark);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next() && rs.getInt(1) < 10) {
                        targetBenchmark = "NIFTY_500_TRI";
                    }
                }
            }

            String sql = """
                WITH fund_daily_returns AS (
                    SELECT nav_date, nav,
                           LAG(nav_date) OVER (ORDER BY nav_date ASC) AS prev_date,
                           LAG(nav) OVER (ORDER BY nav_date ASC) AS prev_nav
                    FROM nav_history
                    WHERE asset_id = ? AND nav > 0
                ),
                valid_fund_returns AS (
                    SELECT nav_date, prev_date,
                           (nav - prev_nav) / prev_nav AS fund_ret
                    FROM fund_daily_returns
                    WHERE prev_nav > 0 AND prev_date IS NOT NULL
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
                SELECT f.nav_date, f.prev_date, f.fund_ret, b.b_ret
                FROM valid_fund_returns f
                JOIN valid_benchmark_returns b ON f.nav_date = b.nav_date AND f.prev_date = b.prev_date
                ORDER BY f.nav_date ASC;
            """;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, assetId);
                pstmt.setString(2, targetBenchmark);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String dateStr = rs.getString("nav_date");
                        String prevDateStr = rs.getString("prev_date");
                        double fRet = rs.getDouble("fund_ret");
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
                                if (Math.abs(fRet) < 0.08 * daysBetween && Math.abs(bRet) < 0.08 * daysBetween) {
                                    double fDaily = Math.pow(1.0 + fRet, 1.0 / daysBetween) - 1.0;
                                    double bDaily = Math.pow(1.0 + bRet, 1.0 / daysBetween) - 1.0;
                                    fundReturns.add(fDaily);
                                    benchmarkReturns.add(bDaily);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching aligned fund benchmark returns: " + e.getMessage());
        }

        Map<String, Object> res = new HashMap<>();
        res.put("fund_returns", fundReturns);
        res.put("benchmark_returns", benchmarkReturns);
        return res;
    }

    public record BenchmarkLevelMetrics(Double currentLevel, Double rollingHigh) {}

    public BenchmarkLevelMetrics getBenchmarkLevelMetrics(String benchmarkId) {
        String targetBenchmark = (benchmarkId != null && !benchmarkId.isBlank()) ? benchmarkId : "NIFTY_500_TRI";
        String sql = "WITH recent_bench AS (" +
                     "  SELECT nav_date, level " +
                     "  FROM benchmark_history " +
                     "  WHERE benchmark_id = ? AND level > 0 " +
                     "  ORDER BY nav_date DESC LIMIT 252" +
                     ") " +
                     "SELECT " +
                     "  (SELECT level FROM recent_bench ORDER BY nav_date DESC LIMIT 1) AS current_level, " +
                     "  MAX(level) AS rolling_high " +
                     "FROM recent_bench";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, targetBenchmark);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    double cur = rs.getDouble("current_level");
                    double high = rs.getDouble("rolling_high");
                    if (cur > 0 && high > 0) {
                        return new BenchmarkLevelMetrics(cur, high);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch benchmark level metrics for " + targetBenchmark + ": " + e.getMessage());
        }

        // Fallback: Check core index fund (ICICI LargeMidcap 250 Index Fund) in nav_history for 252-day history
        String proxySql = "WITH recent_nav AS (" +
                          "  SELECT nav_date, nav " +
                          "  FROM nav_history " +
                          "  WHERE asset_id = 'INF109KC12U0' AND nav > 0 " +
                          "  ORDER BY nav_date DESC LIMIT 252" +
                          ") " +
                          "SELECT " +
                          "  (SELECT nav FROM recent_nav ORDER BY nav_date DESC LIMIT 1) AS current_level, " +
                          "  MAX(nav) AS rolling_high " +
                          "FROM recent_nav";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(proxySql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                double cur = rs.getDouble("current_level");
                double high = rs.getDouble("rolling_high");
                if (cur > 0 && high > 0) {
                    return new BenchmarkLevelMetrics(cur, high);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch proxy benchmark level metrics: " + e.getMessage());
        }

        return null;
    }

    public void checkpoint() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CHECKPOINT;");
        } catch (SQLException e) {
            System.err.println("DuckDB checkpoint error: " + e.getMessage());
        }
    }

    public synchronized void projectEvents(List<TaxEvent> events) {
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
                        insertStmt.addBatch();
                    }
                    insertStmt.executeBatch();
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

    public void saveNavHistoryFullSeries(String assetId, Map<LocalDate, BigDecimal> series) {
        if (series == null || series.isEmpty()) return;
        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String sql = "INSERT INTO nav_history (asset_id, nav_date, nav) VALUES (?, ?, ?) ON CONFLICT (asset_id, nav_date) DO UPDATE SET nav = EXCLUDED.nav";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (Map.Entry<LocalDate, BigDecimal> entry : series.entrySet()) {
                        stmt.setString(1, assetId);
                        stmt.setString(2, entry.getKey().toString());
                        stmt.setDouble(3, entry.getValue().doubleValue());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("DuckDB nav_history series save failure: " + e.getMessage());
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

    public Map<String, BigDecimal> get52WeekPeakNavs(Set<String> assetIds) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty() || dataSource == null) return result;
        String cutoffDate = java.time.LocalDate.now().minusDays(365).toString();
        String placeholders = String.join(",", Collections.nCopies(assetIds.size(), "?"));
        String sql = "SELECT asset_id, MAX(nav) AS peak_nav FROM nav_history " +
                     "WHERE asset_id IN (" + placeholders + ") AND nav_date >= ? AND nav > 0 " +
                     "GROUP BY asset_id";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String isin : assetIds) {
                stmt.setString(idx++, isin);
            }
            stmt.setString(idx, cutoffDate);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double peak = rs.getDouble("peak_nav");
                    if (!rs.wasNull() && peak > 0.0) {
                        result.put(rs.getString("asset_id"), BigDecimal.valueOf(peak));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch 52-week peak NAVs from DuckDB: " + e.getMessage());
        }
        return result;
    }

    public void saveLatestNavEntries(List<com.portfolioos.core.nav.AmfiNavSync.NavEntry> entries) {
        if (entries == null || entries.isEmpty() || dataSource == null) return;
        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String sql = "INSERT INTO nav_history (asset_id, nav_date, nav) VALUES (?, ?, ?) ON CONFLICT (asset_id, nav_date) DO UPDATE SET nav = EXCLUDED.nav";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (var entry : entries) {
                        if (entry.nav() == null || entry.date() == null) continue;
                        String dateStr = entry.date().toString();
                        double navVal = entry.nav().doubleValue();
                        if (entry.isin() != null && !entry.isin().isEmpty() && !"-".equals(entry.isin())) {
                            stmt.setString(1, entry.isin());
                            stmt.setString(2, dateStr);
                            stmt.setDouble(3, navVal);
                            stmt.addBatch();
                        }
                        if (entry.schemeCode() != null && !entry.schemeCode().isEmpty()) {
                            stmt.setString(1, entry.schemeCode());
                            stmt.setString(2, dateStr);
                            stmt.setDouble(3, navVal);
                            stmt.addBatch();
                        }
                    }
                    stmt.executeBatch();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("DuckDB saveLatestNavEntries failure: " + e.getMessage());
        }
    }

    public List<com.portfolioos.core.nav.AmfiNavSync.NavEntry> getLatestNavEntries() {
        List<com.portfolioos.core.nav.AmfiNavSync.NavEntry> result = new ArrayList<>();
        if (dataSource == null) return result;
        try (Connection conn = getConnection()) {
            String sql = "SELECT asset_id, nav_date, nav FROM (" +
                         "  SELECT asset_id, nav_date, nav, " +
                         "         ROW_NUMBER() OVER (PARTITION BY asset_id ORDER BY nav_date DESC) as rn " +
                         "  FROM nav_history " +
                         "  WHERE nav > 0" +
                         ") sub WHERE rn = 1";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String assetId = rs.getString("asset_id");
                    String navDateStr = rs.getString("nav_date");
                    double nav = rs.getDouble("nav");
                    LocalDate navDate = null;
                    if (navDateStr != null) {
                        try {
                            navDate = LocalDate.parse(navDateStr);
                        } catch (Exception ignored) {}
                    }
                    boolean isIsin = assetId != null && assetId.startsWith("INF");
                    result.add(new com.portfolioos.core.nav.AmfiNavSync.NavEntry(
                        isIsin ? null : assetId,
                        isIsin ? assetId : null,
                        assetId,
                        BigDecimal.valueOf(nav),
                        navDate
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("DuckDB getLatestNavEntries failure: " + e.getMessage());
        }
        return result;
    }

    public Map<String, BigDecimal> getJan2018FmvMap() {
        Map<String, BigDecimal> result = new HashMap<>();
        if (dataSource == null) return result;
        try (Connection conn = getConnection()) {
            String sql = "SELECT asset_id, nav FROM (" +
                         "  SELECT asset_id, nav, " +
                         "         ROW_NUMBER() OVER (PARTITION BY asset_id ORDER BY nav_date DESC) as rn " +
                         "  FROM nav_history " +
                         "  WHERE nav_date <= '2018-01-31' AND nav > 0" +
                         ") sub WHERE rn = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String assetId = rs.getString("asset_id");
                    double nav = rs.getDouble("nav");
                    if (assetId != null && !assetId.isBlank()) {
                        result.put(assetId, BigDecimal.valueOf(nav));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch Jan 31 2018 FMV map from DuckDB: " + e.getMessage());
        }
        return result;
    }

    public static record NetWorthPoint(
        String date,
        double valuation,
        double invested,
        double realNavValuation,
        boolean isEstimated
    ) {}

    public List<NetWorthPoint> getDailyNetWorthTrend() {
        List<NetWorthPoint> cached = cachedDailyTrend.get();
        if (cached != null && !cached.isEmpty() && (System.currentTimeMillis() - lastTrendTime.get() < 300_000L)) {
            return cached;
        }

        List<NetWorthPoint> trend = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = """
                WITH min_event AS (
                    SELECT COALESCE(MIN(event_date), '2000-01-01') AS min_d FROM projected_events
                ),
                daily_dates AS (
                    SELECT DISTINCT nav_date FROM nav_history WHERE nav_date >= (SELECT min_d FROM min_event)
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
                ),
                daily_invested AS (
                    SELECT 
                        d.nav_date,
                        SUM(CASE 
                                WHEN pe.event_type IN ('ACQUISITION', 'SIP_INSTALMENT') THEN CAST(pe.gross_amount AS DOUBLE)
                                WHEN pe.event_type = 'DISPOSAL' THEN -CAST(pe.gross_amount AS DOUBLE)
                                ELSE 0.0 
                            END) AS total_invested
                    FROM daily_dates d
                    JOIN projected_events pe ON pe.event_date <= d.nav_date
                    GROUP BY d.nav_date
                )
                SELECT 
                    v.nav_date,
                    v.total_valuation,
                    i.total_invested,
                    v.real_nav_valuation,
                    (v.real_nav_valuation < v.total_valuation - 0.01) AS is_estimated
                FROM daily_valuation v
                JOIN daily_invested i ON v.nav_date = i.nav_date
                ORDER BY v.nav_date ASC
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String d = rs.getString("nav_date");
                    double val = rs.getDouble("total_valuation");
                    double inv = rs.getDouble("total_invested");
                    double realVal = rs.getDouble("real_nav_valuation");
                    boolean est = rs.getBoolean("is_estimated");
                    trend.add(new NetWorthPoint(d, val, inv, realVal, est));
                }
            }
            if (!trend.isEmpty()) {
                cachedDailyTrend.set(trend);
                lastTrendTime.set(System.currentTimeMillis());
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch daily net worth trend from DuckDB: " + e.getMessage());
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

    public void clearFundHoldings(String fundId) {
        String sql = "DELETE FROM fund_holdings WHERE fund_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fundId);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void saveFundHoldings(String fundId, String disclosureDate, List<Map<String, Object>> holdings) {
        saveFundHoldings(fundId, disclosureDate, "NSE_INDEX_CONSTITUENTS", holdings);
    }

    public void saveFundHoldings(String fundId, String disclosureDate, String sourceType, List<Map<String, Object>> holdings) {
        if (holdings == null || holdings.isEmpty()) return;
        clearFundHoldings(fundId);
        String sql = "INSERT OR REPLACE INTO fund_holdings (fund_id, stock_symbol, stock_isin, weight_pct, disclosure_date, market, source_type) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Map<String, Object> h : holdings) {
                String symbol = (String) h.get("stock_symbol");
                String isin = (String) h.getOrDefault("stock_isin", "");
                double weight = ((Number) h.getOrDefault("weight_pct", 0.0)).doubleValue();
                String market = (String) h.getOrDefault("market", "IN");
                if (symbol != null && !symbol.isBlank() && weight > 0) {
                    pstmt.setString(1, fundId);
                    pstmt.setString(2, symbol);
                    pstmt.setString(3, isin);
                    pstmt.setDouble(4, weight);
                    pstmt.setString(5, disclosureDate);
                    pstmt.setString(6, market != null ? market : "IN");
                    pstmt.setString(7, sourceType != null ? sourceType : "NSE_INDEX_CONSTITUENTS");
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            System.err.println("Failed to save fund holdings for " + fundId + ": " + e.getMessage());
        }
    }

    public Map<String, Object> getPairwiseFundOverlap(String fundA, String fundB) {
        Map<String, Object> result = new HashMap<>();
        String metaSql = "SELECT " +
            "(SELECT MAX(disclosure_date) FROM fund_holdings WHERE fund_id = ?) AS date_a, " +
            "(SELECT MAX(disclosure_date) FROM fund_holdings WHERE fund_id = ?) AS date_b, " +
            "(SELECT MAX(source_type) FROM fund_holdings WHERE fund_id = ?) AS src_a, " +
            "(SELECT MAX(source_type) FROM fund_holdings WHERE fund_id = ?) AS src_b";

        String dateA = "";
        String dateB = "";
        String srcA = "NSE_INDEX_CONSTITUENTS";
        String srcB = "NSE_INDEX_CONSTITUENTS";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(metaSql)) {
            pstmt.setString(1, fundA);
            pstmt.setString(2, fundB);
            pstmt.setString(3, fundA);
            pstmt.setString(4, fundB);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    dateA = rs.getString("date_a") != null ? rs.getString("date_a") : "";
                    dateB = rs.getString("date_b") != null ? rs.getString("date_b") : "";
                    srcA = rs.getString("src_a") != null ? rs.getString("src_a") : "NSE_INDEX_CONSTITUENTS";
                    srcB = rs.getString("src_b") != null ? rs.getString("src_b") : "NSE_INDEX_CONSTITUENTS";
                }
            }
        } catch (Exception ignored) {}

        String sql =
            "WITH latest_a AS (SELECT MAX(disclosure_date) AS date_a FROM fund_holdings WHERE fund_id = ?), " +
            "latest_b AS (SELECT MAX(disclosure_date) AS date_b FROM fund_holdings WHERE fund_id = ?), " +
            "holdings_a AS (SELECT h.stock_symbol, h.weight_pct AS weight_a FROM fund_holdings h JOIN latest_a l ON h.disclosure_date = l.date_a WHERE h.fund_id = ? AND (h.market IS NULL OR h.market = 'IN')), " +
            "holdings_b AS (SELECT h.stock_symbol, h.weight_pct AS weight_b FROM fund_holdings h JOIN latest_b l ON h.disclosure_date = l.date_b WHERE h.fund_id = ? AND (h.market IS NULL OR h.market = 'IN')) " +
            "SELECT a.stock_symbol, a.weight_a, b.weight_b, LEAST(a.weight_a, b.weight_b) AS overlap_pct " +
            "FROM holdings_a a JOIN holdings_b b ON a.stock_symbol = b.stock_symbol";

        double totalOverlap = 0.0;
        List<Map<String, Object>> commonStocks = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fundA);
            pstmt.setString(2, fundB);
            pstmt.setString(3, fundA);
            pstmt.setString(4, fundB);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String symbol = rs.getString("stock_symbol");
                    double weightA = rs.getDouble("weight_a");
                    double weightB = rs.getDouble("weight_b");
                    double overlap = rs.getDouble("overlap_pct");

                    totalOverlap += overlap;
                    Map<String, Object> stock = new HashMap<>();
                    stock.put("stock_symbol", symbol);
                    stock.put("weight_a", Math.round(weightA * 100.0) / 100.0);
                    stock.put("weight_b", Math.round(weightB * 100.0) / 100.0);
                    stock.put("overlap_pct", Math.round(overlap * 100.0) / 100.0);
                    commonStocks.add(stock);
                }
            }
        } catch (Exception e) {
            System.err.println("Pairwise overlap calculation failed for " + fundA + " vs " + fundB + ": " + e.getMessage());
        }

        commonStocks.sort((x, y) -> Double.compare(((Number) y.get("overlap_pct")).doubleValue(), ((Number) x.get("overlap_pct")).doubleValue()));

        boolean isUnverified = "MANUAL_ESTIMATE_UNVERIFIED".equals(srcA) || "MANUAL_ESTIMATE_UNVERIFIED".equals(srcB);

        result.put("fund_a", fundA);
        result.put("fund_b", fundB);
        result.put("date_a", dateA);
        result.put("date_b", dateB);
        result.put("source_type_a", srcA);
        result.put("source_type_b", srcB);
        result.put("is_unverified_estimate", isUnverified);
        result.put("date_mismatch", !dateA.isEmpty() && !dateB.isEmpty() && !dateA.equals(dateB));
        result.put("overlap_percentage", Math.round(totalOverlap * 100.0) / 100.0);
        result.put("common_stock_count", commonStocks.size());
        result.put("common_stocks", commonStocks);
        return result;
    }

    public Map<String, Object> getPortfolioStockConcentrations(Map<String, Double> fundValuations, boolean includeUnverified) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> concentrations = new ArrayList<>();
        if (fundValuations == null || fundValuations.isEmpty()) {
            result.put("concentrations", concentrations);
            result.put("coverage_telemetry", Collections.emptyMap());
            return result;
        }

        Map<String, Double> stockRupeeMap = new HashMap<>();
        Map<String, String> stockSourceMap = new HashMap<>();
        double totalAuditedValuation = 0.0;
        double totalUnverifiedValuation = 0.0;
        double totalTrackedValuation = 0.0;

        for (Map.Entry<String, Double> entry : fundValuations.entrySet()) {
            String fundId = entry.getKey();
            double valuation = entry.getValue();

            String metaSql = "SELECT MAX(source_type) AS src FROM fund_holdings WHERE fund_id = ?";
            String srcType = "NSE_INDEX_CONSTITUENTS";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(metaSql)) {
                pstmt.setString(1, fundId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getString("src") != null) {
                        srcType = rs.getString("src");
                    }
                }
            } catch (Exception ignored) {}

            boolean isUnverified = "MANUAL_ESTIMATE_UNVERIFIED".equals(srcType);
            if (isUnverified) {
                totalUnverifiedValuation += valuation;
                if (!includeUnverified) {
                    continue; // Skip unverified funds in Audited-Only default mode
                }
            } else {
                totalAuditedValuation += valuation;
            }

            String sql = "WITH latest AS (SELECT MAX(disclosure_date) AS max_d FROM fund_holdings WHERE fund_id = ?) " +
                         "SELECT h.stock_symbol, h.weight_pct, h.source_type FROM fund_holdings h JOIN latest l ON h.disclosure_date = l.max_d WHERE h.fund_id = ?";

            boolean fundHasHoldings = false;
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, fundId);
                pstmt.setString(2, fundId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        fundHasHoldings = true;
                        String symbol = rs.getString("stock_symbol");
                        double weight = rs.getDouble("weight_pct");
                        double rupeeContrib = (weight / 100.0) * valuation;
                        stockRupeeMap.put(symbol, stockRupeeMap.getOrDefault(symbol, 0.0) + rupeeContrib);
                        if (isUnverified) {
                            stockSourceMap.put(symbol, "MANUAL_ESTIMATE_UNVERIFIED");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Concentration query failed for fund " + fundId + ": " + e.getMessage());
            }

            if (fundHasHoldings) {
                totalTrackedValuation += valuation;
            }
        }

        double totalEquityAum = totalAuditedValuation + totalUnverifiedValuation;
        double auditedCoveragePct = totalEquityAum > 0 ? (totalAuditedValuation / totalEquityAum) * 100.0 : 100.0;

        Map<String, Object> telemetry = new HashMap<>();
        telemetry.put("total_equity_aum", Math.round(totalEquityAum * 100.0) / 100.0);
        telemetry.put("audited_aum", Math.round(totalAuditedValuation * 100.0) / 100.0);
        telemetry.put("unverified_aum", Math.round(totalUnverifiedValuation * 100.0) / 100.0);
        telemetry.put("audited_coverage_pct", Math.round(auditedCoveragePct * 10.0) / 10.0);
        telemetry.put("include_unverified", includeUnverified);

        if (totalTrackedValuation > 0) {
            for (Map.Entry<String, Double> entry : stockRupeeMap.entrySet()) {
                String symbol = entry.getKey();
                double rupees = entry.getValue();
                double portfolioPct = (rupees / totalTrackedValuation) * 100.0;

                Map<String, Object> item = new HashMap<>();
                item.put("stock_symbol", symbol);
                item.put("rupee_exposure", Math.round(rupees));
                item.put("portfolio_percentage", Math.round(portfolioPct * 100.0) / 100.0);
                item.put("is_audited", !"MANUAL_ESTIMATE_UNVERIFIED".equals(stockSourceMap.get(symbol)));
                concentrations.add(item);
            }
        }

        concentrations.sort((x, y) -> Double.compare(((Number) y.get("rupee_exposure")).doubleValue(), ((Number) x.get("rupee_exposure")).doubleValue()));

        result.put("coverage_telemetry", telemetry);
        result.put("concentrations", concentrations.stream().limit(10).toList());
        return result;
    }

    public List<Map<String, Object>> getPortfolioStockConcentrations(Map<String, Double> fundValuations) {
        Map<String, Object> res = getPortfolioStockConcentrations(fundValuations, false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) res.getOrDefault("concentrations", Collections.emptyList());
        return list;
    }

    public List<Map<String, Object>> getMultiFundIntersectionAnalytics(List<String> fundIds) {
        List<Map<String, Object>> upsetCombinations = new ArrayList<>();
        if (fundIds == null || fundIds.isEmpty()) return upsetCombinations;

        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < fundIds.size(); i++) {
            if (i > 0) inClause.append(",");
            inClause.append("?");
        }

        String sql =
            "WITH latest AS ( " +
            "    SELECT fund_id, MAX(disclosure_date) AS max_d FROM fund_holdings WHERE fund_id IN (" + inClause + ") GROUP BY fund_id " +
            "), " +
            "aligned AS ( " +
            "    SELECT h.fund_id, h.stock_symbol, h.weight_pct " +
            "    FROM fund_holdings h JOIN latest l ON h.fund_id = l.fund_id AND h.disclosure_date = l.max_d " +
            ") " +
            "SELECT stock_symbol, ARRAY_AGG(fund_id ORDER BY fund_id) as fund_set, COUNT(fund_id) as set_size, MIN(weight_pct) as min_w, SUM(weight_pct) as sum_w " +
            "FROM aligned GROUP BY stock_symbol ORDER BY set_size DESC, stock_symbol";

        Map<String, List<Map<String, Object>>> groupedCombos = new HashMap<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < fundIds.size(); i++) {
                pstmt.setString(i + 1, fundIds.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String symbol = rs.getString("stock_symbol");
                    Object arrObj = rs.getObject("fund_set");
                    double minW = rs.getDouble("min_w");
                    double sumW = rs.getDouble("sum_w");

                    List<String> fList = new ArrayList<>();
                    if (arrObj instanceof java.sql.Array arr) {
                        Object inner = arr.getArray();
                        if (inner instanceof Object[] objArr) {
                            for (Object o : objArr) if (o != null) fList.add(o.toString());
                        }
                    } else if (arrObj instanceof List<?> list) {
                        for (Object o : list) if (o != null) fList.add(o.toString());
                    } else if (arrObj instanceof Object[] objArr) {
                        for (Object o : objArr) if (o != null) fList.add(o.toString());
                    } else if (arrObj != null) {
                        fList.add(arrObj.toString());
                    }

                    Collections.sort(fList);
                    String comboKey = String.join(",", fList);

                    Map<String, Object> stockItem = new HashMap<>();
                    stockItem.put("stock_symbol", symbol);
                    stockItem.put("min_weight", Math.round(minW * 100.0) / 100.0);
                    stockItem.put("total_weight", Math.round(sumW * 100.0) / 100.0);

                    groupedCombos.computeIfAbsent(comboKey, k -> new ArrayList<>()).add(stockItem);
                }
            }
        } catch (Exception e) {
            System.err.println("UpSet analytics query failed: " + e.getMessage());
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedCombos.entrySet()) {
            String comboKey = entry.getKey();
            List<Map<String, Object>> stocks = entry.getValue();
            List<String> participatingFunds = Arrays.asList(comboKey.split(","));

            double totalOverlapWeight = 0.0;
            for (Map<String, Object> s : stocks) {
                totalOverlapWeight += ((Number) s.get("min_weight")).doubleValue();
            }

            Map<String, Object> comboObj = new HashMap<>();
            comboObj.put("combination_key", comboKey);
            comboObj.put("participating_funds", participatingFunds);
            comboObj.put("stock_count", stocks.size());
            comboObj.put("total_overlap_weight", Math.round(totalOverlapWeight * 100.0) / 100.0);
            comboObj.put("stocks", stocks);

            upsetCombinations.add(comboObj);
        }

        upsetCombinations.sort((x, y) -> Integer.compare(((Number) y.get("stock_count")).intValue(), ((Number) x.get("stock_count")).intValue()));

        return upsetCombinations;
    }

    public Map<String, Object> getAllFundHoldingsDebug() {
        Map<String, Object> res = new HashMap<>();
        String sql = "SELECT fund_id, stock_symbol, stock_isin, weight_pct, disclosure_date, market FROM fund_holdings ORDER BY fund_id, stock_symbol";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("fund_id", rs.getString("fund_id"));
                m.put("stock_symbol", rs.getString("stock_symbol"));
                m.put("stock_isin", rs.getString("stock_isin"));
                m.put("weight_pct", rs.getDouble("weight_pct"));
                m.put("disclosure_date", rs.getString("disclosure_date"));
                m.put("market", rs.getString("market"));
                rows.add(m);
            }
        } catch (Exception e) {
            res.put("error", e.getMessage());
        }
        res.put("total_rows", rows.size());
        res.put("rows", rows);
        return res;
    }

    public void exportQueryToParquet(String sqlQuery, File destination) throws SQLException {
        if (dataSource == null) {
            return;
        }
        if (destination.getParentFile() != null) {
            destination.getParentFile().mkdirs();
        }
        String cleanPath = destination.getAbsolutePath().replace("'", "''");
        String copySql = "COPY (" + sqlQuery + ") TO '" + cleanPath + "' (FORMAT PARQUET, COMPRESSION ZSTD);";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(copySql);
        }
    }

    public void exportTableToParquet(String tableName, File destination) throws SQLException {
        exportQueryToParquet("SELECT * FROM " + tableName, destination);
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

