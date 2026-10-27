package com.portfolioos.core.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TriggerHistoryRepositoryTest {

    @Test
    @DisplayName("F8 Migration: Guarded ALTER TABLE adds drawdown_tier to legacy schema cleanly")
    void testLegacySchemaMigrationAddsDrawdownTier(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("legacy_trigger.db").toFile();
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        // 1. Manually create the old schema without drawdown_tier
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE rebalance_trigger_history (" +
                "  plan_id TEXT PRIMARY KEY," +
                "  trigger_type TEXT NOT NULL," +
                "  reason_code TEXT NOT NULL," +
                "  fired_at TEXT NOT NULL," +
                "  has_sell_side INTEGER NOT NULL," +
                "  has_gold_buy INTEGER NOT NULL," +
                "  details_json TEXT" +
                ")"
            );
            stmt.execute(
                "INSERT INTO rebalance_trigger_history " +
                "(plan_id, trigger_type, reason_code, fired_at, has_sell_side, has_gold_buy, details_json) " +
                "VALUES ('LEGACY-PLAN-1', 'DRAWDOWN', 'DRAWDOWN_TIER_1', '" +
                LocalDateTime.now().minusDays(5).toString() + "', 1, 0, '{}')"
            );
        }

        // 2. Instantiate TriggerHistoryRepository on the legacy DB to trigger migration
        TriggerHistoryRepository repo = new TriggerHistoryRepository(dbFile.getAbsolutePath());

        // Legacy record should have default 0 for drawdown_tier
        int legacyMaxTier = repo.getMaxFiredDrawdownTierWithinDays(java.time.LocalDate.now(), 30);
        assertEquals(0, legacyMaxTier, "Legacy record before migration must default drawdown_tier to 0");

        // 3. Record a new execution with tier 2
        repo.recordExecution(
            "NEW-PLAN-2",
            "DRAWDOWN",
            "DRAWDOWN_TIER_2",
            LocalDateTime.now(),
            true,
            false,
            "{}",
            2
        );

        // 4. Verify updated tier query
        int updatedMaxTier = repo.getMaxFiredDrawdownTierWithinDays(java.time.LocalDate.now(), 30);
        assertEquals(2, updatedMaxTier, "After recording Tier 2, max tier within 30 days must be 2");
    }
}
