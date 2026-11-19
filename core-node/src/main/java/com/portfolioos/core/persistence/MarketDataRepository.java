package com.portfolioos.core.persistence;

import com.zaxxer.hikari.HikariDataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

public class MarketDataRepository {

    private final HikariDataSource dataSource;
    private final java.util.concurrent.atomic.AtomicReference<List<NetWorthPoint>> cachedDailyTrend = new java.util.concurrent.atomic.AtomicReference<>(null);
    private final java.util.concurrent.atomic.AtomicLong lastTrendTime = new java.util.concurrent.atomic.AtomicLong(0L);

    public record NavHistorySeriesEntry(
        List<Double> navs,
        List<String> dates
    ) {}

    public record BenchmarkLevelMetrics(
        Double currentLevel,
        Double rollingHigh
    ) {}

    public record NetWorthPoint(
        String date,
        double valuation,
        double invested,
        double realNavValuation,
        boolean isEstimated
    ) {}

    public MarketDataRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        initSchema();
    }

    public void invalidateTrendCache() {
        cachedDailyTrend.set(null);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("MarketDataRepository initialized without DataSource");
        }
        return dataSource.getConnection();
    }

    private void initSchema() {
        if (dataSource == null) return;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS nav_history (" +
                "  asset_id TEXT NOT NULL," +
                "  nav_date TEXT NOT NULL," +
                "  nav REAL NOT NULL," +
                "  PRIMARY KEY (asset_id, nav_date)" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS benchmark_history (" +
                "  benchmark_id TEXT NOT NULL," +
                "  nav_date TEXT NOT NULL," +
                "  level REAL NOT NULL," +
                "  PRIMARY KEY (benchmark_id, nav_date)" +
                ")"
            );
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nav_history_date ON nav_history(nav_date)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_benchmark_history_date ON benchmark_history(nav_date)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize MarketDataRepository schema", e);
        }
    }

    public void saveLatestNavEntries(List<com.portfolioos.core.nav.AmfiNavSync.NavEntry> entries) {
        if (entries == null || entries.isEmpty() || dataSource == null) return;
        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String sql = "INSERT OR REPLACE INTO nav_history (asset_id, nav_date, nav) VALUES (?, ?, ?)";
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
                invalidateTrendCache();
            } catch (Exception e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("MarketDataRepository saveLatestNavEntries failure: " + e.getMessage());
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
            System.err.println("MarketDataRepository getLatestNavEntries failure: " + e.getMessage());
        }
        return result;
    }

    public void saveNavHistoryFullSeries(String assetId, Map<LocalDate, BigDecimal> series) {
        if (series == null || series.isEmpty() || dataSource == null) return;
        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String sql = "INSERT OR REPLACE INTO nav_history (asset_id, nav_date, nav) VALUES (?, ?, ?)";
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
                invalidateTrendCache();
            } catch (Exception e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("MarketDataRepository nav_history series save failure: " + e.getMessage());
        }
    }

    public Map<String, NavHistorySeriesEntry> getNavHistorySeriesWithDates(Set<String> assetIds) {
        Map<String, NavHistorySeriesEntry> result = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty() || dataSource == null) return result;

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
            System.err.println("Failed to fetch NAV history series with dates from SQLite: " + e.getMessage());
        }
        return result;
    }

    public Map<String, BigDecimal> get52WeekPeakNavs(Set<String> assetIds) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty() || dataSource == null) return result;
        String cutoffDate = LocalDate.now().minusDays(365).toString();
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
            System.err.println("Failed to fetch 52-week peak NAVs from SQLite: " + e.getMessage());
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
            System.err.println("Failed to fetch Jan 31 2018 FMV map from SQLite: " + e.getMessage());
        }
        return result;
    }

    public void saveBenchmarkLevels(String benchmarkId, Map<String, Double> dateToLevel) {
        if (dateToLevel == null || dateToLevel.isEmpty() || dataSource == null) return;
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
        if (benchmarkId == null || benchmarkId.isBlank() || dataSource == null) return false;
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

    public BenchmarkLevelMetrics getBenchmarkLevelMetrics(String benchmarkId) {
        if (dataSource == null) return null;
        String targetBenchmark = (benchmarkId != null && !benchmarkId.isBlank()) ? benchmarkId : "NIFTY_500_TRI";
        String sql = "WITH recent_bench AS (" +
                     "  SELECT nav_date, level " +
                     "  FROM benchmark_history " +
                     "  WHERE benchmark_id = ? AND level > 0" +
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
        return null;
    }

    public List<NetWorthPoint> getDailyNetWorthTrend() {
        if (dataSource == null) return Collections.emptyList();
        List<NetWorthPoint> cached = cachedDailyTrend.get();
        if (cached != null && !cached.isEmpty() && (System.currentTimeMillis() - lastTrendTime.get() < 300_000L)) {
            return cached;
        }

        List<NetWorthPoint> trend = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = """
                WITH min_event AS (
                    SELECT COALESCE(MIN(event_date), '2000-01-01') AS min_d FROM tax_events
                ),
                daily_dates AS (
                    SELECT DISTINCT nav_date FROM nav_history WHERE nav_date >= (SELECT min_d FROM min_event)
                    UNION
                    SELECT DISTINCT event_date AS nav_date FROM tax_events
                ),
                active_units_per_asset AS (
                    SELECT 
                        d.nav_date,
                        pe.asset_id,
                        SUM(CASE 
                                WHEN pe.event_type IN ('ACQUISITION', 'SIP_INSTALMENT', 'BONUS', 'DIVIDEND_REINVEST') THEN CAST(pe.units AS REAL)
                                WHEN pe.event_type IN ('DISPOSAL', 'SGB_MATURITY') THEN -CAST(pe.units AS REAL)
                                ELSE 0.0 
                            END) AS active_units,
                        (
                            SELECT nh.nav 
                            FROM nav_history nh 
                            WHERE nh.asset_id = pe.asset_id AND nh.nav_date <= d.nav_date 
                            ORDER BY nh.nav_date DESC 
                            LIMIT 1
                        ) AS market_nav,
                        AVG(CAST(pe.price_per_unit AS REAL)) AS cost_nav
                    FROM daily_dates d
                    JOIN tax_events pe ON pe.event_date <= d.nav_date
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
                                WHEN pe.event_type IN ('ACQUISITION', 'SIP_INSTALMENT', 'DIVIDEND_REINVEST') THEN CAST(pe.gross_amount AS REAL)
                                WHEN pe.event_type IN ('DISPOSAL', 'SGB_MATURITY') THEN -CAST(pe.gross_amount AS REAL)
                                ELSE 0.0 
                            END) AS total_invested
                    FROM daily_dates d
                    JOIN tax_events pe ON pe.event_date <= d.nav_date
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
            System.err.println("Failed to fetch daily net worth trend from SQLite: " + e.getMessage());
        }
        return trend;
    }
}
