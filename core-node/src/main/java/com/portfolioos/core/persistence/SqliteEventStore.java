package com.portfolioos.core.persistence;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.ports.EventStorePort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SqliteEventStore implements EventStorePort {

    private final String dbPath;
    private final String jdbcUrl;
    private final String hmacSecret;
    private final HikariDataSource dataSource;

    public SqliteEventStore() {
        this(com.portfolioos.core.config.DbPathResolver.resolveDatabasePath("data/tax_ledger.db", "SQLITE_PATH"));
    }

    public SqliteEventStore(String dbPath) {
        String resolvedPath = com.portfolioos.core.config.DbPathResolver.resolveDatabasePath(dbPath, "SQLITE_PATH");
        this.dbPath = resolvedPath;
        String envSecret = System.getenv("LEDGER_HMAC_SECRET");
        if (envSecret == null || envSecret.isBlank()) {
            throw new IllegalStateException("SECURITY CRITICAL: LEDGER_HMAC_SECRET environment variable is required and cannot be empty.");
        }
        this.hmacSecret = envSecret;

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        if (":memory:".equals(resolvedPath)) {
            jdbcUrl = "jdbc:sqlite::memory:";
        } else if (resolvedPath != null && resolvedPath.startsWith("jdbc:sqlite:")) {
            jdbcUrl = resolvedPath;
        } else {
            File file = new File(resolvedPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setIdleTimeout(30000);
        config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA busy_timeout=5000;");
        config.setPoolName("SqliteEventStorePool");

        this.dataSource = new HikariDataSource(config);
        initSchema();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS tax_events (" +
                "  id TEXT PRIMARY KEY," +
                "  asset_id TEXT NOT NULL," +
                "  asset_name TEXT NOT NULL," +
                "  isin TEXT," +
                "  folio_no TEXT," +
                "  event_type TEXT NOT NULL," +
                "  event_date TEXT NOT NULL," +
                "  units TEXT NOT NULL," +
                "  price_per_unit TEXT NOT NULL," +
                "  gross_amount TEXT NOT NULL," +
                "  source_document_id TEXT NOT NULL," +
                "  ingested_at TEXT NOT NULL," +
                "  previous_hash TEXT NOT NULL," +
                "  event_hash TEXT NOT NULL," +
                "  tx_fingerprint TEXT UNIQUE" +
                ")"
            );

            // Backward compatibility migrations for existing database
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(tax_events)")) {
                boolean hasFolio = false;
                boolean hasFingerprint = false;
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("folio_no".equalsIgnoreCase(col)) hasFolio = true;
                    if ("tx_fingerprint".equalsIgnoreCase(col)) hasFingerprint = true;
                }
                if (!hasFolio) {
                    stmt.execute("ALTER TABLE tax_events ADD COLUMN folio_no TEXT");
                }
                if (!hasFingerprint) {
                    stmt.execute("ALTER TABLE tax_events ADD COLUMN tx_fingerprint TEXT");
                    stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_tax_events_tx_fingerprint ON tax_events(tx_fingerprint)");
                }
            }

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS reconciliation_log (" +
                "  run_at TEXT NOT NULL," +
                "  isin TEXT NOT NULL," +
                "  folio TEXT NOT NULL," +
                "  cas_units TEXT NOT NULL," +
                "  ledger_units TEXT NOT NULL," +
                "  delta TEXT NOT NULL," +
                "  status TEXT NOT NULL" +
                ")"
            );

            stmt.execute("CREATE VIEW IF NOT EXISTS ledger_events AS SELECT * FROM tax_events;");

            backfillFingerprints(conn);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite schema", e);
        }
    }

    private void backfillFingerprints(Connection conn) {
        String countSql = "SELECT count(*) FROM tax_events WHERE tx_fingerprint IS NULL";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                String selectSql = "SELECT id, folio_no, isin, event_type, event_date, units, gross_amount FROM tax_events WHERE tx_fingerprint IS NULL ORDER BY event_date ASC, rowid ASC";
                String updateSql = "UPDATE tax_events SET tx_fingerprint = ? WHERE id = ?";
                try (PreparedStatement selStmt = conn.prepareStatement(selectSql);
                     PreparedStatement upStmt = conn.prepareStatement(updateSql);
                     ResultSet r = selStmt.executeQuery()) {

                    java.util.Map<String, Integer> dayOccurrences = new java.util.HashMap<>();
                    while (r.next()) {
                        String id = r.getString("id");
                        String folio = r.getString("folio_no");
                        String isin = r.getString("isin");
                        EventType type = EventType.valueOf(r.getString("event_type"));
                        LocalDate date = LocalDate.parse(r.getString("event_date"));
                        BigDecimal units = new BigDecimal(r.getString("units"));
                        BigDecimal gross = new BigDecimal(r.getString("gross_amount"));

                        String groupKey = (folio != null ? folio : "") + "|" + (isin != null ? isin : "") + "|" + date + "|" + type.name() + "|" + gross.toPlainString() + "|" + units.toPlainString();
                        int occurrence = dayOccurrences.getOrDefault(groupKey, 0);
                        dayOccurrences.put(groupKey, occurrence + 1);

                        String fp = computeTxFingerprint(folio, isin, date, type, gross, units, occurrence);
                        upStmt.setString(1, fp);
                        upStmt.setString(2, id);
                        upStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Notice: Fingerprint backfill: " + e.getMessage());
        }
    }

    public static String computeTxFingerprint(
        String folioNo,
        String isin,
        LocalDate txDate,
        EventType eventType,
        BigDecimal grossAmount,
        BigDecimal units,
        int occurrenceIdxOnDay
    ) {
        String folioStr = (folioNo != null && !folioNo.isBlank()) ? folioNo.trim() : "";
        String isinStr = (isin != null && !isin.isBlank()) ? isin.trim() : "";
        String dateStr = txDate != null ? txDate.toString() : "";
        String typeStr = eventType != null ? eventType.name().toUpperCase() : "";
        BigDecimal amt = grossAmount != null ? grossAmount : BigDecimal.ZERO;
        String amountPaise = amt.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).toPlainString();
        BigDecimal u = units != null ? units : BigDecimal.ZERO;
        String unitsScaled = u.multiply(new BigDecimal("1000")).setScale(0, RoundingMode.HALF_UP).toPlainString();
        String raw = folioStr + "|" + isinStr + "|" + dateStr + "|" + typeStr + "|" + amountPaise + "|" + unitsScaled + "|" + occurrenceIdxOnDay;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte byteVal : b) {
                sb.append(String.format("%02x", byteVal));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256 fingerprint", e);
        }
    }

    @Override
    public boolean isFingerprintPresent(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return false;
        String sql = "SELECT 1 FROM tax_events WHERE tx_fingerprint = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fingerprint);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check fingerprint", e);
        }
    }

    @Override
    public java.util.Set<String> getExistingFingerprints(java.util.Set<String> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) return java.util.Collections.emptySet();
        java.util.Set<String> found = new java.util.HashSet<>();
        String sql = "SELECT tx_fingerprint FROM tax_events WHERE tx_fingerprint = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String fp : fingerprints) {
                stmt.setString(1, fp);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        found.add(fp);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query existing fingerprints", e);
        }
        return found;
    }

    @Override
    public void recordReconciliationLog(List<ReconciliationRecord> records) {
        if (records == null || records.isEmpty()) return;
        String sql = "INSERT INTO reconciliation_log (run_at, isin, folio, cas_units, ledger_units, delta, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (ReconciliationRecord r : records) {
                    stmt.setString(1, r.runAt().toString());
                    stmt.setString(2, r.isin() != null ? r.isin() : "");
                    stmt.setString(3, r.folio() != null ? r.folio() : "");
                    stmt.setString(4, r.casUnits().setScale(3, RoundingMode.HALF_UP).toPlainString());
                    stmt.setString(5, r.ledgerUnits().setScale(3, RoundingMode.HALF_UP).toPlainString());
                    stmt.setString(6, r.delta().setScale(3, RoundingMode.HALF_UP).toPlainString());
                    stmt.setString(7, r.status());
                    stmt.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to record reconciliation log", e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error recording reconciliation log", e);
        }
    }

    @Override
    public List<ReconciliationRecord> getReconciliationLogs() {
        List<ReconciliationRecord> list = new ArrayList<>();
        String sql = "SELECT run_at, isin, folio, cas_units, ledger_units, delta, status FROM reconciliation_log ORDER BY rowid DESC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new ReconciliationRecord(
                    Instant.parse(rs.getString("run_at")),
                    rs.getString("isin"),
                    rs.getString("folio"),
                    new BigDecimal(rs.getString("cas_units")),
                    new BigDecimal(rs.getString("ledger_units")),
                    new BigDecimal(rs.getString("delta")),
                    rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch reconciliation logs", e);
        }
        return list;
    }

    @Override
    public String getLatestEventHash() {
        try (Connection conn = getConnection()) {
            return getLatestEventHash(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch latest event hash", e);
        }
    }

    private String getLatestEventHash(Connection conn) throws SQLException {
        String sql = "SELECT event_hash FROM tax_events ORDER BY rowid DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("event_hash");
            }
            return "GENESIS";
        }
    }

    private String toCanonicalString(BigDecimal val) {
        return val.setScale(8, RoundingMode.HALF_UP).toPlainString();
    }

    private String computeHash(String prevHash, TaxEvent event) {
        String isinStr = event.isin() != null ? event.isin() : "";
        String nameStr = event.assetName() != null ? event.assetName() : "";
        BigDecimal price = event.pricePerUnit() != null ? event.pricePerUnit() : BigDecimal.ZERO;
        String raw = prevHash + "|" + event.id() + "|" + event.assetId() + "|" + isinStr + "|" + nameStr + "|" +
                     event.eventType().name() + "|" + event.eventDate().toString() + "|" +
                     toCanonicalString(event.units()) + "|" + toCanonicalString(price) + "|" +
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
    public synchronized List<String> appendEvents(List<TaxEvent> events) {
        if (events == null || events.isEmpty()) return List.of();

        List<String> hashes = new ArrayList<>();
        String checkFpSql = "SELECT event_hash FROM tax_events WHERE tx_fingerprint = ? LIMIT 1";
        String checkFuzzySql = "SELECT event_hash FROM tax_events " +
                          "WHERE (asset_id = ? OR (isin IS NOT NULL AND isin != '' AND isin = ?)) " +
                          "  AND ( " +
                          "    (event_type IN ('ACQUISITION', 'SIP_INSTALMENT') AND ? IN ('ACQUISITION', 'SIP_INSTALMENT')) " +
                          "    OR (event_type = ? AND ? NOT IN ('ACQUISITION', 'SIP_INSTALMENT')) " +
                          "  ) " +
                          "  AND event_date = ? " +
                          "  AND abs(cast(units as real) - cast(? as real)) < 0.001 LIMIT 1";
        String insertSql = "INSERT INTO tax_events (id, asset_id, asset_name, isin, folio_no, event_type, event_date, units, price_per_unit, gross_amount, source_document_id, ingested_at, previous_hash, event_hash, tx_fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement checkFpStmt = conn.prepareStatement(checkFpSql);
                 PreparedStatement checkFuzzyStmt = conn.prepareStatement(checkFuzzySql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                // Fix #1: prevHash starts strictly from the latest committed event_hash in SQLite
                String prevHash = getLatestEventHash(conn);
                if (prevHash == null || prevHash.isBlank()) prevHash = "GENESIS";

                for (TaxEvent event : events) {
                    boolean alreadyExists = false;
                    String fp = event.txFingerprint();
                    if (fp != null && !fp.isBlank()) {
                        checkFpStmt.setString(1, fp);
                        try (ResultSet rs = checkFpStmt.executeQuery()) {
                            if (rs.next()) {
                                alreadyExists = true;
                            }
                        }
                    } else {
                        checkFuzzyStmt.setString(1, event.assetId());
                        checkFuzzyStmt.setString(2, event.isin() != null ? event.isin() : "");
                        checkFuzzyStmt.setString(3, event.eventType().name());
                        checkFuzzyStmt.setString(4, event.eventType().name());
                        checkFuzzyStmt.setString(5, event.eventType().name());
                        checkFuzzyStmt.setString(6, event.eventDate().toString());
                        checkFuzzyStmt.setString(7, event.units().toPlainString());
                        try (ResultSet rs = checkFuzzyStmt.executeQuery()) {
                            if (rs.next()) {
                                alreadyExists = true;
                            }
                        }
                    }

                    if (alreadyExists) {
                        // Fix #1: When duplicate is detected, skip insertion and DO NOT advance prevHash!
                        continue;
                    }

                    // Compute hash sequentially starting from the latest committed / newly inserted prevHash
                    String eventHash = computeHash(prevHash, event);

                    insertStmt.setString(1, event.id());
                    insertStmt.setString(2, event.assetId());
                    insertStmt.setString(3, event.assetName());
                    insertStmt.setString(4, event.isin());
                    insertStmt.setString(5, event.folioNo());
                    insertStmt.setString(6, event.eventType().name());
                    insertStmt.setString(7, event.eventDate().toString());
                    insertStmt.setString(8, event.units().toPlainString());
                    insertStmt.setString(9, event.pricePerUnit().toPlainString());
                    insertStmt.setString(10, event.grossAmount().toPlainString());
                    insertStmt.setString(11, event.sourceDocumentId());
                    insertStmt.setString(12, event.ingestedAt().toString());
                    insertStmt.setString(13, prevHash);
                    insertStmt.setString(14, eventHash);
                    insertStmt.setString(15, fp);
                    insertStmt.executeUpdate();

                    hashes.add(eventHash);
                    prevHash = eventHash; // advance sequentially only for non-duplicate inserted events
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to commit transaction ledger", e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error in transaction execution", e);
        }
        return hashes;
    }

    @Override
    public List<TaxEvent> getEventsForAsset(String assetId) {
        List<TaxEvent> events = new ArrayList<>();
        String sql = "SELECT * FROM tax_events WHERE asset_id = ? ORDER BY event_date ASC, ingested_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    @Override
    public List<TaxEvent> getAllEvents() {
        List<TaxEvent> events = new ArrayList<>();
        String sql = "SELECT * FROM tax_events ORDER BY event_date ASC, ingested_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                events.add(mapResultSetToTaxEvent(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch all events", e);
        }
        return events;
    }

    @Override
    public boolean verifyLedgerIntegrity() {
        String sql = "SELECT previous_hash, event_hash, id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id FROM tax_events ORDER BY rowid ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            String expectedPrevHash = "GENESIS";
            while (rs.next()) {
                String actualPrevHash = rs.getString("previous_hash");
                String actualEventHash = rs.getString("event_hash");

                if (!actualPrevHash.equals(expectedPrevHash)) {
                    return false;
                }

                String priceStr = rs.getString("price_per_unit");
                BigDecimal price = (priceStr != null && !priceStr.isBlank()) ? new BigDecimal(priceStr) : BigDecimal.ZERO;

                TaxEvent mockEvent = new TaxEvent(
                    rs.getString("id"),
                    rs.getString("asset_id"),
                    rs.getString("asset_name"),
                    rs.getString("isin"),
                    EventType.valueOf(rs.getString("event_type")),
                    LocalDate.parse(rs.getString("event_date")),
                    new BigDecimal(rs.getString("units")),
                    price,
                    new BigDecimal(rs.getString("gross_amount")),
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

    public void rehashLedgerChain() {
        String selectSql = "SELECT id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id FROM tax_events ORDER BY rowid ASC";
        String updateSql = "UPDATE tax_events SET previous_hash = ?, event_hash = ? WHERE id = ?";
        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                String expectedPrevHash = "GENESIS";
                while (rs.next()) {
                    String id = rs.getString("id");
                    String priceStr = rs.getString("price_per_unit");
                    BigDecimal price = (priceStr != null && !priceStr.isBlank()) ? new BigDecimal(priceStr) : BigDecimal.ZERO;

                    TaxEvent mockEvent = new TaxEvent(
                        id,
                        rs.getString("asset_id"),
                        rs.getString("asset_name"),
                        rs.getString("isin"),
                        EventType.valueOf(rs.getString("event_type")),
                        LocalDate.parse(rs.getString("event_date")),
                        new BigDecimal(rs.getString("units")),
                        price,
                        new BigDecimal(rs.getString("gross_amount")),
                        rs.getString("source_document_id"),
                        null
                    );

                    String newHash = computeHash(expectedPrevHash, mockEvent);
                    updateStmt.setString(1, expectedPrevHash);
                    updateStmt.setString(2, newHash);
                    updateStmt.setString(3, id);
                    updateStmt.executeUpdate();

                    expectedPrevHash = newHash;
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed during rehash transaction", e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to rehash ledger chain", e);
        }
    }

    @Override
    public void clearAllEvents() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM tax_events");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear ledger", e);
        }
    }

    @Override
    public synchronized void deleteEvents(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(eventIds.size(), "?"));
        String sql = "DELETE FROM tax_events WHERE id IN (" + placeholders + ")";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < eventIds.size(); i++) {
                stmt.setString(i + 1, eventIds.get(i));
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to rollback events from SQLite ledger", e);
        }
    }



    private TaxEvent mapResultSetToTaxEvent(ResultSet rs) throws SQLException {
        String folioNo = null;
        String txFingerprint = null;
        try {
            folioNo = rs.getString("folio_no");
        } catch (SQLException ignored) {}
        try {
            txFingerprint = rs.getString("tx_fingerprint");
        } catch (SQLException ignored) {}

        return new TaxEvent(
            rs.getString("id"),
            rs.getString("asset_id"),
            rs.getString("asset_name"),
            rs.getString("isin"),
            folioNo,
            EventType.valueOf(rs.getString("event_type")),
            LocalDate.parse(rs.getString("event_date")),
            new BigDecimal(rs.getString("units")),
            new BigDecimal(rs.getString("price_per_unit")),
            new BigDecimal(rs.getString("gross_amount")),
            rs.getString("source_document_id"),
            Instant.parse(rs.getString("ingested_at")),
            txFingerprint
        );
    }
}
