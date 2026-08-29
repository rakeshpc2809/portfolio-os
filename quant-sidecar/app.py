import os
import tempfile
import threading
import logging
from typing import List, Optional
from fastapi import FastAPI, File, Form, UploadFile, HTTPException, Header, Depends
from pydantic import BaseModel
import polars as pl
import uvicorn

import asyncio
from parsers.cas_parser import CasPdfParser
from parsers.broker_csv_parser import BrokerCsvParser
from parsers.sip_detector import detect_and_tag_sips
from parsers.models import TaxEventSchema
from flight_server import QuantFlightServer
from quant.analytics_engine import (
    run_monte_carlo_fire_simulation,
    compute_benchmark_analytics,
    FireSimulationResponse,
    BenchmarkAnalyticsResponse
)

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("quant-sidecar")

import secrets

EXPECTED_AUTH_TOKEN = os.getenv("API_AUTH_TOKEN")

def verify_auth_token(x_api_auth_token: Optional[str] = Header(None)):
    token = EXPECTED_AUTH_TOKEN or "dev_secret_key_123"
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

    def _do_parse(path: str, file_ext: str, pwd: Optional[str]) -> List[TaxEventSchema]:
        if file_ext == ".pdf":
            parser = CasPdfParser(path, password=pwd)
            raw_events = parser.parse_events()
        elif file_ext == ".csv":
            parser = BrokerCsvParser(path)
            raw_events = parser.parse()
        else:
            raise ValueError("Unsupported file format. Please upload PDF or CSV.")

        tagged_events = detect_and_tag_sips(raw_events)
        if tagged_events:
            df = pl.DataFrame([e.model_dump(by_alias=True) for e in tagged_events])
            required_cols = ["id", "assetId", "assetName", "eventType", "eventDate", "units", "grossAmount"]
            for col in required_cols:
                if col not in df.columns:
                    raise ValueError(f"Missing column in parsed dataframe: {col}")
        return tagged_events

    try:
        events = await asyncio.to_thread(_do_parse, tmp_path, ext, password)
        logger.info(f"Successfully parsed {len(events)} events from statement")
        return events
    except Exception as err:
        logger.error(f"Error parsing statement: {err}", exc_info=True)
        status_code = 400 if isinstance(err, ValueError) else 500
        raise HTTPException(status_code=status_code, detail=str(err))
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

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

@app.post("/api/v1/simulate_fire", response_model=FireSimulationResponse, dependencies=[Depends(verify_auth_token)])
async def simulate_fire(req: FireSimulationRequest):
    return await asyncio.to_thread(
        run_monte_carlo_fire_simulation,
        daily_returns_list=req.daily_returns,
        current_corpus=req.current_corpus,
        annual_expense=req.annual_expense,
        monthly_contribution=req.monthly_contribution,
        years_to_retirement=req.years_to_retirement,
        num_simulations=req.num_simulations
    )

@app.post("/api/v1/analytics/benchmark", response_model=BenchmarkAnalyticsResponse, dependencies=[Depends(verify_auth_token)])
async def analyze_benchmark(req: BenchmarkAnalyticsRequest):
    result = await asyncio.to_thread(
        compute_benchmark_analytics,
        portfolio_returns=req.portfolio_returns,
        benchmark_returns=req.benchmark_returns,
        benchmark_name=req.benchmark_name
    )
    if result.get("status") == "ERROR":
        raise HTTPException(status_code=422, detail=result.get("message", "Benchmark analytics computation error"))
    return result

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
