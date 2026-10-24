<script lang="ts">
  import type { BucketAllocationDto } from '../api';
  import { formatINR } from '../formatters';

  let {
    buckets = [],
    isQuiet = false,
    quietMode = false,
    onInspectFund = (isin: string) => {}
  } = $props<{
    buckets?: BucketAllocationDto[];
    isQuiet?: boolean;
    quietMode?: boolean;
    onInspectFund?: (isin: string) => void;
  }>();

  const effectiveQuiet = $derived(isQuiet || quietMode);

  // Set of currently expanded bucket rows
  let expandedBuckets = $state<Set<string>>(new Set(['EQUITY_CORE']));

  function toggleBucket(bucketName: string) {
    const next = new Set(expandedBuckets);
    if (next.has(bucketName)) {
      next.delete(bucketName);
    } else {
      next.add(bucketName);
    }
    expandedBuckets = next;
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
          {@const isExpanded = expandedBuckets.has(b.bucket)}
          {@const color = getBucketColor(b.bucket)}
          {@const gradient = getBucketGradient(b.bucket)}
          {@const drift = (b.current_pct || 0) - (b.target_pct || 0)}
          {@const isDrift = Math.abs(drift) > 5.0 && b.bucket !== 'LEGACY_HOLDINGS'}

          <!-- Bucket Summary Row -->
          <tr
            class="bucket-row"
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
                {#if b.funds && b.funds.length > 0}
                  <span class="fund-count-pill" style="color: {color}; border-color: {color}33;">
                    {b.funds.length} {b.funds.length === 1 ? 'Fund' : 'Funds'}
                  </span>
                {/if}
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
                <span class="badge badge-drift">DRIFT ({drift > 0 ? '+' : ''}{drift.toFixed(1)}%)</span>
              {:else}
                <span class="badge badge-balanced">BALANCED</span>
              {/if}
            </td>
          </tr>

          <!-- Collapsible Fund Holdings Sub-rows -->
          {#if isExpanded && b.funds && b.funds.length > 0}
            {#each b.funds as fund}
              <tr class="fund-subrow">
                <td colspan="6">
                  <div
                    class="fund-item"
                    role="button"
                    tabindex="0"
                    onclick={() => onInspectFund(fund.isin || fund.asset_id)}
                    onkeydown={(e) => {
                      if (e.key === 'Enter') onInspectFund(fund.isin || fund.asset_id);
                    }}
                  >
                    <div class="fund-info">
                      <span class="fund-name">{fund.fund_name || fund.asset_name || fund.name || fund.isin}</span>
                      <span class="fund-isin">{fund.isin || fund.asset_id}</span>
                    </div>
                    <div class="fund-metrics tnum">
                      <div class="metric-item">
                        <span class="m-label">NAV:</span>
                        <span class="m-val">₹{fund.nav || fund.current_nav || '--'}</span>
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
                      <span class="inspect-hint">🔍 Inspect Tax Lots ➔</span>
                    </div>
                  </div>
                </td>
              </tr>
            {/each}
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
  }
  .bucket-row:hover {
    background: oklch(1 0 0 / 0.025);
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
    transition: transform 0.2s ease;
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
    padding: 1px 6px;
    border-radius: 4px;
    background: oklch(1 0 0 / 0.05);
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
  .badge-phaseout {
    background: rgba(255, 255, 255, 0.08);
    color: var(--text-muted);
    border: 1px solid var(--border-hairline);
  }

  /* Sub-row styling */
  .fund-subrow td {
    padding: 6px 14px 10px 32px;
    background: rgba(14, 20, 36, 0.4);
  }

  .fund-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 16px;
    border-radius: var(--radius-md);
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid var(--border-hairline);
    cursor: pointer;
    transition: transform 0.2s var(--ease-bounce), 
                background 0.2s ease, 
                border-color 0.2s ease, 
                box-shadow 0.2s ease;
  }
  .fund-item:hover {
    background: rgba(255, 255, 255, 0.06);
    border-color: rgba(6, 182, 212, 0.35);
    transform: translateX(4px);
    box-shadow: 0 4px 16px -4px rgba(6, 182, 212, 0.2);
  }

  .fund-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .fund-name {
    font-weight: 600;
    color: #ffffff;
    font-size: 0.8rem;
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
</style>
