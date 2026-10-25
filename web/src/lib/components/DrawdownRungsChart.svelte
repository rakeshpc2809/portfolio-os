<script lang="ts">
  import { Shield, TrendingDown, Zap, ArrowDownCircle, AlertCircle, CheckCircle2 } from '@lucide/svelte';
  import { formatINR, formatCompactINR } from '../formatters';

  interface Props {
    drawdownPct?: number;
    liquidBufferValue?: number;
    quietMode?: boolean;
  }

  let {
    drawdownPct = -4.2,
    liquidBufferValue = 0,
    quietMode = false
  }: Props = $props();

  const rungs = [
    {
      level: 1,
      threshold: -10.0,
      deployFraction: 0.3333,
      deployLabel: '33% of Liquid Buffer',
      targetBucket: 'EQUITY_CORE',
      desc: 'Deploy 1/3 of exit-load-free Invesco Arbitrage reserves into underweight Core Equity (ICICI LargeMidcap 250 & Parag Parikh Flexi Cap).'
    },
    {
      level: 2,
      threshold: -15.0,
      deployFraction: 0.3333,
      deployLabel: 'Second 33% of Buffer',
      targetBucket: 'CORE & FACTOR SATELLITE',
      desc: 'Deploy second 1/3 into Core & Satellite dips (ICICI Value 30, Edelweiss Momentum 50, Nippon Small Cap).'
    },
    {
      level: 3,
      threshold: -20.0,
      deployFraction: 0.3334,
      deployLabel: 'Final 33% (Full Buffer)',
      targetBucket: 'MAX CORE COMPOUNDING',
      desc: 'Market capitulation trigger — fully deploy remaining arbitrage buffer into long-term Core index compounding.'
    }
  ];

  const activeRung = $derived(() => {
    if (drawdownPct <= -20.0) return 3;
    if (drawdownPct <= -15.0) return 2;
    if (drawdownPct <= -10.0) return 1;
    return 0; // Standing by in normal regime
  });

  const clampedPos = $derived(Math.min(100, Math.max(0, (Math.abs(drawdownPct) / 20.0) * 100)));
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
      <span class="status-dot {activeRung() > 0 ? 'active' : 'standby'}"></span>
      <span>{activeRung() > 0 ? `RUNG ${activeRung()} ACTIVE` : 'REGIME: STANDBY (<10% DRAWDOWN)'}</span>
    </div>
  </div>

  <div class="drawdown-body">
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
        <div class="drawdown-needle" style="left: {clampedPos}%;">
          <div class="needle-flag mono">
            {drawdownPct.toFixed(1)}%
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
              RUNG {r.level} ({r.threshold}%)
            </div>
            {#if isTriggered}
              <span class="status-tag triggered">DEPLOY ACTIVE</span>
            {:else if activeRung() === r.level - 1}
              <span class="status-tag next">NEXT TRIGGER</span>
            {:else}
              <span class="status-tag standing">STANDBY</span>
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
    background: rgba(6, 182, 212, 0.1);
    border: 1px solid rgba(6, 182, 212, 0.25);
    border-radius: var(--radius-md);
  }

  .header-title {
    font-size: 0.95rem;
    font-weight: 700;
    color: #ffffff;
    margin: 0;
  }

  .header-sub {
    font-size: 0.72rem;
    color: var(--text-muted);
  }

  .market-status-pill {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.68rem;
    font-weight: 700;
    padding: 3px 8px;
    border-radius: var(--radius-xs);
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid var(--border-hairline);
    color: var(--text-muted);
  }

  .status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
  }
  .status-dot.standby {
    background: var(--accent-emerald);
    box-shadow: 0 0 6px var(--accent-emerald);
  }
  .status-dot.active {
    background: var(--accent-amber);
    box-shadow: 0 0 6px var(--accent-amber);
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
    font-size: 0.68rem;
    font-family: var(--font-mono);
  }

  .gauge-track {
    position: relative;
    height: 12px;
    border-radius: var(--radius-pill);
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid var(--border-hairline);
    overflow: visible;
  }

  .zone {
    position: absolute;
    top: 0;
    bottom: 0;
  }
  .zone-1 {
    background: linear-gradient(90deg, rgba(16, 185, 129, 0.2), rgba(245, 158, 11, 0.25));
  }
  .zone-2 {
    background: linear-gradient(90deg, rgba(245, 158, 11, 0.25), rgba(244, 63, 94, 0.3));
  }
  .zone-3 {
    background: linear-gradient(90deg, rgba(244, 63, 94, 0.3), rgba(244, 63, 94, 0.5));
    border-top-right-radius: var(--radius-pill);
    border-bottom-right-radius: var(--radius-pill);
  }

  .drawdown-needle {
    position: absolute;
    top: -24px;
    transform: translateX(-50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    transition: left 0.4s ease;
    z-index: 5;
  }

  .needle-flag {
    font-size: 0.65rem;
    font-weight: 800;
    background: var(--accent-cyan);
    color: #030712;
    padding: 1px 6px;
    border-radius: 4px;
    box-shadow: 0 0 10px rgba(6, 182, 212, 0.6);
  }

  .needle-line {
    width: 2px;
    height: 28px;
    background: var(--accent-cyan);
    box-shadow: 0 0 6px var(--accent-cyan);
  }

  /* Rungs Grid */
  .rungs-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }

  .rung-item {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    transition: all 0.2s ease;
  }
  .rung-item.next-rung {
    border-color: rgba(6, 182, 212, 0.4);
    background: rgba(6, 182, 212, 0.03);
  }
  .rung-item.triggered {
    border-color: rgba(245, 158, 11, 0.5);
    background: rgba(245, 158, 11, 0.05);
  }

  .rung-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .rung-badge {
    font-size: 0.68rem;
    font-weight: 700;
    color: #ffffff;
  }

  .status-tag {
    font-family: var(--font-mono);
    font-size: 0.62rem;
    font-weight: 700;
    padding: 1px 6px;
    border-radius: 3px;
  }
  .status-tag.standing {
    background: rgba(255, 255, 255, 0.04);
    color: var(--text-muted);
  }
  .status-tag.next {
    background: rgba(6, 182, 212, 0.15);
    color: var(--accent-cyan);
    border: 1px solid rgba(6, 182, 212, 0.3);
  }
  .status-tag.triggered {
    background: rgba(245, 158, 11, 0.15);
    color: var(--accent-amber);
    border: 1px solid rgba(245, 158, 11, 0.35);
  }

  .rung-main {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .deploy-action {
    font-size: 0.85rem;
    color: #ffffff;
  }

  .deploy-target {
    font-size: 0.72rem;
  }

  .deploy-amount {
    font-size: 1.05rem;
    color: var(--accent-emerald-bright);
    margin-top: 2px;
  }

  .rung-desc {
    font-size: 0.72rem;
    color: var(--text-muted);
    line-height: 1.35;
    margin: 4px 0 0 0;
  }

  @media (max-width: 860px) {
    .rungs-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
