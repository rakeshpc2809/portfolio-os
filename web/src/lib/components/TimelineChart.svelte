<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { 
    createChart, 
    AreaSeries, 
    LineSeries, 
    ColorType, 
    LineStyle, 
    type IChartApi, 
    type ISeriesApi 
  } from 'lightweight-charts';
  import { TrendingUp, Shield } from '@lucide/svelte';
  import type { NetworthTrendDto } from '../api';
  import { formatCompactINR } from '../formatters';

  interface Props {
    trend?: NetworthTrendDto | null;
    dates?: string[];
    netWorths?: number[];
    investeds?: number[];
    isQuiet?: boolean;
    quietMode?: boolean;
  }

  let { 
    trend = null,
    dates = [],
    netWorths = [],
    investeds = [],
    isQuiet = false,
    quietMode = false
  }: Props = $props();

  const effectiveQuiet = $derived(isQuiet || quietMode);

  let chartContainer: HTMLDivElement | null = $state(null);
  let chart: IChartApi | null = null;
  let areaSeries: any = null;
  let investedSeries: any = null;
  let activeRange = $state<'1M' | '3M' | '6M' | '1Y' | 'ALL'>('ALL');
  let resizeObserver: ResizeObserver | null = null;

  const currentDates = $derived<string[]>(
    (trend?.dates && trend.dates.length > 0) ? trend.dates : (dates || [])
  );

  const currentNetWorths = $derived<number[]>(
    (trend?.net_worth && trend.net_worth.length > 0) ? trend.net_worth : (netWorths || [])
  );

  const currentInvesteds = $derived<number[]>(
    (trend?.invested && trend.invested.length > 0) ? trend.invested : (investeds || [])
  );

  function initChart() {
    if (!chartContainer) return;
    chartContainer.innerHTML = '';

    try {
      chart = createChart(chartContainer, {
        width: chartContainer.clientWidth || 600,
        height: 340,
        layout: {
          background: { type: ColorType.Solid, color: 'transparent' },
          textColor: '#94A3B8',
          fontSize: 11,
          fontFamily: 'JetBrains Mono, monospace'
        },
        grid: {
          vertLines: { color: 'rgba(255, 255, 255, 0.03)' },
          horzLines: { color: 'rgba(255, 255, 255, 0.03)' }
        },
        crosshair: {
          vertLine: { color: 'rgba(6, 182, 212, 0.5)', width: 1, style: LineStyle.Dashed },
          horzLine: { color: 'rgba(6, 182, 212, 0.5)', width: 1, style: LineStyle.Dashed }
        },
        rightPriceScale: {
          borderColor: 'rgba(255, 255, 255, 0.06)',
          scaleMargins: { top: 0.12, bottom: 0.08 }
        },
        timeScale: {
          borderColor: 'rgba(255, 255, 255, 0.06)',
          timeVisible: true
        }
      });

      const areaOptions = {
        lineColor: '#06B6D4',
        topColor: 'rgba(6, 182, 212, 0.32)',
        bottomColor: 'rgba(6, 182, 212, 0.01)',
        lineWidth: 2,
        priceFormat: {
          type: 'custom',
          formatter: (price: number) => formatCompactINR(price)
        }
      };

      const lineOptions = {
        color: '#64748B',
        lineWidth: 1,
        lineStyle: LineStyle.Dotted,
        priceFormat: {
          type: 'custom',
          formatter: (price: number) => formatCompactINR(price)
        }
      };

      // Support Lightweight Charts v5 (addSeries) and v4 (addAreaSeries)
      if (typeof (chart as any).addSeries === 'function' && AreaSeries) {
        areaSeries = (chart as any).addSeries(AreaSeries, areaOptions);
        investedSeries = (chart as any).addSeries(LineSeries, lineOptions);
      } else if (typeof (chart as any).addAreaSeries === 'function') {
        areaSeries = (chart as any).addAreaSeries(areaOptions);
        investedSeries = (chart as any).addLineSeries(lineOptions);
      }

      updateChartData();

      resizeObserver = new ResizeObserver(() => {
        if (chart && chartContainer) {
          chart.resize(chartContainer.clientWidth, 340);
          try {
            chart.timeScale().fitContent();
          } catch {
            // ignore
          }
        }
      });
      resizeObserver.observe(chartContainer);
    } catch (err) {
      console.warn('Chart initialization deferred:', err);
    }
  }

  function filterPointsByRange(d: string[], v: number[], inv: number[], range: string) {
    if (!d || d.length === 0) return { dates: [], values: [], invested: [] };
    if (range === 'ALL') return { dates: d, values: v, invested: inv };

    const lastDate = new Date(d[d.length - 1]);
    const cutoff = new Date(lastDate);

    if (range === '1M') cutoff.setMonth(cutoff.getMonth() - 1);
    else if (range === '3M') cutoff.setMonth(cutoff.getMonth() - 3);
    else if (range === '6M') cutoff.setMonth(cutoff.getMonth() - 6);
    else if (range === '1Y') cutoff.setFullYear(cutoff.getFullYear() - 1);

    const fDates: string[] = [];
    const fVals: number[] = [];
    const fInv: number[] = [];

    for (let i = 0; i < d.length; i++) {
      if (new Date(d[i]) >= cutoff) {
        fDates.push(d[i]);
        fVals.push(v[i] || 0);
        fInv.push(inv[i] || 0);
      }
    }
    return { dates: fDates, values: fVals, invested: fInv };
  }

  function updateChartData() {
    if (!areaSeries || !investedSeries || !chart) return;

    const d = currentDates;
    const v = currentNetWorths;
    const inv = currentInvesteds;

    if (!d || d.length === 0) return;

    const filtered = filterPointsByRange(d, v, inv, activeRange);

    const areaPoints = filtered.dates.map((date, idx) => ({
      time: date,
      value: filtered.values[idx] || 0
    }));

    const linePoints = filtered.dates.map((date, idx) => ({
      time: date,
      value: filtered.invested[idx] || 0
    }));

    // If single data point, prepend a baseline date point so line and area have visible span
    if (areaPoints.length === 1 && areaPoints[0].time) {
      try {
        const dObj = new Date(areaPoints[0].time as string);
        dObj.setDate(dObj.getDate() - 1);
        const prevTime = dObj.toISOString().split('T')[0];
        areaPoints.unshift({ time: prevTime, value: areaPoints[0].value });
        linePoints.unshift({ time: prevTime, value: linePoints[0].value });
      } catch {
        // ignore
      }
    }

    try {
      areaSeries.setData(areaPoints);
      investedSeries.setData(linePoints);
      chart.timeScale().fitContent();
    } catch (e) {
      console.warn('Chart data update non-critical warning:', e);
    }
  }

  function setRange(range: '1M' | '3M' | '6M' | '1Y' | 'ALL') {
    activeRange = range;
    updateChartData();
  }

  onMount(() => {
    initChart();
  });

  onDestroy(() => {
    if (resizeObserver) resizeObserver.disconnect();
    if (chart) {
      try { chart.remove(); } catch {}
    }
  });

  $effect(() => {
    // Re-run when dates, values, activeRange or chart update
    if (chart && areaSeries && currentDates.length > 0) {
      updateChartData();
    }
  });
</script>

<div class="chart-panel obsidian-card">
  <div class="chart-header">
    <div class="title-wrap">
      <TrendingUp size={18} class="chart-icon" />
      <h3 class="chart-title">Net Worth & Capital Invested Timeline</h3>
    </div>
    <div class="range-selector">
      {#each ['1M', '3M', '6M', '1Y', 'ALL'] as range}
        <button
          class="range-btn"
          class:active={activeRange === range}
          onclick={() => setRange(range as any)}
        >
          {range}
        </button>
      {/each}
    </div>
  </div>

  <div class="chart-viewport-wrap">
    <div bind:this={chartContainer} class="tv-container"></div>
    {#if effectiveQuiet}
      <div class="quiet-chart-shield">
        <div class="shield-dialog">
          <Shield size={32} class="shield-lock" />
          <h4>Zen Mode Shield</h4>
          <p>Portfolio timeline and interim market fluctuations hidden for peace of mind</p>
        </div>
      </div>
    {/if}
  </div>

  <div class="chart-legend">
    <div class="legend-item">
      <span class="legend-line line-cyan"></span>
      <span class="legend-label">Total Portfolio Net Worth</span>
    </div>
    <div class="legend-item">
      <span class="legend-line line-grey"></span>
      <span class="legend-label">Cost Basis (Invested Capital)</span>
    </div>
  </div>
</div>

<style>
  .chart-panel {
    padding: 22px 24px;
    position: relative;
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
  }

  .chart-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 14px;
  }

  .title-wrap {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  :global(.chart-icon) {
    color: var(--color-cool);
  }

  .chart-title {
    font-size: 1.15rem;
    font-weight: 700;
    color: var(--color-ink);
    margin: 0;
  }

  .range-selector {
    display: flex;
    gap: 4px;
    background: var(--color-paper-3);
    padding: 3px;
    border-radius: var(--radius-pill);
    border: 1px solid var(--color-rule);
  }

  .range-btn {
    padding: 5px 12px;
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--color-muted);
    background: transparent;
    border: none;
    border-radius: var(--radius-pill);
    cursor: pointer;
    transition: color 0.15s ease, background 0.15s ease;
  }

  .range-btn:hover {
    color: var(--color-ink);
    background: var(--color-paper);
  }

  .range-btn:focus-visible {
    outline: 2px solid var(--color-focus);
    outline-offset: 2px;
  }

  .range-btn.active {
    background: var(--color-cool);
    color: var(--color-paper-3);
  }

  .chart-viewport-wrap {
    position: relative;
    width: 100%;
    min-height: 340px;
  }

  .tv-container {
    width: 100%;
    height: 340px;
  }

  .quiet-chart-shield {
    position: absolute;
    inset: 0;
    background: var(--color-paper-3);
    backdrop-filter: blur(8px);
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--radius-card);
    z-index: 10;
  }

  .shield-dialog {
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
  }

  :global(.shield-lock) {
    color: var(--color-accent);
  }

  .shield-dialog h4 {
    margin: 0;
    font-size: 0.95rem;
    font-weight: 700;
    color: var(--color-accent);
  }

  .shield-dialog p {
    margin: 0;
    font-size: 0.78rem;
    color: var(--color-muted);
  }

  .chart-legend {
    display: flex;
    gap: 20px;
    margin-top: 14px;
    padding-top: 10px;
    border-top: 1px solid var(--color-rule);
    font-size: 0.75rem;
    color: var(--color-muted);
    font-family: var(--font-outlier);
  }

  .legend-item {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .legend-line {
    width: 16px;
    height: 2px;
    border-radius: 1px;
  }

  .line-cyan {
    background: var(--color-cool);
  }

  .line-grey {
    background: var(--color-muted);
    border-top: 1px dashed var(--color-muted);
  }

  @media (prefers-reduced-motion: reduce) {
    .range-btn {
      transition: none !important;
    }
  }
</style>
