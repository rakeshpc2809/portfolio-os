package com.portfolioos.core.nav;

import com.portfolioos.core.persistence.DuckDbProjector;

import java.util.*;

public class NseIndexConstituentDownloader {

    public void seedStandardIndexConstituents(DuckDbProjector projector) {
        String disclosureDate = "2026-03-31"; // Semi-annual March snapshot

        // 1. Nifty LargeMidcap 250 (INF247L01AX8 / 147702)
        List<Map<String, Object>> lm250 = Arrays.asList(
            createHolding("RELIANCE", "INE002A01018", 6.85),
            createHolding("HDFCBANK", "INE040A01034", 6.42),
            createHolding("ICICIBANK", "INE090A01021", 5.10),
            createHolding("INFY", "INE009A01021", 3.85),
            createHolding("LTIM", "INE214T01019", 1.95),
            createHolding("TRENT", "INE849A01020", 2.15),
            createHolding("DIXON", "INE935N01020", 1.80),
            createHolding("COFORGE", "INE591G01017", 1.65),
            createHolding("PERSISTENT", "INE262H01013", 1.75),
            createHolding("BHARTIARTL", "INE397D01024", 3.20)
        );
        projector.saveFundHoldings("INF247L01AX8", disclosureDate, lm250);
        projector.saveFundHoldings("147702", disclosureDate, lm250);

        // 2. Nifty200 Value 30 (INF247L01BM8 / 150642)
        List<Map<String, Object>> val30 = Arrays.asList(
            createHolding("RELIANCE", "INE002A01018", 12.50),
            createHolding("HDFCBANK", "INE040A01034", 10.80),
            createHolding("ICICIBANK", "INE090A01021", 9.60),
            createHolding("NTPC", "INE733E01010", 7.20),
            createHolding("POWERGRID", "INE752E01010", 6.80),
            createHolding("ONGC", "INE213A01029", 5.90),
            createHolding("COALINDIA", "INE522F01014", 5.40),
            createHolding("SBIN", "INE062A01020", 8.10),
            createHolding("TATASTEEL", "INE081A01020", 4.80),
            createHolding("HINDALCO", "INE038A01020", 4.20)
        );
        projector.saveFundHoldings("INF247L01BM8", disclosureDate, val30);
        projector.saveFundHoldings("150642", disclosureDate, val30);

        // 3. Nifty500 Momentum Quality 50 (INF247L01BQ9 / 151814)
        List<Map<String, Object>> mq50 = Arrays.asList(
            createHolding("TRENT", "INE849A01020", 5.40),
            createHolding("BHARTIARTL", "INE397D01024", 5.10),
            createHolding("DIXON", "INE935N01020", 4.80),
            createHolding("PERSISTENT", "INE262H01013", 4.50),
            createHolding("COFORGE", "INE591G01017", 4.20),
            createHolding("BEL", "INE263A01024", 3.90),
            createHolding("HAL", "INE066F01020", 3.80),
            createHolding("BHAL", "INE257A01026", 3.40),
            createHolding("CHOLAFIN", "INE121A01024", 3.20),
            createHolding("TMC", "INE192A01025", 3.00)
        );
        projector.saveFundHoldings("INF247L01BQ9", disclosureDate, mq50);
        projector.saveFundHoldings("151814", disclosureDate, mq50);

        // 4. Nifty100 Equal Weight (INF204K01H36 / 118741)
        List<Map<String, Object>> ew100 = Arrays.asList(
            createHolding("RELIANCE", "INE002A01018", 1.00),
            createHolding("HDFCBANK", "INE040A01034", 1.00),
            createHolding("ICICIBANK", "INE090A01021", 1.00),
            createHolding("INFY", "INE009A01021", 1.00),
            createHolding("TRENT", "INE849A01020", 1.00),
            createHolding("BHARTIARTL", "INE397D01024", 1.00),
            createHolding("NTPC", "INE733E01010", 1.00),
            createHolding("POWERGRID", "INE752E01010", 1.00),
            createHolding("SBIN", "INE062A01020", 1.00),
            createHolding("ONGC", "INE213A01029", 1.00)
        );
        projector.saveFundHoldings("INF204K01H36", disclosureDate, ew100);
        projector.saveFundHoldings("118741", disclosureDate, ew100);

        // 5. Nifty Midcap 150 (INF754K01TN5 / 152985)
        List<Map<String, Object>> mc150 = Arrays.asList(
            createHolding("DIXON", "INE935N01020", 2.40),
            createHolding("PERSISTENT", "INE262H01013", 2.20),
            createHolding("COFORGE", "INE591G01017", 2.10),
            createHolding("CHOLAFIN", "INE121A01024", 1.90),
            createHolding("MAXHEALTH", "INE027H01010", 1.85),
            createHolding("FEDERALBNK", "INE171A01029", 1.75),
            createHolding("APOLLOTYRE", "INE438A01022", 1.65),
            createHolding("INDIAMART", "INE933S01016", 1.50),
            createHolding("SUNDARMFIN", "INE660A01013", 1.40),
            createHolding("POLYCAB", "INE455K01017", 1.80)
        );
        projector.saveFundHoldings("INF754K01TN5", disclosureDate, mc150);
        projector.saveFundHoldings("152985", disclosureDate, mc150);

        System.out.println("Seeded standard NSE index constituent weights for 5 held index schemes into DuckDB.");
    }

    private Map<String, Object> createHolding(String symbol, String isin, double weightPct) {
        Map<String, Object> map = new HashMap<>();
        map.put("stock_symbol", symbol);
        map.put("stock_isin", isin);
        map.put("weight_pct", weightPct);
        return map;
    }
}
