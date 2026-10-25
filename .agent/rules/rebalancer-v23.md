# PORTFOLIO OS v2.3 — QUANT & TAX ENGINE INVARIANTS

1. **Strict Mutual Fund Boundary (Invariant 1):**
   - Never include EPF or bank savings balances in `totalPortfolioValue` or `fireInvestableNetWorth`.
   - Portfolio OS tracks strictly mutual fund/ETF units in `tax_events`.

2. **RTA FIFO & Exit-Load Law (`Sec 45(2A)`):**
   - Indian RTAs (CAMS/KFintech) execute redemptions strictly FIFO per Folio/ISIN.
   - Never write lot-selection comparators that cherry-pick a newer lot in a scheme while an older lot in the same scheme remains open.
   - Always check `ExitLoadCalculator.getRequiredExitLoadDays(assetId, assetName)` (`730d` PPFAS, `365d` Nippon Small Cap, `15d` Invesco Arbitrage & Motilal Gold/Silver FoF) before selecting a lot for non-urgent drift/scheduled trims.

3. **Finance Act (No. 2) 2024 Tax Rules (`FY2026-27.yaml`):**
   - Equity (`>=65%` domestic equity, including Invesco Arbitrage `INF205K01KR8`): LTCG at `>12 months (365d)` @ `12.5%` above `₹1,25,000` annual `Sec 112A` exemption; STCG @ `20%`.
   - Unlisted Gold/Silver FoF (`INF247L01BM8`): LTCG at `>24 months (730d)` @ `12.5%` (no `Sec 112A` exemption); STCG `<730d` @ marginal `SLAB_RATE` (`30%`).
   - Specified Debt (`Sec 50AA`, post-Apr 1, 2023): Always `SHORT_TERM` @ `SLAB_RATE` (`30%`).

4. **Rebalancer Waterfall & Routing:**
   - Step 0 (`LEGACY_HOLDINGS` phase-out) always executes first: 100% of LTCG and loss-making legacy lots liquidate without stalling at the ₹1.25L exemption cap.
   - Core trims (`EQUITY_CORE`) must route via `RebalanceWaterfallEngine.routeTrimProceeds` (Gold deficit -> Arbitrage 10% target -> Core deficit -> Arbitrage 15% cap -> Cash overflow) and NEVER into `EQUITY_SATELLITE`.
   - Sell-side triggers share a global 30-day cooldown (`REBALANCE_COOLDOWN_DAYS = 30`). `GOLD_FLOOR_BACKSTOP` is buy-only and exempt from sell cooldown.

5. **Zero-Fabrication & Zero-PII Policy:**
   - Missing live NAVs must throw via `NavResolver.requireValidNav` — never substitute `0.0` or `1.0`.
   - Outbound payloads (`portfolio_state.enc`, `holdings_snapshot.csv`, `ledger_events.csv`) must NEVER contain `folio_no`, PAN, or email addresses.

6. **Verification Gate:**
   - Before completing any change to `core-node`, `quant-sidecar`, `tui`, `mobile-app`, or `web`, run `just check` to verify all 5 test suites pass.
