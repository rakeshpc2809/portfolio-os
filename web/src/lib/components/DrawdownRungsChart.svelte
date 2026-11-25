<script lang="ts">
  import { TrendingDown, AlertCircle } from '@lucide/svelte';
  import type { HoldingDto, PolicyMetaDto } from '../api';
  import { formatCompactINR } from '../formatters';

  interface Props {
    drawdownPct?: number;
    drawdownContext?: {
      current_drawdown_pct?: number;
      active_tier?: number;
      armed_tier?: number;
      rolling_high_value?: number | string;
      current_value?: number | string;
      arbitrage_exit_load_free_value?: number | string;
      status?: string;
    };
    policyMeta?: PolicyMetaDto;
    holdings?: HoldingDto[];
    quietMode?: boolean;
    status?: string;
  }

  let {
    drawdownPct = 0,
    drawdownContext = undefined,
    policyMeta = undefined,
    holdings = [],
    quietMode = false,
    status = 'AVAILABLE'
  }: Props = $props();

  const effectiveStatus = $derived(drawdownContext?.status ?? status ?? 'AVAILABLE');
  const rawDd = $derived(Number(drawdownContext?.current_drawdown_pct ?? drawdownPct ?? 0));
  const absDrawdown = $derived(Math.abs(rawDd));

  const configuredRungs = $derived.by(() => {
    const list = policyMeta?.drawdown_rungs ?? [];
    return [...list].sort((a, b) => a.drawdown_pct - b.drawdown_pct);
  });

  const maxRungPct = $derived.by(() => {
    if (configuredRungs.length === 0) return 30;
    return Math.max(...configuredRungs.map((r) => Math.abs(r.drawdown_pct)));
  });

  const holdMin = $derived(Math.abs(policyMeta?.drawdown_hold_zone_min_pct ?? 5));
  const holdMax = $derived(Math.abs(policyMeta?.drawdown_hold_zone_max_pct ?? 10));

  const armedTier = $derived.by(() => {
    const ctxTier = drawdownContext?.armed_tier ?? drawdownContext?.active_tier;
    if (ctxTier !== undefined && ctxTier !== null && ctxTier > 0) return ctxTier;
    let tier = 0;
    configuredRungs.forEach((r, idx) => {
      if (absDrawdown >= Math.abs(r.drawdown_pct)) tier = idx + 1;
    });
    return tier;
  });

  const isUnavailable = $derived(effectiveStatus === 'UNAVAILABLE' || configuredRungs.length === 0);

  const clampedPinPct = $derived(
    maxRungPct > 0 ? Math.min(100, Math.max(0, (absDrawdown / maxRungPct) * 100)) : 0
  );

  const readyArbitrageDryPowder = $derived.by(() => {
    const liquidHoldings = (holdings || []).filter((h) => {
      const b = (h.bucket || '').toUpperCase();
      return b.includes('LIQUID') || b.includes('ARBITRAGE');
    });
    if (liquidHoldings.length > 0) {
      return liquidHoldings.reduce(
        (sum, h) => sum + (Number(h.exit_load_free_value ?? h.current_value) || 0),
        0
      );
    }
    return Number(drawdownContext?.arbitrage_exit_load_free_value ?? 0);
  });

  function formatBenchLevel(val: number | string | undefined): string {
    if (val === undefined || val === null || val === '') return '—';
    const num = Number(val);
    return isNaN(num) ? String(val) : num.toFixed(2);
  }
</script>

<section class="dd-card" aria-label="Nifty 500 Drawdown Ladder">
  <div class="dd-header">
    <div class="dd-title-group">
      <TrendingDown size={15} class="dd-icon" />
      <h3 class="dd-title">Nifty 500 Drawdown Ladder</h3>
      <span class="dd-sub tnum">
        {#if isUnavailable}
          Benchmark feed unavailable
        {:else if armedTier > 0}
          Rung {armedTier} Armed (-{absDrawdown.toFixed(1)}%)
        {:else}
          Standby (-{absDrawdown.toFixed(1)}%)
        {/if}
      </span>
    </div>

    <span class="dd-status-pill" class:unavailable={isUnavailable} class:armed={!isUnavailable && armedTier > 0}>
      {isUnavailable ? 'SUSPENDED' : armedTier > 0 ? `TIER ${armedTier} ARMED` : 'STANDBY'}
    </span>
  </div>

  {#if isUnavailable}
    <div class="dd-suspended">
      <AlertCircle size={15} />
      <span>
        Nifty 500 TRI benchmark feed is unseeded or unavailable. Drawdown-triggered deployment is suspended; band &amp; scheduled rules remain active.
      </span>
    </div>
  {:else}
    <!-- Single-Row Horizontal Ruler -->
    <div class="ruler-wrap">
      <div class="ruler-track">
        <!-- Shaded Hold Zone -->
        <div
          class="hold-zone"
          style="left: {(holdMin / maxRungPct) * 100}%; width: {Math.max(0, ((holdMax - holdMin) / maxRungPct) * 100)}%;"
          title="Hold Zone ({holdMin}% to {holdMax}%)"
        ></div>

        <!-- Configured Rung Ticks -->
        {#each configuredRungs as rung}
          {@const pos = (Math.abs(rung.drawdown_pct) / maxRungPct) * 100}
          {@const isHit = absDrawdown >= Math.abs(rung.drawdown_pct)}
          <div class="rung-tick" class:hit={isHit} style="left: {pos}%;">
            <span class="tick-line"></span>
            <span class="tick-label tnum">
              -{Math.abs(rung.drawdown_pct).toFixed(0)}% · {Math.round(rung.liquid_deploy_fraction * 100)}%
            </span>
          </div>
        {/each}

        <!-- Live Drawdown Pin -->
        <div class="dd-pin" style="left: {clampedPinPct}%;">
          <span class="pin-head"></span>
          <span class="pin-badge tnum">-{absDrawdown.toFixed(1)}%</span>
        </div>
      </div>

      <div class="ruler-ends tnum">
        <span>0% ATH</span>
        <span>Hold Zone (-{holdMin}% to -{holdMax}%)</span>
        <span>-{maxRungPct.toFixed(0)}% Max</span>
      </div>
    </div>

    <!-- Footer Telemetry -->
    <div class="dd-footer tnum">
      <div class="meta-cell">
        <span class="meta-label">ATH Watermark</span>
        <span class="meta-val">{formatBenchLevel(drawdownContext?.rolling_high_value)}</span>
      </div>
      <div class="meta-cell">
        <span class="meta-label">Current Level</span>
        <span class="meta-val">{formatBenchLevel(drawdownContext?.current_value)}</span>
      </div>
      <div class="meta-cell">
        <span class="meta-label">Armed Tier</span>
        <span class="meta-val">{armedTier > 0 ? `Rung ${armedTier}` : 'None (0)'}</span>
      </div>
      <div class="meta-cell">
        <span class="meta-label">Ready Arbitrage Dry Powder</span>
        <span class="meta-val highlight" data-sensitive="currency">
          {formatCompactINR(readyArbitrageDryPowder, quietMode)}
        </span>
      </div>
    </div>
  {/if}
</section>

<style>
  .dd-card {
    background: var(--bg-surface);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-md);
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .dd-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .dd-title-group {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  :global(.dd-icon) {
    color: var(--state-action);
  }

  .dd-title {
    font-size: 0.92rem;
    font-weight: 600;
    color: var(--ink-primary);
  }

  .dd-sub {
    font-size: 0.78rem;
    color: var(--ink-muted);
  }

  .dd-status-pill {
    font-size: 0.68rem;
    font-weight: 600;
    letter-spacing: 0.04em;
    padding: 3px 8px;
    border-radius: var(--radius-sm);
    background: var(--bg-elevated);
    color: var(--ink-secondary);
    border: 1px solid var(--border-hair);
  }

  .dd-status-pill.armed {
    background: var(--state-danger-soft);
    color: var(--state-danger);
    border-color: color-mix(in oklch, var(--state-danger) 35%, transparent);
  }

  .dd-status-pill.unavailable {
    background: var(--state-warn-soft);
    color: var(--state-warn);
    border-color: color-mix(in oklch, var(--state-warn) 35%, transparent);
  }

  .dd-suspended {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 11px 14px;
    border-radius: var(--radius-sm);
    background: var(--state-warn-soft);
    color: var(--state-warn);
    font-size: 0.8rem;
    line-height: 1.45;
  }

  .ruler-wrap {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding-top: 18px;
    padding-bottom: 18px;
  }

  .ruler-track {
    position: relative;
    height: 8px;
    background: var(--bg-elevated);
    border-radius: 999px;
    border: 1px solid var(--border-hair);
  }

  .hold-zone {
    position: absolute;
    top: 0;
    bottom: 0;
    background: color-mix(in oklch, var(--state-warn) 25%, transparent);
    border-radius: 999px;
  }

  .rung-tick {
    position: absolute;
    top: -4px;
    transform: translateX(-50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    pointer-events: none;
  }

  .tick-line {
    width: 1.5px;
    height: 16px;
    background: var(--border-strong);
  }

  .rung-tick.hit .tick-line {
    background: var(--state-danger);
  }

  .tick-label {
    margin-top: 4px;
    font-size: 0.68rem;
    color: var(--ink-muted);
    white-space: nowrap;
  }

  .rung-tick.hit .tick-label {
    color: var(--state-danger);
    font-weight: 600;
  }

  .dd-pin {
    position: absolute;
    top: -5px;
    transform: translateX(-50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    z-index: 2;
  }

  .pin-head {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: var(--state-action);
    border: 2px solid var(--bg-canvas);
  }

  .pin-badge {
    position: absolute;
    top: -22px;
    font-size: 0.7rem;
    font-weight: 600;
    color: var(--ink-primary);
    background: var(--bg-elevated);
    border: 1px solid var(--border-strong);
    padding: 1px 6px;
    border-radius: var(--radius-sm);
    white-space: nowrap;
  }

  .ruler-ends {
    display: flex;
    justify-content: space-between;
    font-size: 0.7rem;
    color: var(--ink-muted);
    margin-top: 8px;
    gap: 8px;
    flex-wrap: wrap;
  }

  .dd-footer {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--border-hair);
  }

  .meta-cell {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .meta-label {
    font-size: 0.7rem;
    color: var(--ink-muted);
  }

  .meta-val {
    font-size: 0.84rem;
    font-weight: 600;
    color: var(--ink-primary);
  }

  .meta-val.highlight {
    color: var(--bucket-liquid);
  }

  @media (max-width: 768px) {
    .dd-footer {
      grid-template-columns: repeat(2, 1fr);
    }
  }
</style>
