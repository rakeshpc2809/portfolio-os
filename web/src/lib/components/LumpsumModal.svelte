<script lang="ts">
  import { fetchJson, type RebalancePlanDto } from '../api';
  import { formatINR, formatPercent } from '../formatters';
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
    quietMode?: boolean;
    onclose?: () => void;
    onapply?: (plan: RebalancePlanDto) => void;
  }

  let { open = false, quietMode = false, onclose, onapply }: Props = $props();

  let amount = $state(50000);
  let includeRebalance = $state(false);
  let loading = $state(false);
  let error = $state<string | null>(null);
  let simulatedPlan = $state<RebalancePlanDto | null>(null);

  const PRESET_AMOUNTS = [25000, 50000, 100000, 250000, 500000];

  async function runSimulation() {
    if (!amount || amount <= 0) {
      error = 'Please enter a valid positive lump-sum amount.';
      return;
    }
    loading = true;
    error = null;
    try {
      const res = await fetchJson<RebalancePlanDto>('/rebalance/simulate-lumpsum', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: amount,
          includeRebalance: includeRebalance
        })
      });
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
          <span class="chips-label">Quick:</span>
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

        <!-- Mode Toggle -->
        <div class="mode-toggle-card">
          <label class="toggle-control">
            <input 
              type="checkbox" 
              bind:checked={includeRebalance}
              onchange={runSimulation}
            />
            <span class="toggle-slider"></span>
            <span class="toggle-text">
              <strong>Include Sell-Side Rebalancing:</strong>
              {#if includeRebalance}
                <span class="mode-desc">Liquidates overweight buckets in addition to deploying cash. May trigger capital gains tax.</span>
              {:else}
                <span class="mode-desc">Pure Inflow Mode (Tax-Free): Deploys fresh cash strictly to underweight asset classes.</span>
              {/if}
            </span>
          </label>
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

          <!-- Bucket Allocation Targets Table -->
          <div class="results-table-section">
            <div class="section-header">
              <h4 class="section-title">Recommended Capital Distribution</h4>
              <span class="section-badge">OPTIMAL DRIFT RESTORATION</span>
            </div>

            <table class="allocation-table">
              <thead>
                <tr>
                  <th>Bucket</th>
                  <th>Current Share</th>
                  <th>Target Share</th>
                  <th>Post Share</th>
                  <th class="right-align">Inflow Allocation</th>
                  <th class="right-align">Status</th>
                </tr>
              </thead>
              <tbody>
                {#if simulatedPlan.buy_side?.buckets}
                  {#each simulatedPlan.buy_side.buckets as b}
                    {@const alloc = b.amount_allocated ?? 0}
                    <tr>
                      <td class="bucket-name font-medium">{b.bucket}</td>
                      <td class="mono">{formatPercent(b.current_pct)}</td>
                      <td class="mono text-muted">{formatPercent(b.target_pct)}</td>
                      <td class="mono bold text-cyan">{formatPercent(b.post_pct ?? b.current_pct)}</td>
                      <td class="mono bold right-align" class:text-emerald={alloc > 0}>
                        {alloc > 0 ? `+${formatINR(alloc, quietMode)}` : '—'}
                      </td>
                      <td class="right-align">
                        {#if alloc > 0}
                          <span class="action-pill buy">BUY</span>
                        {:else}
                          <span class="action-pill hold">HOLD</span>
                        {/if}
                      </td>
                    </tr>
                  {/each}
                {/if}
              </tbody>
            </table>
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
    max-width: 720px;
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

  .close-btn:hover {
    color: var(--text-primary);
  }

  /* Input Section */
  .input-section {
    padding: 1.25rem 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 0.85rem;
    background: var(--surface-void);
    border-bottom: 1px solid var(--border-subtle);
  }

  .input-label {
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-muted);
    font-weight: 600;
    margin-bottom: 0.35rem;
    display: block;
  }

  .input-with-symbol {
    display: flex;
    align-items: center;
    position: relative;
    gap: 0.75rem;
  }

  .currency-prefix {
    position: absolute;
    left: 1rem;
    font-family: var(--font-mono);
    color: var(--text-muted);
    font-size: 1.1rem;
    pointer-events: none;
  }

  .amount-input {
    flex: 1;
    padding: 0.65rem 1rem 0.65rem 2.2rem;
    background: var(--surface-card);
    border: 1px solid var(--border-medium);
    border-radius: var(--radius-md);
    color: var(--text-primary);
    font-size: 1.15rem;
    font-weight: 600;
    outline: none;
    transition: border-color var(--transition-fast);
  }

  .amount-input:focus {
    border-color: var(--accent-cyan);
    box-shadow: 0 0 15px var(--glow-cyan);
  }

  .simulate-btn {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.65rem 1.25rem;
    background: var(--surface-glass);
    border: 1px solid var(--accent-cyan);
    border-radius: var(--radius-md);
    color: var(--accent-cyan);
    font-size: 0.85rem;
    font-weight: 600;
    cursor: pointer;
    transition: all var(--transition-fast);
  }

  .simulate-btn:hover:not(:disabled) {
    background: var(--accent-cyan);
    color: var(--surface-void);
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

  /* Mode Toggle */
  .mode-toggle-card {
    padding: 0.65rem 0.85rem;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
  }

  .toggle-control {
    display: flex;
    align-items: flex-start;
    gap: 0.75rem;
    cursor: pointer;
  }

  .toggle-control input {
    margin-top: 0.25rem;
    accent-color: var(--accent-cyan);
    width: 16px;
    height: 16px;
  }

  .toggle-text {
    display: flex;
    flex-direction: column;
    gap: 0.15rem;
    font-size: 0.82rem;
    color: var(--text-primary);
  }

  .mode-desc {
    font-size: 0.75rem;
    color: var(--text-muted);
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

  /* Allocation Table */
  .results-table-section {
    display: flex;
    flex-direction: column;
    gap: 0.65rem;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .section-title {
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--text-secondary);
    margin: 0;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .section-badge {
    font-size: 0.65rem;
    color: var(--accent-emerald);
    font-family: var(--font-mono);
  }

  .allocation-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.82rem;
  }

  .allocation-table th {
    padding: 0.55rem 0.75rem;
    text-align: left;
    font-size: 0.68rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-muted);
    background: var(--surface-void);
    border-bottom: 1px solid var(--border-subtle);
  }

  .allocation-table td {
    padding: 0.65rem 0.75rem;
    border-bottom: 1px solid hsla(215, 25%, 20%, 0.4);
    color: var(--text-secondary);
  }

  .action-pill {
    padding: 0.15rem 0.45rem;
    border-radius: var(--radius-sm);
    font-size: 0.68rem;
    font-weight: 700;
  }

  .action-pill.buy {
    background: hsla(158, 85%, 45%, 0.15);
    color: var(--accent-emerald);
    border: 1px solid hsla(158, 85%, 45%, 0.3);
  }

  .action-pill.hold {
    background: hsla(215, 25%, 35%, 0.2);
    color: var(--text-muted);
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

  .right-align { text-align: right; }

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
