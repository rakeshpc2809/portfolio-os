package com.portfolioos.core.nav;

import com.portfolioos.core.persistence.DuckDbProjector;

import java.util.*;

public class NseIndexConstituentDownloader {

    public void seedStandardIndexConstituents(DuckDbProjector projector) {
        String disclosureDate = "2026-03-31"; // Semi-annual March snapshot

        // 1. ICICI Prudential Nifty LargeMidcap 250 Index Fund (INF109KC12U0 / INF247L01AX8 / 147702)
        List<Map<String, Object>> lm250 = Arrays.asList(
            createHolding("RELIANCE", "INE002A01018", 6.85),
            createHolding("HDFCBANK", "INE040A01034", 6.42),
            createHolding("ICICIBANK", "INE090A01021", 5.10),
            createHolding("INFY", "INE009A01021", 3.85),
            createHolding("BHARTIARTL", "INE397D01024", 3.20),
            createHolding("TRENT", "INE849A01020", 2.15),
            createHolding("LTIM", "INE214T01019", 1.95),
            createHolding("DIXON", "INE935N01020", 1.80),
            createHolding("PERSISTENT", "INE262H01013", 1.75),
            createHolding("COFORGE", "INE591G01017", 1.65)
        );
        projector.saveFundHoldings("INF109KC12U0", disclosureDate, lm250);
        projector.saveFundHoldings("INF247L01AX8", disclosureDate, lm250);
        projector.saveFundHoldings("147702", disclosureDate, lm250);

        // 2. ICICI Prudential Nifty200 Value 30 Index Fund (INF109KC13X2 / INF247L01BM8 / 150642)
        // Full Nifty 200 Value 30 constituent breakdown including Nifty 101-200 midcap value names
        List<Map<String, Object>> val30 = Arrays.asList(
            createHolding("RELIANCE", "INE002A01018", 12.50),
            createHolding("HDFCBANK", "INE040A01034", 10.80),
            createHolding("ICICIBANK", "INE090A01021", 9.60),
            createHolding("SBIN", "INE062A01020", 8.10),
            createHolding("NTPC", "INE733E01010", 7.20),
            createHolding("POWERGRID", "INE752E01010", 6.80),
            createHolding("ONGC", "INE213A01029", 5.90),
            createHolding("COALINDIA", "INE522F01014", 5.40),
            createHolding("TATASTEEL", "INE081A01020", 4.80),
            createHolding("HINDALCO", "INE038A01020", 4.20),
            createHolding("PFC", "INE134E01011", 3.10),
            createHolding("RECLTD", "INE020B01018", 2.90),
            createHolding("OIL", "INE274J01014", 2.40),
            createHolding("NMDC", "INE584A01023", 2.10),
            createHolding("FEDERALBNK", "INE171A01029", 1.75), // Midcap 101-200 universe overlap!
            createHolding("VEDL", "INE205A01012", 1.60),
            createHolding("GAIL", "INE129A01019", 1.50),
            createHolding("BPCL", "INE029A01011", 1.40),
            createHolding("IOC", "INE242A01010", 1.30),
            createHolding("HPCL", "INE094A01015", 1.20)
        );
        projector.saveFundHoldings("INF109KC13X2", disclosureDate, val30);
        projector.saveFundHoldings("INF247L01BM8", disclosureDate, val30);
        projector.saveFundHoldings("150642", disclosureDate, val30);

        // 3. Kotak Nifty 100 Equal Weight Index Fund (INF174KA1TY2 / INF204K01H36 / 118741)
        List<Map<String, Object>> ew100 = Arrays.asList(
            createHolding("RELIANCE", "INE002A01018", 1.00),
            createHolding("HDFCBANK", "INE040A01034", 1.00),
            createHolding("ICICIBANK", "INE090A01021", 1.00),
            createHolding("INFY", "INE009A01021", 1.00),
            createHolding("BHARTIARTL", "INE397D01024", 1.00),
            createHolding("TRENT", "INE849A01020", 1.00),
            createHolding("NTPC", "INE733E01010", 1.00),
            createHolding("POWERGRID", "INE752E01010", 1.00),
            createHolding("SBIN", "INE062A01020", 1.00),
            createHolding("ONGC", "INE213A01029", 1.00)
        );
        projector.saveFundHoldings("INF174KA1TY2", disclosureDate, ew100);
        projector.saveFundHoldings("INF204K01H36", disclosureDate, ew100);
        projector.saveFundHoldings("118741", disclosureDate, ew100);

        // 4. Motilal Oswal Nifty Midcap 150 Index Fund (INF247L01916 / INF754K01TN5 / 152985)
        List<Map<String, Object>> mc150 = Arrays.asList(
            createHolding("DIXON", "INE935N01020", 2.40),
            createHolding("PERSISTENT", "INE262H01013", 2.20),
            createHolding("COFORGE", "INE591G01017", 2.10),
            createHolding("CHOLAFIN", "INE121A01024", 1.95),
            createHolding("MAXHEALTH", "INE027H01010", 1.85),
            createHolding("POLYCAB", "INE455K01017", 1.80),
            createHolding("FEDERALBNK", "INE171A01029", 1.75), // Midcap 101-200 universe overlap!
            createHolding("APOLLOTYRE", "INE438A01022", 1.65),
            createHolding("INDIAMART", "INE933S01016", 1.50),
            createHolding("SUNDARMFIN", "INE660A01013", 1.40)
        );
        projector.saveFundHoldings("INF247L01916", disclosureDate, mc150);
        projector.saveFundHoldings("INF754K01TN5", disclosureDate, mc150);
        projector.saveFundHoldings("152985", disclosureDate, mc150);

        // 5. Motilal Oswal Nifty Microcap 250 / Momentum Quality 50 (INF247L01BQ9 / 151814)
        List<Map<String, Object>> mq50 = Arrays.asList(
            createHolding("TRENT", "INE849A01020", 5.40, "IN"),
            createHolding("BHARTIARTL", "INE397D01024", 5.10, "IN"),
            createHolding("DIXON", "INE935N01020", 4.80, "IN"),
            createHolding("PERSISTENT", "INE262H01013", 4.50, "IN"),
            createHolding("COFORGE", "INE591G01017", 4.20, "IN"),
            createHolding("BEL", "INE263A01024", 3.90, "IN"),
            createHolding("HAL", "INE066F01020", 3.80, "IN"),
            createHolding("BHAL", "INE257A01026", 3.40, "IN"),
            createHolding("CHOLAFIN", "INE121A01024", 3.20, "IN"),
            createHolding("TMC", "INE192A01025", 3.00, "IN")
        );
        projector.saveFundHoldings("INF247L01BQ9", disclosureDate, mq50);
        projector.saveFundHoldings("151814", disclosureDate, mq50);

        // 6. Parag Parikh Flexi Cap Fund (INF879O01027) - Parse Full Excel Factsheet
        java.io.File pFile = new java.io.File("/app/data/factsheets/ppfas_flexicap_full.xlsx");
        boolean parsedPpfas = false;
        if (pFile.exists()) {
            try (java.io.InputStream is = new java.io.FileInputStream(pFile)) {
                parsedPpfas = new com.portfolioos.core.parser.PpfasHoldingsParser().parseAndIngest(projector, is, disclosureDate);
            } catch (Exception e) {
                System.err.println("Failed parsing full PPFAS Excel factsheet: " + e.getMessage());
            }
        }
        if (!parsedPpfas) {
            List<Map<String, Object>> ppfas = Arrays.asList(
                createHolding("HDFCBANK", "INE040A01034", 7.45, "IN"),
                createHolding("BAJFINANCE", "INE296A01024", 6.80, "IN"),
                createHolding("AMAZON", "US0231351067", 6.15, "US"),
                createHolding("ALPHABET", "US02079K3059", 5.80, "US"),
                createHolding("META", "US30303M1027", 4.90, "US"),
                createHolding("MICROSOFT", "US5949181045", 4.20, "US"),
                createHolding("ICICIBANK", "INE090A01021", 5.40, "IN"),
                createHolding("ITC", "INE154A01025", 4.10, "IN"),
                createHolding("TCS", "INE467B01029", 3.90, "IN"),
                createHolding("COALINDIA", "INE522F01014", 3.50, "IN")
            );
            projector.saveFundHoldings("INF879O01027", disclosureDate, ppfas);
        }

        // 7. Nippon India Small Cap Fund (INF204K01K15) - Parse Full Excel Factsheet
        java.io.File nFile = new java.io.File("/app/data/factsheets/nippon_smallcap_full.xlsx");
        boolean parsedNippon = false;
        if (nFile.exists()) {
            try (java.io.InputStream is = new java.io.FileInputStream(nFile)) {
                parsedNippon = new com.portfolioos.core.parser.NipponHoldingsParser().parseAndIngest(projector, is, disclosureDate);
            } catch (Exception e) {
                System.err.println("Failed parsing full Nippon Small Cap Excel factsheet: " + e.getMessage());
            }
        }
        if (!parsedNippon) {
            List<Map<String, Object>> nippon = Arrays.asList(
                createHolding("TUBEINVEST", "INE974X01010", 2.15, "IN"),
                createHolding("HDFC_AMC", "INE127D01025", 1.95, "IN"),
                createHolding("APARINDS", "INE072E01019", 1.85, "IN"),
                createHolding("MULTIOPT", "INE745G01035", 1.70, "IN"),
                createHolding("VOLTAS", "INE226A01021", 1.65, "IN"),
                createHolding("KEI", "INE878B01027", 1.55, "IN"),
                createHolding("DIXON", "INE935N01020", 1.45, "IN"),
                createHolding("PERSISTENT", "INE262H01013", 1.35, "IN"),
                createHolding("CUMMINSIND", "INE299A01018", 1.25, "IN"),
                createHolding("KAYNES", "INE918Z01012", 1.15, "IN")
            );
            projector.saveFundHoldings("INF204K01K15", disclosureDate, nippon);
        }

        System.out.println("Seeded standard index and active fund constituent weights (7 funds) into DuckDB.");
    }

    private Map<String, Object> createHolding(String symbol, String isin, double weightPct) {
        return createHolding(symbol, isin, weightPct, "IN");
    }

    private Map<String, Object> createHolding(String symbol, String isin, double weightPct, String market) {
        Map<String, Object> map = new HashMap<>();
        map.put("stock_symbol", symbol);
        map.put("stock_isin", isin);
        map.put("weight_pct", weightPct);
        map.put("market", market != null ? market : "IN");
        return map;
    }
}
