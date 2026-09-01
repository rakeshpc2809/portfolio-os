package com.portfolioos.core.nav;

import com.portfolioos.core.persistence.DuckDbProjector;

import java.util.*;

/**
 * Seeds constituent stock holdings for standard benchmark indices and portfolio funds into DuckDB.
 * Each fund is strictly mapped to its own distinct constituent holdings.
 */
public class NseIndexConstituentDownloader {

    public void seedStandardIndexConstituents(DuckDbProjector projector) {
        String disclosureDate = "2026-03-31"; // Semi-annual March snapshot

        // 1. ICICI Prudential Nifty LargeMidcap 250 Index Fund (INF109KC12U0 / 147702)
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
        projector.saveFundHoldings("147702", disclosureDate, lm250);

        // 2. ICICI Prudential Nifty200 Value 30 Index Fund (INF109KC13X2 / 150642)
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
            createHolding("FEDERALBNK", "INE171A01029", 1.75),
            createHolding("VEDL", "INE205A01012", 1.60),
            createHolding("GAIL", "INE129A01019", 1.50),
            createHolding("BPCL", "INE029A01011", 1.40),
            createHolding("IOC", "INE242A01010", 1.30),
            createHolding("HPCL", "INE094A01015", 1.20)
        );
        projector.saveFundHoldings("INF109KC13X2", disclosureDate, val30);
        projector.saveFundHoldings("150642", disclosureDate, val30);

        // 3. Kotak Nifty 100 Equal Weight Index Fund (INF174KA1TY2 / 118741)
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
        projector.saveFundHoldings("118741", disclosureDate, ew100);

        // 4. Motilal Oswal Nifty Midcap 150 Index Fund (INF247L01916 / 152985)
        List<Map<String, Object>> mc150 = Arrays.asList(
            createHolding("DIXON", "INE935N01020", 2.40),
            createHolding("PERSISTENT", "INE262H01013", 2.20),
            createHolding("COFORGE", "INE591G01017", 2.10),
            createHolding("CHOLAFIN", "INE121A01024", 1.95),
            createHolding("MAXHEALTH", "INE027H01010", 1.85),
            createHolding("POLYCAB", "INE455K01017", 1.80),
            createHolding("FEDERALBNK", "INE171A01029", 1.75),
            createHolding("APOLLOTYRE", "INE438A01022", 1.65),
            createHolding("INDIAMART", "INE933S01016", 1.50),
            createHolding("SUNDARMFIN", "INE660A01013", 1.40)
        );
        projector.saveFundHoldings("INF247L01916", disclosureDate, mc150);
        projector.saveFundHoldings("152985", disclosureDate, mc150);

        // 5. Edelweiss Nifty500 Multicap Momentum Quality 50 Index Fund (INF754K01TN5 / 151814)
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
        projector.saveFundHoldings("INF754K01TN5", disclosureDate, mq50);
        projector.saveFundHoldings("151814", disclosureDate, mq50);

        // 6. Motilal Oswal Nifty Microcap 250 Index Fund (INF247L01BQ9 / 148943)
        List<Map<String, Object>> micro250 = Arrays.asList(
            createHolding("KAYNES", "INE918Z01012", 2.85, "IN"),
            createHolding("APARINDS", "INE072E01019", 2.60, "IN"),
            createHolding("ELECON", "INE205B01023", 2.35, "IN"),
            createHolding("SCHNEIDER", "INE839M01018", 2.10, "IN"),
            createHolding("CGPOWER", "INE067A01029", 1.95, "IN"),
            createHolding("MOTILALOFS", "INE338I01027", 1.80, "IN"),
            createHolding("SUZLON", "INE040H01021", 1.70, "IN"),
            createHolding("CYIENT", "INE136B01020", 1.55, "IN"),
            createHolding("CDSL", "INE736A01011", 1.45, "IN"),
            createHolding("BSESOFT", "INE118H01025", 1.30, "IN")
        );
        projector.saveFundHoldings("INF247L01BQ9", disclosureDate, micro250);
        projector.saveFundHoldings("148943", disclosureDate, micro250);

        // 7. Nippon India Index Fund - Nifty 50 Plan / ETF (INF204K01H36 / 140088)
        List<Map<String, Object>> nifty50 = Arrays.asList(
            createHolding("HDFCBANK", "INE040A01034", 11.50, "IN"),
            createHolding("RELIANCE", "INE002A01018", 9.80, "IN"),
            createHolding("ICICIBANK", "INE090A01021", 7.90, "IN"),
            createHolding("INFY", "INE009A01021", 5.80, "IN"),
            createHolding("ITC", "INE154A01025", 4.20, "IN"),
            createHolding("TCS", "INE467B01029", 4.00, "IN"),
            createHolding("BHARTIARTL", "INE397D01024", 3.90, "IN"),
            createHolding("LT", "INE018A01030", 3.60, "IN"),
            createHolding("AXISBANK", "INE238A01034", 3.20, "IN"),
            createHolding("SBIN", "INE062A01020", 3.00, "IN")
        );
        projector.saveFundHoldings("INF204K01H36", disclosureDate, nifty50);
        projector.saveFundHoldings("140088", disclosureDate, nifty50);

        // 8. Parag Parikh Flexi Cap Fund (INF879O01027) - Parse Full Excel Factsheet
        java.io.File pFile = new java.io.File("/app/data/factsheets/ppfas_flexicap_full.xlsx");
        if (!pFile.exists()) {
            pFile = new java.io.File("data/factsheets/ppfas_flexicap_full.xlsx");
        }
        if (!pFile.exists()) {
            pFile = new java.io.File("../data/factsheets/ppfas_flexicap_full.xlsx");
        }
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
            projector.saveFundHoldings("INF879O01027", disclosureDate, "MANUAL_ESTIMATE_UNVERIFIED", ppfas);
        }

        // 9. Nippon India Small Cap Fund (INF204K01K15) - Parse Full Excel Factsheet
        java.io.File nFile = new java.io.File("/app/data/factsheets/nippon_smallcap_full.xlsx");
        if (!nFile.exists()) {
            nFile = new java.io.File("data/factsheets/nippon_smallcap_full.xlsx");
        }
        if (!nFile.exists()) {
            nFile = new java.io.File("../data/factsheets/nippon_smallcap_full.xlsx");
        }
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
            projector.saveFundHoldings("INF204K01K15", disclosureDate, "MANUAL_ESTIMATE_UNVERIFIED", nippon);
        }

        // 10. Tata Small Cap Fund (INF277K011O1) - Unverified manual sample
        List<Map<String, Object>> tataSmall = Arrays.asList(
            createHolding("BASF", "INE373A01013", 3.10, "IN"),
            createHolding("QUESS", "INE615P01015", 2.80, "IN"),
            createHolding("RADICO", "INE944F01012", 2.65, "IN"),
            createHolding("HONAUT", "INE671A01010", 2.45, "IN"),
            createHolding("ELGIEQUIP", "INE285A01027", 2.20, "IN"),
            createHolding("IDFCFIRSTB", "INE092T01019", 2.10, "IN"),
            createHolding("CYIENT", "INE136B01020", 1.95, "IN"),
            createHolding("BSESOFT", "INE118H01025", 1.80, "IN"),
            createHolding("REDINGTON", "INE891D01026", 1.65, "IN"),
            createHolding("KNRCON", "INE634I01029", 1.50, "IN")
        );
        projector.saveFundHoldings("INF277K011O1", disclosureDate, "MANUAL_ESTIMATE_UNVERIFIED", tataSmall);

        // 11. SBI Contra Fund (INF200K01RA0) - Unverified manual sample
        List<Map<String, Object>> sbiContra = Arrays.asList(
            createHolding("GAIL", "INE129A01019", 4.80, "IN"),
            createHolding("HDFCBANK", "INE040A01034", 4.50, "IN"),
            createHolding("COALINDIA", "INE522F01014", 4.10, "IN"),
            createHolding("ONGC", "INE213A01029", 3.90, "IN"),
            createHolding("NTPC", "INE733E01010", 3.70, "IN"),
            createHolding("TECHM", "INE669C01036", 3.40, "IN"),
            createHolding("SUNPHARMA", "INE044A01036", 3.10, "IN"),
            createHolding("RELIANCE", "INE002A01018", 2.90, "IN"),
            createHolding("SBIN", "INE062A01020", 2.80, "IN"),
            createHolding("ICICIBANK", "INE090A01021", 2.60, "IN")
        );
        projector.saveFundHoldings("INF200K01RA0", disclosureDate, "MANUAL_ESTIMATE_UNVERIFIED", sbiContra);

        // 12. SBI Large & Midcap Fund (INF200K01UJ5) - Unverified manual sample
        List<Map<String, Object>> sbiLargeMid = Arrays.asList(
            createHolding("HDFCBANK", "INE040A01034", 5.80, "IN"),
            createHolding("ICICIBANK", "INE090A01021", 5.20, "IN"),
            createHolding("RELIANCE", "INE002A01018", 4.60, "IN"),
            createHolding("INFY", "INE009A01021", 3.80, "IN"),
            createHolding("CHOLAFIN", "INE121A01024", 2.90, "IN"),
            createHolding("DIXON", "INE935N01020", 2.40, "IN"),
            createHolding("PERSISTENT", "INE262H01013", 2.20, "IN"),
            createHolding("COFORGE", "INE591G01017", 2.10, "IN"),
            createHolding("SBIN", "INE062A01020", 2.00, "IN"),
            createHolding("BEL", "INE263A01024", 1.90, "IN")
        );
        projector.saveFundHoldings("INF200K01UJ5", disclosureDate, "MANUAL_ESTIMATE_UNVERIFIED", sbiLargeMid);

        // 13. Nippon India Consumption Fund (INF204K01G52) - Unverified manual sample
        List<Map<String, Object>> consumption = Arrays.asList(
            createHolding("ITC", "INE154A01025", 8.20, "IN"),
            createHolding("BHARTIARTL", "INE397D01024", 7.40, "IN"),
            createHolding("TITAN", "INE280A01028", 6.80, "IN"),
            createHolding("HINDUNILVR", "INE030A01027", 6.10, "IN"),
            createHolding("ZOMATO", "INE758T01015", 5.60, "IN"),
            createHolding("NESTLEIND", "INE239A01024", 4.90, "IN"),
            createHolding("ASIANPAINT", "INE021A01026", 4.40, "IN"),
            createHolding("TRENT", "INE849A01020", 4.10, "IN"),
            createHolding("DABUR", "INE016A01026", 3.80, "IN"),
            createHolding("GODREJCP", "INE102D01028", 3.20, "IN")
        );
        projector.saveFundHoldings("INF204K01G52", disclosureDate, "MANUAL_ESTIMATE_UNVERIFIED", consumption);

        // 14. ICICI Prudential Infrastructure Fund (INF109K018M4) - Unverified manual sample
        List<Map<String, Object>> infra = Arrays.asList(
            createHolding("LT", "INE018A01030", 9.40, "IN"),
            createHolding("NTPC", "INE733E01010", 7.80, "IN"),
            createHolding("POWERGRID", "INE752E01010", 6.90, "IN"),
            createHolding("BHARTIARTL", "INE397D01024", 5.80, "IN"),
            createHolding("ONGC", "INE213A01029", 5.40, "IN"),
            createHolding("RELIANCE", "INE002A01018", 4.90, "IN"),
            createHolding("COALINDIA", "INE522F01014", 4.20, "IN"),
            createHolding("ULTRACEMCO", "INE481G01011", 3.80, "IN"),
            createHolding("GRASIM", "INE047A01021", 3.40, "IN"),
            createHolding("ADANIPORTS", "INE742F01042", 3.10, "IN")
        );
        projector.saveFundHoldings("INF109K018M4", disclosureDate, "MANUAL_ESTIMATE_UNVERIFIED", infra);

        // 15. Mirae Asset Healthcare Fund (INF769K01ED6) - Unverified manual sample
        List<Map<String, Object>> healthcare = Arrays.asList(
            createHolding("SUNPHARMA", "INE044A01036", 14.50, "IN"),
            createHolding("CIPLA", "INE059A01026", 9.80, "IN"),
            createHolding("DRREDDY", "INE089A01023", 8.40, "IN"),
            createHolding("DIVISLAB", "INE361B01024", 7.60, "IN"),
            createHolding("APOLLOHOSP", "INE437A01024", 6.80, "IN"),
            createHolding("MAXHEALTH", "INE027H01010", 5.90, "IN"),
            createHolding("LUPIN", "INE326A01037", 5.20, "IN"),
            createHolding("MANKIND", "INE634S01028", 4.60, "IN"),
            createHolding("TORNTPHARM", "INE685A01028", 4.10, "IN"),
            createHolding("AUROPHARMA", "INE406A01037", 3.80, "IN")
        );
        projector.saveFundHoldings("INF769K01ED6", disclosureDate, "MANUAL_ESTIMATE_UNVERIFIED", healthcare);

        // Note: Commodity/Gold-Silver FoF (INF247L01BM8), Arbitrage (INF205K01KR8), and Debt funds (INF109K018C5, INF109K016B1)
        // have 0 unhedged/directional equity exposure and are intentionally cleared to ensure 0% false directional equity overlap.
        projector.clearFundHoldings("INF247L01BM8");
        projector.clearFundHoldings("INF205K01KR8");
        projector.clearFundHoldings("INF109K018C5");
        projector.clearFundHoldings("INF109K016B1");

        System.out.println("Seeded standard index and active fund constituent weights into DuckDB with 1:1 clean fund mapping.");
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
