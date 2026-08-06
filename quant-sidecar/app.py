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
