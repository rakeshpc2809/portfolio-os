<script lang="ts">
  import { getAuthToken, type HoldingDto, type LotDto } from '../api';
  import { formatCompactINR, formatINR, formatPercent } from '../formatters';
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
    Search,
    CalendarClock
  } from '@lucide/svelte';

  interface Props {
    holdings?: HoldingDto[];
    quietMode?: boolean;
    isOffline?: boolean;
    ltcgRemaining?: number;
  }

  let { holdings = [], quietMode = false, isOffline = false, ltcgRemaining = 0 }: Props = $props();

  type FilterType = 'ALL' | 'LTCG' | 'STCG' | 'MATURING' | 'HARVEST';
  let activeFilter = $state<FilterType>('ALL');
  let searchQuery = $state('');
  let expandedSchemes = $state<Record<string, boolean>>({});
  let selectedLot = $state<{ holding: HoldingDto; lot: LotDto } | null>(null);

  async function downloadHarvestCalendar() {
    if (isOffline) return;
    try {
      const token = getAuthToken();
      const headers: Record<string, string> = {};
      if (token) {
        headers["X-Api-Auth-Token"] = token;
      }
      const response = await fetch("/api/v1/tax/harvest-calendar.ics", {
        headers,
      });
      if (!response.ok) {
        throw new Error(`Failed to download calendar: HTTP ${response.status}`);
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "portfolio_harvest_calendar.ics";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err: any) {
      console.error("Failed to download calendar .ics:", err);
      alert(err?.message || "Failed to download calendar");
    }
  }

  function resolveRegime(holding?: HoldingDto, lot?: LotDto): string {
    const cls = (lot?.tax_classification || '').toUpperCase();
    if (cls.includes('50AA') || cls.includes('DEBT')) return 'SEC_50AA_DEBT';
    if (cls.includes('SGB')) return 'SGB';
    if (cls.includes('GOLD') || cls.includes('SILVER') || cls.includes('INTERNATIONAL') || cls.includes('112_NON')) return 'GOLD_SILVER';
    if (cls.includes('112A') || cls.includes('EQUITY')) return 'SEC_112A_EQUITY';

    const bucket = (holding?.bucket || '').toUpperCase();
    if (bucket.includes('DEBT') || bucket.includes('LIQUID') || bucket.includes('ARBITRAGE')) return 'SEC_50AA_DEBT';
    if (bucket.includes('GOLD') || bucket.includes('SILVER') || bucket.includes('INTL')) return 'GOLD_SILVER';
    return 'SEC_112A_EQUITY';
  }

  function getTaxBadge(holding?: HoldingDto, lot?: LotDto): { label: string; isLtcg: boolean; is50AA: boolean } {
    if (!lot) return { label: '—', isLtcg: false, is50AA: false };
    const isLtcg = lot.tax_term === 'LONG_TERM';
    const regime = resolveRegime(holding, lot);

    if (regime === 'SEC_50AA_DEBT') {
      return { label: 'Sec 50AA · Slab (Always ST)', isLtcg: false, is50AA: true };
    }
    if (regime === 'SGB') {
      return { label: isLtcg ? 'LTCG · 13.0% (Matures 0%)' : 'STCG · Slab', isLtcg, is50AA: false };
    }
    if (regime === 'GOLD_SILVER') {
      return { label: isLtcg ? 'LTCG · 13.0% (Sec 112)' : 'STCG · Slab (24m cliff)', isLtcg, is50AA: false };
    }
    return { label: isLtcg ? 'LTCG · 13.0% (>₹1.25L)' : 'STCG · 20.8%', isLtcg, is50AA: false };
  }

  function getTaxRegimeDescription(holding?: HoldingDto, lot?: LotDto): string {
    if (!lot) return '—';
    const isLtcg = lot.tax_term === 'LONG_TERM';
    const regime = resolveRegime(holding, lot);

    if (regime === 'SEC_50AA_DEBT') {
      return 'Sec 50AA · Slab (Always Short-Term, no LTCG maturation)';
    }
    if (regime === 'SGB') {
      return isLtcg
        ? 'SGB LTCG · 13.0% on secondary exit (0% if held to RBI maturity)'
        : 'SGB STCG · Slab Rate';
    }
    if (regime === 'GOLD_SILVER') {
      return isLtcg
        ? 'Sec 112 LTCG · 13.0% (>24m holding, no ₹1.25L exemption)'
        : 'STCG · Slab Rate (24m cliff)';
    }
    return isLtcg
      ? 'Sec 112A LTCG · 13.0% (>12m, exempt up to ₹1.25L/FY)'
      : 'Sec 111A STCG · 20.8% Flat (≤12m)';
  }

  interface EnrichedLot extends LotDto {
    holding: HoldingDto;
    regime: string;
  }

  const allLots = $derived.by<EnrichedLot[]>(() => {
    const list: EnrichedLot[] = [];
    for (const h of holdings) {
      if (h.lots && h.lots.length > 0) {
        for (const lot of h.lots) {
          list.push({ ...lot, holding: h, regime: resolveRegime(h, lot) });
        }
      }
    }
    return list;
  });

  function isMaturingSoon(l: EnrichedLot): boolean {
    return (
      l.tax_term === 'SHORT_TERM' &&
      l.regime !== 'SEC_50AA_DEBT' &&
      (l.days_to_ltcg ?? 0) > 0 &&
      (l.days_to_ltcg ?? 0) <= 30
    );
  }

  const filteredLots = $derived.by<EnrichedLot[]>(() => {
    let list = allLots;
    const q = searchQuery.toLowerCase().trim();
    if (q) {
      list = list.filter(
        (l) =>
          l.holding.asset_name.toLowerCase().includes(q) ||
          l.holding.isin.toLowerCase().includes(q) ||
          (l.lot_id && l.lot_id.toLowerCase().includes(q))
      );
    }

    if (activeFilter === 'LTCG') {
      list = list.filter((l) => l.tax_term === 'LONG_TERM');
    } else if (activeFilter === 'STCG') {
      list = list.filter((l) => l.tax_term === 'SHORT_TERM');
    } else if (activeFilter === 'MATURING') {
      list = list.filter(isMaturingSoon);
    } else if (activeFilter === 'HARVEST') {
      list = list.filter((l) => l.is_harvest_candidate || (l.realized_or_unrealized_gain || 0) < 0);
    }

    return list;
  });

  interface GroupedHolding {
    holding: HoldingDto;
    lots: EnrichedLot[];
    totalCost: number;
    totalValue: number;
    totalGain: number;
    gainPct: number;
    ltcgPct: number;
    stcgPct: number;
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
          ltcgPct: 0,
          stcgPct: 0
        });
      }
      const g = map.get(isin)!;
      g.lots.push(lot);
      g.totalCost += lot.cost_basis || 0;
      g.totalValue += lot.current_value || 0;
      g.totalGain += lot.realized_or_unrealized_gain || 0;
    }

    for (const g of map.values()) {
      g.gainPct = g.totalCost > 0 ? (g.totalGain / g.totalCost) * 100 : 0;
      const totalUnits = g.lots.reduce((s, l) => s + (l.units || 0), 0);
      const ltcgUnits = g.lots
        .filter((l) => l.tax_term === 'LONG_TERM')
        .reduce((s, l) => s + (l.units || 0), 0);
      g.ltcgPct = totalUnits > 0 ? (ltcgUnits / totalUnits) * 100 : 0;
      g.stcgPct = Math.max(0, 100 - g.ltcgPct);
    }

    return Array.from(map.values()).sort((a, b) => b.totalValue - a.totalValue);
  });

  const metrics = $derived.by(() => {
    let ltcgCount = 0;
    let ltcgGain = 0;
    let stcgCount = 0;
    let stcgGain = 0;
    let maturingCount = 0;
    let harvestCount = 0;
    let harvestLoss = 0;

    for (const l of allLots) {
      const g = l.realized_or_unrealized_gain || 0;
      if (l.tax_term === "LONG_TERM") {
        ltcgCount++;
        ltcgGain += g;
      } else {
        stcgCount++;
        stcgGain += g;
      }
      if (isMaturingSoon(l)) {
        maturingCount++;
      }
      if (l.is_harvest_candidate || g < 0) {
        harvestCount++;
        harvestLoss += Math.abs(g);
      }
    }

    return { ltcgCount, ltcgGain, stcgCount, stcgGain, maturingCount, harvestCount, harvestLoss };
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
        <span
          class="chip-badge tnum"
          class:positive={metrics.ltcgGain >= 0}
          class:negative={metrics.ltcgGain < 0}
          data-sensitive="currency"
        >
          {formatINR(metrics.ltcgGain, quietMode)}
        </span>
      </button>

      <button
        type="button"
        class="chip stcg-chip"
        class:active={activeFilter === 'STCG'}
        onclick={() => setFilter('STCG')}
      >
        <Clock size={14} />
        <span>STCG Locked ({metrics.stcgCount})</span>
        <span
          class="chip-badge tnum"
          class:positive={metrics.stcgGain > 0}
          class:neutral={metrics.stcgGain === 0}
          class:negative={metrics.stcgGain < 0}
          data-sensitive="currency"
        >
          {formatINR(metrics.stcgGain, quietMode)}
        </span>
      </button>

      <button
        type="button"
        class="chip maturing-chip"
        class:active={activeFilter === 'MATURING'}
        onclick={() => setFilter('MATURING')}
      >
        <CalendarClock size={14} />
        <span>Maturing ≤30d ({metrics.maturingCount})</span>
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
          <span class="chip-badge negative tnum" data-sensitive="currency">
            {formatINR(-metrics.harvestLoss, quietMode)}
          </span>
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
        <span>
          You have
          <strong class="tnum" data-sensitive="currency">{formatINR(ltcgRemaining, quietMode)}</strong>
          of tax-free LTCG headroom remaining this financial year (exempt up to ₹1.25 Lakh).
        </span>
      </div>
      <button
        type="button"
        onclick={downloadHarvestCalendar}
        disabled={isOffline}
        class="calendar-feed-btn"
        title={isOffline ? 'Disabled while viewing offline cache' : 'Download iCalendar file with lot maturation alarms'}
      >
        <Calendar size={13} />
        <span>{isOffline ? 'Offline (.ics disabled)' : 'Add Maturation Reminders (.ics)'}</span>
      </button>
    </div>
  {/if}

  <!-- Scheme Groups Accordion -->
  {#if groupedSchemes.length === 0}
    <div class="empty-state">
      <Filter size={32} class="empty-icon" />
      <p class="empty-title">No matching tax lots found</p>
      <p class="empty-desc">Try clearing your search query or selecting 'All Lots' to view all open holdings.</p>
    </div>
  {:else}
    <div class="schemes-list">
      {#each groupedSchemes as group (group.holding.isin)}
        {@const isExpanded = expandedSchemes[group.holding.isin] ?? false}
        {@const lockedUnits = Number(group.holding.locked_units ?? 0)}
        {@const exitFreeVal = Number(group.holding.exit_load_free_value ?? group.totalValue)}
        <div class="scheme-group-card" class:expanded={isExpanded}>
          <!-- Header Bar -->
          <div
            class="scheme-header"
            role="button"
            tabindex="0"
            onclick={() => toggleScheme(group.holding.isin)}
            onkeydown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                toggleScheme(group.holding.isin);
              }
            }}
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
                  <span class="isin-pill mono">{group.holding.isin}</span>
                  <span class="bucket-pill">{group.holding.bucket}</span>
                  {#if group.holding.expense_ratio !== undefined}
                    <span class="ter-pill tnum">TER {group.holding.expense_ratio.toFixed(2)}%</span>
                  {/if}
                </div>
                <div class="sub-row tnum">
                  <span>{group.lots.length} {group.lots.length === 1 ? 'lot' : 'lots'}</span>
                  <span class="bullet">·</span>
                  <span>NAV: {formatINR(group.holding.current_nav, false)}</span>
                  <span class="bullet">·</span>
                  {#if lockedUnits > 0.001}
                    <span class="exit-warn">
                      ⚠ {lockedUnits.toFixed(1)} units locked (Free:
                      <span data-sensitive="currency">{formatCompactINR(exitFreeVal, quietMode)}</span>)
                    </span>
                  {:else}
                    <span class="exit-ok">
                      Exit-load free: <span data-sensitive="currency">{formatCompactINR(exitFreeVal, quietMode)}</span>
                    </span>
                  {/if}
                </div>
              </div>
            </div>

            <!-- 120x6 LTCG/STCG Composition Bar -->
            <div class="composition-wrap" title="LTCG {group.ltcgPct.toFixed(0)}% / STCG {group.stcgPct.toFixed(0)}% by units">
              <div class="comp-labels tnum">
                <span class="comp-ltcg">LTCG {group.ltcgPct.toFixed(0)}%</span>
                <span class="comp-stcg">STCG {group.stcgPct.toFixed(0)}%</span>
              </div>
              <div class="comp-bar">
                <div class="comp-fill-ltcg" style="width: {group.ltcgPct}%;"></div>
                <div class="comp-fill-stcg" style="width: {group.stcgPct}%;"></div>
              </div>
            </div>

            <div class="scheme-right tnum">
              <div class="metric-pair">
                <span class="metric-label">Invested</span>
                <span class="metric-val mono" data-sensitive="currency">{formatINR(group.totalCost, quietMode)}</span>
              </div>
              <div class="metric-pair">
                <span class="metric-label">Current Value</span>
                <span class="metric-val mono bold" data-sensitive="currency">{formatINR(group.totalValue, quietMode)}</span>
              </div>
              <div class="metric-pair gain-pair">
                <span class="metric-label">Total Gain</span>
                <div
                  class="gain-badge"
                  class:positive={group.totalGain >= 0}
                  class:negative={group.totalGain < 0}
                >
                  {#if group.totalGain >= 0}
                    <ArrowUpRight size={14} />
                  {:else}
                    <ArrowDownRight size={14} />
                  {/if}
                  <span class="mono bold" data-sensitive="currency">{formatINR(group.totalGain, quietMode)}</span>
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
                    <th class="right-align">Units</th>
                    <th class="right-align">Cost NAV</th>
                    <th class="right-align">Cost Basis</th>
                    <th class="right-align">Current Value</th>
                    <th class="right-align">Unrealized Gain</th>
                    <th>Holding</th>
                    <th>Tax Regime</th>
                    <th class="right-align">Est. Drag</th>
                    <th class="center-align">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {#each group.lots as lot (lot.lot_id)}
                    {@const gain = lot.realized_or_unrealized_gain || 0}
                    {@const gainPct = lot.cost_basis && lot.cost_basis > 0 ? (gain / lot.cost_basis) * 100 : 0}
                    {@const taxInfo = getTaxBadge(group.holding, lot)}
                    {@const showMaturation = !taxInfo.is50AA && !taxInfo.isLtcg && (lot.days_to_ltcg ?? 0) > 0}
                    <tr class:harvest-row={Boolean(lot.is_harvest_candidate)}>
                      <td class="mono lot-id">{lot.lot_id.slice(-8)}</td>
                      <td class="mono">{lot.acquisition_date}</td>
                      <td class="mono right-align">{lot.units.toFixed(3)}</td>
                      <td class="mono right-align">{formatINR(lot.cost_nav, false)}</td>
                      <td class="mono right-align" data-sensitive="currency">{formatINR(lot.cost_basis, quietMode)}</td>
                      <td class="mono right-align bold" data-sensitive="currency">{formatINR(lot.current_value, quietMode)}</td>
                      <td class="right-align">
                        <span class="gain-val mono" class:positive={gain >= 0} class:negative={gain < 0} data-sensitive="currency">
                          {!quietMode && gain > 0 ? '+' : ''}{formatINR(gain, quietMode)}
                          <span class="sub-pct">({formatPercent(gainPct)})</span>
                        </span>
                      </td>
                      <td>
                        <div class="holding-cell tnum">
                          <span>{lot.holding_period_days ?? '—'}d</span>
                          {#if showMaturation}
                            <span class="maturing-chip-inline">in {lot.days_to_ltcg}d</span>
                          {/if}
                        </div>
                      </td>
                      <td>
                        <div class="regime-cell">
                          <span class="tax-badge" class:ltcg={taxInfo.isLtcg} class:stcg={!taxInfo.isLtcg}>
                            {taxInfo.label}
                          </span>
                          {#if lot.is_harvest_candidate}
                            <span class="harvest-badge">Loss Harvest</span>
                          {/if}
                          {#if lot.is_grandfathered}
                            <span class="grandfather-badge" title="FMV 2018 grandfathered rule applied">FMV '18</span>
                          {/if}
                        </div>
                      </td>
                      <td class="mono right-align drag-cell" data-sensitive="currency">
                        {lot.estimated_tax_drag && lot.estimated_tax_drag > 0 ? formatINR(lot.estimated_tax_drag, quietMode) : '₹ 0'}
                      </td>
                      <td class="center-align">
                        <button
                          type="button"
                          class="inspect-btn"
                          onclick={(e) => {
                            e.stopPropagation();
                            openLotModal(group.holding, lot);
                          }}
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

  <!-- Lot Inspector Dialog (Bottom Sheet on <768px) -->
  {#if selectedLot}
    <div
      class="modal-backdrop"
      role="presentation"
      onclick={closeLotModal}
      onkeydown={(e) => { if (e.key === 'Escape') closeLotModal(); }}
    >
      <div
        class="modal-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="lot-modal-title"
        tabindex="-1"
        onclick={(e) => e.stopPropagation()}
        onkeydown={(e) => {
          if (e.key === "Escape") closeLotModal();
        }}
      >
        <div class="modal-header">
          <div>
            <h3 id="lot-modal-title" class="modal-title">{selectedLot.holding.asset_name}</h3>
            <p class="modal-sub">
              Lot ID: <code class="mono">{selectedLot.lot.lot_id}</code> · ISIN: <code class="mono">{selectedLot.holding.isin}</code>
            </p>
          </div>
          <button
            type="button"
            class="close-btn"
            onclick={closeLotModal}
            aria-label="Close dialog">✕</button
          >
        </div>

        <div class="modal-body">
          <div class="inspector-grid">
            <div class="ins-cell">
              <span class="ins-label">Acquisition Date</span>
              <span class="ins-val mono"
                >{selectedLot.lot.acquisition_date}</span
              >
            </div>
            <div class="ins-cell">
              <span class="ins-label">Holding Period</span>
              <span class="ins-val mono">
                {selectedLot.lot.holding_period_days ?? '—'} days
                {#if resolveRegime(selectedLot.holding, selectedLot.lot) !== 'SEC_50AA_DEBT' && selectedLot.lot.tax_term === 'SHORT_TERM' && (selectedLot.lot.days_to_ltcg ?? 0) > 0}
                  (LTCG in {selectedLot.lot.days_to_ltcg}d)
                {/if}
              </span>
            </div>
            <div class="ins-cell">
              <span class="ins-label">Tax Regime</span>
              <span class="ins-val status">
                {getTaxRegimeDescription(selectedLot.holding, selectedLot.lot)}
              </span>
            </div>
            <div class="ins-cell">
              <span class="ins-label">Grandfathering</span>
              <span class="ins-val">
                {selectedLot.lot.is_grandfathered
                  ? `Applicable (FMV 31-Jan-2018: ₹${selectedLot.lot.fmv_2018})`
                  : "Not Applicable (Post-2018)"}
              </span>
            </div>
          </div>

          <div class="inspector-financials tnum">
            <div class="fin-row">
              <span class="fin-label">Units Allocated</span>
              <span class="fin-val mono"
                >{selectedLot.lot.units.toFixed(4)}</span
              >
            </div>
            <div class="fin-row">
              <span class="fin-label">Acquisition Cost NAV</span>
              <span class="fin-val mono"
                >{formatINR(selectedLot.lot.cost_nav, false)}</span
              >
            </div>
            <div class="fin-row">
              <span class="fin-label">Current AMFI NAV</span>
              <span class="fin-val mono"
                >{formatINR(selectedLot.holding.current_nav, false)}</span
              >
            </div>
            <div class="fin-row divider"></div>
            <div class="fin-row">
              <span class="fin-label">Total Cost Basis</span>
              <span class="fin-val mono" data-sensitive="currency">{formatINR(selectedLot.lot.cost_basis, quietMode)}</span>
            </div>
            <div class="fin-row">
              <span class="fin-label">Current Market Value</span>
              <span class="fin-val mono bold" data-sensitive="currency">{formatINR(selectedLot.lot.current_value, quietMode)}</span>
            </div>
            <div class="fin-row highlight">
              <span class="fin-label">Unrealized Gain / (Loss)</span>
              <span
                class="fin-val mono bold"
                class:positive={(selectedLot.lot.realized_or_unrealized_gain || 0) >= 0}
                class:negative={(selectedLot.lot.realized_or_unrealized_gain || 0) < 0}
                data-sensitive="currency"
              >
                {formatINR(selectedLot.lot.realized_or_unrealized_gain || 0, quietMode)}
              </span>
            </div>
            <div class="fin-row">
              <span class="fin-label">Estimated Tax Drag</span>
              <span class="fin-val mono" data-sensitive="currency">
                {formatINR(selectedLot.lot.estimated_tax_drag || 0, quietMode)}
              </span>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button type="button" class="primary-btn" onclick={closeLotModal}
            >Dismiss</button
          >
        </div>
      </div>
    </div>
  {/if}
</div>

<style>
  .tax-lots-container {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .controls-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
    padding: 12px 14px;
    background: var(--bg-surface);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-md);
  }

  .filter-chips {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 11px;
    min-height: 36px;
    background: var(--bg-elevated);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    color: var(--ink-secondary);
    font-size: 0.78rem;
    font-weight: 500;
    cursor: pointer;
  }

  .chip.active {
    background: var(--state-action-soft);
    border-color: var(--state-action);
    color: var(--state-action);
  }

  .chip-badge {
    font-size: 0.72rem;
    padding: 1px 6px;
    border-radius: 4px;
    background: var(--bg-canvas);
  }

  .chip-badge.positive { color: var(--state-ok); }
  .chip-badge.negative { color: var(--state-danger); }
  .chip-badge.neutral { color: var(--ink-muted); }

  .search-box {
    position: relative;
    display: flex;
    align-items: center;
  }

  :global(.search-icon) {
    position: absolute;
    left: 10px;
    color: var(--ink-muted);
    pointer-events: none;
  }

  .search-input {
    background: var(--bg-canvas);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    padding: 7px 10px 7px 30px;
    color: var(--ink-primary);
    font-size: 0.8rem;
    min-width: 210px;
    outline: none;
  }

  .search-input:focus {
    border-color: var(--state-action);
  }

  .exemption-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 16px;
    background: var(--state-ok-soft);
    border: 1px solid color-mix(in oklch, var(--state-ok) 35%, transparent);
    border-radius: var(--radius-md);
    flex-wrap: wrap;
  }

  .banner-icon { color: var(--state-ok); }

  .banner-text {
    flex: 1;
    display: flex;
    gap: 6px;
    font-size: 0.8rem;
    color: var(--ink-primary);
    flex-wrap: wrap;
  }

  .banner-text .strong { color: var(--state-ok); font-weight: 600; }

  .calendar-feed-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 7px 12px;
    background: var(--bg-canvas);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    color: var(--ink-primary);
    font-size: 0.76rem;
    font-weight: 500;
    cursor: pointer;
    min-height: 36px;
  }

  .calendar-feed-btn:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }

  .schemes-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .scheme-group-card {
    background: var(--bg-surface);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-md);
    overflow: hidden;
  }

  .scheme-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding: 13px 16px;
    cursor: pointer;
    flex-wrap: wrap;
  }

  .scheme-header:hover {
    background: var(--bg-elevated);
  }

  .scheme-left {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 1;
    min-width: 240px;
  }

  .expand-btn {
    background: transparent;
    border: none;
    color: var(--ink-muted);
    display: grid;
    place-items: center;
  }

  .scheme-info {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .name-row {
    display: flex;
    align-items: center;
    gap: 7px;
    flex-wrap: wrap;
  }

  .scheme-name {
    font-size: 0.88rem;
    font-weight: 600;
    color: var(--ink-primary);
  }

  .isin-pill,
  .bucket-pill,
  .ter-pill {
    font-size: 0.68rem;
    padding: 2px 6px;
    border-radius: 4px;
    background: var(--bg-canvas);
    border: 1px solid var(--border-hair);
    color: var(--ink-muted);
  }

  .sub-row {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.74rem;
    color: var(--ink-muted);
    flex-wrap: wrap;
  }

  .exit-ok { color: var(--state-ok); }
  .exit-warn { color: var(--state-warn); }

  .composition-wrap {
    display: flex;
    flex-direction: column;
    gap: 4px;
    width: 120px;
  }

  .comp-labels {
    display: flex;
    justify-content: space-between;
    font-size: 0.66rem;
  }

  .comp-ltcg { color: var(--state-ok); }
  .comp-stcg { color: var(--state-warn); }

  .comp-bar {
    width: 120px;
    height: 6px;
    background: var(--bg-elevated);
    border-radius: 999px;
    overflow: hidden;
    display: flex;
  }

  .comp-fill-ltcg { background: var(--state-ok); height: 100%; }
  .comp-fill-stcg { background: var(--state-warn); height: 100%; }

  .scheme-right {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;
  }

  .metric-pair {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
  }

  .metric-label {
    font-size: 0.68rem;
    color: var(--ink-muted);
  }

  .metric-val {
    font-size: 0.84rem;
    color: var(--ink-primary);
  }

  .bold { font-weight: 600; }

  .gain-badge {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 0.82rem;
  }

  .gain-badge.positive,
  .positive { color: var(--state-ok); }
  .gain-badge.negative,
  .negative { color: var(--state-danger); }

  .lots-table-wrapper {
    border-top: 1px solid var(--border-hair);
    overflow-x: auto;
    background: var(--bg-canvas);
  }

  .lots-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.78rem;
  }

  .lots-table th {
    text-align: left;
    padding: 9px 12px;
    font-size: 0.7rem;
    font-weight: 600;
    color: var(--ink-muted);
    border-bottom: 1px solid var(--border-hair);
    white-space: nowrap;
  }

  .lots-table td {
    padding: 9px 12px;
    border-bottom: 1px solid var(--border-hair);
    color: var(--ink-secondary);
    white-space: nowrap;
  }

  .lots-table tr.harvest-row td:first-child {
    border-left: 2px solid var(--state-ok);
  }

  .right-align { text-align: right !important; }
  .center-align { text-align: center !important; }

  .holding-cell,
  .regime-cell {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .maturing-chip-inline {
    font-size: 0.68rem;
    padding: 1px 6px;
    border-radius: 4px;
    background: var(--state-warn-soft);
    color: var(--state-warn);
  }

  .tax-badge {
    font-size: 0.7rem;
    font-weight: 600;
    padding: 2px 7px;
    border-radius: 4px;
  }

  .tax-badge.ltcg { background: var(--state-ok-soft); color: var(--state-ok); }
  .tax-badge.stcg { background: var(--state-warn-soft); color: var(--state-warn); }

  .harvest-badge {
    font-size: 0.68rem;
    font-weight: 600;
    padding: 2px 6px;
    border-radius: 4px;
    background: var(--state-ok-soft);
    color: var(--state-ok);
  }

  .grandfather-badge {
    font-size: 0.68rem;
    padding: 2px 6px;
    border-radius: 4px;
    background: var(--bg-elevated);
    color: var(--ink-muted);
  }

  .inspect-btn {
    padding: 4px 10px;
    background: var(--bg-elevated);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    color: var(--ink-primary);
    font-size: 0.72rem;
    cursor: pointer;
    min-height: 28px;
  }

  .empty-state {
    padding: 40px 20px;
    text-align: center;
    background: var(--bg-surface);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-md);
    color: var(--ink-muted);
  }

  /* Lot Inspector Modal */
  .modal-backdrop {
    position: fixed;
    inset: 0;
    z-index: var(--z-modal);
    background: rgba(0, 0, 0, 0.72);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 16px;
  }

  .modal-card {
    width: 100%;
    max-width: 560px;
    background: var(--bg-modal);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-lg);
    overflow: hidden;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 16px 18px;
    border-bottom: 1px solid var(--border-hair);
  }

  .modal-title { font-size: 0.96rem; font-weight: 600; color: var(--ink-primary); }
  .modal-sub { font-size: 0.74rem; color: var(--ink-muted); margin-top: 4px; }

  .close-btn {
    background: var(--bg-elevated);
    border: 1px solid var(--border-hair);
    color: var(--ink-secondary);
    border-radius: var(--radius-sm);
    width: 32px;
    height: 32px;
    cursor: pointer;
  }

  .modal-body {
    padding: 16px 18px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .inspector-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }

  .ins-cell {
    background: var(--bg-canvas);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    padding: 9px 11px;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .ins-label { font-size: 0.68rem; color: var(--ink-muted); }
  .ins-val { font-size: 0.78rem; color: var(--ink-primary); }

  .inspector-financials {
    background: var(--bg-canvas);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 7px;
    font-size: 0.8rem;
  }

  .fin-row { display: flex; justify-content: space-between; }
  .fin-label { color: var(--ink-muted); }
  .fin-val { color: var(--ink-primary); }
  .fin-row.divider { border-top: 1px solid var(--border-hair); margin: 3px 0; }

  .modal-footer {
    padding: 12px 18px;
    border-top: 1px solid var(--border-hair);
    display: flex;
    justify-content: flex-end;
  }

  .primary-btn {
    padding: 7px 16px;
    background: var(--state-action-soft);
    border: 1px solid color-mix(in oklch, var(--state-action) 40%, transparent);
    border-radius: var(--radius-sm);
    color: var(--state-action);
    font-weight: 600;
    cursor: pointer;
  }

  @media (max-width: 768px) {
    .modal-backdrop {
      align-items: flex-end;
      padding: 0;
    }
    .modal-card {
      border-bottom-left-radius: 0;
      border-bottom-right-radius: 0;
      max-height: 88vh;
      overflow-y: auto;
    }
    .inspector-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
