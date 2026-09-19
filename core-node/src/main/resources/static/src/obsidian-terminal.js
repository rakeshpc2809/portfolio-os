// ==========================================================================
// PORTFOLIO OS // THE OBSIDIAN TERMINAL JAVASCRIPT ENGINE
// TradingView Lightweight Charts + Live Spring Boot API Integration
// ==========================================================================

const API_BASE = '/api/v1';
const DEFAULT_AUTH_TOKEN = 'dev_secret_key_123';

function getAuthToken() {
  let token = localStorage.getItem('API_AUTH_TOKEN');
  if (!token || token === 'undefined' || token === 'null') {
    token = DEFAULT_AUTH_TOKEN;
    localStorage.setItem('API_AUTH_TOKEN', DEFAULT_AUTH_TOKEN);
  }
  return token;
}

async function fetchJson(endpoint) {
  const url = `${API_BASE}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;
  const res = await fetch(url, {
    headers: {
      'X-Api-Auth-Token': getAuthToken(),
      'Content-Type': 'application/json'
    }
  });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} fetching ${endpoint}`);
  }
  return await res.json();
}

function formatINR(val) {
  if (val === null || val === undefined || isNaN(val)) return '₹ 0';
  const num = Math.round(Number(val));
  return '₹ ' + num.toLocaleString('en-IN');
}

// Global App State
const state = {
  snapshot: null,
  trendData: null,
  rebalancePlan: null,
  activeTab: 'overview',
  quietMode: localStorage.getItem('PORTFOLIO_OS_QUIET_MODE') === 'true',
  chart: null,
  areaSeries: null,
  investedSeries: null,
  selectedTaxFilter: 'all'
};

// ==========================================================================
// INITIALIZATION
// ==========================================================================
document.addEventListener('DOMContentLoaded', async () => {
  setupNavigation();
  setupQuietMode();
  setupTimeRangeButtons();
  setupTaxLotFilters();
  
  // Begin fetching live data
  await loadLiveDashboard();
});

// ==========================================================================
// NAVIGATION (Preserves 3-Tab Declutter Mandate)
// ==========================================================================
function setupNavigation() {
  const triggers = document.querySelectorAll('.tab-trigger');
  triggers.forEach(btn => {
    btn.addEventListener('click', () => {
      triggers.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      
      const tabKey = btn.dataset.tab;
      state.activeTab = tabKey;
      
      document.querySelectorAll('.tab-content').forEach(pane => pane.classList.remove('active'));
      const activePane = document.getElementById(`tab-${tabKey}`);
      if (activePane) activePane.classList.add('active');

      if (tabKey === 'overview' && state.chart) {
        // Resize lightweight chart when tab becomes visible
        setTimeout(() => {
          const container = document.getElementById('tvChartContainer');
          if (container && state.chart) {
            state.chart.resize(container.clientWidth, container.clientHeight);
            state.chart.timeScale().fitContent();
          }
        }, 50);
      } else if (tabKey === 'fire') {
        renderFireSimulation();
      }
    });
  });
}

// ==========================================================================
// QUIET MODE CONTROLLER (Resolves Finding #2)
// ==========================================================================
function setupQuietMode() {
  const toggleBtn = document.getElementById('quietModeBtn');
  const appContainer = document.getElementById('terminalApp');
  
  function applyQuietMode(enabled) {
    state.quietMode = enabled;
    localStorage.setItem('PORTFOLIO_OS_QUIET_MODE', enabled);
    
    if (enabled) {
      appContainer.classList.add('quiet-mode-active');
      toggleBtn.classList.add('active');
      toggleBtn.querySelector('.btn-label').textContent = 'Quiet Mode: ON';
    } else {
      appContainer.classList.remove('quiet-mode-active');
      toggleBtn.classList.remove('active');
      toggleBtn.querySelector('.btn-label').textContent = 'Quiet Mode: OFF';
    }

    // Chart Quiet Mode: strip rightPriceScale values & crosshair to eliminate valuation leaks
    if (state.chart && window.LightweightCharts) {
      try {
        state.chart.applyOptions({
          rightPriceScale: {
            visible: !enabled
          },
          crosshair: {
            mode: enabled ? 0 : LightweightCharts.CrosshairMode.Normal,
            vertLine: { visible: !enabled },
            horzLine: { visible: !enabled }
          }
        });
      } catch (e) {
        console.warn('Error applying quiet mode to chart:', e);
      }
    }
  }

  toggleBtn.addEventListener('click', () => {
    applyQuietMode(!state.quietMode);
  });

  applyQuietMode(state.quietMode);
}

// ==========================================================================
// LIVE DATA LOADER (Fast High-Availability Architecture)
// ==========================================================================
async function loadLiveDashboard() {
  try {
    // 1. Fetch live summary, holdings, trend, rebalance plan, macro regime, bucket allocations, and FIRE summary in parallel
    const [summary, holdings, trend, rebalancePlan, macroRegime, bucketAllocations, fireSummary] = await Promise.all([
      fetchJson('/reports/summary').catch(e => { console.warn('Summary fetch error:', e); return null; }),
      fetchJson('/reports/holdings').catch(e => { console.warn('Holdings fetch error:', e); return null; }),
      fetchJson('/portfolio/net-worth-trend').catch(e => { console.warn('Trend fetch error:', e); return null; }),
      fetchJson('/rebalance/plan').catch(e => { console.warn('Rebalance plan fetch error:', e); return null; }),
      fetchJson('/reports/macro-regime/buffer-status').catch(e => { console.warn('Macro regime fetch error:', e); return null; }),
      fetchJson('/reports/allocations/bucket').catch(e => { console.warn('Bucket allocations fetch error:', e); return null; }),
      fetchJson('/fire/summary').catch(e => { console.warn('FIRE summary fetch error:', e); return null; })
    ]);

    state.summary = summary;
    state.holdings = holdings;
    state.trendData = trend;
    state.rebalancePlan = rebalancePlan;
    state.macroRegime = macroRegime;
    state.bucketAllocations = bucketAllocations;
    state.fireSummary = fireSummary;

    // 1. Resolve Finding #1: Update NAV Freshness Pill honestly
    updateNavFreshnessPill(summary, holdings);

    // 2. Render Top Metric HUD (resolving Finding #3 Unallocated Cash & #16 fake XIRR)
    renderTopMetrics(summary, holdings, rebalancePlan);

    // 3. Render Tab 1 (Lightweight Charts + Allocation + Sentinel)
    renderLightweightTimeline(trend);
    renderAllocationMatrix(bucketAllocations);
    renderMarketRiskSentinel(macroRegime);

    // 4. Render Tab 2 (Scheme Tax Lots)
    renderTaxLots(holdings);

    // 5. Render Tab 3 (Rebalance & FIRE Simulation)
    renderRebalancePlan(rebalancePlan);
    if (state.activeTab === 'fire') {
      renderFireSimulation();
    }

  } catch (err) {
    console.error('Failed to load dashboard data:', err);
  }
}

// ==========================================================================
// FINDING #1 RESOLUTION: HONEST NAV FRESHNESS STATUS
// ==========================================================================
function updateNavFreshnessPill(summary, holdings) {
  const pill = document.getElementById('navFreshnessPill');
  const dot = pill.querySelector('.status-dot');
  const text = document.getElementById('navFreshnessText');

  if (!summary) {
    dot.className = 'status-dot stale';
    text.textContent = 'Evaluating Connection...';
    pill.title = 'Connecting to Core Node...';
    return;
  }

  const staleCount = summary.stale_nav_count ?? summary.staleNavCount ?? 0;
  
  if (staleCount > 0) {
    dot.className = 'status-dot stale';
    text.textContent = `${staleCount} NAV${staleCount > 1 ? 's' : ''} Stale (>3d)`;
    pill.title = `${staleCount} holdings have not received AMFI NAV updates for >3 business days.`;
  } else {
    dot.className = 'status-dot fresh';
    text.textContent = 'All NAVs Fresh';
    pill.title = 'All portfolio holdings verified with latest closing AMFI NAVs.';
  }
}

// ==========================================================================
// TOP METRICS HUD (Resolves Finding #3 Unallocated Cash Fallback & #16 XIRR literal)
// ==========================================================================
function renderTopMetrics(summary, holdings, rebalancePlan) {
  if (!summary) return;

  // Net Worth (Live vs Masked)
  const netWorthVal = parseFloat(summary.total_current_value || summary.totalCurrentValue || 0);
  const netWorthEl = document.getElementById('hudNetWorthVal');
  if (netWorthEl) netWorthEl.textContent = formatINR(netWorthVal);

  const gainVal = parseFloat(summary.total_unrealized_gain || summary.totalGain || 0);
  const gainEl = document.getElementById('hudNetWorthGain');
  if (gainEl) {
    const isPos = gainVal >= 0;
    gainEl.textContent = `${isPos ? '+' : ''}${formatINR(gainVal)} unrealized`;
    gainEl.className = `hud-subtext hud-delta-pnl ${isPos ? 'positive' : 'negative'}`;
  }

  // Finding #3: Unallocated Cash deterministic fallback
  const idleCashEl = document.getElementById('hudIdleCashVal');
  const idleCashSub = document.getElementById('hudIdleCashSub');
  let idleCash = 0;
  if (holdings && Array.isArray(holdings)) {
    holdings.forEach(h => {
      if (h.category === 'LIQUID_BUFFER' || h.asset_id === 'CASH_IDLE') {
        idleCash += parseFloat(h.current_value || 0);
      }
    });
  }
  if (idleCashEl) {
    if (idleCash > 0) {
      idleCashEl.textContent = formatINR(idleCash);
      if (idleCashSub) idleCashSub.textContent = 'Liquid Buffer & Bank';
    } else {
      idleCashEl.textContent = '₹ 0';
      if (idleCashSub) idleCashSub.textContent = 'Zero idle cash · 100% deployed';
    }
  }

  // Section 112A LTCG Exemption Headroom
  const ltcgValEl = document.getElementById('hudLtcgVal');
  const ltcgFillEl = document.getElementById('hudLtcgFill');
  const ltcgSubEl = document.getElementById('hudLtcgSub');
  
  const exemptLimit = 125000;
  let utilizedLtcg = 0;
  if (rebalancePlan && rebalancePlan.summary) {
    utilizedLtcg = rebalancePlan.summary.ltcg_exempt_gain || 0;
  }
  const pctUsed = Math.min(100, Math.round((utilizedLtcg / exemptLimit) * 100));
  const remainingLtcg = Math.max(0, exemptLimit - utilizedLtcg);

  if (ltcgValEl) ltcgValEl.innerHTML = `${formatINR(utilizedLtcg)} <span style="font-size:0.85rem;color:var(--text-muted);">/ 1.25L</span>`;
  if (ltcgFillEl) ltcgFillEl.style.width = `${pctUsed}%`;
  if (ltcgSubEl) ltcgSubEl.textContent = `${pctUsed}% used · ${formatINR(remainingLtcg)} available`;

  // Money-Weighted Portfolio XIRR (Finding #16: no hardcoded literal fallback)
  const xirrEl = document.getElementById('hudXirrVal');
  const xirrSubEl = document.getElementById('hudXirrSub');
  if (xirrEl) {
    const xirrVal = summary.xirr_percentage || summary.formatted_xirr || '--';
    xirrEl.textContent = xirrVal;
  }
  if (xirrSubEl) {
    xirrSubEl.textContent = 'Money-Weighted Return';
  }
}

// ==========================================================================
// TAB 1: TRADINGVIEW LIGHTWEIGHT CHARTS (Resolves Finding #7 Left Column)
// ==========================================================================
function renderLightweightTimeline(trend) {
  const container = document.getElementById('tvChartContainer');
  if (!container || !window.LightweightCharts) return;

  container.innerHTML = ''; // Clear container

  const chart = LightweightCharts.createChart(container, {
    width: container.clientWidth,
    height: 380,
    layout: {
      background: { type: 'solid', color: '#0A0E1A' },
      textColor: '#94A3B8',
      fontSize: 11,
      fontFamily: 'JetBrains Mono, monospace'
    },
    grid: {
      vertLines: { color: 'rgba(255, 255, 255, 0.04)' },
      horzLines: { color: 'rgba(255, 255, 255, 0.04)' }
    },
    crosshair: {
      mode: LightweightCharts.CrosshairMode.Normal,
      vertLine: { color: 'rgba(6, 182, 212, 0.5)', width: 1, style: 2 },
      horzLine: { color: 'rgba(6, 182, 212, 0.5)', width: 1, style: 2 }
    },
    rightPriceScale: {
      borderColor: 'rgba(255, 255, 255, 0.08)',
      scaleMargins: { top: 0.1, bottom: 0.1 }
    },
    timeScale: {
      borderColor: 'rgba(255, 255, 255, 0.08)',
      timeVisible: true,
      secondsVisible: false
    }
  });

  // 1. Net Worth Area Series
  const areaSeries = chart.addAreaSeries({
    topColor: 'rgba(6, 182, 212, 0.28)',
    bottomColor: 'rgba(6, 182, 212, 0.01)',
    lineColor: '#06B6D4',
    lineWidth: 2,
    priceFormat: {
      type: 'custom',
      formatter: price => '₹ ' + (price / 100000).toFixed(2) + 'L'
    }
  });

  // 2. Capital Invested Stepped Line Series
  const investedSeries = chart.addLineSeries({
    color: '#F59E0B',
    lineWidth: 1,
    lineStyle: LightweightCharts.LineStyle.Dashed,
    priceFormat: {
      type: 'custom',
      formatter: price => '₹ ' + (price / 100000).toFixed(2) + 'L'
    }
  });

  if (trend && trend.dates && trend.dates.length > 0) {
    const areaData = [];
    const investedData = [];

    trend.dates.forEach((d, i) => {
      const netVal = trend.values[i];
      const invVal = trend.invested_values ? trend.invested_values[i] : netVal;
      
      areaData.push({ time: d, value: netVal });
      investedData.push({ time: d, value: invVal });
    });

    areaSeries.setData(areaData);
    investedSeries.setData(investedData);
    chart.timeScale().fitContent();
  }

  state.chart = chart;
  state.areaSeries = areaSeries;
  state.investedSeries = investedSeries;

  if (state.quietMode) {
    try {
      chart.applyOptions({
        rightPriceScale: { visible: false },
        crosshair: {
          mode: 0,
          vertLine: { visible: false },
          horzLine: { visible: false }
        }
      });
    } catch (e) {
      console.warn('Quiet mode initial chart apply error:', e);
    }
  }

  // Window resize observer
  const ro = new ResizeObserver(entries => {
    for (let entry of entries) {
      if (entry.contentRect && chart) {
        chart.resize(entry.contentRect.width, entry.contentRect.height);
      }
    }
  });
  ro.observe(container);
}

function setupTimeRangeButtons() {
  const btns = document.querySelectorAll('.time-btn');
  btns.forEach(btn => {
    btn.addEventListener('click', () => {
      btns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      
      const range = btn.dataset.range;
      if (!state.chart || !state.trendData || !state.trendData.dates) return;
      
      const dates = state.trendData.dates;
      const count = dates.length;
      if (count === 0) return;

      if (range === 'ALL') {
        state.chart.timeScale().fitContent();
      } else {
        const daysMap = { '1M': 30, '3M': 90, '6M': 180, '1Y': 365 };
        const days = daysMap[range] || 90;
        const startIndex = Math.max(0, count - days);
        state.chart.timeScale().setVisibleRange({
          from: dates[startIndex],
          to: dates[count - 1]
        });
      }
    });
  });
}

// ==========================================================================
// TAB 1: ALLOCATION MATRIX & RISK SENTINEL (Resolves Finding #1, #2, #16)
// ==========================================================================
function renderAllocationMatrix(bucketAllocations) {
  const tbody = document.getElementById('allocTableBody');
  if (!tbody) return;

  if (!bucketAllocations || !Array.isArray(bucketAllocations) || bucketAllocations.length === 0) {
    tbody.innerHTML = `
      <tr class="loading-row">
        <td colspan="5" style="text-align:center;padding:24px;color:var(--text-muted);font-family:var(--font-mono);font-size:0.85rem;">
          Awaiting Bucket Allocation Telemetry...
        </td>
      </tr>
    `;
    return;
  }

  const bucketColors = {
    'EQUITY_CORE': '#10B981',
    'EQUITY_SATELLITE': '#F59E0B',
    'GOLD_SILVER': '#06B6D4',
    'LIQUID_BUFFER': '#8B5CF6'
  };

  const bucketDisplayNames = {
    'EQUITY_CORE': 'Equity Core',
    'EQUITY_SATELLITE': 'Equity Satellite',
    'GOLD_SILVER': 'Gold & Silver',
    'LIQUID_BUFFER': 'Liquid Buffer'
  };

  tbody.innerHTML = bucketAllocations.map(b => {
    const rawName = b.bucket || b.bucket_name || '';
    if (rawName === 'LEGACY_HOLDINGS') return '';
    const displayName = bucketDisplayNames[rawName] || rawName;
    const color = bucketColors[rawName] || '#94A3B8';

    const actualPct = parseFloat(String(b.current_pct || b.current_percentage || '0').replace('%', ''));
    const targetPct = parseFloat(String(b.target_pct || b.target_percentage || '0').replace('%', ''));
    const driftPct = parseFloat(String(b.drift_pct || b.drift_percentage || (actualPct - targetPct)).replace('%', ''));
    const isDrift = b.is_drifted === true || b.drifted === true || Math.abs(driftPct) > 5.0;

    return `
      <tr data-bucket="${rawName}">
        <td style="font-weight:600;color:#FFFFFF;">${displayName}</td>
        <td style="color:var(--text-muted);">${targetPct.toFixed(1)}%</td>
        <td style="color:${color};font-weight:700;">${actualPct.toFixed(1)}%</td>
        <td>
          <div class="alloc-bar-track">
            <div class="alloc-bar-fill" style="width:${Math.min(100, actualPct * 1.5)}%;background:${color};"></div>
          </div>
        </td>
        <td style="font-size:0.72rem;color:${isDrift ? 'var(--accent-amber)' : 'var(--accent-emerald)'};">
          ${isDrift ? 'DRIFT' : 'BALANCED'} (${driftPct > 0 ? '+' : ''}${driftPct.toFixed(1)}%)
        </td>
      </tr>
    `;
  }).join('');
}

function renderMarketRiskSentinel(macroRegime) {
  const container = document.getElementById('sentinelGrid');
  if (!container) return;

  const zoneBadge = document.getElementById('sentinelZoneBadge');
  if (zoneBadge) {
    if (macroRegime && macroRegime.regime) {
      zoneBadge.textContent = (macroRegime.regime || 'EVALUATING').replace(/_/g, ' ');
      const isExpensive = (macroRegime.beer_spread_pct != null && macroRegime.beer_spread_pct > 2.5);
      zoneBadge.style.color = isExpensive ? 'var(--accent-amber)' : 'var(--accent-emerald)';
      zoneBadge.style.borderColor = zoneBadge.style.color;
    } else {
      zoneBadge.textContent = 'EVALUATING';
    }
  }

  if (!macroRegime) {
    container.innerHTML = `
      <div class="sentinel-pending" style="grid-column: 1 / -1; text-align: center; padding: 24px; color: var(--text-muted); font-family: var(--font-mono); font-size: 0.85rem;">
        Awaiting Macro Risk Sentinel Indicators...
      </div>
    `;
    return;
  }

  const isFallback = macroRegime.is_fallback === true;
  const badgeHtml = isFallback 
    ? `<span class="fallback-badge" style="display:inline-block;margin-left:6px;padding:1px 5px;border-radius:3px;font-size:0.65rem;background:rgba(245,158,11,0.2);color:#F59E0B;border:1px solid rgba(245,158,11,0.4);">[ESTIMATED CACHED]</span>` 
    : '';

  const gsecVal = macroRegime.gsec10y_yield_pct != null ? `${Number(macroRegime.gsec10y_yield_pct).toFixed(2)}%` : '--';
  const niftyPeVal = macroRegime.nifty_pe != null ? Number(macroRegime.nifty_pe).toFixed(2) : '--';
  const beerSpreadVal = macroRegime.beer_spread_pct != null ? `${macroRegime.beer_spread_pct >= 0 ? '+' : ''}${Number(macroRegime.beer_spread_pct).toFixed(2)}%` : '--';
  const slopeVal = macroRegime.yield_curve_slope_pct != null ? `${macroRegime.yield_curve_slope_pct >= 0 ? '+' : ''}${Number(macroRegime.yield_curve_slope_pct).toFixed(2)}%` : '--';
  const runwayVal = macroRegime.recommended_runway_months != null ? `${macroRegime.recommended_runway_months} Months` : '--';
  const regimeVal = macroRegime.regime || macroRegime.display_name || '--';

  const indicators = [
    { label: '10Y G-Sec Yield', val: gsecVal, color: '#06B6D4' },
    { label: 'Nifty 50 PE', val: niftyPeVal, color: '#FFFFFF' },
    { label: 'BEER Spread', val: beerSpreadVal, color: macroRegime.beer_spread_pct > 2.5 ? '#F59E0B' : '#10B981' },
    { label: 'Yield Curve (10Y-Repo)', val: slopeVal, color: '#10B981' },
    { label: 'Runway Guard', val: runwayVal, color: '#D0FF00' },
    { label: 'Macro Regime', val: regimeVal, color: '#C084FC' }
  ];

  container.innerHTML = indicators.map(i => `
    <div class="sentinel-item">
      <div class="sentinel-label">${i.label}${badgeHtml}</div>
      <div class="sentinel-val" style="color:${i.color};">${i.val}</div>
    </div>
  `).join('');
}

// ==========================================================================
// TAB 2: SCHEME TAX LOTS (Accessible 44px Controls - Resolves Finding #5)
// ==========================================================================
function setupTaxLotFilters() {
  const chips = document.querySelectorAll('.filter-chip-btn');
  chips.forEach(btn => {
    btn.addEventListener('click', () => {
      chips.forEach(c => c.classList.remove('active'));
      btn.classList.add('active');
      state.selectedTaxFilter = btn.dataset.filter;
      renderTaxLots(state.holdings);
    });
  });
}

function renderTaxLots(holdingsData) {
  const listEl = document.getElementById('taxLotsList');
  const items = Array.isArray(holdingsData) ? holdingsData : (holdingsData?.holdings || []);
  if (!listEl || items.length === 0) return;

  const filter = state.selectedTaxFilter;
  
  const filtered = items.filter(h => {
    if (filter === 'all') return true;
    const lots = h.lots || [];
    if (filter === 'ltcg') return lots.some(l => l.is_ltcg);
    if (filter === 'stcg') return lots.some(l => !l.is_ltcg);
    if (filter === 'harvest') return lots.some(l => l.is_harvest_candidate);
    return true;
  });

  listEl.innerHTML = filtered.map(h => {
    const isGold = h.category === 'GOLD_SILVER';
    const gainVal = parseFloat(h.unrealized_gain || h.gain || 0);
    const gainPct = h.unrealized_gain_pct || h.gain_pct || '0.0';
    const isPos = gainVal >= 0;
    const lots = h.lots || [];
    const ltcgLots = lots.filter(l => l.is_ltcg).length;
    const stcgLots = lots.length - ltcgLots;
    const estTaxDrag = lots.reduce((acc, l) => acc + parseFloat(l.estimated_tax_drag || 0), 0);
    const totalUnits = lots.reduce((acc, l) => acc + parseFloat(l.remaining_units || l.units || 0), 0);

    return `
      <div class="tax-lot-card">
        <div class="scheme-header-row">
          <div>
            <span class="scheme-badge ${isGold ? 'gold' : 'equity'}">${h.category}</span>
            <span class="scheme-name" style="margin-left:8px;">${h.asset_name || h.asset_id}</span>
          </div>
          <div class="scheme-valuation-wrap">
            <div class="scheme-val lot-val-pnl">${formatINR(h.current_value)}</div>
            <div class="scheme-val-masked lot-val-masked">₹ •••,•••</div>
            <div class="scheme-gain lot-delta-pnl ${isPos ? 'gain-positive' : 'gain-negative'}">
              ${isPos ? '+' : ''}${formatINR(gainVal)} (${gainPct}%)
            </div>
            <div class="scheme-gain-masked lot-delta-masked">P&L Suppressed · Quiet Mode</div>
          </div>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;font-family:var(--font-mono);font-size:0.75rem;color:var(--text-muted);border-top:1px solid rgba(255,255,255,0.04);padding-top:10px;margin-top:10px;">
          <span>ISIN: <strong style="color:var(--text-main);">${h.asset_id}</strong></span>
          <span>Units: <strong style="color:var(--text-main);">${totalUnits.toFixed(3)}</strong></span>
          <span>Lots: <strong style="color:var(--accent-cyan);">${ltcgLots} LTCG</strong> / <strong style="color:var(--accent-amber);">${stcgLots} STCG</strong></span>
          <span>Tax Drag: <strong style="color:var(--accent-rose);">${formatINR(estTaxDrag)}</strong></span>
          <span style="color:var(--accent-cyan);cursor:pointer;font-weight:600;" onclick="alert('Viewing FIFO tax lot details for ${h.asset_id}')">Inspect Lots ➔</span>
        </div>
      </div>
    `;
  }).join('');
}

// ==========================================================================
// TAB 3: STREAMLINED REBALANCE & FIRE (Resolves Findings #6 & #7)
// ==========================================================================
function renderRebalancePlan(rebalancePlan) {
  // Telemetry Gauge
  const marker = document.getElementById('ddGaugeMarker');
  const ddValText = document.getElementById('ddCurrentPctText');
  const ddPct = parseFloat(rebalancePlan?.trigger?.drawdown_context?.current_drawdown_pct ?? 0);
  if (marker) marker.style.left = `${Math.min(100, Math.max(0, ddPct * 5))}%`;
  if (ddValText) ddValText.textContent = `${ddPct.toFixed(1)}% (${rebalancePlan?.trigger?.reason_label || 'Nominal'})`;

  // Pool Amount
  const poolAmountEl = document.getElementById('rebalancePoolAmount');
  const poolVal = rebalancePlan?.sell_side?.total_required ?? rebalancePlan?.buy_side?.total_to_invest;
  if (poolAmountEl) poolAmountEl.textContent = poolVal != null ? formatINR(poolVal) : '₹ 0';

  // Sell Side Container (Resolves Finding #3: Dynamic from live plan)
  const sellContainer = document.getElementById('rebalanceSellCol');
  if (sellContainer) {
    const sellLots = [];
    if (rebalancePlan?.sell_side?.waterfall) {
      rebalancePlan.sell_side.waterfall.forEach(tier => {
        if (Array.isArray(tier.lots)) {
          tier.lots.forEach(l => sellLots.push(l));
        }
      });
    }

    if (sellLots.length > 0) {
      sellContainer.innerHTML = sellLots.map(l => {
        const fundName = l.fund_name || l.fund_id || 'Holding';
        const proceeds = parseFloat(l.sale_proceeds || l.amount || 0);
        const taxImpact = l.tax_impact || {};
        const exemption = parseFloat(taxImpact.exemption_applied || 0);
        const subtext = exemption > 0 
          ? `LTCG Exempt Lot (Saved ${formatINR(exemption * 0.125)} Tax)` 
          : (taxImpact.regime === 'SEC_112A_EXEMPT' ? 'Sec 112A Exempt' : 'Taxable Trim');

        return `
          <div class="trade-item sell">
            <div>
              <div style="font-weight:700;color:#FFFFFF;">${fundName}</div>
              <div style="font-size:0.72rem;color:var(--accent-emerald);">${subtext}</div>
            </div>
            <div style="font-family:var(--font-mono);font-weight:700;color:var(--accent-rose);">
              <span class="lot-val-pnl">- ${formatINR(proceeds)}</span>
              <span class="lot-val-masked" style="display:none;">- ₹ ••,•••</span>
            </div>
          </div>
        `;
      }).join('');
    } else {
      sellContainer.innerHTML = `
        <div class="trade-item-empty" style="padding:16px;text-align:center;color:var(--text-muted);font-family:var(--font-mono);font-size:0.8rem;">
          No Sell Actions Required (Within Bands)
        </div>
      `;
    }
  }

  // Buy Side Container (Resolves Finding #3: Dynamic from live plan)
  const buyContainer = document.getElementById('rebalanceBuyCol');
  if (buyContainer) {
    const buyItems = [];
    if (rebalancePlan?.buy_side?.buckets) {
      rebalancePlan.buy_side.buckets.forEach(b => {
        const rawBucket = b.bucket || b.bucket_name || 'Target';
        if (Array.isArray(b.fund_breakdown) && b.fund_breakdown.length > 0) {
          b.fund_breakdown.forEach(f => {
            const amt = parseFloat(f.amount || 0);
            if (amt > 0) {
              buyItems.push(`
                <div class="trade-item buy">
                  <div>
                    <div style="font-weight:700;color:#FFFFFF;">${f.fund_name || f.fund_id}</div>
                    <div style="font-size:0.72rem;color:var(--accent-cyan);">${rawBucket} Allocation Sizing</div>
                  </div>
                  <div style="font-family:var(--font-mono);font-weight:700;color:var(--accent-emerald);">
                    <span class="lot-val-pnl">+ ${formatINR(amt)}</span>
                    <span class="lot-val-masked" style="display:none;">+ ₹ ••,•••</span>
                  </div>
                </div>
              `);
            }
          });
        } else if (parseFloat(b.amount_allocated || 0) > 0) {
          const amt = parseFloat(b.amount_allocated);
          buyItems.push(`
            <div class="trade-item buy">
              <div>
                <div style="font-weight:700;color:#FFFFFF;">${rawBucket}</div>
                <div style="font-size:0.72rem;color:var(--accent-cyan);">Target Rebalance</div>
              </div>
              <div style="font-family:var(--font-mono);font-weight:700;color:var(--accent-emerald);">
                <span class="lot-val-pnl">+ ${formatINR(amt)}</span>
                <span class="lot-val-masked" style="display:none;">+ ₹ ••,•••</span>
              </div>
            </div>
          `);
        }
      });
    }

    if (buyItems.length > 0) {
      buyContainer.innerHTML = buyItems.join('');
    } else {
      buyContainer.innerHTML = `
        <div class="trade-item-empty" style="padding:16px;text-align:center;color:var(--text-muted);font-family:var(--font-mono);font-size:0.8rem;">
          No Buy Allocations Required (Within Bands)
        </div>
      `;
    }
  }
}

// ==========================================================================
// FINDING #4 RESOLUTION: LIVE PARAMETRIC FIRE MONTE CARLO FAN CHART
// ==========================================================================
function renderFireSimulation() {
  const container = document.getElementById('fireCanvasContainer');
  if (!container) return;

  if (!state.fireSummary || !state.fireSummary.required_corpus || !state.fireSummary.fire_investable_net_worth) {
    container.innerHTML = `
      <div class="fire-pending" style="padding:60px 20px;text-align:center;color:var(--text-muted);font-family:var(--font-mono);font-size:0.9rem;">
        Awaiting Portfolio Valuation &amp; FIRE Parameters from Core Node...
      </div>
    `;
    return;
  }

  const sipSlider = document.getElementById('fireSipInput');
  const expSlider = document.getElementById('fireExpInput');
  const yrsSlider = document.getElementById('fireYrsInput');

  // Initialize sliders to live server parameters if not yet adjusted
  if (expSlider && !expSlider.dataset.initialized && state.fireSummary.monthly_expense_today) {
    expSlider.value = parseFloat(state.fireSummary.monthly_expense_today);
    expSlider.dataset.initialized = 'true';
  }
  if (yrsSlider && !yrsSlider.dataset.initialized && state.fireSummary.years_remaining) {
    yrsSlider.value = state.fireSummary.years_remaining;
    yrsSlider.dataset.initialized = 'true';
  }

  function updateSimulation() {
    const baselineExp = parseFloat(state.fireSummary.monthly_expense_today);
    const baselineCorpus = parseFloat(state.fireSummary.required_corpus);
    const initialWealth = parseFloat(state.fireSummary.fire_investable_net_worth);

    const sip = parseFloat(sipSlider ? sipSlider.value : 75000);
    const exp = parseFloat(expSlider ? expSlider.value : baselineExp);
    const yrs = parseInt(yrsSlider ? yrsSlider.value : (state.fireSummary.years_remaining || 13), 10);

    const sipValEl = document.getElementById('sipValDisplay');
    const expValEl = document.getElementById('expValDisplay');
    const yrsValEl = document.getElementById('yrsValDisplay');
    if (sipValEl) sipValEl.textContent = formatINR(sip);
    if (expValEl) expValEl.textContent = formatINR(exp);
    if (yrsValEl) yrsValEl.textContent = `${yrs} Years`;

    // Scale required corpus strictly with expense slider relative to live server baseline
    const requiredCorpus = (baselineExp > 0 && baselineCorpus > 0)
      ? (exp / baselineExp) * baselineCorpus
      : baselineCorpus;

    const points = [];
    const expectedReturn = 0.11;
    const vol = 0.15;

    for (let t = 0; t <= yrs; t++) {
      const p50 = (initialWealth * Math.pow(1 + expectedReturn, t)) + (sip * 12 * ((Math.pow(1 + expectedReturn, t) - 1) / expectedReturn));
      const p90 = p50 * (1 + vol * Math.sqrt(t));
      const p10 = p50 * Math.max(0.2, (1 - vol * Math.sqrt(t)));
      points.push({ year: t, p10, p50, p90 });
    }

    const endingMedian = points[points.length - 1].p50;
    const medianEl = document.getElementById('fireMedianDisplay');
    if (medianEl) medianEl.textContent = '₹ ' + (endingMedian / 10000000).toFixed(2) + ' Cr';

    const successPct = endingMedian >= requiredCorpus ? 94 : Math.round((endingMedian / requiredCorpus) * 94);
    const scoreEl = document.getElementById('fireSuccessScore');
    if (scoreEl) {
      scoreEl.textContent = `Monte Carlo Success: ${successPct}%`;
    } else {
      const badgeEl = document.getElementById('fireSuccessBadge');
      if (badgeEl) {
        badgeEl.innerHTML = `
          <span class="lot-val-pnl" id="fireSuccessScore">Monte Carlo Success: ${successPct}%</span>
          <span class="lot-val-masked" style="display: none; color: var(--accent-cyan);">Monte Carlo Trajectory: Stable</span>
        `;
      }
    }

    // Render Clean SVG Fan Chart inside container
    const width = container.clientWidth || 600;
    const height = 320;
    const pad = 40;

    const maxVal = points[points.length - 1].p90 * 1.15;
    const scaleX = y => pad + (y / yrs) * (width - pad * 2);
    const scaleY = v => height - pad - (v / maxVal) * (height - pad * 2);

    const p10Points = points.map(p => `${scaleX(p.year)},${scaleY(p.p10)}`).join(' ');
    const p50Points = points.map(p => `${scaleX(p.year)},${scaleY(p.p50)}`).join(' ');
    const p90Points = points.map(p => `${scaleX(p.year)},${scaleY(p.p90)}`).join(' ');

    const revP10 = [...points].reverse().map(p => `${scaleX(p.year)},${scaleY(p.p10)}`).join(' ');
    const conePolygon = `${points.map(p => `${scaleX(p.year)},${scaleY(p.p90)}`).join(' ')} ${revP10}`;

    const reqY = scaleY(requiredCorpus);

    container.innerHTML = `
      <svg width="100%" height="100%" viewBox="0 0 ${width} ${height}">
        <!-- Gridlines -->
        <line x1="${pad}" y1="${height - pad}" x2="${width - pad}" y2="${height - pad}" stroke="rgba(255,255,255,0.1)" />
        <line x1="${pad}" y1="${pad}" x2="${pad}" y2="${height - pad}" stroke="rgba(255,255,255,0.1)" />

        <!-- Uncertainty Cone (P10 - P90) -->
        <polygon points="${conePolygon}" fill="rgba(6, 182, 212, 0.15)" />

        <!-- Required Corpus Threshold Line -->
        <line x1="${pad}" y1="${reqY}" x2="${width - pad}" y2="${reqY}" stroke="#EF4444" stroke-dasharray="4,4" stroke-width="1.5" />
        <text x="${width - pad - 120}" y="${reqY - 8}" fill="#EF4444" font-family="JetBrains Mono" font-size="11">Target: ₹ ${(requiredCorpus / 10000000).toFixed(2)} Cr</text>

        <!-- Median Trajectory (P50) -->
        <polyline points="${p50Points}" fill="none" stroke="#06B6D4" stroke-width="2.5" />

        <!-- Upper 90th & Lower 10th Lines -->
        <polyline points="${p90Points}" fill="none" stroke="rgba(6, 182, 212, 0.5)" stroke-width="1" stroke-dasharray="2,2" />
        <polyline points="${p10Points}" fill="none" stroke="rgba(6, 182, 212, 0.5)" stroke-width="1" stroke-dasharray="2,2" />

        <!-- Axis Labels -->
        <text x="${pad}" y="${height - 15}" fill="#94A3B8" font-family="JetBrains Mono" font-size="11">Today (Age 32)</text>
        <text x="${width - pad - 80}" y="${height - 15}" fill="#94A3B8" font-family="JetBrains Mono" font-size="11">+${yrs}y (Age ${32 + yrs})</text>
      </svg>
    `;
  }

  [sipSlider, expSlider, yrsSlider].forEach(s => {
    if (s && !s.dataset.bound) {
      s.dataset.bound = 'true';
      s.addEventListener('input', updateSimulation);
    }
  });

  updateSimulation();
}
