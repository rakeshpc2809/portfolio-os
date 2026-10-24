<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchJson, type OverlapReportDto } from '../api';
  import { formatINR, formatPercent } from '../formatters';
  import { 
    AlertTriangle, 
    CheckCircle2, 
    RefreshCw, 
    X, 
    PieChart, 
    Layers, 
    ShieldAlert,
    ExternalLink
  } from '@lucide/svelte';

  interface Props {
    open?: boolean;
    quietMode?: boolean;
    onclose?: () => void;
  }

  let { open = false, quietMode = false, onclose }: Props = $props();

  let loading = $state(false);
  let error = $state<string | null>(null);
  let overlapData = $state<OverlapReportDto | null>(null);
  let activeTab = $state<'PAIRWISE' | 'STOCKS'>('PAIRWISE');

  async function loadOverlap() {
    loading = true;
    error = null;
    try {
      overlapData = await fetchJson<OverlapReportDto>('/analytics/overlap');
    } catch (e: any) {
      error = e.message || 'Failed to load portfolio overlap';
    } finally {
      loading = false;
    }
  }

  $effect(() => {
    if (open && !overlapData && !loading) {
      loadOverlap();
    }
  });

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      onclose?.();
    }
  }
</script>

{#if open}
  <div 
    class="modal-backdrop" 
    role="presentation"
    onclick={onclose}
  >
    <div 
      class="modal-container"
      role="dialog"
      aria-modal="true"
      aria-labelledby="overlap-modal-title"
      tabindex="-1"
      onclick={(e) => e.stopPropagation()}
      onkeydown={handleKeydown}
    >
      <!-- Modal Header -->
      <div class="modal-header">
        <div class="header-titles">
          <div class="tag-row">
            <span class="hud-tag">QUANT ENGINE v3.0</span>
            {#if overlapData?.overlap_summary}
              {@const isBreached = overlapData.overlap_summary.threshold_breached}
              <span class="status-pill" class:breached={isBreached} class:safe={!isBreached}>
                {#if isBreached}
                  <AlertTriangle size={12} />
                  <span>THRESHOLD BREACH (>30%)</span>
                {:else}
                  <CheckCircle2 size={12} />
                  <span>DIVERSIFICATION OPTIMAL</span>
                {/if}
              </span>
            {/if}
          </div>
          <h2 id="overlap-modal-title" class="title">Portfolio Overlap & Concentration Lookthrough</h2>
          <p class="subtitle">Deep-scan of underlying equity holdings across active mutual fund schemes</p>
        </div>

        <div class="header-actions">
          <button 
            type="button" 
            class="refresh-btn" 
            onclick={loadOverlap} 
            disabled={loading}
            title="Re-run DuckDB overlap calculation"
          >
            <RefreshCw size={14} class={loading ? 'spin' : ''} />
          </button>
          <button 
            type="button" 
            class="close-btn" 
            onclick={onclose} 
            aria-label="Close modal"
          >
            <X size={18} />
          </button>
        </div>
      </div>

      <!-- Max Overlap Callout Banner -->
      {#if overlapData?.overlap_summary}
        {@const summary = overlapData.overlap_summary}
        <div class="callout-banner" class:danger={summary.threshold_breached}>
          <div class="callout-icon">
            {#if summary.threshold_breached}
              <ShieldAlert size={20} />
            {:else}
              <PieChart size={20} />
            {/if}
          </div>
          <div class="callout-content">
            <div class="callout-header">
              <span class="callout-title">Max Pairwise Overlap:</span>
              <span class="callout-pct mono bold" class:danger={summary.threshold_breached}>
                {summary.max_overlap_percentage.toFixed(1)}%
              </span>
            </div>
            <div class="callout-pair">
              <span class="fund-name">{summary.fund_a_name}</span>
              <span class="connector">⟷</span>
              <span class="fund-name">{summary.fund_b_name}</span>
            </div>
          </div>
        </div>
      {/if}

      <!-- Tab Switcher -->
      <div class="tab-bar">
        <button 
          type="button" 
          class="tab-btn" 
          class:active={activeTab === 'PAIRWISE'}
          onclick={() => activeTab = 'PAIRWISE'}
        >
          <Layers size={14} />
          <span>Pairwise Overlap Matrix ({overlapData?.pairwise_overlap?.length || 0})</span>
        </button>
        <button 
          type="button" 
          class="tab-btn" 
          class:active={activeTab === 'STOCKS'}
          onclick={() => activeTab = 'STOCKS'}
        >
          <PieChart size={14} />
          <span>Top Stock Concentrations ({overlapData?.portfolio_top_stock_concentrations?.length || 0})</span>
        </button>
      </div>

      <!-- Modal Content Body -->
      <div class="modal-body">
        {#if loading && !overlapData}
          <div class="loading-state">
            <RefreshCw size={28} class="spin loading-icon" />
            <p>Running DuckDB lookthrough cross-join on holding portfolios...</p>
          </div>
        {:else if error}
          <div class="error-state">
            <AlertTriangle size={28} class="error-icon" />
            <p class="error-msg">{error}</p>
            <button type="button" class="retry-btn" onclick={loadOverlap}>Retry Analysis</button>
          </div>
        {:else if activeTab === 'PAIRWISE'}
          {#if !overlapData?.pairwise_overlap || overlapData.pairwise_overlap.length === 0}
            <div class="empty-state">
              <p>No pairwise overlap records found. Upload latest portfolio disclosures to calculate overlap.</p>
            </div>
          {:else}
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Scheme A</th>
                    <th>Scheme B</th>
                    <th>Overlap %</th>
                    <th class="right-align">Common Stocks</th>
                  </tr>
                </thead>
                <tbody>
                  {#each overlapData.pairwise_overlap as pair}
                    {@const pct = pair.overlap_percentage}
                    {@const isHigh = pct >= 30}
                    {@const isMid = pct >= 20 && pct < 30}
                    <tr>
                      <td class="fund-cell font-medium">{pair.fund_a_name}</td>
                      <td class="fund-cell font-medium">{pair.fund_b_name}</td>
                      <td>
                        <div class="pct-bar-wrapper">
                          <span class="mono bold val" class:danger={isHigh} class:warn={isMid}>
                            {pct.toFixed(1)}%
                          </span>
                          <div class="progress-track">
                            <div 
                              class="progress-fill" 
                              class:fill-danger={isHigh}
                              class:fill-warn={isMid}
                              class:fill-safe={!isHigh && !isMid}
                              style="width: {Math.min(pct, 100)}%"
                            ></div>
                          </div>
                        </div>
                      </td>
                      <td class="right-align mono">{pair.common_stock_count} stocks</td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
        {:else if activeTab === 'STOCKS'}
          {#if !overlapData?.portfolio_top_stock_concentrations || overlapData.portfolio_top_stock_concentrations.length === 0}
            <div class="empty-state">
              <p>No stock concentration records available.</p>
            </div>
          {:else}
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Stock / Symbol</th>
                    <th>Portfolio Share</th>
                    <th class="right-align">Rupee Exposure</th>
                    <th class="center-align">Audited</th>
                  </tr>
                </thead>
                <tbody>
                  {#each overlapData.portfolio_top_stock_concentrations as stock}
                    {@const share = stock.portfolio_percentage}
                    {@const isHeavy = share > 6.0}
                    <tr>
                      <td>
                        <div class="stock-title">
                          <span class="mono bold stock-symbol">{stock.stock_symbol}</span>
                          {#if isHeavy}
                            <span class="heavy-badge">CONCENTRATED (>6%)</span>
                          {/if}
                        </div>
                      </td>
                      <td>
                        <div class="pct-bar-wrapper">
                          <span class="mono bold val">{share.toFixed(2)}%</span>
                          <div class="progress-track">
                            <div 
                              class="progress-fill" 
                              class:fill-warn={isHeavy}
                              class:fill-safe={!isHeavy}
                              style="width: {Math.min(share * 10, 100)}%"
                            ></div>
                          </div>
                        </div>
                      </td>
                      <td class="right-align mono bold">{formatINR(stock.rupee_exposure, quietMode)}</td>
                      <td class="center-align">
                        {#if stock.is_audited}
                          <span class="audited-badge" title="Verified against latest portfolio disclosure">AUDITED</span>
                        {:else}
                          <span class="unaudited-badge">ESTIMATE</span>
                        {/if}
                      </td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
        {/if}
      </div>

      <!-- Footer -->
      <div class="modal-footer">
        <span class="footer-tip">Tip: Maximum healthy pairwise overlap between active equity funds is generally &lt;30%.</span>
        <button type="button" class="close-action-btn" onclick={onclose}>Done</button>
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
    max-width: 840px;
    max-height: 85vh;
    display: flex;
    flex-direction: column;
    box-shadow: 0 25px 60px rgba(0, 0, 0, 0.85), 0 0 40px var(--glow-cyan);
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
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.15rem 0.5rem;
    border-radius: var(--radius-full);
    font-size: 0.68rem;
    font-weight: 600;
  }

  .status-pill.safe {
    background: hsla(158, 85%, 45%, 0.15);
    color: var(--accent-emerald);
    border: 1px solid hsla(158, 85%, 45%, 0.3);
  }

  .status-pill.breached {
    background: hsla(350, 85%, 60%, 0.15);
    color: var(--accent-crimson);
    border: 1px solid hsla(350, 85%, 60%, 0.4);
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

  .header-actions {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .refresh-btn, .close-btn {
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
    transition: all var(--transition-fast);
  }

  .refresh-btn:hover, .close-btn:hover {
    color: var(--text-primary);
    border-color: var(--accent-cyan);
  }

  /* Callout Banner */
  .callout-banner {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin: 1rem 1.5rem 0.5rem;
    padding: 0.85rem 1.2rem;
    background: hsla(185, 85%, 52%, 0.08);
    border: 1px solid hsla(185, 85%, 52%, 0.25);
    border-radius: var(--radius-md);
  }

  .callout-banner.danger {
    background: hsla(350, 85%, 60%, 0.08);
    border-color: hsla(350, 85%, 60%, 0.35);
  }

  .callout-icon {
    color: var(--accent-cyan);
    display: flex;
  }

  .callout-banner.danger .callout-icon {
    color: var(--accent-crimson);
  }

  .callout-content {
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
  }

  .callout-header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .callout-title {
    font-size: 0.8rem;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .callout-pct {
    font-size: 1.15rem;
    color: var(--accent-cyan);
  }

  .callout-pct.danger {
    color: var(--accent-crimson);
  }

  .callout-pair {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 0.85rem;
  }

  .fund-name {
    font-weight: 500;
    color: var(--text-primary);
  }

  .connector {
    color: var(--text-muted);
  }

  /* Tab Bar */
  .tab-bar {
    display: flex;
    gap: 0.5rem;
    padding: 0.5rem 1.5rem;
    border-bottom: 1px solid var(--border-subtle);
  }

  .tab-btn {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.45rem 0.85rem;
    background: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    color: var(--text-secondary);
    font-size: 0.82rem;
    font-weight: 500;
    cursor: pointer;
    transition: all var(--transition-fast);
  }

  .tab-btn:hover {
    color: var(--text-primary);
  }

  .tab-btn.active {
    color: var(--accent-cyan);
    border-bottom-color: var(--accent-cyan);
  }

  /* Body Content */
  .modal-body {
    flex: 1;
    overflow-y: auto;
    padding: 1rem 1.5rem;
  }

  .loading-state, .error-state, .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 3rem 1rem;
    text-align: center;
    color: var(--text-muted);
    font-size: 0.85rem;
  }

  :global(.loading-icon) {
    color: var(--accent-cyan);
    margin-bottom: 1rem;
  }

  :global(.error-icon) {
    color: var(--accent-crimson);
    margin-bottom: 0.75rem;
  }

  .error-msg {
    color: var(--accent-crimson);
    margin-bottom: 1rem;
  }

  .retry-btn {
    padding: 0.45rem 1rem;
    background: var(--surface-glass);
    border: 1px solid var(--accent-cyan);
    border-radius: var(--radius-md);
    color: var(--accent-cyan);
    cursor: pointer;
  }

  /* Table styling */
  .table-container {
    overflow-x: auto;
  }

  .data-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.82rem;
  }

  .data-table th {
    padding: 0.6rem 0.8rem;
    text-align: left;
    font-size: 0.68rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-muted);
    background: var(--surface-void);
    border-bottom: 1px solid var(--border-subtle);
  }

  .data-table td {
    padding: 0.65rem 0.8rem;
    border-bottom: 1px solid hsla(215, 25%, 20%, 0.4);
    color: var(--text-secondary);
  }

  .data-table tr:hover td {
    background: var(--surface-card-hover);
  }

  .fund-cell {
    color: var(--text-primary);
    max-width: 240px;
  }

  .pct-bar-wrapper {
    display: flex;
    align-items: center;
    gap: 0.65rem;
  }

  .pct-bar-wrapper .val {
    width: 48px;
    flex-shrink: 0;
  }

  .pct-bar-wrapper .val.danger {
    color: var(--accent-crimson);
  }

  .pct-bar-wrapper .val.warn {
    color: var(--accent-amber);
  }

  .progress-track {
    flex: 1;
    height: 6px;
    background: var(--surface-void);
    border-radius: var(--radius-full);
    overflow: hidden;
    min-width: 60px;
  }

  .progress-fill {
    height: 100%;
    border-radius: var(--radius-full);
    transition: width var(--transition-normal);
  }

  .progress-fill.fill-safe {
    background: var(--accent-cyan);
  }

  .progress-fill.fill-warn {
    background: var(--accent-amber);
  }

  .progress-fill.fill-danger {
    background: var(--accent-crimson);
  }

  .right-align {
    text-align: right;
  }

  .center-align {
    text-align: center;
  }

  .stock-title {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .stock-symbol {
    color: var(--text-primary);
    font-size: 0.85rem;
  }

  .heavy-badge {
    padding: 0.1rem 0.35rem;
    border-radius: var(--radius-sm);
    font-size: 0.65rem;
    background: hsla(38, 92%, 50%, 0.15);
    color: var(--accent-amber);
    border: 1px solid hsla(38, 92%, 50%, 0.3);
  }

  .audited-badge {
    padding: 0.15rem 0.45rem;
    border-radius: var(--radius-sm);
    font-size: 0.65rem;
    font-weight: 700;
    background: hsla(158, 85%, 45%, 0.15);
    color: var(--accent-emerald);
  }

  .unaudited-badge {
    padding: 0.15rem 0.45rem;
    border-radius: var(--radius-sm);
    font-size: 0.65rem;
    background: hsla(215, 25%, 35%, 0.2);
    color: var(--text-muted);
  }

  /* Modal Footer */
  .modal-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.85rem 1.5rem;
    border-top: 1px solid var(--border-subtle);
    background: var(--surface-card-hover);
  }

  .footer-tip {
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .close-action-btn {
    padding: 0.45rem 1.25rem;
    background: var(--accent-cyan);
    color: var(--surface-void);
    border: none;
    border-radius: var(--radius-md);
    font-weight: 600;
    font-size: 0.85rem;
    cursor: pointer;
  }

  :global(.spin) {
    animation: spin 1s linear infinite;
  }

  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }

  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }
</style>
