<script lang="ts">
  import { fetchJson, type HoldingDto, type RebalancePlanDto } from '../api';
  import { formatCompactINR, formatINR } from '../formatters';
  import { Coins, RefreshCw, RotateCcw, AlertCircle } from '@lucide/svelte';

  interface Props {
    holdings?: HoldingDto[];
    netWorth?: number;
    quietMode?: boolean;
    prefillAmount?: number | null;
    autoRun?: boolean;
  }

  let {
    holdings = [],
    netWorth = 0,
    quietMode = false,
    prefillAmount = null,
    autoRun = true
  }: Props = $props();

  const PRESETS = [25000, 50000, 100000, 200000];

  let amount = $state(50000);
  let includeRebalance = $state(false);
  let loading = $state(false);
  let error = $state<string | null>(null);
  let simulatedPlan = $state<RebalancePlanDto | null>(null);
  let inputEl = $state<HTMLInputElement | null>(null);

  $effect(() => {
    if (prefillAmount && prefillAmount > 0) {
      amount = Math.round(prefillAmount);
      runSimulation();
      inputEl?.focus();
    }
  });

  $effect(() => {
    if (autoRun && !simulatedPlan && !loading && !error) {
      runSimulation();
    }
  });

  export async function runSimulation() {
    if (!amount || amount <= 0) {
      error = 'Enter a positive rupee amount.';
      return;
    }
    loading = true;
    error = null;
    try {
      let res: RebalancePlanDto;
      try {
        res = await fetchJson<RebalancePlanDto>('/rebalance/simulate-lumpsum', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ amount, includeRebalance })
        });
      } catch {
        res = await fetchJson<RebalancePlanDto>('/sync/rebalance/simulate-lumpsum', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ amount, includeRebalance })
        });
      }
      simulatedPlan = res;
    } catch (e: any) {
      error = e?.message || 'Simulation failed';
    } finally {
      loading = false;
    }
  }

  function setPreset(val: number) {
    amount = val;
    runSimulation();
  }

  function resetSimulation() {
    amount = 50000;
    includeRebalance = false;
    simulatedPlan = null;
    error = null;
  }

  const baseNetWorth = $derived(
    Number(netWorth || simulatedPlan?.summary?.net_worth || holdings.reduce((s, h) => s + (h.current_value || 0), 0))
  );

  const projectedTax = $derived(
    Number(simulatedPlan?.sell_side?.tax_summary?.total_tax_estimate ?? simulatedPlan?.summary?.projected_tax ?? 0)
  );

  const postNetWorth = $derived(
    baseNetWorth + (Number(amount) || 0) - (includeRebalance ? projectedTax : 0)
  );

  const buyBuckets = $derived(
    (simulatedPlan?.buy_side?.buckets ?? []).filter((b) => b.bucket !== 'LEGACY_HOLDINGS')
  );

  const sellOrders = $derived(
    simulatedPlan?.sell_side?.orders ?? simulatedPlan?.orders ?? []
  );

  function getBucketColor(bucket: string): string {
    switch (bucket) {
      case 'EQUITY_CORE': return 'var(--bucket-core)';
      case 'EQUITY_SATELLITE': return 'var(--bucket-satellite)';
      case 'GOLD_SILVER': return 'var(--bucket-gold)';
      case 'LIQUID_BUFFER': return 'var(--bucket-liquid)';
      default: return 'var(--bucket-core)';
    }
  }
</script>

<section class="router-card" aria-label="Fresh Bank Cash Router">
  <div class="router-header">
    <div class="router-title-group">
      <Coins size={15} class="router-icon" />
      <h3 class="router-title">Fresh Bank Cash Router</h3>
      <span class="sim-badge">SIMULATION PREVIEW · Read-Only · Live plan unchanged</span>
    </div>
    <button type="button" class="reset-btn" onclick={resetSimulation} title="Reset simulator">
      <RotateCcw size={13} />
      <span>Reset</span>
    </button>
  </div>

  <!-- Controls Row -->
  <div class="controls-row">
    <div class="amount-box">
      <label class="sr-only" for="cash-router-amount">Fresh cash amount in INR</label>
      <span class="rupee-prefix tnum">₹</span>
      <input
        id="cash-router-amount"
        bind:this={inputEl}
        type="number"
        min="1000"
        step="1000"
        bind:value={amount}
        class="amount-input tnum"
        onkeydown={(e) => { if (e.key === 'Enter') runSimulation(); }}
      />
      <button type="button" class="run-btn" onclick={runSimulation} disabled={loading}>
        <RefreshCw size={13} class={loading ? 'spin' : ''} />
        <span>{loading ? 'Routing…' : 'Simulate'}</span>
      </button>
    </div>

    <div class="preset-chips">
      {#each PRESETS as preset}
        <button
          type="button"
          class="chip-btn tnum"
          class:active={amount === preset}
          onclick={() => setPreset(preset)}
        >
          {formatCompactINR(preset, false)}
        </button>
      {/each}
    </div>

    <div class="mode-toggle" role="radiogroup" aria-label="Routing mode">
      <button
        type="button"
        class="mode-btn"
        class:active={!includeRebalance}
        onclick={() => { includeRebalance = false; runSimulation(); }}
      >
        Fresh Cash Only · 0 Sells
      </button>
      <button
        type="button"
        class="mode-btn"
        class:active={includeRebalance}
        onclick={() => { includeRebalance = true; runSimulation(); }}
      >
        Cash + Band Rebalance
      </button>
    </div>
  </div>

  {#if error}
    <div class="sim-error">
      <AlertCircle size={14} />
      <span>{error}</span>
    </div>
  {/if}

  {#if simulatedPlan}
    <!-- Post-Trade Summary Strip -->
    <div class="sim-summary tnum">
      <div class="sum-cell">
        <span class="sum-label">Current Corpus</span>
        <span class="sum-val" data-sensitive="currency">{formatINR(baseNetWorth, quietMode)}</span>
      </div>
      <div class="sum-cell">
        <span class="sum-label">Fresh Inflow</span>
        <span class="sum-val action" data-sensitive="currency">+{formatINR(amount, quietMode)}</span>
      </div>
      {#if includeRebalance}
        <div class="sum-cell">
          <span class="sum-label">Est. Rebalance Tax</span>
          <span class="sum-val warn" data-sensitive="currency">-{formatINR(projectedTax, quietMode)}</span>
        </div>
      {/if}
      <div class="sum-cell">
        <span class="sum-label">Projected Post-NW</span>
        <span class="sum-val ok" data-sensitive="currency">{formatINR(postNetWorth, quietMode)}</span>
      </div>
    </div>

    <!-- Pre -> Post Bucket Allocation Shifts -->
    <div class="shift-list">
      {#each buyBuckets as b}
        {@const pre = Number(b.current_pct ?? 0)}
        {@const post = Number(b.post_rebalance_pct ?? b.post_pct ?? pre)}
        {@const alloc = Number(b.amount_allocated ?? 0)}
        {@const color = getBucketColor(b.bucket)}
        <div class="shift-row">
          <div class="shift-top">
            <div class="shift-name-wrap">
              <span class="swatch" style="background: {color};"></span>
              <span class="shift-name">{b.bucket.replace(/_/g, ' ')}</span>
            </div>
            <div class="shift-nums tnum">
              <span class="pre-post">{pre.toFixed(1)}% → <strong style="color: {color};">{post.toFixed(1)}%</strong></span>
              <span class="target-ref">(Target {Number(b.target_pct ?? 0).toFixed(0)}%)</span>
              {#if alloc > 0}
                <span class="alloc-pill" data-sensitive="currency">+{formatINR(alloc, quietMode)}</span>
              {:else}
                <span class="alloc-none">₹0</span>
              {/if}
            </div>
          </div>

          {#if b.fund_breakdown && b.fund_breakdown.length > 0 && alloc > 0}
            <div class="fund-splits">
              {#each b.fund_breakdown.filter((f) => Number(f.amount) > 0) as f}
                <div class="fund-split-row tnum">
                  <span class="split-name">{f.fund_name}</span>
                  <span class="split-amt" data-sensitive="currency">+{formatINR(f.amount, quietMode)}</span>
                </div>
              {/each}
            </div>
          {/if}
        </div>
      {/each}
    </div>

    {#if includeRebalance && sellOrders.length > 0}
      <div class="sim-sells">
        <div class="sells-title">Simulated Sells ({sellOrders.length})</div>
        {#each sellOrders as o}
          <div class="sell-row tnum">
            <span>{o.fund_name}</span>
            <span data-sensitive="currency">{formatINR(o.total_proceeds, quietMode)}</span>
          </div>
        {/each}
      </div>
    {/if}
  {/if}
</section>

<style>
  .router-card {
    background: var(--bg-surface);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-md);
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .router-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
  }

  .router-title-group {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  :global(.router-icon) {
    color: var(--state-action);
  }

  .router-title {
    font-size: 0.92rem;
    font-weight: 600;
    color: var(--ink-primary);
  }

  .sim-badge {
    font-size: 0.68rem;
    font-weight: 600;
    letter-spacing: 0.03em;
    padding: 2px 8px;
    border-radius: var(--radius-sm);
    background: var(--state-action-soft);
    color: var(--state-action);
    border: 1px solid color-mix(in oklch, var(--state-action) 30%, transparent);
  }

  .reset-btn {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 5px 10px;
    background: var(--bg-elevated);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    color: var(--ink-secondary);
    font-size: 0.74rem;
    cursor: pointer;
    min-height: 32px;
  }

  .controls-row {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .amount-box {
    display: flex;
    align-items: center;
    background: var(--bg-canvas);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    padding-left: 10px;
    overflow: hidden;
  }

  .rupee-prefix {
    color: var(--ink-muted);
    font-size: 0.86rem;
  }

  .amount-input {
    width: 110px;
    background: transparent;
    border: none;
    color: var(--ink-primary);
    font-size: 0.88rem;
    font-weight: 600;
    padding: 7px 8px;
    outline: none;
  }

  .run-btn {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 8px 12px;
    background: var(--state-action-soft);
    color: var(--state-action);
    border: none;
    border-left: 1px solid var(--border-hair);
    font-size: 0.78rem;
    font-weight: 600;
    cursor: pointer;
    min-height: 36px;
  }

  .preset-chips {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  .chip-btn {
    padding: 6px 10px;
    background: var(--bg-elevated);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    color: var(--ink-secondary);
    font-size: 0.74rem;
    cursor: pointer;
    min-height: 34px;
  }

  .chip-btn.active {
    border-color: var(--state-action);
    color: var(--state-action);
    background: var(--state-action-soft);
  }

  .mode-toggle {
    display: inline-flex;
    background: var(--bg-canvas);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    padding: 2px;
    margin-left: auto;
  }

  .mode-btn {
    padding: 6px 10px;
    background: transparent;
    border: none;
    border-radius: 4px;
    color: var(--ink-muted);
    font-size: 0.74rem;
    font-weight: 500;
    cursor: pointer;
    min-height: 32px;
  }

  .mode-btn.active {
    background: var(--bg-elevated);
    color: var(--ink-primary);
  }

  .sim-error {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 9px 12px;
    border-radius: var(--radius-sm);
    background: var(--state-danger-soft);
    color: var(--state-danger);
    font-size: 0.78rem;
  }

  .sim-summary {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: 10px;
    padding: 12px 14px;
    background: var(--bg-canvas);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
  }

  .sum-cell {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .sum-label {
    font-size: 0.7rem;
    color: var(--ink-muted);
  }

  .sum-val {
    font-size: 0.88rem;
    font-weight: 600;
    color: var(--ink-primary);
  }

  .sum-val.action { color: var(--state-action); }
  .sum-val.warn { color: var(--state-warn); }
  .sum-val.ok { color: var(--state-ok); }

  .shift-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .shift-row {
    background: var(--bg-canvas);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .shift-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
  }

  .shift-name-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .swatch {
    width: 8px;
    height: 8px;
    border-radius: 2px;
  }

  .shift-name {
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--ink-primary);
  }

  .shift-nums {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 0.78rem;
    flex-wrap: wrap;
  }

  .target-ref {
    color: var(--ink-muted);
    font-size: 0.72rem;
  }

  .alloc-pill {
    padding: 2px 7px;
    border-radius: var(--radius-sm);
    background: var(--state-action-soft);
    color: var(--state-action);
    font-weight: 600;
  }

  .alloc-none {
    color: var(--ink-muted);
  }

  .fund-splits {
    padding-left: 16px;
    border-top: 1px solid var(--border-hair);
    padding-top: 6px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .fund-split-row {
    display: flex;
    justify-content: space-between;
    font-size: 0.74rem;
    color: var(--ink-secondary);
  }

  .split-amt {
    color: var(--state-action);
    font-weight: 600;
  }

  .sim-sells {
    border-top: 1px dashed var(--border-strong);
    padding-top: 10px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .sells-title {
    font-size: 0.74rem;
    font-weight: 600;
    color: var(--state-warn);
  }

  .sell-row {
    display: flex;
    justify-content: space-between;
    font-size: 0.76rem;
    color: var(--ink-secondary);
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
  }

  @media (max-width: 768px) {
    .mode-toggle {
      margin-left: 0;
      width: 100%;
      display: grid;
      grid-template-columns: 1fr 1fr;
    }
  }
</style>
