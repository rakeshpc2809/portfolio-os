package com.portfolioos.core.persistence;

import com.portfolioos.core.model.TaxEvent;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

public class DuckDbProjector {

    private final String dbPath;
    private final String jdbcUrl;
    private Connection connection;

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

        try {
            connection = DriverManager.getConnection(jdbcUrl);
            initReadSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to DuckDB", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
        }
        return connection;
    }

    private void initReadSchema() {
        try (Statement stmt = getConnection().createStatement()) {
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

        try {
            Connection conn = getConnection();
            boolean wasAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                initReadSchema();

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

        try {
            Connection conn = getConnection();
            boolean wasAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                initReadSchema();

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
        Map<String, List<Double>> result = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty()) return result;

        try {
            Connection conn = getConnection();
            String sql = "SELECT asset_id, nav FROM nav_history WHERE asset_id = ? ORDER BY nav_date ASC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (String assetId : assetIds) {
                    stmt.setString(1, assetId);
                    List<Double> series = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            series.add(rs.getDouble("nav"));
                        }
                    }
                    if (!series.isEmpty()) {
                        result.put(assetId, series);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch NAV history series from DuckDB: " + e.getMessage());
        }
        return result;
    }
}
