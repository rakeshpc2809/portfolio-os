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
