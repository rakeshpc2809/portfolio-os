package com.portfolioos.core.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class TriggerHistoryRepository {

    private final HikariDataSource dataSource;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public TriggerHistoryRepository() {
        this(System.getenv("SQLITE_PATH") != null && !System.getenv("SQLITE_PATH").isBlank()
             ? System.getenv("SQLITE_PATH") : "data/tax_ledger.db");
    }

    public TriggerHistoryRepository(String dbPath) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        String jdbcUrl;
        if (":memory:".equals(dbPath)) {
            jdbcUrl = "jdbc:sqlite::memory:";
        } else {
            File file = new File(dbPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("TriggerHistoryPool");

        this.dataSource = new HikariDataSource(config);
        initSchema();
    }

    public TriggerHistoryRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        initSchema();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS rebalance_trigger_history (" +
                "  plan_id TEXT PRIMARY KEY," +
                "  trigger_type TEXT NOT NULL," +
                "  reason_code TEXT NOT NULL," +
                "  fired_at TEXT NOT NULL," +
                "  has_sell_side INTEGER NOT NULL," +
                "  has_gold_buy INTEGER NOT NULL," +
                "  details_json TEXT" +
                ")"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize rebalance_trigger_history schema", e);
        }
    }

    public void recordExecution(
        String planId,
        String triggerType,
        String reasonCode,
        LocalDateTime firedAt,
        boolean hasSellSide,
        boolean hasGoldBuy,
        String detailsJson
    ) {
        String sql = "INSERT OR REPLACE INTO rebalance_trigger_history " +
                     "(plan_id, trigger_type, reason_code, fired_at, has_sell_side, has_gold_buy, details_json) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, planId);
            stmt.setString(2, triggerType);
            stmt.setString(3, reasonCode);
            stmt.setString(4, firedAt.format(ISO_FORMATTER));
            stmt.setInt(5, hasSellSide ? 1 : 0);
            stmt.setInt(6, hasGoldBuy ? 1 : 0);
            stmt.setString(7, detailsJson != null ? detailsJson : "");
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record trigger execution", e);
        }
    }

    public Optional<LocalDateTime> getLastSellSideFiringDate() {
        String sql = "SELECT MAX(fired_at) FROM rebalance_trigger_history WHERE has_sell_side = 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                String str = rs.getString(1);
                if (str != null && !str.isBlank()) {
                    return Optional.of(LocalDateTime.parse(str, ISO_FORMATTER));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query last sell-side firing date", e);
        }
    }

    public Optional<LocalDateTime> getLastGoldBuyDate() {
        String sql = "SELECT MAX(fired_at) FROM rebalance_trigger_history WHERE has_gold_buy = 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                String str = rs.getString(1);
                if (str != null && !str.isBlank()) {
                    return Optional.of(LocalDateTime.parse(str, ISO_FORMATTER));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query last Gold buy date", e);
        }
    }

    public int getRecordCount() {
        String sql = "SELECT COUNT(*) FROM rebalance_trigger_history";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get record count", e);
        }
    }

    public void clearAll() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM rebalance_trigger_history");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear trigger history", e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
