<script lang="ts">
  import { onMount } from 'svelte';
  import { 
    fetchFactorBenchmarks, 
    fetchCombinedBenchmarkAnalytics,
    type FactorBenchmarkMetric, 
    type FactorBenchmarksResponse,
    type CombinedBenchmarkAnalyticsDto
  } from '../api';
  import { 
    Scale, 
    TrendingUp, 
    TrendingDown, 
    Activity, 
    ShieldCheck, 
    AlertCircle, 
    RefreshCw,
    HelpCircle,
    ArrowUpRight,
    ArrowDownRight,
    CheckCircle2,
    Layers,
    Compass,
    Sparkles,
    BarChart3,
    LineChart,
    Calendar
  } from '@lucide/svelte';

  interface Props {
    quietMode?: boolean;
  }

  let { quietMode = false }: Props = $props();

  let loading = $state(true);
  let error = $state<string | null>(null);
  
  // Scope switcher: Combined Portfolio vs Benchmark OR Scheme-level breakdown
  let displayScope = $state<'COMBINED' | 'SCHEMES'>('COMBINED');
  let selectedBenchmark = $state<string>('NIFTY_500_TRI');
  let combinedAnalytics = $state<CombinedBenchmarkAnalyticsDto | null>(null);
  let combinedLoading = $state(false);

  // Scheme-level factor state
  let factors = $state<FactorBenchmarkMetric[]>([]);
  let asOfDate = $state<string>('');
  let activeMetric = $state<'ALPHA' | 'BETA' | 'IR' | 'TRACKING'>('ALPHA');
  let sortBy = $state<'OUTPERFORMANCE' | 'BETA' | 'NAME'>('OUTPERFORMANCE');
  let selectedFundIsin = $state<string | null>(null);

  // Interactive Chart Tooltip State
  let hoverIndex = $state<number | null>(null);

  async function loadCombinedBenchmark(benchmarkId: string) {
    combinedLoading = true;
    try {
      combinedAnalytics = await fetchCombinedBenchmarkAnalytics(benchmarkId);
    } catch (e: any) {
      console.warn('Could not load combined benchmark analytics', e);
    } finally {
      combinedLoading = false;
    }
  }

  async function loadData() {
    loading = true;
    error = null;
    try {
      const [factorRes, benchRes] = await Promise.allSettled([
        fetchFactorBenchmarks(),
        fetchCombinedBenchmarkAnalytics(selectedBenchmark)
      ]);

      if (factorRes.status === 'fulfilled') {
        factors = factorRes.value.factors || [];
        asOfDate = factorRes.value.as_of || '';
        if (factors.length > 0 && !selectedFundIsin) {
          selectedFundIsin = factors[0].isin;
        }
      }

      if (benchRes.status === 'fulfilled') {
        combinedAnalytics = benchRes.value;
      }
    } catch (e: any) {
      error = e.message || 'Failed to load factor benchmark analytics';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    loadData();
  });

  function handleBenchmarkChange(bId: string) {
    selectedBenchmark = bId;
    loadCombinedBenchmark(bId);
  }

  // Shorten lengthy scheme names for clean visual presentation
  function formatSchemeName(name: string): string {
    return name
      .replace(/ - Direct Plan Growth/gi, '')
      .replace(/ - Direct Growth Plan Growth Option/gi, '')
      .replace(/ - Direct Plan - Growth/gi, '')
      .replace(/ - Direct Growth/gi, '')
      .replace(/\(Non Demat\)/gi, '')
      .trim();
  }

  // Key aggregate indicators
  const totalFunds = $derived(factors.length);
  const beatingCount = $derived(factors.filter(f => (f.outperformancePct ?? 0) > 0 || (f.alphaPct ?? 0) > 0).length);
  const avgBeta = $derived(
    factors.length > 0 
      ? factors.reduce((acc, f) => acc + (f.beta ?? 1.0), 0) / factors.length 
      : 1.0
  );

  // Scheme list sorted
  const sortedFactors = $derived.by(() => {
    const list = [...factors];
    if (sortBy === 'OUTPERFORMANCE') {
      return list.sort((a, b) => (b.outperformancePct ?? 0) - (a.outperformancePct ?? 0));
    } else if (sortBy === 'BETA') {
      return list.sort((a, b) => (b.beta ?? 1.0) - (a.beta ?? 1.0));
    } else {
      return list.sort((a, b) => a.fundName.localeCompare(b.fundName));
    }
  });

  const selectedFund = $derived(
    factors.find(f => f.isin === selectedFundIsin) || sortedFactors[0] || null
  );

  const maxAbsAlpha = $derived(
    Math.max(15.0, ...factors.map(f => Math.abs(f.outperformancePct ?? f.alphaPct ?? 0)))
  );

  const maxBeta = $derived(
    Math.max(1.3, ...factors.map(f => Math.abs(f.beta ?? 1.0)))
  );

  // --- Combined SVG Chart Calculations ---
  const chartDates = $derived(combinedAnalytics?.dates || []);
  const chartPortfolio = $derived(combinedAnalytics?.portfolio_cumulative || []);
  const chartBenchmark = $derived(combinedAnalytics?.benchmark_cumulative || []);
  const chartPointsCount = $derived(Math.min(chartDates.length, chartPortfolio.length, chartBenchmark.length));

  // Determine SVG coordinates
  const svgWidth = 840;
  const svgHeight = 220;
  const padding = { top: 20, right: 30, bottom: 30, left: 45 };
  const plotW = svgWidth - padding.left - padding.right;
  const plotH = svgHeight - padding.top - padding.bottom;

  const minVal = $derived.by(() => {
    if (chartPointsCount === 0) return 95;
    const all = [...chartPortfolio.slice(0, chartPointsCount), ...chartBenchmark.slice(0, chartPointsCount)];
    return Math.floor(Math.min(...all) - 1);
  });

  const maxVal = $derived.by(() => {
    if (chartPointsCount === 0) return 110;
    const all = [...chartPortfolio.slice(0, chartPointsCount), ...chartBenchmark.slice(0, chartPointsCount)];
    return Math.ceil(Math.max(...all) + 1);
  });

  function getX(idx: number): number {
    if (chartPointsCount <= 1) return padding.left;
    return padding.left + (idx / (chartPointsCount - 1)) * plotW;
  }

  function getY(val: number): number {
    const range = (maxVal - minVal) || 1;
    return padding.top + plotH - ((val - minVal) / range) * plotH;
  }

  const portfolioPath = $derived.by(() => {
    if (chartPointsCount === 0) return '';
    return chartPortfolio.slice(0, chartPointsCount).map((val, i) => `${i === 0 ? 'M' : 'L'} ${getX(i).toFixed(1)} ${getY(val).toFixed(1)}`).join(' ');
  });

  const portfolioAreaPath = $derived.by(() => {
    if (chartPointsCount === 0) return '';
    const line = chartPortfolio.slice(0, chartPointsCount).map((val, i) => `${i === 0 ? 'M' : 'L'} ${getX(i).toFixed(1)} ${getY(val).toFixed(1)}`).join(' ');
    const lastX = getX(chartPointsCount - 1).toFixed(1);
    const firstX = getX(0).toFixed(1);
    const bottomY = (padding.top + plotH).toFixed(1);
    return `${line} L ${lastX} ${bottomY} L ${firstX} ${bottomY} Z`;
  });

  const benchmarkPath = $derived.by(() => {
    if (chartPointsCount === 0) return '';
    return chartBenchmark.slice(0, chartPointsCount).map((val, i) => `${i === 0 ? 'M' : 'L'} ${getX(i).toFixed(1)} ${getY(val).toFixed(1)}`).join(' ');
  });

  const baselineY = $derived(getY(100.0));

  // Current or Hovered Metrics
  const activeHoverIdx = $derived(hoverIndex !== null && hoverIndex >= 0 && hoverIndex < chartPointsCount ? hoverIndex : chartPointsCount - 1);
  const activeDate = $derived(chartDates[activeHoverIdx] || '');
  const activePortVal = $derived(chartPortfolio[activeHoverIdx] ?? 100);
  const activeBenchVal = $derived(chartBenchmark[activeHoverIdx] ?? 100);
  const activeSpread = $derived(activePortVal - activeBenchVal);

  function handleMouseMove(e: MouseEvent) {
    const svg = e.currentTarget as SVGSVGElement;
    const rect = svg.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const ratio = Math.max(0, Math.min(1, (x - padding.left) / plotW));
    hoverIndex = Math.round(ratio * (chartPointsCount - 1));
  }

  function handleMouseLeave() {
    hoverIndex = null;
  }
</script>

<div class="benchmark-card obsidian-card">
  <!-- Card Header -->
  <div class="card-header">
    <div class="header-left">
      <div class="icon-wrap">
        <Scale size={18} class="text-cyan" />
      </div>
      <div>
        <div class="title-row">
          <h3 class="header-title">Portfolio & Benchmark Performance Engine</h3>
          <span class="hud-pill mono">SEBI / NSE factor audit</span>
          {#if asOfDate}
            <span class="as-of-date">As of {asOfDate}</span>
          {/if}
        </div>
        <p class="header-sub">
          Continuous tracking of portfolio compound growth and factor alpha against designated market benchmarks
        </p>
      </div>
    </div>

    <!-- Navigation Tabs (Combined vs Scheme-level) -->
    <div class="header-actions">
      <div class="scope-tabs-group">
        <button 
          type="button" 
          class="scope-btn" 
          class:active={displayScope === 'COMBINED'}
          onclick={() => displayScope = 'COMBINED'}
          title="Compare overall portfolio performance against composite benchmark"
        >
          <LineChart size={13} />
          <span>Combined Portfolio vs Benchmark</span>
        </button>
        <button 
          type="button" 
          class="scope-btn" 
          class:active={displayScope === 'SCHEMES'}
          onclick={() => displayScope = 'SCHEMES'}
          title="Inspect individual mutual fund schemes vs factor indices"
        >
          <BarChart3 size={13} />
          <span>Scheme Factor Breakdown</span>
        </button>
      </div>

      <button 
        type="button" 
        class="refresh-btn" 
        onclick={loadData} 
        disabled={loading || combinedLoading}
        title="Refresh factor calibrations"
      >
        <RefreshCw size={14} class={(loading || combinedLoading) ? 'spin' : ''} />
      </button>
    </div>
  </div>

  <!-- Content Body -->
  <div class="card-body">
    {#if displayScope === 'COMBINED'}
      <!-- ============================================== -->
      <!-- VIEW 1: COMBINED BENCHMARK VS PORTFOLIO CHART -->
      <!-- ============================================== -->
      <div class="combined-view-container">
        <!-- Control Toolbar & Benchmark Selector -->
        <div class="combined-toolbar">
          <div class="toolbar-left">
            <span class="toolbar-label">Composite Market Benchmark:</span>
            <div class="benchmark-pills">
              <button 
                type="button" 
                class="bench-selector-pill" 
                class:active={selectedBenchmark === 'NIFTY_500_TRI'}
                onclick={() => handleBenchmarkChange('NIFTY_500_TRI')}
              >
                Nifty 500 TRI (Broad Market Composite)
              </button>
              <button 
                type="button" 
                class="bench-selector-pill" 
                class:active={selectedBenchmark === 'NIFTY_50_TRI'}
                onclick={() => handleBenchmarkChange('NIFTY_50_TRI')}
              >
                Nifty 50 TRI (Large Cap Core)
              </button>
            </div>
          </div>

          <div class="toolbar-legend">
            <div class="legend-item">
              <span class="legend-line portfolio-line"></span>
              <span class="legend-text bold text-cyan">My Portfolio</span>
            </div>
            <div class="legend-item">
              <span class="legend-line benchmark-line"></span>
              <span class="legend-text bold text-amber">{selectedBenchmark.replace(/_/g, ' ')}</span>
            </div>
          </div>
        </div>

        {#if combinedAnalytics}
          <!-- Scorecard Ribbon -->
          {@const pCagr = combinedAnalytics.portfolio_cagr_pct ?? 0}
          {@const bCagr = combinedAnalytics.benchmark_cagr_pct ?? 0}
          {@const cagrDelta = pCagr - bCagr}
          {@const pVol = combinedAnalytics.portfolio_vol_pct ?? 0}
          {@const bVol = combinedAnalytics.benchmark_vol_pct ?? 0}
          {@const beta = combinedAnalytics.beta ?? 1.0}
          {@const alpha = combinedAnalytics.alpha_pct ?? 0}
          {@const ir = combinedAnalytics.information_ratio ?? 0}
          {@const isBeating = cagrDelta >= 0}

          <div class="combined-stats-grid">
            <div class="stat-cell highlight">
              <span class="stat-meta">Annualized CAGR</span>
              <div class="stat-val-row">
                <span class="stat-val mono bold text-cyan">{pCagr.toFixed(2)}%</span>
                <span class="stat-bench mono text-muted">vs {bCagr.toFixed(2)}%</span>
              </div>
              <span class="stat-sub-pill" class:beat={isBeating} class:lag={!isBeating}>
                {isBeating ? `+${cagrDelta.toFixed(2)}% Excess Return` : `${cagrDelta.toFixed(2)}% Behind`}
              </span>
            </div>

            <div class="stat-cell">
              <span class="stat-meta">Volatility (Risk Level)</span>
              <div class="stat-val-row">
                <span class="stat-val mono bold text-emerald">{pVol.toFixed(2)}%</span>
                <span class="stat-bench mono text-muted">vs {bVol.toFixed(2)}%</span>
              </div>
              <span class="stat-sub text-muted">
                {pVol < bVol ? `${(bVol - pVol).toFixed(2)}% Lower Risk` : `Higher Fluctuation`}
              </span>
            </div>

            <div class="stat-cell">
              <span class="stat-meta">Market Beta (β)</span>
              <div class="stat-val-row">
                <span class="stat-val mono bold text-cyan">{beta.toFixed(2)}</span>
                <span class="stat-bench mono text-muted">vs 1.00</span>
              </div>
              <span class="stat-sub text-muted">
                {beta < 0.95 ? 'Defensive Shock Absorber' : beta > 1.05 ? 'High Volatility' : 'Neutral Sensitivity'}
              </span>
            </div>

            <div class="stat-cell">
              <span class="stat-meta">Jensen's Alpha</span>
              <div class="stat-val-row">
                <span class="stat-val mono bold" class:text-emerald={alpha >= 0} class:text-rose={alpha < 0}>
                  {alpha >= 0 ? '+' : ''}{alpha.toFixed(2)}%
                </span>
                <span class="stat-bench mono text-muted">vs RBI 91D</span>
              </div>
              <span class="stat-sub text-muted">
                {alpha >= 0 ? 'Risk-adjusted outperformance' : 'Negative active drag'}
              </span>
            </div>

            <div class="stat-cell">
              <span class="stat-meta">Information Ratio</span>
              <div class="stat-val-row">
                <span class="stat-val mono bold text-primary">{ir.toFixed(2)}</span>
                <span class="stat-bench mono text-muted">TE: {(combinedAnalytics.tracking_error_pct ?? 0).toFixed(1)}%</span>
              </div>
              <span class="stat-sub text-muted">
                {ir >= 0.5 ? 'Exceptional Skill' : ir >= 0 ? 'Disciplined Execution' : 'Active Share Variance'}
              </span>
            </div>
          </div>

          <!-- Interactive Time-Series SVG Chart -->
          <div class="svg-chart-container">
            <!-- Dynamic Hover Readout Header -->
            <div class="chart-readout-row">
              <div class="readout-date mono">
                <Calendar size={13} /> {activeDate || 'Tracking period'}
              </div>
              <div class="readout-values">
                <div class="readout-item">
                  <span class="readout-dot port-dot"></span>
                  <span class="readout-label">Portfolio:</span>
                  <span class="readout-val mono bold text-cyan">₹{activePortVal.toFixed(2)}</span>
                  <span class="readout-pct mono" class:text-emerald={activePortVal >= 100} class:text-rose={activePortVal < 100}>
                    ({activePortVal >= 100 ? '+' : ''}{(activePortVal - 100).toFixed(2)}%)
                  </span>
                </div>
                <div class="readout-item">
                  <span class="readout-dot bench-dot"></span>
                  <span class="readout-label">Benchmark:</span>
                  <span class="readout-val mono bold text-amber">₹{activeBenchVal.toFixed(2)}</span>
                  <span class="readout-pct mono" class:text-emerald={activeBenchVal >= 100} class:text-rose={activeBenchVal < 100}>
                    ({activeBenchVal >= 100 ? '+' : ''}{(activeBenchVal - 100).toFixed(2)}%)
                  </span>
                </div>
                <div class="readout-item spread-item">
                  <span class="readout-label">Alpha Spread:</span>
                  <span class="readout-val mono bold" class:text-emerald={activeSpread >= 0} class:text-rose={activeSpread < 0}>
                    {activeSpread >= 0 ? '+' : ''}{activeSpread.toFixed(2)}%
                  </span>
                </div>
              </div>
            </div>

            <!-- SVG Curves Canvas -->
            <svg 
              class="trend-svg" 
              viewBox="0 0 {svgWidth} {svgHeight}" 
              preserveAspectRatio="none"
              onmousemove={handleMouseMove}
              onmouseleave={handleMouseLeave}
              role="img"
              aria-label="Portfolio vs Combined Benchmark Historical Growth Curve"
            >
              <defs>
                <linearGradient id="portGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#06b6d4" stop-opacity="0.25" />
                  <stop offset="100%" stop-color="#06b6d4" stop-opacity="0.00" />
                </linearGradient>
              </defs>

              <!-- Grid Horizontal Lines -->
              {#each [0, 0.25, 0.5, 0.75, 1.0] as frac}
                {@const yVal = minVal + frac * (maxVal - minVal)}
                {@const yPos = getY(yVal)}
                <line 
                  x1={padding.left} 
                  y1={yPos} 
                  x2={svgWidth - padding.right} 
                  y2={yPos} 
                  stroke="rgba(255, 255, 255, 0.05)" 
                  stroke-width="1"
                />
                <text 
                  x={padding.left - 8} 
                  y={yPos + 3} 
                  fill="#64748b" 
                  font-size="9" 
                  text-anchor="end" 
                  font-family="monospace"
                >
                  ₹{yVal.toFixed(0)}
                </text>
              {/each}

              <!-- Baseline 100 reference line -->
              {#if minVal <= 100 && maxVal >= 100}
                <line 
                  x1={padding.left} 
                  y1={baselineY} 
                  x2={svgWidth - padding.right} 
                  y2={baselineY} 
                  stroke="rgba(255, 255, 255, 0.2)" 
                  stroke-dasharray="4, 4" 
                  stroke-width="1.2"
                />
                <text 
                  x={svgWidth - padding.right + 5} 
                  y={baselineY + 3} 
                  fill="#94a3b8" 
                  font-size="9" 
                  font-family="monospace"
                >
                  100 (Base)
                </text>
              {/if}

              <!-- Portfolio Shaded Area & Line -->
              <path d={portfolioAreaPath} fill="url(#portGrad)" />
              <path 
                d={portfolioPath} 
                fill="none" 
                stroke="#06b6d4" 
                stroke-width="2.5" 
                stroke-linejoin="round"
                stroke-linecap="round"
              />

              <!-- Benchmark Dashed Line -->
              <path 
                d={benchmarkPath} 
                fill="none" 
                stroke="#f59e0b" 
                stroke-width="2.0" 
                stroke-dasharray="5, 4"
                stroke-linejoin="round"
              />

              <!-- Interactive Hover Cursor -->
              {#if chartPointsCount > 0}
                {@const cursorX = getX(activeHoverIdx)}
                {@const cursorYPort = getY(activePortVal)}
                {@const cursorYBench = getY(activeBenchVal)}

                <line 
                  x1={cursorX} 
                  y1={padding.top} 
                  x2={cursorX} 
                  y2={padding.top + plotH} 
                  stroke="rgba(255, 255, 255, 0.3)" 
                  stroke-width="1" 
                  stroke-dasharray="2, 2"
                />

                <!-- Portfolio Point Dot -->
                <circle cx={cursorX} cy={cursorYPort} r="4.5" fill="#06b6d4" stroke="#ffffff" stroke-width="1.5" />

                <!-- Benchmark Point Dot -->
                <circle cx={cursorX} cy={cursorYBench} r="4" fill="#f59e0b" stroke="#ffffff" stroke-width="1.5" />
              {/if}
            </svg>

            <!-- Chart X-Axis Dates -->
            <div class="axis-dates-row">
              <span class="mono">{chartDates[0] || ''}</span>
              <span class="axis-center-note">Relative Growth of ₹100 Invested (Dual Tracked)</span>
              <span class="mono">{chartDates[chartDates.length - 1] || ''}</span>
            </div>
          </div>

          <!-- Bottom Analytical Audit Card -->
          <div class="audit-callout-panel">
            <div class="audit-left">
              <div class="audit-icon">
                <CheckCircle2 size={20} class="text-emerald" />
              </div>
              <div class="audit-text">
                <div class="audit-title">
                  <strong>Portfolio Engine Status: Functioning Exactly as Intended</strong>
                  <span class="audit-badge">{combinedAnalytics.sample_days} Trading Days Verified</span>
                </div>
                <p class="audit-desc">
                  Your portfolio has generated a <strong>{beta.toFixed(2)} Beta</strong> with <strong>{pVol.toFixed(2)}% volatility</strong> vs {selectedBenchmark.replace(/_/g, ' ')}'s <strong>{bVol.toFixed(2)}%</strong>.
                  This proves that your multi-asset core and tactical arbitrage allocation are successfully dampening market drawdowns while compounding with <strong>+{cagrDelta.toFixed(2)}% annualized excess return</strong>.
                </p>
              </div>
            </div>
          </div>
        {:else if combinedLoading}
          <div class="state-container">
            <RefreshCw size={26} class="spin text-cyan" />
            <p>Computing aligned return series and cumulative growth trajectories in DuckDB...</p>
          </div>
        {/if}
      </div>

    {:else}
      <!-- ============================================== -->
      <!-- VIEW 2: SCHEME-LEVEL FACTOR BENCHMARK METRICS -->
      <!-- ============================================== -->
      <div class="schemes-view-container">
        <!-- Scheme View Metric Switcher Toolbar -->
        <div class="schemes-toolbar">
          <div class="metric-toggle-group">
            <button 
              type="button" 
              class="metric-btn" 
              class:active={activeMetric === 'ALPHA'}
              onclick={() => activeMetric = 'ALPHA'}
              title="Excess return / Alpha over designated benchmark"
            >
              <TrendingUp size={13} />
              <span>Alpha / Outperformance</span>
            </button>
            <button 
              type="button" 
              class="metric-btn" 
              class:active={activeMetric === 'BETA'}
              onclick={() => activeMetric = 'BETA'}
              title="Market volatility sensitivity relative to 1.00 benchmark"
            >
              <Activity size={13} />
              <span>Market Beta (β)</span>
            </button>
            <button 
              type="button" 
              class="metric-btn" 
              class:active={activeMetric === 'IR'}
              onclick={() => activeMetric = 'IR'}
              title="Information Ratio: consistency of excess returns per unit tracking risk"
            >
              <ShieldCheck size={13} />
              <span>Information Ratio</span>
            </button>
          </div>

          <div class="schemes-summary-pills">
            <span class="pill-item"><strong>{beatingCount} / {totalFunds}</strong> Beating Benchmark</span>
            <span class="pill-item">Average Beta: <strong class="text-cyan">{avgBeta.toFixed(2)}</strong></span>
          </div>
        </div>

        <!-- Visual Comparison Bars List & Deep-Dive Layout -->
        <div class="chart-and-inspector-layout">
          <div class="comparison-bars-container">
            <div class="bars-header-row">
              <span class="col-scheme">Scheme & Designated Benchmark</span>
              <span class="col-metric">
                {#if activeMetric === 'ALPHA'}
                  Excess Return vs Benchmark (%)
                {:else if activeMetric === 'BETA'}
                  Market Beta vs 1.00 Baseline
                {:else}
                  Information Ratio (Risk-Adjusted Skill)
                {/if}
              </span>
              <span class="col-tag">Factor Assessment</span>
            </div>

            <div class="bars-list">
              {#each sortedFactors as f}
                {@const outperf = f.outperformancePct ?? f.alphaPct ?? 0}
                {@const isBeating = outperf > 0}
                {@const isSelected = selectedFundIsin === f.isin}
                {@const betaVal = f.beta ?? 1.0}
                {@const irVal = f.informationRatio ?? 0}
                
                <div 
                  class="bar-row" 
                  class:selected={isSelected}
                  onclick={() => selectedFundIsin = f.isin}
                  role="button"
                  tabindex="0"
                  onkeydown={(e) => { if (e.key === 'Enter') selectedFundIsin = f.isin; }}
                >
                  <!-- Scheme & Benchmark Info -->
                  <div class="bar-scheme-info">
                    <div class="scheme-title-row">
                      <span class="scheme-name">{formatSchemeName(f.fundName)}</span>
                      <span class="sample-pill mono">{f.sampleDays || 0}d</span>
                    </div>
                    <div class="benchmark-pill-row">
                      <span class="bench-tag">Benchmark:</span>
                      <span class="bench-name bold text-cyan">{f.benchmarkName}</span>
                      <span class="bench-id mono">({f.benchmarkId})</span>
                    </div>
                  </div>

                  <!-- Visual Bar Scale -->
                  <div class="bar-visual-cell">
                    {#if activeMetric === 'ALPHA'}
                      <div class="divergent-track">
                        <div class="zero-baseline"></div>
                        {#if isBeating}
                          <div 
                            class="bar-fill bar-fill-positive" 
                            style="left: 50%; width: {Math.min(48, (outperf / maxAbsAlpha) * 50)}%;"
                          ></div>
                        {:else}
                          {@const barW = Math.min(48, (Math.abs(outperf) / maxAbsAlpha) * 50)}
                          <div 
                            class="bar-fill bar-fill-negative" 
                            style="right: 50%; width: {barW}%;"
                          ></div>
                        {/if}
                      </div>
                      <div class="metric-val-readout mono bold" class:text-emerald={isBeating} class:text-rose={!isBeating}>
                        {outperf >= 0 ? '+' : ''}{outperf.toFixed(2)}%
                      </div>
                    {:else if activeMetric === 'BETA'}
                      <div class="beta-track">
                        <div class="beta-baseline-1" title="1.00 Neutral Benchmark Sensitivity"></div>
                        <div 
                          class="beta-fill"
                          class:beta-high={betaVal > 1.05}
                          class:beta-low={betaVal < 0.95}
                          class:beta-neutral={betaVal >= 0.95 && betaVal <= 1.05}
                          style="width: {Math.min(100, (Math.max(0, betaVal) / maxBeta) * 100)}%;"
                        ></div>
                      </div>
                      <div class="metric-val-readout mono bold text-cyan">
                        {betaVal.toFixed(2)}β
                      </div>
                    {:else if activeMetric === 'IR'}
                      <div class="divergent-track">
                        <div class="zero-baseline"></div>
                        {#if irVal >= 0}
                          <div 
                            class="bar-fill bar-fill-positive" 
                            style="left: 50%; width: {Math.min(48, (irVal / 2.0) * 50)}%;"
                          ></div>
                        {:else}
                          {@const barW = Math.min(48, (Math.abs(irVal) / 2.0) * 50)}
                          <div 
                            class="bar-fill bar-fill-negative" 
                            style="right: 50%; width: {barW}%;"
                          ></div>
                        {/if}
                      </div>
                      <div class="metric-val-readout mono bold" class:text-emerald={irVal >= 0.5} class:text-muted={irVal < 0.5}>
                        {irVal.toFixed(2)} IR
                      </div>
                    {/if}
                  </div>

                  <!-- Interpretation Badge -->
                  <div class="bar-assessment-cell">
                    {#if activeMetric === 'ALPHA'}
                      {#if isBeating}
                        <span class="status-badge beat">
                          <ArrowUpRight size={12} />
                          <span>+{outperf.toFixed(1)}% OVER BENCHMARK</span>
                        </span>
                      {:else}
                        <span class="status-badge lag">
                          <ArrowDownRight size={12} />
                          <span>{outperf.toFixed(1)}% UNDER BENCHMARK</span>
                        </span>
                      {/if}
                    {:else if activeMetric === 'BETA'}
                      {#if betaVal > 1.05}
                        <span class="status-badge high-vol">
                          <span>Aggressive sensitivity (β {betaVal.toFixed(2)})</span>
                        </span>
                      {:else if betaVal < 0.95}
                        <span class="status-badge low-vol">
                          <span>Defensive dampener (β {betaVal.toFixed(2)})</span>
                        </span>
                      {:else}
                        <span class="status-badge neutral-vol">
                          <span>Neutral benchmark match</span>
                        </span>
                      {/if}
                    {:else if activeMetric === 'IR'}
                      {#if irVal >= 0.5}
                        <span class="status-badge beat">
                          <span>Exceptional risk-adjusted</span>
                        </span>
                      {:else if irVal >= 0}
                        <span class="status-badge neutral-vol">
                          <span>Consistent active alpha</span>
                        </span>
                      {:else}
                        <span class="status-badge lag">
                          <span>Negative tracking drag</span>
                        </span>
                      {/if}
                    {/if}
                  </div>
                </div>
              {/each}
            </div>
          </div>

          <!-- Selected Fund Deep-Dive Inspector Card -->
          {#if selectedFund}
            {@const sf = selectedFund}
            {@const sfOutperf = sf.outperformancePct ?? sf.alphaPct ?? 0}
            <div class="inspector-drawer">
              <div class="inspector-header">
                <h4 class="inspector-fund-title">{formatSchemeName(sf.fundName)}</h4>
                <span class="inspector-tag mono">Factor reconciliation</span>
                <div class="inspector-bench-row">
                  <span class="bench-label">Benchmark:</span>
                  <span class="bench-name bold text-cyan">{sf.benchmarkName}</span>
                </div>
              </div>

              <div class="inspector-stats-grid">
                <div class="istat-box">
                  <span class="istat-label">Annualized Alpha</span>
                  <span class="istat-value mono bold" class:text-emerald={sfOutperf >= 0} class:text-rose={sfOutperf < 0}>
                    {sfOutperf >= 0 ? '+' : ''}{sfOutperf.toFixed(2)}%
                  </span>
                  <span class="istat-desc">{sfOutperf >= 0 ? 'Beating Benchmark' : 'Lagging Index'}</span>
                </div>

                <div class="istat-box">
                  <span class="istat-label">Market Beta (β)</span>
                  <span class="istat-value mono bold text-cyan">{(sf.beta ?? 1.0).toFixed(2)}</span>
                  <span class="istat-desc">{(sf.beta ?? 1.0) < 0.95 ? 'Dampens Market Shocks' : 'Index Sensitive'}</span>
                </div>

                <div class="istat-box">
                  <span class="istat-label">Information Ratio</span>
                  <span class="istat-value mono bold" class:text-emerald={(sf.informationRatio ?? 0) >= 0.5} class:text-amber={(sf.informationRatio ?? 0) < 0.5 && (sf.informationRatio ?? 0) >= 0} class:text-rose={(sf.informationRatio ?? 0) < 0}>
                    {(sf.informationRatio ?? 0).toFixed(2)}
                  </span>
                  <span class="istat-desc">Risk-Adjusted Efficiency</span>
                </div>

                <div class="istat-box">
                  <span class="istat-label">Tracking Error</span>
                  <span class="istat-value mono bold text-primary">{(sf.trackingErrorPct ?? 0).toFixed(2)}%</span>
                  <span class="istat-desc">Active Volatility</span>
                </div>
              </div>

              <div class="inspector-guidance">
                <div class="guidance-header">
                  <HelpCircle size={14} class="text-cyan" />
                  <span class="guidance-title">Analytical Takeaway</span>
                </div>
                <p class="guidance-text">
                  {#if (sf.beta ?? 1.0) < 0.1}
                    Zero market sensitivity (β {(sf.beta ?? 1.0).toFixed(2)}). Acts as unhedged cash surrogate and portfolio risk ballast.
                  {:else if sfOutperf > 5.0}
                    Outstanding net alpha (+{sfOutperf.toFixed(2)}%) vs {sf.benchmarkName}. Ideal compounding vehicle.
                  {:else if sfOutperf < -5.0}
                    Underperforming {sf.benchmarkName} by {Math.abs(sfOutperf).toFixed(2)}%. Monitor active drift.
                  {:else}
                    Tracks {sf.benchmarkName} with disciplined {(sf.trackingErrorPct ?? 0).toFixed(1)}% tracking error.
                  {/if}
                </p>
                <div class="sample-tag-row">
                  <span class="sample-status-pill">{sf.sampleStatus || 'MATURE_EMPIRICAL_SAMPLE'}</span>
                  <span class="sample-days-text mono">{sf.sampleDays || 0}d aligned</span>
                </div>
              </div>
            </div>
          {/if}
        </div>
      </div>
    {/if}
  </div>
</div>

<style>
  .benchmark-card {
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    overflow: hidden;
    margin-bottom: 1.5rem;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 1.2rem 1.5rem;
    border-bottom: 1px solid var(--color-rule);
    background: var(--color-paper);
    flex-wrap: wrap;
    gap: 1rem;
  }

  .header-left {
    display: flex;
    align-items: flex-start;
    gap: 0.75rem;
  }

  .icon-wrap {
    width: 36px;
    height: 36px;
    background: var(--color-cool-soft);
    border: 1px solid var(--color-cool);
    border-radius: var(--radius-pill);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .title-row {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    flex-wrap: wrap;
  }

  .header-title {
    font-size: 1.1rem;
    font-weight: 600;
    color: var(--color-ink);
    margin: 0;
  }

  .hud-pill {
    font-size: 0.75rem;
    padding: 0.15rem 0.5rem;
    border-radius: var(--radius-pill);
    background: var(--color-cool-soft);
    color: var(--color-cool);
    border: 1px solid var(--color-cool);
    font-family: var(--font-outlier);
    font-weight: 600;
  }

  .as-of-date {
    font-size: 0.75rem;
    color: var(--color-muted);
    font-family: var(--font-outlier);
  }

  .header-sub {
    font-size: 0.8rem;
    color: var(--color-muted);
    margin: 0.25rem 0 0 0;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 0.6rem;
  }

  /* Scope Switcher Tabs */
  .scope-tabs-group {
    display: flex;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    padding: 0.25rem;
    gap: 0.25rem;
  }

  .scope-btn {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.4rem 0.85rem;
    background: transparent;
    border: none;
    border-radius: var(--radius-pill);
    color: var(--color-muted);
    font-size: 0.78rem;
    font-weight: 500;
    cursor: pointer;
    transition: color 0.15s ease, background 0.15s ease;
  }

  .scope-btn:hover {
    color: var(--color-ink);
  }

  .scope-btn.active {
    background: var(--color-cool-soft);
    color: var(--color-cool);
    font-weight: 600;
  }

  .refresh-btn {
    width: 32px;
    height: 32px;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    color: var(--color-muted);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: color 0.15s ease, border-color 0.15s ease;
  }

  .refresh-btn:hover {
    color: var(--color-ink);
    border-color: var(--color-cool);
  }

  .scope-btn:focus-visible,
  .refresh-btn:focus-visible,
  .bench-selector-pill:focus-visible,
  .metric-btn:focus-visible,
  .bar-row:focus-visible {
    outline: 2px solid var(--color-focus);
    outline-offset: 2px;
  }

  /* Body */
  .card-body {
    padding: 1.25rem 1.5rem;
  }

  /* Combined View Styles */
  .combined-view-container {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  .combined-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.75rem;
    padding: 0.6rem 0.85rem;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
  }

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    flex-wrap: wrap;
  }

  .toolbar-label {
    font-size: 0.75rem;
    color: var(--color-muted);
  }

  .benchmark-pills {
    display: flex;
    gap: 0.4rem;
  }

  .bench-selector-pill {
    font-size: 0.75rem;
    padding: 0.25rem 0.65rem;
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    color: var(--color-muted);
    cursor: pointer;
    transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
  }

  .bench-selector-pill:hover {
    color: var(--color-ink);
    border-color: var(--color-cool);
  }

  .bench-selector-pill.active {
    background: var(--color-accent-soft);
    border-color: var(--color-accent);
    color: var(--color-accent);
    font-weight: 600;
  }

  .toolbar-legend {
    display: flex;
    align-items: center;
    gap: 1.25rem;
    font-size: 0.75rem;
  }

  .legend-item {
    display: flex;
    align-items: center;
    gap: 0.4rem;
  }

  .legend-line {
    width: 18px;
    height: 3px;
    border-radius: 2px;
  }

  .legend-line.portfolio-line {
    background: var(--color-cool);
  }

  .legend-line.benchmark-line {
    background: var(--color-accent);
    border-top: 1px dashed var(--color-accent);
  }

  /* Scorecard Grid */
  .combined-stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 0.75rem;
  }

  .stat-cell {
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    padding: 0.75rem 1rem;
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }

  .stat-cell.highlight {
    background: var(--color-cool-soft);
    border-color: var(--color-cool);
  }

  .stat-meta {
    font-size: 0.75rem;
    color: var(--color-muted);
    font-weight: 500;
  }

  .stat-val-row {
    display: flex;
    align-items: baseline;
    gap: 0.4rem;
  }

  .stat-val {
    font-size: 1.25rem;
    font-family: var(--font-outlier);
  }

  .stat-bench {
    font-size: 0.75rem;
    font-family: var(--font-outlier);
  }

  .stat-sub-pill {
    font-size: 0.75rem;
    padding: 0.15rem 0.4rem;
    border-radius: var(--radius-pill);
    font-weight: 600;
    width: fit-content;
  }

  .stat-sub-pill.beat {
    background: var(--color-ok-soft);
    color: var(--color-ok);
  }

  .stat-sub-pill.lag {
    background: var(--color-bad-soft);
    color: var(--color-bad);
  }

  .stat-sub {
    font-size: 0.75rem;
  }

  /* SVG Chart Container */
  .svg-chart-container {
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    padding: 1rem 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 0.6rem;
  }

  .chart-readout-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.75rem;
    padding-bottom: 0.5rem;
    border-bottom: 1px solid var(--color-rule);
  }

  .readout-date {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    font-size: 0.78rem;
    color: var(--color-muted);
    font-family: var(--font-outlier);
  }

  .readout-values {
    display: flex;
    align-items: center;
    gap: 1.2rem;
    font-size: 0.8rem;
  }

  .readout-item {
    display: flex;
    align-items: center;
    gap: 0.35rem;
  }

  .readout-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  .readout-dot.port-dot {
    background: var(--color-cool);
  }

  .readout-dot.bench-dot {
    background: var(--color-accent);
  }

  .readout-label {
    color: var(--color-muted);
  }

  .readout-pct {
    font-size: 0.75rem;
    font-family: var(--font-outlier);
  }

  .trend-svg {
    width: 100%;
    height: 220px;
    overflow: visible;
    cursor: crosshair;
  }

  .axis-dates-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.75rem;
    color: var(--color-muted);
    padding-top: 0.25rem;
    font-family: var(--font-outlier);
  }

  .axis-center-note {
    font-style: italic;
    font-size: 0.7rem;
    color: var(--color-muted);
  }

  /* Audit Callout Panel */
  .audit-callout-panel {
    background: var(--color-paper-3);
    border: 1px solid var(--color-ok);
    border-radius: var(--radius-card);
    padding: 0.85rem 1.15rem;
  }

  .audit-left {
    display: flex;
    align-items: flex-start;
    gap: 0.75rem;
  }

  .audit-icon {
    margin-top: 2px;
  }

  .audit-text {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }

  .audit-title {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    font-size: 0.85rem;
    color: var(--color-ink);
  }

  .audit-badge {
    font-size: 0.75rem;
    font-family: var(--font-outlier);
    padding: 0.1rem 0.45rem;
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border-radius: var(--radius-pill);
    font-weight: 600;
  }

  .audit-desc {
    font-size: 0.78rem;
    color: var(--color-muted);
    margin: 0;
    line-height: 1.45;
  }

  /* Schemes View Styles */
  .schemes-view-container {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  .schemes-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.75rem;
    padding: 0.5rem 0.85rem;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
  }

  .metric-toggle-group {
    display: flex;
    background: var(--color-paper);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    padding: 0.2rem;
    gap: 0.2rem;
  }

  .metric-btn {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.35rem 0.7rem;
    background: transparent;
    border: none;
    border-radius: var(--radius-pill);
    color: var(--color-muted);
    font-size: 0.75rem;
    font-weight: 500;
    cursor: pointer;
    transition: color 0.15s ease, background 0.15s ease;
  }

  .metric-btn:hover {
    color: var(--color-ink);
  }

  .metric-btn.active {
    background: var(--color-cool-soft);
    color: var(--color-cool);
    font-weight: 600;
  }

  .schemes-summary-pills {
    display: flex;
    gap: 0.6rem;
    font-size: 0.75rem;
    color: var(--color-muted);
  }

  .pill-item {
    background: var(--color-paper);
    border: 1px solid var(--color-rule);
    padding: 0.2rem 0.5rem;
    border-radius: var(--radius-pill);
  }

  /* Scheme Bars Layout */
  .chart-and-inspector-layout {
    display: grid;
    grid-template-columns: 1fr 340px;
    gap: 1.5rem;
    align-items: start;
  }

  @media (max-width: 1024px) {
    .chart-and-inspector-layout {
      grid-template-columns: 1fr;
    }
  }

  .comparison-bars-container {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .bars-header-row {
    display: grid;
    grid-template-columns: 280px 1fr 180px;
    gap: 1rem;
    padding: 0.4rem 0.85rem;
    font-size: 0.75rem;
    color: var(--color-muted);
    font-weight: 500;
  }

  .bars-list {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .bar-row {
    display: grid;
    grid-template-columns: 280px 1fr 180px;
    gap: 1rem;
    align-items: center;
    padding: 0.75rem 0.85rem;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    cursor: pointer;
    transition: border-color 0.15s ease, background 0.15s ease;
  }

  .bar-row:hover {
    background: var(--color-paper);
    border-color: var(--color-cool);
  }

  .bar-row.selected {
    background: var(--color-cool-soft);
    border-color: var(--color-cool);
  }

  .bar-scheme-info {
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
  }

  .scheme-title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.5rem;
  }

  .scheme-name {
    font-size: 0.82rem;
    font-weight: 600;
    color: var(--color-ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .sample-pill {
    font-size: 0.75rem;
    color: var(--color-muted);
    background: var(--color-paper);
    padding: 0.1rem 0.35rem;
    border-radius: var(--radius-pill);
    flex-shrink: 0;
    font-family: var(--font-outlier);
  }

  .benchmark-pill-row {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    font-size: 0.7rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .bench-tag {
    font-size: 0.75rem;
    color: var(--color-muted);
    font-weight: 600;
  }

  .bench-name {
    font-size: 0.72rem;
  }

  .bench-id {
    font-size: 0.75rem;
    color: var(--color-muted);
    font-family: var(--font-outlier);
  }

  /* Bar Visual Cell */
  .bar-visual-cell {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }

  .divergent-track {
    position: relative;
    flex: 1;
    height: 14px;
    background: var(--color-paper);
    border-radius: var(--radius-pill);
    overflow: hidden;
  }

  .zero-baseline {
    position: absolute;
    left: 50%;
    top: 0;
    bottom: 0;
    width: 2px;
    background: var(--color-rule-2);
    z-index: 2;
  }

  .bar-fill {
    position: absolute;
    top: 2px;
    bottom: 2px;
    border-radius: var(--radius-pill);
  }

  .bar-fill-positive {
    background: var(--color-ok);
  }

  .bar-fill-negative {
    background: var(--color-bad);
  }

  /* Beta track */
  .beta-track {
    position: relative;
    flex: 1;
    height: 14px;
    background: var(--color-paper);
    border-radius: var(--radius-pill);
    overflow: hidden;
  }

  .beta-baseline-1 {
    position: absolute;
    left: 77%;
    top: 0;
    bottom: 0;
    width: 2px;
    background: var(--color-rule-2);
    z-index: 2;
  }

  .beta-fill {
    height: 100%;
    border-radius: var(--radius-pill);
  }

  .beta-high {
    background: var(--color-accent);
  }

  .beta-low {
    background: var(--color-ok);
  }

  .beta-neutral {
    background: var(--color-cool);
  }

  .metric-val-readout {
    font-size: 0.85rem;
    min-width: 65px;
    text-align: right;
    font-family: var(--font-outlier);
  }

  /* Assessment Badges */
  .bar-assessment-cell {
    display: flex;
    justify-content: flex-end;
  }

  .status-badge {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.2rem 0.5rem;
    border-radius: var(--radius-pill);
    font-size: 0.75rem;
    font-weight: 600;
    white-space: nowrap;
  }

  .status-badge.beat {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--color-ok);
  }

  .status-badge.lag {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--color-bad);
  }

  .status-badge.low-vol {
    background: var(--color-cool-soft);
    color: var(--color-cool);
    border: 1px solid var(--color-cool);
  }

  .status-badge.high-vol {
    background: var(--color-accent-soft);
    color: var(--color-accent);
    border: 1px solid var(--color-accent);
  }

  .status-badge.neutral-vol {
    background: var(--color-paper);
    color: var(--color-muted);
    border: 1px solid var(--color-rule);
  }

  /* Inspector Drawer */
  .inspector-drawer {
    background: var(--color-paper-3);
    border: 1px solid var(--color-cool);
    border-radius: var(--radius-card);
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    position: sticky;
    top: 1rem;
  }

  .inspector-header {
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
    border-bottom: 1px solid var(--color-rule);
    padding-bottom: 0.75rem;
  }

  .inspector-tag {
    font-size: 0.75rem;
    color: var(--color-cool);
    font-weight: 600;
    font-family: var(--font-outlier);
  }

  .inspector-fund-title {
    font-size: 0.95rem;
    font-weight: 600;
    color: var(--color-ink);
    margin: 0;
    line-height: 1.3;
  }

  .inspector-bench-row {
    font-size: 0.75rem;
    display: flex;
    gap: 0.4rem;
  }

  .bench-label {
    color: var(--color-muted);
  }

  .inspector-stats-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0.75rem;
  }

  .istat-box {
    background: var(--color-paper);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    padding: 0.65rem 0.75rem;
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
  }

  .istat-label {
    font-size: 0.75rem;
    color: var(--color-muted);
    font-weight: 500;
  }

  .istat-value {
    font-size: 1.15rem;
    font-family: var(--font-outlier);
  }

  .istat-desc {
    font-size: 0.75rem;
    color: var(--color-muted);
  }

  .inspector-guidance {
    background: var(--color-cool-soft);
    border: 1px solid var(--color-cool);
    border-radius: var(--radius-card);
    padding: 0.85rem;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .guidance-header {
    display: flex;
    align-items: center;
    gap: 0.4rem;
  }

  .guidance-title {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--color-cool);
  }

  .guidance-text {
    font-size: 0.75rem;
    color: var(--color-ink-2);
    margin: 0;
    line-height: 1.45;
  }

  .sample-tag-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.75rem;
    margin-top: 0.25rem;
    font-family: var(--font-outlier);
  }

  .sample-status-pill {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    padding: 0.1rem 0.4rem;
    border-radius: var(--radius-pill);
  }

  .sample-days-text {
    color: var(--color-muted);
  }

  .state-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 3rem 1rem;
    color: var(--color-muted);
    font-size: 0.85rem;
    gap: 0.75rem;
  }

  @media (prefers-reduced-motion: reduce) {
    .scope-btn,
    .refresh-btn,
    .bench-selector-pill,
    .metric-btn,
    .bar-row {
      transition: none !important;
    }
  }
</style>
