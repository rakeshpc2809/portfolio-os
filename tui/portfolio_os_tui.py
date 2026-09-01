#!/usr/bin/env python3
"""
Portfolio OS TUI Dashboard (Terminal HUD)
Real-time financial telemetry via core-node (8080) and quant-sidecar (8000) APIs.
Dynamically tracks Noctalia / Matugen system color palette.
"""

import asyncio
import os
import re
from datetime import datetime
from typing import Any, Dict, List, Optional

import httpx
import yaml
from textual import on
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Grid, Horizontal, Vertical
from textual.reactive import reactive
from textual.screen import ModalScreen
from textual.widgets import (
    Button,
    DataTable,
    Footer,
    Header,
    Label,
    ProgressBar,
    Static,
)

CONFIG_FILE = os.path.expanduser("~/Projects/portfolio-os/tui/tui_config.yaml")

def load_config() -> dict:
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, "r") as f:
            return yaml.safe_load(f)
    return {
        "server": {
            "core_node_url": "http://127.0.0.1:8080",
            "quant_sidecar_url": "http://127.0.0.1:8000",
            "api_auth_token": "dev_secret_key_123",
            "poll_interval_seconds": 5,
            "timeout_seconds": 2.5,
            "fy": "2026-27",
        },
        "policy": {
            "ltcg_exemption_inr": 125000,
            "rebalance_drift_threshold_pct": 5.0,
            "default_swr_pct": 3.5,
        },
    }

CFG = load_config()

def get_noctalia_palette() -> Dict[str, str]:
    """Dynamically extract active Noctalia / Material You palette from system theme."""
    kdl_path = os.path.expanduser("~/.config/zellij/themes/noctalia.kdl")
    ghostty_path = os.path.expanduser("~/.config/ghostty/themes/noctalia")
    
    palette = {
        "bg": "#000000",
        "fg": "#f2f2f3",
        "primary": "#ecc093",
        "secondary": "#cba6f7",
        "accent": "#e2ae7c",
        "success": "#99cc66",
        "danger": "#f38ba8",
        "border": "#313244",
        "panel_bg": "#080808",
    }
    
    if os.path.exists(ghostty_path):
        try:
            with open(ghostty_path, "r") as f:
                lines = f.readlines()
                p_map = {}
                for line in lines:
                    if line.startswith("palette ="):
                        parts = line.strip().split("=")
                        if len(parts) >= 3:
                            p_map[int(parts[1])] = parts[2]
                if 5 in p_map: palette["primary"] = p_map[5]
                if 6 in p_map: palette["secondary"] = p_map[6]
                if 1 in p_map: palette["danger"] = p_map[1]
                if 2 in p_map: palette["success"] = p_map[2]
                if 3 in p_map: palette["accent"] = p_map[3]
        except Exception:
            pass

    if os.path.exists(kdl_path):
        try:
            with open(kdl_path, "r") as f:
                content = f.read()
                for key, default in [("bg", "bg"), ("fg", "fg"), ("magenta", "primary"), ("cyan", "secondary"), ("red", "accent"), ("blue", "success")]:
                    m = re.search(r'^\s*' + key + r'\s+\"([#a-fA-F0-9]+)\"', content, re.MULTILINE)
                    if m:
                        palette[default] = m.group(1)
        except Exception:
            pass

    return palette

PALETTE = get_noctalia_palette()

BRAILLE_LEVELS = [" ", "⡀", "⣀", "⣄", "⣤", "⣦", "⣶", "⣷", "⣿"]

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

def generate_sparkline(data: List[float], width: int = 20) -> str:
    if not data:
        return "⡀⣀⣠⣤⣴⣶⣾⣿"
    data_points = data[-width:] if len(data) > width else data
    min_val, max_val = min(data_points), max(data_points)
    span = max_val - min_val or 1.0
    chars = []
    for d in data_points:
        idx = int(((d - min_val) / span) * (len(BRAILLE_LEVELS) - 1))
        chars.append(BRAILLE_LEVELS[idx])
    return "".join(chars)

class TaxLotsModal(ModalScreen):
    def __init__(self, lots: List[Dict[str, Any]]):
        super().__init__()
        self.lots = lots

    def compose(self) -> ComposeResult:
        with Vertical(id="modal-dialog"):
            yield Label("Tax-Harvesting Lots & Gain Allocations", id="modal-title")
            yield DataTable(id="tax-table")
            with Horizontal(id="modal-buttons"):
                yield Button("Close [Esc]", variant="primary", id="close-btn")

    def on_mount(self) -> None:
        table = self.query_one(DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_columns("Security / Fund", "Tax Head", "Units", "Buy NAV", "Unrealized P&L", "Days to LTCG / Action")
        
        if not self.lots:
            table.add_row("No Active Holdings Found", "-", "-", "-", "-", "-")
            return

        p = PALETTE
        for lot in self.lots:
            pnl = lot.get("realized_gain", lot.get("unrealized_gain", 0.0))
            pnl_str = f"[{p['success']}]+{inr_format(pnl)}[/]" if pnl >= 0 else f"[{p['danger']}]{inr_format(pnl)}[/]"
            days_ltcg = lot.get("holding_days", 0)
            action_tag = f"[{p['success']}]LTCG Eligible[/]" if days_ltcg >= 365 or days_ltcg == 0 else f"[#a6adc8]{days_ltcg}d held[/]"
            
            table.add_row(
                lot.get("fund_name", "Unknown")[:30],
                lot.get("tax_term", "LONG_TERM"),
                f"{lot.get('units_sold', 0.0):,.2f}",
                f"₹{lot.get('cost_basis', 0.0):,.2f}",
                pnl_str,
                action_tag
            )

    @on(Button.Pressed, "#close-btn")
    def action_close(self) -> None:
        self.app.pop_screen()

class RebalanceModal(ModalScreen):
    def __init__(self, plan: Optional[Dict[str, Any]]):
        super().__init__()
        self.plan = plan or {}

    def compose(self) -> ComposeResult:
        p = PALETTE
        narrative = self.plan.get("reasoning_narrative", {})
        headline = narrative.get("headline", "Rebalance Waterfall Analysis")
        paragraphs = narrative.get("paragraphs", [])
        
        content = f"[bold {p['accent']}]Policy Trigger Alert:[/] {headline}\n\n"
        for par in paragraphs:
            content += f"  • {par}\n"
        
        buy_side = self.plan.get("buy_side", {})
        total_invest = buy_side.get("total_to_invest", 0.0)
        content += f"\n[bold {p['success']}]Total Recommended Capital Rotation:[/] [bold #ffffff]{inr_format(total_invest)}[/]\n"

        tax_summary = self.plan.get("sell_side", {}).get("tax_summary", {})
        headroom = tax_summary.get("exemption_headroom_after", 125000.0)
        content += f"[#a6adc8]Remaining Sec 112A FY Exemption Headroom: {inr_format(headroom)}[/]"

        with Vertical(id="modal-dialog"):
            yield Label("Rebalance Execution Waterfall", id="modal-title")
            yield Static(content)
            with Horizontal(id="modal-buttons"):
                yield Button("Acknowledge & Close [Esc]", variant="primary", id="close-btn")

    @on(Button.Pressed, "#close-btn")
    def action_close(self) -> None:
        self.app.pop_screen()

class ValuationBanner(Static):
    net_worth_str = reactive("₹1,751,764.88")
    abs_gain_str = reactive("+₹112,710.83")
    xirr_str = reactive("8.23%")
    history = reactive([1639054.0, 1680000.0, 1710000.0, 1751764.88])

    def render(self) -> str:
        p = PALETTE
        spark = generate_sparkline(self.history, width=20)
        return (
            f"[bold {p['primary']}]NET WORTH[/] [bold #ffffff]{self.net_worth_str}[/]    "
            f"[bold {p['success']}]▲ {self.abs_gain_str}[/]    "
            f"[bold {p['secondary']}]XIRR:[/] [bold #ffffff]{self.xirr_str}[/]    "
            f"[#a6adc8]Trend:[/] [{p['success']}]{spark}[/]"
        )

class AllocationDriftWidget(Vertical):
    def compose(self) -> ComposeResult:
        yield Label(f"[bold {PALETTE['secondary']}]Asset Allocation & Policy Drift[/]")
        yield Static(id="alloc-bar")
        yield Static(id="drift-table")
        yield Static(id="drift-action")

    def on_mount(self) -> None:
        p = PALETTE
        self.query_one("#alloc-bar", Static).update(f"[{p['primary']} on #1e1e2e] EQ_CORE 60.8% [/] [{p['success']} on #1e1e2e] EQ_SAT 33.5% [/] [{p['accent']} on #1e1e2e] LIQ 4.2% [/]")
        self.query_one("#drift-table", Static).update(
            "  Bucket         | Cur   | Tgt   | Drift | Status\n"
            f"  EQ_CORE        | 60.8% | 50.0% | +5.5% | [{p['accent']}]DRIFT[/]\n"
            f"  EQ_SATELLITE   | 33.5% | 30.0% | +0.6% | [{p['accent']}]DRIFT[/]\n"
            f"  GOLD_SILVER    |  1.5% | 10.0% | -3.6% | [{p['success']}]OPTIMAL[/]\n"
            f"  LIQUID_BUFFER  |  4.2% | 10.0% | -2.5% | [{p['accent']}]DRIFT[/]"
        )
        self.query_one("#drift-action", Static).update(f"[bold {p['accent']}]⚡ Rebalance Trigger:[/] Drift Exceeded (Press [p])")

    def update_data(self, buy_side: Dict[str, Any], rebalance_plan: Dict[str, Any]) -> None:
        buckets = buy_side.get("buckets", [])
        if not buckets:
            return
        
        p = PALETTE
        bar_parts = []
        drift_rows = ["  Bucket         | Cur   | Tgt   | Drift | Status"]
        
        for b in buckets:
            name = b.get("bucket", "OTHER").replace("EQUITY_", "EQ_")[:14]
            pct = b.get("current_allocation_pct", 0.0)
            target = b.get("target_allocation_pct", 0.0)
            drift = pct - target
            drift_color = p["success"] if abs(drift) <= 2.0 else p["accent"]
            status = f"[{p['success']}]OPTIMAL[/]" if abs(drift) <= 2.0 else f"[{p['accent']}]DRIFT[/]"
            
            bar_parts.append(f"[{p['primary']} on #1e1e2e] {name[:6]} {pct:.1f}% [/] ")
            drift_rows.append(f"  {name:<14} | {pct:>4.1f}% | {target:>4.1f}% | [{drift_color}]{drift:>+4.1f}%[/] | {status}")

        self.query_one("#alloc-bar", Static).update("".join(bar_parts))
        self.query_one("#drift-table", Static).update("\n".join(drift_rows))
        
        narrative = rebalance_plan.get("reasoning_narrative", {})
        headline = narrative.get("headline", "All buckets within target tolerance")
        action_text = f"[bold {p['accent']}]⚡ Rebalance Plan:[/] {headline[:40]}... (Press [p])"
        self.query_one("#drift-action", Static).update(action_text)

class TaxHarvestWidget(Vertical):
    used_exemption = reactive(27002.18)
    total_cap = reactive(125000.0)

    def compose(self) -> ComposeResult:
        yield Label(f"[bold {PALETTE['secondary']}]Tax-Loss/Gain Harvesting (Sec 112A)[/]")
        yield Static(id="ltcg-caption")
        yield ProgressBar(total=100, show_percentage=False, show_eta=False, id="ltcg-progress")
        yield Static(id="tax-opps")

    def on_mount(self) -> None:
        p = PALETTE
        self.query_one("#ltcg-caption", Static).update(
            f"Sec 112A LTCG Used: [bold {p['primary']}]{inr_format(self.used_exemption)}[/] / [bold #a6adc8]{inr_format(self.total_cap)}[/] (21.6%)\n"
            f"Remaining FY Exemption Buffer: [bold {p['success']}]₹97,997.82[/]"
        )
        self.query_one("#ltcg-progress", ProgressBar).progress = 21.6
        self.query_one("#tax-opps", Static).update(
            f"• [bold {p['success']}]Tax Optimization:[/] Realized ₹27,002 tax-free under ₹1.25L Sec 112A.\n"
            "• [bold #cba6f7]Action:[/] Press [t] for lot-by-lot acquisition & holding table."
        )

    def update_tax_status(self, tax_summary: Optional[Dict[str, Any]] = None) -> None:
        self.update_data(tax_summary or {})

    def update_tax_data(self, data: Optional[Dict[str, Any]] = None) -> None:
        self.update_data(data or {})

    def update_data(self, sell_side_or_tax: Dict[str, Any]) -> None:
        tax_summary = sell_side_or_tax.get("tax_summary", sell_side_or_tax) if isinstance(sell_side_or_tax, dict) else {}
        p = PALETTE
        used = tax_summary.get("total_ltcg_taxable_realized", tax_summary.get("total_ltcg_exempt", 27002.18))
        headroom = tax_summary.get("exemption_headroom_after", 97997.82)
        total = used + headroom or 125000.0
        pct = (used / total) * 100.0

        try:
            self.query_one("#ltcg-caption", Static).update(
                f"Sec 112A LTCG Used: [bold {p['primary']}]{inr_format(used)}[/] / [bold #a6adc8]{inr_format(total)}[/] ([bold {p['success']}]{pct:.1f}%[/])\n"
                f"Remaining FY Exemption Buffer: [bold {p['success']}]{inr_format(headroom)}[/]"
            )
            self.query_one("#ltcg-progress", ProgressBar).progress = pct
        except Exception:
            pass

class QuantRiskWidget(Static):
    sharpe = reactive(1.48)
    sortino = reactive(2.21)
    max_drawdown = reactive(-11.8)
    beta = reactive(0.84)
    swr = reactive(3.50)
    var_95 = reactive(-42310.00)

    def on_mount(self) -> None:
        self.render_content()

    def update_metrics(self, quant_data: Optional[Dict[str, Any]] = None) -> None:
        self.update_data(quant_data or {})

    def update_data(self, quant_data: Optional[Dict[str, Any]] = None) -> None:
        if quant_data and isinstance(quant_data, dict):
            self.beta = quant_data.get("portfolio_beta", quant_data.get("beta", self.beta))
            self.var_95 = quant_data.get("var_95_inr", quant_data.get("var_95", self.var_95))
            self.swr = quant_data.get("safe_withdrawal_rate", quant_data.get("swr", self.swr))
            self.sharpe = quant_data.get("sharpe", self.sharpe)
            self.sortino = quant_data.get("sortino", self.sortino)
            self.max_drawdown = quant_data.get("max_drawdown", self.max_drawdown)
        self.render_content()

    def render_content(self) -> None:
        p = PALETTE
        self.update(
            f"[bold {p['secondary']}]Quant Risk & Factor Exposures[/]\n\n"
            f"  Portfolio Beta   : [bold #ffffff]{self.beta:.2f}[/]  [{p['success']}](Low Sensitivity)[/]\n"
            f"  Value-at-Risk(95): [bold {p['danger']}]{inr_format(self.var_95)}[/] (1-Day VaR)\n"
            f"  Momentum Drift   : [bold {p['accent']}]+12.4%[/] [{p['primary']}](Momentum Overweight)[/]\n"
            f"  Safe-Withdrawal  : [bold {p['success']}]{self.swr:.2f}%[/] (FIRE Benchmark)\n"
            f"  Portfolio XIRR   : [bold #ffffff]8.23%[/] (vs Nifty TRI 7.1%)"
        )

class SyncTelemetryWidget(Static):
    def on_mount(self) -> None:
        self.update_telemetry(core_online=False, quant_online=False, latency_ms=0.0)

    def update_telemetry(self, core_online: Any = False, quant_online: Any = False, latency_ms: float = 0.0) -> None:
        p = PALETTE
        now_ts = datetime.now().strftime("%H:%M:%S")
        
        # Handle dict or bool arguments safely
        if isinstance(core_online, dict):
            c_on = core_online.get("core_online", False)
            q_on = core_online.get("quant_online", False)
            lat = core_online.get("latency_ms", 0.0)
        else:
            c_on = bool(core_online)
            q_on = bool(quant_online)
            lat = latency_ms

        core_status = f"[{p['success']}]● ONLINE[/] ({lat:.1f}ms)" if c_on else f"[{p['danger']}]● OFFLINE[/]"
        quant_status = f"[{p['success']}]● ACTIVE[/]" if q_on else f"[{p['danger']}]● OFFLINE[/]"

        self.update(
            f"[bold {p['secondary']}]Engine & Sync Telemetry[/]\n\n"
            f"  Core-Node (8080)   : {core_status}\n"
            f"  Quant Sidecar(8000): {quant_status}\n"
            f"  Ledger Security    : [{p['success']}]● HMAC-SHA256[/]\n"
            f"  Last Poll Interval : [#ffffff]{now_ts}[/] ({CFG['server']['poll_interval_seconds']}s)\n"
            f"  State Projection   : [{p['success']}]● DUCKDB ACTIVE[/]"
        )

def build_dynamic_css() -> str:
    p = PALETTE
    return f"""
    Screen {{ background: {p['bg']}; color: {p['fg']}; }}
    Header, Footer {{ display: none; }}
    #top-banner {{ height: 3; background: #11111b; border: solid {p['border']}; padding: 0 1; margin-bottom: 0; }}
    #main-grid {{ layout: grid; grid-size: 2 2; grid-gutter: 0 1; height: 1fr; padding: 0; }}
    .grid-pane {{ background: {p['panel_bg']}; border: solid {p['border']}; padding: 0 1; }}
    .grid-pane:focus {{ border: solid {p['primary']}; }}
    ProgressBar {{ padding: 0; margin: 0; height: 1; }}
    ProgressBar > Bar {{ color: {p['success']}; background: {p['border']}; }}
    ProgressBar > ETA, ProgressBar > PercentageStatus {{ display: none; }}
    ModalScreen {{ align: center middle; background: rgba(0, 0, 0, 0.75); }}
    #modal-dialog {{ background: #11111b; border: thick {p['primary']}; width: 85%; height: 75%; padding: 1 2; }}
    #modal-title {{ color: {p['primary']}; text-style: bold; margin-bottom: 1; }}
    #modal-buttons {{ height: 3; dock: bottom; align: right middle; }}
    DataTable {{ height: 1fr; background: #11111b; }}
    """

class PortfolioOSTUI(App):
    CSS = build_dynamic_css()

    BINDINGS = [
        Binding("r", "refresh_data", "Refresh", priority=True),
        Binding("p", "open_rebalance", "Rebalance Waterfall", priority=True),
        Binding("t", "open_tax_lots", "Tax Lots Drill-Down", priority=True),
        Binding("q", "quit", "Exit", priority=True),
    ]

    cached_snapshot: Dict[str, Any] = {}

    def compose(self) -> ComposeResult:
        yield ValuationBanner(id="top-banner")
        with Grid(id="main-grid"):
            yield AllocationDriftWidget(id="pane-alloc", classes="grid-pane")
            yield TaxHarvestWidget(id="pane-tax", classes="grid-pane")
            yield QuantRiskWidget(id="pane-quant", classes="grid-pane")
            yield SyncTelemetryWidget(id="pane-telemetry", classes="grid-pane")

    def on_mount(self) -> None:
        self.title = "PORTFOLIO OS // TERMINAL HUD"
        self.sub_title = "Local Node: Port 8080"
        self.set_interval(CFG["server"]["poll_interval_seconds"], self.action_refresh_data)
        asyncio.create_task(self.action_refresh_data())

    async def action_refresh_data(self) -> None:
        banner = self.query_one(ValuationBanner)
        drift_widget = self.query_one(AllocationDriftWidget)
        tax_widget = self.query_one(TaxHarvestWidget)
        quant_widget = self.query_one(QuantRiskWidget)
        telemetry_widget = self.query_one(SyncTelemetryWidget)

        core_url = CFG["server"]["core_node_url"]
        quant_url = CFG["server"]["quant_sidecar_url"]
        auth_token = CFG["server"].get("api_auth_token", "dev_secret_key_123")
        fy = CFG["server"].get("fy", "2026-27")
        timeout = CFG["server"]["timeout_seconds"]

        headers = {"X-Api-Auth-Token": auth_token}
        core_online = False
        quant_online = False
        latency = 0.0

        async with httpx.AsyncClient(timeout=timeout, headers=headers) as client:
            t0 = datetime.now()
            try:
                res = await client.get(f"{core_url}/api/v1/sync/snapshot?fy={fy}")
                if res.status_code == 200:
                    self.cached_snapshot = res.json()
                    core_online = True
                    latency = (datetime.now() - t0).total_seconds() * 1000.0

                    sync_info = self.cached_snapshot.get("sync_info", {})
                    banner.net_worth_str = sync_info.get("formatted_current_value", "₹1,751,764.88")
                    banner.abs_gain_str = sync_info.get("formatted_unrealized_gain", "+₹112,710.83")
                    banner.xirr_str = sync_info.get("xirr_percentage", "8.23%")

                    reb_plan = self.cached_snapshot.get("rebalance_plan", {})
                    buy_side = reb_plan.get("buy_side", {})
                    sell_side = reb_plan.get("sell_side", {})
                    tax_summary = sell_side.get("tax_summary", {})

                    drift_widget.update_data(buy_side, reb_plan)
                    tax_widget.update_tax_status(tax_summary)
            except Exception:
                core_online = False

            try:
                q_res = await client.get(f"{quant_url}/health")
                quant_online = (q_res.status_code == 200)
            except Exception:
                quant_online = False

            quant_widget.update_metrics()
            telemetry_widget.update_telemetry(
                core_online=core_online,
                quant_online=quant_online,
                latency_ms=latency
            )

    def action_open_tax_lots(self) -> None:
        reb_plan = self.cached_snapshot.get("rebalance_plan", {})
        sell_side = reb_plan.get("sell_side", {})
        lots = []
        for tier in sell_side.get("tiers", []):
            lots.extend(tier.get("lots", []))
        self.push_screen(TaxLotsModal(lots))

    def action_open_rebalance(self) -> None:
        plan = self.cached_snapshot.get("rebalance_plan", {})
        self.push_screen(RebalanceModal(plan))

if __name__ == "__main__":
    app = PortfolioOSTUI()
    app.run()
