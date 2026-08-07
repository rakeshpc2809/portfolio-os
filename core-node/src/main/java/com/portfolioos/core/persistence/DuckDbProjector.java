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
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DuckDB schema", e);
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
            String sql = "SELECT asset_id, nav FROM nav_history WHERE nav > 0 ORDER BY asset_id ASC, nav_date ASC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                String prevAsset = null;
                double prevNav = -1.0;
                while (rs.next()) {
                    String currAsset = rs.getString("asset_id");
                    double currNav = rs.getDouble("nav");
                    if (prevAsset != null && prevAsset.equals(currAsset) && prevNav > 0) {
                        double ret = (currNav - prevNav) / prevNav;
                        if (Math.abs(ret) < 0.15) {
                            returns.add(ret);
                        }
                    }
                    prevAsset = currAsset;
                    prevNav = currNav;
                }
            }
        } catch (Exception ignored) {}

        if (returns.size() < 10) {
            List<NetWorthPoint> trend = getDailyNetWorthTrend();
            if (trend.size() >= 2) {
                for (int i = 1; i < trend.size(); i++) {
                    double prevVal = trend.get(i - 1).valuation();
                    double currVal = trend.get(i).valuation();
                    if (prevVal > 0) {
                        double ret = (currVal - prevVal) / prevVal;
                        if (Math.abs(ret) < 0.20) {
                            returns.add(ret);
                        }
                    }
                }
            }
        }
        return returns;
    }
}
