<script lang="ts">
  import { fetchJson, type RebalancePlanDto } from '../api';
  import { formatINR, formatPercent, formatCompactINR } from '../formatters';
  import { 
    Coins, 
    ArrowRight, 
    CheckCircle2, 
    AlertCircle, 
    RefreshCw, 
    X, 
    Sliders, 
    TrendingUp,
    ShieldCheck
  } from '@lucide/svelte';

  interface Props {
    open?: boolean;
    holdings?: any[];
    netWorth?: number;
    quietMode?: boolean;
    onclose?: () => void;
    onapply?: (plan: RebalancePlanDto) => void;
  }

  let { open = false, holdings = [], netWorth = 0, quietMode = false, onclose, onapply }: Props = $props();

  let amount = $state(50000);
  let includeRebalance = $state(false);
  let loading = $state(false);
  let error = $state<string | null>(null);
  let simulatedPlan = $state<RebalancePlanDto | null>(null);

  const PRESET_AMOUNTS = [25000, 50000, 100000, 250000, 500000];

  function getFundHoldingMetrics(fundIdOrName: string, orderProceeds: number = 0) {
    const nw = netWorth || Number(simulatedPlan?.summary?.net_worth ?? 2178955);
    const holding = holdings?.find((h: any) => 
      (h.asset_id && fundIdOrName.toLowerCase().includes(h.asset_id.toLowerCase())) ||
      (h.isin && fundIdOrName.toLowerCase().includes(h.isin.toLowerCase())) ||
      (h.asset_name && fundIdOrName.toLowerCase().includes(h.asset_name.toLowerCase())) ||
      (h.asset_name && h.asset_name.toLowerCase().includes(fundIdOrName.toLowerCase()))
    );
    const curVal = Number(holding?.current_value ?? (orderProceeds > 0 ? orderProceeds * 1.5 : 0));
    const curPct = nw > 0 ? (curVal / nw) * 100 : 0;
    const postVal = Math.max(0, curVal - orderProceeds);
    const postPct = nw > 0 ? (postVal / nw) * 100 : 0;
    return {
      curVal,
      curPct,
      postVal,
      postPct
    };
  }

  async function runSimulation() {
    if (!amount || amount <= 0) {
      error = 'Please enter a valid positive lump-sum amount.';
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
          body: JSON.stringify({
            amount: amount,
            includeRebalance: includeRebalance
          })
        });
      } catch {
        res = await fetchJson<RebalancePlanDto>('/sync/rebalance/simulate-lumpsum', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            amount: amount,
            includeRebalance: includeRebalance
          })
        });
      }
      simulatedPlan = res;
    } catch (e: any) {
      error = e.message || 'Simulation failed';
    } finally {
      loading = false;
    }
  }

  function setPreset(val: number) {
    amount = val;
    runSimulation();
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      onclose?.();
    }
  }

  $effect(() => {
    if (open && !simulatedPlan && !loading) {
      runSimulation();
    }
  });
</script>

{#if open}
  <div 
    id="lumpsumModal"
    class="modal-backdrop" 
    role="presentation"
    onclick={onclose}
  >
    <div 
      class="modal-container"
      role="dialog"
      aria-modal="true"
      aria-labelledby="lumpsum-modal-title"
      tabindex="-1"
      onclick={(e) => e.stopPropagation()}
      onkeydown={handleKeydown}
    >
      <!-- Header -->
      <div class="modal-header">
        <div>
          <div class="tag-row">
            <span class="hud-tag">CAPITAL ALLOCATION SIMULATOR</span>
            <span class="status-pill">DRY RUN PREVIEW</span>
          </div>
          <h2 id="lumpsum-modal-title" class="title">Deploy Lump-Sum Inflow</h2>
          <p class="subtitle">Direct fresh capital into underweight buckets to restore target drift with zero tax drag</p>
        </div>
        <button type="button" class="close-btn" onclick={onclose} aria-label="Close dialog">
          <X size={18} />
        </button>
      </div>

      <!-- Simulator Inputs -->
      <div class="input-section">
        <div class="amount-field-wrapper">
          <label for="lumpsum-amount" class="input-label">Inflow Capital (₹)</label>
          <div class="input-with-symbol">
            <span class="currency-prefix">₹</span>
            <input 
              id="lumpsum-amount"
              type="number" 
              bind:value={amount}
              min="1000"
              step="5000"
              class="amount-input mono"
              onkeydown={(e) => { if (e.key === 'Enter') runSimulation(); }}
            />
            <button 
              type="button" 
              class="simulate-btn"
              onclick={runSimulation}
              disabled={loading}
            >
              {#if loading}
                <RefreshCw size={14} class="spin" />
              {:else}
                <Sliders size={14} />
              {/if}
              <span>Recalculate</span>
            </button>
          </div>
        </div>

        <div class="preset-chips">
          <span class="chips-label">Quick Presets:</span>
          {#each PRESET_AMOUNTS as preset}
            <button 
              type="button" 
              class="preset-chip" 
              class:active={amount === preset}
              onclick={() => setPreset(preset)}
            >
              {formatINR(preset, false)}
            </button>
          {/each}
        </div>

        <!-- Dual Mode Selection Segmented Cards -->
        <div class="simulation-mode-grid">
          <button 
            type="button" 
            class="mode-card" 
            class:active={!includeRebalance}
            onclick={() => {
              if (includeRebalance) {
                includeRebalance = false;
                runSimulation();
              }
            }}
          >
            <div class="mode-icon emerald">
              <ShieldCheck size={18} />
            </div>
            <div class="mode-text">
              <div class="mode-title-row">
                <span class="mode-title">1. Lump-Sum Alone</span>
                <span class="mode-tax-badge emerald">0% TAX DRAG</span>
              </div>
              <p class="mode-desc">
                Deploys cash strictly into underweight buckets. <strong>Zero asset sales</strong>, preserving all existing holdings.
              </p>
            </div>
          </button>

          <button 
            type="button" 
            class="mode-card" 
            class:active={includeRebalance}
            onclick={() => {
              if (!includeRebalance) {
                includeRebalance = true;
                runSimulation();
              }
            }}
          >
            <div class="mode-icon amber">
              <Sliders size={18} />
            </div>
            <div class="mode-text">
              <div class="mode-title-row">
                <span class="mode-title">2. Inflow + Full Rebalance</span>
                <span class="mode-tax-badge amber">INCLUDES SELLS</span>
              </div>
              <p class="mode-desc">
                Deploys cash <strong>plus trims overweight holdings</strong> to bring every asset class to exact Policy v2.3 targets.
              </p>
            </div>
          </button>
        </div>
      </div>

      <!-- Results Body -->
      <div class="modal-body">
        {#if error}
          <div class="alert-box error">
            <AlertCircle size={16} />
            <span>{error}</span>
          </div>
        {/if}

        {#if loading && !simulatedPlan}
          <div class="loading-state">
            <RefreshCw size={24} class="spin loading-icon" />
            <p>Solving quadratic drift optimization across asset allocation buckets...</p>
          </div>
        {:else if simulatedPlan}
          <!-- Plan Summary Cards -->
          <div class="summary-grid">
            <div class="sum-card">
              <span class="sum-label">Capital to Deploy</span>
              <span class="sum-val mono bold text-cyan">
                {formatINR(simulatedPlan.buy_side?.total_to_invest ?? amount, quietMode)}
              </span>
            </div>
            <div class="sum-card">
              <span class="sum-label">Estimated Post Net Worth</span>
              <span class="sum-val mono bold">
                {formatINR((simulatedPlan.summary?.net_worth ?? 0) + (includeRebalance ? 0 : amount), quietMode)}
              </span>
            </div>
            <div class="sum-card">
              <span class="sum-label">Projected Tax Drag</span>
              <span class="sum-val mono bold" class:text-emerald={!includeRebalance}>
                {includeRebalance ? formatINR(simulatedPlan.summary?.projected_tax ?? 0, quietMode) : '₹0 (Tax-Free)'}
              </span>
            </div>
          </div>

          <!-- Section: Sell Suggestions (Only when includeRebalance is true and sell orders exist) -->
          {#if includeRebalance && simulatedPlan.sell_side?.orders && simulatedPlan.sell_side.orders.length > 0}
            <div class="results-table-section sell-section">
              <div class="section-header">
                <div class="sec-left">
                  <span class="sec-pill rose">SELL TRIMS</span>
                  <h4 class="section-title">Disciplined Sell Suggestions ({simulatedPlan.sell_side.orders.length} Schemes)</h4>
                </div>
                <span class="section-badge text-rose mono">
                  Total Proceeds: +{formatINR(simulatedPlan.sell_side.total_required, quietMode)}
                </span>
              </div>

              <div class="sell-orders-cards">
                {#each simulatedPlan.sell_side.orders as order}
                  {@const metrics = getFundHoldingMetrics(order.fund_id || order.fund_name, order.total_proceeds)}
                  <div class="sell-order-card">
                    <div class="so-left">
                      <span class="so-name font-medium">{order.fund_name}</span>
                      <div class="so-meta mono">
                        <span class="so-impact-pill">
                          Impact: <strong class="text-rose">{metrics.curPct.toFixed(1)}% ➔ {metrics.postPct.toFixed(1)}%</strong>
                        </span>
                        <span class="so-val-span text-muted">
                          ({formatCompactINR(metrics.curVal)} ➔ {formatCompactINR(metrics.postVal)})
                        </span>
                        <span class="so-label-span">{order.summary_label || 'Trim'}</span>
                      </div>
                    </div>
                    <div class="so-right mono">
                      <strong class="so-proceeds text-rose">+{formatINR(order.total_proceeds, quietMode)}</strong>
                      <span class="so-tax-tag">
                        Net P&L: 
                        <span class={(order.total_realized_gain ?? 0) >= 0 ? 'text-emerald' : 'text-amber'}>
                          {(order.total_realized_gain ?? 0) >= 0 ? '+' : ''}{formatINR(order.total_realized_gain ?? 0, quietMode)}
                        </span>
                      </span>
                    </div>
                  </div>
                {/each}
              </div>
            </div>
          {/if}

          <!-- Section: Recommended Fund Deployments (Buys) -->
          <div class="results-table-section">
            <div class="section-header">
              <div class="sec-left">
                <span class="sec-pill cyan">BUY DEPLOYMENTS</span>
                <h4 class="section-title">Recommended Fund Inflow Routing</h4>
              </div>
              <span class="section-badge text-emerald mono">OPTIMAL DRIFT RESTORATION</span>
            </div>

            <div class="buy-buckets-container">
              {#if simulatedPlan.buy_side?.buckets}
                {#each simulatedPlan.buy_side.buckets as b}
                  {@const alloc = b.amount_allocated ?? 0}
                  <div class="buy-bucket-card" class:active-alloc={alloc > 0}>
                    <div class="bb-header">
                      <div class="bb-left">
                        <span class="bb-name font-bold">{b.bucket}</span>
                        <span class="bb-drift mono text-muted">
                          Target: {b.target_pct}% · Share: <strong class="text-cyan">{b.current_pct.toFixed(1)}% ➔ {b.post_rebalance_pct ? b.post_rebalance_pct.toFixed(1) : (b.post_pct ? b.post_pct.toFixed(1) : b.target_pct)}%</strong>
                        </span>
                      </div>
                      <div class="bb-right mono">
                        {#if alloc > 0}
                          <strong class="bb-alloc text-emerald">+{formatINR(alloc, quietMode)}</strong>
                          <span class="action-pill buy">BUY</span>
                        {:else}
                          <span class="action-pill hold">HOLD (IN TARGET)</span>
                        {/if}
                      </div>
                    </div>

                    <!-- Specific Funds to Buy inside this Bucket -->
                    {#if alloc > 0 && b.fund_breakdown && b.fund_breakdown.length > 0}
                      <div class="bb-funds-subtable">
                        <div class="funds-subtable-head mono">
                          <span>SPECIFIC FUND TO BUY</span>
                          <span class="mono-head-right">PRE ➔ POST IMPACT · ALLOCATION</span>
                        </div>
                        {#each b.fund_breakdown as fb}
                          <div class="fund-subrow">
                            <div class="fs-left">
                              <span class="fs-arrow">↳</span>
                              <span class="fs-name font-medium">{fb.fund_name}</span>
                              <span class="fs-impact-badge mono">
                                {fb.current_pct ? fb.current_pct.toFixed(1) : '0.0'}% ➔ <strong class="text-emerald">{fb.post_pct ? fb.post_pct.toFixed(1) : '0.0'}%</strong>
                              </span>
                              {#if fb.pre_valuation !== undefined && fb.post_valuation !== undefined}
                                <span class="fs-val-note mono text-muted">
                                  ({formatCompactINR(fb.pre_valuation)} ➔ {formatCompactINR(fb.post_valuation)})
                                </span>
                              {/if}
                            </div>
                            <strong class="fs-amount mono text-emerald">+{formatINR(fb.amount, quietMode)}</strong>
                          </div>
                        {/each}
                      </div>
                    {/if}
                  </div>
                {/each}
              {/if}
            </div>
          </div>
        {/if}
      </div>

      <!-- Footer -->
      <div class="modal-footer">
        <button type="button" class="cancel-btn" onclick={onclose}>Dismiss</button>
        <button 
          type="button" 
          class="apply-btn" 
          disabled={!simulatedPlan || loading}
          onclick={() => {
            if (simulatedPlan) {
              onapply?.(simulatedPlan);
              onclose?.();
            }
          }}
        >
          <CheckCircle2 size={16} />
          <span>Accept & Route Trades</span>
        </button>
      </div>
    </div>
  </div>
{/if}

<style>
  .modal-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(4, 7, 13, 0.82);
    backdrop-filter: blur(12px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    padding: 1.5rem;
    animation: fadeIn var(--transition-fast) ease-out;
  }

  .modal-container {
    background: var(--surface-card);
    border: 1px solid var(--border-medium);
    border-radius: var(--radius-xl);
    width: 100%;
    max-width: 780px;
    max-height: 90vh;
    display: flex;
    flex-direction: column;
    box-shadow: 0 25px 60px rgba(0, 0, 0, 0.85), 0 0 35px var(--glow-cyan);
    overflow: hidden;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 1.25rem 1.5rem;
    border-bottom: 1px solid var(--border-subtle);
    background: var(--surface-card-hover);
  }

  .tag-row {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    margin-bottom: 0.35rem;
  }

  .hud-tag {
    font-size: 0.68rem;
    font-family: var(--font-mono);
    color: var(--accent-cyan);
    font-weight: 700;
    letter-spacing: 0.05em;
  }

  .status-pill {
    padding: 0.12rem 0.45rem;
    border-radius: var(--radius-sm);
    font-size: 0.65rem;
    font-weight: 700;
    background: hsla(185, 85%, 52%, 0.15);
    color: var(--accent-cyan);
    border: 1px solid hsla(185, 85%, 52%, 0.3);
  }

  .title {
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0;
  }

  .subtitle {
    font-size: 0.8rem;
    color: var(--text-muted);
    margin: 0.2rem 0 0 0;
  }

  .close-btn {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--surface-glass);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    color: var(--text-secondary);
    cursor: pointer;
  }

  /* Inputs */
  .input-section {
    padding: 1.25rem 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    background: rgba(0, 0, 0, 0.2);
    border-bottom: 1px solid var(--border-subtle);
  }

  .amount-field-wrapper {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }

  .input-label {
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--text-secondary);
  }

  .input-with-symbol {
    display: flex;
    align-items: center;
    position: relative;
    background: var(--surface-void);
    border: 1px solid var(--border-medium);
    border-radius: var(--radius-md);
    overflow: hidden;
  }

  .currency-prefix {
    padding-left: 1rem;
    font-size: 1.1rem;
    font-family: var(--font-mono);
    color: var(--accent-cyan);
    font-weight: 700;
  }

  .amount-input {
    flex: 1;
    background: transparent;
    border: none;
    padding: 0.65rem 0.75rem;
    font-size: 1.2rem;
    color: var(--text-primary);
    outline: none;
  }

  .simulate-btn {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.65rem 1.2rem;
    background: var(--surface-card-hover);
    border: none;
    border-left: 1px solid var(--border-subtle);
    color: var(--accent-cyan);
    font-size: 0.82rem;
    font-weight: 600;
    cursor: pointer;
    transition: all var(--transition-fast) ease;
  }
  .simulate-btn:hover {
    background: hsla(185, 85%, 52%, 0.1);
  }

  .preset-chips {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    flex-wrap: wrap;
  }

  .chips-label {
    font-size: 0.75rem;
    color: var(--text-muted);
    margin-right: 0.25rem;
  }

  .preset-chip {
    padding: 0.25rem 0.6rem;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    color: var(--text-secondary);
    font-size: 0.75rem;
    font-family: var(--font-mono);
    cursor: pointer;
  }

  .preset-chip.active {
    background: hsla(185, 85%, 52%, 0.15);
    color: var(--accent-cyan);
    border-color: var(--accent-cyan);
  }

  /* Dual Mode Selection Grid */
  .simulation-mode-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }

  .mode-card {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 12px 14px;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    cursor: pointer;
    text-align: left;
    transition: all 0.2s ease;
  }
  .mode-card:hover {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.2);
  }
  .mode-card.active {
    background: rgba(6, 182, 212, 0.08);
    border-color: var(--accent-cyan);
    box-shadow: 0 0 16px rgba(6, 182, 212, 0.15);
  }

  .mode-icon {
    width: 34px;
    height: 34px;
    border-radius: var(--radius-sm);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .mode-icon.emerald {
    background: rgba(16, 185, 129, 0.15);
    color: var(--accent-emerald-bright);
    border: 1px solid rgba(16, 185, 129, 0.3);
  }
  .mode-icon.amber {
    background: rgba(245, 158, 11, 0.15);
    color: var(--accent-amber);
    border: 1px solid rgba(245, 158, 11, 0.3);
  }

  .mode-text {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .mode-title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .mode-title {
    font-size: 0.85rem;
    font-weight: 700;
    color: #ffffff;
  }

  .mode-tax-badge {
    font-size: 0.62rem;
    font-weight: 700;
    padding: 1px 6px;
    border-radius: var(--radius-pill);
    font-family: var(--font-mono);
  }
  .mode-tax-badge.emerald {
    background: rgba(16, 185, 129, 0.2);
    color: var(--accent-emerald-bright);
    border: 1px solid rgba(16, 185, 129, 0.4);
  }
  .mode-tax-badge.amber {
    background: rgba(245, 158, 11, 0.2);
    color: var(--accent-amber);
    border: 1px solid rgba(245, 158, 11, 0.4);
  }

  .mode-desc {
    font-size: 0.72rem;
    color: #94a3b8;
    line-height: 1.4;
    margin: 0;
  }

  /* Body */
  .modal-body {
    flex: 1;
    overflow-y: auto;
    padding: 1.25rem 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 1.25rem;
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 0.75rem;
  }

  .sum-card {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    padding: 0.75rem 1rem;
    background: var(--surface-card-hover);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
  }

  .sum-label {
    font-size: 0.7rem;
    text-transform: uppercase;
    color: var(--text-muted);
  }

  .sum-val {
    font-size: 1.05rem;
    color: var(--text-primary);
  }

  .text-cyan { color: var(--accent-cyan); }
  .text-emerald { color: var(--accent-emerald); }
  .text-rose { color: var(--accent-rose); }

  /* Sections */
  .results-table-section {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .sec-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .sec-pill {
    font-size: 0.65rem;
    font-weight: 700;
    padding: 2px 7px;
    border-radius: 4px;
    font-family: var(--font-mono);
  }
  .sec-pill.rose {
    background: rgba(244, 63, 94, 0.15);
    color: var(--accent-rose-bright);
    border: 1px solid rgba(244, 63, 94, 0.3);
  }
  .sec-pill.cyan {
    background: rgba(6, 182, 212, 0.15);
    color: var(--accent-cyan);
    border: 1px solid rgba(6, 182, 212, 0.3);
  }

  .section-title {
    font-size: 0.85rem;
    font-weight: 600;
    color: #e2e8f0;
    margin: 0;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .section-badge {
    font-size: 0.65rem;
    font-family: var(--font-mono);
  }

  /* Sell Orders Cards */
  .sell-orders-cards {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .sell-order-card {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: rgba(244, 63, 94, 0.04);
    border: 1px solid rgba(244, 63, 94, 0.2);
    border-radius: var(--radius-sm);
    padding: 10px 14px;
    gap: 14px;
  }

  .so-left {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .so-name {
    font-size: 0.82rem;
    color: #f1f5f9;
  }

  .so-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.7rem;
    flex-wrap: wrap;
  }

  .so-impact-pill {
    padding: 1px 6px;
    background: rgba(244, 63, 94, 0.15);
    border-radius: 3px;
    color: #fca5a5;
  }

  .so-right {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
  }

  .so-proceeds {
    font-size: 0.92rem;
  }

  .so-tax-tag {
    font-size: 0.68rem;
    color: #94a3b8;
  }

  /* Buy Buckets Cards */
  .buy-buckets-container {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .buy-bucket-card {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .buy-bucket-card.active-alloc {
    background: rgba(6, 182, 212, 0.03);
    border-color: rgba(6, 182, 212, 0.25);
  }

  .bb-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .bb-left {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .bb-name {
    font-size: 0.85rem;
    color: #ffffff;
  }

  .bb-drift {
    font-size: 0.72rem;
  }

  .bb-right {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .bb-alloc {
    font-size: 0.95rem;
  }

  .action-pill {
    padding: 0.15rem 0.5rem;
    border-radius: var(--radius-sm);
    font-size: 0.68rem;
    font-weight: 700;
  }
  .action-pill.buy {
    background: hsla(158, 85%, 45%, 0.15);
    color: var(--accent-emerald-bright);
    border: 1px solid hsla(158, 85%, 45%, 0.3);
  }
  .action-pill.hold {
    background: hsla(215, 25%, 35%, 0.2);
    color: var(--text-muted);
  }

  /* Specific Funds Subtable inside Bucket */
  .bb-funds-subtable {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding-top: 8px;
    border-top: 1px solid rgba(255, 255, 255, 0.06);
  }

  .funds-subtable-head {
    display: flex;
    justify-content: space-between;
    font-size: 0.65rem;
    color: #64748b;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .fund-subrow {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: rgba(0, 0, 0, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.04);
    border-radius: var(--radius-sm);
    padding: 6px 10px;
    gap: 12px;
  }

  .fs-left {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    font-size: 0.76rem;
  }

  .fs-arrow {
    color: var(--accent-cyan);
    font-weight: 700;
  }

  .fs-name {
    color: #e2e8f0;
  }

  .fs-impact-badge {
    font-size: 0.68rem;
    background: rgba(16, 185, 129, 0.12);
    color: var(--accent-emerald-bright);
    padding: 1px 6px;
    border-radius: 3px;
    border: 1px solid rgba(16, 185, 129, 0.25);
  }

  .fs-val-note {
    font-size: 0.68rem;
  }

  .fs-amount {
    font-size: 0.85rem;
    white-space: nowrap;
  }

  .alert-box.error {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.65rem 1rem;
    background: hsla(350, 85%, 60%, 0.1);
    border: 1px solid hsla(350, 85%, 60%, 0.3);
    border-radius: var(--radius-md);
    color: var(--accent-crimson);
    font-size: 0.82rem;
  }

  .loading-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 2.5rem 1rem;
    color: var(--text-muted);
    font-size: 0.85rem;
  }

  /* Modal Footer */
  .modal-footer {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 0.75rem;
    padding: 1rem 1.5rem;
    border-top: 1px solid var(--border-subtle);
    background: var(--surface-card-hover);
  }

  .cancel-btn {
    padding: 0.5rem 1.15rem;
    background: transparent;
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    color: var(--text-secondary);
    font-size: 0.85rem;
    cursor: pointer;
  }

  .apply-btn {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    padding: 0.5rem 1.35rem;
    background: var(--accent-cyan);
    border: none;
    border-radius: var(--radius-md);
    color: var(--surface-void);
    font-size: 0.85rem;
    font-weight: 600;
    cursor: pointer;
  }

  .apply-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }

  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }

  :global(.spin) {
    animation: spin 1s linear infinite;
  }
</style>
