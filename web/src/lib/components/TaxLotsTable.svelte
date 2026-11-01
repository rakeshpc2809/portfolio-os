<script lang="ts">
  import type { HoldingDto, LotDto } from '../api';
  import { formatINR, formatPercent } from '../formatters';
  import { 
    Filter, 
    Calendar, 
    ArrowUpRight, 
    ArrowDownRight, 
    ShieldCheck, 
    Clock, 
    Layers, 
    Sparkles,
    ChevronDown,
    ChevronRight,
    Search
  } from '@lucide/svelte';

  interface Props {
    holdings?: HoldingDto[];
    quietMode?: boolean;
    ltcgRemaining?: number;
  }

  let { holdings = [], quietMode = false, ltcgRemaining = 0 }: Props = $props();

  type FilterType = 'ALL' | 'LTCG' | 'STCG' | 'HARVEST';
  let activeFilter = $state<FilterType>('ALL');
  let searchQuery = $state('');
  let expandedSchemes = $state<Record<string, boolean>>({});
  let selectedLot = $state<{ holding: HoldingDto; lot: LotDto } | null>(null);

  // Flattened lots with parent holding reference
  interface EnrichedLot extends LotDto {
    holding: HoldingDto;
  }

  const allLots = $derived.by<EnrichedLot[]>(() => {
    const list: EnrichedLot[] = [];
    for (const h of holdings) {
      if (h.lots && h.lots.length > 0) {
        for (const lot of h.lots) {
          list.push({ ...lot, holding: h });
        }
      }
    }
    return list;
  });

  const filteredLots = $derived.by<EnrichedLot[]>(() => {
    let list = allLots;
    const q = searchQuery.toLowerCase().trim();
    if (q) {
      list = list.filter(
        (l: EnrichedLot) => l.holding.asset_name.toLowerCase().includes(q) ||
             l.holding.isin.toLowerCase().includes(q) ||
             (l.lot_id && l.lot_id.toLowerCase().includes(q))
      );
    }

    if (activeFilter === 'LTCG') {
      list = list.filter((l: EnrichedLot) => l.tax_term === 'LONG_TERM');
    } else if (activeFilter === 'STCG') {
      list = list.filter((l: EnrichedLot) => l.tax_term === 'SHORT_TERM');
    } else if (activeFilter === 'HARVEST') {
      list = list.filter((l: EnrichedLot) => (l.realized_or_unrealized_gain || 0) < 0);
    }

    return list;
  });

  // Grouped by holding
  interface GroupedHolding {
    holding: HoldingDto;
    lots: EnrichedLot[];
    totalCost: number;
    totalValue: number;
    totalGain: number;
    gainPct: number;
  }

  const groupedSchemes = $derived.by<GroupedHolding[]>(() => {
    const map = new Map<string, GroupedHolding>();

    for (const lot of filteredLots) {
      const isin = lot.holding.isin;
      if (!map.has(isin)) {
        map.set(isin, {
          holding: lot.holding,
          lots: [],
          totalCost: 0,
          totalValue: 0,
          totalGain: 0,
          gainPct: 0,
        });
      }
      const g = map.get(isin)!;
      g.lots.push(lot);
      g.totalCost += (lot.cost_basis || 0);
      g.totalValue += (lot.current_value || 0);
      g.totalGain += (lot.realized_or_unrealized_gain || 0);
    }

    for (const g of map.values()) {
      g.gainPct = g.totalCost > 0 ? (g.totalGain / g.totalCost) * 100 : 0;
    }

    return Array.from(map.values()).sort((a, b) => b.totalValue - a.totalValue);
  });

  // Summary tallies
  const metrics = $derived.by(() => {
    let ltcgCount = 0;
    let ltcgGain = 0;
    let stcgCount = 0;
    let stcgGain = 0;
    let harvestCount = 0;
    let harvestLoss = 0;

    for (const l of allLots) {
      const g = l.realized_or_unrealized_gain || 0;
      if (l.tax_term === 'LONG_TERM') {
        ltcgCount++;
        ltcgGain += g;
      } else {
        stcgCount++;
        stcgGain += g;
      }
      if (g < 0) {
        harvestCount++;
        harvestLoss += Math.abs(g);
      }
    }

    return { ltcgCount, ltcgGain, stcgCount, stcgGain, harvestCount, harvestLoss };
  });


  function toggleScheme(isin: string) {
    expandedSchemes[isin] = !expandedSchemes[isin];
  }

  function setFilter(f: FilterType) {
    activeFilter = f;
  }

  function openLotModal(holding: HoldingDto, lot: LotDto) {
    selectedLot = { holding, lot };
  }

  function closeLotModal() {
    selectedLot = null;
  }
</script>

<div class="tax-lots-container">
  <!-- Controls Bar: Filter Chips & Search -->
  <div class="controls-bar">
    <div class="filter-chips">
      <button 
        type="button" 
        class="chip" 
        class:active={activeFilter === 'ALL'}
        onclick={() => setFilter('ALL')}
      >
        <Layers size={14} />
        <span>All Lots ({allLots.length})</span>
      </button>

      <button 
        type="button" 
        class="chip ltcg-chip" 
        class:active={activeFilter === 'LTCG'}
        onclick={() => setFilter('LTCG')}
      >
        <ShieldCheck size={14} />
        <span>LTCG Mature ({metrics.ltcgCount})</span>
        <span class="chip-badge" class:positive={metrics.ltcgGain >= 0} class:negative={metrics.ltcgGain < 0}>{formatINR(metrics.ltcgGain, quietMode)}</span>
      </button>

      <button 
        type="button" 
        class="chip stcg-chip" 
        class:active={activeFilter === 'STCG'}
        onclick={() => setFilter('STCG')}
      >
        <Clock size={14} />
        <span>STCG Locked ({metrics.stcgCount})</span>
        <span class="chip-badge" class:positive={metrics.stcgGain > 0} class:neutral={metrics.stcgGain === 0} class:negative={metrics.stcgGain < 0}>{formatINR(metrics.stcgGain, quietMode)}</span>
      </button>

      {#if metrics.harvestCount > 0}
        <button 
          type="button" 
          class="chip harvest-chip" 
          class:active={activeFilter === 'HARVEST'}
          onclick={() => setFilter('HARVEST')}
        >
          <Sparkles size={14} />
          <span>Loss Harvest ({metrics.harvestCount})</span>
          <span class="chip-badge negative">-{formatINR(metrics.harvestLoss, quietMode)}</span>
        </button>
      {/if}
    </div>

    <div class="search-box">
      <Search size={14} class="search-icon" />
      <input 
        type="text" 
        placeholder="Filter schemes or ISIN..." 
        bind:value={searchQuery}
        class="search-input"
      />
    </div>
  </div>

  <!-- Exemption banner if headroom is available -->
  {#if ltcgRemaining > 0}
    <div class="exemption-banner">
      <div class="banner-icon">
        <Sparkles size={16} />
      </div>
      <div class="banner-text">
        <span class="strong">Sec 112A Tax-Harvest Opportunity:</span>
        <span>You have <strong>{formatINR(ltcgRemaining, quietMode)}</strong> of tax-free LTCG headroom remaining this financial year (exempt up to ₹1.25 Lakh).</span>
      </div>
      <a 
        href="/api/v1/tax/harvest-calendar.ics" 
        download="portfolio_harvest_calendar.ics"
        class="calendar-feed-btn"
        title="Download iCalendar file with lot maturation alarms"
      >
        <Calendar size={13} />
        <span>Add Maturation Reminders (.ics)</span>
      </a>
    </div>
  {/if}

  <!-- Scheme Groups Accordion -->
  {#if groupedSchemes.length === 0}
    <div class="empty-state">
      <Filter size={32} class="empty-icon" />
      <p class="empty-title">No matching tax lots found</p>
      <p class="empty-desc">Try clearing your search query or selecting a different tax filter.</p>
    </div>
  {:else}
    <div class="schemes-list">
      {#each groupedSchemes as group (group.holding.isin)}

        {@const isExpanded = expandedSchemes[group.holding.isin] ?? false}
        <div class="scheme-group-card" class:expanded={isExpanded}>
          <!-- Header Bar -->
          <div 
            class="scheme-header" 
            role="button"
            tabindex="0"
            onclick={() => toggleScheme(group.holding.isin)}
            onkeydown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleScheme(group.holding.isin); } }}
          >
            <div class="scheme-left">
              <button 
                type="button" 
                class="expand-btn" 
                aria-label={isExpanded ? 'Collapse scheme' : 'Expand scheme'}
              >
                {#if isExpanded}
                  <ChevronDown size={16} />
                {:else}
                  <ChevronRight size={16} />
                {/if}
              </button>
              <div class="scheme-info">
                <div class="name-row">
                  <span class="scheme-name">{group.holding.asset_name}</span>
                  <span class="isin-pill">{group.holding.isin}</span>
                  <span class="bucket-pill">{group.holding.bucket}</span>
                </div>
                <div class="sub-row">
                  <span class="lot-count">{group.lots.length} {group.lots.length === 1 ? 'lot' : 'lots'} matched</span>
                  <span class="bullet">•</span>
                  <span>NAV: {formatINR(group.holding.current_nav, false)} ({group.holding.nav_date})</span>
                </div>
              </div>
            </div>

            <div class="scheme-right">
              <div class="metric-pair">
                <span class="metric-label">Invested</span>
                <span class="metric-val mono">{formatINR(group.totalCost, quietMode)}</span>
              </div>
              <div class="metric-pair">
                <span class="metric-label">Current Value</span>
                <span class="metric-val mono bold">{formatINR(group.totalValue, quietMode)}</span>
              </div>
              <div class="metric-pair gain-pair">
                <span class="metric-label">Total Gain</span>
                <div class="gain-badge" class:positive={group.totalGain >= 0} class:negative={group.totalGain < 0}>
                  {#if group.totalGain >= 0}
                    <ArrowUpRight size={14} />
                  {:else}
                    <ArrowDownRight size={14} />
                  {/if}
                  <span class="mono bold">{formatINR(group.totalGain, quietMode)}</span>
                  <span class="pct mono">({formatPercent(group.gainPct)})</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Expanded Lots Table -->
          {#if isExpanded}
            <div class="lots-table-wrapper">
              <table class="lots-table">
                <thead>
                  <tr>
                    <th>Lot ID</th>
                    <th>Acquisition Date</th>
                    <th>Units</th>
                    <th>Cost NAV</th>
                    <th>Cost Basis</th>
                    <th>Current Value</th>
                    <th>Unrealized Gain</th>
                    <th>Holding Period</th>
                    <th>Tax Term</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {#each group.lots as lot (lot.lot_id)}
                    {@const isLtcg = lot.tax_term === 'LONG_TERM'}
                    {@const gain = lot.realized_or_unrealized_gain || 0}
                    {@const gainPct = (lot.cost_basis && lot.cost_basis > 0) ? (gain / lot.cost_basis) * 100 : 0}
                    <tr>
                      <td class="mono lot-id">{lot.lot_id.slice(-8)}</td>
                      <td>
                        <span class="mono">{lot.acquisition_date}</span>
                      </td>
                      <td class="mono right-align">{lot.units.toFixed(3)}</td>
                      <td class="mono right-align">{formatINR(lot.cost_nav, false)}</td>
                      <td class="mono right-align">{formatINR(lot.cost_basis, quietMode)}</td>
                      <td class="mono right-align bold">{formatINR(lot.current_value, quietMode)}</td>
                      <td class="right-align">
                        <span class="gain-val mono" class:positive={gain >= 0} class:negative={gain < 0}>
                          {gain >= 0 ? '+' : ''}{formatINR(gain, quietMode)}
                          <span class="sub-pct">({formatPercent(gainPct)})</span>
                        </span>
                      </td>
                      <td>
                        <span class="holding-days mono">{lot.holding_period_days ?? '—'} days</span>
                      </td>
                      <td>
                        <span class="tax-badge" class:ltcg={isLtcg} class:stcg={!isLtcg}>
                          {isLtcg ? 'LTCG (12.5%)' : 'STCG (20%)'}
                        </span>
                        {#if lot.is_grandfathered}
                          <span class="grandfather-badge" title="FMV 2018 grandfathered rule applied">FMV '18</span>
                        {/if}
                      </td>
                      <td class="center-align">
                        <button 
                          type="button" 
                          class="inspect-btn" 
                          onclick={(e) => { e.stopPropagation(); openLotModal(group.holding, lot); }}
                          title="View detailed tax lot breakdown"
                        >
                          Inspect
                        </button>
                      </td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
        </div>
      {/each}
    </div>
  {/if}

  <!-- Lot Inspector Dialog -->
  {#if selectedLot}
    <div 
      class="modal-backdrop" 
      role="presentation"
      onclick={closeLotModal}
    >
      <div 
        class="modal-card" 
        role="dialog"
        aria-modal="true"
        aria-labelledby="lot-modal-title"
        tabindex="-1"
        onclick={(e) => e.stopPropagation()}
        onkeydown={(e) => { if (e.key === 'Escape') closeLotModal(); }}
      >
        <div class="modal-header">
          <div>
            <h3 id="lot-modal-title" class="modal-title">{selectedLot.holding.asset_name}</h3>
            <p class="modal-sub">Lot ID: <code class="mono">{selectedLot.lot.lot_id}</code> • ISIN: <code class="mono">{selectedLot.holding.isin}</code></p>
          </div>
          <button type="button" class="close-btn" onclick={closeLotModal} aria-label="Close dialog">✕</button>
        </div>

        <div class="modal-body">
          <div class="inspector-grid">
            <div class="ins-cell">
              <span class="ins-label">Acquisition Date</span>
              <span class="ins-val mono">{selectedLot.lot.acquisition_date}</span>
            </div>
            <div class="ins-cell">
              <span class="ins-label">Holding Period</span>
              <span class="ins-val mono">{selectedLot.lot.holding_period_days ?? '—'} days</span>
            </div>
            <div class="ins-cell">
              <span class="ins-label">Tax Regime</span>
              <span class="ins-val status">
                {selectedLot.lot.tax_term === 'LONG_TERM' ? 'LTCG (>365d, 12.5% over ₹1.25L)' : 'STCG (≤365d, 20% Flat)'}
              </span>
            </div>
            <div class="ins-cell">
              <span class="ins-label">Grandfathering</span>
              <span class="ins-val">
                {selectedLot.lot.is_grandfathered ? `Applicable (FMV 31-Jan-2018: ₹${selectedLot.lot.fmv_2018})` : 'Not Applicable (Post-2018)'}
              </span>
            </div>
          </div>

          <div class="inspector-financials">
            <div class="fin-row">
              <span class="fin-label">Units Allocated</span>
              <span class="fin-val mono">{selectedLot.lot.units.toFixed(4)}</span>
            </div>
            <div class="fin-row">
              <span class="fin-label">Acquisition Cost NAV</span>
              <span class="fin-val mono">{formatINR(selectedLot.lot.cost_nav, false)}</span>
            </div>
            <div class="fin-row">
              <span class="fin-label">Current Amfi NAV</span>
              <span class="fin-val mono">{formatINR(selectedLot.holding.current_nav, false)}</span>
            </div>
            <div class="fin-row divider"></div>
            <div class="fin-row">
              <span class="fin-label">Total Cost Basis</span>
              <span class="fin-val mono">{formatINR(selectedLot.lot.cost_basis, quietMode)}</span>
            </div>
            <div class="fin-row">
              <span class="fin-label">Current Market Value</span>
              <span class="fin-val mono bold">{formatINR(selectedLot.lot.current_value, quietMode)}</span>
            </div>
            <div class="fin-row highlight">
              <span class="fin-label">Unrealized Gain / (Loss)</span>
              <span class="fin-val mono bold" class:positive={(selectedLot.lot.realized_or_unrealized_gain || 0) >= 0} class:negative={(selectedLot.lot.realized_or_unrealized_gain || 0) < 0}>
                {formatINR(selectedLot.lot.realized_or_unrealized_gain || 0, quietMode)}
              </span>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button type="button" class="primary-btn" onclick={closeLotModal}>Dismiss</button>
        </div>
      </div>
    </div>
  {/if}
</div>

<style>
  .tax-lots-container {
    display: flex;
    flex-direction: column;
    gap: 1.25rem;
  }

  .controls-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 1rem;
    padding: 0.75rem 1rem;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
  }

  .filter-chips {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .chip {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.4rem 0.75rem;
    background: var(--surface-card-hover);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-full);
    color: var(--text-secondary);
    font-size: 0.8rem;
    font-weight: 500;
    cursor: pointer;
    transition: color 0.15s ease, border-color 0.15s ease, background-color 0.15s ease;
  }

  .chip:hover {
    color: var(--text-primary);
    border-color: var(--border-medium);
  }

  .chip.active {
    background: var(--surface-card);
    color: var(--text-primary);
    border-color: var(--accent-cyan);
  }

  .chip-badge {
    padding: 0.1rem 0.4rem;
    border-radius: var(--radius-xs);
    font-size: 0.72rem;
    font-family: var(--font-mono);
  }

  .chip-badge.positive {
    background: var(--color-ok-soft);
    color: var(--color-ok);
  }

  .chip-badge.neutral {
    background: var(--surface-void);
    color: var(--text-secondary);
    border: 1px solid var(--border-hairline);
  }

  .chip-badge.negative {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--border-hairline);
  }

  .search-box {
    display: flex;
    align-items: center;
    position: relative;
    min-width: 240px;
  }

  :global(.search-icon) {
    position: absolute;
    left: 0.75rem;
    color: var(--text-muted);
    pointer-events: none;
  }

  .search-input {
    width: 100%;
    padding: 0.45rem 0.75rem 0.45rem 2.2rem;
    background: var(--surface-void);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    color: var(--text-primary);
    font-size: 0.82rem;
    outline: none;
    transition: border-color var(--transition-fast);
  }

  .search-input:focus {
    border-color: var(--accent-cyan);
  }

  .exemption-banner {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.75rem 1rem;
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    color: var(--text-primary);
    font-size: 0.85rem;
  }

  .banner-icon {
    color: var(--accent-emerald);
    display: flex;
    align-items: center;
  }

  .banner-text .strong {
    color: var(--accent-emerald);
    font-weight: 600;
    margin-right: 0.4rem;
  }

  .calendar-feed-btn {
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    background: var(--color-ok-soft);
    border: 1px solid var(--border-hairline);
    color: var(--color-ok);
    font-size: 0.75rem;
    font-weight: 500;
    padding: 0.35rem 0.65rem;
    border-radius: var(--radius-xs);
    text-decoration: none;
    white-space: nowrap;
    transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
  }

  .calendar-feed-btn:hover {
    background: var(--surface-card-hover);
    color: var(--text-main);
    border-color: var(--accent-emerald);
  }


  /* Empty State */
  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 3rem 1rem;
    background: var(--surface-card);
    border: 1px dashed var(--border-subtle);
    border-radius: var(--radius-lg);
    text-align: center;
  }

  :global(.empty-icon) {
    color: var(--text-muted);
    margin-bottom: 0.75rem;
  }

  .empty-title {
    font-weight: 600;
    color: var(--text-secondary);
    margin-bottom: 0.25rem;
  }

  .empty-desc {
    font-size: 0.8rem;
    color: var(--text-muted);
  }

  /* Schemes List */
  .schemes-list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .scheme-group-card {
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    overflow: hidden;
    transition: border-color 0.15s ease;
  }

  .scheme-group-card:hover {
    border-color: var(--border-subtle);
  }

  .scheme-group-card.expanded {
    border-color: var(--border-highlight);
  }

  .scheme-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 1rem 1.25rem;
    cursor: pointer;
    user-select: none;
    transition: background-color 0.15s ease;
  }

  .scheme-header:hover {
    background: var(--surface-card-hover);
  }

  .scheme-left {
    display: flex;
    align-items: center;
    gap: 0.85rem;
    max-width: 60%;
  }

  .expand-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
    color: var(--text-secondary);
    cursor: pointer;
    flex-shrink: 0;
  }

  .scheme-info {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }

  .name-row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .scheme-name {
    font-weight: 500;
    font-size: 0.95rem;
    color: var(--text-main);
  }

  .isin-pill, .bucket-pill {
    padding: 0.1rem 0.4rem;
    border-radius: var(--radius-xs);
    font-size: 0.7rem;
    font-family: var(--font-mono);
  }

  .isin-pill {
    background: var(--surface-void);
    color: var(--text-muted);
    border: 1px solid var(--border-hairline);
  }

  .bucket-pill {
    background: var(--accent-cyan-bg);
    color: var(--accent-cyan);
    border: 1px solid var(--border-hairline);
  }

  .sub-row {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .bullet {
    opacity: 0.5;
  }

  .scheme-right {
    display: flex;
    align-items: center;
    gap: 1.5rem;
  }

  .metric-pair {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
  }

  .metric-label {
    font-size: 0.72rem;
    color: var(--text-muted);
    font-weight: 500;
  }

  .metric-val {
    font-size: 0.88rem;
    color: var(--text-secondary);
  }

  .gain-badge {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.2rem 0.5rem;
    border-radius: var(--radius-xs);
    font-size: 0.85rem;
  }

  .gain-badge.positive {
    background: var(--color-ok-soft);
    color: var(--color-ok);
  }

  .gain-badge.negative {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--border-hairline);
  }

  .gain-badge .pct {
    font-size: 0.75rem;
    opacity: 0.85;
  }

  /* Lots Table */
  .lots-table-wrapper {
    overflow-x: auto;
    border-top: 1px solid var(--border-hairline);
    background: var(--surface-void);
  }

  .lots-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.8rem;
  }

  .lots-table th {
    padding: 0.6rem 0.85rem;
    text-align: left;
    font-size: 0.75rem;
    font-weight: 500;
    font-family: var(--font-sans);
    color: var(--text-muted);
    background: var(--surface-void);
    border-bottom: 1px solid var(--border-hairline);
  }

  .lots-table td {
    padding: 0.65rem 0.85rem;
    border-bottom: 1px solid var(--border-hairline);
    color: var(--text-secondary);
  }

  .lots-table tr:hover td {
    background: var(--surface-card-hover);
  }

  .lot-id {
    color: var(--accent-cyan);
  }

  .right-align {
    text-align: right;
  }

  .center-align {
    text-align: center;
  }

  .gain-val.positive {
    color: var(--accent-emerald);
  }

  .gain-val.negative {
    color: var(--accent-rose-bright) !important;
    font-weight: 600;
  }

  .sub-pct {
    font-size: 0.7rem;
    opacity: 0.75;
    margin-left: 0.2rem;
  }

  .holding-days {
    color: var(--text-muted);
  }

  .tax-badge {
    display: inline-block;
    padding: 0.15rem 0.45rem;
    border-radius: var(--radius-xs);
    font-size: 0.7rem;
    font-weight: 500;
  }

  .tax-badge.ltcg {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--border-hairline);
  }

  .tax-badge.stcg {
    background: var(--color-accent-soft);
    color: var(--color-accent);
    border: 1px solid var(--border-hairline);
  }

  .grandfather-badge {
    margin-left: 0.3rem;
    padding: 0.1rem 0.3rem;
    border-radius: var(--radius-xs);
    font-size: 0.65rem;
    background: var(--accent-violet-bg);
    color: var(--accent-violet-bright);
    border: 1px solid var(--border-hairline);
  }

  .inspect-btn {
    padding: 0.25rem 0.55rem;
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
    color: var(--text-secondary);
    font-size: 0.72rem;
    cursor: pointer;
    transition: color 0.15s ease, border-color 0.15s ease, background-color 0.15s ease;
  }

  .inspect-btn:hover {
    color: var(--accent-cyan);
    border-color: var(--accent-cyan);
    background: var(--surface-card);
  }

  /* Modal Dialog */
  .modal-backdrop {
    position: fixed;
    inset: 0;
    background: color-mix(in oklab, var(--color-paper-3) 85%, transparent);
    backdrop-filter: blur(8px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    padding: 1rem;
  }

  .modal-card {
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    width: 100%;
    max-width: 540px;
    overflow: hidden;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 1.25rem 1.5rem;
    border-bottom: 1px solid var(--border-hairline);
  }

  .modal-title {
    font-size: 1.1rem;
    font-weight: 600;
    color: var(--text-main);
    margin: 0 0 0.25rem 0;
  }

  .modal-sub {
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .close-btn {
    background: transparent;
    border: none;
    color: var(--text-muted);
    font-size: 1.1rem;
    cursor: pointer;
    padding: 0.25rem;
  }

  .close-btn:hover {
    color: var(--text-main);
  }

  .modal-body {
    padding: 1.25rem 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 1.25rem;
  }

  .inspector-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0.75rem;
  }

  .ins-cell {
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
    padding: 0.65rem 0.85rem;
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
  }

  .ins-label {
    font-size: 0.72rem;
    color: var(--text-muted);
    font-weight: 500;
  }

  .ins-val {
    font-size: 0.85rem;
    color: var(--text-main);
  }

  .ins-val.status {
    color: var(--accent-cyan);
    font-size: 0.78rem;
  }

  .inspector-financials {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    padding: 0.85rem 1rem;
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
  }

  .fin-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.82rem;
  }

  .fin-row.divider {
    height: 1px;
    background: var(--border-hairline);
    margin: 0.3rem 0;
  }

  .fin-row.highlight {
    padding-top: 0.25rem;
    font-size: 0.95rem;
  }

  .fin-label {
    color: var(--text-secondary);
  }

  .fin-val {
    color: var(--text-main);
  }

  .fin-val.positive {
    color: var(--accent-emerald);
  }

  .fin-val.negative {
    color: var(--accent-rose-bright) !important;
    font-weight: 600;
  }

  .modal-footer {
    display: flex;
    justify-content: flex-end;
    padding: 1rem 1.5rem;
    border-top: 1px solid var(--border-hairline);
    background: var(--surface-void);
  }

  .primary-btn {
    padding: 0.5rem 1.25rem;
    background: var(--accent-cyan);
    color: var(--surface-void);
    border: none;
    border-radius: var(--radius-xs);
    font-weight: 500;
    font-size: 0.85rem;
    cursor: pointer;
    transition: opacity 0.15s ease;
  }

  .primary-btn:hover {
    opacity: 0.9;
  }

  @media (max-width: 768px) {
    .scheme-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 0.75rem;
    }
    .scheme-left {
      max-width: 100%;
    }
    .scheme-right {
      width: 100%;
      justify-content: space-between;
      gap: 0.75rem;
    }
    .inspector-grid {
      grid-template-columns: 1fr;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .chip, .calendar-feed-btn, .scheme-group-card, .inspect-btn, .primary-btn {
      transition: none !important;
    }
  }
</style>
