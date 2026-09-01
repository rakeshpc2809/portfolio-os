# Portfolio OS — Terminal HUD (TUI Client)

High-aesthetic, keyboard-driven Terminal User Interface (TUI) client for **Portfolio OS**, built with **Textual** and styled for OLED dark environments.

---

## 🌟 Key Features

- **Live Valuation & XIRR**: Queries `core-node` (`:8080/api/v1/sync/snapshot`) in real-time with zero database locks.
- **Asset Allocation & Policy Drift**: Visual multi-color stacked asset bar with real-time drift alerts vs target tolerance.
- **Section 112A Tax-Harvesting**: LTCG tax-free threshold progress gauge (`₹1.25L` exemption headroom tracking).
- **Quant Risk & FIRE Telemetry**: 10,000-iteration Monte Carlo Safe Withdrawal Rate (SWR) survival score.
- **Interactive Modals**:
  - `[t]`: Tax Lots drill-down table with holding period and LTCG eligibility.
  - `[p]`: Rebalance Waterfall Plan detailing tax-optimized asset rotation.
  - `[r]`: Force immediate API refresh.
  - `[q]`: Exit TUI.

---

## 🚀 Running Standalone

```bash
# Install dependencies
pip install -r requirements.txt

# Run TUI
python portfolio_os_tui.py
```
