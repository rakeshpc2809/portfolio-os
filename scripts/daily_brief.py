#!/usr/bin/env python3
"""
Portfolio OS — Daily Executive Brief Generator
Uses Ollama (phi4-mini) with strict pre-formatted grounding strings,
hard-gated verification, and automatic model unloading (keep_alive: 0).
"""

from datetime import datetime
import json
import os
from pathlib import Path
import re
import sys
import urllib.request
from typing import Any, Dict, List, Optional, Tuple

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434")
CORE_NODE_URL = os.getenv("CORE_NODE_URL", "http://127.0.0.1:8080")
AUTH_TOKEN = os.getenv("API_AUTH_TOKEN", "dev_secret_key_123")
MODEL_NAME = os.getenv("BRIEF_MODEL", "phi4-mini")


def inr_format(val: float) -> str:
    """Canonical Indian numbering format (e.g. ₹48,75,200.00)."""
    sign = "-" if val < 0 else ""
    val = abs(val)
    int_part = int(val)
    frac_part = f"{val - int_part:.2f}"[1:]
    s = str(int_part)
    if len(s) <= 3:
        return f"{sign}₹{s}{frac_part}"
    last3 = s[-3:]
    leading = s[:-3]
    chunks = []
    while len(leading) > 2:
        chunks.append(leading[-2:])
        leading = leading[:-2]
    if leading:
        chunks.append(leading)
    chunks.reverse()
    return f"{sign}₹{','.join(chunks)},{last3}{frac_part}"


def pct_format(val: float, include_sign: bool = False) -> str:
    """Canonical percentage display string."""
    prefix = "+" if (include_sign and val > 0) else ""
    return f"{prefix}{val:.2f}%"


def generate_mock_or_offline_snapshot() -> Dict[str, Any]:
    """Generates snapshot payload with dual value/display fields for grounding and verification."""
    today = datetime.now().strftime("%Y-%m-%d")

    # Read HRP audit cache if present
    hrp_audit = None
    hrp_path = Path(__file__).resolve().parent.parent / "data" / "hrp_audit.json"
    if hrp_path.exists():
        try:
            with open(hrp_path, "r") as f:
                hrp_audit = json.load(f)
        except Exception:
            pass

    net_worth = 4875200.0
    invested = 3650000.0
    gain = net_worth - invested
    day_chg = -24300.0
    day_pct = -0.50
    xirr = 18.42
    ltcg_allowed = 125000.0
    ltcg_used = 45000.0
    ltcg_rem = ltcg_allowed - ltcg_used
    fire_target = 30000000.0
    fire_progress = (net_worth / fire_target) * 100.0
    years_to_fi = 9.4

    buckets = [
        {
            "bucket": "CORE",
            "current_val": {"value": 2340000.0, "display": inr_format(2340000.0)},
            "current_pct": {"value": 48.0, "display": pct_format(48.0)},
            "target_pct": {"value": 50.0, "display": pct_format(50.0)},
            "drift_pct": {"value": -2.0, "display": pct_format(-2.0, include_sign=True)},
            "status": "IN_BAND",
        },
        {
            "bucket": "SATELLITE",
            "current_val": {"value": 1560000.0, "display": inr_format(1560000.0)},
            "current_pct": {"value": 32.0, "display": pct_format(32.0)},
            "target_pct": {"value": 30.0, "display": pct_format(30.0)},
            "drift_pct": {"value": 2.0, "display": pct_format(2.0, include_sign=True)},
            "status": "IN_BAND",
        },
        {
            "bucket": "GOLD_SILVER",
            "current_val": {"value": 438768.0, "display": inr_format(438768.0)},
            "current_pct": {"value": 9.0, "display": pct_format(9.0)},
            "target_pct": {"value": 10.0, "display": pct_format(10.0)},
            "drift_pct": {"value": -1.0, "display": pct_format(-1.0, include_sign=True)},
            "status": "IN_BAND",
        },
        {
            "bucket": "LIQUID_BUFFER",
            "current_val": {"value": 536432.0, "display": inr_format(536432.0)},
            "current_pct": {"value": 11.0, "display": pct_format(11.0)},
            "target_pct": {"value": 10.0, "display": pct_format(10.0)},
            "drift_pct": {"value": 1.0, "display": pct_format(1.0, include_sign=True)},
            "status": "IN_BAND",
        },
    ]

    snapshot = {
        "as_of_date": today,
        "net_worth": {"value": net_worth, "display": inr_format(net_worth)},
        "invested_capital": {"value": invested, "display": inr_format(invested)},
        "total_gain": {"value": gain, "display": inr_format(gain)},
        "day_change": {"value": day_chg, "display": inr_format(day_chg)},
        "day_change_pct": {"value": day_pct, "display": pct_format(day_pct, include_sign=True)},
        "overall_xirr": {"value": xirr, "display": pct_format(xirr)},
        "financial_year": "2026-27",
        "ltcg_budget": {
            "total_allowed": {"value": ltcg_allowed, "display": inr_format(ltcg_allowed)},
            "harvested_ytd": {"value": ltcg_used, "display": inr_format(ltcg_used)},
            "remaining_allowance": {"value": ltcg_rem, "display": inr_format(ltcg_rem)},
            "status": "HEALTHY",
        },
        "buckets": buckets,
        "rebalance_action": "NO_OP (All buckets within +/- 5% corridor)",
        "fire_tracker": {
            "target_corpus": {"value": fire_target, "display": inr_format(fire_target)},
            "progress_pct": {"value": round(fire_progress, 2), "display": pct_format(fire_progress)},
            "swr_pct": {"value": 3.5, "display": "3.5%"},
            "projected_years_to_fi": {"value": years_to_fi, "display": f"{years_to_fi} years"},
        },
    }

    if hrp_audit:
        snapshot["hrp_audit_summary"] = {
            "last_audit_date": hrp_audit.get("audit_as_of", "N/A"),
            "trading_days": hrp_audit.get("trading_days", 0),
            "core_hrp_pct": hrp_audit.get("bucket_summary", {}).get("CORE", {}).get("hrp_pct", 7.5),
            "arbitrage_hrp_pct": hrp_audit.get("bucket_summary", {}).get("LIQUID_BUFFER", {}).get("hrp_pct", 82.0),
        }

    return snapshot


def fetch_live_snapshot() -> Optional[Dict[str, Any]]:
    """Attempts to pull live snapshot from running core-node."""
    url = f"{CORE_NODE_URL}/api/v1/sync/snapshot?fy=2026-27"
    req = urllib.request.Request(url, headers={"X-Api-Auth-Token": AUTH_TOKEN})
    try:
        with urllib.request.urlopen(req, timeout=3) as resp:
            if resp.status == 200:
                data = json.loads(resp.read().decode())
                # Format into structured dual payload
                sync_info = data.get("sync_info", {})
                nw = float(sync_info.get("current_value", 0.0))
                inv = float(sync_info.get("total_invested", 0.0))
                gain = float(sync_info.get("unrealized_gain", 0.0))
                xirr_raw = float(str(sync_info.get("portfolio_xirr", sync_info.get("xirr_percentage", "0.0"))).replace("%", "").strip() or 0.0)

                rebalance_plan = data.get("rebalance_plan", {})
                buckets_raw = rebalance_plan.get("buy_side", {}).get("buckets", [])
                buckets = []
                for b in buckets_raw:
                    raw_b_name = b.get("bucket", "UNKNOWN")
                    # Canonicalize bucket naming (e.g. EQUITY_CORE -> CORE)
                    b_name = "CORE" if "CORE" in raw_b_name else ("SATELLITE" if "SATELLITE" in raw_b_name else raw_b_name)
                    c_pct = float(b.get("current_pct", 0.0))
                    t_pct = float(b.get("target_pct", 0.0))
                    d_pct = round(c_pct - t_pct, 2)
                    c_val = round(nw * (c_pct / 100.0), 2)
                    buckets.append({
                        "bucket": b_name,
                        "current_val": {"value": c_val, "display": inr_format(c_val)},
                        "current_pct": {"value": c_pct, "display": pct_format(c_pct)},
                        "target_pct": {"value": t_pct, "display": pct_format(t_pct)},
                        "drift_pct": {"value": d_pct, "display": pct_format(d_pct, include_sign=True)},
                        "status": "IN_BAND" if abs(d_pct) <= 5.0 else "TRIGGERED",
                    })

                tax = rebalance_plan.get("sell_side", {}).get("tax_summary", {})
                rem_ltcg = float(tax.get("exemption_headroom_after", 105590.36))
                used_ltcg = float(tax.get("total_ltcg_exempt", 19409.64))

                # Rebalance Trigger Evaluation:
                # Core-Node's RebalanceTriggerDto exposes: type (DRAWDOWN, DRIFT, SCHEDULED, NONE),
                # reasonCode, and reasonLabel (e.g. "Bucket Allocation Drift Exceeded Threshold (...)").
                trigger_info = rebalance_plan.get("trigger", {})
                trigger_type = trigger_info.get("type", "NONE")
                reason_label = trigger_info.get("reasonLabel", "")
                triggered_buckets = [b["bucket"] for b in buckets if b["status"] == "TRIGGERED"]

                if trigger_type not in ["NONE", ""] and reason_label:
                    action_str = f"{trigger_type}: {reason_label}"
                elif triggered_buckets:
                    action_str = f"REBALANCE_TRIGGERED: Corridors breached for {', '.join(triggered_buckets)}"
                else:
                    action_str = "NO_OP (All buckets within +/- 5% corridor)"

                return {
                    "as_of_date": datetime.now().strftime("%Y-%m-%d"),
                    "net_worth": {"value": nw, "display": inr_format(nw)},
                    "invested_capital": {"value": inv, "display": inr_format(inv)},
                    "total_gain": {"value": gain, "display": inr_format(gain)},
                    # NOTE ON DAY CHANGE: Core-Node's SyncSnapshot does not track intraday/1-day delta
                    # because Indian Mutual Fund NAVs are settled EOD by AMFI post-market close.
                    # Zero (₹0.00) indicates no intraday tick data, not a calculation bug.
                    "day_change": {"value": 0.0, "display": inr_format(0.0)},
                    "day_change_pct": {"value": 0.0, "display": "0.00%"},
                    "overall_xirr": {"value": xirr_raw, "display": pct_format(xirr_raw)},
                    "financial_year": "2026-27",
                    "ltcg_budget": {
                        "total_allowed": {"value": 125000.0, "display": "₹1,25,000.00"},
                        "harvested_ytd": {"value": used_ltcg, "display": inr_format(used_ltcg)},
                        "remaining_allowance": {"value": rem_ltcg, "display": inr_format(rem_ltcg)},
                        "status": "HEALTHY",
                    },
                    "buckets": buckets,
                    "rebalance_action": action_str,
                    "fire_tracker": {
                        "target_corpus": {"value": 30000000.0, "display": "₹3,00,00,000.00"},
                        "progress_pct": {"value": round((nw / 30000000.0) * 100, 2), "display": f"{(nw / 30000000.0) * 100:.2f}%"},
                        "swr_pct": {"value": 3.5, "display": "3.5%"},
                        "projected_years_to_fi": {"value": 9.4, "display": "9.4 years"},
                    },
                }
    except Exception:
        pass
    return None


def extract_expected_numbers(snapshot: Dict[str, Any]) -> Dict[str, float]:
    """Flattens all expected numbers into a mapping of {context_key: numeric_value}."""
    registry = {}
    registry["net_worth"] = snapshot["net_worth"]["value"]
    registry["invested_capital"] = snapshot["invested_capital"]["value"]
    registry["day_change"] = snapshot["day_change"]["value"]
    registry["day_change_pct"] = snapshot["day_change_pct"]["value"]
    registry["overall_xirr"] = snapshot["overall_xirr"]["value"]
    registry["ltcg_remaining"] = snapshot["ltcg_budget"]["remaining_allowance"]["value"]

    for b in snapshot["buckets"]:
        name = b["bucket"]
        registry[f"{name}_val"] = b["current_val"]["value"]
        registry[f"{name}_target"] = b["target_pct"]["value"]
        registry[f"{name}_current"] = b["current_pct"]["value"]
        registry[f"{name}_drift"] = b["drift_pct"]["value"]

    registry["fire_progress"] = snapshot["fire_tracker"]["progress_pct"]["value"]
    registry["fire_years"] = snapshot["fire_tracker"]["projected_years_to_fi"]["value"]
    return registry


def verify_narration(output_text: str, expected_numbers: Dict[str, float]) -> Tuple[bool, List[str]]:
    """
    Hard-gated verifier:
    1. Unescapes raw unicode (e.g. \\u20b9 -> ₹).
    2. Extracts all numeric patterns from model prose (currency, percentages, years).
    3. Reconciles extracted numbers against expected values with context pairing.
    """
    errors = []

    # Ensure unicode escape sequences are decoded
    try:
        import codecs
        if "\\u" in output_text:
            output_text = codecs.decode(output_text, "unicode_escape")
    except Exception:
        pass

    # Clean numbers out of prose
    # Regex to find: ₹XX,XX,XXX.XX or XX.XX% or XX.X years or raw numbers
    extracted_raw = re.findall(r"[-+]?[₹]?[\d,]+(?:\.\d+)?%?", output_text)

    parsed_nums = []
    for raw in extracted_raw:
        s = raw.replace("₹", "").replace("%", "").replace(",", "").strip()
        try:
            val = float(s)
            parsed_nums.append((raw, abs(val)))
        except ValueError:
            continue

    # Refined Typed Tolerances:
    # - Currency: 0.01 (1 paisa tolerance to absorb float epsilon, zero rounding tolerance)
    # - Percentage: 0.01 (0.01% exact match to canonical 2-decimal display; prevents 3.6% vs 3.61% drift)
    # - Years / Ratios: 0.05 (exact to 1 decimal place)

    # Statutory references allowed in text (e.g. Section 112A, 5.0% threshold)
    STATUTORY_ALLOWLIST = {112.0, 112.00, 80.0, 5.0}

    pct_expected = [round(abs(v), 2) for k, v in expected_numbers.items() if "pct" in k or "drift" in k or "target" in k or "current" in k or "xirr" in k or "progress" in k]
    curr_expected = [round(abs(v), 2) for k, v in expected_numbers.items() if "val" in k or "worth" in k or "capital" in k or "change" in k or "remaining" in k]
    year_expected = [round(abs(v), 2) for k, v in expected_numbers.items() if "year" in k]

    for raw_token, num_val in parsed_nums:
        # Ignore small stylistic bullet numbers (e.g. 1., 2., 3.) or statutory references (112A)
        if (num_val in [1.0, 2.0, 3.0, 4.0] and ("." not in raw_token or raw_token.endswith("."))) or num_val in STATUTORY_ALLOWLIST:
            continue

        matched = False
        rounded_val = round(num_val, 2)

        if "%" in raw_token:
            # Strict percentage check: tolerance 0.01%
            matched = any(abs(rounded_val - exp) <= 0.01 for exp in pct_expected)
        elif "₹" in raw_token or rounded_val >= 100.0:
            # Strict currency check: 1 paisa (0.01) tolerance
            matched = any(abs(rounded_val - exp) <= 0.01 for exp in curr_expected)
        else:
            # General / years check: tolerance 0.05
            matched = any(abs(rounded_val - exp) <= 0.05 for exp in (year_expected + pct_expected + curr_expected))

        if not matched:
            errors.append(f"Unrecognized or miscalculated number in brief: '{raw_token}' (parsed as {num_val})")

    # Context-pairing assertions: strictly parse line-by-line bucket statements
    # Catches transpositions like swapping CORE (50% / -2%) with SATELLITE (30% / +2%)
    for line in output_text.splitlines():
        for bucket in ["CORE", "SATELLITE", "GOLD_SILVER", "GOLD/SILVER", "LIQUID_BUFFER", "LIQUID BUFFER"]:
            canon_bucket = "GOLD_SILVER" if "GOLD" in bucket else ("LIQUID_BUFFER" if "LIQUID" in bucket else bucket)
            if bucket in line:
                expected_target = expected_numbers.get(f"{canon_bucket}_target")
                expected_drift = expected_numbers.get(f"{canon_bucket}_drift")

                # Extract all percentages on this specific bucket line
                line_pcts = [float(p.replace("%", "").strip()) for p in re.findall(r"[-+]?\d+(?:\.\d+)?%", line)]

                if expected_target is not None:
                    # Check if expected target is present on this line (exact within 0.01%)
                    has_target = any(abs(p - expected_target) <= 0.01 for p in line_pcts)
                    if not has_target:
                        errors.append(f"Context mismatch: {bucket} line stated percentages {line_pcts}, but expected target {expected_target}%.")

                if expected_drift is not None:
                    # Check if expected drift is present on this line (exact within 0.01%)
                    has_drift = any(abs(p - expected_drift) <= 0.01 for p in line_pcts)
                    if not has_drift:
                        errors.append(f"Context mismatch: {bucket} line stated percentages {line_pcts}, but expected drift {expected_drift:+.2f}%.")

    is_valid = len(errors) == 0
    return is_valid, errors


def render_plain_fallback(snapshot: Dict[str, Any]) -> str:
    """Deterministic, unembellished templated fallback dump when verification fails."""
    nw = snapshot["net_worth"]["display"]
    dc = snapshot["day_change"]["display"]
    xirr = snapshot["overall_xirr"]["display"]
    ltcg = snapshot["ltcg_budget"]["remaining_allowance"]["display"]

    lines = [
        "=" * 60,
        "PORTFOLIO OS // DETERMINISTIC EXECUTIVE BRIEF (FALLBACK)",
        "=" * 60,
        f"As of:              {snapshot['as_of_date']}",
        f"Net Worth:          {nw}  (Day Change: {dc})",
        f"Overall XIRR:       {xirr}",
        f"Remaining LTCG:     {ltcg}",
        f"Rebalance Action:   {snapshot['rebalance_action']}",
        "-" * 60,
        "Bucket Allocations & Corridors:",
    ]
    for b in snapshot["buckets"]:
        lines.append(
            f"  • {b['bucket']:<14}: Current {b['current_pct']['display']} (Target {b['target_pct']['display']}, Drift {b['drift_pct']['display']}) [{b['status']}]"
        )
    lines.append("-" * 60)
    lines.append(f"FIRE Timeline:      {snapshot['fire_tracker']['progress_pct']['display']} funded · {snapshot['fire_tracker']['projected_years_to_fi']['display']} to FI")
    lines.append("=" * 60)
    return "\n".join(lines)


def generate_executive_brief(snapshot: Dict[str, Any]) -> Tuple[str, bool, Dict[str, Any]]:
    """Calls Ollama phi4-mini with keep_alive=0, then validates output."""
    system_prompt = (
        "You are the executive portfolio intelligence narrator for Portfolio OS.\n"
        "STRICT GROUNDING CONSTRAINTS:\n"
        "1. You MUST copy the exact 'display' strings verbatim for every number (currency, percentages, years).\n"
        "2. NEVER invent, calculate, round, or reformat any number.\n"
        "3. Provide a dense executive brief structured as:\n"
        "   - **Executive Health**: Net worth, day change, and overall XIRR.\n"
        "   - **Rebalance Corridors**: Status of Core, Satellite, Gold/Silver, and Liquid Buffer against targets, explicitly stating the 'rebalance_action' and any TRIGGERED breaches.\n"
        "   - **Tax & Retirement**: Remaining Section 112A LTCG allowance, FIRE progress and runway.\n"
        "4. Tone is institutional, neutral, and precise."
    )

    prompt = (
        "Generate the daily executive brief using these exact display values:\n"
        + json.dumps(snapshot, indent=2)
    )

    payload = {
        "model": MODEL_NAME,
        "system": system_prompt,
        "prompt": prompt,
        "stream": False,
        "keep_alive": 0,  # Unload from VRAM immediately after completion
        "options": {
            "temperature": 0.1,  # Low temperature for deterministic adherence
            "top_p": 0.9,
        },
    }

    req = urllib.request.Request(
        f"{OLLAMA_URL}/api/generate",
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
    )

    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            res = json.loads(resp.read().decode())
            text = res.get("response", "").strip()
            if "\\u" in text:
                import codecs
                try:
                    text = codecs.decode(text, "unicode_escape")
                except Exception:
                    pass
            telemetry = {
                "total_sec": round(res.get("total_duration", 0) / 1e9, 2),
                "eval_tok_per_sec": round(res.get("eval_count", 0) / (res.get("eval_duration", 1) / 1e9), 2),
                "eval_tokens": res.get("eval_count", 0),
            }
            return text, True, telemetry
    except Exception as e:
        return f"Ollama generation failed: {e}", False, {}


def main():
    print("=" * 64)
    print("PORTFOLIO OS // DAILY EXECUTIVE BRIEF RUNNER")
    print(f"Model: {MODEL_NAME} (with keep_alive: 0 VRAM unload)")
    print("=" * 64)

    # 1. Fetch live snapshot or construct grounded offline payload
    snapshot = None if "--offline" in sys.argv else fetch_live_snapshot()
    if snapshot:
        print("[+] Sourced live telemetry from Core-Node.")
    else:
        print("[*] Sourced from offline ledger state & snapshot.")
        snapshot = generate_mock_or_offline_snapshot()

    expected_registry = extract_expected_numbers(snapshot)

    # 2. Invoke phi4-mini or simulate adversarial failures
    if "--simulate-corrupt-number" in sys.argv:
        # Corrupt one single number: drop a zero from remaining LTCG (e.g. ₹1,05,590.36 -> ₹10,559.03 or ₹80,000 -> ₹8,000)
        ltcg_str = snapshot["ltcg_budget"]["remaining_allowance"]["display"]
        corrupted_ltcg = ltcg_str.replace("0", "", 1) if "0" in ltcg_str else "₹9,999.00"
        print(f"[*] TEST MODE: Simulating model dropping a digit in LTCG ({ltcg_str} -> {corrupted_ltcg})...")
        
        b_map = {b["bucket"]: b for b in snapshot["buckets"]}
        core_b = b_map.get("CORE", snapshot["buckets"][0])
        sat_b = b_map.get("SATELLITE", snapshot["buckets"][1])
        gs_b = b_map.get("GOLD_SILVER", snapshot["buckets"][2])
        liq_b = b_map.get("LIQUID_BUFFER", snapshot["buckets"][3])

        narrative = (
            f"**Executive Health**:\n- Net worth: {snapshot['net_worth']['display']}\n- Day change: {snapshot['day_change']['display']}\n- Overall XIRR: {snapshot['overall_xirr']['display']}\n\n"
            f"**Rebalance Corridors**:\n- CORE: Target pct: {core_b['target_pct']['display']}, Drift pct: {core_b['drift_pct']['display']}\n"
            f"- SATELLITE: Target pct: {sat_b['target_pct']['display']}, Drift pct: {sat_b['drift_pct']['display']}\n"
            f"- GOLD/SILVER: Target pct: {gs_b['target_pct']['display']}, Drift pct: {gs_b['drift_pct']['display']}\n"
            f"- LIQUID BUFFER: Target pct: {liq_b['target_pct']['display']}, Drift pct: {liq_b['drift_pct']['display']}\n\n"
            f"**Tax & Retirement**:\n- Remaining Section 112A LTCG allowance: {corrupted_ltcg}\n- FIRE progress: {snapshot['fire_tracker']['progress_pct']['display']}\n- FIRE runway: {snapshot['fire_tracker']['projected_years_to_fi']['display']}"
        )
        success = True
        telemetry = {"eval_tok_per_sec": 18.5}
    elif "--simulate-bucket-swap" in sys.argv:
        b_map = {b["bucket"]: b for b in snapshot["buckets"]}
        core_b = b_map.get("CORE", snapshot["buckets"][0])
        sat_b = b_map.get("SATELLITE", snapshot["buckets"][1])
        gs_b = b_map.get("GOLD_SILVER", snapshot["buckets"][2])
        liq_b = b_map.get("LIQUID_BUFFER", snapshot["buckets"][3])

        print(f"[*] TEST MODE: Simulating model swapping CORE ({core_b['target_pct']['display']} / {core_b['drift_pct']['display']}) with SATELLITE ({sat_b['target_pct']['display']} / {sat_b['drift_pct']['display']})...")
        # Every single number in this narration exists in the input registry! No foreign numbers.
        # Only the context-pairing (CORE vs SATELLITE) is transposed.
        narrative = (
            f"**Executive Health**:\n- Net worth: {snapshot['net_worth']['display']}\n- Day change: {snapshot['day_change']['display']}\n- Overall XIRR: {snapshot['overall_xirr']['display']}\n\n"
            f"**Rebalance Corridors**:\n- CORE: Target pct: {sat_b['target_pct']['display']}, Drift pct: {sat_b['drift_pct']['display']}\n"
            f"- SATELLITE: Target pct: {core_b['target_pct']['display']}, Drift pct: {core_b['drift_pct']['display']}\n"
            f"- GOLD/SILVER: Target pct: {gs_b['target_pct']['display']}, Drift pct: {gs_b['drift_pct']['display']}\n"
            f"- LIQUID BUFFER: Target pct: {liq_b['target_pct']['display']}, Drift pct: {liq_b['drift_pct']['display']}\n\n"
            f"**Tax & Retirement**:\n- Remaining Section 112A LTCG allowance: {snapshot['ltcg_budget']['remaining_allowance']['display']}\n- FIRE progress: {snapshot['fire_tracker']['progress_pct']['display']}\n- FIRE runway: {snapshot['fire_tracker']['projected_years_to_fi']['display']}"
        )
        success = True
        telemetry = {"eval_tok_per_sec": 18.5}
    else:
        print(f"[*] Generating grounded narrative via {MODEL_NAME}...")
        narrative, success, telemetry = generate_executive_brief(snapshot)

    if not success:
        print(f"[-] {narrative}")
        print("[!] Falling back to deterministic templated brief:\n")
        print(render_plain_fallback(snapshot))
        sys.exit(0)

    # 3. Hard-Gated Verification
    is_valid, errors = verify_narration(narrative, expected_registry)

    if not is_valid:
        print("\n" + "!" * 64)
        print("VERIFICATION GATE FAILED: Model hallucinated or transposed numbers!")
        for err in errors:
            print(f"  • {err}")
        print("\nDiscarding LLM narrative to prevent silent misreporting.")
        if "--debug" in sys.argv:
            print("\n--- REJECTED NARRATIVE ---")
            print(narrative)
            print("--------------------------\n")
        print("!" * 64 + "\n")
        print(render_plain_fallback(snapshot))
        sys.exit(1)
    else:
        print(f"\n[✓] Verification PASSED: All numbers and contexts reconciled ({telemetry.get('eval_tok_per_sec')} tok/s).")
        print("=" * 64)
        print(narrative)
        print("=" * 64)


if __name__ == "__main__":
    main()
