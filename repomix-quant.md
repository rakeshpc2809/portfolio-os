This file is a merged representation of a subset of the codebase, containing specifically included files, combined into a single document by Repomix.

# File Summary

## Purpose
This file contains a packed representation of a subset of the repository's contents that is considered the most important context.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.

## File Format
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  a. A header with the file path (## File: path/to/file)
  b. The full contents of the file in a code block

## Usage Guidelines
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.

## Notes
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Only files matching these patterns are included: quant-sidecar/**/*
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)

# Directory Structure
```
quant-sidecar/
  parsers/
    broker_csv_parser.py
    cas_parser.py
    models.py
    sip_detector.py
  quant/
    analytics_engine.py
  app.py
  Dockerfile
  flight_server.py
  requirements.txt
```

# Files

## File: quant-sidecar/parsers/models.py
```python
from enum import Enum
from datetime import date, datetime
from decimal import Decimal
from typing import Optional
from pydantic import BaseModel, Field

class EventType(str, Enum):
    ACQUISITION = "ACQUISITION"
    SIP_INSTALMENT = "SIP_INSTALMENT"
    DISPOSAL = "DISPOSAL"
    BONUS = "BONUS"
    SPLIT = "SPLIT"
    DIVIDEND_REINVEST = "DIVIDEND_REINVEST"
    SGB_INTEREST = "SGB_INTEREST"
    SGB_MATURITY = "SGB_MATURITY"
    MERGER = "MERGER"

class TaxEventSchema(BaseModel):
    id: str
    asset_id: str = Field(..., alias="assetId")
    asset_name: str = Field(..., alias="assetName")
    isin: Optional[str] = None
    event_type: EventType = Field(..., alias="eventType")
    event_date: date = Field(..., alias="eventDate")
    units: Decimal
    price_per_unit: Decimal = Field(..., alias="pricePerUnit")
    gross_amount: Decimal = Field(..., alias="grossAmount")
    source_document_id: str = Field(..., alias="sourceDocumentId")
    ingested_at: datetime = Field(default_factory=datetime.utcnow, alias="ingestedAt")

    class Config:
        populate_by_name = True

    def unit_delta(self) -> Decimal:
        if self.event_type == EventType.DISPOSAL or self.event_type == EventType.SGB_MATURITY:
            return -self.units
        elif self.event_type == EventType.SGB_INTEREST:
            return Decimal("0.0")
        return self.units
```

## File: quant-sidecar/Dockerfile
```dockerfile
FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000 8001

CMD ["python", "app.py"]
```

## File: quant-sidecar/parsers/broker_csv_parser.py
```python
import uuid
from datetime import datetime
from decimal import Decimal
from typing import List, Optional
import polars as pl
from .models import TaxEventSchema, EventType

class BrokerCsvParser:
    def __init__(self, csv_path: str, broker_type: str = "generic"):
        self.csv_path = csv_path
        self.broker_type = broker_type

    def parse(self) -> List[TaxEventSchema]:
        events: List[TaxEventSchema] = []
        try:
            df = pl.read_csv(self.csv_path, infer_schema_length=0)
            if df.is_empty():
                return events

            col_map = {str(c).strip().lower(): c for c in df.columns}

            date_col = next((col_map[k] for k in col_map if any(x in k for x in ["date", "txn_date", "trade_date"])), None)
            symbol_col = next((col_map[k] for k in col_map if any(x in k for x in ["symbol", "scheme", "scrip", "asset", "description"])), None)
            type_col = next((col_map[k] for k in col_map if any(x in k for x in ["type", "buy/sell", "transaction", "action"])), None)
            qty_col = next((col_map[k] for k in col_map if any(x in k for x in ["qty", "quantity", "units"])), None)
            price_col = next((col_map[k] for k in col_map if any(x in k for x in ["price", "nav", "rate"])), None)
            amount_col = next((col_map[k] for k in col_map if any(x in k for x in ["amount", "value", "total"])), None)

            for row in df.to_dicts():
                try:
                    asset_name = str(row[symbol_col]) if symbol_col and row.get(symbol_col) else "Broker Asset"
                    date_str = str(row[date_col]) if date_col and row.get(date_col) else ""

                    event_date = datetime.now().date()
                    if date_str:
                        for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%d/%m/%Y", "%d-%b-%Y"):
                            try:
                                event_date = datetime.strptime(date_str.strip(), fmt).date()
                                break
                            except ValueError:
                                pass

                    txn_type_str = str(row[type_col]).upper() if type_col and row.get(type_col) else "BUY"
                    if any(x in txn_type_str for x in ["SELL", "REDEMPTION", "DISPOSAL", "SWITCH OUT"]):
                        event_type = EventType.DISPOSAL
                    elif "BONUS" in txn_type_str:
                        event_type = EventType.BONUS
                    elif "SPLIT" in txn_type_str:
                        event_type = EventType.SPLIT
                    else:
                        event_type = EventType.ACQUISITION

                    units_val = row.get(qty_col)
                    units = Decimal(str(abs(float(units_val)))) if units_val is not None and str(units_val).strip() != "" else Decimal("1")
                    
                    price_val = row.get(price_col)
                    price = Decimal(str(abs(float(price_val)))) if price_val is not None and str(price_val).strip() != "" else Decimal("0")
                    
                    amt_val = row.get(amount_col)
                    amount = Decimal(str(abs(float(amt_val)))) if amt_val is not None and str(amt_val).strip() != "" else (units * price)

                    events.append(
                        TaxEventSchema(
                            id=str(uuid.uuid4()),
                            assetId=asset_name.replace(" ", "_").upper()[:20],
                            assetName=asset_name,
                            isin=None,
                            eventType=event_type,
                            eventDate=event_date,
                            units=units,
                            pricePerUnit=price,
                            grossAmount=amount,
                            sourceDocumentId=self.csv_path,
                            ingestedAt=datetime.now()
                        )
                    )
                except Exception:
                    continue
        except Exception:
            pass

        from .sip_detector import detect_and_tag_sips
        return detect_and_tag_sips(events)
```

## File: quant-sidecar/parsers/cas_parser.py
```python
import re
import uuid
from decimal import Decimal
from typing import List, Optional
from datetime import datetime, date
from .models import TaxEventSchema, EventType

DATE_REGEX = re.compile(r"^(\d{2}-[A-Za-z]{3}-\d{4})\s+(.+)$")
# Added support for both CAMS and KFintech PAN formats in CAS
ISIN_REGEX = re.compile(r"ISIN:\s*([A-Z0-9]{12})", re.IGNORECASE)
TOKEN_REGEX = re.compile(r"\((?:\d{1,3}(?:,\d{3})*|\d+)(?:\.\d+)?\)|\b\d{1,3}(?:,\d{3})*(?:\.\d+)?\b|\b\d+\.\d+\b")

class CasPdfParser:
    def __init__(self, pdf_path: str, password: Optional[str] = None):
        self.pdf_path = pdf_path
        self.password = password

    def parse_events(self) -> List[TaxEventSchema]:
        events: List[TaxEventSchema] = []
        if not self.pdf_path:
            return events

        # Try specialized casparser library first
        try:
            import casparser
            data = casparser.read_cas_pdf(self.pdf_path, self.password or "")
            for folio in data.folios:
                for scheme in folio.schemes:
                    isin = scheme.isin
                    scheme_name = scheme.scheme
                    asset_id = isin or scheme_name.replace(" ", "_").upper()[:20]

                    for txn in scheme.transactions:
                        txn_type_str = str(txn.type).upper()
                        if any(x in txn_type_str for x in ["REDEMPTION", "SWITCH_OUT", "SWITCH OUT", "SELL", "STP_OUT", "STP OUT", "SWP", "SYSTEMATIC WITHDRAWAL"]):
                            event_type = EventType.DISPOSAL
                        elif "BONUS" in txn_type_str:
                            event_type = EventType.BONUS
                        elif "SPLIT" in txn_type_str:
                            event_type = EventType.SPLIT
                        else:
                            event_type = EventType.ACQUISITION

                        txn_date = txn.date if isinstance(txn.date, date) else datetime.now().date()
                        units = Decimal(str(abs(txn.units or 0)))
                        price = Decimal(str(abs(txn.nav or 0)))
                        amount = Decimal(str(abs(txn.amount or 0)))
                        if amount == Decimal("0") and units > 0 and price > 0:
                            amount = units * price

                        if units > Decimal("0"):
                            events.append(
                                TaxEventSchema(
                                    id=str(uuid.uuid4()),
                                    assetId=asset_id,
                                    assetName=scheme_name,
                                    isin=isin,
                                    eventType=event_type,
                                    eventDate=txn_date,
                                    units=units,
                                    pricePerUnit=price,
                                    grossAmount=amount,
                                    sourceDocumentId=self.pdf_path,
                                    ingestedAt=datetime.now()
                                )
                            )
            if events:
                return events
        except Exception as e:
            print(f"casparser notice: {e}, falling back to custom line parser.")

        # Fallback to pdfplumber regex line parser
        try:
            import pdfplumber

            current_scheme = "Mutual Fund Scheme"
            current_isin: Optional[str] = None

            with pdfplumber.open(self.pdf_path, password=self.password or "") as pdf:
                for page in pdf.pages:
                    text = page.extract_text() or ""
                    for line in text.splitlines():
                        line_str = line.strip()
                        if not line_str:
                            continue

                        isin_match = ISIN_REGEX.search(line_str)
                        if isin_match:
                            current_isin = isin_match.group(1)

                        if "ISIN:" in line_str or ("Fund" in line_str and "Registrar" in line_str):
                            current_scheme = line_str.split(" - ISIN:")[0].split("(Advisor")[0].strip()

                        if any(
                            x in line_str
                            for x in [
                                "*** Stamp Duty ***",
                                "*** STT Paid ***",
                                "***Cancelled***",
                                "***Address Updated",
                                "Opening Unit Balance",
                                "CAMSCASWS",
                                "Consolidated Account Statement",
                                "Closing Unit Balance",
                                "NAV on",
                            ]
                        ):
                            continue

                        match = DATE_REGEX.match(line_str)
                        if match:
                            date_str, rest = match.groups()
                            try:
                                event_date = datetime.strptime(date_str, "%d-%b-%Y").date()
                            except ValueError:
                                event_date = datetime.now().date()

                            num_tokens = TOKEN_REGEX.findall(rest)

                            clean_nums = []
                            for tok in num_tokens:
                                is_neg = tok.startswith("(") and tok.endswith(")")
                                raw_val = tok.replace("(", "").replace(")", "").replace(",", "").strip()
                                try:
                                    val = Decimal(raw_val)
                                    if is_neg:
                                        val = -val
                                    clean_nums.append(val)
                                except Exception:
                                    pass

                            if len(clean_nums) >= 3:
                                amount = abs(clean_nums[0])
                                units = abs(clean_nums[1])
                                price = clean_nums[2]

                                line_upper = rest.upper()
                                if any(x in line_upper for x in ["REDEMPTION", "SWITCH OUT", "SWITCH_OUT", "SELL", "STP OUT", "STP_OUT", "SWP", "SYSTEMATIC WITHDRAWAL"]):
                                    event_type = EventType.DISPOSAL
                                elif "BONUS" in line_upper:
                                    event_type = EventType.BONUS
                                elif "SPLIT" in line_upper:
                                    event_type = EventType.SPLIT
                                else:
                                    event_type = EventType.ACQUISITION

                                events.append(
                                    TaxEventSchema(
                                        id=str(uuid.uuid4()),
                                        assetId=current_isin or current_scheme.replace(" ", "_").upper()[:20],
                                        assetName=current_scheme,
                                        isin=current_isin,
                                        eventType=event_type,
                                        eventDate=event_date,
                                        units=units,
                                        pricePerUnit=price,
                                        grossAmount=amount,
                                        sourceDocumentId=self.pdf_path,
                                        ingestedAt=datetime.now()
                                    )
                                )
        except Exception as err:
            print(f"Fallback parser error: {err}")

        from .sip_detector import detect_and_tag_sips
        return detect_and_tag_sips(events)
```

## File: quant-sidecar/parsers/sip_detector.py
```python
from typing import List
from collections import defaultdict
from .models import TaxEventSchema, EventType

def detect_and_tag_sips(events: List[TaxEventSchema], min_consecutive_matches: int = 3) -> List[TaxEventSchema]:
    """
    Auto-detects Systematic Investment Plans (SIPs) by grouping transactions by ISIN/Asset ID,
    checking date spacing (25 to 35 days for monthly recurring investments), and amount variation (<= 5%).
    Requires at least `min_consecutive_matches` (default 3+) consecutive matching transactions to eliminate false positives.
    Tags matching ACQUISITION events as EventType.SIP_INSTALMENT.
    """
    if not events:
        return events

    acquisitions_by_asset = defaultdict(list)
    for idx, event in enumerate(events):
        if event.event_type in (EventType.ACQUISITION, EventType.SIP_INSTALMENT):
            asset_key = event.isin or event.asset_id
            acquisitions_by_asset[asset_key].append((idx, event))

    sip_indices = set()

    for asset_key, asset_events in acquisitions_by_asset.items():
        if len(asset_events) < min_consecutive_matches:
            continue

        sorted_events = sorted(asset_events, key=lambda x: x[1].event_date)
        current_chain = [sorted_events[0]]

        for i in range(len(sorted_events) - 1):
            idx1, ev1 = sorted_events[i]
            idx2, ev2 = sorted_events[i + 1]

            date_diff = (ev2.event_date - ev1.event_date).days
            amt1 = float(ev1.gross_amount)
            amt2 = float(ev2.gross_amount)
            amt_diff_pct = abs(amt1 - amt2) / max(amt1, amt2, 1.0)

            # Monthly SIP criteria: 25 to 35 days spacing AND <= 5% amount variation
            if 25 <= date_diff <= 35 and amt_diff_pct <= 0.05:
                current_chain.append(sorted_events[i + 1])
            else:
                if len(current_chain) >= min_consecutive_matches:
                    for chain_idx, _ in current_chain:
                        sip_indices.add(chain_idx)
                current_chain = [sorted_events[i + 1]]

        if len(current_chain) >= min_consecutive_matches:
            for chain_idx, _ in current_chain:
                sip_indices.add(chain_idx)

    updated_events = []
    for idx, event in enumerate(events):
        if idx in sip_indices:
            updated_events.append(event.model_copy(update={"event_type": EventType.SIP_INSTALMENT}))
        else:
            updated_events.append(event)

    return updated_events
```

## File: quant-sidecar/requirements.txt
```
fastapi>=0.110.0
uvicorn>=0.28.0
granian>=1.2.0
polars>=0.20.15
pyarrow>=15.0.0
pdfplumber>=0.11.0
casparser>=0.7.0
casparser-isin>=0.3.0
numpy>=1.26.0
scipy>=1.12.0
yfinance>=0.2.37
pandas>=2.2.0
quantstats>=0.0.62
pydantic>=2.6.0
python-multipart>=0.0.9
```

## File: quant-sidecar/flight_server.py
```python
import json
import pyarrow as pa
import pyarrow.flight as flight
import polars as pl
import logging
from quant.analytics_engine import compute_fund_analytics, run_monte_carlo_fire_simulation

logger = logging.getLogger(__name__)

class QuantFlightServer(flight.FlightServerBase):
    def __init__(self, host="0.0.0.0", port=8001, **kwargs):
        location = flight.Location.for_grpc_tcp(host, port)
        super(QuantFlightServer, self).__init__(location, **kwargs)
        self._srv_host = host
        self._srv_port = port
        logger.info(f"Initialized Apache Arrow Flight RPC server on {host}:{port}")

    def do_action(self, context, action):
        if action.type == "fire_simulation":
            try:
                params = json.loads(action.body.to_pybytes().decode('utf-8'))
                missing_keys = [k for k in ("current_corpus", "annual_expense", "monthly_contribution", "years_to_retirement") if k not in params]
                if missing_keys:
                    raise flight.FlightInvalidArgument(f"Missing required simulation parameters: {', '.join(missing_keys)}")

                daily_returns = params.get("daily_returns", [])
                current_corpus = float(params["current_corpus"])
                annual_expense = float(params["annual_expense"])
                monthly_contrib = float(params["monthly_contribution"])
                years_ret = int(params["years_to_retirement"])
                num_sims = int(params.get("num_simulations", 10000))

                result = run_monte_carlo_fire_simulation(
                    daily_returns_list=daily_returns,
                    current_corpus=current_corpus,
                    annual_expense=annual_expense,
                    monthly_contribution=monthly_contrib,
                    years_to_retirement=years_ret,
                    num_simulations=num_sims
                )
                result_bytes = json.dumps(result).encode('utf-8')
                return [flight.Result(result_bytes)]
            except flight.FlightError:
                raise
            except Exception as e:
                logger.error(f"Error executing FIRE Monte Carlo action: {e}", exc_info=True)
                raise flight.FlightInternalError(f"FIRE simulation action failed: {str(e)}")
        return []

    def do_exchange(self, context, descriptor, reader, writer):
        try:
            table = reader.read_all()
            if table.num_rows == 0:
                self._write_empty_response(writer)
                return

            df = pl.from_arrow(table)
            results = []
            unique_codes = df["amfi_code"].unique().to_list()

            for code in unique_codes:
                fund_df = df.filter(pl.col("amfi_code") == code)
                nav_values = fund_df["nav_value"].to_list()
                dates_list = fund_df["nav_date"].to_list() if "nav_date" in fund_df.columns else None

                analytics = compute_fund_analytics(nav_values, dates=dates_list)

                results.append({
                    "amfi_code": str(code),
                    "status": str(analytics.get("status", "OK")),
                    "sharpe": float(analytics.get("sharpe", 0.0)),
                    "sortino": float(analytics.get("sortino", 0.0)),
                    "calmar": float(analytics.get("calmar", 0.0)),
                    "max_drawdown": float(analytics.get("max_drawdown", 0.0)),
                    "volatility_annual": float(analytics.get("volatility_annual", 0.0)),
                    "var_95": float(analytics.get("var_95", 0.0)),
                    "cvar_95": float(analytics.get("cvar_95", 0.0)),
                    "beta": float(analytics.get("beta", 0.0))
                })

            if results:
                out_df = pl.DataFrame(results)
                out_table = out_df.to_arrow()
            else:
                self._write_empty_response(writer)
                return

            writer.begin(out_table.schema)
            writer.write_table(out_table)
            writer.close()
        except Exception as e:
            logger.error(f"Error during Flight exchange processing: {e}", exc_info=True)
            raise flight.FlightInternalError(f"Flight exchange failed: {str(e)}")

    def _write_empty_response(self, writer):
        schema = pa.schema([
            ("amfi_code", pa.string()),
            ("status", pa.string()),
            ("sharpe", pa.float64()),
            ("sortino", pa.float64()),
            ("calmar", pa.float64()),
            ("max_drawdown", pa.float64()),
            ("volatility_annual", pa.float64()),
            ("var_95", pa.float64()),
            ("cvar_95", pa.float64()),
            ("beta", pa.float64())
        ])
        out_table = pa.Table.from_batches([], schema)
        writer.begin(schema)
        writer.write_table(out_table)
        writer.close()

def start_flight_server(host="0.0.0.0", port=8001):
    server = QuantFlightServer(host, port)
    server.serve()

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    start_flight_server()
```

## File: quant-sidecar/app.py
```python
import os
import tempfile
import threading
import logging
from typing import List, Optional
from fastapi import FastAPI, File, Form, UploadFile, HTTPException, Header, Depends
from pydantic import BaseModel
import polars as pl
import uvicorn

from parsers.cas_parser import CasPdfParser
from parsers.broker_csv_parser import BrokerCsvParser
from parsers.sip_detector import detect_and_tag_sips
from parsers.models import TaxEventSchema
from flight_server import QuantFlightServer
from quant.analytics_engine import run_monte_carlo_fire_simulation

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("quant-sidecar")

import secrets

EXPECTED_AUTH_TOKEN = os.getenv("API_AUTH_TOKEN")

def verify_auth_token(x_api_auth_token: Optional[str] = Header(None)):
    token = EXPECTED_AUTH_TOKEN or "fintracker-cachyos-default-key-2026"
    if not x_api_auth_token or not secrets.compare_digest(x_api_auth_token, token):
        raise HTTPException(status_code=401, detail="Unauthorized: Invalid or missing X-Api-Auth-Token header")

app = FastAPI(title="Portfolio OS Quant & Parser Sidecar", version="3.0.0")

@app.get("/health")
def health_check():
    return {"status": "UP", "engine": "Polars + FastAPI + Arrow Flight", "version": "3.0.0"}

@app.post("/api/v1/parse", response_model=List[TaxEventSchema], dependencies=[Depends(verify_auth_token)])
async def parse_statement(
    file: UploadFile = File(...),
    password: Optional[str] = Form(None)
):
    filename = file.filename or "statement"
    ext = os.path.splitext(filename)[1].lower()
    logger.info(f"Received statement upload: {filename} with extension {ext}")

    with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        events = []
        if ext == ".pdf":
            parser = CasPdfParser(tmp_path, password=password)
            events = parser.parse_events()
        elif ext == ".csv":
            parser = BrokerCsvParser(tmp_path)
            events = parser.parse()
        else:
            raise HTTPException(status_code=400, detail="Unsupported file format. Please upload PDF or CSV.")

        # Apply robust 3+ match SIP auto-detection
        events = detect_and_tag_sips(events)

        # Polars multi-threaded dataframe verification
        if events:
            df = pl.DataFrame([e.model_dump(by_alias=True) for e in events])
            required_cols = ["id", "assetId", "assetName", "eventType", "eventDate", "units", "grossAmount"]
            for col in required_cols:
                if col not in df.columns:
                    raise HTTPException(status_code=422, detail=f"Missing column in parsed dataframe: {col}")
        
        logger.info(f"Successfully parsed {len(events)} events from statement")
        return events
    except Exception as err:
        logger.error(f"Error parsing statement: {err}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(err))
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

from quant.analytics_engine import run_monte_carlo_fire_simulation, compute_benchmark_analytics

class FireSimulationRequest(BaseModel):
    daily_returns: List[float] = []
    current_corpus: float
    annual_expense: float
    monthly_contribution: float
    years_to_retirement: int
    num_simulations: int = 10000

class BenchmarkAnalyticsRequest(BaseModel):
    portfolio_returns: List[float]
    benchmark_returns: List[float]
    benchmark_name: str = "NIFTY_50_TRI"

@app.post("/api/v1/simulate_fire", dependencies=[Depends(verify_auth_token)])
async def simulate_fire(req: FireSimulationRequest):
    return run_monte_carlo_fire_simulation(
        daily_returns_list=req.daily_returns,
        current_corpus=req.current_corpus,
        annual_expense=req.annual_expense,
        monthly_contribution=req.monthly_contribution,
        years_to_retirement=req.years_to_retirement,
        num_simulations=req.num_simulations
    )

@app.post("/api/v1/analytics/benchmark", dependencies=[Depends(verify_auth_token)])
async def analyze_benchmark(req: BenchmarkAnalyticsRequest):
    return compute_benchmark_analytics(
        portfolio_returns=req.portfolio_returns,
        benchmark_returns=req.benchmark_returns,
        benchmark_name=req.benchmark_name
    )

def run_flight_server():
    try:
        server = QuantFlightServer("0.0.0.0", 8001)
        logger.info("Starting Apache Arrow Flight RPC server on port 8001...")
        server.serve()
    except Exception as e:
        logger.error(f"Failed to start Flight server: {e}", exc_info=True)

if __name__ == "__main__":
    flight_thread = threading.Thread(target=run_flight_server, daemon=True)
    flight_thread.start()
    
    logger.info("Starting FastAPI HTTP Server on port 8000...")
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

## File: quant-sidecar/quant/analytics_engine.py
```python
import numpy as np
import pandas as pd
import logging

logger = logging.getLogger("quant.analytics_engine")
try:
    import quantstats as qs
except ImportError:
    qs = None

def compute_fund_analytics(nav_series, dates=None, benchmark_returns=None):
    if len(nav_series) < 30:
        return {
            "status": "INSUFFICIENT_HISTORY",
            "data_points": len(nav_series),
            "sharpe": 0.0,
            "sortino": 0.0,
            "calmar": 0.0,
            "max_drawdown": 0.0,
            "volatility_annual": 0.0,
            "var_95": 0.0,
            "cvar_95": 0.0,
            "beta": 0.0
        }

    try:
        if dates is not None and len(dates) == len(nav_series) and any(d for d in dates if d):
            valid_pairs = [(nav, d) for nav, d in zip(nav_series, dates) if d]
            if len(valid_pairs) >= 10:
                vals, d_str = zip(*valid_pairs)
                idx = pd.to_datetime(d_str)
                s = pd.Series(vals, index=idx)
            else:
                s = pd.Series(nav_series)
        else:
            s = pd.Series(nav_series)

        returns = s.pct_change().dropna()

        if len(returns) < 10:
            return {
                "status": "INSUFFICIENT_HISTORY",
                "data_points": len(returns),
                "sharpe": 0.0,
                "sortino": 0.0,
                "calmar": 0.0,
                "max_drawdown": 0.0,
                "volatility_annual": 0.0,
                "var_95": 0.0,
                "cvar_95": 0.0,
                "beta": 0.0
            }

        if qs is not None:
            sharpe = float(qs.stats.sharpe(returns))
            sortino = float(qs.stats.sortino(returns))
            calmar = float(qs.stats.calmar(returns))
            max_dd = float(qs.stats.max_drawdown(returns))
            vol = float(qs.stats.volatility(returns))
            var95 = float(qs.stats.value_at_risk(returns))
            cvar95 = float(qs.stats.conditional_value_at_risk(returns))

            beta = 0.0
            if benchmark_returns is not None:
                try:
                    beta_val = qs.stats.greeks(returns, benchmark_returns).get("beta", 0.0)
                    beta = float(beta_val) if not np.isnan(beta_val) else 0.0
                except Exception:
                    beta = 0.0
        else:
            # Vectorized fallback calculation with true Downside Deviation Sortino ratio
            mean_ret = returns.mean()
            std_ret = returns.std()
            sharpe = float((mean_ret / std_ret) * np.sqrt(252)) if std_ret > 0 else 0.0
            
            downside_returns = returns[returns < 0]
            downside_std = downside_returns.std() if not downside_returns.empty else 0.0
            sortino = float((mean_ret / downside_std) * np.sqrt(252)) if downside_std > 0 else sharpe

            cum_returns = (1 + returns).cumprod()
            peak = cum_returns.cummax()
            dd = (cum_returns - peak) / peak
            max_dd = float(dd.min())
            calmar = float(mean_ret * 252 / abs(max_dd)) if abs(max_dd) > 0 else 0.0
            vol = float(std_ret * np.sqrt(252))
            var95 = float(returns.quantile(0.05))
            cvar95 = float(returns[returns <= var95].mean()) if not returns[returns <= var95].empty else var95
            beta = 0.0

        return {
            "status": "OK",
            "sharpe": 0.0 if np.isnan(sharpe) else round(sharpe, 2),
            "sortino": 0.0 if np.isnan(sortino) else round(sortino, 2),
            "calmar": 0.0 if np.isnan(calmar) else round(calmar, 2),
            "max_drawdown": 0.0 if np.isnan(max_dd) else round(max_dd, 4),
            "volatility_annual": 0.0 if np.isnan(vol) else round(vol, 4),
            "var_95": 0.0 if np.isnan(var95) else round(var95, 4),
            "cvar_95": 0.0 if np.isnan(cvar95) else round(cvar95, 4),
            "beta": 0.0 if np.isnan(beta) else round(beta, 2)
        }
    except Exception as e:
        return {
            "status": "ERROR",
            "message": str(e),
            "sharpe": 0.0,
            "sortino": 0.0,
            "calmar": 0.0,
            "max_drawdown": 0.0,
            "volatility_annual": 0.0,
            "var_95": 0.0,
            "cvar_95": 0.0,
            "beta": 0.0
        }

def run_monte_carlo_fire_simulation(
    daily_returns_list,
    current_corpus=1407122.81,
    annual_expense=720000.0,
    monthly_contribution=75000.0,
    years_to_retirement=13,
    retirement_duration_years=30,
    num_simulations=10000
):
    is_empirical = daily_returns_list is not None and len(daily_returns_list) >= 750
    if not is_empirical:
        returns = np.random.normal(loc=0.00045, scale=0.011, size=10000)
        returns = returns - returns.mean() + 0.00045
        data_source = "SYNTHETIC_MARKET_BENCHMARK"
        data_source_label = "Nifty 50 Historical Return Model (Insufficient Empirical History < 3 Years)"
    else:
        returns = np.array(daily_returns_list)
        data_source = "EMPIRICAL_PORTFOLIO"
        data_source_label = "Empirical Portfolio Return History (15-Day Block Bootstrap)"

    n_returns = len(returns)
    total_years = max(1, years_to_retirement) + max(1, retirement_duration_years)
    total_days = total_years * 252
    accumulation_days = max(1, years_to_retirement) * 252

    daily_sip = (monthly_contribution * 12.0) / 252.0
    daily_expense = annual_expense / 252.0

    block_size = min(15, n_returns)
    n_blocks_needed = int(np.ceil(total_days / block_size))

    max_start = max(1, n_returns - block_size + 1)
    start_indices = np.random.randint(0, max_start, size=(num_simulations, n_blocks_needed))
    offsets = np.arange(block_size)
    sampled_blocks = start_indices[:, :, None] + offsets[None, None, :]
    sim_returns = returns[sampled_blocks].reshape(num_simulations, -1)[:, :total_days]
    daily_inflation = 0.06 / 252.0
    real_sim_returns = sim_returns - daily_inflation

    logger.info(f"Realized simulation returns: daily_real_mean={real_sim_returns.mean():.6f}, annualized_real_mean={real_sim_returns.mean()*252:.4f}, annualized_std={real_sim_returns.std()*np.sqrt(252):.4f}")

    corpuses = np.full(num_simulations, float(current_corpus))
    failed = np.zeros(num_simulations, dtype=bool)

    trajectories = []
    trajectories.append({
        "year": 0,
        "p10": round(float(current_corpus), 2),
        "p25": round(float(current_corpus), 2),
        "p50": round(float(current_corpus), 2),
        "p75": round(float(current_corpus), 2),
        "p90": round(float(current_corpus), 2)
    })

    for y in range(1, total_years + 1):
        day_start = (y - 1) * 252
        day_end = min(y * 252, total_days)

        for day in range(day_start, day_end):
            if day < accumulation_days:
                corpuses = corpuses * (1.0 + real_sim_returns[:, day]) + daily_sip
            else:
                corpuses = corpuses * (1.0 + real_sim_returns[:, day]) - daily_expense
                failed = failed | (corpuses <= 0)
                corpuses = np.maximum(corpuses, 0.0)

        trajectories.append({
            "year": y,
            "p10": round(float(np.percentile(corpuses, 10)), 2),
            "p25": round(float(np.percentile(corpuses, 25)), 2),
            "p50": round(float(np.median(corpuses)), 2),
            "p75": round(float(np.percentile(corpuses, 75)), 2),
            "p90": round(float(np.percentile(corpuses, 90)), 2)
        })

    surviving = ~failed
    success_rate = float(np.mean(surviving) * 100.0)
    ret_year_idx = min(years_to_retirement, len(trajectories) - 1)
    ret_trajectory = trajectories[ret_year_idx]
    median_corpus = ret_trajectory["p50"]
    p10_corpus = ret_trajectory["p10"]

    final_trajectory = trajectories[-1]
    return {
        "status": "OK",
        "data_source": data_source,
        "data_source_label": data_source_label,
        "num_simulations": num_simulations,
        "years_to_retirement": years_to_retirement,
        "retirement_duration_years": retirement_duration_years,
        "success_rate_pct": round(success_rate, 2),
        "median_retirement_start_corpus": round(median_corpus, 2),
        "median_final_ending_corpus": round(final_trajectory["p50"], 2),
        "tenth_percentile_final_ending_corpus": round(final_trajectory["p10"], 2),
        "median_ending_corpus": round(median_corpus, 2),
        "tenth_percentile_corpus": round(p10_corpus, 2),
        "fan_chart_trajectories": trajectories
    }


def compute_benchmark_analytics(portfolio_returns, benchmark_returns, benchmark_name="NIFTY_50_TRI"):
    p_rets = np.array(portfolio_returns, dtype=float)
    b_rets = np.array(benchmark_returns, dtype=float)

    if len(p_rets) == 0 or len(b_rets) == 0 or len(p_rets) != len(b_rets):
        return {
            "status": "ERROR",
            "message": "Mismatch or empty return series for benchmark analytics"
        }

    p_cagr = float(p_rets.mean() * 252.0 * 100.0)
    b_cagr = float(b_rets.mean() * 252.0 * 100.0)
    p_vol = float(p_rets.std() * np.sqrt(252.0) * 100.0)
    b_vol = float(b_rets.std() * np.sqrt(252.0) * 100.0)

    cov = float(np.cov(p_rets, b_rets)[0][1]) if len(p_rets) > 1 else 0.0
    var_b = float(np.var(b_rets)) if len(b_rets) > 1 else 0.0
    beta = round(cov / var_b, 3) if var_b > 0 else 1.0

    rf_pct = 6.50 # RBI 91-Day T-Bill Benchmark Rate
    alpha_ann = round(p_cagr - (rf_pct + beta * (b_cagr - rf_pct)), 2)
    tracking_err = round(float(np.std(p_rets - b_rets) * np.sqrt(252.0) * 100.0), 2)
    sharpe = round((p_cagr - rf_pct) / p_vol, 2) if p_vol > 0 else 0.0
    outperformance = round(p_cagr - b_cagr, 2)

    sample_days = len(p_rets)
    is_provisional = sample_days < 750

    # Sanity guard on Sharpe ratio: Extreme ratios (|Sharpe| > 3.5) on short samples (< 30 days) are statistically ungrounded
    if sample_days < 30 or abs((p_cagr - rf_pct) / p_vol if p_vol > 0 else 0.0) > 3.5:
        sharpe = 0.0
        sample_status = "PROVISIONAL_UNSTABLE_SAMPLE" if is_provisional else "SANITY_BOUND_REJECTED"
    else:
        sample_status = "PROVISIONAL_SHORT_SAMPLE" if is_provisional else "MATURE_EMPIRICAL_SAMPLE"

    data_source_label = f"Provisional Benchmark Metrics (Short Sample: {sample_days} Days < 3 Years)" if is_provisional else "Mature Benchmark Risk Metrics (3+ Years History)"

    return {
        "status": "OK",
        "benchmark_name": benchmark_name,
        "sample_days": sample_days,
        "is_provisional": is_provisional,
        "sample_status": sample_status,
        "data_source_label": data_source_label,
        "risk_free_rate_pct": rf_pct,
        "portfolio_cagr_pct": round(p_cagr, 2),
        "benchmark_cagr_pct": round(b_cagr, 2),
        "portfolio_vol_pct": round(p_vol, 2),
        "benchmark_vol_pct": round(b_vol, 2),
        "alpha_pct": alpha_ann,
        "beta": beta,
        "sharpe_ratio": sharpe,
        "tracking_error_pct": tracking_err,
        "outperformance_pct": outperformance
    }
```
