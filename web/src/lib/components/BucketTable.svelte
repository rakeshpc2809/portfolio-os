<script lang="ts">
  import type { BucketAllocationDto, HoldingDto, PolicyMetaDto } from '../api';
  import { formatCompactINR, formatINR } from '../formatters';
  import { ChevronRight, ShieldAlert, CheckCircle2, ArrowRight } from '@lucide/svelte';

  interface Props {
    buckets?: BucketAllocationDto[];
    holdings?: HoldingDto[];
    policyMeta?: PolicyMetaDto;
    isQuiet?: boolean;
    quietMode?: boolean;
    onInspectFund?: (isin: string) => void;
  }

  let {
    buckets = [],
    holdings = [],
    policyMeta = undefined,
    isQuiet = false,
    quietMode = false,
    onInspectFund = (_isin: string) => {}
  }: Props = $props();

  const effectiveQuiet = $derived(isQuiet || quietMode);

  let expandedBuckets = $state<Record<string, boolean>>({
    EQUITY_CORE: true,
    EQUITY_SATELLITE: false,
    GOLD_SILVER: false,
    LIQUID_BUFFER: false,
    LEGACY_HOLDINGS: false
  });

  const holdingsByIsin = $derived.by(() => {
    const map: Record<string, HoldingDto> = {};
    for (const h of holdings || []) {
      if (h.isin) map[h.isin] = h;
    }
    return map;
  });

  const strategicBuckets = $derived(
    (buckets || []).filter((b) => b.bucket !== 'LEGACY_HOLDINGS')
  );

  const legacyBuckets = $derived(
    (buckets || []).filter((b) => b.bucket === 'LEGACY_HOLDINGS')
  );

  const activeCorpus = $derived(
    strategicBuckets.reduce((sum, b) => sum + (Number(b.current_value) || 0), 0)
  );

  function toggleBucket(bucketName: string) {
    expandedBuckets = {
      ...expandedBuckets,
      [bucketName]: !expandedBuckets[bucketName]
    };
  }

  function getBucketColor(bucket: string): string {
    switch (bucket) {
      case 'EQUITY_CORE': return 'var(--bucket-core)';
      case 'EQUITY_SATELLITE': return 'var(--bucket-satellite)';
      case 'GOLD_SOVEREIGN':
      case 'GOLD_TACTICAL':
      case 'GOLD_SILVER': return 'var(--bucket-gold)';
      case 'ARBITRAGE_LIQUID':
      case 'LIQUID_BUFFER': return 'var(--bucket-liquid)';
      case 'LEGACY_HOLDINGS': return 'var(--bucket-legacy)';
      default: return 'var(--bucket-core)';
    }
  }

  function formatBucketName(raw: string): string {
    return raw.replace(/_/g, ' ');
  }

  function getBand(bucketName: string, targetPct: number) {
    const meta = policyMeta?.buckets?.[bucketName];
    const lower = meta?.band_lower_pct ?? 5.0;
    const upper = meta?.band_upper_pct ?? 5.0;
    const min = Math.max(0, targetPct - lower);
    const max = Math.min(100, targetPct + upper);
    return { lower, upper, min, max, routing: meta?.intra_bucket_routing ?? null };
  }

  function getScaleMax(bucketName: string, targetPct: number, currentPct: number, upperBandMax: number): number {
    const baseMax = bucketName === 'EQUITY_CORE' ? 70 : bucketName === 'EQUITY_SATELLITE' ? 50 : 25;
    return Math.max(baseMax, Math.ceil(Math.max(targetPct, currentPct, upperBandMax) / 5) * 5 + 5);
  }

  function getBucketState(b: BucketAllocationDto, min: number, max: number) {
    const cur = b.current_pct || 0;
    const tgt = b.target_pct || 0;
    if (b.is_drifted || cur < min) {
      return cur < tgt
        ? { label: 'UNDERWEIGHT', tone: 'warn' }
        : { label: 'OVERWEIGHT', tone: 'danger' };
    }
    if (cur > max) {
      return { label: 'OVERWEIGHT', tone: 'danger' };
    }
    if (cur - min <= 1.0 && tgt > 0) {
      return { label: 'NEAR FLOOR', tone: 'warn' };
    }
    return { label: 'IN BAND', tone: 'ok' };
  }

  function getRoutingStatus(bucketName: string, funds: any[]) {
    const routing = policyMeta?.buckets?.[bucketName]?.intra_bucket_routing;
    if (!routing || !funds || funds.length === 0) return null;
    const totalVal = funds.reduce((s, f) => s + (Number(f.current_value ?? f.value) || 0), 0);
    if (totalVal <= 0) return null;
    const primary = funds.find((f) => (f.isin || f.asset_id) === routing.primary_isin);
    const primaryShare = primary ? ((Number(primary.current_value ?? primary.value) || 0) / totalVal) * 100 : 0;
    const diff = primaryShare - routing.primary_target_ratio;
    const inBand = Math.abs(diff) <= routing.ratio_band;
    return {
      primaryIsin: routing.primary_isin,
      primaryShare,
      targetRatio: routing.primary_target_ratio,
      ratioBand: routing.ratio_band,
      inBand,
      passiveOnly: routing.remediate_on_passive_flow_only
    };
  }
</script>

<section class="drift-matrix" aria-label="Strategic Bucket Drift Matrix">
  <div class="matrix-header">
    <div class="matrix-title-group">
      <h2 class="matrix-title">Strategic Buckets</h2>
      <span class="matrix-meta tnum">
        Active Corpus:
        <strong data-sensitive="currency">{formatCompactINR(activeCorpus, effectiveQuiet)}</strong>
      </span>
    </div>
    <div class="matrix-badges">
      <span class="policy-pill mono">Policy {policyMeta?.version || 'v3.0'}</span>
    </div>
  </div>

  <div class="bucket-list">
    {#each strategicBuckets as b}
      {@const isExpanded = Boolean(expandedBuckets[b.bucket])}
      {@const color = getBucketColor(b.bucket)}
      {@const targetPct = b.target_pct || 0}
      {@const currentPct = b.current_pct || 0}
      {@const drift = currentPct - targetPct}
      {@const band = getBand(b.bucket, targetPct)}
      {@const scaleMax = getScaleMax(b.bucket, targetPct, currentPct, band.max)}
      {@const state = getBucketState(b, band.min, band.max)}
      {@const gapInr = ((targetPct - currentPct) / 100) * activeCorpus}
      {@const hasFunds = b.funds && b.funds.length > 0}
      {@const routingStatus = getRoutingStatus(b.bucket, b.funds || [])}
      {@const hardCap = b.bucket === 'LIQUID_BUFFER' ? (policyMeta?.liquid_hard_cap_pct ?? 15) : null}

      <div
        class="bucket-card"
        id="bucket-row-{b.bucket}"
        data-bucket={b.bucket}
        class:expanded={isExpanded}
      >
        <button
          type="button"
          class="bucket-row-btn"
          aria-expanded={isExpanded}
          onclick={() => toggleBucket(b.bucket)}
        >
          <div class="row-top">
            <div class="row-identity">
              <ChevronRight size={15} class="chevron {isExpanded ? 'open' : ''}" />
              <span class="bucket-swatch" style="background: {color};"></span>
              <span class="bucket-name">{formatBucketName(b.bucket)}</span>
              {#if routingStatus}
                <span class="routing-chip" class:skewed={!routingStatus.inBand}>
                  {routingStatus.inBand
                    ? `${routingStatus.targetRatio.toFixed(0)}/${(100 - routingStatus.targetRatio).toFixed(0)} Healthy`
                    : `Skew next buy → Primary`}
                </span>
              {/if}
            </div>

            <div class="row-numbers tnum">
              <span class="pct-pair">
                <strong style="color: {color};">{currentPct.toFixed(1)}%</strong>
                <span class="pct-sep">/</span>
                <span class="pct-target">{targetPct.toFixed(1)}%</span>
              </span>
              <span
                class="drift-delta"
                class:pos={drift > 0.05}
                class:neg={drift < -0.05}
              >
                {drift >= 0 ? '+' : ''}{drift.toFixed(1)}%
              </span>
              <span class="bucket-val" data-sensitive="currency">
                {formatCompactINR(Number(b.current_value) || 0, effectiveQuiet)}
              </span>
              <span class="state-pill state-{state.tone}">{state.label}</span>
            </div>
          </div>

          <!-- Inline SVG Tolerance-Band Bullet Bar -->
          <div class="bullet-wrap">
            <svg
              class="bullet-svg"
              viewBox="0 0 400 16"
              preserveAspectRatio="none"
              aria-hidden="true"
            >
              <!-- Full Track -->
              <rect x="0" y="5" width="400" height="6" rx="3" fill="var(--bg-elevated)" />
              <!-- Shaded Tolerance Corridor -->
              <rect
                x={(band.min / scaleMax) * 400}
                y="2"
                width={Math.max(2, ((band.max - band.min) / scaleMax) * 400)}
                height="12"
                rx="2"
                fill={color}
                fill-opacity="0.18"
              />
              <!-- Actual Allocation Bar -->
              <rect
                x="0"
                y="5"
                width={Math.min(400, Math.max(2, (currentPct / scaleMax) * 400))}
                height="6"
                rx="3"
                fill={state.tone === 'ok' ? color : state.tone === 'warn' ? 'var(--state-warn)' : 'var(--state-danger)'}
              />
              <!-- Target Tick -->
              <line
                x1={(targetPct / scaleMax) * 400}
                y1="1"
                x2={(targetPct / scaleMax) * 400}
                y2="15"
                stroke="var(--ink-primary)"
                stroke-width="1.5"
              />
              <!-- Liquid Hard Cap Tick -->
              {#if hardCap !== null}
                <line
                  x1={(hardCap / scaleMax) * 400}
                  y1="1"
                  x2={(hardCap / scaleMax) * 400}
                  y2="15"
                  stroke="var(--state-danger)"
                  stroke-width="1.2"
                  stroke-dasharray="2,2"
                />
              {/if}
            </svg>

            <div class="bullet-caption tnum">
              <span>Corridor: {band.min.toFixed(1)}%–{band.max.toFixed(1)}%</span>
              {#if hardCap !== null}
                <span>Hard Cap: {hardCap.toFixed(0)}%</span>
              {/if}
              <span class="gap-text">
                {#if Math.abs(gapInr) >= 1000}
                  Gap: <span data-sensitive="currency">{gapInr > 0 ? '+' : ''}{formatCompactINR(gapInr, effectiveQuiet)}</span>
                {:else}
                  On target
                {/if}
              </span>
            </div>
          </div>
        </button>

        {#if isExpanded && hasFunds}
          <div class="fund-list">
            {#each b.funds! as fund}
              {@const isin = fund.isin || fund.asset_id}
              {@const h = holdingsByIsin[isin]}
              {@const fVal = Number(fund.current_value ?? fund.value ?? 0)}
              {@const bucketShare = Number(fund.allocation_pct_in_bucket ?? (Number(b.current_value) > 0 ? (fVal / Number(b.current_value)) * 100 : 0))}
              {@const targetWeight = fund.target_weight_in_bucket}
              {@const exitFreeVal = h?.exit_load_free_value ?? fVal}
              {@const lockedUnits = h?.locked_units ?? 0}
              {@const gainPct = Number(fund.gain_pct ?? h?.unrealized_gain_pct ?? 0)}

              <button
                type="button"
                class="fund-row"
                id="fund-item-{isin}"
                data-isin={isin}
                onclick={() => onInspectFund(isin)}
                title="Inspect FIFO tax lots"
              >
                <div class="fund-left">
                  <div class="fund-name-line">
                    <span class="fund-name">{fund.fund_name || fund.asset_name || fund.name || isin}</span>
                    <span class="fund-isin mono">{isin}</span>
                  </div>
                  <div class="fund-sub-line tnum">
                    <span>{bucketShare.toFixed(1)}% of bucket{#if targetWeight} (Target {Number(targetWeight).toFixed(0)}%){/if}</span>
                    <span class="dot-sep">·</span>
                    {#if lockedUnits > 0.001}
                      <span class="exit-warn">
                        Exit-load free: <span data-sensitive="currency">{formatCompactINR(exitFreeVal, effectiveQuiet)}</span>
                      </span>
                    {:else}
                      <span class="exit-ok">0d exit load</span>
                    {/if}
                  </div>
                </div>

                <div class="fund-right tnum">
                  <span class="fund-val" data-sensitive="currency">{formatINR(fVal, effectiveQuiet)}</span>
                  <span class="fund-gain" class:pos={gainPct >= 0} class:neg={gainPct < 0}>
                    {gainPct >= 0 ? '+' : ''}{gainPct.toFixed(1)}%
                  </span>
                  <ArrowRight size={13} class="inspect-arrow" />
                </div>
              </button>
            {/each}
          </div>
        {/if}
      </div>
    {/each}
  </div>

  <!-- Separated Legacy Wind-Down Footer -->
  {#if legacyBuckets.length > 0}
    {#each legacyBuckets as leg}
      {@const legExpanded = Boolean(expandedBuckets[leg.bucket])}
      {@const legFunds = leg.funds || []}
      <div class="legacy-footer" id="bucket-row-{leg.bucket}" data-bucket={leg.bucket}>
        <button
          type="button"
          class="legacy-btn"
          aria-expanded={legExpanded}
          onclick={() => toggleBucket(leg.bucket)}
        >
          <div class="row-identity">
            <ChevronRight size={14} class="chevron {legExpanded ? 'open' : ''}" />
            <span class="bucket-swatch" style="background: var(--bucket-legacy);"></span>
            <span class="legacy-title">LEGACY WIND-DOWN</span>
            <span class="legacy-sub">Excluded from active 100% targets</span>
          </div>
          <div class="row-numbers tnum">
            <span class="bucket-val" data-sensitive="currency">
              {formatINR(Number(leg.current_value) || 0, effectiveQuiet)}
            </span>
            <span class="state-pill state-danger">{legFunds.length} {legFunds.length === 1 ? 'fund' : 'funds'}</span>
          </div>
        </button>

        {#if legExpanded && legFunds.length > 0}
          <div class="fund-list">
            {#each legFunds as fund}
              {@const isin = fund.isin || fund.asset_id}
              <button
                type="button"
                class="fund-row"
                onclick={() => onInspectFund(isin)}
              >
                <div class="fund-left">
                  <div class="fund-name-line">
                    <span class="fund-name">{fund.fund_name || fund.asset_name || isin}</span>
                    <span class="fund-isin mono">{isin}</span>
                  </div>
                  <div class="fund-sub-line">Scheduled for tax-optimal exit</div>
                </div>
                <div class="fund-right tnum">
                  <span class="fund-val" data-sensitive="currency">
                    {formatINR(Number(fund.current_value ?? fund.value) || 0, effectiveQuiet)}
                  </span>
                  <ArrowRight size={13} class="inspect-arrow" />
                </div>
              </button>
            {/each}
          </div>
        {/if}
      </div>
    {/each}
  {/if}
</section>

<style>
  .drift-matrix {
    background: var(--bg-surface);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-md);
    padding: 18px 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .matrix-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .matrix-title-group {
    display: flex;
    align-items: baseline;
    gap: 12px;
    flex-wrap: wrap;
  }

  .matrix-title {
    font-size: 0.95rem;
    font-weight: 600;
    letter-spacing: -0.01em;
    color: var(--ink-primary);
  }

  .matrix-meta {
    font-size: 0.8rem;
    color: var(--ink-muted);
  }

  .matrix-meta strong {
    color: var(--ink-secondary);
    font-weight: 600;
  }

  .policy-pill {
    font-size: 0.72rem;
    color: var(--ink-muted);
    background: var(--bg-elevated);
    border: 1px solid var(--border-hair);
    padding: 2px 8px;
    border-radius: var(--radius-sm);
  }

  .bucket-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .bucket-card {
    background: var(--bg-canvas);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-md);
    overflow: hidden;
    transition: border-color 140ms var(--ease-snappy);
  }

  .bucket-card:hover {
    border-color: var(--border-strong);
  }

  .bucket-row-btn {
    width: 100%;
    text-align: left;
    background: transparent;
    border: none;
    color: inherit;
    padding: 14px 16px;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-height: 44px;
  }

  .row-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .row-identity {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  :global(.chevron) {
    color: var(--ink-muted);
    transition: transform 150ms var(--ease-snappy);
    flex-shrink: 0;
  }

  :global(.chevron.open) {
    transform: rotate(90deg);
    color: var(--ink-primary);
  }

  .bucket-swatch {
    width: 8px;
    height: 8px;
    border-radius: 2px;
    flex-shrink: 0;
  }

  .bucket-name {
    font-size: 0.86rem;
    font-weight: 600;
    letter-spacing: 0.02em;
    color: var(--ink-primary);
  }

  .routing-chip {
    font-size: 0.7rem;
    padding: 2px 7px;
    border-radius: var(--radius-sm);
    background: var(--state-ok-soft);
    color: var(--state-ok);
    border: 1px solid color-mix(in oklch, var(--state-ok) 30%, transparent);
  }

  .routing-chip.skewed {
    background: var(--state-warn-soft);
    color: var(--state-warn);
    border-color: color-mix(in oklch, var(--state-warn) 35%, transparent);
  }

  .row-numbers {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-left: auto;
  }

  .pct-pair {
    font-size: 0.88rem;
  }

  .pct-sep,
  .pct-target {
    color: var(--ink-muted);
    font-size: 0.8rem;
  }

  .drift-delta {
    font-size: 0.78rem;
    color: var(--ink-muted);
    min-width: 48px;
    text-align: right;
  }

  .drift-delta.pos { color: var(--state-ok); }
  .drift-delta.neg { color: var(--state-warn); }

  .bucket-val {
    font-size: 0.88rem;
    font-weight: 600;
    color: var(--ink-primary);
    min-width: 74px;
    text-align: right;
  }

  .state-pill {
    font-size: 0.68rem;
    font-weight: 600;
    letter-spacing: 0.04em;
    padding: 3px 8px;
    border-radius: var(--radius-sm);
    white-space: nowrap;
  }

  .state-ok {
    background: var(--state-ok-soft);
    color: var(--state-ok);
  }

  .state-warn {
    background: var(--state-warn-soft);
    color: var(--state-warn);
  }

  .state-danger {
    background: var(--state-danger-soft);
    color: var(--state-danger);
  }

  .bullet-wrap {
    display: flex;
    flex-direction: column;
    gap: 5px;
    padding-left: 22px;
  }

  .bullet-svg {
    width: 100%;
    height: 14px;
    display: block;
  }

  .bullet-caption {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 0.72rem;
    color: var(--ink-muted);
    gap: 10px;
    flex-wrap: wrap;
  }

  .gap-text {
    margin-left: auto;
    color: var(--ink-secondary);
  }

  .fund-list {
    border-top: 1px solid var(--border-hair);
    background: var(--bg-surface);
    display: flex;
    flex-direction: column;
  }

  .fund-row {
    width: 100%;
    text-align: left;
    background: transparent;
    border: none;
    border-bottom: 1px solid var(--border-hair);
    color: inherit;
    padding: 11px 16px 11px 38px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    cursor: pointer;
    min-height: 44px;
    transition: background 120ms var(--ease-snappy);
  }

  .fund-row:last-child {
    border-bottom: none;
  }

  .fund-row:hover {
    background: var(--bg-elevated);
  }

  .fund-left {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
  }

  .fund-name-line {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .fund-name {
    font-size: 0.84rem;
    font-weight: 500;
    color: var(--ink-primary);
  }

  .fund-isin {
    font-size: 0.7rem;
    color: var(--ink-muted);
  }

  .fund-sub-line {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.74rem;
    color: var(--ink-muted);
    flex-wrap: wrap;
  }

  .dot-sep {
    opacity: 0.5;
  }

  .exit-ok {
    color: var(--state-ok);
  }

  .exit-warn {
    color: var(--state-warn);
  }

  .fund-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
  }

  .fund-val {
    font-size: 0.84rem;
    font-weight: 600;
    color: var(--ink-primary);
  }

  .fund-gain {
    font-size: 0.76rem;
    min-width: 50px;
    text-align: right;
  }

  .fund-gain.pos { color: var(--state-ok); }
  .fund-gain.neg { color: var(--state-danger); }

  :global(.inspect-arrow) {
    color: var(--ink-muted);
  }

  .legacy-footer {
    border-top: 1px dashed var(--border-strong);
    padding-top: 12px;
    margin-top: 2px;
  }

  .legacy-btn {
    width: 100%;
    background: var(--bg-canvas);
    border: 1px solid color-mix(in oklch, var(--bucket-legacy) 30%, var(--border-hair));
    border-radius: var(--radius-md);
    padding: 11px 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    cursor: pointer;
    color: inherit;
    min-height: 44px;
  }

  .legacy-title {
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--bucket-legacy);
    letter-spacing: 0.03em;
  }

  .legacy-sub {
    font-size: 0.74rem;
    color: var(--ink-muted);
  }

  @media (max-width: 640px) {
    .drift-matrix {
      padding: 14px;
    }

    .row-numbers {
      width: 100%;
      justify-content: space-between;
      padding-left: 22px;
    }

    .fund-row {
      padding-left: 16px;
      flex-direction: column;
      align-items: flex-start;
    }

    .fund-right {
      width: 100%;
      justify-content: space-between;
    }
  }
</style>
