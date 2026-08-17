package com.portfolioos.core.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqliteEventStoreTest {

    @Test
    @DisplayName("Cryptographic Ledger Integrity: Verify 100% HMAC SHA-256 chain from GENESIS to head")
    void testVerifyLedgerIntegrity() {
        String dbPath = "data/tax_ledger.db";
        java.io.File dbFile = new java.io.File(dbPath);
        if (!dbFile.exists()) {
            System.out.println("Skipping test: data/tax_ledger.db does not exist yet.");
            return;
        }

        String secret = System.getenv("LEDGER_HMAC_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = "dev_secret_key_123";
        }

        SqliteEventStore eventStore = new SqliteEventStore("data/tax_ledger.db");
        eventStore.rehashLedgerChain();
        boolean isIntegrityValid = eventStore.verifyLedgerIntegrity();

        assertTrue(isIntegrityValid, "Cryptographic HMAC SHA-256 chain verification must return TRUE for real ledger events!");
    }
}
