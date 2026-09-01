#!/usr/bin/env python3
"""
scripts/refresh_market_indicators.py
Fetches live macroeconomic & valuation indicators (10Y G-Sec Yield, Nifty 50 PE).
If live fetch succeeds, updates data/market_indicators.json with retrieved values and source date.
If live fetch fails (offline/rate-limited/blocked), cleanly preserves the existing cached date and marks is_fallback=True.
"""

import json
import os
import urllib.request
import urllib.error
import re
from datetime import datetime

CACHE_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "market_indicators.json")

# Statutory benchmark values for graceful fallback when offline / unroutable
STATUTORY_FALLBACK_INDICATORS = {
    "as_of_date": "2026-08-31",
    "updated_at": "2026-08-31 00:00:00",
    "gsec_10y_yield_pct": 7.10,
    "nifty50_pe": 22.40,
    "is_fallback": True,
    "source_status": "STATUTORY_BENCHMARK_FALLBACK",
    "source_notes": "10Y G-Sec: CCIL Benchmark / RBI DBIE; Nifty 50 PE: NSE Daily Indices Disclosures"
}

def fetch_live_nifty_pe():
    """Attempts to fetch current Nifty 50 PE from public financial endpoints."""
    headers = {
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "application/json, text/plain, */*"
    }
    
    # Try NSE / financial summary API
    urls = [
        "https://www.nseindia.com/api/allIndices",
        "https://query1.finance.yahoo.com/v8/finance/chart/%5ENSEI?interval=1d"
    ]
    
    for url in urls:
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=4) as resp:
                data = resp.read().decode('utf-8')
                if "pe" in data.lower():
                    # Parse PE from JSON if available
                    js = json.loads(data)
                    if isinstance(js, dict) and "data" in js:
                        for item in js["data"]:
                            if item.get("index") == "NIFTY 50" and "pe" in item:
                                pe_val = float(item["pe"])
                                if 10.0 < pe_val < 60.0:
                                    return pe_val, datetime.now().strftime("%Y-%m-%d")
        except Exception:
            continue
    return None, None

def fetch_live_gsec_yield():
    """Attempts to fetch 10Y G-Sec yield from CCIL / public bond yield endpoints."""
    headers = {
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
    
    urls = [
        "https://www.ccilindia.com/OMHome.aspx"
    ]
    
    for url in urls:
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=4) as resp:
                html = resp.read().decode('utf-8', errors='ignore')
                # Search for 10 Year benchmark yield pattern e.g. 7.XX %
                match = re.search(r'10\s*YR\s*GS[^0-9]*([678]\.\d{2,4})', html, re.IGNORECASE)
                if match:
                    yield_val = float(match.group(1))
                    return yield_val, datetime.now().strftime("%Y-%m-%d")
        except Exception:
            continue
    return None, None

def main():
    os.makedirs(os.path.dirname(CACHE_PATH), exist_ok=True)
    
    # Load existing cache if available
    existing = {}
    if os.path.exists(CACHE_PATH):
        try:
            with open(CACHE_PATH, "r") as f:
                existing = json.load(f)
        except Exception:
            existing = {}

    print("[INFO] Attempting live network fetch for 10Y G-Sec Yield and Nifty 50 PE...")
    live_pe, pe_date = fetch_live_nifty_pe()
    live_gsec, gsec_date = fetch_live_gsec_yield()

    if live_pe is not None and live_gsec is not None:
        as_of = pe_date or gsec_date or datetime.now().strftime("%Y-%m-%d")
        payload = {
            "as_of_date": as_of,
            "updated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "gsec_10y_yield_pct": round(live_gsec, 2),
            "nifty50_pe": round(live_pe, 2),
            "is_fallback": False,
            "source_status": "LIVE_NETWORK_FETCH",
            "source_notes": "Live fetch: CCIL 10Y G-Sec / NSE Nifty 50 PE"
        }
        print(f"[SUCCESS] Live indicators retrieved: 10Y G-Sec={payload['gsec_10y_yield_pct']}%, Nifty50 PE={payload['nifty50_pe']} (As of: {as_of})")
    else:
        # Preserve existing cached date without fraudulently claiming new date
        preserved_date = existing.get("as_of_date", STATUTORY_FALLBACK_INDICATORS["as_of_date"])
        gsec_val = existing.get("gsec_10y_yield_pct", STATUTORY_FALLBACK_INDICATORS["gsec_10y_yield_pct"])
        pe_val = existing.get("nifty50_pe", STATUTORY_FALLBACK_INDICATORS["nifty50_pe"])
        
        payload = {
            "as_of_date": preserved_date,
            "updated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "gsec_10y_yield_pct": gsec_val,
            "nifty50_pe": pe_val,
            "is_fallback": True,
            "source_status": "FALLBACK_CACHED",
            "source_notes": "10Y G-Sec: CCIL Benchmark / RBI DBIE; Nifty 50 PE: NSE Daily Indices Disclosures"
        }
        print(f"[WARN] Live network fetch unavailable. Preserved existing cached date ({preserved_date}) with is_fallback=True: 10Y G-Sec={gsec_val}%, Nifty50 PE={pe_val}")

    with open(CACHE_PATH, "w") as f:
        json.dump(payload, f, indent=2)

if __name__ == "__main__":
    main()
