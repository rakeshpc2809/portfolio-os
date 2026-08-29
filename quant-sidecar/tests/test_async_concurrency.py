import unittest
import time
import json
import urllib.request
import concurrent.futures

class TestAsyncConcurrency(unittest.TestCase):
    def test_health_check_non_blocking_during_monte_carlo(self):
        """
        Verify that a 10,000-simulation Monte Carlo request on /api/v1/simulate_fire
        does NOT block the event loop, and concurrent /health calls respond with < 25ms latency.
        """
        base_url = "http://127.0.0.1:8000"
        
        # Verify sidecar is reachable
        try:
            req = urllib.request.Request(f"{base_url}/health")
            with urllib.request.urlopen(req, timeout=2.0) as resp:
                self.assertEqual(resp.status, 200)
        except Exception as e:
            self.skipTest(f"Sidecar server not running on {base_url}: {e}")

        payload = json.dumps({
            "daily_returns": [0.0005] * 252,
            "current_corpus": 1000000.0,
            "annual_expense": 600000.0,
            "monthly_contribution": 50000.0,
            "years_to_retirement": 15,
            "num_simulations": 10000
        }).encode("utf-8")

        headers = {
            "Content-Type": "application/json",
            "X-Api-Auth-Token": "dev_secret_key_123"
        }

        health_latencies = []

        def run_heavy_simulation():
            sim_req = urllib.request.Request(f"{base_url}/api/v1/simulate_fire", data=payload, headers=headers)
            t0 = time.perf_counter()
            with urllib.request.urlopen(sim_req, timeout=30.0) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            elapsed = time.perf_counter() - t0
            return elapsed, data

        def probe_health():
            t0 = time.perf_counter()
            h_req = urllib.request.Request(f"{base_url}/health")
            with urllib.request.urlopen(h_req, timeout=5.0) as resp:
                resp.read()
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            return elapsed_ms

        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as executor:
            # Launch heavy Monte Carlo in background thread
            sim_future = executor.submit(run_heavy_simulation)
            
            # Wait 30ms to ensure the simulation is actively computing in its worker thread
            time.sleep(0.03)
            
            # Fire 10 concurrent health checks while the simulation is actively executing
            health_futures = [executor.submit(probe_health) for _ in range(10)]
            
            for hf in health_futures:
                latency = hf.result()
                health_latencies.append(latency)

            sim_elapsed, sim_data = sim_future.result()

        print(f"\n[CONCURRENCY TIMING RESULTS]")
        print(f" - Heavy Monte Carlo (10k sims) Wall-Clock Duration: {sim_elapsed*1000.0:.2f} ms")
        print(f" - Concurrent /health Latencies (10 probes while sim in-flight): {[f'{l:.2f}ms' for l in health_latencies]}")
        print(f" - Max /health Latency: {max(health_latencies):.2f} ms")
        print(f" - Average /health Latency: {sum(health_latencies)/len(health_latencies):.2f} ms")

        # Confirm simulation succeeded
        self.assertEqual(sim_data["status"], "OK")
        self.assertEqual(sim_data["num_simulations"], 10000)
        
        # Verify non-blocking latency: all health checks must complete rapidly (<25ms)
        # If the event loop were blocked, health checks would have queued behind the ~1.5s simulation.
        for latency in health_latencies:
            self.assertLess(latency, 25.0, f"Health check blocked by event loop! Latency: {latency:.2f}ms")

if __name__ == "__main__":
    unittest.main()
