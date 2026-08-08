import { API_BASE, fetchJson } from '../api.js';
import { state } from '../state.js';
import { formatINR } from '../utils.js';

export function updatePortfolioSummary(summary) {
  const netWorthVal = document.querySelector('.net-worth-val');
  const gainText = document.querySelector('.net-worth-gain');
  const subText = document.querySelector('.net-worth-sub');
  const xirrVal = document.querySelector('.xirr-val');

  const curVal = summary.total_current_value || summary.totalCurrentValue;
  const gainVal = summary.total_unrealized_gain || summary.totalUnrealizedGain;
  const countVal = summary.active_holding_count !== undefined ? summary.active_holding_count : summary.activeHoldingCount;
  const xirr = summary.xirr_percentage || summary.xirrPercentage;

  if (netWorthVal && curVal) {
    netWorthVal.textContent = formatINR(curVal);
    netWorthVal.classList.remove('skeleton');
  }
  if (gainText && gainVal) {
    const gain = Math.round(parseFloat(gainVal) || 0);
    const sign = gain >= 0 ? '+' : '';
    gainText.textContent = `Unrealized gain: ${sign}${formatINR(gain)}`;
    gainText.className = `metric-delta ${gain >= 0 ? 'positive' : 'negative'}`;
  }
  if (subText && countVal !== undefined) {
    subText.innerHTML = `Active Holdings: <strong>${countVal} Schemes</strong>`;
  }
  if (xirrVal && xirr) {
    xirrVal.textContent = xirr;
    xirrVal.classList.remove('skeleton');
  }
}

export function renderHoldingsTable(holdings) {
  const tableBody = document.querySelector('#holdingsTable tbody');
  if (!tableBody) return;

  if (!holdings || holdings.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:#64748b;">No open holdings found in ledger.</td></tr>`;
    return;
  }

  const fragment = document.createDocumentFragment();
  const template = document.createElement('template');

  let html = '';
  holdings.forEach((h, idx) => {
    const assetName = h.asset_name || h.assetName || '';
    const category = h.category || '';
    const inv = Math.round(parseFloat(h.invested_value || h.investedValue) || 0);
    const cur = Math.round(parseFloat(h.current_value || h.currentValue) || 0);
    const gain = Math.round(parseFloat(h.unrealized_gain || h.unrealizedGain) || 0);
    const gainPct = h.unrealized_gain_pct || h.unrealizedGainPct || '0.00';
    const allocPct = h.allocation_pct || h.allocationPct || '0.00';
    const lots = h.lots || [];

    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';

    const isSip = h.has_sip || h.hasSip || (lots && lots.some(l => (l.event_type || l.eventType) === 'SIP_INSTALMENT'));
    const sipBadge = isSip ? ' <span style="background:rgba(208,255,0,0.15); color:#d0ff00; border:1px solid rgba(208,255,0,0.3); font-size:10px; padding:2px 6px; border-radius:4px; margin-left:6px; font-weight:700;">🔄 Active SIP</span>' : '';

    html += `
      <tr class="holding-row" onclick="window.openHoldingDrawer && window.openHoldingDrawer(${idx})">
        <td style="font-weight:600;">${assetName}${sipBadge}</td>
        <td><span class="cat-badge cat-${category}">${category.replace('_SPECIFIED_50AA', '')}</span></td>
        <td class="font-mono">${formatINR(inv)}</td>
        <td class="font-mono" style="font-weight:600;">${formatINR(cur)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)} (${gainPct}%)</td>
        <td class="font-mono">${allocPct}%</td>
        <td><button class="pill-btn" onclick="event.stopPropagation(); window.openHoldingDrawer && window.openHoldingDrawer(${idx});">Inspect ➔</button></td>
      </tr>
    `;
  });

  template.innerHTML = html;
  fragment.appendChild(template.content);
  tableBody.replaceChildren(fragment);
}

window.toggleLotDetails = (idx) => {
  const row = document.getElementById(`lotRow-${idx}`);
  if (row) {
    row.style.display = row.style.display === 'none' ? 'table-row' : 'none';
  }
};

export function renderPieChart(containerId, data) {
  const container = document.getElementById(containerId);
  if (!container || !data || data.length === 0 || !window.echarts) return null;

  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}: ₹ {c} ({d}%)' },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { color: '#94a3b8', fontSize: 11 }
    },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['38%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#0c101c', borderWidth: 2 },
      label: { show: false },
      data: data
    }]
  };
  instance.setOption(option);
  return instance;
}

export function renderNetWorthTrendChart(containerId, dates, values) {
  const container = document.getElementById(containerId);
  if (!container || !dates || dates.length === 0 || !window.echarts) return null;

  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#090f1e' } },
      formatter: params => `${params[0].name}<br/>Valuation: <b>₹ ${formatINR(params[0].value)}</b>`
    },
    grid: { left: '3%', right: '4%', bottom: '18%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: v => `₹ ${(v/100000).toFixed(1)}L` }
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', start: 0, end: 100, height: 16, bottom: 0, borderColor: 'transparent', backgroundColor: 'rgba(255,255,255,0.05)', fillerColor: 'rgba(208,255,0,0.2)' }
    ],
    series: [{
      name: 'Net Worth',
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 3, color: '#d0ff00' },
      areaStyle: {
        color: new window.echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(208,255,0,0.35)' },
          { offset: 1, color: 'rgba(6,182,212,0.02)' }
        ])
      },
      data: values
    }]
  };
  instance.setOption(option);
  return instance;
}

export function renderAllocationChart(allocations) {
  if (state.charts.allocChart) state.charts.allocChart.dispose();
  if (!allocations || allocations.length === 0) return;

  const total = allocations.reduce((sum, a) => sum + (parseFloat(a.current_value || a.currentValue) || 0), 0);
  
  const main = [];
  let othersVal = 0;
  let othersCount = 0;

  allocations.forEach(a => {
    const val = parseFloat(a.current_value || a.currentValue) || 0;
    const assetName = a.asset_name || a.assetName || '';
    const pct = total > 0 ? (val / total) * 100 : 0;
    if (pct < 3.0 && allocations.length > 6) {
      othersVal += val;
      othersCount++;
    } else {
      main.push({
        name: assetName.length > 25 ? assetName.substring(0, 23) + '...' : assetName,
        value: val
      });
    }
  });

  if (othersVal > 0) {
    main.push({
      name: `Others (${othersCount})`,
      value: othersVal
    });
  }

  state.charts.allocChart = renderPieChart('allocationChart', main);
}

export function renderCategoryChart(catAllocations) {
  if (state.charts.categoryChart) state.charts.categoryChart.dispose();

  const data = catAllocations.map(c => ({
    name: c.category_name || c.categoryName,
    value: parseFloat(c.current_value || c.currentValue) || 0
  }));

  state.charts.categoryChart = renderPieChart('categoryChart', data);
}

export async function fetchConsolidationPreviewData() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/consolidation-preview?fy=${state.currentFy}`).catch(() => null);
    if (data) {
      renderConsolidationPlan(data);
    }
  } catch (e) {
    console.error('Error fetching consolidation preview:', e);
  }
}

export function renderConsolidationPlan(data) {
  const container = document.getElementById('consolidationPlanContainer');
  const badge = document.getElementById('consolidationWindowBadge');
  if (!container) return;

  const isWindowOpen = data.is_rebalance_window_open !== undefined ? data.is_rebalance_window_open : data.isRebalanceWindowOpen;
  const nextWindow = data.next_scheduled_window || data.nextScheduledWindow;
  const totalProceeds = data.total_proceeds || data.totalProceeds;
  const totalTaxDrag = data.total_tax_drag || data.totalTaxDrag;
  const proRata = data.pro_rata_allocations || data.proRataAllocations || [];

  if (badge) {
    badge.textContent = isWindowOpen ? 'WINDOW OPEN: EXECUTE REBALANCE' : `SCHEDULED WINDOW: ${nextWindow}`;
    badge.style.color = isWindowOpen ? '#10b981' : '#06b6d4';
  }

  const parsedProceeds = parseFloat(totalProceeds);
  const proceeds = (!isNaN(parsedProceeds)) ? Math.round(parsedProceeds) : 0;
  const taxDrag = Math.round(parseFloat(totalTaxDrag) || 0);

  let html = `
    <div style="margin-bottom:12px; font-size:13px;" class="font-mono">
      Unlocked Capital: <strong style="color:#06b6d4;">${formatINR(proceeds)}</strong> | 
      Estimated Tax Drag: <strong style="color:#f59e0b;">${formatINR(taxDrag)}</strong>
    </div>
    <table class="data-table" style="font-size:12px;">
      <thead>
        <tr>
          <th>Active 6-Fund Core Asset</th>
          <th>SIP Target %</th>
          <th>Pro-Rata Deployment Amount</th>
        </tr>
      </thead>
      <tbody>
  `;

  for (const alloc of proRata) {
    const assetName = alloc.asset_name || alloc.assetName;
    const weightPct = alloc.sip_weight_pct || alloc.sipWeightPct;
    const deployAmt = alloc.deployment_amount || alloc.deploymentAmount;

    const amt = Math.round(parseFloat(deployAmt) || 0);
    html += `
      <tr>
        <td style="font-weight:600;">${assetName}</td>
        <td><span class="days-badge">${weightPct}%</span></td>
        <td class="font-mono" style="font-weight:600; color:#10b981;">${formatINR(amt)}</td>
      </tr>
    `;
  }

  html += `</tbody></table>`;
  container.innerHTML = html;
}

export async function fetchRebalancePreview(amount = 100000) {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/rebalance-preview?amount=${amount}&fy=${state.currentFy}`).catch(() => null);
    if (data) {
      updateRebalanceSummary(data);
    }
  } catch (e) {
    console.error('Error fetching rebalance preview:', e);
  }
}

export function updateRebalanceSummary(data) {
  const rebTaxDrag = document.getElementById('rebTaxDrag');
  const rebEffRate = document.getElementById('rebEffRate');
  const rebLtcgHarvested = document.getElementById('rebLtcgHarvested');

  const taxDrag = data.total_tax_drag || data.totalTaxDrag;
  const effRate = data.effective_tax_rate_pct || data.effectiveTaxRatePct;
  const ltcgHarv = data.ltcg_exemption_harvested || data.ltcgExemptionHarvested;

  if (rebTaxDrag && taxDrag) {
    rebTaxDrag.textContent = formatINR(taxDrag);
  }
  if (rebEffRate && effRate) {
    rebEffRate.textContent = effRate;
  }
  if (rebLtcgHarvested && ltcgHarv) {
    rebLtcgHarvested.textContent = formatINR(ltcgHarv);
  }
}

export async function fetchGoalSummary() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/goals`).catch(() => null);
    if (data) {
      renderGoalSummary(data);
    }
  } catch (e) {
    console.error('Goal summary error:', e);
  }
}

export function renderGoalSummary(data) {
  const idleVal = document.querySelector('.idle-cash-val');
  const unallocCash = data.unallocated_cash || data.unallocatedCash;
  if (idleVal && unallocCash) {
    idleVal.textContent = formatINR(unallocCash);
    idleVal.classList.remove('skeleton');
  }
}

export async function fetchFireSummary() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/fire`).catch(() => null);
    if (data) {
      renderFireSummary(data);
    }
  } catch (e) {
    console.error('FIRE summary error:', e);
  }
}

export function renderFireSummary(data) {
  const statusPill = document.getElementById('fireStatusPill');
  const scenarioLabel = document.getElementById('fireScenarioLabel');
  const investableNw = document.getElementById('fireInvestableNw');
  const reqCorpus = document.getElementById('fireRequiredCorpus');
  const projCorpus = document.getElementById('fireProjectedCorpus');

  const status = data.status;
  const shortage = data.shortage_or_surplus_amount || data.shortageOrSurplusAmount;
  const activeLabel = data.active_scenario_label || data.activeScenarioLabel;
  const fireInvestable = data.fire_investable_net_worth || data.fireInvestableNetWorth;
  const requiredCorpus = data.required_corpus || data.requiredCorpus;
  const projectedCorpus = data.projected_corpus_at_target_age || data.projectedCorpusAtTargetAge;

  if (statusPill) {
    statusPill.textContent = status === 'ON_TRACK' ? 'ON TRACK' : `SHORT BY ${formatINR(shortage)}`;
    statusPill.className = `fire-status-pill ${status === 'ON_TRACK' ? 'on-track' : 'short'}`;
  }

  if (scenarioLabel) scenarioLabel.textContent = `Scenario: ${activeLabel}`;

  if (investableNw) investableNw.textContent = formatINR(fireInvestable);
  if (reqCorpus) reqCorpus.textContent = `₹ ${(parseFloat(requiredCorpus) / 10000000).toFixed(2)} Cr`;
  if (projCorpus) projCorpus.textContent = `₹ ${(parseFloat(projectedCorpus) / 10000000).toFixed(2)} Cr`;

  const mcSuccess = data.monte_carlo_success_rate_pct !== undefined ? data.monte_carlo_success_rate_pct : data.monteCarloSuccessRatePct;
  const mcP10 = data.monte_carlo_tenth_percentile_corpus || data.monteCarloTenthPercentileCorpus;
  const dsLabel = data.monte_carlo_data_source_label || data.monteCarloDataSourceLabel || 'Nifty 50 Historical Return Model (Cold Start)';
  const isSynthetic = (data.monte_carlo_data_source || data.monteCarloDataSource) === 'SYNTHETIC_MARKET_BENCHMARK';

  const mcCard = document.getElementById('fireMonteCarloCard');
  if (mcCard && mcSuccess !== undefined) {
    const p10Cr = mcP10 ? (parseFloat(mcP10) / 10000000).toFixed(2) : '0.00';
    mcCard.innerHTML = `
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
        <div style="font-size: 11px; font-weight: 600; color: #a855f7; text-transform: uppercase; letter-spacing: 0.05em;">10,000-Path Monte Carlo SORR Simulation</div>
        <span style="font-size: 10px; padding: 2px 8px; border-radius: 12px; background: ${isSynthetic ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)'}; color: ${isSynthetic ? '#f59e0b' : '#10b981'}; font-weight: 500;">
          ${dsLabel}
        </span>
      </div>
      <div style="display: flex; gap: 16px; align-items: center;">
        <div>
          <span style="font-size: 18px; font-weight: 700; color: #d0ff00;">${mcSuccess}%</span>
          <span style="font-size: 11px; color: #94a3b8;"> Success Rate</span>
        </div>
        <div style="border-left: 1px solid rgba(255,255,255,0.1); padding-left: 16px;">
          <span style="font-size: 14px; font-weight: 600; color: #f59e0b;">₹ ${p10Cr} Cr</span>
          <span style="font-size: 11px; color: #94a3b8;"> (10th Percentile Floor)</span>
        </div>
      </div>
    `;
  }
}

export async function fetchBucketRebalance() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/buckets/rebalance`).catch(() => null);
    if (data) {
      renderBucketRebalance(data);
    }
  } catch (e) {
    console.error('Bucket rebalance error:', e);
  }
}

export function renderBucketRebalance(data) {
  const drawdownTag = document.getElementById('drawdownTag');
  const bucketGrid = document.getElementById('bucketGrid');

  const dd = data.drawdown_status || data.drawdownStatus;
  const statuses = data.bucket_statuses || data.bucketStatuses;

  if (drawdownTag && dd) {
    const bmName = dd.benchmark_name || dd.benchmarkName;
    const ddPct = dd.drawdown_pct || dd.drawdownPct;
    drawdownTag.textContent = `${bmName}: ${ddPct}% Drawdown`;
  }

  if (bucketGrid && statuses) {
    let html = '';
    statuses.forEach(b => {
      const isDrifted = b.is_drifted !== undefined ? b.is_drifted : b.isDrifted;
      const driftPct = b.drift_pct || b.driftPct;
      const curVal = b.current_value || b.currentValue;
      const curPct = b.current_pct || b.currentPct;
      const targetPct = b.target_pct || b.targetPct;

      const nameFmt = b.bucket.replace('_', ' ');
      html += `
        <div class="bucket-card ${isDrifted ? 'drifted' : ''}">
          <div class="bucket-card-header">
            <span class="bucket-name">${nameFmt}</span>
            <span class="drift-badge ${isDrifted ? 'warn' : 'ok'}">${isDrifted ? 'Drift: ' + driftPct + '%' : 'Target OK'}</span>
          </div>
          <div class="font-mono" style="font-size:16px; font-weight:700;">${formatINR(curVal)}</div>
          <div class="text-muted" style="font-size:11px; margin-top:4px;">Current: ${curPct}% · Target: ${targetPct}%</div>
        </div>
      `;
    });
    bucketGrid.innerHTML = html;
  }
}

export function renderCashflowSankey(containerId, holdingsData, bucketData) {
  const container = document.getElementById(containerId);
  if (!container || !window.echarts) return null;

  let totalEquity = 0;
  let totalLiquid = 0;
  let totalGold = 0;
  let totalTaxDrag = 0;

  if (holdingsData && holdingsData.length > 0) {
    holdingsData.forEach(h => {
      const cur = parseFloat(h.current_value || h.currentValue) || 0;
      const cat = h.category || '';
      if (cat === 'EQUITY') totalEquity += cur;
      else if (cat === 'GOLD_SILVER' || cat === 'SGB') totalGold += cur;
      else totalLiquid += cur;
    });
  }

  if (bucketData && bucketData.recommendations) {
    bucketData.recommendations.forEach(r => {
      totalTaxDrag += parseFloat(r.estimated_tax_drag || r.estimatedTaxDrag) || 0;
    });
  }

  if (totalEquity === 0 && totalLiquid === 0 && totalGold === 0) {
    totalEquity = 1250000;
    totalLiquid = 350000;
    totalGold = 175000;
  }

  const netEquity = Math.max(0, totalEquity - totalTaxDrag);

  const nodes = [
    { name: 'Portfolio Capital' },
    { name: 'Equity Core' },
    { name: 'Liquid Buffer' },
    { name: 'Gold & Commodities' },
    { name: 'Net Core Wealth' },
    { name: 'Est Tax Liability' },
    { name: 'Emergency Cash' }
  ];

  const links = [
    { source: 'Portfolio Capital', target: 'Equity Core', value: totalEquity },
    { source: 'Portfolio Capital', target: 'Liquid Buffer', value: totalLiquid },
    { source: 'Portfolio Capital', target: 'Gold & Commodities', value: totalGold },
    { source: 'Equity Core', target: 'Net Core Wealth', value: netEquity },
    { source: 'Equity Core', target: 'Est Tax Liability', value: Math.max(10, totalTaxDrag) },
    { source: 'Liquid Buffer', target: 'Emergency Cash', value: totalLiquid }
  ];

  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [
      {
        type: 'sankey',
        data: nodes,
        links: links,
        emphasis: { focus: 'adjacency' },
        lineStyle: { color: 'gradient', curveness: 0.5, opacity: 0.45 },
        label: { color: '#f8fafc', fontFamily: 'Inter', fontSize: 11, fontWeight: 'bold' },
        itemStyle: { borderWidth: 1, borderColor: '#06b6d4' }
      }
    ]
  };
  instance.setOption(option);
  return instance;
}

export async function loadBenchmarkAnalytics() {
  try {
    const res = await fetchJson(`${API_BASE}/analytics/benchmark?benchmark=NIFTY_50_TRI`);
    if (res && res.status === 'OK') {
      const elAlpha = document.querySelector('#benchmarkAlphaVal');
      const elBeta = document.querySelector('#benchmarkBetaVal');
      const elSharpe = document.querySelector('#benchmarkSharpeVal');
      const elTracking = document.querySelector('#benchmarkTrackingVal');
      const elOut = document.querySelector('#benchmarkOutperformVal');
      const elBadge = document.querySelector('#benchmarkSampleBadge');
      const elSub = document.querySelector('#benchmarkProvenanceSub');

      const star = res.is_provisional ? '*' : '';
      if (elAlpha) elAlpha.textContent = `${res.alpha_pct > 0 ? '+' : ''}${res.alpha_pct}%${star}`;
      if (elBeta) elBeta.textContent = `${res.beta}${star}`;
      if (elSharpe) elSharpe.textContent = `${res.sharpe_ratio}${star}`;
      if (elTracking) elTracking.textContent = `${res.tracking_error_pct}%${star}`;
      if (elOut) elOut.textContent = `${res.outperformance_pct > 0 ? '+' : ''}${res.outperformance_pct}%${star}`;

      const cardGrid = document.querySelector('#benchmarkMetricsGrid');
      if (cardGrid) {
        cardGrid.style.opacity = res.is_provisional ? '0.82' : '1.0';
      }

      if (elBadge) {
        if (res.is_provisional) {
          elBadge.textContent = `PROVISIONAL (${res.sample_days} DAYS)`;
          elBadge.className = 'live-tag warning-tag';
        } else {
          elBadge.textContent = `MATURE (${res.sample_days} DAYS)`;
          elBadge.className = 'live-tag positive-tag';
        }
      }

      if (elSub && res.data_source_label) {
        elSub.textContent = res.data_source_label;
      }
    }
  } catch (err) {
    console.error('Failed to load benchmark analytics:', err);
  }
}

export async function loadOverlapAnalytics() {
  try {
    const res = await fetchJson(`${API_BASE}/analytics/overlap?fundA=INF109KC13X2&fundB=INF109KC12U0`);
    if (res && res.status === 'OK') {
      const pairwise = res.pairwise_overlap;
      const concentrations = res.portfolio_top_stock_concentrations;

      const elPairwise = document.querySelector('#pairwiseOverlapVal');
      const elCount = document.querySelector('#commonStockCountSub');
      const elBadge = document.querySelector('#overlapDateBadge');
      const tableBody = document.querySelector('#topStockConcentrationTable tbody');

      if (elPairwise && pairwise) {
        elPairwise.textContent = `${pairwise.overlap_percentage}%`;
      }

      if (elCount && pairwise && pairwise.common_stocks) {
        const topSymbols = pairwise.common_stocks.slice(0, 4).map(s => s.stock_symbol).join(', ');
        elCount.textContent = `Common Holdings: ${pairwise.common_stock_count} Stocks (${topSymbols})`;
      }

      if (elBadge && pairwise) {
        if (pairwise.date_mismatch) {
          elBadge.textContent = 'DATE MISMATCH';
          elBadge.className = 'live-tag warning-tag';
        } else {
          elBadge.textContent = 'SNAPSHOT ALIGNED';
          elBadge.className = 'live-tag positive-tag';
        }
      }

      if (tableBody && concentrations) {
        if (concentrations.length === 0) {
          tableBody.innerHTML = `<tr><td colspan="3" style="text-align:center; color:#64748b;">No stock concentrations calculated.</td></tr>`;
        } else {
          let html = '';
          concentrations.forEach(item => {
            html += `<tr>
              <td><strong>${item.stock_symbol}</strong></td>
              <td>${formatINR(item.rupee_exposure)}</td>
              <td><span class="metric-delta positive">${item.portfolio_percentage}%</span></td>
            </tr>`;
          });
          tableBody.innerHTML = html;
        }
      }
    }

    await loadUpSetAnalytics();
  } catch (err) {
    console.error('Failed to load overlap analytics:', err);
  }
}

export async function loadUpSetAnalytics() {
  const container = document.querySelector('#upsetContainer');
  if (!container) return;

  try {
    const res = await fetchJson(`${API_BASE}/analytics/overlap/upset`);
    if (res && res.status === 'OK' && res.upset_combinations) {
      const combos = res.upset_combinations;
      const fundMap = {
        'INF109KC12U0': 'LargeMidcap 250',
        'INF109KC13X2': 'Value 30',
        'INF174KA1TY2': '100 Equal Weight',
        'INF247L01916': 'Midcap 150',
        'INF247L01BQ9': 'Momentum Quality 50'
      };
      const allFundKeys = Object.keys(fundMap);

      if (combos.length === 0) {
        container.innerHTML = `<div style="text-align:center; color:#64748b;">No multi-set intersections found.</div>`;
        return;
      }

      const maxCount = Math.max(...combos.map(c => c.stock_count));

      let html = `<div style="display: flex; gap: 20px; font-family: monospace; font-size: 0.78rem;">`;
      html += `<div style="display: flex; flex-direction: column; justify-content: flex-end; gap: 8px; font-weight: 600; color: #94a3b8; padding-bottom: 22px;">`;
      allFundKeys.forEach(key => {
        html += `<div style="height: 18px; line-height: 18px; text-align: right; white-space: nowrap;">${fundMap[key]}</div>`;
      });
      html += `</div>`;

      html += `<div style="display: flex; gap: 14px; overflow-x: auto; padding-bottom: 6px;">`;

      combos.forEach(c => {
        const participating = c.participating_funds;
        const participatingNames = participating.map(k => fundMap[k] || k);
        const stockList = c.stocks.map(s => s.stock_symbol).join(', ');

        const barPct = Math.round((c.stock_count / maxCount) * 100);

        html += `<div style="display: flex; flex-direction: column; items: center; min-width: 55px;" title="Intersection Set: [${participatingNames.join(' + ')}]\nShared Stocks (${c.stock_count}): ${stockList}\nWeighted Overlap: ${c.total_overlap_weight}%">`;

        html += `<div style="height: 60px; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; margin-bottom: 8px; width: 100%;">`;
        html += `<span style="font-size: 0.72rem; color: #38bdf8; font-weight: bold; margin-bottom: 2px;">${c.stock_count}</span>`;
        html += `<div style="width: 14px; height: ${Math.max(barPct * 0.45, 4)}px; background: linear-gradient(180deg, #38bdf8, #0284c7); border-radius: 3px;"></div>`;
        html += `</div>`;

        html += `<div style="display: flex; flex-direction: column; gap: 8px; align-items: center;">`;
        allFundKeys.forEach(fKey => {
          const isActive = participating.includes(fKey);
          if (isActive) {
            html += `<div style="width: 18px; height: 18px; border-radius: 50%; background: #38bdf8; box-shadow: 0 0 6px rgba(56, 189, 248, 0.6);"></div>`;
          } else {
            html += `<div style="width: 18px; height: 18px; border-radius: 50%; background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12);"></div>`;
          }
        });
        html += `</div>`;

        html += `<div style="font-size: 0.65rem; color: #64748b; margin-top: 6px; text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 60px;">${c.total_overlap_weight}%</div>`;
        html += `</div>`;
      });

      html += `</div></div>`;
      container.innerHTML = html;
    }
  } catch (err) {
    console.error('Failed to load UpSet analytics:', err);
  }
}

