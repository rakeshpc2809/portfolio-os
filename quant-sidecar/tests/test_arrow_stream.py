import os
import unittest
import pyarrow as pa

# Ensure environment variable for auth
os.environ["API_AUTH_TOKEN"] = "dev_secret_key_123"

from app import get_nav_arrow_stream, stream_nav_analytics
from quant.analytics_engine import (
    serialize_arrow_stream,
    deserialize_arrow_stream,
    nav_records_to_arrow_stream,
    compute_arrow_stream_metrics
)


class MockRequest:
    def __init__(self, body_bytes: bytes):
        self._body = body_bytes

    async def body(self):
        return self._body


class TestArrowStreamIPC(unittest.IsolatedAsyncioTestCase):

    def test_roundtrip_arrow_serialization(self):
        schema = pa.schema([
            ("isin", pa.string()),
            ("nav_date", pa.string()),
            ("nav_value", pa.float64())
        ])
        table = pa.Table.from_arrays(
            [
                pa.array(["INF109KC12U0", "INF109KC12U0"]),
                pa.array(["2026-01-01", "2026-01-02"]),
                pa.array([100.0, 101.5])
            ],
            schema=schema
        )
        stream_bytes = serialize_arrow_stream(table)
        self.assertTrue(len(stream_bytes) > 0)

        restored_table = deserialize_arrow_stream(stream_bytes)
        self.assertEqual(restored_table.num_rows, 2)
        self.assertEqual(restored_table.column_names, ["isin", "nav_date", "nav_value"])

    def test_nav_records_to_arrow_stream_empty_and_populated(self):
        empty_bytes = nav_records_to_arrow_stream([])
        t_empty = deserialize_arrow_stream(empty_bytes)
        self.assertEqual(t_empty.num_rows, 0)

        records = [
            {"isin": "INF879O01027", "nav_date": "2026-03-01", "nav_value": 75.25},
            {"isin": "INF879O01027", "nav_date": "2026-03-02", "nav_value": 75.80}
        ]
        pop_bytes = nav_records_to_arrow_stream(records)
        t_pop = deserialize_arrow_stream(pop_bytes)
        self.assertEqual(t_pop.num_rows, 2)

    def test_compute_arrow_stream_metrics(self):
        records = []
        base_nav = 100.0
        for i in range(40):
            base_nav += 0.5 * (1 if i % 2 == 0 else -0.3)
            records.append({
                "isin": "INF109KC12U0",
                "nav_date": f"2026-01-{i+1:02d}" if i < 30 else f"2026-02-{i-29:02d}",
                "nav_value": base_nav
            })
        input_stream = nav_records_to_arrow_stream(records)
        output_stream = compute_arrow_stream_metrics(input_stream)
        self.assertTrue(len(output_stream) > 0)

        metrics_table = deserialize_arrow_stream(output_stream)
        self.assertEqual(metrics_table.num_rows, 1)
        self.assertIn("sharpe", metrics_table.column_names)
        self.assertIn("cvar_95", metrics_table.column_names)
        self.assertIn("isin", metrics_table.column_names)

    def test_get_nav_arrow_stream_endpoint(self):
        resp = get_nav_arrow_stream(limit=5)
        self.assertEqual(resp.media_type, "application/vnd.apache.arrow.stream")
        table = deserialize_arrow_stream(resp.body)
        self.assertTrue(table.num_rows <= 5)

    def test_get_nav_arrow_stream_route_enforces_auth_dependency(self):
        from app import app
        route = next((r for r in app.routes if getattr(r, "path", None) == "/api/v1/analytics/nav_arrow_stream" and "GET" in getattr(r, "methods", set())), None)
        self.assertIsNotNone(route)
        self.assertTrue(len(route.dependencies) > 0, "GET /api/v1/analytics/nav_arrow_stream must enforce authentication dependency")

    async def test_post_nav_arrow_stream_endpoint(self):
        records = []
        base_nav = 50.0
        for i in range(35):
            base_nav += 0.2
            records.append({
                "isin": "INF204K01K15",
                "nav_date": f"2026-01-{i+1:02d}" if i < 30 else f"2026-02-{i-29:02d}",
                "nav_value": base_nav
            })
        input_stream = nav_records_to_arrow_stream(records)
        mock_req = MockRequest(input_stream)
        resp = await stream_nav_analytics(mock_req)
        self.assertEqual(resp.media_type, "application/vnd.apache.arrow.stream")
        table = deserialize_arrow_stream(resp.body)
        self.assertEqual(table.num_rows, 1)
        row = table.to_pylist()[0]
        self.assertEqual(row["isin"], "INF204K01K15")
        self.assertEqual(row["data_points"], 35)


if __name__ == "__main__":
    unittest.main()
