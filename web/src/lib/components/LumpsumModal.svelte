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
          <h2 id="lumpsum-modal-title" class="title">Deploy Lump-Sum Inflow</h2>
          <div class="tag-row">
            <span class="hud-tag">Capital allocation simulator</span>
            <span class="status-pill">Dry run preview</span>
          </div>
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
                <span class="mode-tax-badge emerald">0% Tax drag</span>
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
                <span class="mode-tax-badge amber">Includes sells</span>
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
                  <h4 class="section-title">Disciplined Sell Suggestions ({simulatedPlan.sell_side.orders.length} Schemes)</h4>
                  <span class="sec-pill rose">Sell trims</span>
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
                <h4 class="section-title">Recommended Fund Inflow Routing</h4>
                <span class="sec-pill cyan">Buy deployments</span>
              </div>
              <span class="section-badge text-emerald mono">Optimal drift restoration</span>
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
                          <span class="action-pill buy">Buy</span>
                        {:else}
                          <span class="action-pill hold">Hold (in target)</span>
                        {/if}
                      </div>
                    </div>

                    <!-- Specific Funds to Buy inside this Bucket -->
                    {#if alloc > 0 && b.fund_breakdown && b.fund_breakdown.length > 0}
                      <div class="bb-funds-subtable">
                        <div class="funds-subtable-head mono">
                          <span>Specific fund to buy</span>
                          <span class="mono-head-right">Pre ➔ post impact · allocation</span>
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
    background: var(--color-paper-3);
    backdrop-filter: blur(12px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: var(--z-modal);
    padding: 1.5rem;
    animation: fadeIn 0.15s ease-out;
  }

  .modal-container {
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    width: 100%;
    max-width: 780px;
    max-height: 90vh;
    display: flex;
    flex-direction: column;
    box-shadow: none;
    overflow: hidden;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 1.25rem 1.5rem;
    border-bottom: 1px solid var(--color-rule);
    background: var(--color-paper);
  }

  .tag-row {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    margin-top: 0.35rem;
  }

  .hud-tag {
    font-size: 0.75rem;
    font-family: var(--font-outlier);
    color: var(--color-cool);
    font-weight: 600;
  }

  .status-pill {
    padding: 0.12rem 0.45rem;
    border-radius: var(--radius-pill);
    font-size: 0.75rem;
    font-family: var(--font-outlier);
    font-weight: 600;
    background: var(--color-cool-soft);
    color: var(--color-cool);
    border: 1px solid var(--color-cool);
  }

  .title {
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--color-ink);
    margin: 0;
  }

  .subtitle {
    font-size: 0.8rem;
    color: var(--color-muted);
    margin: 0.2rem 0 0 0;
  }

  .close-btn {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    color: var(--color-muted);
    cursor: pointer;
    transition: background 0.15s ease, color 0.15s ease;
  }
  .close-btn:hover {
    color: var(--color-ink);
    background: var(--color-paper);
  }

  .close-btn:focus-visible,
  .simulate-btn:focus-visible,
  .preset-chip:focus-visible,
  .mode-card:focus-visible,
  .cancel-btn:focus-visible,
  .apply-btn:focus-visible {
    outline: 2px solid var(--color-focus);
    outline-offset: 2px;
  }

  /* Inputs */
  .input-section {
    padding: 1.25rem 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    background: var(--color-paper-3);
    border-bottom: 1px solid var(--color-rule);
  }

  .amount-field-wrapper {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }

  .input-label {
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--color-muted);
  }

  .input-with-symbol {
    display: flex;
    align-items: center;
    position: relative;
    background: var(--color-paper);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    overflow: hidden;
  }

  .currency-prefix {
    padding-left: 1rem;
    font-size: 1.1rem;
    font-family: var(--font-outlier);
    color: var(--color-cool);
    font-weight: 700;
  }

  .amount-input {
    flex: 1;
    background: transparent;
    border: none;
    padding: 0.65rem 0.75rem;
    font-size: 1.2rem;
    color: var(--color-ink);
    outline: none;
    font-family: var(--font-outlier);
  }

  .simulate-btn {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.65rem 1.2rem;
    background: var(--color-paper-2);
    border: none;
    border-left: 1px solid var(--color-rule);
    color: var(--color-cool);
    font-size: 0.82rem;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.15s ease, color 0.15s ease;
  }
  .simulate-btn:hover {
    background: var(--color-cool-soft);
  }

  .preset-chips {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    flex-wrap: wrap;
  }

  .chips-label {
    font-size: 0.75rem;
    color: var(--color-muted);
    margin-right: 0.25rem;
  }

  .preset-chip {
    padding: 0.25rem 0.6rem;
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    color: var(--color-ink-2);
    font-size: 0.75rem;
    font-family: var(--font-outlier);
    cursor: pointer;
    transition: background 0.15s ease, color 0.15s ease;
  }

  .preset-chip.active {
    background: var(--color-cool-soft);
    color: var(--color-cool);
    border-color: var(--color-cool);
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
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    cursor: pointer;
    text-align: left;
    transition: background 0.15s ease, border-color 0.15s ease;
  }
  .mode-card:hover {
    background: var(--color-paper);
    border-color: var(--color-rule-2);
  }
  .mode-card.active {
    background: var(--color-cool-soft);
    border-color: var(--color-cool);
    box-shadow: none;
  }

  .mode-icon {
    width: 34px;
    height: 34px;
    border-radius: var(--radius-pill);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .mode-icon.emerald {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--color-ok);
  }
  .mode-icon.amber {
    background: var(--color-accent-soft);
    color: var(--color-accent);
    border: 1px solid var(--color-accent);
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
    color: var(--color-ink);
  }

  .mode-tax-badge {
    font-size: 0.75rem;
    font-weight: 600;
    padding: 1px 6px;
    border-radius: var(--radius-pill);
    font-family: var(--font-outlier);
  }
  .mode-tax-badge.emerald {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--color-ok);
  }
  .mode-tax-badge.amber {
    background: var(--color-accent-soft);
    color: var(--color-accent);
    border: 1px solid var(--color-accent);
  }

  .mode-desc {
    font-size: 0.75rem;
    color: var(--color-muted);
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

  .summary-bar {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 0.75rem;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    padding: 0.75rem 1rem;
  }

  .sum-item {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }

  .sum-divider {
    display: none;
  }

  .sum-label {
    font-size: 0.75rem;
    color: var(--color-muted);
    font-weight: 500;
  }

  .sum-val {
    font-size: 1.05rem;
    color: var(--color-ink);
  }

  .text-cyan { color: var(--color-cool); }
  .text-emerald { color: var(--color-ok); }
  .text-rose { color: var(--color-bad); }

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
    font-size: 0.75rem;
    font-weight: 600;
    padding: 2px 7px;
    border-radius: var(--radius-pill);
    font-family: var(--font-outlier);
  }
  .sec-pill.rose {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--color-bad);
  }
  .sec-pill.cyan {
    background: var(--color-cool-soft);
    color: var(--color-cool);
    border: 1px solid var(--color-cool);
  }

  .section-title {
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--color-ink);
    margin: 0;
  }

  .section-badge {
    font-size: 0.75rem;
    font-family: var(--font-outlier);
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
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
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
    color: var(--color-ink);
  }

  .so-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.75rem;
    flex-wrap: wrap;
    font-family: var(--font-outlier);
  }

  .so-impact-pill {
    padding: 1px 6px;
    background: var(--color-bad-soft);
    border-radius: var(--radius-pill);
    color: var(--color-bad);
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
    font-size: 0.75rem;
    color: var(--color-muted);
  }

  /* Buy Buckets Cards */
  .buy-buckets-container {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .buy-bucket-card {
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .buy-bucket-card.active-alloc {
    background: var(--color-paper-3);
    border-color: var(--color-cool);
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
    color: var(--color-ink);
  }

  .bb-drift {
    font-size: 0.75rem;
    font-family: var(--font-outlier);
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
    border-radius: var(--radius-pill);
    font-size: 0.75rem;
    font-weight: 600;
    font-family: var(--font-outlier);
  }
  .action-pill.buy {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--color-ok);
  }
  .action-pill.hold {
    background: var(--color-paper);
    color: var(--color-muted);
    border: 1px solid var(--color-rule);
  }

  /* Specific Funds Subtable inside Bucket */
  .bb-funds-subtable {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding-top: 8px;
    border-top: 1px solid var(--color-rule);
  }

  .funds-subtable-head {
    display: flex;
    justify-content: space-between;
    font-size: 0.75rem;
    color: var(--color-muted);
    font-family: var(--font-outlier);
  }

  .fund-subrow {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: var(--color-paper);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
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
    color: var(--color-cool);
    font-weight: 700;
  }

  .fs-name {
    color: var(--color-ink-2);
  }

  .fs-impact-badge {
    font-size: 0.75rem;
    background: var(--color-ok-soft);
    color: var(--color-ok);
    padding: 1px 6px;
    border-radius: var(--radius-pill);
    border: 1px solid var(--color-ok);
    font-family: var(--font-outlier);
  }

  .fs-val-note {
    font-size: 0.75rem;
  }

  .fs-amount {
    font-size: 0.85rem;
    white-space: nowrap;
    font-family: var(--font-outlier);
  }

  .alert-box.error {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.65rem 1rem;
    background: var(--color-bad-soft);
    border: 1px solid var(--color-bad);
    border-radius: var(--radius-card);
    color: var(--color-bad);
    font-size: 0.82rem;
  }

  .loading-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 2.5rem 1rem;
    color: var(--color-muted);
    font-size: 0.85rem;
  }

  /* Modal Footer */
  .modal-footer {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 0.75rem;
    padding: 1rem 1.5rem;
    border-top: 1px solid var(--color-rule);
    background: var(--color-paper);
  }

  .cancel-btn {
    padding: 0.5rem 1.15rem;
    background: transparent;
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    color: var(--color-muted);
    font-size: 0.85rem;
    cursor: pointer;
    transition: background 0.15s ease, color 0.15s ease;
  }
  .cancel-btn:hover {
    background: var(--color-paper-3);
    color: var(--color-ink);
  }

  .apply-btn {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    padding: 0.5rem 1.35rem;
    background: var(--color-cool);
    border: none;
    border-radius: var(--radius-pill);
    color: var(--color-paper-3);
    font-size: 0.85rem;
    font-weight: 600;
    cursor: pointer;
    transition: filter 0.15s ease;
  }
  .apply-btn:hover:not(:disabled) {
    filter: brightness(1.1);
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

  @media (prefers-reduced-motion: reduce) {
    .modal-backdrop,
    :global(.spin) {
      animation: none !important;
    }
    .close-btn,
    .simulate-btn,
    .preset-chip,
    .mode-card,
    .cancel-btn,
    .apply-btn {
      transition: none !important;
    }
  }
</style>
