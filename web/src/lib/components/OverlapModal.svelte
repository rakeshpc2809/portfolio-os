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
          <h2 id="overlap-modal-title" class="title">Portfolio Overlap & Concentration Lookthrough</h2>
          <div class="tag-row">
            <span class="hud-tag">Quant engine v3.0</span>
            {#if overlapData?.overlap_summary}
              {@const isBreached = overlapData.overlap_summary.threshold_breached}
              <span class="status-pill" class:breached={isBreached} class:safe={!isBreached}>
                {#if isBreached}
                  <AlertTriangle size={12} />
                  <span>Threshold breach (&gt;30%)</span>
                {:else}
                  <CheckCircle2 size={12} />
                  <span>Diversification optimal</span>
                {/if}
              </span>
            {/if}
          </div>
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

          <div class="provenance-lowerbound-hint">
            <span>Lower-bound estimate: Passive index schemes reflect top 10 to 20 constituent stocks. Full portfolio overlap may be marginally higher across minor tail holdings.</span>
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
            <div class="provenance-lowerbound-hint">
              <span>Lower-bound estimate: Passive index schemes reflect top 10 to 20 constituent stocks. Tail-end exposures (&lt;1%) are excluded from the lookthrough baseline.</span>
            </div>
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
                            <span class="heavy-badge">Concentrated (&gt;6%)</span>
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
                              style="transform: scaleX({Math.min(share * 10, 100) / 100});"
                            ></div>
                          </div>
                        </div>
                      </td>
                      <td class="right-align mono bold">{formatINR(stock.rupee_exposure, quietMode)}</td>
                      <td class="center-align">
                        {#if stock.is_audited}
                          <span class="audited-badge" title="Verified against latest portfolio disclosure">Audited</span>
                        {:else}
                          <span class="unaudited-badge">Estimate</span>
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
    max-width: 840px;
    max-height: 85vh;
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
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.15rem 0.5rem;
    border-radius: var(--radius-pill);
    font-size: 0.75rem;
    font-weight: 600;
    font-family: var(--font-outlier);
  }

  .status-pill.safe {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--color-ok);
  }

  .status-pill.breached {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--color-bad);
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
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    color: var(--color-muted);
    cursor: pointer;
    transition: color 0.15s ease, border-color 0.15s ease;
  }

  .refresh-btn:hover, .close-btn:hover {
    color: var(--color-ink);
    border-color: var(--color-cool);
  }

  .refresh-btn:focus-visible,
  .close-btn:focus-visible,
  .tab-btn:focus-visible,
  .retry-btn:focus-visible,
  .expand-icon-btn:focus-visible,
  .close-action-btn:focus-visible {
    outline: 2px solid var(--color-focus);
    outline-offset: 2px;
  }

  /* Callout Banner */
  .callout-banner {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin: 1rem 1.5rem 0.5rem;
    padding: 0.85rem 1.2rem;
    background: var(--color-cool-soft);
    border: 1px solid var(--color-cool);
    border-radius: var(--radius-card);
  }

  .callout-banner.danger {
    background: var(--color-bad-soft);
    border-color: var(--color-bad);
  }

  .callout-icon {
    color: var(--color-cool);
    display: flex;
  }

  .callout-banner.danger .callout-icon {
    color: var(--color-bad);
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
    color: var(--color-muted);
    font-weight: 600;
  }

  .callout-pct {
    font-size: 1.15rem;
    color: var(--color-cool);
    font-family: var(--font-outlier);
  }

  .callout-pct.danger {
    color: var(--color-bad);
  }

  .callout-pair {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 0.85rem;
  }

  .fund-name {
    font-weight: 500;
    color: var(--color-ink);
  }

  .connector {
    color: var(--color-muted);
  }

  /* Tab Bar */
  .tab-bar {
    display: flex;
    gap: 0.5rem;
    padding: 0.5rem 1.5rem;
    border-bottom: 1px solid var(--color-rule);
    background: var(--color-paper-3);
  }

  .tab-btn {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.45rem 0.85rem;
    background: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    color: var(--color-muted);
    font-size: 0.82rem;
    font-weight: 500;
    cursor: pointer;
    transition: color 0.15s ease, border-color 0.15s ease;
  }

  .tab-btn:hover {
    color: var(--color-ink);
  }

  .tab-btn.active {
    color: var(--color-cool);
    border-bottom-color: var(--color-cool);
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
    color: var(--color-muted);
    font-size: 0.85rem;
  }

  :global(.loading-icon) {
    color: var(--color-cool);
    margin-bottom: 1rem;
  }

  :global(.error-icon) {
    color: var(--color-bad);
    margin-bottom: 0.75rem;
  }

  .error-msg {
    color: var(--color-bad);
    margin-bottom: 1rem;
  }

  .retry-btn {
    padding: 0.45rem 1rem;
    background: var(--color-paper-3);
    border: 1px solid var(--color-cool);
    border-radius: var(--radius-pill);
    color: var(--color-cool);
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
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
  }

  .provenance-lowerbound-hint {
    font-size: 0.75rem;
    color: var(--color-muted);
    background: var(--color-paper-3);
    border: 1px dashed var(--color-rule);
    border-radius: var(--radius-pill);
    padding: 0.35rem 0.65rem;
    margin-bottom: 0.75rem;
    display: flex;
    align-items: center;
    gap: 0.4rem;
  }

  .search-box {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    background: var(--color-paper);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    padding: 0.25rem 0.5rem;
    width: 260px;
  }

  :global(.search-icon) {
    color: var(--color-muted);
  }

  .search-input {
    background: transparent;
    border: none;
    outline: none;
    color: var(--color-ink);
    font-size: 0.78rem;
    width: 100%;
  }

  .clear-search-btn {
    background: transparent;
    border: none;
    color: var(--color-muted);
    cursor: pointer;
    display: flex;
    align-items: center;
  }

  .clear-search-btn:hover {
    color: var(--color-ink);
  }

  .toggle-filter-label {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    font-size: 0.78rem;
    color: var(--color-muted);
    cursor: pointer;
    user-select: none;
  }

  .filter-checkbox {
    accent-color: var(--color-cool);
    cursor: pointer;
  }

  /* Table styling */
  .table-container {
    overflow-x: auto;
  }

  .row-clickable {
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .row-expanded td {
    background: var(--color-paper-3) !important;
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
    color: var(--color-muted);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: var(--radius-pill);
    transition: color 0.15s ease, background 0.15s ease;
  }

  .row-clickable:hover .expand-icon-btn {
    color: var(--color-cool);
    background: var(--color-cool-soft);
  }

  .common-stocks-drawer-row td {
    padding: 0 0.8rem 0.85rem !important;
    background: var(--color-paper-3);
    border-bottom: 1px solid var(--color-rule) !important;
  }

  .common-stocks-drawer {
    background: var(--color-paper);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    padding: 0.85rem 1rem;
    display: flex;
    flex-direction: column;
    gap: 0.65rem;
  }

  .drawer-header-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.75rem;
  }

  .drawer-label {
    font-weight: 600;
    color: var(--color-cool);
  }

  .drawer-hint {
    color: var(--color-muted);
    font-style: italic;
  }

  .stocks-chips-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
    gap: 0.5rem;
  }

  .stock-overlap-chip {
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
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
    color: var(--color-ink);
  }

  .chip-overlap-pct {
    font-size: 0.75rem;
    color: var(--color-ok);
  }

  .chip-bottom {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    font-size: 0.75rem;
    color: var(--color-muted);
  }

  .weight-sep {
    color: var(--color-rule-2);
  }

  .data-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.82rem;
  }

  .data-table th {
    padding: 0.6rem 0.8rem;
    text-align: left;
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--color-muted);
    background: var(--color-paper-3);
    border-bottom: 1px solid var(--color-rule);
  }

  .data-table td {
    padding: 0.65rem 0.8rem;
    border-bottom: 1px solid var(--color-rule);
    color: var(--color-ink-2);
  }

  .data-table tr:hover td {
    background: var(--color-paper-3);
  }

  .fund-cell {
    color: var(--color-ink);
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
    color: var(--color-bad);
  }

  .pct-bar-wrapper .val.warn {
    color: var(--color-accent);
  }

  .progress-track {
    flex: 1;
    height: 6px;
    background: var(--color-paper-3);
    border-radius: var(--radius-pill);
    overflow: hidden;
    min-width: 60px;
  }

  .progress-fill {
    height: 100%;
    width: 100%;
    transform-origin: left;
    border-radius: var(--radius-pill);
    transition: transform 0.15s ease;
  }

  .progress-fill.fill-safe {
    background: var(--color-cool);
  }

  .progress-fill.fill-warn {
    background: var(--color-accent);
  }

  .progress-fill.fill-danger {
    background: var(--color-bad);
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
    color: var(--color-ink);
    font-size: 0.85rem;
  }

  .heavy-badge {
    padding: 0.1rem 0.35rem;
    border-radius: var(--radius-pill);
    font-size: 0.75rem;
    background: var(--color-accent-soft);
    color: var(--color-accent);
    border: 1px solid var(--color-accent);
  }

  .audited-badge {
    padding: 0.15rem 0.45rem;
    border-radius: var(--radius-pill);
    font-size: 0.75rem;
    font-weight: 600;
    background: var(--color-ok-soft);
    color: var(--color-ok);
  }

  .unaudited-badge {
    padding: 0.15rem 0.45rem;
    border-radius: var(--radius-pill);
    font-size: 0.75rem;
    background: var(--color-paper-3);
    color: var(--color-muted);
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
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    font-size: 0.78rem;
    color: var(--color-muted);
  }

  .meta-val {
    color: var(--color-cool);
  }

  .upset-card-list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .upset-combo-card {
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    padding: 1rem 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    transition: border-color 0.15s ease;
  }

  .upset-combo-card:hover {
    border-color: var(--color-cool);
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
    font-size: 0.75rem;
    font-weight: 600;
    padding: 0.2rem 0.6rem;
    background: var(--color-cool-soft);
    color: var(--color-cool);
    border: 1px solid var(--color-cool);
    border-radius: var(--radius-pill);
  }

  .combo-metrics {
    display: flex;
    align-items: center;
    gap: 0.8rem;
    font-size: 0.82rem;
  }

  .combo-stock-count {
    color: var(--color-muted);
  }

  .combo-weight {
    color: var(--color-ok);
    font-size: 0.9rem;
  }

  .combo-stocks-preview {
    display: flex;
    flex-wrap: wrap;
    gap: 0.4rem;
  }

  .shared-stock-chip {
    font-size: 0.75rem;
    font-family: var(--font-outlier);
    padding: 0.2rem 0.5rem;
    background: var(--color-paper);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    color: var(--color-ink);
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
  }

  .stock-chip-weight {
    font-size: 0.75rem;
    color: var(--color-cool);
    font-weight: 600;
  }

  .shared-stock-more {
    font-size: 0.75rem;
    color: var(--color-muted);
    align-self: center;
    font-style: italic;
  }

  /* Modal Footer */
  .modal-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.85rem 1.5rem;
    border-top: 1px solid var(--color-rule);
    background: var(--color-paper);
  }

  .footer-tip {
    font-size: 0.75rem;
    color: var(--color-muted);
  }

  .close-action-btn {
    padding: 0.45rem 1.25rem;
    background: var(--color-cool);
    color: var(--color-paper-3);
    border: none;
    border-radius: var(--radius-pill);
    font-weight: 600;
    font-size: 0.85rem;
    cursor: pointer;
    transition: filter 0.15s ease;
  }
  .close-action-btn:hover {
    filter: brightness(1.1);
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

  @media (prefers-reduced-motion: reduce) {
    .modal-backdrop,
    :global(.spin) {
      animation: none !important;
    }
    .refresh-btn,
    .close-btn,
    .tab-btn,
    .expand-icon-btn,
    .progress-fill,
    .upset-combo-card,
    .close-action-btn {
      transition: none !important;
    }
  }
</style>
