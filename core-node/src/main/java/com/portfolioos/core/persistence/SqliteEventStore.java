package com.portfolioos.core.persistence;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.ports.EventStorePort;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SqliteEventStore implements EventStorePort {

    private final String dbPath;
    private final String jdbcUrl;
    private final String hmacSecret;
    private final Object lock = new Object();
    private Connection connection;

    public SqliteEventStore() {
        this(System.getenv("SQLITE_PATH") != null && !System.getenv("SQLITE_PATH").isBlank() 
             ? System.getenv("SQLITE_PATH") : "data/tax_ledger.db");
    }

    public SqliteEventStore(String dbPath) {
        this.dbPath = dbPath;
        String envSecret = System.getenv("LEDGER_HMAC_SECRET");
        if (envSecret == null || envSecret.isBlank()) {
            System.err.println("SECURITY WARNING: LEDGER_HMAC_SECRET environment variable is unset. Using default development secret.");
            this.hmacSecret = "fintracker-cachyos-default-key-2026";
        } else {
            this.hmacSecret = envSecret;
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        if (":memory:".equals(dbPath)) {
            jdbcUrl = "jdbc:sqlite::memory:";
        } else {
            File file = new File(dbPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        }

        try {
            connection = DriverManager.getConnection(jdbcUrl);
            initSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to SQLite database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
        }
        return connection;
    }

    private void initSchema() {
        synchronized (lock) {
            try (Statement stmt = getConnection().createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS tax_events (" +
                    "  id TEXT PRIMARY KEY," +
                    "  asset_id TEXT NOT NULL," +
                    "  asset_name TEXT NOT NULL," +
                    "  isin TEXT," +
                    "  event_type TEXT NOT NULL," +
                    "  event_date TEXT NOT NULL," +
                    "  units TEXT NOT NULL," +
                    "  price_per_unit TEXT NOT NULL," +
                    "  gross_amount TEXT NOT NULL," +
                    "  source_document_id TEXT NOT NULL," +
                    "  ingested_at TEXT NOT NULL," +
                    "  previous_hash TEXT NOT NULL," +
                    "  event_hash TEXT NOT NULL" +
                    ")"
                );
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize SQLite schema", e);
            }
        }
    }

    @Override
    public String getLatestEventHash() {
        synchronized (lock) {
            String sql = "SELECT event_hash FROM tax_events ORDER BY ingested_at DESC, id DESC LIMIT 1";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("event_hash");
                }
                return "GENESIS";
            } catch (SQLException e) {
                throw new RuntimeException("Failed to fetch latest event hash", e);
            }
        }
    }

    private String toCanonicalString(BigDecimal val) {
        return val.setScale(8, RoundingMode.HALF_UP).toPlainString();
    }

    private String computeHash(String prevHash, TaxEvent event) {
        String raw = prevHash + "|" + event.id() + "|" + event.assetId() + "|" + event.eventType().name() + "|" +
                     event.eventDate().toString() + "|" + toCanonicalString(event.units()) + "|" +
                     toCanonicalString(event.grossAmount()) + "|" + event.sourceDocumentId();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] bytes = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }

    @Override
    public String appendEvent(TaxEvent event) {
        List<String> hashes = appendEvents(List.of(event));
        return hashes.isEmpty() ? null : hashes.get(0);
    }

    @Override
    public List<String> appendEvents(List<TaxEvent> events) {
        if (events.isEmpty()) return List.of();

        synchronized (lock) {
            List<String> hashes = new ArrayList<>();
            Connection conn;
            boolean wasAutoCommit;
            try {
                conn = getConnection();
                wasAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
            } catch (SQLException e) {
                throw new RuntimeException("Database error in transaction config", e);
            }

            String checkSql = "SELECT event_hash FROM tax_events WHERE asset_id = ? AND event_type = ? AND event_date = ? AND units = ? AND gross_amount = ? AND source_document_id = ? LIMIT 1";
            String insertSql = "INSERT INTO tax_events (id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id, ingested_at, previous_hash, event_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                String prevHash = getLatestEventHash();
                if (prevHash == null) prevHash = "GENESIS";

                for (TaxEvent event : events) {
                    checkStmt.setString(1, event.assetId());
                    checkStmt.setString(2, event.eventType().name());
                    checkStmt.setString(3, event.eventDate().toString());
                    checkStmt.setString(4, event.units().toPlainString());
                    checkStmt.setString(5, event.grossAmount().toPlainString());
                    checkStmt.setString(6, event.sourceDocumentId());

                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            String existingHash = rs.getString("event_hash");
                            hashes.add(existingHash);
                            continue;
                        }
                    }

                    String eventHash = computeHash(prevHash, event);

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
                    insertStmt.setString(12, prevHash);
                    insertStmt.setString(13, eventHash);
                    insertStmt.executeUpdate();

                    hashes.add(eventHash);
                    prevHash = eventHash;
                }

                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // ignore rollback errors
                }
                throw new RuntimeException("Failed to commit transaction ledger", e);
            } finally {
                try {
                    conn.setAutoCommit(wasAutoCommit);
                } catch (SQLException e) {
                    // ignore autocommit reset errors
                }
            }
            return hashes;
        }
    }

    @Override
    public List<TaxEvent> getEventsForAsset(String assetId) {
        synchronized (lock) {
            List<TaxEvent> events = new ArrayList<>();
            String sql = "SELECT * FROM tax_events WHERE asset_id = ? ORDER BY event_date ASC, ingested_at ASC";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, assetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        events.add(mapResultSetToTaxEvent(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to fetch events for asset " + assetId, e);
            }
            return events;
        }
    }

    @Override
    public List<TaxEvent> getAllEvents() {
        synchronized (lock) {
            List<TaxEvent> events = new ArrayList<>();
            String sql = "SELECT * FROM tax_events ORDER BY event_date ASC, ingested_at ASC";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    events.add(mapResultSetToTaxEvent(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to fetch all events", e);
            }
            return events;
        }
    }

    @Override
    public boolean verifyLedgerIntegrity() {
        synchronized (lock) {
            String sql = "SELECT previous_hash, event_hash, id, asset_id, event_type, event_date, units, gross_amount, source_document_id FROM tax_events ORDER BY ingested_at ASC, id ASC";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                String expectedPrevHash = "GENESIS";
                while (rs.next()) {
                    String actualPrevHash = rs.getString("previous_hash");
                    String actualEventHash = rs.getString("event_hash");

                    if (!actualPrevHash.equals(expectedPrevHash)) {
                        return false;
                    }

                    TaxEvent mockEvent = new TaxEvent(
                        rs.getString("id"),
                        rs.getString("asset_id"),
                        "", // assetName not needed for hash
                        null,
                        EventType.valueOf(rs.getString("event_type")),
                        LocalDate.parse(rs.getString("event_date")),
                        rs.getBigDecimal("units"),
                        BigDecimal.ZERO,
                        rs.getBigDecimal("gross_amount"),
                        rs.getString("source_document_id"),
                        null
                    );

                    String recomputedHash = computeHash(expectedPrevHash, mockEvent);
                    if (!recomputedHash.equals(actualEventHash)) {
                        return false;
                    }
                    expectedPrevHash = actualEventHash;
                }
                return true;
            } catch (SQLException e) {
                throw new RuntimeException("Ledger integrity verification failed", e);
            }
        }
    }

    @Override
    public void clearAllEvents() {
        synchronized (lock) {
            try (Statement stmt = getConnection().createStatement()) {
                stmt.execute("DELETE FROM tax_events");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to clear ledger", e);
            }
        }
    }

    private TaxEvent mapResultSetToTaxEvent(ResultSet rs) throws SQLException {
        return new TaxEvent(
            rs.getString("id"),
            rs.getString("asset_id"),
            rs.getString("asset_name"),
            rs.getString("isin"),
            EventType.valueOf(rs.getString("event_type")),
            LocalDate.parse(rs.getString("event_date")),
            new BigDecimal(rs.getString("units")),
            new BigDecimal(rs.getString("price_per_unit")),
            new BigDecimal(rs.getString("gross_amount")),
            rs.getString("source_document_id"),
            Instant.parse(rs.getString("ingested_at"))
        );
    }
}
