package com.portfolioos.core.parser;

import com.portfolioos.core.persistence.DuckDbProjector;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PpfasHoldingsParser {

    public static final String PPFAS_ISIN = "INF879O01027";
    public static final String PPFAS_URL = "https://amc.ppfas.com/schemes/ppfas-flexi-cap-fund/portfolio-disclosure/monthly-portfolio.xlsx";

    public boolean parseAndIngest(DuckDbProjector projector, InputStream excelInputStream, String defaultAsOfDate) {
        try (Workbook workbook = new XSSFWorkbook(excelInputStream)) {
            Sheet sheet = workbook.getSheet("PPLTVF");
            if (sheet == null) {
                // Fallback to first sheet if PPLTVF not found by exact name
                sheet = workbook.getSheetAt(0);
            }

            List<Map<String, Object>> holdings = new ArrayList<>();
            double totalWeight = 0.0;
            double usWeight = 0.0;

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

            // Fallback column positions if headers weren't matched dynamically
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

                String market = "IN";
                if (isin.startsWith("US") || symbol.equalsIgnoreCase("ALPHABET") || symbol.equalsIgnoreCase("AMAZON") ||
                    symbol.equalsIgnoreCase("META") || symbol.equalsIgnoreCase("MICROSOFT") || symbol.equalsIgnoreCase("APPLE") ||
                    name.toUpperCase().contains("ALPHABET") || name.toUpperCase().contains("AMAZON") ||
                    name.toUpperCase().contains("META") || name.toUpperCase().contains("MICROSOFT")) {
                    market = "US";
                    usWeight += weightPct;
                }

                Map<String, Object> h = new HashMap<>();
                h.put("stock_symbol", symbol);
                h.put("stock_isin", isin);
                h.put("weight_pct", weightPct);
                h.put("market", market);

                holdings.add(h);
                totalWeight += weightPct;
            }

            System.out.println(String.format("PPFAS Parse Result: %d holdings extracted, total_weight=%.2f%%, us_weight=%.2f%%",
                holdings.size(), totalWeight, usWeight));

            // Weight-Sum Validation Self-Check (75% to 102%)
            if (totalWeight < 75.0 || totalWeight > 102.0) {
                System.err.println(String.format("WARNING: PPFAS weight sum validation failed: %.2f%% outside expected bounds [75.0%%, 102.0%%]", totalWeight));
            }

            // Overseas sleeve plausibility check (12% to 28%)
            if (usWeight < 5.0 || usWeight > 35.0) {
                System.err.println(String.format("WARNING: PPFAS US sleeve weight (%.2f%%) outside expected plausibility bounds [5.0%%, 35.0%%]", usWeight));
            }

            if (!holdings.isEmpty()) {
                projector.clearFundHoldings(PPFAS_ISIN);
                projector.saveFundHoldings(PPFAS_ISIN, defaultAsOfDate, "FACTSHEET_POI_PARSED", holdings);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to parse PPFAS Excel workbook: " + e.getMessage());
        }
        return false;
    }

    private String cleanSymbol(String name, String isin) {
        String u = name.toUpperCase();
        if (u.contains("HDFC BANK")) return "HDFCBANK";
        if (u.contains("ICICI BANK")) return "ICICIBANK";
        if (u.contains("BAJAJ HOLDINGS") || u.contains("BAJAJ FIN")) return "BAJFINANCE";
        if (u.contains("ITC ")) return "ITC";
        if (u.contains("POWER GRID")) return "POWERGRID";
        if (u.contains("COAL INDIA")) return "COALINDIA";
        if (u.contains("TATA CONSULTANCY") || u.contains("TCS")) return "TCS";
        if (u.contains("AXIS BANK")) return "AXISBANK";
        if (u.contains("MARUTI")) return "MARUTI";
        if (u.contains("HCL TECH")) return "HCLTECH";
        if (u.contains("TECH MAHINDRA")) return "TECHM";
        if (u.contains("LARSEN")) return "LT";
        if (u.contains("KOTAK")) return "KOTAKBANK";
        if (u.contains("NTPC")) return "NTPC";
        if (u.contains("TITAN")) return "TITAN";
        if (u.contains("CIPLA")) return "CIPLA";
        if (u.contains("SUN PHARMA")) return "SUNPHARMA";
        if (u.contains("DR REDDY")) return "DRREDDY";
        if (u.contains("HERO MOTOCORP")) return "HEROMOTOCO";
        if (u.contains("MAHINDRA & MAHINDRA") || u.contains("M&M")) return "M&M";
        if (u.contains("ULTRATECH")) return "ULTRACEMCO";
        if (u.contains("GRASIM")) return "GRASIM";
        if (u.contains("NESTLE")) return "NESTLEIND";
        if (u.contains("ASIAN PAINTS")) return "ASIANPAINT";
        if (u.contains("BRITANNIA")) return "BRITANNIA";
        if (u.contains("ALPHABET") || isin.equals("US02079K3059")) return "ALPHABET";
        if (u.contains("AMAZON") || isin.equals("US0231351067")) return "AMAZON";
        if (u.contains("META") || isin.equals("US30303M1027")) return "META";
        if (u.contains("MICROSOFT") || isin.equals("US5949181045")) return "MICROSOFT";

        if (name != null && !name.isBlank()) {
            return name.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        }
        return isin;
    }
}
