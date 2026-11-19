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

    @Test
    @DisplayName("F7: Baseline arbitrage storage lifecycle (get, set, clear)")
    void testActiveDrawdownArbitrageBaselineLifecycle(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("baseline_test.db").toFile();
        TriggerHistoryRepository repo = new TriggerHistoryRepository(dbFile.getAbsolutePath());

        org.junit.jupiter.api.Assertions.assertTrue(repo.getActiveDrawdownArbitrageBaseline().isEmpty(), "Initial baseline must be empty");

        java.math.BigDecimal baseline = new java.math.BigDecimal("300000.00");
        repo.setActiveDrawdownArbitrageBaseline(baseline);

        java.util.Optional<java.math.BigDecimal> retrieved = repo.getActiveDrawdownArbitrageBaseline();
        org.junit.jupiter.api.Assertions.assertTrue(retrieved.isPresent());
        assertEquals(0, baseline.compareTo(retrieved.get()));

        repo.clearActiveDrawdownArbitrageBaseline();
        org.junit.jupiter.api.Assertions.assertTrue(repo.getActiveDrawdownArbitrageBaseline().isEmpty(), "Cleared baseline must be empty");
    }

    @Test
    @DisplayName("ReadOnly View: Reads return real persisted values while writes are strictly NO-OPs")
    void testReadOnlyView(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("readonly_test.db").toFile();
        TriggerHistoryRepository repo = new TriggerHistoryRepository(dbFile.getAbsolutePath());

        // 1. Seed values in the primary repository
        repo.setActiveDrawdownArbitrageBaseline(new java.math.BigDecimal("500000.00"));
        repo.setNiftyTriWatermark(new java.math.BigDecimal("25000.00"));
        repo.recordExecution("PLAN-REAL", "SCHEDULED", "ANNUAL", LocalDateTime.now(), false, false, "{}", 0);

        assertEquals(1, repo.getRecordCount());
        assertEquals(0, new java.math.BigDecimal("500000.00").compareTo(repo.getActiveDrawdownArbitrageBaseline().orElseThrow()));
        assertEquals(0, new java.math.BigDecimal("25000.00").compareTo(repo.getNiftyTriWatermark().orElseThrow()));

        // 2. Get read-only view
        TriggerHistoryRepository readOnlyRepo = repo.asReadOnly();
        org.junit.jupiter.api.Assertions.assertTrue(readOnlyRepo.isReadOnly());

        // Reads must work identically
        assertEquals(1, readOnlyRepo.getRecordCount());
        assertEquals(0, new java.math.BigDecimal("500000.00").compareTo(readOnlyRepo.getActiveDrawdownArbitrageBaseline().orElseThrow()));
        assertEquals(0, new java.math.BigDecimal("25000.00").compareTo(readOnlyRepo.getNiftyTriWatermark().orElseThrow()));

        // Writes on read-only view must be NO-OPs
        readOnlyRepo.setActiveDrawdownArbitrageBaseline(new java.math.BigDecimal("999999.00"));
        readOnlyRepo.setNiftyTriWatermark(new java.math.BigDecimal("30000.00"));
        readOnlyRepo.recordExecution("PLAN-PREVIEW", "DRAWDOWN", "TEST", LocalDateTime.now(), true, false, "{}", 10);
        readOnlyRepo.clearActiveDrawdownArbitrageBaseline();

        // State in DB remains completely unchanged!
        assertEquals(1, repo.getRecordCount());
        assertEquals(0, new java.math.BigDecimal("500000.00").compareTo(repo.getActiveDrawdownArbitrageBaseline().orElseThrow()));
        assertEquals(0, new java.math.BigDecimal("25000.00").compareTo(repo.getNiftyTriWatermark().orElseThrow()));
    }
}
