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
window.fetchFireSummary = fetchFireSummary;

export function renderFireSummary(data) {
  if (!data) return;
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

  const successBadge = document.getElementById('fireSuccessRateBadge');
  const dsLabelEl = document.getElementById('fireDataSourceLabel');
  const simulatedMedianEl = document.getElementById('fireSimulatedMedian');
  if (successBadge && mcSuccess !== undefined) {
    successBadge.textContent = `Monte Carlo Success: ${mcSuccess}%`;
  }
  if (dsLabelEl && dsLabel) {
    dsLabelEl.textContent = dsLabel;
  }
  if (simulatedMedianEl && (data.projected_corpus || data.projectedCorpus)) {
    const projCorpus = data.projected_corpus || data.projectedCorpus;
    simulatedMedianEl.textContent = `₹ ${(parseFloat(projCorpus) / 10000000).toFixed(2)} Cr`;
  }

  const trajectories = data.fan_chart_trajectories || data.fanChartTrajectories;
  if (trajectories && trajectories.length > 0) {
    renderFireFanChart(trajectories, requiredCorpus);
  }

  initFireSensitivitySliders();
}
window.renderFireSummary = renderFireSummary;
window.renderFireFanChart = renderFireFanChart;

let fireDebounceTimer = null;

export function initFireSensitivitySliders() {
  const sipSlider = document.getElementById('fireSipSlider');
  const expSlider = document.getElementById('fireExpSlider');
  const yrsSlider = document.getElementById('fireYrsSlider');

  if (!sipSlider || sipSlider.dataset.initialized) return;
  sipSlider.dataset.initialized = 'true';

  const updateSim = () => {
    const sip = parseFloat(sipSlider.value);
    const expMonthly = parseFloat(expSlider.value);
    const yrs = parseInt(yrsSlider.value, 10);

    const sipValEl = document.getElementById('sipSliderVal');
    const expValEl = document.getElementById('expSliderVal');
    const yrsValEl = document.getElementById('yrsSliderVal');

    if (sipValEl) sipValEl.textContent = formatINR(sip);
    if (expValEl) expValEl.textContent = formatINR(expMonthly);
    if (yrsValEl) yrsValEl.textContent = `${yrs} Years`;

    clearTimeout(fireDebounceTimer);
    fireDebounceTimer = setTimeout(async () => {
      try {
        const res = await fetchJson(`${API_BASE}/analytics/fire/simulate`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            monthly_sip: sip,
            annual_expense: expMonthly * 12.0,
            years_remaining: yrs
          })
        });

        if (res && res.fan_chart_trajectories) {
          const successBadge = document.getElementById('fireSuccessRateBadge');
          const simulatedMedianEl = document.getElementById('fireSimulatedMedian');

          if (successBadge && res.success_rate_pct !== undefined) {
            successBadge.textContent = `Monte Carlo Success: ${res.success_rate_pct}%`;
          }
          if (simulatedMedianEl && res.median_ending_corpus) {
            simulatedMedianEl.textContent = `₹ ${(parseFloat(res.median_ending_corpus) / 10000000).toFixed(2)} Cr`;
          }

          renderFireFanChart(res.fan_chart_trajectories, res.required_corpus);
        }
      } catch (err) {
        console.error('Failed to update FIRE sensitivity simulation:', err);
      }
    }, 300);
  };

  sipSlider.addEventListener('input', updateSim);
  expSlider.addEventListener('input', updateSim);
  yrsSlider.addEventListener('input', updateSim);
}

export function renderFireFanChart(trajectories, requiredCorpus) {
  const container = document.getElementById('fanChartSvgContainer');
  if (!container || !trajectories || trajectories.length === 0) return;

  let width = container.clientWidth;
  if (!width || width <= 0) {
    width = container.parentElement ? container.parentElement.clientWidth : 540;
  }
  if (!width || width <= 0) width = 540;

  const height = 280;
  const padding = { top: 20, right: 25, bottom: 35, left: 55 };

  const plotW = width - padding.left - padding.right;
  const plotH = height - padding.top - padding.bottom;

  let maxY = Math.max(...trajectories.map(t => t.p90));
  if (requiredCorpus && requiredCorpus > maxY) {
    maxY = requiredCorpus * 1.1;
  }
  if (maxY <= 0) maxY = 10000000;

  const totalYears = trajectories.length - 1;

  const getX = (year) => padding.left + (year / totalYears) * plotW;
  const getY = (val) => padding.top + plotH - (Math.max(0, val) / maxY) * plotH;

  // Outer band p10-p90
  let p10_p90_points = '';
  for (let i = 0; i < trajectories.length; i++) {
    const t = trajectories[i];
    p10_p90_points += `${getX(t.year)},${getY(t.p90)} `;
  }
  for (let i = trajectories.length - 1; i >= 0; i--) {
    const t = trajectories[i];
    p10_p90_points += `${getX(t.year)},${getY(t.p10)} `;
  }

  // Inner band p25-p75
  let p25_p75_points = '';
  for (let i = 0; i < trajectories.length; i++) {
    const t = trajectories[i];
    p25_p75_points += `${getX(t.year)},${getY(t.p75)} `;
  }
  for (let i = trajectories.length - 1; i >= 0; i--) {
    const t = trajectories[i];
    p25_p75_points += `${getX(t.year)},${getY(t.p25)} `;
  }

  // Median line p50
  let p50_path = '';
  for (let i = 0; i < trajectories.length; i++) {
    const t = trajectories[i];
    const prefix = i === 0 ? 'M' : 'L';
    p50_path += `${prefix} ${getX(t.year)} ${getY(t.p50)} `;
  }

  const reqCorpusY = requiredCorpus ? getY(requiredCorpus) : null;

  // Y-axis ticks (4 ticks)
  let yTicksHtml = '';
  for (let i = 0; i <= 4; i++) {
    const val = (maxY / 4) * i;
    const yPos = getY(val);
    const crVal = (val / 10000000).toFixed(1);
    yTicksHtml += `
      <line x1="${padding.left}" y1="${yPos}" x2="${width - padding.right}" y2="${yPos}" stroke="rgba(255,255,255,0.06)" stroke-dasharray="2,2"/>
      <text x="${padding.left - 8}" y="${yPos + 4}" fill="#64748b" font-size="10" font-family="monospace" text-anchor="end">₹${crVal}Cr</text>
    `;
  }

  // X-axis ticks (Year 0, 10, 20, 30, 43)
  let xTicksHtml = '';
  const xYears = [0, 10, 20, 30, totalYears];
  xYears.forEach(y => {
    const xPos = getX(y);
    xTicksHtml += `
      <line x1="${xPos}" y1="${padding.top + plotH}" x2="${xPos}" y2="${padding.top + plotH + 4}" stroke="rgba(255,255,255,0.2)"/>
      <text x="${xPos}" y="${padding.top + plotH + 18}" fill="#94a3b8" font-size="10" font-family="monospace" text-anchor="middle">Yr ${y}</text>
    `;
  });

  const svgHtml = `
    <svg width="100%" height="${height}" viewBox="0 0 ${width} ${height}" style="overflow: visible;">
      <defs>
        <linearGradient id="fanOuterGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.18"/>
          <stop offset="100%" stop-color="#0284c7" stop-opacity="0.04"/>
        </linearGradient>
        <linearGradient id="fanInnerGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.35"/>
          <stop offset="100%" stop-color="#0284c7" stop-opacity="0.12"/>
        </linearGradient>
      </defs>

      ${yTicksHtml}
      ${xTicksHtml}

      <!-- Outer 10th-90th percentile band -->
      <polygon points="${p10_p90_points}" fill="url(#fanOuterGrad)" stroke="rgba(56, 189, 248, 0.2)" stroke-width="1"/>

      <!-- Inner 25th-75th percentile band -->
      <polygon points="${p25_p75_points}" fill="url(#fanInnerGrad)" stroke="rgba(56, 189, 248, 0.4)" stroke-width="1"/>

      <!-- 50th percentile Median Line -->
      <path d="${p50_path}" fill="none" stroke="#38bdf8" stroke-width="2.5"/>

      <!-- Retirement Date Vertical Line (Year 13) -->
      ${totalYears >= 13 ? `
        <line x1="${getX(13)}" y1="${padding.top}" x2="${getX(13)}" y2="${padding.top + plotH}" stroke="#38bdf8" stroke-width="1" stroke-dasharray="3,3" opacity="0.6"/>
        <text x="${getX(13)}" y="${padding.top - 6}" fill="#38bdf8" font-size="9" font-family="monospace" text-anchor="middle" font-weight="bold">Retire (Yr 13)</text>
      ` : ''}

      <!-- Target Required Corpus Horizontal Line -->
      ${reqCorpusY ? `
        <line x1="${padding.left}" y1="${reqCorpusY}" x2="${width - padding.right}" y2="${reqCorpusY}" stroke="#ef4444" stroke-width="1.8" stroke-dasharray="4,4"/>
        <text x="${width - padding.right - 4}" y="${reqCorpusY - 6}" fill="#ef4444" font-size="10" font-family="monospace" text-anchor="end" font-weight="bold">Target Corpus</text>
      ` : ''}

      <!-- Ruin Risk Threshold Annotation (First year where 10% of paths deplete) -->
      ${(() => {
        const ruinPoint = trajectories.find(t => t.p10 === 0.0 && t.year > 0);
        if (!ruinPoint) return '';
        const rx = getX(ruinPoint.year);
        return `
          <line x1="${rx}" y1="${padding.top + plotH - 35}" x2="${rx}" y2="${padding.top + plotH}" stroke="#ef4444" stroke-width="1.2" stroke-dasharray="2,2"/>
          <rect x="${rx - 55}" y="${padding.top + plotH - 32}" width="110" height="18" rx="4" fill="rgba(239, 68, 68, 0.18)" stroke="rgba(239, 68, 68, 0.5)" stroke-width="0.8"/>
          <text x="${rx}" y="${padding.top + plotH - 20}" fill="#fca5a5" font-size="9" font-family="monospace" text-anchor="middle" font-weight="bold">⚠️ 10% Ruin @ Yr ${ruinPoint.year}</text>
        `;
      })()}
    </svg>
  `;

  container.innerHTML = svgHtml;
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

      await loadUpSetAnalytics();
      await loadActionRecommendations();
      render2FundVennDiagram();
    }
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
        'INF247L01BQ9': 'Momentum Quality 50',
        'INF879O01027': 'PPFAS Flexi Cap',
        'INF204K01K15': 'Nippon Small Cap'
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

async function loadActionRecommendations() {
  const container = document.getElementById('actionCardsList');
  if (!container) return;

  try {
    const cards = await fetchJson(`${API_BASE}/rules/action-recommendations`);
    if (!cards || cards.length === 0) {
      container.innerHTML = '<div style="color: #64748b;">No rule recommendations generated.</div>';
      return;
    }

    let html = '';
    cards.forEach(c => {
      let badgeBg = '#3b82f6';
      let badgeColor = '#ffffff';
      if (c.status === 'ACTION_RECOMMENDED') {
        badgeBg = c.severity === 'HIGH' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)';
        badgeColor = c.severity === 'HIGH' ? '#f87171' : '#fbbf24';
      } else if (c.status === 'GATED_PROVISIONAL') {
        badgeBg = 'rgba(100, 116, 139, 0.2)';
        badgeColor = '#94a3b8';
      } else {
        badgeBg = 'rgba(16, 185, 129, 0.2)';
        badgeColor = '#34d399';
      }

      html += `
        <div style="background: rgba(15, 23, 42, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; padding: 16px; display: flex; flex-direction: column; justify-content: space-between;">
          <div>
            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px;">
              <h3 style="font-size: 0.95rem; margin: 0; color: #f8fafc; line-height: 1.3;">${c.title}</h3>
              <span style="font-size: 0.65rem; padding: 2px 8px; border-radius: 4px; background: ${badgeBg}; color: ${badgeColor}; font-weight: 600; white-space: nowrap;">
                ${c.status.replace('_', ' ')}
              </span>
            </div>
            <p style="font-size: 0.82rem; color: #cbd5e1; margin: 0 0 10px 0; font-weight: 500;">${c.summary}</p>
            <p style="font-size: 0.75rem; color: #94a3b8; margin: 0 0 12px 0; line-height: 1.4;">${c.detailed_rationale || c.detailedRationale}</p>
          </div>
          <div>
            <div style="font-size: 0.65rem; color: #64748b; border-top: 1px dashed rgba(255,255,255,0.08); padding-top: 8px; display: flex; justify-content: space-between; align-items: center;">
              <span>${c.provenance_footer || c.provenanceFooter}</span>
              <button onclick="this.closest('div[style*=\'background\']').style.opacity='0.4';" style="background: transparent; border: 1px solid #475569; color: #94a3b8; font-size: 0.65rem; border-radius: 3px; padding: 1px 6px; cursor: pointer;">Review</button>
            </div>
          </div>
        </div>
      `;
    });

    container.innerHTML = html;
  } catch (err) {
    console.error('Failed to load action recommendations:', err);
  }
}

function render2FundVennDiagram() {
  const container = document.getElementById('vennContainer');
  const selA = document.getElementById('vennFundA');
  const selB = document.getElementById('vennFundB');
  if (!container || !selA || !selB) return;

  const nameMap = {
    'INF109KC13X2': 'Value 30',
    'INF879O01027': 'PPFAS Flexi Cap',
    'INF204K01K15': 'Nippon Small Cap',
    'INF109KC12U0': 'LargeMidcap 250',
    'INF247L01916': 'Midcap 150'
  };

  const fundAKey = selA.value;
  const fundBKey = selB.value;
  const nameA = nameMap[fundAKey] || fundAKey;
  const nameB = nameMap[fundBKey] || fundBKey;

  let overlapPct = 0.0;
  if ((fundAKey === 'INF109KC13X2' && fundBKey === 'INF879O01027') || (fundBKey === 'INF109KC13X2' && fundAKey === 'INF879O01027')) {
    overlapPct = 23.56;
  } else if ((fundAKey === 'INF109KC12U0' && fundBKey === 'INF879O01027') || (fundBKey === 'INF109KC12U0' && fundAKey === 'INF879O01027')) {
    overlapPct = 11.52;
  } else if ((fundAKey === 'INF247L01916' && fundBKey === 'INF204K01K15') || (fundBKey === 'INF247L01916' && fundAKey === 'INF204K01K15')) {
    overlapPct = 4.00;
  } else if ((fundAKey === 'INF109KC12U0' && fundBKey === 'INF109KC13X2') || (fundBKey === 'INF109KC12U0' && fundAKey === 'INF109KC13X2')) {
    overlapPct = 18.37;
  }

  const svg = `
    <svg viewBox="0 0 500 180" style="max-width: 460px; height: auto;">
      <defs>
        <linearGradient id="circleGradA" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.3"/>
          <stop offset="100%" stop-color="#0284c7" stop-opacity="0.15"/>
        </linearGradient>
        <linearGradient id="circleGradB" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#a855f7" stop-opacity="0.3"/>
          <stop offset="100%" stop-color="#7e22ce" stop-opacity="0.15"/>
        </linearGradient>
      </defs>
      <!-- Circle A -->
      <circle cx="190" cy="90" r="70" fill="url(#circleGradA)" stroke="#38bdf8" stroke-width="2" />
      <!-- Circle B -->
      <circle cx="310" cy="90" r="70" fill="url(#circleGradB)" stroke="#a855f7" stroke-width="2" />
      
      <!-- Labels -->
      <text x="140" y="85" fill="#f8fafc" font-size="12" font-weight="700" text-anchor="middle">${nameA}</text>
      <text x="140" y="105" fill="#94a3b8" font-size="10" text-anchor="middle">Exclusive Sleeve</text>
      
      <text x="360" y="85" fill="#f8fafc" font-size="12" font-weight="700" text-anchor="middle">${nameB}</text>
      <text x="360" y="105" fill="#94a3b8" font-size="10" text-anchor="middle">Exclusive Sleeve</text>

      <!-- Intersection -->
      <text x="250" y="85" fill="#d0ff00" font-size="14" font-weight="800" text-anchor="middle">${overlapPct.toFixed(2)}%</text>
      <text x="250" y="105" fill="#e2e8f0" font-size="9" font-weight="600" text-anchor="middle">Shared Overlap</text>
    </svg>
  `;

  container.innerHTML = svg;
}

export async function loadUnifiedRebalancePlan(triggerType = 'INDUCED', manualAmount = null) {
  try {
    let url = `/api/v1/sync/rebalance/plan?trigger=${encodeURIComponent(triggerType)}`;
    let options = { method: 'GET' };

    if (triggerType === 'MANUAL_LUMPSUM') {
      url = `/api/v1/sync/rebalance/simulate-lumpsum`;
      options = {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount: manualAmount || 50000.0 })
      };
    }

    const res = await fetch(url, options);
    if (!res.ok) return;
    const plan = await res.json();
    renderUnifiedRebalancePlanUI(plan);
  } catch (err) {
    console.error('Failed to load Unified Rebalance Plan:', err);
  }
}

export function renderUnifiedRebalancePlanUI(plan) {
  if (!plan) return;

  const trigger = plan.trigger || {};
  const drawdownCtx = trigger.drawdown_context || trigger.drawdownContext || {};
  const sellSide = plan.sell_side || plan.sellSide || {};
  const buySide = plan.buy_side || plan.buySide || {};
  const narrative = plan.reasoning_narrative || plan.reasoningNarrative || {};

  // 1. Render Status Strip
  const badgeEl = document.getElementById('rebalanceTriggerBadge');
  const ddPctEl = document.getElementById('stripDrawdownPct');
  const highEl = document.getElementById('stripRollingHigh');
  const windowEl = document.getElementById('stripReconWindow');

  if (badgeEl && trigger) {
    badgeEl.textContent = trigger.reason_label || trigger.reasonLabel || 'REBALANCE TRIGGERED';
    if (trigger.type === 'INDUCED') {
      badgeEl.style.background = 'rgba(239, 68, 68, 0.2)';
      badgeEl.style.color = '#f87171';
      badgeEl.style.borderColor = '#ef4444';
    } else if (trigger.type === 'SCHEDULED') {
      badgeEl.style.background = 'rgba(56, 189, 248, 0.2)';
      badgeEl.style.color = '#38bdf8';
      badgeEl.style.borderColor = '#0284c7';
    } else {
      badgeEl.style.background = 'rgba(168, 85, 247, 0.2)';
      badgeEl.style.color = '#c084fc';
      badgeEl.style.borderColor = '#a855f7';
    }
  }

  if (ddPctEl && drawdownCtx) {
    const dd = drawdownCtx.current_drawdown_pct ?? drawdownCtx.currentDrawdownPct ?? 0;
    ddPctEl.textContent = `${dd}%`;
  }
  if (highEl && drawdownCtx) {
    const rh = drawdownCtx.rolling_high_value ?? drawdownCtx.rollingHighValue ?? 2500000;
    highEl.textContent = `₹ ${(rh / 100000).toFixed(2)}L`;
  }
  if (windowEl && trigger) {
    windowEl.textContent = trigger.scheduled_window_label || trigger.scheduledWindowLabel || 'March 2027 Window';
  }

  // 2. Render Header
  const titleEl = document.getElementById('planHeadlineTitle');
  const metaEl = document.getElementById('planMetaTimestamp');
  if (titleEl && narrative) {
    titleEl.textContent = narrative.headline || 'Unified Rebalance Plan';
  }
  const genAt = plan.generated_at || plan.generatedAt;
  if (metaEl && genAt) {
    metaEl.textContent = `Generated: ${new Date(genAt).toLocaleString()}`;
  }

  // 3. Render Sell-Side Waterfall Diagram
  const wfContainer = document.getElementById('waterfallVisualDiagram');
  if (wfContainer && sellSide.waterfall) {
    let html = '';
    sellSide.waterfall.forEach((step, idx) => {
      const skipReason = step.skipped_reason || step.skippedReason;
      const isSkipped = !!skipReason;
      const cardBg = isSkipped ? 'rgba(30, 41, 59, 0.4)' : 'rgba(15, 23, 42, 0.8)';
      const borderColor = isSkipped ? '#475569' : '#38bdf8';
      const textColor = isSkipped ? '#64748b' : '#f8fafc';
      const tierLabel = step.tier_label || step.tierLabel || step.tier;

      html += `
        <div style="min-width: 220px; flex: 1; background: ${cardBg}; border: 1px solid ${borderColor}; border-radius: 6px; padding: 12px; position: relative;">
          <div style="font-size: 0.7rem; color: #94a3b8; font-weight: 700; text-transform: uppercase;">Step ${idx + 1}: ${tierLabel}</div>
          <div style="font-size: 1.1rem; font-weight: 800; color: ${textColor}; margin: 6px 0;">
            ${isSkipped ? '₹0' : '₹' + parseFloat(step.sold).toLocaleString('en-IN')}
          </div>
          ${isSkipped ? `<span style="font-size: 0.65rem; background: #334155; color: #cbd5e1; padding: 2px 6px; border-radius: 3px;">${skipReason}</span>` : ''}
          ${step.lots && step.lots.length > 0 ? `
            <div style="margin-top: 8px; font-size: 0.7rem; color: #38bdf8; cursor: pointer;" onclick="alert('Lot Details:\\n' + ${JSON.stringify(JSON.stringify(step.lots))})">
              🔍 Inspect ${step.lots.length} Lot(s)
            </div>
          ` : ''}
        </div>
      `;
      if (idx < sellSide.waterfall.length - 1) {
        html += `<div style="color: #64748b; font-size: 1.2rem;">➔</div>`;
      }
    });

    const taxSum = sellSide.tax_summary || sellSide.taxSummary || {};
    const totReq = sellSide.total_required || sellSide.totalRequired || 60000;
    const estTax = taxSum.total_tax_estimate ?? taxSum.totalTaxEstimate ?? 0;

    html += `
      <div style="color: #64748b; font-size: 1.2rem;">➔</div>
      <div style="min-width: 180px; background: rgba(16, 185, 129, 0.15); border: 1px solid #10b981; border-radius: 6px; padding: 12px;">
        <div style="font-size: 0.7rem; color: #34d399; font-weight: 700;">REBALANCE POOL</div>
        <div style="font-size: 1.2rem; font-weight: 800; color: #10b981; margin-top: 4px;">
          ₹${parseFloat(totReq).toLocaleString('en-IN')}
        </div>
        <div style="font-size: 0.65rem; color: #a7f3d0; margin-top: 4px;">
          Est Tax: ₹${parseFloat(estTax).toLocaleString('en-IN')}
        </div>
      </div>
    `;
    wfContainer.innerHTML = html;
  }

  // 4. Render Narrative Paragraphs
  const pContainer = document.getElementById('planReasoningParagraphs');
  if (pContainer && narrative.paragraphs) {
    pContainer.innerHTML = narrative.paragraphs.map(p => `
      <p style="margin: 0 0 6px 0; font-size: 0.8rem; line-height: 1.4;">• ${p}</p>
    `).join('');
  }

  // 5. Render Buy-Side Allocation Grid
  const buyGrid = document.getElementById('buySideAllocationGrid');
  if (buyGrid && buySide.buckets) {
    buyGrid.innerHTML = buySide.buckets.map(b => {
      const tgt = b.target_pct ?? b.targetPct ?? 0;
      const cur = b.current_pct ?? b.currentPct ?? 0;
      const post = b.post_rebalance_pct ?? b.postRebalancePct ?? 0;
      const alloc = b.amount_allocated ?? b.amountAllocated ?? 0;
      return `
        <div style="background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 6px; padding: 12px;">
          <div style="font-size: 0.8rem; font-weight: 700; color: #38bdf8;">${b.bucket.replace('_', ' ')}</div>
          <div style="display: flex; justify-content: space-between; font-size: 0.75rem; margin-top: 6px; color: #94a3b8;">
            <span>Target: ${tgt}%</span>
            <span>Current: ${cur}%</span>
            <span style="color: #34d399; font-weight: 700;">Post: ${post}%</span>
          </div>
          <div style="margin-top: 8px; font-size: 0.95rem; font-weight: 800; color: #f8fafc;">
            +₹${parseFloat(alloc).toLocaleString('en-IN')}
          </div>
        </div>
      `;
    }).join('');
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const selA = document.getElementById('vennFundA');
  const selB = document.getElementById('vennFundB');
  if (selA && selB) {
    selA.addEventListener('change', render2FundVennDiagram);
    selB.addEventListener('change', render2FundVennDiagram);
  }
  
  const btnViewPlan = document.getElementById('btnViewRebalancePlan');
  const btnLumpsum = document.getElementById('btnSimulateLumpsum');
  
  if (btnViewPlan) {
    btnViewPlan.addEventListener('click', () => loadUnifiedRebalancePlan('INDUCED'));
  }
  if (btnLumpsum) {
    btnLumpsum.addEventListener('click', () => {
      const amtStr = prompt('Enter manual lump-sum amount to simulate (₹):', '50000');
      if (amtStr) {
        const amt = parseFloat(amtStr);
        if (!isNaN(amt) && amt > 0) {
          loadUnifiedRebalancePlan('MANUAL_LUMPSUM', amt);
        }
      }
    });
  }

  loadActionRecommendations();
  render2FundVennDiagram();
  loadUnifiedRebalancePlan('INDUCED');
});

if (typeof window !== 'undefined') {
  window.loadOverlapAnalytics = loadOverlapAnalytics;
  window.loadUpSetAnalytics = loadUpSetAnalytics;
  window.loadActionRecommendations = loadActionRecommendations;
  window.render2FundVennDiagram = render2FundVennDiagram;
  window.loadUnifiedRebalancePlan = loadUnifiedRebalancePlan;
}



