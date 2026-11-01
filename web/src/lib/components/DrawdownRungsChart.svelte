<script lang="ts">
  import { Shield, TrendingDown, Zap, ArrowDownCircle, AlertCircle, CheckCircle2 } from '@lucide/svelte';
  import { formatINR, formatCompactINR } from '../formatters';

  interface Props {
    drawdownPct?: number;
    liquidBufferValue?: number;
    quietMode?: boolean;
    status?: string;
  }

  let {
    drawdownPct = 0,
    liquidBufferValue = 0,
    quietMode = false,
    status = 'AVAILABLE'
  }: Props = $props();

  const normalizedDrawdown = $derived(drawdownPct > 0 ? -drawdownPct : drawdownPct);

  const rungs = [
    {
      level: 1,
      threshold: -10.0,
      deployFraction: 0.25,
      deployLabel: '25% of Liquid Buffer',
      targetBucket: 'EQUITY_CORE',
      desc: 'Deploy 25% of exit-load-free Invesco Arbitrage reserves into underweight Core Equity (ICICI LargeMidcap 250 & Parag Parikh Flexi Cap).'
    },
    {
      level: 2,
      threshold: -15.0,
      deployFraction: 0.50,
      deployLabel: 'Cumulative 50% of Buffer',
      targetBucket: 'CORE & FACTOR SATELLITE',
      desc: 'Deploy 50% cumulative tranche into Core & Satellite dips (ICICI Value 30, Edelweiss Momentum 50, Nippon Small Cap).'
    },
    {
      level: 3,
      threshold: -20.0,
      deployFraction: 1.0,
      deployLabel: '100% (Full Liquid Reserve)',
      targetBucket: 'MAX CORE COMPOUNDING',
      desc: 'Market capitulation trigger — fully deploy remaining arbitrage buffer into long-term Core index compounding.'
    }
  ];

  const activeRung = $derived(() => {
    if (status === 'UNAVAILABLE') return 0;
    if (normalizedDrawdown <= -20.0) return 3;
    if (normalizedDrawdown <= -15.0) return 2;
    if (normalizedDrawdown <= -10.0) return 1;
    return 0; // Standing by in normal regime
  });

  const clampedPos = $derived(Math.min(100, Math.max(0, (Math.abs(normalizedDrawdown) / 20.0) * 100)));
</script>

<div class="drawdown-card obsidian-card">
  <div class="drawdown-header">
    <div class="header-left">
      <div class="icon-wrap">
        <TrendingDown size={16} class="text-cyan" />
      </div>
      <div>
        <h3 class="header-title">Nifty 500 Drawdown Rungs & Tactical Inflow</h3>
        <span class="header-sub">Pre-committed buy-the-dip rule engine (Finance Act 2024 / v2.3 Spec)</span>
      </div>
    </div>
    <div class="market-status-pill mono">
      <span class="status-dot {status === 'UNAVAILABLE' ? 'warning' : (activeRung() > 0 ? 'active' : 'standby')}"></span>
      <span>{status === 'UNAVAILABLE' ? 'BENCHMARK MONITORING UNAVAILABLE' : (activeRung() > 0 ? `RUNG ${activeRung()} ACTIVE` : 'REGIME: STANDBY (<10% DRAWDOWN)')}</span>
    </div>
  </div>

  <div class="drawdown-body">
    {#if status === 'UNAVAILABLE'}
      <div class="drawdown-unavailable-banner">
        <AlertCircle size={16} class="text-amber" />
        <span>Notice: Nifty 500 TRI benchmark feed is unseeded or unavailable. Drawdown-induced rebalancing is temporarily suspended while portfolio sync remains operational.</span>
      </div>
    {/if}
    <!-- Visual Gauge Bar -->
    <div class="gauge-container">
      <div class="gauge-labels">
        <span class="gauge-end text-muted">ATH (0.0%)</span>
        <span class="gauge-marker text-muted">-10.0% (Rung 1)</span>
        <span class="gauge-marker text-muted">-15.0% (Rung 2)</span>
        <span class="gauge-end text-rose">-20.0% (Rung 3)</span>
      </div>

      <div class="gauge-track">
        <div class="track-bg"></div>
        <div class="zone zone-1" style="left: 0%; width: 50%;"></div>
        <div class="zone zone-2" style="left: 50%; width: 25%;"></div>
        <div class="zone zone-3" style="left: 75%; width: 25%;"></div>

        <!-- Current Drawdown Indicator Needle -->
        <div class="drawdown-needle" style="--needle-pct: {clampedPos}%;">
          <div class="needle-flag mono">
            {normalizedDrawdown.toFixed(1)}%
          </div>
          <div class="needle-line"></div>
        </div>
      </div>
    </div>

    <!-- Tactical Deployment Rung Cards -->
    <div class="rungs-grid">
      {#each rungs as r}
        {@const isTriggered = activeRung() >= r.level}
        {@const deployAmt = liquidBufferValue * r.deployFraction}
        <div class="rung-item" class:triggered={isTriggered} class:next-rung={activeRung() === r.level - 1}>
          <div class="rung-top">
            <div class="rung-badge mono">
              Rung {r.level} ({r.threshold}%)
            </div>
            {#if isTriggered}
              <span class="status-tag triggered">Deploy active</span>
            {:else if activeRung() === r.level - 1}
              <span class="status-tag next">Next trigger</span>
            {:else}
              <span class="status-tag standing">Standby</span>
            {/if}
          </div>

          <div class="rung-main">
            <span class="deploy-action bold">{r.deployLabel}</span>
            <span class="deploy-target text-cyan mono font-medium">➔ {r.targetBucket}</span>
          </div>

          <div class="deploy-amount mono bold">
            {#if quietMode}
              ₹ •••,•••
            {:else}
              {deployAmt > 0 ? formatINR(deployAmt) : 'Buffer Standing By'}
            {/if}
          </div>

          <p class="rung-desc">{r.desc}</p>
        </div>
      {/each}
    </div>
  </div>
</div>

<style>
  .drawdown-card {
    padding: 18px 20px;
    margin-top: 16px;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
  }

  .drawdown-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    flex-wrap: wrap;
    gap: 8px;
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .icon-wrap {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    background: var(--color-cool-soft);
    border: 1px solid var(--color-cool);
    border-radius: var(--radius-pill);
  }

  .header-title {
    font-size: 0.95rem;
    font-weight: 700;
    color: var(--color-ink);
    margin: 0;
  }

  .header-sub {
    font-size: 0.72rem;
    color: var(--color-muted);
  }

  .market-status-pill {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.75rem;
    font-weight: 600;
    padding: 3px 8px;
    border-radius: var(--radius-pill);
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    color: var(--color-muted);
  }

  .status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
  }
  .status-dot.standby {
    background: var(--color-ok);
  }
  .status-dot.active {
    background: var(--color-accent);
  }
  .status-dot.warning {
    background: var(--color-bad);
  }

  .drawdown-unavailable-banner {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    background: var(--color-accent-soft);
    border: 1px solid var(--color-accent);
    border-radius: var(--radius-card);
    color: var(--color-accent);
    font-size: 0.78rem;
    font-weight: 500;
    line-height: 1.4;
  }

  .drawdown-body {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  /* Gauge */
  .gauge-container {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .gauge-labels {
    display: flex;
    justify-content: space-between;
    font-size: 0.75rem;
    font-family: var(--font-outlier);
  }

  .gauge-track {
    position: relative;
    height: 12px;
    border-radius: var(--radius-pill);
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    overflow: visible;
  }

  .zone {
    position: absolute;
    top: 0;
    bottom: 0;
  }
  .zone-1 {
    background: var(--color-ok-soft);
    border-top-left-radius: var(--radius-pill);
    border-bottom-left-radius: var(--radius-pill);
  }
  .zone-2 {
    background: var(--color-accent-soft);
  }
  .zone-3 {
    background: var(--color-bad-soft);
    border-top-right-radius: var(--radius-pill);
    border-bottom-right-radius: var(--radius-pill);
  }

  .drawdown-needle {
    position: absolute;
    top: -24px;
    left: var(--needle-pct, 0%);
    transform: translateX(-50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    z-index: 5;
  }

  .needle-flag {
    font-size: 0.75rem;
    font-weight: 700;
    font-family: var(--font-outlier);
    background: var(--color-accent);
    color: var(--color-accent-ink);
    padding: 1px 6px;
    border-radius: var(--radius-pill);
  }

  .needle-line {
    width: 2px;
    height: 28px;
    background: var(--color-accent);
  }

  /* Rungs Grid */
  .rungs-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }

  .rung-item {
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    transition: border-color 0.15s ease, background 0.15s ease;
  }
  .rung-item.next-rung {
    border-color: var(--color-cool);
    background: var(--color-cool-soft);
  }
  .rung-item.triggered {
    border-color: var(--color-accent);
    background: var(--color-accent-soft);
  }

  .rung-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .rung-badge {
    font-size: 0.75rem;
    font-weight: 600;
    font-family: var(--font-outlier);
    color: var(--color-ink);
  }

  .status-tag {
    font-family: var(--font-outlier);
    font-size: 0.75rem;
    font-weight: 600;
    padding: 1px 6px;
    border-radius: var(--radius-pill);
  }
  .status-tag.standing {
    background: var(--color-paper);
    color: var(--color-muted);
    border: 1px solid var(--color-rule);
  }
  .status-tag.next {
    background: var(--color-cool-soft);
    color: var(--color-cool);
    border: 1px solid var(--color-cool);
  }
  .status-tag.triggered {
    background: var(--color-accent-soft);
    color: var(--color-accent);
    border: 1px solid var(--color-accent);
  }

  .rung-main {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .deploy-action {
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--color-ink);
  }

  .deploy-target {
    font-size: 0.75rem;
    color: var(--color-cool);
  }

  .deploy-amount {
    font-size: 1.05rem;
    color: var(--color-ok);
    margin-top: 2px;
  }

  .rung-desc {
    font-size: 0.75rem;
    color: var(--color-muted);
    line-height: 1.35;
    margin: 4px 0 0 0;
  }

  @media (prefers-reduced-motion: reduce) {
    .rung-item {
      transition: none !important;
    }
  }

  @media (max-width: 860px) {
    .rungs-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
