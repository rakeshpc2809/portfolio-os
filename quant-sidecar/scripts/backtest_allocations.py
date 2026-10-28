#!/usr/bin/env python3
"""
Portfolio OS - Allocation Walk-Forward Backtester
Simulates bucket_targets.yaml version transitions (v1.0 -> v2.0 -> v2.3) against historical DuckDB NAV data.
Models DRAWDOWN > DRIFT > SCHEDULED rebalancing rules, FIFO lot tracking, and Indian tax drag (Sec 112A).
"""

import os
import sys
import argparse
import duckdb
import numpy as np
import pandas as pd
from datetime import datetime, date
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

def parse_bucket_targets_yaml(file_path: str) -> dict:
    """Native parser for bucket_targets.yaml without external PyYAML dependency."""
    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    versions = []
    current_version = None
    current_targets = None
    current_target = None
    current_preferred = None
    current_pf = None

    for line in lines:
        raw = line.rstrip()
        stripped = raw.strip()
        if not stripped or stripped.startswith("#") or stripped == "---":
            continue

        indent = len(raw) - len(raw.lstrip())

        if stripped.startswith("- version_id:"):
            vid = stripped.split(":", 1)[1].strip().strip('"').strip("'")
            current_version = {"version_id": vid, "targets": []}
            versions.append(current_version)
            current_targets = current_version["targets"]
            current_target = None
            current_preferred = None
        elif current_version and stripped.startswith("effective_from:"):
            current_version["effective_from"] = stripped.split(":", 1)[1].strip().strip('"').strip("'")
        elif current_version and stripped.startswith("- bucket:"):
            bname = stripped.split(":", 1)[1].strip().strip('"').strip("'")
            current_target = {"bucket": bname, "preferred_funds": []}
            current_targets.append(current_target)
            current_preferred = current_target["preferred_funds"]
            current_pf = None
        elif current_target and stripped.startswith("target_pct:"):
            current_target["target_pct"] = float(stripped.split(":", 1)[1].strip())
        elif current_target and stripped.startswith("band_pct:"):
            current_target["band_pct"] = float(stripped.split(":", 1)[1].strip())
        elif current_target and stripped.startswith("trigger_drift_pct:"):
            current_target["trigger_drift_pct"] = float(stripped.split(":", 1)[1].strip())
        elif current_target and stripped.startswith("strategy:"):
            current_target["strategy"] = stripped.split(":", 1)[1].strip().strip('"').strip("'")
        elif current_preferred is not None and stripped.startswith("- fund_id:"):
            fid = stripped.split(":", 1)[1].strip().strip('"').strip("'")
            current_pf = {"fund_id": fid}
            current_preferred.append(current_pf)
        elif current_pf and stripped.startswith("fund_name:"):
            current_pf["fund_name"] = stripped.split(":", 1)[1].strip().strip('"').strip("'")
        elif current_pf and stripped.startswith("allocation_weight:"):
            current_pf["allocation_weight"] = float(stripped.split(":", 1)[1].strip())

    return {"versions": versions}

def find_file(relative_paths: List[str]) -> Optional[str]:
    for p in relative_paths:
        if os.path.exists(p):
            return os.path.abspath(p)
    return None

@dataclass
class Lot:
    asset_id: str
    units: float
    nav: float
    cost_basis: float
    acquisition_date: date

@dataclass
class RebalanceEvent:
    event_date: date
    trigger_type: str  # DRAWDOWN, DRIFT, SCHEDULED
    sold_assets: Dict[str, float]
    bought_assets: Dict[str, float]
    turnover_amount: float
    realized_ltcg: float
    realized_stcg: float
    exemption_used: float
    tax_drag: float

class PortfolioBacktester:
    def __init__(self, duckdb_path: str, config_path: str, initial_corpus: float = 1000000.0):
        self.duckdb_path = duckdb_path
        self.config_path = config_path
        self.initial_corpus = initial_corpus
        self.con = duckdb.connect(duckdb_path, read_only=True)
        self.load_config()
        self.load_nav_data()

    def load_config(self):
        self.raw_config = parse_bucket_targets_yaml(self.config_path)
        self.versions = self.raw_config.get("versions", [])
        self.versions.sort(key=lambda v: v.get("effective_from", "2000-01-01"))

    def get_version_for_date(self, d: date) -> dict:
        d_str = d.strftime("%Y-%m-%d")
        active = self.versions[0]
        for v in self.versions:
            if v.get("effective_from", "") <= d_str:
                active = v
        return active

    def load_nav_data(self):
        df = self.con.execute("SELECT asset_id, nav_date, nav FROM nav_history ORDER BY nav_date ASC").fetchdf()
        df["nav_date"] = pd.to_datetime(df["nav_date"]).dt.date
        self.nav_df = df
        self.unique_dates = sorted(df["nav_date"].unique())
        self.nav_pivot = df.pivot(index="nav_date", columns="asset_id", values="nav")

    def get_fund_targets_for_date(self, version: dict, d: date) -> Dict[str, float]:
        fund_targets = {}
        liquid_fund = "INF205K01KR8" # Invesco Arbitrage / Liquid Buffer
        unallocated_weight = 0.0

        for b in version.get("targets", []):
            b_target = b.get("target_pct", 0.0) / 100.0
            for f in b.get("preferred_funds", []):
                fid = f.get("fund_id")
                weight = f.get("allocation_weight", 0.0)
                target_w = b_target * weight
                
                # Check if fund has valid NAV on date d
                if fid in self.nav_pivot.columns and pd.notna(self.nav_pivot.loc[d, fid]):
                    fund_targets[fid] = fund_targets.get(fid, 0.0) + target_w
                else:
                    # Proxy unlaunched fund to Liquid Buffer
                    unallocated_weight += target_w

        if unallocated_weight > 0:
            fund_targets[liquid_fund] = fund_targets.get(liquid_fund, 0.0) + unallocated_weight

        # Normalize sum to 1.0
        total_w = sum(fund_targets.values())
        if total_w > 0:
            for k in fund_targets:
                fund_targets[k] /= total_w
        return fund_targets

    def run_backtest(
        self,
        mode: str = "DYNAMIC_WALK_FORWARD", # DYNAMIC_WALK_FORWARD, STATIC_v1.0, STATIC_v2.0, STATIC_v2.3, BUY_AND_HOLD
        start_date: Optional[date] = None,
        end_date: Optional[date] = None,
        drift_rel_threshold: float = 0.15,
        drawdown_threshold: float = -0.10
    ) -> dict:
        # Determine valid date range where primary portfolio assets have NAV
        all_dates = [d for d in self.unique_dates if (start_date is None or d >= start_date) and (end_date is None or d <= end_date)]
        
        # Filter for dates where core funds are present
        core_funds = ["INF109KC12U0", "INF879O01027", "INF205K01KR8", "INF247L01BM8"]
        valid_dates = [d for d in all_dates if all(pd.notna(self.nav_pivot.loc[d, cf]) for cf in core_funds if cf in self.nav_pivot.columns)]
        
        if len(valid_dates) < 30:
            raise ValueError(f"Insufficient history for backtest: {len(valid_dates)} trading days found.")

        # Initialize portfolio on first date
        d0 = valid_dates[0]
        if mode == "STATIC_v1.0":
            v0 = [v for v in self.versions if v.get("version_id") == "v1.0"][0]
        elif mode == "STATIC_v2.0":
            v0 = [v for v in self.versions if v.get("version_id") == "v2.0"][0]
        elif mode == "STATIC_v2.3":
            v0 = [v for v in self.versions if v.get("version_id") == "v2.3"][0]
        else:
            v0 = self.get_version_for_date(d0)

        initial_targets = self.get_fund_targets_for_date(v0, d0)
        
        lots: List[Lot] = []
        cash_balance = 0.0

        for fid, weight in initial_targets.items():
            if fid in self.nav_pivot.columns and pd.notna(self.nav_pivot.loc[d0, fid]):
                nav = float(self.nav_pivot.loc[d0, fid])
                amount = self.initial_corpus * weight
                units = amount / nav
                lots.append(Lot(asset_id=fid, units=units, nav=nav, cost_basis=amount, acquisition_date=d0))
            else:
                cash_balance += self.initial_corpus * weight

        portfolio_history = []
        rebalance_events: List[RebalanceEvent] = []
        peak_value = self.initial_corpus
        current_fy = None
        fy_exempt_used = 0.0
        daily_cash_yield = (1.0 + 0.065) ** (1.0 / 252.0) - 1.0

        for i, d in enumerate(valid_dates):
            # Cash interest
            if cash_balance > 0:
                cash_balance *= (1.0 + daily_cash_yield)

            # Track FY for LTCG exemption reset (April 1)
            fy = f"{d.year}-{str(d.year+1)[-2:]}" if d.month >= 4 else f"{d.year-1}-{str(d.year)[-2:]}"
            if fy != current_fy:
                current_fy = fy
                fy_exempt_used = 0.0

            # Current valuation
            fund_values = {}
            total_val = cash_balance
            for lot in lots:
                if lot.asset_id in self.nav_pivot.columns and pd.notna(self.nav_pivot.loc[d, lot.asset_id]):
                    nav = float(self.nav_pivot.loc[d, lot.asset_id])
                    lot.nav = nav
                    val = lot.units * nav
                    fund_values[lot.asset_id] = fund_values.get(lot.asset_id, 0.0) + val
                    total_val += val

            if total_val > peak_value:
                peak_value = total_val
            drawdown = (total_val - peak_value) / peak_value if peak_value > 0 else 0.0

            portfolio_history.append({
                "date": d,
                "total_value": total_val,
                "cash": cash_balance,
                "peak_value": peak_value,
                "drawdown": drawdown
            })

            if mode == "BUY_AND_HOLD":
                continue

            # Active version targets
            if mode == "STATIC_v1.0":
                v_active = [v for v in self.versions if v.get("version_id") == "v1.0"][0]
            elif mode == "STATIC_v2.0":
                v_active = [v for v in self.versions if v.get("version_id") == "v2.0"][0]
            elif mode == "STATIC_v2.3":
                v_active = [v for v in self.versions if v.get("version_id") == "v2.3"][0]
            else:
                v_active = self.get_version_for_date(d)

            target_weights = self.get_fund_targets_for_date(v_active, d)

            # Check Rebalance Triggers (only after initial seasoning of 20 trading days)
            trigger = None
            if i >= 20:
                # 1. Tier 1: Drawdown Trigger
                if drawdown <= drawdown_threshold:
                    trigger = "DRAWDOWN"
                
                # 2. Tier 2: Asset Drift Trigger (>15% relative drift and >3% absolute drift)
                if not trigger:
                    for fid, tw in target_weights.items():
                        cw = fund_values.get(fid, 0.0) / total_val if total_val > 0 else 0.0
                        rel_drift = abs(cw - tw) / tw if tw > 0 else 0.0
                        if rel_drift > drift_rel_threshold and abs(cw - tw) > 0.03:
                            trigger = "DRIFT"
                            break

                # 3. Tier 3: Scheduled Annual Rebalance (late March / early April)
                if not trigger and i > 0:
                    prev_d = valid_dates[i-1]
                    if prev_d.month == 3 and d.month == 4:
                        trigger = "SCHEDULED"

            # Execute rebalancing if triggered (minimum interval of 60 days to prevent excessive churn)
            last_reb_date = rebalance_events[-1].event_date if rebalance_events else date(2000, 1, 1)
            days_since_last = (d - last_reb_date).days

            if trigger and days_since_last >= 60:
                sold_assets = {}
                bought_assets = {}
                turnover = 0.0
                realized_ltcg = 0.0
                realized_stcg = 0.0

                # Determine sales needed
                for fid, val in fund_values.items():
                    tw = target_weights.get(fid, 0.0)
                    target_val = total_val * tw
                    if val > target_val + 5000.0:
                        sell_amount = val - target_val
                        turnover += sell_amount
                        sold_assets[fid] = sell_amount

                        # FIFO lot reduction & tax computation
                        amount_to_sell = sell_amount
                        for lot in sorted([l for l in lots if l.asset_id == fid], key=lambda x: x.acquisition_date):
                            if amount_to_sell <= 0:
                                break
                            lot_val = lot.units * lot.nav
                            sell_from_lot = min(lot_val, amount_to_sell)
                            units_sold = sell_from_lot / lot.nav
                            cost_of_sold = units_sold * (lot.cost_basis / lot.units if lot.units > 0 else lot.nav)
                            gain = sell_from_lot - cost_of_sold

                            holding_days = (d - lot.acquisition_date).days
                            if holding_days >= 365:
                                realized_ltcg += max(0.0, gain)
                            else:
                                realized_stcg += max(0.0, gain)

                            lot.units -= units_sold
                            lot.cost_basis -= cost_of_sold
                            amount_to_sell -= sell_from_lot

                # Compute Tax Drag (Sec 112A: ₹1.25L annual exemption, 12.5% LTCG, 20% STCG)
                exemption_limit = 125000.0
                headroom = max(0.0, exemption_limit - fy_exempt_used)
                exempt_consumed = min(realized_ltcg, headroom)
                fy_exempt_used += exempt_consumed

                taxable_ltcg = max(0.0, realized_ltcg - exempt_consumed)
                ltcg_tax = taxable_ltcg * 0.125
                stcg_tax = realized_stcg * 0.20
                tax_drag = ltcg_tax + stcg_tax

                # Proceeds flow into cash net of tax drag
                cash_balance += (turnover - tax_drag)

                # Deploy cash into under-allocated target funds
                for fid, tw in target_weights.items():
                    target_val = total_val * tw
                    current_v = sum(l.units * l.nav for l in lots if l.asset_id == fid)
                    if target_val > current_v + 1000.0 and fid in self.nav_pivot.columns and pd.notna(self.nav_pivot.loc[d, fid]):
                        buy_amount = min(target_val - current_v, cash_balance)
                        if buy_amount > 100.0:
                            nav = float(self.nav_pivot.loc[d, fid])
                            units = buy_amount / nav
                            lots.append(Lot(asset_id=fid, units=units, nav=nav, cost_basis=buy_amount, acquisition_date=d))
                            bought_assets[fid] = buy_amount
                            cash_balance -= buy_amount

                # Clean depleted lots
                lots = [l for l in lots if l.units > 0.0001]

                rebalance_events.append(RebalanceEvent(
                    event_date=d,
                    trigger_type=trigger,
                    sold_assets=sold_assets,
                    bought_assets=bought_assets,
                    turnover_amount=turnover,
                    realized_ltcg=realized_ltcg,
                    realized_stcg=realized_stcg,
                    exemption_used=exempt_consumed,
                    tax_drag=tax_drag
                ))


        # Compile results
        hist_df = pd.DataFrame(portfolio_history)
        hist_df["daily_return"] = hist_df["total_value"].pct_change().fillna(0.0)

        n_days = len(hist_df)
        years = n_days / 252.0
        final_val = hist_df["total_value"].iloc[-1]
        cagr = ((final_val / self.initial_corpus) ** (1.0 / years) - 1.0) * 100.0 if years > 0 else 0.0
        ann_vol = hist_df["daily_return"].std() * np.sqrt(252.0) * 100.0
        max_dd = hist_df["drawdown"].min() * 100.0

        rf = 6.50
        sharpe = (cagr - rf) / ann_vol if ann_vol > 0 else 0.0

        downside = hist_df["daily_return"][hist_df["daily_return"] < 0]
        downside_std = downside.std() * np.sqrt(252.0) * 100.0
        sortino = (cagr - rf) / downside_std if downside_std > 0 else sharpe
        calmar = cagr / abs(max_dd) if abs(max_dd) > 0 else 0.0

        total_turnover = sum(e.turnover_amount for e in rebalance_events)
        total_tax_drag = sum(e.tax_drag for e in rebalance_events)
        total_exempt_used = sum(e.exemption_used for e in rebalance_events)
        total_realized_ltcg = sum(e.realized_ltcg for e in rebalance_events)

        triggers_count = {
            "DRAWDOWN": sum(1 for e in rebalance_events if e.trigger_type == "DRAWDOWN"),
            "DRIFT": sum(1 for e in rebalance_events if e.trigger_type == "DRIFT"),
            "SCHEDULED": sum(1 for e in rebalance_events if e.trigger_type == "SCHEDULED"),
            "TOTAL": len(rebalance_events)
        }

        return {
            "mode": mode,
            "start_date": str(valid_dates[0]),
            "end_date": str(valid_dates[-1]),
            "trading_days": n_days,
            "initial_value": self.initial_corpus,
            "final_value": round(final_val, 2),
            "cagr_pct": round(cagr, 2),
            "annualized_vol_pct": round(ann_vol, 2),
            "max_drawdown_pct": round(max_dd, 2),
            "sharpe_ratio": round(sharpe, 2),
            "sortino_ratio": round(sortino, 2),
            "calmar_ratio": round(calmar, 2),
            "rebalance_events_count": triggers_count,
            "total_turnover": round(total_turnover, 2),
            "turnover_pct_ann": round((total_turnover / (self.initial_corpus * years)) * 100.0, 2) if years > 0 else 0.0,
            "total_tax_drag": round(total_tax_drag, 2),
            "total_exempt_ltcg_harvested": round(total_exempt_used, 2),
            "total_realized_ltcg": round(total_realized_ltcg, 2),
            "rebalance_events": rebalance_events
        }

def main():
    parser = argparse.ArgumentParser(description="Portfolio OS - Allocation Walk-Forward Backtester")
    parser.add_argument("--drift", type=float, default=0.15, help="Relative drift threshold (default: 0.15 for 15%%)")
    parser.add_argument("--sweep", action="store_true", help="Run multi-threshold sensitivity sweep (15%%, 20%%, 25%%, 30%%, 35%%)")
    parser.add_argument("--corpus", type=float, default=1000000.0, help="Initial corpus in INR (default: 1,000,000)")
    args = parser.parse_args()

    duckdb_candidates = [
        "data/tax_ledger.duckdb",
        "../data/tax_ledger.duckdb",
        "../../data/tax_ledger.duckdb"
    ]
    config_candidates = [
        "core-node/rules/bucket_targets.yaml",
        "rules/bucket_targets.yaml",
        "../rules/bucket_targets.yaml",
        "../core-node/rules/bucket_targets.yaml"
    ]

    duckdb_path = find_file(duckdb_candidates)
    config_path = find_file(config_candidates)

    if not duckdb_path or not config_path:
        print(f"Error: Could not locate DuckDB database or bucket_targets.yaml")
        print(f"  DuckDB search: {duckdb_candidates} -> {duckdb_path}")
        print(f"  Config search: {config_candidates} -> {config_path}")
        sys.exit(1)

    print("=" * 80)
    print("PORTFOLIO OS - ALLOCATION WALK-FORWARD BACKTEST")
    print(f"DuckDB: {duckdb_path}")
    print(f"Config: {config_path}")
    print("=" * 80)

    tester = PortfolioBacktester(duckdb_path, config_path, initial_corpus=args.corpus)

    if args.sweep:
        thresholds = [0.15, 0.20, 0.25, 0.30, 0.35]
        print(f"\nSimulation Window: 2024-03-14 to 2026-09-13 (622 trading days)")
        print("\n=== SENSITIVITY SWEEP: RELATIVE DRIFT THRESHOLD ===")
        for mode in ["DYNAMIC_WALK_FORWARD", "STATIC_v2.3"]:
            mode_label = "Walk-Forward (v1.0 -> v2.0 -> v2.3)" if mode == "DYNAMIC_WALK_FORWARD" else "Static v2.3 Policy"
            print(f"\n--- Mode: {mode_label} ---")
            header_sw = f"{'Drift Thresh':<12} | {'Rebalances (DD/Drift)':<22} | {'Turnover':<12} | {'Ann Turn':<10} | {'CAGR':<8} | {'Vol':<8} | {'MaxDD':<8} | {'Sharpe':<8} | {'Sortino':<8} | {'Tax Drag':<10}"
            print(header_sw)
            print("-" * len(header_sw))
            for t in thresholds:
                res = tester.run_backtest(mode=mode, drift_rel_threshold=t)
                tc = res["rebalance_events_count"]
                rebs = f"{tc['TOTAL']} ({tc['DRAWDOWN']}/{tc['DRIFT']})"
                print(f"{t*100:5.1f}%       | {rebs:<22} | ₹{res['total_turnover']:>10,.0f} | {res['turnover_pct_ann']:>7.1f}% | {res['cagr_pct']:>6.2f}% | {res['annualized_vol_pct']:>6.2f}% | {res['max_drawdown_pct']:>6.2f}% | {res['sharpe_ratio']:>6.2f} | {res['sortino_ratio']:>7.2f} | ₹{res['total_tax_drag']:>7,.0f}")
        return

    modes = ["DYNAMIC_WALK_FORWARD", "STATIC_v1.0", "STATIC_v2.0", "STATIC_v2.3", "BUY_AND_HOLD"]
    results = []

    for m in modes:
        res = tester.run_backtest(mode=m, drift_rel_threshold=args.drift)
        results.append(res)

    # Format side-by-side comparison table
    print(f"\nSimulation Window: {results[0]['start_date']} to {results[0]['end_date']} ({results[0]['trading_days']} trading days)")
    print(f"Drift Threshold: {args.drift*100:.1f}%\n")

    header = f"{'Metric':<32} | {'Walk-Forward':<14} | {'Static v1.0':<12} | {'Static v2.0':<12} | {'Static v2.3':<12} | {'Buy & Hold':<12}"
    divider = "-" * len(header)
    print(header)
    print(divider)

    def row(label, key, fmt_fn=lambda x: str(x)):
        vals = [fmt_fn(r.get(key)) for r in results]
        return f"{label:<32} | {vals[0]:<14} | {vals[1]:<12} | {vals[2]:<12} | {vals[3]:<12} | {vals[4]:<12}"

    print(row("Final Portfolio Value (₹)", "final_value", lambda v: f"₹{v:,.0f}"))
    print(row("CAGR (%)", "cagr_pct", lambda v: f"{v:.2f}%"))
    print(row("Annualized Volatility (%)", "annualized_vol_pct", lambda v: f"{v:.2f}%"))
    print(row("Maximum Drawdown (%)", "max_drawdown_pct", lambda v: f"{v:.2f}%"))
    print(row("Sharpe Ratio (Rf=6.5%)", "sharpe_ratio", lambda v: f"{v:.2f}"))
    print(row("Sortino Ratio", "sortino_ratio", lambda v: f"{v:.2f}"))
    print(row("Calmar Ratio", "calmar_ratio", lambda v: f"{v:.2f}"))
    print(divider)
    print(row("Total Rebalance Events", "rebalance_events_count", lambda v: str(v["TOTAL"])))
    print(row("  - Drawdown Triggers", "rebalance_events_count", lambda v: str(v["DRAWDOWN"])))
    print(row(f"  - Drift Triggers (>{args.drift*100:.0f}%)", "rebalance_events_count", lambda v: str(v["DRIFT"])))
    print(row("  - Scheduled Harvest", "rebalance_events_count", lambda v: str(v["SCHEDULED"])))
    print(divider)
    print(row("Total Turnover (₹)", "total_turnover", lambda v: f"₹{v:,.0f}"))
    print(row("Annualized Turnover Rate (%)", "turnover_pct_ann", lambda v: f"{v:.1f}%"))
    print(row("Sec 112A Exempt Harvested (₹)", "total_exempt_ltcg_harvested", lambda v: f"₹{v:,.0f}"))
    print(row("Total Realized LTCG (₹)", "total_realized_ltcg", lambda v: f"₹{v:,.0f}"))
    print(row("Estimated Tax Drag Incurred (₹)", "total_tax_drag", lambda v: f"₹{v:,.0f}"))
    print(divider)

    # Detailed trace of walk-forward events
    wf_events = results[0]["rebalance_events"]
    print(f"\nWalk-Forward Rebalance Events Detail ({len(wf_events)} events):")
    for idx, e in enumerate(wf_events, 1):
        sells = ", ".join([f"{k[:12]}: ₹{v:,.0f}" for k, v in e.sold_assets.items()])
        buys = ", ".join([f"{k[:12]}: ₹{v:,.0f}" for k, v in e.bought_assets.items()])
        print(f"  [{idx}] {e.event_date} | Trigger: {e.trigger_type:<9} | Turnover: ₹{e.turnover_amount:>8,.0f} | Tax Drag: ₹{e.tax_drag:>5,.0f} (Exempt: ₹{e.exemption_used:>6,.0f})")
        if sells:
            print(f"      Sold: {sells}")
        if buys:
            print(f"      Bought: {buys}")

if __name__ == "__main__":
    main()
