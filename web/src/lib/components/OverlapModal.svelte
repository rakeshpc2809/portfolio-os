<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchJson, fetchMultiFundUpSetAnalytics, type OverlapReportDto, type MultiFundUpSetResponse } from '../api';
  import { formatINR, formatPercent } from '../formatters';
  import { 
    AlertTriangle, 
    CheckCircle2, 
    RefreshCw, 
    X, 
    PieChart, 
    Layers, 
    ShieldAlert,
    ExternalLink,
    Network,
    Search,
    ChevronDown,
    ChevronUp
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
  let upsetData = $state<MultiFundUpSetResponse | null>(null);
  let upsetLoading = $state(false);
  let upsetError = $state<string | null>(null);
  let activeTab = $state<'PAIRWISE' | 'STOCKS' | 'UPSET'>('PAIRWISE');
  let onlyOverlapping = $state(true);
  let pairSearch = $state('');
  let expandedPairKey = $state<string | null>(null);

  const pairwisePairs = $derived.by(() => {
    if (!overlapData) return [];
    if (Array.isArray(overlapData.pairwise_matrix) && overlapData.pairwise_matrix.length > 0) {
      return overlapData.pairwise_matrix;
    }
    if (Array.isArray(overlapData.pairwise_overlap)) {
      return overlapData.pairwise_overlap;
    }
    if (overlapData.pairwise_overlap && typeof overlapData.pairwise_overlap === 'object') {
      const obj = overlapData.pairwise_overlap as any;
      if (obj.fund_a_name || obj.overlap_percentage !== undefined) {
        return [obj];
      }
    }
    return [];
  });

  const totalPairsCount = $derived(pairwisePairs.length);
  const overlappingPairsCount = $derived(pairwisePairs.filter(p => (p.overlap_percentage ?? 0) > 0).length);

  const filteredPairs = $derived.by(() => {
    let list = [...pairwisePairs];
    if (onlyOverlapping) {
      list = list.filter(p => (p.overlap_percentage ?? 0) > 0);
    }
    if (pairSearch.trim()) {
      const q = pairSearch.toLowerCase().trim();
      list = list.filter(p => 
        (p.fund_a_name && p.fund_a_name.toLowerCase().includes(q)) ||
        (p.fund_b_name && p.fund_b_name.toLowerCase().includes(q))
      );
    }
    return list.sort((a, b) => (b.overlap_percentage ?? 0) - (a.overlap_percentage ?? 0));
  });

  function togglePairExpand(key: string) {
    if (expandedPairKey === key) {
      expandedPairKey = null;
    } else {
      expandedPairKey = key;
    }
  }

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

  async function loadUpSet() {
    upsetLoading = true;
    upsetError = null;
    try {
      upsetData = await fetchMultiFundUpSetAnalytics();
    } catch (e: any) {
      upsetError = e.message || 'Failed to load UpSet intersection analytics';
    } finally {
      upsetLoading = false;
    }
  }

  $effect(() => {
    if (open && !overlapData && !loading) {
      loadOverlap();
      loadUpSet();
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
          <span>Pairwise Overlap Matrix ({overlappingPairsCount > 0 ? `${overlappingPairsCount} Active` : totalPairsCount})</span>
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
        <button 
          type="button" 
          class="tab-btn" 
          class:active={activeTab === 'UPSET'}
          onclick={() => { activeTab = 'UPSET'; if (!upsetData && !upsetLoading) loadUpSet(); }}
        >
          <Network size={14} />
          <span>Multi-Fund Intersections ({upsetData?.upset_combinations?.length || 0})</span>
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
          <!-- Pairwise Toolbar -->
          <div class="pairwise-toolbar">
            <div class="search-box">
              <Search size={14} class="search-icon" />
              <input 
                type="text" 
                placeholder="Search scheme name..." 
                bind:value={pairSearch} 
                class="search-input"
              />
              {#if pairSearch}
                <button type="button" class="clear-search-btn" onclick={() => pairSearch = ''}>
                  <X size={12} />
                </button>
              {/if}
            </div>
            <label class="toggle-filter-label">
              <input 
                type="checkbox" 
                bind:checked={onlyOverlapping} 
                class="filter-checkbox"
              />
              <span>Only show overlapping schemes ({overlappingPairsCount})</span>
            </label>
          </div>

          {#if filteredPairs.length === 0}
            <div class="empty-state">
              <p>No pairwise overlap records match current search or filters.</p>
              {#if onlyOverlapping}
                <button type="button" class="retry-btn" onclick={() => { onlyOverlapping = false; pairSearch = ''; }}>Show All {totalPairsCount} Pairs</button>
              {/if}
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
                    <th class="expand-col"></th>
                  </tr>
                </thead>
                <tbody>
                  {#each filteredPairs as pair}
                    {@const pct = pair.overlap_percentage ?? 0}
                    {@const isHigh = pct >= 30}
                    {@const isMid = pct >= 20 && pct < 30}
                    {@const pairKey = `${pair.fund_a || pair.fund_a_name}_${pair.fund_b || pair.fund_b_name}`}
                    {@const isExpanded = expandedPairKey === pairKey}
                    {@const hasCommonStocks = (pair.common_stocks && pair.common_stocks.length > 0) || (pair.common_stock_count ?? 0) > 0}
                    <tr 
                      class="pair-row" 
                      class:row-clickable={hasCommonStocks}
                      class:row-expanded={isExpanded}
                      onclick={() => hasCommonStocks && togglePairExpand(pairKey)}
                    >
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
                      <td class="right-align mono">
                        {pair.common_stock_count ?? 0} stocks
                      </td>
                      <td class="expand-col">
                        {#if hasCommonStocks}
                          <button 
                            type="button" 
                            class="expand-icon-btn" 
                            aria-label={isExpanded ? 'Collapse common stocks' : 'Expand common stocks'}
                          >
                            {#if isExpanded}
                              <ChevronUp size={14} />
                            {:else}
                              <ChevronDown size={14} />
                            {/if}
                          </button>
                        {/if}
                      </td>
                    </tr>
                    {#if isExpanded && pair.common_stocks && pair.common_stocks.length > 0}
                      <tr class="common-stocks-drawer-row">
                        <td colspan="5">
                          <div class="common-stocks-drawer">
                            <div class="drawer-header-meta">
                              <span class="drawer-label">Common Stock Lookthrough ({pair.common_stocks.length} overlapping holdings):</span>
                              <span class="drawer-hint">Overlap contribution = min(Weight A, Weight B)</span>
                            </div>
                            <div class="stocks-chips-grid">
                              {#each pair.common_stocks as stock}
                                <div class="stock-overlap-chip">
                                  <div class="chip-top">
                                    <span class="chip-symbol mono bold">{stock.stock_symbol}</span>
                                    <span class="chip-overlap-pct mono bold">+{stock.overlap_pct?.toFixed(2) ?? '0.00'}%</span>
                                  </div>
                                  <div class="chip-bottom">
                                    <span class="weight-a" title={pair.fund_a_name}>A: {stock.weight_a?.toFixed(1) ?? '0.0'}%</span>
                                    <span class="weight-sep">|</span>
                                    <span class="weight-b" title={pair.fund_b_name}>B: {stock.weight_b?.toFixed(1) ?? '0.0'}%</span>
                                  </div>
                                </div>
                              {/each}
                            </div>
                          </div>
                        </td>
                      </tr>
                    {/if}
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
        {:else if activeTab === 'UPSET'}
          {#if upsetLoading && !upsetData}
            <div class="loading-state">
              <RefreshCw size={28} class="spin loading-icon" />
              <p>Computing n-way set intersections in DuckDB...</p>
            </div>
          {:else if upsetError}
            <div class="error-state">
              <AlertTriangle size={28} class="error-icon" />
              <p class="error-msg">{upsetError}</p>
              <button type="button" class="retry-btn" onclick={loadUpSet}>Retry</button>
            </div>
          {:else if !upsetData?.upset_combinations || upsetData.upset_combinations.length === 0}
            <div class="empty-state">
              <p>No multi-fund stock intersections identified across evaluated funds.</p>
            </div>
          {:else}
            <div class="upset-container">
              <div class="upset-meta-bar">
                <span class="meta-item">Coverage: <strong class="meta-val">{upsetData.holding_coverage_type}</strong></span>
                <span class="meta-item">Source: <strong class="meta-val">{upsetData.constituent_source}</strong></span>
                <span class="meta-item">Evaluated Schemes: <strong class="meta-val">{upsetData.evaluated_funds?.length || 0}</strong></span>
              </div>
              <div class="upset-card-list">
                {#each upsetData.upset_combinations as combo}
                  <div class="upset-combo-card">
                    <div class="combo-card-header">
                      <div class="combo-funds-tags">
                        {#each combo.participating_funds as fundId}
                          <span class="combo-fund-badge">{fundId}</span>
                        {/each}
                      </div>
                      <div class="combo-metrics">
                        <span class="combo-stock-count mono">{combo.stock_count} shared stocks</span>
                        <span class="combo-weight mono bold">{combo.total_overlap_weight.toFixed(2)}% overlap</span>
                      </div>
                    </div>
                    <div class="combo-stocks-preview">
                      {#each combo.stocks.slice(0, 8) as stock}
                        <span class="shared-stock-chip" title="Min weight: {stock.min_weight}% | Total: {stock.total_weight}%">
                          {stock.stock_symbol} <span class="stock-chip-weight">{stock.min_weight}%</span>
                        </span>
                      {/each}
                      {#if combo.stocks.length > 8}
                        <span class="shared-stock-more">+{combo.stocks.length - 8} more</span>
                      {/if}
                    </div>
                  </div>
                {/each}
              </div>
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

  /* Pairwise Toolbar */
  .pairwise-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.75rem;
    margin-bottom: 0.85rem;
    padding: 0.5rem 0.75rem;
    background: var(--surface-glass, rgba(255, 255, 255, 0.02));
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
  }

  .search-box {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    background: var(--surface-void, #0a0e17);
    border: 1px solid var(--border-medium);
    border-radius: var(--radius-sm);
    padding: 0.25rem 0.5rem;
    width: 260px;
  }

  :global(.search-icon) {
    color: var(--text-muted);
  }

  .search-input {
    background: transparent;
    border: none;
    outline: none;
    color: var(--text-primary);
    font-size: 0.78rem;
    width: 100%;
  }

  .clear-search-btn {
    background: transparent;
    border: none;
    color: var(--text-muted);
    cursor: pointer;
    display: flex;
    align-items: center;
  }

  .clear-search-btn:hover {
    color: var(--text-primary);
  }

  .toggle-filter-label {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    font-size: 0.78rem;
    color: var(--text-secondary);
    cursor: pointer;
    user-select: none;
  }

  .filter-checkbox {
    accent-color: var(--accent-cyan);
    cursor: pointer;
  }

  /* Table styling */
  .table-container {
    overflow-x: auto;
  }

  .row-clickable {
    cursor: pointer;
    transition: background var(--transition-fast);
  }

  .row-expanded td {
    background: hsla(185, 85%, 52%, 0.05) !important;
    border-bottom: none !important;
  }

  .expand-col {
    width: 32px;
    text-align: center;
    padding: 0.4rem !important;
  }

  .expand-icon-btn {
    background: transparent;
    border: none;
    color: var(--text-muted);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: var(--radius-sm);
    transition: all var(--transition-fast);
  }

  .row-clickable:hover .expand-icon-btn {
    color: var(--accent-cyan);
    background: hsla(185, 85%, 52%, 0.1);
  }

  .common-stocks-drawer-row td {
    padding: 0 0.8rem 0.85rem !important;
    background: hsla(185, 85%, 52%, 0.04);
    border-bottom: 1px solid var(--border-subtle) !important;
  }

  .common-stocks-drawer {
    background: rgba(10, 15, 25, 0.7);
    border: 1px solid hsla(185, 85%, 52%, 0.2);
    border-radius: var(--radius-md);
    padding: 0.85rem 1rem;
    display: flex;
    flex-direction: column;
    gap: 0.65rem;
  }

  .drawer-header-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.72rem;
  }

  .drawer-label {
    font-weight: 600;
    color: var(--accent-cyan);
  }

  .drawer-hint {
    color: var(--text-muted);
    font-style: italic;
  }

  .stocks-chips-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
    gap: 0.5rem;
  }

  .stock-overlap-chip {
    background: var(--surface-card, rgba(20, 27, 45, 0.85));
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    padding: 0.45rem 0.6rem;
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
  }

  .chip-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .chip-symbol {
    font-size: 0.78rem;
    color: var(--text-primary);
  }

  .chip-overlap-pct {
    font-size: 0.75rem;
    color: var(--accent-emerald, #10b981);
  }

  .chip-bottom {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    font-size: 0.68rem;
    color: var(--text-muted);
  }

  .weight-sep {
    color: var(--border-medium);
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

  /* UpSet Multi-Fund Intersection Styles */
  .upset-container {
    display: flex;
    flex-direction: column;
    gap: 1rem;
    padding: 0.5rem 0;
  }

  .upset-meta-bar {
    display: flex;
    gap: 1.5rem;
    padding: 0.65rem 1rem;
    background: var(--surface-glass, rgba(255, 255, 255, 0.03));
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    font-size: 0.78rem;
    color: var(--text-muted);
  }

  .meta-val {
    color: var(--accent-cyan);
  }

  .upset-card-list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .upset-combo-card {
    background: var(--surface-glass, rgba(255, 255, 255, 0.02));
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    padding: 1rem 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    transition: border-color var(--transition-fast);
  }

  .upset-combo-card:hover {
    border-color: rgba(6, 182, 212, 0.35);
  }

  .combo-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.6rem;
  }

  .combo-funds-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.4rem;
  }

  .combo-fund-badge {
    font-size: 0.72rem;
    font-weight: 600;
    padding: 0.2rem 0.6rem;
    background: rgba(6, 182, 212, 0.12);
    color: var(--accent-cyan);
    border: 1px solid rgba(6, 182, 212, 0.25);
    border-radius: var(--radius-sm);
  }

  .combo-metrics {
    display: flex;
    align-items: center;
    gap: 0.8rem;
    font-size: 0.82rem;
  }

  .combo-stock-count {
    color: var(--text-muted);
  }

  .combo-weight {
    color: var(--accent-emerald, #10b981);
    font-size: 0.9rem;
  }

  .combo-stocks-preview {
    display: flex;
    flex-wrap: wrap;
    gap: 0.4rem;
  }

  .shared-stock-chip {
    font-size: 0.75rem;
    font-family: var(--font-mono);
    padding: 0.2rem 0.5rem;
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid var(--border-hairline, rgba(255, 255, 255, 0.08));
    border-radius: var(--radius-sm);
    color: var(--text-primary);
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
  }

  .stock-chip-weight {
    font-size: 0.68rem;
    color: var(--accent-cyan);
    font-weight: 600;
  }

  .shared-stock-more {
    font-size: 0.72rem;
    color: var(--text-muted);
    align-self: center;
    font-style: italic;
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
