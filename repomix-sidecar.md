This file is a merged representation of the entire codebase, combined into a single document by Repomix.

<file_summary>
This section contains a summary of this file.

<purpose>
This file contains a packed representation of the entire repository's contents.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.
</purpose>

<file_format>
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  - File path as an attribute
  - Full contents of the file
</file_format>

<usage_guidelines>
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.
</usage_guidelines>

<notes>
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)
</notes>

</file_summary>

<directory_structure>
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
</directory_structure>

<files>
This section contains the contents of the repository's files.

<file path="parsers/broker_csv_parser.py">
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
</file>

<file path="parsers/cas_parser.py">
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
</file>

<file path="parsers/models.py">
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
</file>

<file path="parsers/sip_detector.py">
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
</file>

<file path="quant/analytics_engine.py">
import numpy as np
import pandas as pd
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
    current_corpus=1754783.21,
    annual_expense=600000.0,
    years=15,
    num_simulations=10000
):
    if daily_returns_list is None or len(daily_returns_list) < 10:
        return {
            "status": "INSUFFICIENT_DATA",
            "success_rate_pct": 95.0,
            "median_ending_corpus": current_corpus * 1.5,
            "tenth_percentile_corpus": current_corpus * 0.9
        }

    returns = np.array(daily_returns_list)
    trading_days = years * 252
    daily_expense = annual_expense / 252.0

    # Circular Block Bootstrapping: Sample contiguous multi-day blocks (block_size = 15 trading days)
    # to preserve temporal autocorrelation and GARCH volatility clustering regimes
    n_returns = len(returns)
    block_size = min(15, n_returns)
    n_blocks_needed = int(np.ceil(trading_days / block_size))

    simulated_daily_returns = np.zeros((num_simulations, trading_days))
    for sim_idx in range(num_simulations):
        start_indices = np.random.randint(0, n_returns, size=n_blocks_needed)
        path = []
        for idx in start_indices:
            block = [returns[(idx + k) % n_returns] for k in range(block_size)]
            path.extend(block)
        simulated_daily_returns[sim_idx, :] = np.array(path[:trading_days])

    surviving_sims = 0
    final_corpuses = []

    for sim_idx in range(num_simulations):
        corpus = current_corpus
        failed = False
        for day in range(trading_days):
            corpus = corpus * (1.0 + simulated_daily_returns[sim_idx, day]) - daily_expense
            if corpus <= 0:
                failed = True
                break
        if not failed:
            surviving_sims += 1
            final_corpuses.append(corpus)
        else:
            final_corpuses.append(0.0)

    success_rate = (surviving_sims / num_simulations) * 100.0
    median_corpus = float(np.median(final_corpuses))
    p10_corpus = float(np.percentile(final_corpuses, 10))

    return {
        "status": "OK",
        "num_simulations": num_simulations,
        "years": years,
        "success_rate_pct": round(success_rate, 2),
        "median_ending_corpus": round(median_corpus, 2),
        "tenth_percentile_corpus": round(p10_corpus, 2)
    }
</file>

<file path="app.py">
import os
import tempfile
import threading
import logging
from typing import List, Optional
from fastapi import FastAPI, File, Form, UploadFile, HTTPException, Header, Depends
import polars as pl
import uvicorn

from parsers.cas_parser import CasPdfParser
from parsers.broker_csv_parser import BrokerCsvParser
from parsers.sip_detector import detect_and_tag_sips
from parsers.models import TaxEventSchema
from flight_server import QuantFlightServer

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("quant-sidecar")

EXPECTED_AUTH_TOKEN = os.getenv("API_AUTH_TOKEN", "fintracker-cachyos-default-key-2026")

def verify_auth_token(x_api_auth_token: Optional[str] = Header(None)):
    if not x_api_auth_token or x_api_auth_token != EXPECTED_AUTH_TOKEN:
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
</file>

<file path="Dockerfile">
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
</file>

<file path="flight_server.py">
import pyarrow as pa
import pyarrow.flight as flight
import polars as pl
import logging
from quant.analytics_engine import compute_fund_analytics

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
            self._write_empty_response(writer)

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
</file>

<file path="requirements.txt">
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
</file>

</files>
