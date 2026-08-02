This file is a merged representation of a subset of the codebase, containing files not matching ignore patterns, combined into a single document by Repomix.
The content has been processed where empty lines have been removed.

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
- Files matching these patterns are excluded: __pycache__/**, *.pyc
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Empty lines have been removed from all files
- Files are sorted by Git change count (files with more changes are at the bottom)

# Directory Structure
```
parsers/
  broker_csv_parser.py
  cas_parser.py
  models.py
quant/
  quant_engine.py
app.py
Dockerfile
flight_server.py
requirements.txt
```

# Files

## File: parsers/broker_csv_parser.py
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
        return events
```

## File: parsers/cas_parser.py
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
                        if any(x in txn_type_str for x in ["REDEMPTION", "SWITCH_OUT", "SELL"]):
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
                                if any(x in line_upper for x in ["REDEMPTION", "SWITCH OUT", "SELL"]):
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
        return events
```

## File: parsers/models.py
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

## File: quant/quant_engine.py
```python
import numpy as np
import polars as pl
from hmmlearn import hmm
import logging
from typing import List, Tuple, Dict
logger = logging.getLogger(__name__)
def calculate_hurst_vectorized(ts: List[float]) -> float:
    """Vectorized Hurst Exponent calculation using Rescaled Range."""
    arr = np.array(ts)
    if len(arr) < 50:
        return 0.5
    lags = range(2, 20)
    tau = [np.sqrt(np.std(np.subtract(arr[lag:], arr[:-lag]))) for lag in lags]
    poly = np.polyfit(np.log(lags), np.log(tau), 1)
    return float(poly[0] * 2.0)
def calculate_ou_params_vectorized(navs: List[float]) -> dict:
    """Vectorized Ornstein-Uhlenbeck parameter estimation."""
    arr = np.array(navs)
    if len(arr) < 30:
        return {"half_life": 0.0, "valid": False}
    y = np.log(arr)
    x = y[:-1]
    dy = np.diff(y)
    # Regression: dy = (a + b*x)
    poly = np.polyfit(x, dy, 1)
    b, a = poly
    if b >= 0: # Non-stationary / diverging process
        return {"half_life": 0.0, "valid": False}
    theta = -b
    mu = -a / b
    half_life = np.log(2) / theta
    return {
        "theta": float(theta),
        "mu": float(mu),
        "half_life": float(half_life),
        "valid": True
    }
def calculate_hmm_regimes(returns_list: List[float], n_states: int = 3) -> Tuple[List[int], float, float, float]:
    """Fits HMM and returns states, bull probability, bear probability, and transit prob to bear."""
    if len(returns_list) < 50:
        return [0] * len(returns_list), 0.33, 0.33, 0.33
    try:
        data = np.array(returns_list).reshape(-1, 1)
        model = hmm.GaussianHMM(n_components=n_states, covariance_type="diag", n_iter=1000, random_state=42)
        model.fit(data)
        means = model.means_.flatten()
        # Sort indices by mean returns descending: [Bull, Neutral, Bear]
        sorted_indices = np.argsort(means)[::-1]
        rank_map = {orig_idx: rank for rank, orig_idx in enumerate(sorted_indices)}
        # Predictions
        states_raw = model.predict(data)
        states_mapped = [rank_map[s] for s in states_raw]
        curr_state_raw = states_raw[-1]
        # Probabilities
        probs_raw = model.predict_proba(data)[-1]
        bull_p = float(probs_raw[sorted_indices[0]])
        bear_p = float(probs_raw[sorted_indices[2]])
        # Transition matrix
        trans_mat_raw = model.transmat_
        to_bear_p = float(trans_mat_raw[curr_state_raw][sorted_indices[2]])
        return states_mapped, bull_p, bear_p, to_bear_p
    except Exception as e:
        logger.error(f"HMM fitting failed: {e}")
        return [0] * len(returns_list), 0.33, 0.33, 0.33
```

## File: app.py
```python
import os
import tempfile
import threading
import logging
from typing import List, Optional
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
import polars as pl
import uvicorn
from parsers.cas_parser import CasPdfParser
from parsers.broker_csv_parser import BrokerCsvParser
from parsers.models import TaxEventSchema
from flight_server import QuantFlightServer
# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("quant-sidecar")
app = FastAPI(title="Portfolio OS Quant & Parser Sidecar", version="3.0.0")
@app.get("/health")
def health_check():
    return {"status": "UP", "engine": "Polars + FastAPI + Arrow Flight", "version": "3.0.0"}
@app.post("/api/v1/parse", response_model=List[TaxEventSchema])
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
def run_flight_server():
    try:
        server = QuantFlightServer("0.0.0.0", 8001)
        logger.info("Starting Apache Arrow Flight RPC server on port 8001...")
        server.serve()
    except Exception as e:
        logger.error(f"Failed to start Flight server: {e}", exc_info=True)
if __name__ == "__main__":
    # Start Apache Arrow Flight RPC Server in a background daemon thread
    flight_thread = threading.Thread(target=run_flight_server, daemon=True)
    flight_thread.start()
    # Run FastAPI server
    logger.info("Starting FastAPI HTTP Server on port 8000...")
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

## File: Dockerfile
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

## File: flight_server.py
```python
import pyarrow as pa
import pyarrow.flight as flight
import polars as pl
import numpy as np
import logging
from quant.quant_engine import calculate_hmm_regimes, calculate_hurst_vectorized, calculate_ou_params_vectorized
logger = logging.getLogger(__name__)
class QuantFlightServer(flight.FlightServerBase):
    def __init__(self, host="0.0.0.0", port=8001, **kwargs):
        location = flight.Location.for_grpc_tcp(host, port)
        super(QuantFlightServer, self).__init__(location, **kwargs)
        self.host = host
        self.port = port
        logger.info(f"Initialized Apache Arrow Flight RPC server on {host}:{port}")
    def do_exchange(self, context, descriptor, reader, writer):
        try:
            # Read input table containing amfi_code and nav_value
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
                if len(nav_values) < 2:
                    continue
                # Compute returns
                returns = []
                for i in range(len(nav_values) - 1):
                    prev = nav_values[i]
                    curr = nav_values[i+1]
                    returns.append(np.log(curr / prev) if prev > 0 else 0.0)
                # Calculate metrics
                hurst = calculate_hurst_vectorized(returns)
                h_regime = "MEAN_REVERTING" if hurst < 0.47 else ("TRENDING" if hurst > 0.53 else "RANDOM_WALK")
                ou = calculate_ou_params_vectorized(nav_values)
                states, bull, bear, trans = calculate_hmm_regimes(returns)
                state_map = {0: "CALM_BULL", 1: "STRESSED_NEUTRAL", 2: "VOLATILE_BEAR"}
                hmm_state_str = state_map.get(states[-1], "UNKNOWN") if states else "UNKNOWN"
                results.append({
                    "amfi_code": code,
                    "hurst": float(hurst),
                    "hurst_regime": h_regime,
                    "ou_half_life": float(ou["half_life"]),
                    "ou_valid": bool(ou["valid"]),
                    "hmm_state": hmm_state_str,
                    "hmm_bull_prob": float(bull),
                    "hmm_bear_prob": float(bear),
                    "hmm_transition_bear": float(trans)
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
            self._write_empty_response(writer)
    def _write_empty_response(self, writer):
        schema = pa.schema([
            ("amfi_code", pa.string()),
            ("hurst", pa.float64()),
            ("hurst_regime", pa.string()),
            ("ou_half_life", pa.float64()),
            ("ou_valid", pa.bool_()),
            ("hmm_state", pa.string()),
            ("hmm_bull_prob", pa.float64()),
            ("hmm_bear_prob", pa.float64()),
            ("hmm_transition_bear", pa.float64())
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

## File: requirements.txt
```
fastapi>=0.110.0
uvicorn>=0.28.0
granian>=1.2.0
polars>=0.20.15
pyarrow>=15.0.0
pdfplumber>=0.11.0
casparser>=0.7.0
casparser-isin>=0.3.0
scikit-learn>=1.4.0
hmmlearn>=0.3.2
numpy>=1.26.0
scipy>=1.12.0
yfinance>=0.2.37
pydantic>=2.6.0
python-multipart>=0.0.9
```
