#!/usr/bin/env python3
import time
import urllib.request
import sys

print("[*] Waiting for Portfolio OS services to become healthy...")
ready_quant = False
ready_core = False

for i in range(120):  # up to 12s
    if not ready_quant:
        try:
            with urllib.request.urlopen("http://127.0.0.1:8000/health", timeout=0.5) as r:
                if r.status == 200:
                    ready_quant = True
                    print("  [+] Quant Sidecar: HEALTHY (http://127.0.0.1:8000/health)")
        except Exception:
            pass

    if not ready_core:
        try:
            # Check port 8080 (returns 200 on index.html or 401 on protected endpoints)
            with urllib.request.urlopen("http://127.0.0.1:8080/", timeout=0.5) as r:
                if r.status in (200, 401, 404):
                    ready_core = True
                    print("  [+] Core Node: HEALTHY (http://127.0.0.1:8080/)")
        except Exception:
            pass

    if ready_quant and ready_core:
        print("[SUCCESS] All Portfolio OS services are healthy.")
        sys.exit(0)

    time.sleep(0.1)

print("[ERROR] Timed out waiting for services to become healthy.", file=sys.stderr)
sys.exit(1)
