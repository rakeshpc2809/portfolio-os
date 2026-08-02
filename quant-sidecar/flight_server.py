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
