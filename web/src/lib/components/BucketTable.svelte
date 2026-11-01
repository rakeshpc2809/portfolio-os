<script lang="ts">
  import { onMount } from 'svelte';
  import type { BucketAllocationDto } from '../api';
  import { fetchFactorBenchmarks, type FactorBenchmarkMetric } from '../api';
  import { formatINR } from '../formatters';
  import { Scale, ChevronRight, ShieldCheck } from '@lucide/svelte';

  interface Props {
    buckets?: BucketAllocationDto[];
    isQuiet?: boolean;
    quietMode?: boolean;
    onInspectFund?: (isin: string) => void;
  }

  let {
    buckets = [],
    isQuiet = false,
    quietMode = false,
    onInspectFund = (isin: string) => {}
  }: Props = $props();

  const effectiveQuiet = $derived(isQuiet || quietMode);

  // Reactive map of expanded buckets for reliable Svelte 5 reactivity
  let expandedBuckets = $state<Record<string, boolean>>({
    EQUITY_CORE: true,
    EQUITY_SATELLITE: false,
    GOLD_SILVER: false,
    LIQUID_BUFFER: false,
    LEGACY_HOLDINGS: false
  });

  let expandedFundIsin = $state<string | null>(null);
  let factorMetrics = $state<Record<string, FactorBenchmarkMetric>>({});

  onMount(async () => {
    try {
      const res = await fetchFactorBenchmarks();
      if (res && res.factors) {
        const map: Record<string, FactorBenchmarkMetric> = {};
        for (const f of res.factors) {
          const item: FactorBenchmarkMetric = {
            isin: f.isin || '',
            fundName: f.fundName || (f as any).fund_name || '',
            benchmarkId: f.benchmarkId || (f as any).benchmark_id || 'NIFTY_500_TRI',
            benchmarkName: f.benchmarkName || (f as any).benchmark_name || 'Standard Factor Benchmark',
            beta: Number(f.beta ?? 1.0),
            trackingErrorPct: Number(f.trackingErrorPct ?? (f as any).tracking_error_pct ?? 0),
            alphaPct: Number(f.alphaPct ?? (f as any).alpha_pct ?? 0),
            informationRatio: Number(f.informationRatio ?? (f as any).information_ratio ?? 0),
            outperformancePct: Number(f.outperformancePct ?? (f as any).outperformance_pct ?? 0),
            sampleStatus: String(f.sampleStatus || (f as any).sample_status || 'PROVISIONAL'),
            sampleDays: Number(f.sampleDays ?? (f as any).sample_days ?? 0),
          };
          map[f.isin] = item;
        }
        factorMetrics = map;
      }
    } catch (e) {
      console.warn('Could not load factor benchmarks', e);
    }
  });

  function toggleInspectFund(isin: string) {
    if (expandedFundIsin === isin) {
      expandedFundIsin = null;
    } else {
      expandedFundIsin = isin;
    }
  }

  function toggleBucket(bucketName: string) {
    expandedBuckets = {
      ...expandedBuckets,
      [bucketName]: !expandedBuckets[bucketName]
    };
  }

  function getBucketColor(bucket: string): string {
    switch (bucket) {
      case 'EQUITY_CORE': return 'var(--accent-cyan)';
      case 'EQUITY_SATELLITE': return 'var(--accent-violet)';
      case 'GOLD_SOVEREIGN':
      case 'GOLD_TACTICAL':
      case 'GOLD_SILVER': return 'var(--accent-amber)';
      case 'ARBITRAGE_LIQUID':
      case 'LIQUID_BUFFER': return 'var(--accent-emerald)';
      case 'LEGACY_HOLDINGS': return 'var(--accent-rose)';
      default: return 'var(--accent-cyan)';
    }
  }

  function getBucketGradient(bucket: string): string {
    switch (bucket) {
      case 'EQUITY_CORE': return 'linear-gradient(90deg, #06B6D4, #3B82F6)';
      case 'EQUITY_SATELLITE': return 'linear-gradient(90deg, #A855F7, #EC4899)';
      case 'GOLD_SOVEREIGN':
      case 'GOLD_TACTICAL':
      case 'GOLD_SILVER': return 'linear-gradient(90deg, #F59E0B, #FBBF24)';
      case 'ARBITRAGE_LIQUID':
      case 'LIQUID_BUFFER': return 'linear-gradient(90deg, #10B981, #06B6D4)';
      case 'LEGACY_HOLDINGS': return 'linear-gradient(90deg, #F43F5E, #FB7185)';
      default: return 'linear-gradient(90deg, #06B6D4, #3B82F6)';
    }
  }

  function formatBucketName(raw: string): string {
    return raw.replace(/_/g, ' ');
  }

  function getPreferredFundName(bucket: string): string {
    switch (bucket) {
      case 'EQUITY_CORE': return 'Parag Parikh Flexi Cap / UTI Nifty 50 Index';
      case 'EQUITY_SATELLITE': return 'ICICI Prudential Smallcap / Bandhan Large Cap';
      case 'GOLD_SILVER': return 'Motilal Oswal Gold and Silver Passive FoF';
      case 'LIQUID_BUFFER':
      case 'ARBITRAGE_LIQUID': return 'Invesco India Arbitrage / Parag Parikh Liquid';
      case 'LEGACY_HOLDINGS': return 'Motilal Oswal Midcap (Phase-out Target)';
      default: return 'Policy Canonical Fund';
    }
  }
</script>

<div class="bucket-card obsidian-card">
  <div class="bucket-header">
    <div class="title-wrap">
      <Scale size={16} class="header-icon" />
      <h3 class="header-title">Target Allocation & Drift Status</h3>
    </div>
    <span class="version-tag">Policy v2.3</span>
  </div>

  <div class="table-wrap">
    <table class="obsidian-table">
      <thead>
        <tr>
          <th style="text-align: left;">Portfolio Bucket</th>
          <th style="text-align: right;">Target</th>
          <th style="text-align: right;">Actual</th>
          <th style="width: 28%;">Allocation Progress</th>
          <th style="text-align: right;">Valuation</th>
          <th style="text-align: center;">Status</th>
        </tr>
      </thead>
      <tbody>
        {#each buckets as b}
          {@const isExpanded = Boolean(expandedBuckets[b.bucket])}
          {@const color = getBucketColor(b.bucket)}
          {@const gradient = getBucketGradient(b.bucket)}
          {@const drift = (b.current_pct || 0) - (b.target_pct || 0)}
          {@const isDrift = Math.abs(drift) > 5.0 && b.bucket !== 'LEGACY_HOLDINGS'}
          {@const hasFunds = b.funds && b.funds.length > 0}

          <!-- Bucket Summary Row -->
          <tr
            class="bucket-row"
            id="bucket-row-{b.bucket}"
            data-bucket={b.bucket}
            class:expanded={isExpanded}
            tabindex="0"
            role="button"
            aria-expanded={isExpanded}
            onclick={() => toggleBucket(b.bucket)}
            onkeydown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                toggleBucket(b.bucket);
              }
            }}
          >
            <td>
              <div class="bucket-name-cell">
                <ChevronRight size={14} class="chevron {isExpanded ? 'open' : ''}" />
                <span class="bucket-label">{formatBucketName(b.bucket)}</span>
                <span class="fund-count-pill" style="color: {color}; border-color: {color}33;">
                  {hasFunds ? `${b.funds!.length} ${b.funds!.length === 1 ? 'Fund' : 'Funds'}` : '0 Funds'}
                </span>
              </div>
            </td>
            <td class="tnum" style="text-align: right; color: var(--text-muted); font-weight: 500;">
              {b.bucket === 'LEGACY_HOLDINGS' ? '0.0%' : (b.target_pct || 0).toFixed(1) + '%'}
            </td>
            <td class="tnum" style="text-align: right; font-weight: 600; font-size: 0.95rem; color: {color};">
              {(b.current_pct || 0).toFixed(1)}%
            </td>
            <td>
              <div class="alloc-track">
                <div
                  class="alloc-bar"
                  style="transform: scaleX({Math.min(1, Math.max(0, (b.current_pct || 0) * 1.5 / 100))}); background: {gradient};"
                ></div>
              </div>
            </td>
            <td class="tnum" style="text-align: right; font-weight: 600; font-size: 0.92rem;">
              {#if effectiveQuiet}
                <span style="color: var(--text-muted);">₹ •••,•••</span>
              {:else}
                <span>{formatINR(Number(b.current_value) || 0)}</span>
              {/if}
            </td>
            <td style="text-align: center;">
              {#if b.bucket === 'LEGACY_HOLDINGS'}
                <span class="badge badge-phaseout">Phase-out</span>
              {:else if isDrift}
                <span class="badge badge-drift" class:negative={drift < 0}>Drift ({drift > 0 ? '+' : ''}{drift.toFixed(1)}%)</span>
              {:else}
                <span class="badge badge-balanced">Balanced</span>
              {/if}
            </td>
          </tr>

          <!-- Collapsible Fund Holdings Sub-rows -->
          {#if isExpanded && hasFunds}
            {#each b.funds! as fund}
              {@const isin = fund.isin || fund.asset_id}
              {@const factor = factorMetrics[isin]}
              <tr class="fund-subrow">
                <td colspan="6">
                  <div
                    class="fund-item"
                    id="fund-item-{isin}"
                    data-isin={isin}
                    role="button"
                    tabindex="0"
                    onclick={() => onInspectFund(isin)}
                    onkeydown={(e) => {
                      if (e.key === 'Enter') onInspectFund(isin);
                    }}
                    title="Click to view tax lots and FIFO acquisition details"
                  >
                    <div class="fund-info">
                      <div class="fund-name-row">
                        <span class="fund-bullet" style="color: {color};">●</span>
                        <span class="fund-name">{fund.fund_name || fund.asset_name || fund.name || isin}</span>
                      </div>
                      <div class="fund-subinfo-row">
                        <span class="fund-isin mono">{isin}</span>
                        {#if factor}
                          <span class="fund-bench-badge" title="Designated Benchmark: {factor.benchmarkName} ({factor.benchmarkId})">
                            {factor.benchmarkName.replace(/TRI/g, '').trim()}
                          </span>
                        {/if}
                      </div>
                    </div>
                    <div class="fund-metrics tnum">
                      <div class="metric-item">
                        <span class="m-label">NAV:</span>
                        <span class="m-val">₹{fund.current_nav || fund.nav || '--'}</span>
                      </div>
                      <div class="metric-item">
                        <span class="m-label">Value:</span>
                        <span class="m-val">
                          {#if effectiveQuiet}
                            ₹ •••,•••
                          {:else}
                            {formatINR(Number(fund.current_value || fund.value) || 0)}
                          {/if}
                        </span>
                      </div>
                      {#if fund.unrealized_gain !== undefined && !effectiveQuiet}
                        <div class="metric-item">
                          <span class="m-label">Gain:</span>
                          <span class="m-val" style="color: {Number(fund.unrealized_gain) >= 0 ? 'var(--accent-emerald-bright)' : 'var(--accent-rose)'};">
                            {Number(fund.unrealized_gain) >= 0 ? '+' : ''}{formatINR(Number(fund.unrealized_gain))} ({Number(fund.gain_pct || 0).toFixed(1)}%)
                          </span>
                        </div>
                      {/if}
                      {#if factor}
                        <div class="metric-item factor-quick-pill" title="Outperformance vs Benchmark: {(factor.outperformancePct ?? factor.alphaPct ?? 0).toFixed(1)}% | Beta: {(factor.beta ?? 1.0).toFixed(2)} | IR: {(factor.informationRatio ?? 0).toFixed(2)}">
                          <span class="m-label">Alpha:</span>
                          <span class="m-val bold" style="color: {(factor.outperformancePct ?? factor.alphaPct ?? 0) >= 0 ? 'var(--accent-emerald-bright)' : 'var(--accent-rose)'};">
                            {(factor.outperformancePct ?? factor.alphaPct ?? 0) >= 0 ? '+' : ''}{(factor.outperformancePct ?? factor.alphaPct ?? 0).toFixed(1)}%
                          </span>
                          <span class="m-label" style="margin-left: 6px;">β:</span>
                          <span class="m-val" style="color: var(--accent-cyan);">{(factor.beta ?? 1.0).toFixed(2)}</span>
                        </div>
                      {/if}
                      <button
                        type="button"
                        class="view-lots-btn-inline"
                        onclick={(e) => {
                          e.stopPropagation();
                          onInspectFund(isin);
                        }}
                        title="View Tax Lots & FIFO acquisition history"
                      >
                        Lots
                      </button>
                    </div>
                  </div>
                </td>
              </tr>
            {/each}
          {:else if isExpanded}
            <!-- Empty Bucket Informative State -->
            <tr class="fund-subrow">
              <td colspan="6">
                <div class="empty-bucket-state">
                  <div class="empty-bucket-left">
                    <ShieldCheck size={18} class="empty-icon" style="color: {color};" />
                    <div class="empty-text">
                      <span class="empty-title">No Active Holdings in {formatBucketName(b.bucket)}</span>
                      <span class="empty-desc">
                        Target allocation: <strong style="color: {color};">{b.target_pct || 0}%</strong>.
                        Preferred policy fund: <span class="fund-highlight">{getPreferredFundName(b.bucket)}</span>.
                        Any fresh lumpsum deployment or waterfall trim will route capital here automatically.
                      </span>
                    </div>
                  </div>
                  <div class="empty-badge" style="border-color: {color}44; color: {color};">
                    Awaiting deployment
                  </div>
                </div>
              </td>
            </tr>
          {/if}
        {/each}
      </tbody>
    </table>
  </div>
</div>

<style>
  .bucket-card {
    padding: 18px 20px;
    margin-top: 16px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
  }

  .bucket-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 14px;
  }

  .title-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  :global(.header-icon) {
    color: var(--accent-cyan);
  }

  .header-title {
    font-family: var(--font-brand);
    font-size: 0.95rem;
    font-weight: 600;
    color: var(--text-main);
    letter-spacing: -0.01em;
  }

  .version-tag {
    font-family: var(--font-mono);
    font-size: 0.65rem;
    font-weight: 500;
    padding: 2px 8px;
    border-radius: var(--radius-xs);
    background: var(--surface-void);
    color: var(--text-muted);
    border: 1px solid var(--border-hairline);
  }

  .table-wrap {
    overflow-x: auto;
  }

  .obsidian-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.82rem;
  }

  .obsidian-table th {
    font-family: var(--font-sans);
    font-size: 0.75rem;
    font-weight: 500;
    text-transform: none;
    letter-spacing: normal;
    color: var(--text-muted);
    padding: 8px 12px;
    border-bottom: 1px solid var(--border-hairline);
  }

  .obsidian-table td {
    padding: 12px;
    border-bottom: 1px solid var(--border-hairline);
  }

  .bucket-row {
    cursor: pointer;
    transition: background-color 0.15s ease;
    user-select: none;
  }
  .bucket-row:hover {
    background: var(--surface-card-hover);
  }
  .bucket-row:focus-visible {
    outline: 2px solid var(--accent-cyan);
    outline-offset: -2px;
  }

  .bucket-name-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  :global(.chevron) {
    color: var(--text-muted);
    transition: transform 0.2s ease;
    display: inline-block;
  }
  :global(.chevron.open) {
    transform: rotate(90deg);
  }

  .bucket-label {
    font-weight: 500;
    color: var(--text-main);
    font-family: var(--font-sans);
  }

  .fund-count-pill {
    font-family: var(--font-mono);
    font-size: 0.68rem;
    font-weight: 500;
    padding: 1px 7px;
    border-radius: var(--radius-xs);
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
  }

  .alloc-track {
    width: 100%;
    height: 5px;
    background: var(--surface-void);
    border-radius: var(--radius-xs);
    overflow: hidden;
  }

  .alloc-bar {
    height: 100%;
    width: 100%;
    transform-origin: left;
    border-radius: var(--radius-xs);
    transition: transform 0.3s ease;
  }

  .badge {
    font-family: var(--font-sans);
    font-size: 0.72rem;
    font-weight: 500;
    padding: 2px 7px;
    border-radius: var(--radius-xs);
    display: inline-block;
  }
  .badge-balanced {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--border-hairline);
  }
  .badge-drift {
    background: var(--color-accent-soft);
    color: var(--color-accent);
    border: 1px solid var(--border-hairline);
  }
  .badge-drift.negative {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--border-hairline);
  }
  .badge-phaseout {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--border-hairline);
  }

  /* Subrows */
  .fund-subrow td {
    padding: 4px 12px 10px 32px;
    background: var(--surface-void);
    border-bottom: 1px solid var(--border-hairline);
  }

  .fund-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    padding: 10px 14px;
    margin-bottom: 6px;
    cursor: pointer;
    transition: background-color 0.15s ease, border-color 0.15s ease;
  }
  .fund-item:hover {
    background: var(--surface-card-hover);
    border-color: var(--border-subtle);
  }

  .fund-info {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .fund-name {
    font-weight: 500;
    color: var(--text-main);
    font-size: 0.85rem;
  }

  .fund-isin {
    font-family: var(--font-mono);
    font-size: 0.7rem;
    color: var(--text-muted);
  }

  .fund-metrics {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .metric-item {
    font-size: 0.78rem;
  }
  .m-label {
    color: var(--text-muted);
    margin-right: 4px;
  }
  .m-val {
    color: var(--text-main);
    font-weight: 500;
  }

  .fund-name-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .fund-bullet {
    font-size: 0.65rem;
    margin-right: 4px;
  }
  .fund-subinfo-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 2px;
  }
  .fund-bench-badge {
    font-size: 0.68rem;
    color: var(--accent-cyan);
    background: var(--accent-cyan-bg);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
    padding: 1px 6px;
    font-weight: 500;
  }
  .view-lots-btn-inline {
    font-size: 0.72rem;
    font-weight: 500;
    color: var(--accent-cyan);
    background: var(--accent-cyan-bg);
    border: 1px solid var(--border-hairline);
    padding: 3px 8px;
    border-radius: var(--radius-xs);
    cursor: pointer;
    transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
    margin-left: 4px;
  }
  .view-lots-btn-inline:hover {
    background: var(--surface-card-hover);
    border-color: var(--accent-cyan);
    color: var(--text-main);
  }
  .factor-quick-pill {
    padding: 2px 8px;
    border-radius: var(--radius-xs);
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
  }

  /* Empty bucket state (not a nested card) */
  .empty-bucket-state {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: var(--surface-void);
    border: 1px dashed var(--border-hairline);
    border-radius: var(--radius-sm);
    padding: 14px 18px;
    margin: 4px 0 8px 0;
  }
  .empty-bucket-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  :global(.empty-icon) {
    opacity: 0.9;
  }
  .empty-text {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .empty-title {
    font-weight: 500;
    color: var(--text-main);
    font-size: 0.85rem;
  }
  .empty-desc {
    font-size: 0.78rem;
    color: var(--text-muted);
    line-height: 1.4;
  }
  .fund-highlight {
    color: var(--accent-cyan);
    font-family: var(--font-mono);
  }
  .empty-badge {
    font-family: var(--font-sans);
    font-size: 0.68rem;
    font-weight: 500;
    padding: 3px 8px;
    border-radius: var(--radius-xs);
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    white-space: nowrap;
  }

  @media (max-width: 768px) {
    .empty-bucket-state {
      flex-direction: column;
      align-items: flex-start;
      gap: 10px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .alloc-bar, :global(.chevron), .fund-item, .view-lots-btn-inline {
      transition: none !important;
    }
  }
</style>
