This file is a merged representation of a subset of the codebase, containing files not matching ignore patterns, combined into a single document by Repomix.
The content has been processed where empty lines have been removed, content has been compressed (code blocks are separated by ⋮---- delimiter).

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
- Files matching these patterns are excluded: **/*.pyc, **/__pycache__/**
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Empty lines have been removed from all files
- Content has been compressed - code blocks are separated by ⋮---- delimiter
- Files are sorted by Git change count (files with more changes are at the bottom)

# Directory Structure
```
parsers/
  broker_csv_parser.py
  cas_parser.py
  models.py
quant/
  analytics_engine.py
app.py
Dockerfile
flight_server.py
requirements.txt
```

# Files

## File: parsers/broker_csv_parser.py
```python
class BrokerCsvParser
⋮----
def __init__(self, csv_path: str, broker_type: str = "generic")
⋮----
def parse(self) -> List[TaxEventSchema]
⋮----
events: List[TaxEventSchema] = []
⋮----
df = pl.read_csv(self.csv_path, infer_schema_length=0)
⋮----
col_map = {str(c).strip().lower(): c for c in df.columns}
⋮----
date_col = next((col_map[k] for k in col_map if any(x in k for x in ["date", "txn_date", "trade_date"])), None)
symbol_col = next((col_map[k] for k in col_map if any(x in k for x in ["symbol", "scheme", "scrip", "asset", "description"])), None)
type_col = next((col_map[k] for k in col_map if any(x in k for x in ["type", "buy/sell", "transaction", "action"])), None)
qty_col = next((col_map[k] for k in col_map if any(x in k for x in ["qty", "quantity", "units"])), None)
price_col = next((col_map[k] for k in col_map if any(x in k for x in ["price", "nav", "rate"])), None)
amount_col = next((col_map[k] for k in col_map if any(x in k for x in ["amount", "value", "total"])), None)
⋮----
asset_name = str(row[symbol_col]) if symbol_col and row.get(symbol_col) else "Broker Asset"
date_str = str(row[date_col]) if date_col and row.get(date_col) else ""
⋮----
event_date = datetime.now().date()
⋮----
event_date = datetime.strptime(date_str.strip(), fmt).date()
⋮----
txn_type_str = str(row[type_col]).upper() if type_col and row.get(type_col) else "BUY"
⋮----
event_type = EventType.DISPOSAL
⋮----
event_type = EventType.BONUS
⋮----
event_type = EventType.SPLIT
⋮----
event_type = EventType.ACQUISITION
⋮----
units_val = row.get(qty_col)
units = Decimal(str(abs(float(units_val)))) if units_val is not None and str(units_val).strip() != "" else Decimal("1")
⋮----
price_val = row.get(price_col)
price = Decimal(str(abs(float(price_val)))) if price_val is not None and str(price_val).strip() != "" else Decimal("0")
⋮----
amt_val = row.get(amount_col)
amount = Decimal(str(abs(float(amt_val)))) if amt_val is not None and str(amt_val).strip() != "" else (units * price)
```

## File: parsers/cas_parser.py
```python
DATE_REGEX = re.compile(r"^(\d{2}-[A-Za-z]{3}-\d{4})\s+(.+)$")
# Added support for both CAMS and KFintech PAN formats in CAS
ISIN_REGEX = re.compile(r"ISIN:\s*([A-Z0-9]{12})", re.IGNORECASE)
TOKEN_REGEX = re.compile(r"\((?:\d{1,3}(?:,\d{3})*|\d+)(?:\.\d+)?\)|\b\d{1,3}(?:,\d{3})*(?:\.\d+)?\b|\b\d+\.\d+\b")
⋮----
class CasPdfParser
⋮----
def __init__(self, pdf_path: str, password: Optional[str] = None)
⋮----
def parse_events(self) -> List[TaxEventSchema]
⋮----
events: List[TaxEventSchema] = []
⋮----
# Try specialized casparser library first
⋮----
data = casparser.read_cas_pdf(self.pdf_path, self.password or "")
⋮----
isin = scheme.isin
scheme_name = scheme.scheme
asset_id = isin or scheme_name.replace(" ", "_").upper()[:20]
⋮----
txn_type_str = str(txn.type).upper()
⋮----
event_type = EventType.DISPOSAL
⋮----
event_type = EventType.BONUS
⋮----
event_type = EventType.SPLIT
⋮----
event_type = EventType.ACQUISITION
⋮----
txn_date = txn.date if isinstance(txn.date, date) else datetime.now().date()
units = Decimal(str(abs(txn.units or 0)))
price = Decimal(str(abs(txn.nav or 0)))
amount = Decimal(str(abs(txn.amount or 0)))
⋮----
amount = units * price
⋮----
# Fallback to pdfplumber regex line parser
⋮----
current_scheme = "Mutual Fund Scheme"
current_isin: Optional[str] = None
⋮----
text = page.extract_text() or ""
⋮----
line_str = line.strip()
⋮----
isin_match = ISIN_REGEX.search(line_str)
⋮----
current_isin = isin_match.group(1)
⋮----
current_scheme = line_str.split(" - ISIN:")[0].split("(Advisor")[0].strip()
⋮----
match = DATE_REGEX.match(line_str)
⋮----
event_date = datetime.strptime(date_str, "%d-%b-%Y").date()
⋮----
event_date = datetime.now().date()
⋮----
num_tokens = TOKEN_REGEX.findall(rest)
⋮----
clean_nums = []
⋮----
is_neg = tok.startswith("(") and tok.endswith(")")
raw_val = tok.replace("(", "").replace(")", "").replace(",", "").strip()
⋮----
val = Decimal(raw_val)
⋮----
val = -val
⋮----
amount = abs(clean_nums[0])
units = abs(clean_nums[1])
price = clean_nums[2]
⋮----
line_upper = rest.upper()
```

## File: parsers/models.py
```python
class EventType(str, Enum)
⋮----
ACQUISITION = "ACQUISITION"
SIP_INSTALMENT = "SIP_INSTALMENT"
DISPOSAL = "DISPOSAL"
BONUS = "BONUS"
SPLIT = "SPLIT"
DIVIDEND_REINVEST = "DIVIDEND_REINVEST"
SGB_INTEREST = "SGB_INTEREST"
SGB_MATURITY = "SGB_MATURITY"
MERGER = "MERGER"
⋮----
class TaxEventSchema(BaseModel)
⋮----
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
⋮----
class Config
⋮----
populate_by_name = True
⋮----
def unit_delta(self) -> Decimal
```

## File: quant/analytics_engine.py
```python
qs = None
⋮----
def compute_fund_analytics(nav_series, dates=None, benchmark_returns=None)
⋮----
valid_pairs = [(nav, d) for nav, d in zip(nav_series, dates) if d]
⋮----
idx = pd.to_datetime(d_str)
s = pd.Series(vals, index=idx)
⋮----
s = pd.Series(nav_series)
⋮----
returns = s.pct_change().dropna()
⋮----
sharpe = float(qs.stats.sharpe(returns))
sortino = float(qs.stats.sortino(returns))
calmar = float(qs.stats.calmar(returns))
max_dd = float(qs.stats.max_drawdown(returns))
vol = float(qs.stats.volatility(returns))
var95 = float(qs.stats.value_at_risk(returns))
cvar95 = float(qs.stats.conditional_value_at_risk(returns))
⋮----
beta = 0.0
⋮----
beta_val = qs.stats.greeks(returns, benchmark_returns).get("beta", 0.0)
beta = float(beta_val) if not np.isnan(beta_val) else 0.0
⋮----
# Vectorized fallback calculation with true Downside Deviation Sortino ratio
mean_ret = returns.mean()
std_ret = returns.std()
sharpe = float((mean_ret / std_ret) * np.sqrt(252)) if std_ret > 0 else 0.0
⋮----
downside_returns = returns[returns < 0]
downside_std = downside_returns.std() if not downside_returns.empty else 0.0
sortino = float((mean_ret / downside_std) * np.sqrt(252)) if downside_std > 0 else sharpe
⋮----
cum_returns = (1 + returns).cumprod()
peak = cum_returns.cummax()
dd = (cum_returns - peak) / peak
max_dd = float(dd.min())
calmar = float(mean_ret * 252 / abs(max_dd)) if abs(max_dd) > 0 else 0.0
vol = float(std_ret * np.sqrt(252))
var95 = float(returns.quantile(0.05))
cvar95 = float(returns[returns <= var95].mean()) if not returns[returns <= var95].empty else var95
```

## File: app.py
```python
# Setup logging
⋮----
logger = logging.getLogger("quant-sidecar")
⋮----
app = FastAPI(title="Portfolio OS Quant & Parser Sidecar", version="3.0.0")
⋮----
@app.get("/health")
def health_check()
⋮----
filename = file.filename or "statement"
ext = os.path.splitext(filename)[1].lower()
⋮----
content = await file.read()
⋮----
tmp_path = tmp.name
⋮----
events = []
⋮----
parser = CasPdfParser(tmp_path, password=password)
events = parser.parse_events()
⋮----
parser = BrokerCsvParser(tmp_path)
events = parser.parse()
⋮----
# Polars multi-threaded dataframe verification
⋮----
df = pl.DataFrame([e.model_dump(by_alias=True) for e in events])
required_cols = ["id", "assetId", "assetName", "eventType", "eventDate", "units", "grossAmount"]
⋮----
def run_flight_server()
⋮----
server = QuantFlightServer("0.0.0.0", 8001)
⋮----
# Start Apache Arrow Flight RPC Server in a background daemon thread
flight_thread = threading.Thread(target=run_flight_server, daemon=True)
⋮----
# Run FastAPI server
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
logger = logging.getLogger(__name__)
⋮----
class QuantFlightServer(flight.FlightServerBase)
⋮----
def __init__(self, host="0.0.0.0", port=8001, **kwargs)
⋮----
location = flight.Location.for_grpc_tcp(host, port)
⋮----
def do_exchange(self, context, descriptor, reader, writer)
⋮----
table = reader.read_all()
⋮----
df = pl.from_arrow(table)
results = []
unique_codes = df["amfi_code"].unique().to_list()
⋮----
fund_df = df.filter(pl.col("amfi_code") == code)
nav_values = fund_df["nav_value"].to_list()
dates_list = fund_df["nav_date"].to_list() if "nav_date" in fund_df.columns else None
⋮----
analytics = compute_fund_analytics(nav_values, dates=dates_list)
⋮----
out_df = pl.DataFrame(results)
out_table = out_df.to_arrow()
⋮----
def _write_empty_response(self, writer)
⋮----
schema = pa.schema([
out_table = pa.Table.from_batches([], schema)
⋮----
def start_flight_server(host="0.0.0.0", port=8001)
⋮----
server = QuantFlightServer(host, port)
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
numpy>=1.26.0
scipy>=1.12.0
yfinance>=0.2.37
pandas>=2.2.0
quantstats>=0.0.62
pydantic>=2.6.0
python-multipart>=0.0.9
```
