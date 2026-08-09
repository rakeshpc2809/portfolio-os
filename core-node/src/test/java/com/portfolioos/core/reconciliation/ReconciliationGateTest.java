package com.portfolioos.core.reconciliation;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationGateTest {

    @Test
    void testPerAssetReconciliationPassesOnExactMatch() {
        Lot lot1 = new Lot("lot_1", "asset_A", "Fund A", LocalDate.now(), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("1000"), false, BigDecimal.ZERO);
        Lot lot2 = new Lot("lot_2", "asset_B", "Fund B", LocalDate.now(), new BigDecimal("200"), new BigDecimal("200"), new BigDecimal("20"), new BigDecimal("4000"), false, BigDecimal.ZERO);

        FifoMatcher.FifoResult fifoResult = new FifoMatcher.FifoResult(List.of(lot1, lot2), List.of());
        Map<String, BigDecimal> declared = Map.of(
            "asset_A", new BigDecimal("100"),
            "asset_B", new BigDecimal("200")
        );

        ReconciliationGate.MultiAssetReconciliationResult res = ReconciliationGate.validateStatementPerAsset(fifoResult, declared);
        assertTrue(res.allMatched());
        assertEquals(2, res.assetResults().size());
    }

    @Test
    void testPerAssetReconciliationFailsOnMismatch() {
        Lot lot1 = new Lot("lot_1", "asset_A", "Fund A", LocalDate.now(), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("1000"), false, BigDecimal.ZERO);
        Lot lot2 = new Lot("lot_2", "asset_B", "Fund B", LocalDate.now(), new BigDecimal("200"), new BigDecimal("200"), new BigDecimal("20"), new BigDecimal("4000"), false, BigDecimal.ZERO);

        FifoMatcher.FifoResult fifoResult = new FifoMatcher.FifoResult(List.of(lot1, lot2), List.of());
        Map<String, BigDecimal> declared = Map.of(
            "asset_A", new BigDecimal("100"),
            "asset_B", new BigDecimal("150") // mismatch on asset B
        );

        ReconciliationGate.MultiAssetReconciliationResult res = ReconciliationGate.validateStatementPerAsset(fifoResult, declared);
        assertFalse(res.allMatched());
    }
}
