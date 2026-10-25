<script lang="ts">
  import type { BucketAllocationDto } from '../api';
  import { formatINR, formatCompactINR } from '../formatters';
  import { PieChart, TrendingUp, AlertTriangle, CheckCircle2 } from '@lucide/svelte';

  interface Props {
    buckets?: BucketAllocationDto[];
    totalNetWorth?: number;
    quietMode?: boolean;
    onSelectBucket?: (bucketName: string) => void;
  }

  let {
    buckets = [],
    totalNetWorth = 0,
    quietMode = false,
    onSelectBucket = () => {}
  }: Props = $props();

  let hoveredBucket = $state<string | null>(null);

  const BUCKET_COLORS: Record<string, string> = {
    EQUITY_CORE: '#06B6D4',
    EQUITY_SATELLITE: '#A855F7',
    GOLD_SILVER: '#F59E0B',
    GOLD_SOVEREIGN: '#F59E0B',
    LIQUID_BUFFER: '#10B981',
    ARBITRAGE_LIQUID: '#10B981',
    LEGACY_HOLDINGS: '#F43F5E',
  };

  function getColor(bucket: string): string {
    return BUCKET_COLORS[bucket] || '#38BDF8';
  }

  function formatBucketLabel(raw: string): string {
    return raw.replace(/_/g, ' ');
  }

  // Calculate SVG arc segments for outer ring (Current Allocation)
  interface ArcSlice {
    bucket: string;
    label: string;
    currentPct: number;
    targetPct: number;
    currentValue: number;
    driftPct: number;
    startAngle: number;
    endAngle: number;
    path: string;
    color: string;
  }

  const radiusOuter = 95;
  const radiusInner = 68;
  const cx = 130;
  const cy = 130;

  function polarToCartesian(centerX: number, centerY: number, radius: number, angleInDegrees: number) {
    const angleInRadians = ((angleInDegrees - 90) * Math.PI) / 180.0;
    return {
      x: centerX + radius * Math.cos(angleInRadians),
      y: centerY + radius * Math.sin(angleInRadians),
    };
  }

  function describeArc(x: number, y: number, rOuter: number, rInner: number, startAngle: number, endAngle: number) {
    // Clamp arc to prevent full 360 drawing glitch
    const sweepAngle = Math.min(endAngle - startAngle, 359.99);
    const end = startAngle + sweepAngle;

    const startO = polarToCartesian(x, y, rOuter, end);
    const endO = polarToCartesian(x, y, rOuter, startAngle);
    const startI = polarToCartesian(x, y, rInner, startAngle);
    const endI = polarToCartesian(x, y, rInner, end);

    const largeArcFlag = sweepAngle <= 180 ? '0' : '1';

    return [
      'M', startO.x, startO.y,
      'A', rOuter, rOuter, 0, largeArcFlag, 0, endO.x, endO.y,
      'L', startI.x, startI.y,
      'A', rInner, rInner, 0, largeArcFlag, 1, endI.x, endI.y,
      'Z'
    ].join(' ');
  }

  const slices = $derived<ArcSlice[]>(() => {
    if (!buckets || buckets.length === 0) return [];
    const validBuckets = buckets.filter(b => (b.current_pct || 0) > 0 || (b.target_pct || 0) > 0);
    const sumPct = validBuckets.reduce((acc, b) => acc + (b.current_pct || 0), 0) || 100;

    let currentAngle = 0;
    return validBuckets.map((b) => {
      const pct = b.current_pct || 0;
      const angleSpan = (pct / sumPct) * 360;
      const start = currentAngle;
      const end = currentAngle + Math.max(angleSpan, 0.5);
      currentAngle = end;

      return {
        bucket: b.bucket,
        label: formatBucketLabel(b.bucket),
        currentPct: b.current_pct || 0,
        targetPct: b.target_pct || 0,
        currentValue: Number(b.current_value) || 0,
        driftPct: (b.current_pct || 0) - (b.target_pct || 0),
        startAngle: start,
        endAngle: end,
        path: describeArc(cx, cy, radiusOuter, radiusInner, start, end),
        color: getColor(b.bucket)
      };
    });
  });

  const activeHoveredSlice = $derived<ArcSlice | null>(() => {
    if (!hoveredBucket) return null;
    return slices().find(s => s.bucket === hoveredBucket) || null;
  });
</script>

<div class="donut-card obsidian-card">
  <div class="donut-header">
    <div class="header-left">
      <div class="icon-wrap">
        <PieChart size={16} class="text-cyan" />
      </div>
      <div>
        <h3 class="header-title">Asset Allocation & Policy Radar</h3>
        <span class="header-sub">Dual-ring visual drift map (Outer: Actual vs Inner: Target)</span>
      </div>
    </div>
    <span class="v23-pill mono">v2.3 POLICY</span>
  </div>

  <div class="donut-body">
    <!-- SVG Donut Graphic -->
    <div class="svg-container">
      <svg viewBox="0 0 260 260" class="donut-svg">
        <defs>
          <filter id="glow-cyan" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        <!-- Inner target ring track -->
        <circle cx={cx} cy={cy} r="60" fill="none" stroke="rgba(255, 255, 255, 0.05)" stroke-width="6" />

        <!-- Outer Slices -->
        {#each slices() as s}
          {@const isHovered = hoveredBucket === s.bucket}
          <path
            d={s.path}
            fill={s.color}
            opacity={hoveredBucket && !isHovered ? 0.35 : 0.9}
            stroke="#0a0f1d"
            stroke-width="2.5"
            class="slice-path"
            class:hovered={isHovered}
            style="filter: {isHovered ? `drop-shadow(0 0 8px ${s.color})` : 'none'};"
            role="button"
            tabindex="0"
            aria-label={`${s.label}: ${s.currentPct.toFixed(1)}%`}
            onmouseenter={() => hoveredBucket = s.bucket}
            onmouseleave={() => hoveredBucket = null}
            onclick={() => onSelectBucket(s.bucket)}
            onkeydown={(e) => { if (e.key === 'Enter') onSelectBucket(s.bucket); }}
          />
        {/each}

        <!-- Center HUD Text -->
        <g class="center-hud" pointer-events="none">
          {#if activeHoveredSlice()}
            {@const hs = activeHoveredSlice()!}
            <text x={cx} y={cy - 14} class="center-sub-text">{hs.label}</text>
            <text x={cx} y={cy + 8} class="center-main-text" fill={hs.color}>
              {hs.currentPct.toFixed(1)}%
            </text>
            <text x={cx} y={cy + 24} class="center-drift-text" fill={Math.abs(hs.driftPct) > 5 ? '#F43F5E' : '#10B981'}>
              Target: {hs.targetPct.toFixed(1)}% ({hs.driftPct > 0 ? '+' : ''}{hs.driftPct.toFixed(1)}%)
            </text>
          {:else}
            <text x={cx} y={cy - 12} class="center-sub-text">TOTAL CORPUS</text>
            <text x={cx} y={cy + 10} class="center-main-text">
              {formatCompactINR(totalNetWorth, quietMode)}
            </text>
            <text x={cx} y={cy + 26} class="center-drift-text">
              {slices().length} Active Buckets
            </text>
          {/if}
        </g>
      </svg>
    </div>

    <!-- Legend & Quantitative Breakdown -->
    <div class="donut-legend">
      {#each slices() as s}
        {@const isHovered = hoveredBucket === s.bucket}
        {@const isDrift = Math.abs(s.driftPct) > 5.0 && s.bucket !== 'LEGACY_HOLDINGS'}
        <div 
          class="legend-row"
          class:active={isHovered}
          onmouseenter={() => hoveredBucket = s.bucket}
          onmouseleave={() => hoveredBucket = null}
          onclick={() => onSelectBucket(s.bucket)}
          role="button"
          tabindex="0"
          onkeydown={(e) => { if (e.key === 'Enter') onSelectBucket(s.bucket); }}
        >
          <div class="legend-left">
            <span class="legend-dot" style="background: {s.color}; box-shadow: 0 0 8px {s.color}66;"></span>
            <div class="legend-labels">
              <span class="legend-name font-medium">{s.label}</span>
              <span class="legend-val mono text-muted">
                {quietMode ? '₹ •••,•••' : formatINR(s.currentValue)}
              </span>
            </div>
          </div>

          <div class="legend-right tnum">
            <div class="pct-stack">
              <span class="actual-pct bold" style="color: {s.color};">{s.currentPct.toFixed(1)}%</span>
              <span class="target-pct text-muted">Goal: {s.targetPct.toFixed(1)}%</span>
            </div>
            {#if s.bucket === 'LEGACY_HOLDINGS'}
              <span class="badge badge-rose">EXIT</span>
            {:else if isDrift}
              <span class="badge badge-amber">{s.driftPct > 0 ? '+' : ''}{s.driftPct.toFixed(1)}%</span>
            {:else}
              <span class="badge badge-green">IN-BAND</span>
            {/if}
          </div>
        </div>
      {/each}
    </div>
  </div>
</div>

<style>
  .donut-card {
    padding: 18px 20px;
    margin-top: 16px;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
  }

  .donut-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 14px;
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .icon-wrap {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    background: rgba(6, 182, 212, 0.1);
    border: 1px solid rgba(6, 182, 212, 0.25);
    border-radius: var(--radius-md);
  }

  .header-title {
    font-size: 0.95rem;
    font-weight: 700;
    color: #ffffff;
    margin: 0;
  }

  .header-sub {
    font-size: 0.72rem;
    color: var(--text-muted);
  }

  .v23-pill {
    font-size: 0.65rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: var(--radius-xs);
    background: rgba(6, 182, 212, 0.1);
    color: var(--accent-cyan);
    border: 1px solid rgba(6, 182, 212, 0.3);
  }

  .donut-body {
    display: grid;
    grid-template-columns: 260px 1fr;
    gap: 24px;
    align-items: center;
  }

  .svg-container {
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .donut-svg {
    width: 240px;
    height: 240px;
    overflow: visible;
  }

  .slice-path {
    cursor: pointer;
    transition: opacity 0.2s ease, transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  }
  .slice-path:hover {
    transform: scale(1.02);
    transform-origin: 130px 130px;
  }

  .center-main-text {
    font-family: var(--font-mono);
    font-size: 1.25rem;
    font-weight: 800;
    fill: #ffffff;
    text-anchor: middle;
    dominant-baseline: middle;
  }

  .center-sub-text {
    font-family: var(--font-mono);
    font-size: 0.62rem;
    font-weight: 700;
    fill: var(--text-muted);
    text-anchor: middle;
    letter-spacing: 0.08em;
  }

  .center-drift-text {
    font-family: var(--font-mono);
    font-size: 0.68rem;
    font-weight: 600;
    fill: var(--text-muted);
    text-anchor: middle;
  }

  .donut-legend {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .legend-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all 0.2s ease;
  }
  .legend-row:hover, .legend-row.active {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(6, 182, 212, 0.4);
    transform: translateX(3px);
  }

  .legend-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .legend-dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .legend-labels {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .legend-name {
    font-size: 0.82rem;
    color: #ffffff;
  }

  .legend-val {
    font-size: 0.72rem;
  }

  .legend-right {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .pct-stack {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 1px;
  }

  .actual-pct {
    font-size: 0.88rem;
  }

  .target-pct {
    font-size: 0.68rem;
  }

  .badge {
    font-family: var(--font-mono);
    font-size: 0.65rem;
    font-weight: 700;
    padding: 2px 6px;
    border-radius: 4px;
    white-space: nowrap;
  }

  .badge-green {
    background: rgba(16, 185, 129, 0.15);
    color: var(--accent-emerald-bright);
    border: 1px solid rgba(16, 185, 129, 0.3);
  }
  .badge-amber {
    background: rgba(245, 158, 11, 0.15);
    color: var(--accent-amber-bright);
    border: 1px solid rgba(245, 158, 11, 0.3);
  }
  .badge-rose {
    background: rgba(244, 63, 94, 0.15);
    color: var(--accent-rose);
    border: 1px solid rgba(244, 63, 94, 0.3);
  }

  @media (max-width: 840px) {
    .donut-body {
      grid-template-columns: 1fr;
    }
  }
</style>
