<script lang="ts">
  import { onMount } from 'svelte';
  import type { BucketAllocationDto } from '../api';
  import { fetchFactorBenchmarks, type FactorBenchmarkMetric } from '../api';
  import { formatINR } from '../formatters';

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
      <span class="header-icon">⚖️</span>
      <h3 class="header-title">Target Allocation & Drift Status</h3>
    </div>
    <span class="version-tag">POLICY v2.3</span>
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
                <span class="chevron" class:open={isExpanded}>▶</span>
                <span class="bucket-label">{formatBucketName(b.bucket)}</span>
                <span class="fund-count-pill" style="color: {color}; border-color: {color}33;">
                  {hasFunds ? `${b.funds!.length} ${b.funds!.length === 1 ? 'Fund' : 'Funds'}` : '0 Funds'}
                </span>
              </div>
            </td>
            <td class="tnum" style="text-align: right; color: var(--text-muted); font-weight: 600;">
              {b.bucket === 'LEGACY_HOLDINGS' ? '0.0%' : (b.target_pct || 0).toFixed(1) + '%'}
            </td>
            <td class="tnum" style="text-align: right; font-weight: 800; font-size: 0.95rem; color: {color};">
              {(b.current_pct || 0).toFixed(1)}%
            </td>
            <td>
              <div class="alloc-track">
                <div
                  class="alloc-bar"
                  style="width: {Math.min(100, Math.max(0, (b.current_pct || 0) * 1.5))}%; background: {gradient}; box-shadow: 0 0 12px {color}66;"
                ></div>
              </div>
            </td>
            <td class="tnum" style="text-align: right; font-weight: 700; font-size: 0.92rem;">
              {#if effectiveQuiet}
                <span style="color: var(--text-muted);">₹ •••,•••</span>
              {:else}
                <span>{formatINR(Number(b.current_value) || 0)}</span>
              {/if}
            </td>
            <td style="text-align: center;">
              {#if b.bucket === 'LEGACY_HOLDINGS'}
                <span class="badge badge-phaseout">PHASE-OUT</span>
              {:else if isDrift}
                <span class="badge badge-drift" class:negative={drift < 0}>DRIFT ({drift > 0 ? '+' : ''}{drift.toFixed(1)}%)</span>
              {:else}
                <span class="badge badge-balanced">BALANCED</span>
              {/if}
            </td>
          </tr>

          <!-- Collapsible Fund Holdings Sub-rows -->
          {#if isExpanded && hasFunds}
            {#each b.funds! as fund}
              {@const isin = fund.isin || fund.asset_id}
              {@const isFundDrawerOpen = expandedFundIsin === isin}
              {@const factor = factorMetrics[isin]}
              <tr class="fund-subrow">
                <td colspan="6">
                  <div
                    class="fund-item"
                    id="fund-item-{isin}"
                    data-isin={isin}
                    class:drawer-open={isFundDrawerOpen}
                    role="button"
                    tabindex="0"
                    onclick={() => toggleInspectFund(isin)}
                    onkeydown={(e) => {
                      if (e.key === 'Enter') toggleInspectFund(isin);
                    }}
                  >
                    <div class="fund-info">
                      <div class="fund-name-row">
                        <span class="chevron fund-chevron" class:open={isFundDrawerOpen}>▶</span>
                        <span class="fund-name">{fund.fund_name || fund.asset_name || fund.name || isin}</span>
                      </div>
                      <span class="fund-isin">{isin}</span>
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
                        <div class="metric-item factor-quick-pill">
                          <span class="m-label">β:</span>
                          <span class="m-val" style="color: var(--accent-cyan);">{(factor.beta ?? 1.0).toFixed(2)}</span>
                          <span class="m-label" style="margin-left: 6px;">IR:</span>
                          <span class="m-val" style="color: {(factor.informationRatio ?? 0) >= 0.5 ? 'var(--accent-emerald)' : 'var(--text-muted)'};">{(factor.informationRatio ?? 0).toFixed(2)}</span>
                        </div>
                      {/if}
                      <span class="inspect-hint">{isFundDrawerOpen ? '▲ Close HUD' : '▼ Factor HUD'}</span>
                    </div>
                  </div>

                  <!-- Factor Inspection Drawer -->
                  {#if isFundDrawerOpen}
                    <div class="factor-hud-drawer">
                      <div class="drawer-header">
                        <div class="benchmark-identity">
                          <span class="badge-bench">FACTOR BENCHMARK</span>
                          <span class="benchmark-title">{factor?.benchmarkName || 'Standard Factor Benchmark'}</span>
                          <span class="benchmark-id">({factor?.benchmarkId || 'NIFTY_500_TRI'})</span>
                        </div>
                        <button
                          type="button"
                          class="view-lots-btn"
                          onclick={(e) => {
                            e.stopPropagation();
                            onInspectFund(isin);
                          }}
                        >
                          View Tax Lots ➔
                        </button>
                      </div>

                      {#if factor}
                        <div class="factor-grid">
                          <div class="factor-stat-card">
                            <span class="stat-label">Market Beta (β)</span>
                            <span class="stat-value tnum">{(factor.beta ?? 1.0).toFixed(2)}</span>
                            <span class="stat-sub">{(factor.beta ?? 1.0) > 1.05 ? 'High Volatility vs Factor' : (factor.beta ?? 1.0) < 0.95 ? 'Defensive Factor Dampener' : 'Neutral Factor Sensitivity'}</span>
                          </div>
                          <div class="factor-stat-card">
                            <span class="stat-label">Tracking Error</span>
                            <span class="stat-value tnum">{(factor.trackingErrorPct ?? 0).toFixed(2)}%</span>
                            <span class="stat-sub">{(factor.trackingErrorPct ?? 0) < 0.5 ? 'Tight Factor Replication' : 'Active Share Divergence'}</span>
                          </div>
                          <div class="factor-stat-card">
                            <span class="stat-label">Annualized Alpha</span>
                            <span class="stat-value tnum" style="color: {(factor.alphaPct ?? 0) >= 0 ? 'var(--accent-emerald-bright)' : 'var(--accent-rose)'};">
                              {(factor.alphaPct ?? 0) > 0 ? '+' : ''}{(factor.alphaPct ?? 0).toFixed(2)}%
                            </span>
                            <span class="stat-sub">Jensen's Alpha vs RBI 91D T-Bill</span>
                          </div>
                          <div class="factor-stat-card">
                            <span class="stat-label">Information Ratio</span>
                            <span class="stat-value tnum" style="color: {(factor.informationRatio ?? 0) >= 0.5 ? 'var(--accent-emerald-bright)' : 'var(--accent-amber)'};">
                              {(factor.informationRatio ?? 0).toFixed(2)}
                            </span>
                            <span class="stat-sub">{(factor.informationRatio ?? 0) >= 0.5 ? 'Exceptional Risk-Adjusted' : 'Consistent Factor Match'}</span>
                          </div>
                        </div>

                        <div class="stat-audit-bar">
                          <span class="sample-badge {factor.sampleStatus && factor.sampleStatus.includes('MATURE') ? 'mature' : 'provisional'}">
                            {factor.sampleStatus || 'PROVISIONAL'}
                          </span>
                          <span class="sample-days">{factor.sampleDays ?? 0} Trading Days Aligned</span>
                        </div>
                      {:else}
                        <div class="factor-empty">
                          <span>Historical factor return series alignment loading or uncalibrated for {isin}...</span>
                        </div>
                      {/if}
                    </div>
                  {/if}
                </td>
              </tr>
            {/each}
          {:else if isExpanded}
            <!-- Empty Bucket Informative Card -->
            <tr class="fund-subrow">
              <td colspan="6">
                <div class="empty-bucket-card">
                  <div class="empty-bucket-left">
                    <span class="empty-icon" style="color: {color};">🛡️</span>
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
                    AWAITING DEPLOYMENT
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

  .header-icon {
    font-size: 1.1rem;
  }

  .header-title {
    font-family: var(--font-brand);
    font-size: 0.95rem;
    font-weight: 700;
    color: #ffffff;
    letter-spacing: -0.01em;
  }

  .version-tag {
    font-family: var(--font-mono);
    font-size: 0.65rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: var(--radius-xs);
    background: oklch(1 0 0 / 0.05);
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
    font-family: var(--font-mono);
    font-size: 0.68rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--text-muted);
    padding: 8px 12px;
    border-bottom: 1px solid var(--border-hairline);
  }

  .obsidian-table td {
    padding: 12px;
    border-bottom: 1px solid oklch(1 0 0 / 0.04);
  }

  .bucket-row {
    cursor: pointer;
    transition: background 0.15s ease;
    user-select: none;
  }
  .bucket-row:hover {
    background: oklch(1 0 0 / 0.035);
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

  .chevron {
    font-size: 0.62rem;
    color: var(--text-muted);
    transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    display: inline-block;
  }
  .chevron.open {
    transform: rotate(90deg);
  }

  .bucket-label {
    font-weight: 600;
    color: #ffffff;
    font-family: var(--font-mono);
  }

  .fund-count-pill {
    font-family: var(--font-mono);
    font-size: 0.68rem;
    font-weight: 600;
    padding: 1px 7px;
    border-radius: 4px;
    background: oklch(1 0 0 / 0.05);
    border: 1px solid var(--border-hairline);
  }

  .alloc-track {
    width: 100%;
    height: 7px;
    background: rgba(255, 255, 255, 0.08);
    border-radius: var(--radius-pill);
    overflow: hidden;
  }

  .alloc-bar {
    height: 100%;
    border-radius: var(--radius-pill);
    transition: width 0.4s var(--ease-bounce);
  }

  .badge {
    font-family: var(--font-sans);
    font-size: 0.72rem;
    font-weight: 700;
    padding: 3px 9px;
    border-radius: var(--radius-pill);
    display: inline-block;
    letter-spacing: 0.03em;
  }
  .badge-balanced {
    background: rgba(16, 185, 129, 0.16);
    color: var(--accent-emerald-bright);
    border: 1px solid rgba(16, 185, 129, 0.35);
  }
  .badge-drift {
    background: rgba(245, 158, 11, 0.16);
    color: var(--accent-amber-bright);
    border: 1px solid rgba(245, 158, 11, 0.35);
  }
  .badge-drift.negative {
    background: rgba(244, 63, 94, 0.16);
    color: var(--accent-rose);
    border-color: rgba(244, 63, 94, 0.35);
  }
  .badge-phaseout {
    background: rgba(244, 63, 94, 0.16);
    color: var(--accent-rose);
    border: 1px solid rgba(244, 63, 94, 0.4);
  }

  /* Subrows */
  .fund-subrow td {
    padding: 4px 12px 10px 32px;
    background: rgba(0, 0, 0, 0.2);
    border-bottom: 1px solid var(--border-hairline);
  }

  .fund-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 10px 14px;
    margin-bottom: 6px;
    cursor: pointer;
    transition: all 0.2s ease;
  }
  .fund-item:hover {
    background: rgba(255, 255, 255, 0.04);
    border-color: rgba(255, 255, 255, 0.12);
  }

  .fund-info {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .fund-name {
    font-weight: 600;
    color: #ffffff;
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
    color: #ffffff;
    font-weight: 600;
  }

  .inspect-hint {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    color: var(--accent-cyan);
    margin-left: 8px;
  }

  .fund-name-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .fund-chevron {
    font-size: 0.65rem;
    color: var(--text-muted);
    transition: transform 0.2s ease;
  }
  .fund-chevron.open {
    transform: rotate(90deg);
  }
  .drawer-open {
    border-color: rgba(6, 182, 212, 0.45) !important;
    background: rgba(6, 182, 212, 0.05) !important;
    border-bottom-left-radius: 0 !important;
    border-bottom-right-radius: 0 !important;
  }
  .factor-quick-pill {
    padding: 2px 8px;
    border-radius: 4px;
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid var(--border-hairline);
  }
  .factor-hud-drawer {
    background: rgba(10, 15, 28, 0.85);
    border: 1px solid rgba(6, 182, 212, 0.35);
    border-top: none;
    border-bottom-left-radius: var(--radius-md);
    border-bottom-right-radius: var(--radius-md);
    padding: 14px 18px;
    backdrop-filter: blur(12px);
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-bottom: 8px;
  }
  .drawer-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .benchmark-identity {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .badge-bench {
    font-size: 0.65rem;
    font-weight: 700;
    padding: 2px 6px;
    border-radius: 4px;
    background: rgba(6, 182, 212, 0.15);
    color: var(--accent-cyan);
    border: 1px solid rgba(6, 182, 212, 0.3);
  }
  .benchmark-title {
    font-size: 0.82rem;
    font-weight: 600;
    color: #fff;
  }
  .benchmark-id {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    color: var(--text-muted);
  }
  .view-lots-btn {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--accent-cyan);
    background: rgba(6, 182, 212, 0.1);
    border: 1px solid rgba(6, 182, 212, 0.3);
    padding: 4px 10px;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s ease;
  }
  .view-lots-btn:hover {
    background: rgba(6, 182, 212, 0.2);
    border-color: var(--accent-cyan);
  }
  .factor-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 10px;
  }
  .factor-stat-card {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: 6px;
    padding: 8px 12px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .stat-label {
    font-size: 0.68rem;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }
  .stat-value {
    font-size: 1.1rem;
    font-weight: 700;
    color: #fff;
  }
  .stat-sub {
    font-size: 0.65rem;
    color: var(--text-muted);
  }
  .stat-audit-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 0.7rem;
    color: var(--text-muted);
  }
  .sample-badge {
    padding: 1px 6px;
    border-radius: 4px;
    font-weight: 600;
    font-size: 0.65rem;
  }
  .sample-badge.mature {
    background: rgba(16, 185, 129, 0.15);
    color: var(--accent-emerald-bright);
    border: 1px solid rgba(16, 185, 129, 0.3);
  }
  .sample-badge.provisional {
    background: rgba(245, 158, 11, 0.15);
    color: var(--accent-amber-bright);
    border: 1px solid rgba(245, 158, 11, 0.3);
  }
  .sample-days {
    font-family: var(--font-mono);
  }
  .factor-empty {
    font-size: 0.78rem;
    color: var(--text-muted);
    font-style: italic;
    padding: 8px 0;
  }

  /* Empty bucket state card */
  .empty-bucket-card {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: rgba(255, 255, 255, 0.02);
    border: 1px dashed var(--border-subtle);
    border-radius: var(--radius-md);
    padding: 14px 18px;
    margin: 4px 0 8px 0;
  }
  .empty-bucket-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .empty-icon {
    font-size: 1.3rem;
  }
  .empty-text {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .empty-title {
    font-weight: 600;
    color: #ffffff;
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
    font-family: var(--font-mono);
    font-size: 0.68rem;
    font-weight: 700;
    padding: 3px 8px;
    border-radius: 4px;
    border: 1px solid;
    white-space: nowrap;
  }

  @media (max-width: 768px) {
    .factor-grid {
      grid-template-columns: repeat(2, 1fr);
    }
    .empty-bucket-card {
      flex-direction: column;
      align-items: flex-start;
      gap: 10px;
    }
  }
</style>
