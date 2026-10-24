"""Statutory Monthly AMC Portfolio Disclosure Parser.

Parses monthly mutual fund statutory portfolio disclosure files (XLSX, XLS, CSV)
published by AMCs pursuant to SEBI regulations. Extracts holdings with ISIN,
instrument name, sector, and % weight to NAV.
"""

import os
import re
from typing import Dict, List, Optional
import pandas as pd


def parse_amc_portfolio_disclosure(file_path: str, fund_id: Optional[str] = None) -> List[Dict[str, any]]:
    """Parses an AMC disclosure file and returns a list of normalized holding dicts.

    Each dict contains:
    - stock_symbol: str
    - stock_isin: str
    - weight_pct: float
    - market: str ('IN' or 'US')
    - source_type: 'FACTSHEET_POI_PARSED'
    """
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Disclosure file not found: {file_path}")

    ext = os.path.splitext(file_path)[1].lower()
    if ext in (".xlsx", ".xls"):
        # Read with openpyxl engine
        df = pd.read_excel(file_path, engine="openpyxl")
    elif ext == ".csv":
        df = pd.read_csv(file_path)
    else:
        raise ValueError(f"Unsupported file format: {ext}")

    # Find the header row if the first row is metadata
    isin_col = None
    name_col = None
    weight_col = None

    for col in df.columns:
        c_str = str(col).strip().upper()
        if "ISIN" in c_str:
            isin_col = col
        elif any(k in c_str for k in ("NAME OF THE INSTRUMENT", "COMPANY NAME", "INSTRUMENT NAME", "SECURITY NAME")):
            name_col = col
        elif any(k in c_str for k in ("% TO NAV", "% OF NAV", "WEIGHT", "PERCENTAGE")):
            weight_col = col

    # If columns not found in header, scan top 10 rows
    if not (isin_col and weight_col):
        for idx in range(min(15, len(df))):
            row_vals = [str(v).strip().upper() for v in df.iloc[idx].values]
            if any("ISIN" in v for v in row_vals):
                df.columns = df.iloc[idx]
                df = df.iloc[idx + 1:].reset_index(drop=True)
                for col in df.columns:
                    c_str = str(col).strip().upper()
                    if "ISIN" in c_str:
                        isin_col = col
                    elif any(k in c_str for k in ("NAME", "COMPANY", "INSTRUMENT")):
                        name_col = col
                    elif any(k in c_str for k in ("% TO NAV", "% OF NAV", "WEIGHT", "PERCENTAGE")):
                        weight_col = col
                break

    if not isin_col or not weight_col:
        raise ValueError(f"Could not identify required columns ('ISIN' and '% to NAV') in {file_path}")

    holdings: List[Dict[str, any]] = []
    isin_pattern = re.compile(r"^[A-Z]{2}[A-Z0-9]{9}\d$")

    for _, row in df.iterrows():
        raw_isin = str(row.get(isin_col, "")).strip().upper()
        raw_weight = row.get(weight_col)

        if not raw_isin or not isin_pattern.match(raw_isin):
            continue

        try:
            weight = float(str(raw_weight).replace("%", "").replace(",", "").strip())
        except (ValueError, TypeError):
            continue

        if weight <= 0 or weight > 100:
            continue

        raw_name = str(row.get(name_col, raw_isin)).strip() if name_col else raw_isin
        # Determine market
        market = "IN"
        if raw_isin.startswith("US") or "INC" in raw_name.upper() or "CORP" in raw_name.upper():
            if not raw_isin.startswith("INE") and not raw_isin.startswith("INF"):
                market = "US"

        holdings.append({
            "stock_symbol": raw_name[:64],
            "stock_isin": raw_isin,
            "weight_pct": round(weight, 3),
            "market": market,
            "source_type": "FACTSHEET_POI_PARSED"
        })

    return holdings
