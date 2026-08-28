package com.portfolioos.core.reporting;

import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.service.SimulationService;
import com.portfolioos.core.service.TaxOptimizationService;
import com.portfolioos.core.rules.TaxRulesConfig;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class LiveDbLotTest {
    @Test
    public void testLiveLot() {
        // b82b7ed1, Edelweiss Nifty500 (INF754K01TN5), Date: 2025-03-25, Units: 872.983, Price: 8.3766, Cost: 7312.63
        // Simulate a sale at NAV 10.0 on 2026-08-22
        Lot buyLot = new Lot("b82b7ed1", "INF754K01TN5", "Edelweiss Nifty500 Multicap", LocalDate.parse("2025-03-25"), new BigDecimal("872.983"), new BigDecimal("872.983"), new BigDecimal("8.3766"), new BigDecimal("7312.63"), false, BigDecimal.ZERO);
        Lot sellLot = new Lot("sell-1", "INF754K01TN5", "Edelweiss Nifty500 Multicap", LocalDate.parse("2026-08-22"), new BigDecimal("-872.983"), new BigDecimal("-872.983"), new BigDecimal("10.0000"), BigDecimal.ZERO, false, BigDecimal.ZERO);
        
        MatchedLot matched = new MatchedLot("match-1", "sell-1", "b82b7ed1", "INF754K01TN5", LocalDate.parse("2025-03-25"), LocalDate.parse("2026-08-22"), new BigDecimal("872.983"), new BigDecimal("7312.63"), new BigDecimal("8729.83"), new BigDecimal("1417.20"), 515L, com.portfolioos.core.model.TaxTerm.LONG_TERM, com.portfolioos.core.model.AssetCategory.EQUITY);
        
        Map<String, String> exported = Itr2CsvExporter.exportItr2ScheduleCg(List.of(matched), "2026-2027", Map.of(), Map.of());
        System.out.println("---- ITR2 CSV EXPORT ----");
        System.out.println(exported.get("Schedule_112A.csv"));
        System.out.println("-------------------------");
    }
}
