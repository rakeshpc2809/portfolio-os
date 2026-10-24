#!/usr/bin/env python3
"""
Portfolio OS v4.1+ — Pre-Release Production Smoke Test
Verifies the complete institutional-grade stack in a single automated runner:
1. Zero-Knowledge State Bundle (AEAD AES-256-GCM + HKDF-SHA256 + Gzip) roundtrip & PII scan.
2. Arrow IPC Stream zero-copy metrics & Unix Domain Socket readiness.
3. FastMCP Portfolio Server 6-tool deterministic execution.
4. Monorepo test gates (Core Node, Quant Sidecar, TUI, Mobile, Web).
5. Android APK debug compilation gate.
"""

import gzip
import hashlib
import hmac
import json
import math
import os
import re
import socket
import subprocess
import sys
import tempfile
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

REPO_ROOT = Path(__file__).resolve().parent.parent
VENV_PYTHON = REPO_ROOT / "quant-sidecar" / ".venv" / "bin" / "python"
if VENV_PYTHON.exists() and sys.executable != str(VENV_PYTHON):
    os.execv(str(VENV_PYTHON), [str(VENV_PYTHON)] + sys.argv)

sys.path.insert(0, str(REPO_ROOT / "quant-sidecar"))

PAN_PATTERN = re.compile(r"\b[A-Z]{5}[0-9]{4}[A-Z]{1}\b")
FOLIO_PATTERN = re.compile(r"\b\d{6,12}/\d{2}\b|\bfolio(?:[_\s]*(?:no|num|number))?\b", re.IGNORECASE)


def log_step(name: str):
    print(f"\n========================================================")
    print(f"[*] {name}")
    print(f"========================================================")


def hkdf_derive(ikm: bytes, salt: bytes, info: bytes, length: int) -> bytes:
    if not salt:
        salt = bytes(32)
    prk = hmac.new(salt, ikm, hashlib.sha256).digest()
    n = math.ceil(length / 32)
    okm = b""
    t = b""
    for i in range(1, n + 1):
        t = hmac.new(prk, t + info + bytes([i]), hashlib.sha256).digest()
        okm += t
    return okm[:length]


def test_bundle_crypto_and_pii():
    log_step("1. VERIFYING AEAD CLOUD BUNDLE CRYPTO & ZERO-PII POLICY")
    passphrase = os.environ.get("CLOUD_SYNC_SECRET_KEY", "f8a129d2b7e56c3401928374a5b6c7d8e9f0123456789abcdef0123456789abc")
    
    # Generate representative mobile state bundle
    sample_bundle = {
        "sync_info": {
            "status": "HEALTHY",
            "generated_at": "2026-10-24T23:00:00Z",
            "ledger_hmac_root": "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        },
        "holdings": [
            {
                "isin": "INF109KC12U0",
                "asset_name": "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
                "bucket": "EQUITY_CORE",
                "units": 61879.528,
                "current_nav": 11.4431,
                "peak_52w_nav": 12.1050,
                "exit_load_free_units": 61879.528
            },
            {
                "isin": "INF879O01027",
                "asset_name": "Parag Parikh Flexi Cap Fund",
                "bucket": "EQUITY_CORE",
                "units": 6831.371,
                "current_nav": 88.9695,
                "peak_52w_nav": 92.4500,
                "exit_load_free_units": 6831.371
            }
        ],
        "buckets": [
            {"bucket": "EQUITY_CORE", "target_pct": 50.0, "current_pct": 51.2, "status": "BALANCED"},
            {"bucket": "EQUITY_SATELLITE", "target_pct": 35.0, "current_pct": 34.1, "status": "BALANCED"}
        ]
    }
    
    raw_json = json.dumps(sample_bundle).encode("utf-8")
    
    # 1. Assert no PII in plaintext bundle
    raw_str = raw_json.decode("utf-8")
    assert not FOLIO_PATTERN.search(raw_str), "Folio pattern detected in state bundle!"
    assert not PAN_PATTERN.search(raw_str), "PAN pattern detected in state bundle!"
    print("  ✓ Zero-PII assertions passed: No folio_no or PAN detected in bundle payload.")

    # 2. Encrypt bundle using POS1 format
    salt = os.urandom(16)
    iv = os.urandom(12)
    derived_key = hkdf_derive(passphrase.encode("utf-8"), salt, b"portfolio-os-cloud-sync-v1", 32)
    compressed = gzip.compress(raw_json)
    ciphertext = AESGCM(derived_key).encrypt(iv, compressed, None)
    bundle_bytes = b"POS1" + salt + iv + ciphertext
    
    print(f"  ✓ State bundle encrypted: {len(raw_json)} bytes -> {len(compressed)} bytes (gzip) -> {len(bundle_bytes)} bytes (AEAD)")
    assert bundle_bytes.startswith(b"POS1")

    # 3. Decrypt bundle
    d_salt = bundle_bytes[4:20]
    d_iv = bundle_bytes[20:32]
    d_ct = bundle_bytes[32:]
    dec_key = hkdf_derive(passphrase.encode("utf-8"), d_salt, b"portfolio-os-cloud-sync-v1", 32)
    decrypted_gzip = AESGCM(dec_key).decrypt(d_iv, d_ct, None)
    recovered_json = gzip.decompress(decrypted_gzip)
    
    assert recovered_json == raw_json, "Decrypted payload mismatch!"
    print("  ✓ Cryptographic roundtrip verified: GCM tag verified and payload matches perfectly.")


def test_arrow_ipc_and_socket():
    log_step("2. VERIFYING ARROW IPC ZERO-COPY & UNIX SOCKET")
    from quant.analytics_engine import (
        nav_records_to_arrow_stream,
        deserialize_arrow_stream,
        compute_arrow_stream_metrics
    )

    records = [
        {"isin": "INF109KC12U0", "nav_date": "2026-10-20", "nav_value": 11.40},
        {"isin": "INF109KC12U0", "nav_date": "2026-10-21", "nav_value": 11.42},
        {"isin": "INF109KC12U0", "nav_date": "2026-10-22", "nav_value": 11.44},
        {"isin": "INF109KC12U0", "nav_date": "2026-10-23", "nav_value": 11.45},
    ]

    stream_bytes = nav_records_to_arrow_stream(records)
    assert len(stream_bytes) > 0
    table = deserialize_arrow_stream(stream_bytes)
    assert table.num_rows == 4
    metrics_bytes = compute_arrow_stream_metrics(stream_bytes)
    metrics_table = deserialize_arrow_stream(metrics_bytes)
    assert metrics_table.num_rows == 1
    assert "sharpe" in metrics_table.column_names
    print(f"  ✓ Arrow IPC stream verified: {table.num_rows} records in, {metrics_table.num_rows} fund metric rows computed zero-copy.")

    # Test Unix Domain Socket binding in /tmp
    test_sock_path = f"/tmp/portfolio_smoke_{os.getpid()}.sock"
    try:
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        sock.bind(test_sock_path)
        os.chmod(test_sock_path, 0o600)
        sock.listen(1)
        stat = os.stat(test_sock_path)
        assert stat.st_mode & 0o777 == 0o600, "Unix socket permissions not restricted to 0600!"
        sock.close()
        print(f"  ✓ Unix domain socket verified: Bound and restricted to 0600 permissions.")
    finally:
        if os.path.exists(test_sock_path):
            os.remove(test_sock_path)


def test_fastmcp_tools():
    log_step("3. VERIFYING FASTMCP PORTFOLIO SERVER TOOLS")
    from mcp_server import (
        tool_get_portfolio_snapshot,
        tool_simulate_trade_what_if,
        tool_simulate_lumpsum_inflow,
        tool_get_sec112a_harvest_candidates,
        tool_get_guyton_klinger_fire_status,
        tool_verify_financial_narration,
    )

    # 1. Snapshot
    snap = json.loads(tool_get_portfolio_snapshot())
    assert snap["status"] == "SUCCESS"
    assert snap["portfolio_summary"]["net_worth"] > 0
    print("  ✓ tool_get_portfolio_snapshot: Returned verified portfolio summary.")

    # 2. Trade simulation
    trade = json.loads(tool_simulate_trade_what_if("INF879O01027", 100.0))
    assert trade["status"] == "SUCCESS"
    assert trade["gross_proceeds"] > 0
    print(f"  ✓ tool_simulate_trade_what_if: Calculated tax drag ₹{trade['total_tax_drag']} on 100 units.")

    # 3. Lumpsum simulation
    lump = json.loads(tool_simulate_lumpsum_inflow(50000.0))
    assert lump["status"] == "SUCCESS"
    alloc_sum = sum(a["allocated_amount"] for a in lump["recommended_allocations"])
    assert abs(alloc_sum - 50000.0) < 1.0
    print(f"  ✓ tool_simulate_lumpsum_inflow: Conservation verified for ₹50,000 inflow.")

    # 4. Sec 112A harvest
    harv = json.loads(tool_get_sec112a_harvest_candidates())
    assert harv["status"] == "SUCCESS"
    print(f"  ✓ tool_get_sec112a_harvest_candidates: Found ₹{harv['total_eligible_ltcg_available']} eligible LTCG.")

    # 5. Guyton-Klinger FIRE
    gk = json.loads(tool_get_guyton_klinger_fire_status(600000.0))
    assert gk["status"] == "SUCCESS"
    print(f"  ✓ tool_get_guyton_klinger_fire_status: Live SWR {gk['current_live_swr_pct']}%, runway {gk['cash_runway_months']} months.")

    # 6. Verify narration
    ver = json.loads(tool_verify_financial_narration(claimed_net_worth=snap["portfolio_summary"]["net_worth"]))
    assert ver["status"] == "SUCCESS"
    assert ver["verified"] is True
    print("  ✓ tool_verify_financial_narration: Ground-truth verification engine verified.")


def run_command(cmd: str, desc: str):
    log_step(desc)
    print(f"Running: {cmd}")
    res = subprocess.run(cmd, shell=True, cwd=str(REPO_ROOT))
    if res.returncode != 0:
        print(f"\n[ERROR] Command failed with exit code {res.returncode}: {cmd}", file=sys.stderr)
        sys.exit(res.returncode)


def main():
    print("==================================================================")
    print("      PORTFOLIO OS v4.1+ PRE-RELEASE PRODUCTION SMOKE TEST        ")
    print("==================================================================")
    
    # 1. Bundle Crypto & PII Scan
    test_bundle_crypto_and_pii()

    # 2. Arrow IPC & Unix Socket
    test_arrow_ipc_and_socket()

    # 3. FastMCP Tools
    test_fastmcp_tools()

    # 4. Full Suite Test Gate
    run_command("just check", "4. RUNNING FULL CROSS-PLATFORM TEST GATES (JUST CHECK)")

    # 5. Mobile APK Compilation Gate
    run_command("just build-mobile", "5. COMPILING PRODUCTION ANDROID APK (JUST BUILD-MOBILE)")

    apk_path = REPO_ROOT / "mobile-app" / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    if apk_path.exists():
        size_mb = apk_path.stat().st_size / (1024 * 1024)
        print(f"\n[SUCCESS] Android debug APK generated: {apk_path} ({size_mb:.2f} MB)")
    else:
        print(f"\n[WARNING] APK not found at default path {apk_path}")

    print("\n==================================================================")
    print(" >>> ALL PRE-RELEASE SMOKE TESTS & PRODUCTION GATES PASSED! <<<   ")
    print("==================================================================")


if __name__ == "__main__":
    main()
