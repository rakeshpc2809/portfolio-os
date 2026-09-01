package com.portfolioos.core.parser;

import com.portfolioos.core.persistence.DuckDbProjector;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.*;

public class NipponHoldingsParser {

    public static final String NIPPON_SMALLCAP_ISIN = "INF204K01K15";

    public boolean parseAndIngest(DuckDbProjector projector, InputStream excelInputStream, String defaultAsOfDate) {
        try (Workbook workbook = new XSSFWorkbook(excelInputStream)) {
            Sheet sheet = workbook.getSheet("SC");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            List<Map<String, Object>> holdings = new ArrayList<>();
            double totalWeight = 0.0;

            int isinCol = -1;
            int nameCol = -1;
            int weightCol = -1;

            for (Row row : sheet) {
                if (row == null) continue;
                for (Cell cell : row) {
                    if (cell == null || cell.getCellType() != CellType.STRING) continue;
                    String val = cell.getStringCellValue().trim().toUpperCase();
                    if (val.contains("ISIN")) isinCol = cell.getColumnIndex();
                    if (val.contains("NAME OF THE INSTRUMENT") || val.contains("COMPANY") || val.contains("SECURITY")) nameCol = cell.getColumnIndex();
                    if (val.contains("% TO NAV") || val.contains("% TO AUM") || val.contains("PERCENTAGE")) weightCol = cell.getColumnIndex();
                }
                if (isinCol >= 0 && weightCol >= 0) break;
            }

            if (isinCol == -1) isinCol = 1;
            if (nameCol == -1) nameCol = 2;
            if (weightCol == -1) weightCol = 4;

            for (Row row : sheet) {
                if (row == null) continue;

                Cell isinCell = row.getCell(isinCol);
                Cell nameCell = row.getCell(nameCol);
                Cell weightCell = row.getCell(weightCol);

                if (weightCell == null) continue;

                double weightPct = 0.0;
                if (weightCell.getCellType() == CellType.NUMERIC) {
                    weightPct = weightCell.getNumericCellValue();
                } else if (weightCell.getCellType() == CellType.STRING) {
                    try {
                        weightPct = Double.parseDouble(weightCell.getStringCellValue().replace("%", "").trim());
                    } catch (NumberFormatException ignored) {}
                }

                if (weightPct <= 0.01) continue;

                String isin = isinCell != null && isinCell.getCellType() == CellType.STRING ? isinCell.getStringCellValue().trim() : "";
                String name = nameCell != null && nameCell.getCellType() == CellType.STRING ? nameCell.getStringCellValue().trim() : "";

                if (name.toUpperCase().contains("TOTAL") || name.toUpperCase().contains("TREPS") || name.toUpperCase().contains("NET CURRENT ASSETS")) {
                    continue;
                }

                String symbol = cleanSymbol(name, isin);

                Map<String, Object> h = new HashMap<>();
                h.put("stock_symbol", symbol);
                h.put("stock_isin", isin);
                h.put("weight_pct", weightPct);
                h.put("market", "IN");

                holdings.add(h);
                totalWeight += weightPct;
            }

            System.out.println(String.format("Nippon Small Cap Parse Result: %d holdings extracted, total_weight=%.2f%%",
                holdings.size(), totalWeight));

            // Weight-Sum Validation Self-Check (30% to 102%)
            if (totalWeight < 30.0 || totalWeight > 102.0) {
                System.err.println(String.format("WARNING: Nippon Small Cap weight sum validation failed: %.2f%% outside expected bounds [30.0%%, 102.0%%]", totalWeight));
            }

            if (!holdings.isEmpty()) {
                projector.clearFundHoldings(NIPPON_SMALLCAP_ISIN);
                projector.saveFundHoldings(NIPPON_SMALLCAP_ISIN, defaultAsOfDate, "FACTSHEET_POI_PARSED", holdings);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to parse Nippon Small Cap Excel workbook: " + e.getMessage());
        }
        return false;
    }

    private String cleanSymbol(String name, String isin) {
        String u = name.toUpperCase();
        if (u.contains("TUBE INVEST")) return "TUBEINVEST";
        if (u.contains("HDFC ASSET") || u.contains("HDFC_AMC")) return "HDFC_AMC";
        if (u.contains("APAR IND")) return "APARINDS";
        if (u.contains("MULTI COMMODITY") || u.contains("MCX")) return "MULTIOPT";
        if (u.contains("VOLTAS")) return "VOLTAS";
        if (u.contains("KEI IND")) return "KEI";
        if (u.contains("DIXON")) return "DIXON";
        if (u.contains("PERSISTENT")) return "PERSISTENT";
        if (u.contains("CUMMINS")) return "CUMMINSIND";
        if (u.contains("KAYNES")) return "KAYNES";
        if (u.contains("CARBORUNDUM")) return "CARBORUN";
        if (u.contains("BHARAT DYNAMICS")) return "BDL";
        if (u.contains("ELGI")) return "ELGIEQUIP";
        if (u.contains("CHOLAMANDALAM")) return "CHOLAFIN";
        if (u.contains("KIRLOSKAR")) return "KIRLOSENG";
        if (u.contains("TIMKEN")) return "TIMKEN";
        if (u.contains("CENTURY TEXT")) return "CENTURYTEX";
        if (u.contains("TECHNOCRAFT")) return "TIIL";
        if (u.contains("JYOTHY")) return "JYOTHYLAB";
        if (u.contains("GRINDWELL")) return "GRINDWELL";
        if (u.contains("CREDITACCESS")) return "CREDITACC";
        if (u.contains("EQUITAS")) return "EQUITASBNK";
        if (u.contains("CITY UNION")) return "CUB";
        if (u.contains("KARUR VYSYA")) return "KVB";
        if (u.contains("UJJIVAN")) return "UJJIVANSFB";
        if (u.contains("CAN FIN")) return "CANFINHOME";
        if (u.contains("HOME FIRST")) return "HOMEFIRST";
        if (u.contains("AAVAS")) return "AAVAS";
        if (u.contains("BALRAMPUR")) return "BALRAMCHIN";
        if (u.contains("TRIVENI")) return "TRIVENI";
        if (u.contains("PARRY")) return "EIDPARRY";
        if (u.contains("DCM SHRIRAM")) return "DCMSHRIRAM";
        if (u.contains("PRAJ")) return "PRAJIND";
        if (u.contains("CONCORD")) return "CONCORD";
        if (u.contains("BLUE JET")) return "BLUEJET";
        if (u.contains("JUPITER")) return "JUPITERLIFE";
        if (u.contains("INNOVA")) return "INNOVA";

        if (name != null && !name.isBlank()) {
            return name.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        }
        return isin;
    }
}
