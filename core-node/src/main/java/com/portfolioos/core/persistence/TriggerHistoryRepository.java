package com.portfolioos.core.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.math.BigDecimal;
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
        this(com.portfolioos.core.config.DbPathResolver.resolveDatabasePath("data/tax_ledger.db", "SQLITE_PATH"));
    }

    public TriggerHistoryRepository(String dbPath) {
        String resolvedPath = com.portfolioos.core.config.DbPathResolver.resolveDatabasePath(dbPath, "SQLITE_PATH");
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        String jdbcUrl;
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
        config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA busy_timeout=5000;");
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
                "  details_json TEXT," +
                "  drawdown_tier INTEGER DEFAULT 0" +
                ")"
            );
            boolean hasColumn = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(rebalance_trigger_history)")) {
                while (rs.next()) {
                    String colName = rs.getString("name");
                    if ("drawdown_tier".equalsIgnoreCase(colName)) {
                        hasColumn = true;
                        break;
                    }
                }
            }
            if (!hasColumn) {
                stmt.execute("ALTER TABLE rebalance_trigger_history ADD COLUMN drawdown_tier INTEGER DEFAULT 0");
            }
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS active_drawdown_baseline (" +
                "  id INTEGER PRIMARY KEY CHECK (id = 1)," +
                "  baseline_value TEXT NOT NULL," +
                "  recorded_at TEXT NOT NULL" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS nifty_tri_watermark (" +
                "  id INTEGER PRIMARY KEY CHECK (id = 1)," +
                "  watermark_value TEXT NOT NULL," +
                "  recorded_at TEXT NOT NULL" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS scheduled_rebalance_history (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  run_year INTEGER NOT NULL," +
                "  run_month INTEGER NOT NULL," +
                "  executed_at TEXT NOT NULL," +
                "  UNIQUE (run_year, run_month)" +
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
        String detailsJson,
        int drawdownTier
    ) {
        String sql = "INSERT OR REPLACE INTO rebalance_trigger_history " +
                     "(plan_id, trigger_type, reason_code, fired_at, has_sell_side, has_gold_buy, details_json, drawdown_tier) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, planId);
            stmt.setString(2, triggerType);
            stmt.setString(3, reasonCode);
            stmt.setString(4, firedAt.format(ISO_FORMATTER));
            stmt.setInt(5, hasSellSide ? 1 : 0);
            stmt.setInt(6, hasGoldBuy ? 1 : 0);
            stmt.setString(7, detailsJson != null ? detailsJson : "");
            stmt.setInt(8, drawdownTier);
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

    public int getMaxFiredDrawdownTierWithinDays(java.time.LocalDate today, int days) {
        String cutoff = today.minusDays(days).atStartOfDay().format(ISO_FORMATTER);
        String sql = "SELECT COALESCE(MAX(drawdown_tier), 0) FROM rebalance_trigger_history " +
                     "WHERE trigger_type = 'DRAWDOWN' AND has_sell_side = 1 AND fired_at >= ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cutoff);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query max fired drawdown tier", e);
        }
        return 0;
    }

    public Optional<String> getExecutedPlanSnapshot(String planId) {
        String sql = "SELECT details_json FROM rebalance_trigger_history WHERE plan_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, planId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString(1);
                    return Optional.ofNullable(json);
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query executed plan snapshot", e);
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

    public Optional<BigDecimal> getActiveDrawdownArbitrageBaseline() {
        String sql = "SELECT baseline_value FROM active_drawdown_baseline WHERE id = 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                String val = rs.getString(1);
                if (val != null && !val.isBlank()) {
                    return Optional.of(new BigDecimal(val));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query active drawdown baseline", e);
        }
    }

    public void setActiveDrawdownArbitrageBaseline(BigDecimal baseline) {
        if (baseline == null) return;
        String sql = "INSERT OR REPLACE INTO active_drawdown_baseline (id, baseline_value, recorded_at) VALUES (1, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, baseline.toPlainString());
            stmt.setString(2, LocalDateTime.now().format(ISO_FORMATTER));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to persist active drawdown baseline", e);
        }
    }

    public void clearActiveDrawdownArbitrageBaseline() {
        String sql = "DELETE FROM active_drawdown_baseline WHERE id = 1";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear active drawdown baseline", e);
        }
    }

    public Optional<BigDecimal> getNiftyTriWatermark() {
        String sql = "SELECT watermark_value FROM nifty_tri_watermark WHERE id = 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                String val = rs.getString(1);
                if (val != null && !val.isBlank()) {
                    return Optional.of(new BigDecimal(val));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query Nifty TRI watermark", e);
        }
    }

    public void setNiftyTriWatermark(BigDecimal watermark) {
        if (watermark == null) return;
        String sql = "INSERT OR REPLACE INTO nifty_tri_watermark (id, watermark_value, recorded_at) VALUES (1, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, watermark.toPlainString());
            stmt.setString(2, LocalDateTime.now().format(ISO_FORMATTER));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to persist Nifty TRI watermark", e);
        }
    }

    public boolean hasScheduledRunExecutedForMonth(int year, int month) {
        String sql = "SELECT COUNT(*) FROM scheduled_rebalance_history WHERE run_year = ? AND run_month = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, year);
            stmt.setInt(2, month);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query scheduled rebalance history", e);
        }
    }

    public void recordScheduledRunExecution(int year, int month, LocalDateTime executedAt) {
        String sql = "INSERT OR REPLACE INTO scheduled_rebalance_history (run_year, run_month, executed_at) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, year);
            stmt.setInt(2, month);
            stmt.setString(3, (executedAt != null ? executedAt : LocalDateTime.now()).format(ISO_FORMATTER));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record scheduled rebalance run", e);
        }
    }

    public Optional<LocalDateTime> getLastScheduledRunDate() {
        String sql = "SELECT MAX(executed_at) FROM scheduled_rebalance_history";
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
            throw new RuntimeException("Failed to query last scheduled run date", e);
        }
    }

    public void clearAll() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM rebalance_trigger_history");
            stmt.execute("DELETE FROM active_drawdown_baseline");
            stmt.execute("DELETE FROM nifty_tri_watermark");
            stmt.execute("DELETE FROM scheduled_rebalance_history");
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
