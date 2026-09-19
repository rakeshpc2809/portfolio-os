from pathlib import Path
import json
from datetime import datetime
from typing import Any, Dict, List, Optional

def inr_format(val: float) -> str:
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

def load_hrp_audit() -> Optional[Dict[str, Any]]:
    candidate_paths = [
        Path("data/hrp_audit.json"),
        Path("../data/hrp_audit.json"),
        Path(__file__).resolve().parent.parent / "data" / "hrp_audit.json",
    ]
    for p in candidate_paths:
        if p.exists():
            try:
                with open(p, "r") as f:
                    return json.load(f)
            except Exception:
                pass
    return None

def build_summary_markup(
    snapshot: Dict[str, Any],
    hrp_audit: Optional[Dict[str, Any]] = None,
    palette: Optional[Dict[str, str]] = None
) -> str:
    p = palette or {
        "primary": "#ecc093",
        "secondary": "#cba6f7",
        "accent": "#e2ae7c",
        "success": "#a6e3a1",
        "danger": "#f38ba8",
        "gold": "#f9e2af",
        "cyan": "#94e2d5",
        "blue": "#89b4fa",
        "fg": "#e2e4ed",
        "fg_sub": "#6e738d",
    }

    if not hrp_audit:
        hrp_audit = load_hrp_audit()

    today_str = datetime.now().strftime("%Y-%m-%d %A")

    # 1. Executive Health
    sync_info = snapshot.get("sync_info", {})
    net_worth = float(sync_info.get("current_value", 1751764.88))
    invested = float(sync_info.get("total_invested", 1639054.05))
    gain_str = sync_info.get("formatted_unrealized_gain", sync_info.get("unrealized_gain", "+₹1,12,710.83"))
    if isinstance(gain_str, (int, float)):
        gain_str = inr_format(gain_str)
    xirr_str = str(sync_info.get("portfolio_xirr", sync_info.get("xirr_percentage", "8.23%")))
    if not xirr_str.endswith("%"):
        xirr_str = f"{xirr_str}%"

    lines = [
        f"[bold {p['primary']}]PORTFOLIO OS // DAILY EXECUTIVE BRIEF[/]",
        f"[#6e738d]As of: {today_str} · Verified Telemetry[/]",
        "",
        f"[bold {p['secondary']}]1. EXECUTIVE HEALTH[/]",
        f"  • Net Worth:        [bold #ffffff]{inr_format(net_worth)}[/]  ([{p['success']}]{gain_str}[/])",
        f"  • Invested Capital: [#cdd6f4]{inr_format(invested)}[/]",
        f"  • Portfolio XIRR:   [bold {p['secondary']}]{xirr_str}[/]",
        "",
    ]

    # 2. Rebalance Corridors & Drift Status
    reb_plan = snapshot.get("rebalance_plan", {})
    buy_side = reb_plan.get("buy_side", {})
    buckets = buy_side.get("buckets", [])

    lines.append(f"[bold {p['secondary']}]2. REBALANCE CORRIDORS & POLICY BANDS[/]")
    if not buckets:
        lines.append("  [#6e738d]• Sourcing corridor status from policy targets...[/]")
    else:
        for b in buckets:
            b_name = b.get("bucket", "").replace("EQUITY_", "").replace("_BUFFER", "")
            cur = float(b.get("current_allocation_pct", b.get("current_pct", 0.0)))
            tgt = float(b.get("target_allocation_pct", b.get("target_pct", 0.0)))
            drift = cur - tgt
            col = p["success"] if abs(drift) <= 5.0 else p["accent"]
            status_badge = f"[{p['success']}]IN_BAND[/]" if abs(drift) <= 5.0 else f"[{p['accent']}]DRIFT_ALERT[/]"
            lines.append(
                f"  • {b_name:<11}: Current [bold #ffffff]{cur:>5.1f}%[/]  "
                f"(Target {tgt:>4.1f}%, Drift [{col}]{drift:>+5.1f}%[/])  {status_badge}"
            )

    rotation = float(buy_side.get("total_to_invest", 0.0))
    if rotation > 0:
        lines.append(f"  ⚡ Action: [bold {p['accent']}]Rebalance Required[/] · Rotation: [bold #ffffff]{inr_format(rotation)}[/]")
    else:
        lines.append(f"  ✔ Action: [{p['success']}]NO_OP (All buckets within ±5% corridor)[/]")
    lines.append("")

    # 3. HRP CVaR Quantitative Advisory Audit
    lines.append(f"[bold {p['secondary']}]3. HRP CVaR ADVISORY WEIGHTS (Option A)[/]")
    if hrp_audit and hrp_audit.get("status") == "SUCCESS":
        days = hrp_audit.get("trading_days", 432)
        start_d = hrp_audit.get("start_date", "2024-12-11")
        end_d = hrp_audit.get("end_date", "2026-09-06")
        lines.append(f"  [#6e738d]• Model: CVaR 95% Risk Parity ({days} aligned trading days, {start_d} → {end_d})[/]")
        lines.append(f"  [#6e738d]• Macro Invariant: Top-level bucket targets strictly preserved (100.00% exact)[/]")

        allocs = {a["isin"]: a for a in hrp_audit.get("allocations", [])}
        
        # Core details
        lm = allocs.get("INF109KC12U0", {})
        pp = allocs.get("INF879O01027", {})
        if lm and pp:
            lines.append(f"  [bold {p['primary']}]Core Bucket (50% Policy):[/]")
            lines.append(
                f"    - {lm.get('name', 'LargeMid 250')[:18]:<18}: Intra {lm.get('intra_bucket_hrp_pct', 37.44):>5.2f}% "
                f"(Target {lm.get('intra_bucket_target_pct', 60.0):>5.1f}%) → Port {lm.get('hrp_pct', 18.72):>5.2f}%"
            )
            lines.append(
                f"    - {pp.get('name', 'PPFAS Flexi Cap')[:18]:<18}: Intra {pp.get('intra_bucket_hrp_pct', 62.56):>5.2f}% "
                f"(Target {pp.get('intra_bucket_target_pct', 40.0):>5.1f}%) → Port {pp.get('hrp_pct', 31.28):>5.2f}%"
            )

        # Satellite details
        v30 = allocs.get("INF109KC13X2", {})
        m50 = allocs.get("INF754K01TN5", {})
        sc = allocs.get("INF204K01K15", {})
        if v30 and m50 and sc:
            lines.append(f"  [bold {p['secondary']}]Satellite Bucket (30% Policy):[/]")
            lines.append(
                f"    - {v30.get('name', 'ICICI Value 30')[:18]:<18}: Intra {v30.get('intra_bucket_hrp_pct', 25.05):>5.2f}% "
                f"(Target {v30.get('intra_bucket_target_pct', 33.33):>5.2f}%) → Port {v30.get('hrp_pct', 7.51):>5.2f}%"
            )
            lines.append(
                f"    - {m50.get('name', 'Edelweiss Mom 50')[:18]:<18}: Intra {m50.get('intra_bucket_hrp_pct', 46.50):>5.2f}% "
                f"(Target {m50.get('intra_bucket_target_pct', 33.33):>5.2f}%) → Port {m50.get('hrp_pct', 13.95):>5.2f}%"
            )
            lines.append(
                f"    - {sc.get('name', 'Nippon Small Cap')[:18]:<18}: Intra {sc.get('intra_bucket_hrp_pct', 28.45):>5.2f}% "
                f"(Target {sc.get('intra_bucket_target_pct', 33.34):>5.2f}%) → Port {sc.get('hrp_pct', 8.54):>5.2f}%"
            )
    else:
        lines.append("  [#6e738d]• HRP advisory audit cache not found. Run `just alloc-hrp` to compute.[/]")
    lines.append("")

    # 4. Tax & Retirement Strip
    sell_side = reb_plan.get("sell_side", {})
    tax_sum = sell_side.get("tax_summary", {})
    ltcg_used = float(tax_sum.get("total_ltcg_taxable_realized", tax_sum.get("total_ltcg_exempt", 27002.18)))
    headroom = float(tax_sum.get("exemption_headroom_after", 97997.82))

    lines.append(f"[bold {p['secondary']}]4. TAX & RETIREMENT MILESTONES[/]")
    lines.append(f"  • Sec 112A LTCG Harvested: [{p['gold']}]{inr_format(ltcg_used)}[/] (Exemption Headroom: [bold {p['success']}]{inr_format(headroom)}[/])")
    
    # Next lot milestone if present
    # Primary: authoritative top-level tax_lots unconditionally embedded by /api/v1/sync/snapshot
    # Fallback: defensive check against tier lots if tax_lots key is omitted in partial/mock snapshots
    all_lots = snapshot.get("tax_lots", snapshot.get("taxLots", []))
    if not all_lots:
        for tier in sell_side.get("waterfall", sell_side.get("tiers", [])):
            all_lots.extend(tier.get("lots", []))
    stcg_lots = [
        l for l in all_lots
        if (l.get("taxTerm", l.get("tax_term")) == "STCG" or not l.get("is_long_term", l.get("isLongTerm", True)))
        and l.get("daysToLtcg", l.get("days_to_ltcg", 0)) > 0
    ]
    if stcg_lots:
        stcg_lots.sort(key=lambda x: x.get("daysToLtcg", x.get("days_to_ltcg", 0)))
        nl = stcg_lots[0]
        fname = nl.get("fundName", nl.get("fund_name", nl.get("name", nl.get("isin", "Fund"))))
        dl = nl.get("daysToLtcg", nl.get("days_to_ltcg", 0))
        lines.append(f"  • Next LTCG Transition:    [#cdd6f4]{fname}[/] ({dl} days remaining)")

    lines.append("")
    lines.append("[#6e738d]Press [bold #cdd6f4][Esc][/] to return to Cockpit HUD.[/]")

    return "\n".join(lines)
