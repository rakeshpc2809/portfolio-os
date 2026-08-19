import { API_BASE, fetchJson } from '../api.js';
import { state } from '../state.js';
import { formatINR } from '../utils.js';
import { FUND_REGISTRY, getActionBadgeStyle } from '../constants.js';
import { setText, setHtml, setBadgeStyle, setErrorState } from '../domUtils.js';

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
      radius: ['40%', '75%'],
      center: ['38%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#0c101c', borderWidth: 2 },
      label: { show: false },
      data: data
    }]
  };
  instance.setOption(option);

  if (window.ResizeObserver && !container.__resizeObserverAttached) {
    container.__resizeObserverAttached = true;
    const ro = new ResizeObserver(() => {
      try { instance.resize(); } catch (e) {}
    });
    ro.observe(container);
  }

  return instance;
}

export function resampleToMonthEnd(dates, values, investedValues) {
  if (!dates || dates.length === 0) return { dates: [], values: [], investedValues: [] };

  const monthMap = new Map();
  for (let i = 0; i < dates.length; i++) {
    const dStr = dates[i];
    const monthKey = dStr.substring(0, 7); // YYYY-MM
    monthMap.set(monthKey, {
      date: dStr,
      value: values[i],
      invested: investedValues && investedValues.length > i ? investedValues[i] : 0
    });
  }

  const allResampled = Array.from(monthMap.values());
  const sliced = allResampled.slice(-12);

  const resDates = sliced.map(p => p.date);
  const resValues = sliced.map(p => p.value);
  const resInvested = sliced.map(p => p.invested);

  const windowBadge = document.getElementById('netWorthWindowBadge');
  if (windowBadge) {
    windowBadge.textContent = `Trailing ${sliced.length} Months (Month-End Snapshot)`;
  }

  return { dates: resDates, values: resValues, investedValues: resInvested };
}

export function renderNetWorthTrendChart(containerId, dates, values, investedValues = null, isMonthly = false) {
  const container = document.getElementById(containerId);
  if (!container || !dates || dates.length === 0 || !window.echarts) return null;

  if (state.charts.netWorthTrendChart) {
    try { state.charts.netWorthTrendChart.dispose(); } catch (e) {}
  }

  const instance = window.echarts.init(container);

  // Calculate MoM % if monthly or latest period change
  if (values && values.length >= 2) {
    const prevVal = values[values.length - 2];
    const currVal = values[values.length - 1];
    if (prevVal > 0) {
      const momPct = ((currVal - prevVal) / prevVal) * 100;
      const momBadge = document.getElementById('netWorthMoMBadge');
      if (momBadge) {
        const sign = momPct >= 0 ? '+' : '';
        momBadge.textContent = `MoM: ${sign}${momPct.toFixed(1)}%`;
        if (momPct >= 0) {
          momBadge.style.background = 'rgba(16, 185, 129, 0.15)';
          momBadge.style.color = '#10b981';
          momBadge.style.borderColor = '#10b981';
        } else {
          momBadge.style.background = 'rgba(239, 68, 68, 0.15)';
          momBadge.style.color = '#ef4444';
          momBadge.style.borderColor = '#ef4444';
        }
      }
    }
  }

  const series = [{
    name: 'Net Worth',
    type: 'line',
    smooth: true,
    showSymbol: isMonthly,
    symbolSize: 6,
    lineStyle: { width: 3, color: '#d0ff00' },
    areaStyle: {
      color: new window.echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(208,255,0,0.25)' },
        { offset: 1, color: 'rgba(6,182,212,0.01)' }
      ])
    },
    data: values
  }];

  if (investedValues && investedValues.length > 0) {
    series.push({
      name: 'Capital Invested',
      type: 'line',
      smooth: true,
      z: 10,
      showSymbol: isMonthly,
      symbolSize: 6,
      lineStyle: { width: 2.5, color: '#38bdf8', type: 'dashed' },
      data: investedValues
    });
  }

  const option = {
    backgroundColor: 'transparent',
    legend: {
      show: true,
      top: '0%',
      right: '2%',
      textStyle: { color: '#cbd5e1', fontSize: 11 },
      data: ['Net Worth', 'Capital Invested']
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#090f1e' } },
      formatter: params => {
        let res = `<div style="font-weight:700; color:#f8fafc; margin-bottom:4px;">${params[0].name}</div>`;
        params.forEach(p => {
          const color = p.color || '#38bdf8';
          res += `<div><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${color};margin-right:6px;"></span>${p.seriesName}: <b>₹ ${formatINR(p.value)}</b></div>`;
        });
        if (isMonthly && params[0].dataIndex > 0) {
          const idx = params[0].dataIndex;
          const pVal = values[idx - 1];
          const cVal = values[idx];
          if (pVal > 0) {
            const diff = cVal - pVal;
            const pct = (diff / pVal) * 100;
            const sign = pct >= 0 ? '+' : '';
            res += `<div style="margin-top:4px; font-size:0.75rem; color:#cbd5e1;">MoM Return: <b style="color:${pct >= 0 ? '#10b981' : '#ef4444'};">${sign}${pct.toFixed(1)}% (${sign}₹ ${formatINR(diff)})</b></div>`;
          }
        }
        return res;
      }
    },
    grid: { left: '3%', right: '3%', top: '16%', bottom: '16%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.15)' } },
      axisLabel: { color: '#94a3b8', fontSize: 10, hideOverlap: true }
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
    series: series
  };
  instance.setOption(option);
  state.charts.netWorthTrendChart = instance;

  // Handle Dynamic ResizeObserver for parent container
  if (window.ResizeObserver && container) {
    if (container._resizeObserver) {
      container._resizeObserver.disconnect();
    }
    container._resizeObserver = new ResizeObserver(() => {
      try { instance.resize(); } catch (e) {}
    });
    container._resizeObserver.observe(container);
  }

  return instance;
}

export async function loadNetWorthTrend(isMonthly = false) {
  try {
    const data = await fetchJson(`${API_BASE}/reports/trend`).catch(() => null) ||
                 await fetchJson(`${API_BASE}/portfolio/net-worth-trend`).catch(() => null);
    if (!data || !data.dates || data.dates.length === 0) return;

    state.netWorthRawData = data;

    let dates = data.dates;
    let values = data.values;
    let investedValues = data.invested_values || data.investedValues || [];
    let coverage = typeof data.coverage_pct === 'number' ? data.coverage_pct : 100.0;

    if (isMonthly) {
      const resampled = resampleToMonthEnd(dates, values, investedValues);
      dates = resampled.dates;
      values = resampled.values;
      investedValues = resampled.investedValues;
    } else {
      const windowBadge = document.getElementById('netWorthWindowBadge');
      if (windowBadge) {
        windowBadge.textContent = coverage >= 99.0
          ? 'Daily Valuation & Capital Contributed (100% Mark-to-Market NAV)'
          : `Daily Valuation & Capital Contributed (${coverage.toFixed(1)}% Value-Weighted NAV Coverage)`;
      }
    }

    renderNetWorthTrendChart('netWorthChartContainer', dates, values, investedValues, isMonthly);
  } catch (err) {
    console.error('Failed to load Net Worth Trend:', err);
  }
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
        name: shortenFundName(assetName),
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

export function renderBucketAllocationChart(containerId, bucketStatuses) {
  const container = document.getElementById(containerId);
  if (!container || !bucketStatuses || bucketStatuses.length === 0 || !window.echarts) return null;

  if (state.charts.bucketAllocChart) state.charts.bucketAllocChart.dispose();

  const instance = window.echarts.init(container);

  const categories = bucketStatuses.map(b => b.bucket_name || b.bucketName || b.bucket);
  const targetData = bucketStatuses.map(b => parseFloat(b.target_pct || b.targetPct) || 0);
  const actualData = bucketStatuses.map(b => {
    const val = parseFloat(b.current_pct || b.currentPct) || 0;
    const isDrifted = b.is_drifted !== undefined ? b.is_drifted : b.isDrifted;
    const isLegacy = (b.bucket_name || b.bucketName || b.bucket) === 'LEGACY_HOLDINGS';
    return {
      value: val,
      itemStyle: {
        color: isLegacy ? '#64748b' : (isDrifted ? '#f59e0b' : '#10b981')
      }
    };
  });

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        let res = `<b>${params[0].name}</b><br/>`;
        params.forEach(p => {
          res += `${p.marker} ${p.seriesName}: <b>${p.value}%</b><br/>`;
        });
        return res;
      }
    },
    legend: {
      data: ['Target %', 'Actual %'],
      textStyle: { color: '#94a3b8', fontSize: 11 },
      right: 10,
      top: 10
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: categories,
      axisLine: { lineStyle: { color: '#334155' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: '#334155' } },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.05)' } },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: '{value}%' }
    },
    series: [
      {
        name: 'Target %',
        type: 'bar',
        data: targetData,
        itemStyle: { color: '#38bdf8', borderRadius: [4, 4, 0, 0] },
        barGap: '20%'
      },
      {
        name: 'Actual %',
        type: 'bar',
        data: actualData,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        barGap: '20%'
      }
    ]
  };

  instance.setOption(option);
  state.charts.bucketAllocChart = instance;
  return instance;
}

export function renderFundAllocationCompareChart(containerId, holdings, bucketTargetsConfig) {
  const container = document.getElementById(containerId);
  if (!container || !window.echarts) return null;

  if (state.charts.fundAllocCompareChart) {
    try { state.charts.fundAllocCompareChart.dispose(); } catch (e) {}
  }

  const instance = window.echarts.init(container);

  // 1. Extract active target version (e.g. v2.0)
  let activeVersion = null;
  if (bucketTargetsConfig && bucketTargetsConfig.versions && bucketTargetsConfig.versions.length > 0) {
    activeVersion = bucketTargetsConfig.versions[bucketTargetsConfig.versions.length - 1];
  }

  // 2. Build planned map: fund_id -> planned_pct
  const plannedMap = {};
  const fundNameMap = {};

  if (activeVersion && activeVersion.targets) {
    activeVersion.targets.forEach(t => {
      const bucketTargetPct = parseFloat(t.target_pct || t.targetPct) || 0;
      const prefFunds = t.preferred_funds || t.preferredFunds || [];
      prefFunds.forEach(pf => {
        const isin = pf.fund_id || pf.fundId;
        const name = pf.fund_name || pf.fundName;
        const weight = parseFloat(pf.allocation_weight || pf.allocationWeight) || 0;
        const plannedPct = Math.round(bucketTargetPct * weight * 100) / 100;
        if (isin) {
          plannedMap[isin] = plannedPct;
          if (name) fundNameMap[isin] = name;
        }
      });
    });
  }

  // 3. Build total portfolio net worth & actual map: fund_id -> actual_pct
  const totalVal = (holdings || []).reduce((sum, h) => sum + (parseFloat(h.current_value || h.currentValue) || 0), 0);
  const actualMap = {};
  const isinList = new Set();

  (holdings || []).forEach(h => {
    const isin = h.asset_id || h.assetId;
    const name = h.asset_name || h.assetName;
    const val = parseFloat(h.current_value || h.currentValue) || 0;
    const actualPct = totalVal > 0 ? Math.round((val / totalVal) * 10000) / 100 : 0;
    if (isin) {
      actualMap[isin] = actualPct;
      fundNameMap[isin] = name || fundNameMap[isin] || isin;
      isinList.add(isin);
    }
  });

  // Add any target ISINs that aren't in holdings yet
  Object.keys(plannedMap).forEach(isin => isinList.add(isin));

  // 4. Create combined items array
  const items = Array.from(isinList).map(isin => {
    const name = fundNameMap[isin] || isin;
    const plannedPct = plannedMap[isin] || 0;
    const actualPct = actualMap[isin] || 0;
    const isTarget = plannedPct > 0;
    const drift = Math.round((actualPct - plannedPct) * 100) / 100;
    return {
      isin,
      name,
      shortName: shortenFundName(name),
      plannedPct,
      actualPct,
      drift,
      isTarget
    };
  });

  // Sort: Target funds first (by plannedPct asc for bottom-to-top rendering in horizontal bar), then legacy funds
  items.sort((a, b) => {
    if (a.isTarget && !b.isTarget) return 1;
    if (!a.isTarget && b.isTarget) return -1;
    if (a.isTarget && b.isTarget) return a.plannedPct - b.plannedPct;
    return a.actualPct - b.actualPct;
  });

  const categories = items.map(i => i.shortName);
  const plannedData = items.map(i => i.plannedPct);
  const actualData = items.map(i => ({
    value: i.actualPct,
    itemStyle: {
      color: !i.isTarget ? '#64748b' : (Math.abs(i.drift) > 5.0 ? '#f59e0b' : '#10b981')
    }
  }));

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        const index = params[0].dataIndex;
        const item = items[index];
        let res = `<div style="font-weight:600; color:#f8fafc; margin-bottom:4px;">${item.name}</div>`;
        res += `<span style="color:#94a3b8; font-size:11px;">ISIN: ${item.isin}</span><br/>`;
        params.forEach(p => {
          res += `${p.marker} ${p.seriesName}: <b>${p.value.toFixed(2)}%</b><br/>`;
        });
        const driftSign = item.drift >= 0 ? '+' : '';
        const driftColor = item.drift > 5 ? '#f59e0b' : (item.drift < -5 ? '#ef4444' : '#10b981');
        res += `Drift (&Delta;): <b style="color:${driftColor}">${driftSign}${item.drift.toFixed(2)}%</b>`;
        return res;
      }
    },
    legend: {
      data: ['Planned %', 'Actual %'],
      textStyle: { color: '#94a3b8', fontSize: 11 },
      right: 10,
      top: 10
    },
    grid: { left: '3%', right: '5%', bottom: '3%', top: '40px', containLabel: true },
    xAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: '#334155' } },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.05)' } },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: '{value}%' }
    },
    yAxis: {
      type: 'category',
      data: categories,
      axisLine: { lineStyle: { color: '#334155' } },
      axisLabel: { color: '#cbd5e1', fontSize: 11 }
    },
    series: [
      {
        name: 'Planned %',
        type: 'bar',
        data: plannedData,
        itemStyle: { color: '#38bdf8', borderRadius: [0, 4, 4, 0] },
        barGap: '20%'
      },
      {
        name: 'Actual %',
        type: 'bar',
        data: actualData,
        itemStyle: { borderRadius: [0, 4, 4, 0] },
        barGap: '20%'
      }
    ]
  };

  instance.setOption(option);
  state.charts.fundAllocCompareChart = instance;
  return instance;
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
      const star = res.is_provisional ? '*' : '';
      setText('#benchmarkAlphaVal', `${res.alpha_pct > 0 ? '+' : ''}${res.alpha_pct}%${star}`);
      setText('#benchmarkBetaVal', `${res.beta}${star}`);
      setText('#benchmarkSharpeVal', `${res.sharpe_ratio}${star}`);
      setText('#benchmarkTrackingVal', `${res.tracking_error_pct}%${star}`);
      setText('#benchmarkOutperformVal', `${res.outperformance_pct > 0 ? '+' : ''}${res.outperformance_pct}%${star}`);

      const cardGrid = document.querySelector('#benchmarkMetricsGrid');
      if (cardGrid) {
        cardGrid.style.opacity = res.is_provisional ? '0.82' : '1.0';
      }

      if (res.is_provisional) {
        setBadgeStyle('#benchmarkSampleBadge', `PROVISIONAL (${res.sample_days} DAYS)`, 'live-tag warning-tag');
      } else {
        setBadgeStyle('#benchmarkSampleBadge', `MATURE (${res.sample_days} DAYS)`, 'live-tag positive-tag');
      }

      if (res.data_source_label) {
        setText('#benchmarkProvenanceSub', res.data_source_label);
      }
    }
  } catch (err) {
    console.error('Failed to load benchmark analytics:', err);
    setErrorState('#benchmarkAlphaVal', '—');
    setErrorState('#benchmarkBetaVal', '—');
    setErrorState('#benchmarkSharpeVal', '—');
    setErrorState('#benchmarkTrackingVal', '—');
    setErrorState('#benchmarkOutperformVal', '—');
    setBadgeStyle('#benchmarkSampleBadge', 'OFFLINE', 'live-tag warning-tag');
  }
}

export async function populateFundDropdowns() {
  const selA = document.getElementById('vennFundA');
  const selB = document.getElementById('vennFundB');
  if (!selA || !selB) return;

  try {
    const res = await fetchJson(`${API_BASE}/funds/registry`);
    if (res && res.status === 'OK' && res.funds) {
      // Clear static FUND_REGISTRY and populate from live ingested tax_events response
      Object.keys(FUND_REGISTRY).forEach(key => delete FUND_REGISTRY[key]);
      res.funds.forEach(f => {
        if (f.isin && f.name) {
          FUND_REGISTRY[f.isin] = f.name;
        }
      });
    }
  } catch (err) {
    console.warn('Failed to load live fund registry from backend, using fallback:', err);
  }

  const currentA = selA.value || 'INF879O01027';
  const currentB = selB.value || 'INF109KC13X2';

  let optionsHtml = '';
  Object.keys(FUND_REGISTRY).forEach(key => {
    optionsHtml += `<option value="${key}">${FUND_REGISTRY[key]}</option>`;
  });

  selA.innerHTML = optionsHtml;
  selB.innerHTML = optionsHtml;

  selA.value = currentA;
  selB.value = currentB;
}

let activeOverlapRequestId = 0;

export async function loadOverlapAnalytics(fundAOverride = null, fundBOverride = null) {
  const currentRequestId = ++activeOverlapRequestId;

  const selA = document.getElementById('vennFundA');
  const selB = document.getElementById('vennFundB');

  const fundAKey = fundAOverride || (selA ? selA.value : 'INF879O01027');
  const fundBKey = fundBOverride || (selB ? selB.value : 'INF109KC13X2');

  const nameA = FUND_REGISTRY[fundAKey] || fundAKey;
  const nameB = FUND_REGISTRY[fundBKey] || fundBKey;

  const tableBody = document.querySelector('#topStockConcentrationTable tbody');
  const container = document.getElementById('vennContainer');

  setText('#overlapPairName', `${nameA} vs ${nameB}`);

  // Same Fund Selected Case (Strict raw ISIN string comparison)
  if (fundAKey === fundBKey) {
    setText('#pairwiseOverlapVal', '100.00%');
    setText('#commonStockCountSub', 'Identical Fund Selected (100% Stock Overlap)');
    setBadgeStyle('#overlapDateBadge', 'SAME FUND (100%)', 'live-tag positive-tag');
    renderVennSvg(container, nameA, nameB, 100.00);
    return;
  } else {
    setText('#pairwiseOverlapVal', '...');
    setText('#commonStockCountSub', 'Calculating live stock overlap...');
  }

  try {
    const res = await fetchJson(`${API_BASE}/analytics/overlap?fundA=${encodeURIComponent(fundAKey)}&fundB=${encodeURIComponent(fundBKey)}`);
    if (currentRequestId !== activeOverlapRequestId) return; // Stale fetch race guard

    if (res && res.status === 'OK') {
      const pairwise = res.pairwise_overlap;
      const concentrations = res.portfolio_top_stock_concentrations;

      if (fundAKey !== fundBKey && pairwise) {
        if (pairwise.common_stock_count === 0) {
          // Genuine 0% Overlap between 2 distinct funds
          setText('#pairwiseOverlapVal', '0.00%');
          setText('#commonStockCountSub', 'Common Holdings: 0 Stocks (No Shared Holdings)');
          setBadgeStyle('#overlapDateBadge', 'NO SHARED HOLDINGS', 'live-tag neutral-tag');
          renderVennSvg(container, nameA, nameB, 0.00);
        } else {
          // Genuine > 0% Overlap
          setText('#pairwiseOverlapVal', `${pairwise.overlap_percentage}%`);
          const topSymbols = (pairwise.common_stocks || []).slice(0, 4).map(s => s.stock_symbol).join(', ');
          const extraStr = topSymbols ? ` (${topSymbols})` : '';
          setText('#commonStockCountSub', `Common Holdings: ${pairwise.common_stock_count} Stocks${extraStr}`);

          if (pairwise.date_mismatch) {
            setBadgeStyle('#overlapDateBadge', 'DATE MISMATCH', 'live-tag warning-tag');
          } else {
            setBadgeStyle('#overlapDateBadge', 'SNAPSHOT ALIGNED', 'live-tag positive-tag');
          }
          renderVennSvg(container, nameA, nameB, pairwise.overlap_percentage);
        }
      }

      if (tableBody && concentrations) {
        if (concentrations.length === 0) {
          setHtml(tableBody, `<tr><td colspan="3" style="text-align:center; color:#64748b;">No stock concentrations calculated.</td></tr>`);
        } else {
          let html = '';
          concentrations.forEach(item => {
            html += `<tr>
              <td><strong>${item.stock_symbol}</strong></td>
              <td>${formatINR(item.rupee_exposure)}</td>
              <td><span class="metric-delta positive">${item.portfolio_percentage}%</span></td>
            </tr>`;
          });
          setHtml(tableBody, html);
        }
      }

      await loadUpSetAnalytics();
      await loadActionRecommendations();
    } else {
      throw new Error(res ? res.message : 'Invalid API response');
    }
  } catch (err) {
    if (currentRequestId !== activeOverlapRequestId) return;
    console.error('Failed to load overlap analytics:', err);
    setErrorState('#pairwiseOverlapVal', '—');
    setText('#commonStockCountSub', '⚠️ Overlap Fetch Failed (Check Backend Service)');
    setBadgeStyle('#overlapDateBadge', 'OFFLINE', 'live-tag warning-tag');
    if (container) {
      setHtml(container, `<div style="text-align: center; color: #f87171; padding: 12px;">⚠️ Failed to load overlap graph from backend.</div>`);
    }
  }
}

function renderVennSvg(container, nameA, nameB, overlapPct) {
  if (!container) return;
  const numOverlap = typeof overlapPct === 'number' ? overlapPct : parseFloat(overlapPct) || 0;

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
      <text x="250" y="85" fill="#d0ff00" font-size="14" font-weight="800" text-anchor="middle">${numOverlap.toFixed(2)}%</text>
      <text x="250" y="105" fill="#e2e8f0" font-size="9" font-weight="600" text-anchor="middle">Shared Overlap</text>
    </svg>
  `;

  container.innerHTML = svg;
}

export async function loadUpSetAnalytics() {
  const container = document.querySelector('#upsetContainer');
  if (!container) return;

  try {
    const res = await fetchJson(`${API_BASE}/analytics/overlap/upset`);
    if (res && res.status === 'OK' && res.upset_combinations) {
      const combos = res.upset_combinations;
      const allFundKeys = Object.keys(FUND_REGISTRY);

      if (combos.length === 0) {
        container.innerHTML = `<div style="text-align:center; color:#64748b;">No multi-set intersections found.</div>`;
        return;
      }

      const maxCount = Math.max(...combos.map(c => c.stock_count));

      let html = `<div style="display: flex; gap: 20px; font-family: monospace; font-size: 0.78rem;">`;
      html += `<div style="display: flex; flex-direction: column; justify-content: flex-end; gap: 8px; font-weight: 600; color: #94a3b8; padding-bottom: 22px;">`;
      allFundKeys.forEach(key => {
        html += `<div style="height: 18px; line-height: 18px; text-align: right; white-space: nowrap;">${FUND_REGISTRY[key]}</div>`;
      });
      html += `</div>`;

      html += `<div style="display: flex; gap: 14px; overflow-x: auto; padding-bottom: 6px;">`;

      combos.forEach(c => {
        const participating = c.participating_funds;
        const participatingNames = participating.map(k => FUND_REGISTRY[k] || k);
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
    if (container) {
      container.innerHTML = `<div style="text-align: center; color: #f87171; padding: 8px;">⚠️ UpSet Analytics Unavailable</div>`;
    }
  }
}

export async function loadActionRecommendations() {
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
    if (container) {
      container.innerHTML = `<div style="color: #f87171;">⚠️ Action Recommendations Unavailable</div>`;
    }
  }
}

function render2FundVennDiagram() {
  const selA = document.getElementById('vennFundA');
  const selB = document.getElementById('vennFundB');
  if (selA && selB) {
    loadOverlapAnalytics(selA.value, selB.value);
  } else {
    loadOverlapAnalytics();
  }
}

export async function loadUnifiedRebalancePlan(triggerType = 'INDUCED', manualAmount = null, includeRebalance = false) {
  try {
    let url = `/api/v1/sync/rebalance/plan?trigger=${encodeURIComponent(triggerType)}`;
    let options = { method: 'GET' };

    if (triggerType === 'MANUAL_LUMPSUM') {
      url = `/api/v1/sync/rebalance/simulate-lumpsum`;
      options = {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: manualAmount || 100000.0,
          includeRebalance: Boolean(includeRebalance)
        })
      };
    }

    const plan = await fetchJson(url, options);
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
  const lumpsumMeta = plan.manual_lumpsum_meta || plan.manualLumpsumMeta;

  // 1. Render Status Strip
  const badgeEl = document.getElementById('rebalanceTriggerBadge');
  const ddPctEl = document.getElementById('stripDrawdownPct');
  const highEl = document.getElementById('stripRollingHigh');
  const windowEl = document.getElementById('stripReconWindow');

  if (badgeEl && trigger) {
    if (trigger.type === 'MANUAL_LUMPSUM' || lumpsumMeta) {
      const isIncRebal = lumpsumMeta ? (lumpsumMeta.include_rebalance ?? lumpsumMeta.includeRebalance) : false;
      badgeEl.textContent = isIncRebal ? 'MANUAL LUMP-SUM + REBALANCE' : 'MANUAL LUMP-SUM ONLY (NO SALES)';
      if (isIncRebal) {
        badgeEl.style.background = 'rgba(168, 85, 247, 0.2)';
        badgeEl.style.color = '#c084fc';
        badgeEl.style.borderColor = '#a855f7';
      } else {
        badgeEl.style.background = 'rgba(56, 189, 248, 0.2)';
        badgeEl.style.color = '#38bdf8';
        badgeEl.style.borderColor = '#0284c7';
      }
    } else {
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

  // 2. Render Header & Drawdown Gauge
  const titleEl = document.getElementById('planHeadlineTitle');
  const metaEl = document.getElementById('planMetaTimestamp');
  if (titleEl && narrative) {
    titleEl.textContent = narrative.headline || 'Unified Rebalance Plan';
  }
  const genAt = plan.generated_at || plan.generatedAt;
  if (metaEl && genAt) {
    metaEl.textContent = `Generated: ${new Date(genAt).toLocaleString()}`;
  }

  // Drawdown Tripwire Depth Gauge
  const ddPct = drawdownCtx.current_drawdown_pct ?? drawdownCtx.currentDrawdownPct ?? 0;
  const barEl = document.getElementById('gaugeProgressBar');
  const markEl = document.getElementById('gaugeIndicatorMarker');
  const statusEl = document.getElementById('gaugeStatusText');
  const distEl = document.getElementById('gaugeNextDistance');

  if (barEl && markEl) {
    const gaugeWidth = Math.min(100, Math.max(0, (ddPct / 20.0) * 100));
    barEl.style.width = `${gaugeWidth}%`;
    markEl.style.left = `${gaugeWidth}%`;
  }
  if (statusEl) {
    statusEl.textContent = `Current Drawdown: ${ddPct}%`;
  }
  if (distEl && drawdownCtx) {
    const dist = drawdownCtx.next_tier_distance_pct ?? drawdownCtx.nextTierDistancePct ?? 0;
    const nextT = drawdownCtx.next_tier ?? drawdownCtx.nextTier ?? 'TIER_10';
    distEl.textContent = `${dist}% to ${nextT}`;
  }

  // 3. Exemption Headroom Burndown Bar
  const taxSum = sellSide.tax_summary || sellSide.taxSummary || {};
  const headroomBefore = taxSum.exemption_headroom_before ?? taxSum.exemptionHeadroomBefore ?? 125000;
  const tradeExempt = taxSum.total_ltcg_exempt ?? taxSum.totalLtcgExempt ?? 0;
  const taxableSpill = taxSum.total_stcg_taxable ?? taxSum.totalStcgTaxable ?? 0;
  const headroomAfter = taxSum.exemption_headroom_after ?? taxSum.exemptionHeadroomAfter ?? 112580;
  const priorUsed = Math.max(0, 125000 - headroomBefore);

  const burnPriorEl = document.getElementById('burnUsedPrior');
  const burnTradeEl = document.getElementById('burnTradeExempt');
  const burnSpillEl = document.getElementById('burnTaxableSpill');
  const burnRemTag = document.getElementById('burndownHeadroomRemaining');

  if (burnPriorEl) burnPriorEl.style.width = `${(priorUsed / 125000) * 100}%`;
  if (burnTradeEl) burnTradeEl.style.width = `${(tradeExempt / 125000) * 100}%`;
  if (burnSpillEl) burnSpillEl.style.width = `${(taxableSpill / 125000) * 100}%`;
  if (burnRemTag) burnRemTag.textContent = `Remaining Headroom: ₹${headroomAfter.toLocaleString('en-IN')}`;

  const burnTextPrior = document.getElementById('burnTextPrior');
  const burnTextTrade = document.getElementById('burnTextTrade');
  const burnTextRem = document.getElementById('burnTextRem');

  if (burnTextPrior) burnTextPrior.textContent = `Prior Used: ₹${priorUsed.toLocaleString('en-IN')}`;
  if (burnTextTrade) burnTextTrade.textContent = `Trade Exempt: ₹${tradeExempt.toLocaleString('en-IN')}`;
  if (burnTextRem) burnTextRem.textContent = `Remaining: ₹${headroomAfter.toLocaleString('en-IN')}`;

  // 4. Render Primary Box & Connector Layout and Summary Line
  renderRebalanceBoxConnector(plan);

  // 5. Render Pre/Post Allocation Progression Delta Badges
  renderPrePostAllocationDelta(plan);
  renderTargetFundProgression(plan, state.holdings, state.bucketTargetsConfig);

  // 6. Render Secondary Sankey (mounted, hidden by default until toggle)
  renderRebalanceMicroSankey(sellSide, buySide);

  // 7. Render Interactive Tactical Action Matrix (Granular Lot Override)
  renderTacticalActionMatrix(plan);

  // 6. Render Narrative Paragraphs
  const pContainer = document.getElementById('planReasoningParagraphs');
  if (pContainer && narrative.paragraphs) {
    pContainer.innerHTML = narrative.paragraphs.map(p => `
      <p style="margin: 0 0 6px 0; font-size: 0.8rem; line-height: 1.4;">• ${p}</p>
    `).join('');
  }

  // 7. Render Buy-Side Allocation Grid
  renderBuySideAllocationGrid(buySide);
}

function renderBuySideAllocationGrid(buySide, liveTotalOverride = null) {
  const buyGrid = document.getElementById('buySideAllocationGrid');
  if (!buyGrid || !buySide.buckets) return;

  const totalPool = liveTotalOverride !== null ? liveTotalOverride : (buySide.total_to_invest ?? buySide.totalToInvest ?? 0);

  buyGrid.innerHTML = buySide.buckets.map(b => {
    const tgt = b.target_pct ?? b.targetPct ?? 0;
    const cur = b.current_pct ?? b.currentPct ?? 0;
    const post = b.post_rebalance_pct ?? b.postRebalancePct ?? 0;
    const alloc = totalPool * (tgt / 100.0);

    const fundsHtml = (b.fund_breakdown || b.fundBreakdown || []).map(f => {
      const fName = f.fund_name || f.fundName || f.fund_id;
      const fAlloc = alloc * (f.allocation_weight || (1.0 / (b.fund_breakdown || b.fundBreakdown || [1]).length));
      return `
        <div style="display: flex; justify-content: space-between; font-size: 0.7rem; color: #cbd5e1; margin-top: 4px; border-top: 1px dashed rgba(255,255,255,0.06); padding-top: 3px;">
          <span>• ${fName}</span>
          <span style="font-weight: 700; color: #34d399;">+₹${Math.round(fAlloc).toLocaleString('en-IN')}</span>
        </div>
      `;
    }).join('');

    return `
      <div style="background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 6px; padding: 12px;">
        <div style="font-size: 0.8rem; font-weight: 700; color: #38bdf8;">${b.bucket.replace('_', ' ')}</div>
        <div style="display: flex; justify-content: space-between; font-size: 0.75rem; margin-top: 6px; color: #94a3b8;">
          <span>Target: ${tgt}%</span>
          <span>Current: ${cur}%</span>
          <span style="color: #34d399; font-weight: 700;">Post: ${post}%</span>
        </div>
        <div style="margin-top: 8px; font-size: 0.95rem; font-weight: 800; color: #f8fafc;">
          +₹${Math.round(alloc).toLocaleString('en-IN')}
        </div>
        <div style="margin-top: 6px;">
          ${fundsHtml}
        </div>
      </div>
    `;
  }).join('');
}

function shortenFundName(rawName) {
  if (!rawName) return '';
  return rawName
    .replace(/\s*-\s*Direct Plan Growth\s*\(Non Demat\)/gi, '')
    .replace(/\s*-\s*DIRECT GROWTH PLAN GROWTH OPTION\s*\(Non Demat\)/gi, '')
    .replace(/\s*Direct Plan\s*-\s*Growth/gi, '')
    .replace(/\s*-\s*Direct Plan Growth/gi, '')
    .replace(/\s*Direct Growth Plan Growth Option\s*\(Non Demat\)/gi, '')
    .replace(/\s*-\s*Direct Growth/gi, '')
    .replace(/\s*Direct Plan/gi, '')
    .replace(/\s*Index Fund/gi, '')
    .replace(/ICICI Prudential/gi, 'ICICI')
    .replace(/Motilal Oswal/gi, 'Motilal')
    .replace(/NIPPON INDIA/gi, 'Nippon')
    .replace(/Mirae Asset/gi, 'Mirae')
    .replace(/Edelweiss Nifty500 Multicap Momentum Quality 50/gi, 'Edelweiss MomQual 50')
    .replace(/Invesco India/gi, 'Invesco')
    .replace(/Kotak Mahindra/gi, 'Kotak')
    .replace(/Parag Parikh/gi, 'PPFAS')
    .replace(/\s+/g, ' ')
    .trim();
}

function renderRebalanceBoxConnector(plan) {
  const sellSide = plan.sell_side || plan.sellSide || {};
  const buySide = plan.buy_side || plan.buySide || {};
  const taxSum = sellSide.tax_summary || sellSide.taxSummary || {};

  const totalRealized = parseFloat(taxSum.total_sale_proceeds ?? taxSum.totalSaleProceeds ?? buySide.total_to_invest ?? buySide.totalToInvest ?? 0);
  const tradeExempt = parseFloat(taxSum.total_ltcg_exempt ?? taxSum.totalLtcgExempt ?? taxSum.total_ltcg_exemption_applied ?? 0);
  const taxSavedTotal = Math.round(tradeExempt * 0.125);
  const headroomBefore = parseFloat(taxSum.exemption_headroom_before ?? taxSum.exemptionHeadroomBefore ?? 125000);
  const headroomAfter = parseFloat(taxSum.exemption_headroom_after ?? taxSum.exemptionHeadroomAfter ?? (headroomBefore - tradeExempt));
  const priorUsed = Math.max(0, 125000 - headroomBefore);
  const totalYtdExempt = priorUsed + tradeExempt;
  const headroomRem = headroomAfter;
  const totalTax = parseFloat(taxSum.total_tax_estimate ?? taxSum.totalTaxEstimate ?? 0);

  // 1. Update Summary Bar
  const elRealized = document.getElementById('sumRealizedProceeds');
  const elTradeEx = document.getElementById('sumTradeExemption');
  const elTaxSaved = document.getElementById('sumTaxSaved');
  const elYtdEx = document.getElementById('sumYtdExemption');
  const elHeadroom = document.getElementById('sumRemainingHeadroom');
  const elTax = document.getElementById('sumTaxOwed');

  if (elRealized) elRealized.textContent = `₹${Math.round(totalRealized).toLocaleString('en-IN')}`;
  if (elTradeEx) elTradeEx.textContent = `₹${Math.round(tradeExempt).toLocaleString('en-IN')}`;
  if (elTaxSaved) elTaxSaved.textContent = `+₹${taxSavedTotal.toLocaleString('en-IN')}`;
  if (elYtdEx) elYtdEx.textContent = `₹${Math.round(totalYtdExempt).toLocaleString('en-IN')} of ₹1,25,000`;
  if (elHeadroom) elHeadroom.textContent = `₹${Math.round(headroomRem).toLocaleString('en-IN')}`;
  if (elTax) elTax.textContent = `₹${Math.round(totalTax).toLocaleString('en-IN')}`;

  // 2. Build Sell Cards Column (Fund-Wise Aggregated)
  const sellCol = document.getElementById('rebalanceSellCardsCol');
  const sellFundMap = new Map();

  (sellSide.waterfall || []).forEach(tier => {
    const tLabel = tier.tier_label || tier.tierLabel || 'Waterfall Tier';
    (tier.lots || []).forEach(lot => {
      const fName = shortenFundName(lot.fundName || lot.fund_name || lot.fundId || lot.fund_id);
      const proceeds = parseFloat(lot.saleProceeds || lot.sale_proceeds || 0);
      const units = parseFloat(lot.units_sold || lot.unitsSold || lot.units || 0);
      const gain = parseFloat(lot.realizedGain || lot.realized_gain || 0);
      const ti = lot.tax_impact || lot.taxImpact || {};
      const regime = ti.regime || ti.regime_type || lot.tax_term || lot.taxTerm || 'SEC_112A_EXEMPT';

      if (proceeds > 0) {
        if (!sellFundMap.has(fName)) {
          sellFundMap.set(fName, { name: fName, proceeds: 0, units: 0, gain: 0, regime: regime, tierLabel: tLabel });
        }
        const existing = sellFundMap.get(fName);
        existing.proceeds += proceeds;
        existing.units += units;
        existing.gain += gain;
        if (regime === 'SLAB_RATE_STCG') existing.regime = 'SLAB_RATE_STCG';
        else if (regime === 'SEC_112A_TAXABLE_12_5' && existing.regime !== 'SLAB_RATE_STCG') existing.regime = 'SEC_112A_TAXABLE_12_5';
      }
    });
  });

  if (sellCol) {
    if (sellFundMap.size > 0) {
      sellCol.innerHTML = Array.from(sellFundMap.values()).map(f => {
        const fundTaxSaved = Math.round(Math.max(0, f.gain) * 0.125);
        let badgeBg = 'rgba(16, 185, 129, 0.15)';
        let badgeColor = '#10b981';
        let badgeBorder = '#10b981';
        let badgeLabel = fundTaxSaved > 0 ? `LTCG EXEMPT (Saved +₹${fundTaxSaved.toLocaleString('en-IN')} Tax)` : 'LTCG EXEMPT';

        if (f.regime === 'SLAB_RATE_STCG') {
          badgeBg = 'rgba(239, 68, 68, 0.15)';
          badgeColor = '#ef4444';
          badgeBorder = '#ef4444';
          badgeLabel = 'STCG (20%)';
        } else if (f.regime === 'SEC_112A_TAXABLE_12_5') {
          badgeBg = 'rgba(245, 158, 11, 0.15)';
          badgeColor = '#f59e0b';
          badgeBorder = '#f59e0b';
          badgeLabel = 'LTCG (12.5%)';
        }

        return `
          <div class="rebalance-sell-card" style="background: rgba(30, 41, 59, 0.75); border: 1px solid rgba(244,63,94,0.3); border-left: 4px solid #f43f5e; border-radius: 6px; padding: 8px 12px; display: flex; justify-content: space-between; align-items: center; font-size: 0.78rem;">
            <div>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 4px;">
                <span style="background: rgba(244, 63, 94, 0.2); color: #fb7185; border: 1px solid #f43f5e; font-size: 0.6rem; padding: 1px 5px; border-radius: 3px; font-weight: 800;">SELL</span>
                ${f.name}
              </div>
              <div style="font-size: 0.7rem; color: #94a3b8; margin-top: 3px;">
                ${f.units > 0 ? `${f.units.toFixed(1)} units` : ''} · <span style="color: #cbd5e1;">${f.tierLabel}</span>
                <span style="background: ${badgeBg}; color: ${badgeColor}; border: 1px solid ${badgeBorder}; font-size: 0.62rem; padding: 1px 5px; border-radius: 3px; margin-left: 6px; font-weight: 600;">${badgeLabel}</span>
              </div>
            </div>
            <div style="font-weight: 800; color: #fb7185; font-size: 0.85rem;">
              -₹${Math.round(f.proceeds).toLocaleString('en-IN')}
            </div>
          </div>
        `;
      }).join('');
    } else {
      sellCol.innerHTML = `
        <div style="background: rgba(30, 41, 59, 0.6); border: 1px dashed rgba(255,255,255,0.1); border-radius: 6px; padding: 12px; text-align: center; color: #94a3b8; font-size: 0.78rem;">
          No liquidations required — using available cash reserves
        </div>
      `;
    }
  }

  // 3. Build Central Pool Amount
  const elPoolAmt = document.getElementById('rebalancePoolAmount');
  if (elPoolAmt) elPoolAmt.textContent = `₹${Math.round(totalRealized).toLocaleString('en-IN')}`;

  // 4. Build Buy Cards Column
  const buyCol = document.getElementById('rebalanceBuyCardsCol');
  const buyFunds = [];

  (buySide.buckets || []).forEach(b => {
    const bucketName = (b.bucket || '').replace('_', ' ');
    (b.fund_breakdown || b.fundBreakdown || []).forEach(f => {
      const fName = shortenFundName(f.fundName || f.fund_name || f.fundId || f.fund_id);
      const amt = parseFloat(f.amount || 0);
      if (amt > 0) {
        buyFunds.push({ name: fName, amount: amt, bucket: bucketName });
      }
    });
  });

  if (buyCol) {
    if (buyFunds.length > 0) {
      buyCol.innerHTML = buyFunds.map(f => `
        <div class="rebalance-buy-card" style="background: rgba(30, 41, 59, 0.75); border: 1px solid rgba(16, 185, 129, 0.3); border-right: 4px solid #10b981; border-radius: 6px; padding: 8px 12px; display: flex; justify-content: space-between; align-items: center; font-size: 0.78rem;">
          <div>
            <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 4px;">
              <span style="background: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid #10b981; font-size: 0.6rem; padding: 1px 5px; border-radius: 3px; font-weight: 800;">BUY</span>
              ${f.name}
            </div>
            <div style="font-size: 0.68rem; color: #34d399; margin-top: 3px; font-weight: 600;">${f.bucket}</div>
          </div>
          <div style="font-weight: 800; color: #34d399; font-size: 0.85rem;">
            +₹${Math.round(f.amount).toLocaleString('en-IN')}
          </div>
        </div>
      `).join('');
    } else {
      buyCol.innerHTML = `<div style="text-align: center; color: #64748b; padding: 12px; font-size: 0.78rem;">No target buy allocations</div>`;
    }
  }

  // 5. Draw SVG Bezier Connectors
  setTimeout(drawBoxSvgConnectors, 50);

  // 6. View Toggle Event Listeners
  const btnBox = document.getElementById('btnViewBoxConnector');
  const btnSankey = document.getElementById('btnViewSankey');
  const boxContainer = document.getElementById('rebalanceBoxConnectorContainer');
  const sankeyContainer = document.getElementById('rebalanceSankeyChartContainer');

  if (btnBox && btnSankey && boxContainer && sankeyContainer) {
    btnBox.onclick = () => {
      boxContainer.style.display = 'flex';
      sankeyContainer.style.display = 'none';
      btnBox.style.background = 'rgba(56, 189, 248, 0.2)';
      btnBox.style.color = '#38bdf8';
      btnBox.style.borderColor = '#38bdf8';
      btnSankey.style.background = 'rgba(255,255,255,0.05)';
      btnSankey.style.color = '#94a3b8';
      btnSankey.style.borderColor = 'rgba(255,255,255,0.1)';
      setTimeout(drawBoxSvgConnectors, 50);
    };

    btnSankey.onclick = () => {
      boxContainer.style.display = 'none';
      sankeyContainer.style.display = 'block';
      btnSankey.style.background = 'rgba(56, 189, 248, 0.2)';
      btnSankey.style.color = '#38bdf8';
      btnSankey.style.borderColor = '#38bdf8';
      btnBox.style.background = 'rgba(255,255,255,0.05)';
      btnBox.style.color = '#94a3b8';
      btnBox.style.borderColor = 'rgba(255,255,255,0.1)';

      const sankeyEl = document.getElementById('rebalanceSankeyChart');
      if (sankeyEl && typeof echarts !== 'undefined') {
        const inst = echarts.getInstanceByDom(sankeyEl);
        if (inst) inst.resize();
      }
    };
  }
}

function drawBoxSvgConnectors() {
  const container = document.getElementById('rebalanceBoxConnectorContainer');
  const poolPill = document.getElementById('rebalancePoolPill');
  const svg = document.getElementById('rebalanceConnectorSvg');
  if (!container || !poolPill || !svg) return;

  const containerRect = container.getBoundingClientRect();
  const poolRect = poolPill.getBoundingClientRect();

  const poolLeftX = poolRect.left - containerRect.left;
  const poolRightX = poolRect.right - containerRect.left;
  const poolY = poolRect.top + poolRect.height / 2 - containerRect.top;

  let pathHtml = '';

  // Sell Cards -> Pool Left (Rose Red dashed bezier)
  const sellCards = document.querySelectorAll('.rebalance-sell-card');
  sellCards.forEach(card => {
    const cRect = card.getBoundingClientRect();
    const cardX = cRect.right - containerRect.left;
    const cardY = cRect.top + cRect.height / 2 - containerRect.top;

    const dx = (poolLeftX - cardX) * 0.5;
    pathHtml += `<path d="M ${cardX} ${cardY} C ${cardX + dx} ${cardY}, ${poolLeftX - dx} ${poolY}, ${poolLeftX} ${poolY}" fill="none" stroke="rgba(244, 63, 94, 0.6)" stroke-width="2" stroke-dasharray="4 3" />`;
  });

  // Pool Right -> Buy Cards (Emerald Green solid bezier)
  const buyCards = document.querySelectorAll('.rebalance-buy-card');
  buyCards.forEach(card => {
    const cRect = card.getBoundingClientRect();
    const cardX = cRect.left - containerRect.left;
    const cardY = cRect.top + cRect.height / 2 - containerRect.top;

    const dx = (cardX - poolRightX) * 0.5;
    pathHtml += `<path d="M ${poolRightX} ${poolY} C ${poolRightX + dx} ${poolY}, ${cardX - dx} ${cardY}, ${cardX} ${cardY}" fill="none" stroke="rgba(16, 185, 129, 0.6)" stroke-width="2" />`;
  });

  svg.innerHTML = pathHtml;
}

function renderPrePostAllocationDelta(plan) {
  const container = document.getElementById('rebalanceAllocationDeltaContainer');
  const buySide = plan.buy_side || plan.buySide || {};

  if (!container || !buySide.buckets) return;

  container.innerHTML = buySide.buckets.map(b => {
    const name = (b.bucket || '').replace('_', ' ');
    const tgt = b.target_pct ?? b.targetPct ?? 0;
    const cur = b.current_pct ?? b.currentPct ?? 0;
    const post = b.post_rebalance_pct ?? b.postRebalancePct ?? 0;

    let deltaColor = '#34d399'; // Green for increase or match
    if (post < cur) deltaColor = '#f87171'; // Red for decrease

    return `
      <div style="background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 6px; padding: 6px 12px; font-size: 0.75rem; display: flex; align-items: center; gap: 8px;">
        <span style="font-weight: 700; color: #38bdf8;">${name}:</span>
        <span style="color: #94a3b8;">${cur.toFixed(1)}%</span>
        <span style="color: #64748b;">➔</span>
        <span style="font-weight: 800; color: ${deltaColor};">${post.toFixed(1)}%</span>
        <span style="color: #64748b; font-size: 0.7rem;">(Target ${tgt.toFixed(1)}%)</span>
      </div>
    `;
  }).join('');
}

function renderTargetFundProgression(plan, holdings, bucketTargetsConfig) {
  const container = document.getElementById('rebalanceFundProgressionContainer');
  if (!container) return;

  const sellSide = plan.sell_side || plan.sellSide || {};
  const buySide = plan.buy_side || plan.buySide || {};
  const actualHoldings = holdings || state.holdings || [];
  const targetsConfig = bucketTargetsConfig || state.bucketTargetsConfig || null;

  // 1. Calculate current fund valuations & total portfolio net worth
  const currentFundVal = {};
  const fundNameMap = {};
  let totalNetWorth = 0;

  actualHoldings.forEach(h => {
    const isin = h.asset_id || h.assetId;
    const name = h.asset_name || h.assetName || isin;
    const val = parseFloat(h.current_value || h.currentValue) || 0;
    if (isin) {
      currentFundVal[isin] = val;
      fundNameMap[isin] = name;
      totalNetWorth += val;
    }
  });

  // 2. Calculate sell amounts per fund
  const fundSellMap = {};
  (sellSide.waterfall || []).forEach(tier => {
    (tier.lots || []).forEach(lot => {
      const isin = lot.fundId || lot.fund_id;
      const proceeds = parseFloat(lot.saleProceeds || lot.sale_proceeds) || 0;
      if (isin) {
        fundSellMap[isin] = (fundSellMap[isin] || 0) + proceeds;
      }
    });
  });

  // 3. Calculate buy amounts per fund
  const fundBuyMap = {};
  (buySide.buckets || []).forEach(b => {
    const bucketAlloc = parseFloat(b.amount_allocated ?? b.amountAllocated) || 0;
    const prefFunds = b.fund_breakdown || b.fundBreakdown || [];
    const fundCount = prefFunds.length > 0 ? prefFunds.length : 1;
    prefFunds.forEach(f => {
      const isin = f.fund_id || f.fundId;
      const weight = parseFloat(f.allocation_weight || f.allocationWeight) || (1.0 / fundCount);
      const buyAmt = f.amount !== undefined ? parseFloat(f.amount) : (bucketAlloc * weight);
      if (isin) {
        fundBuyMap[isin] = (fundBuyMap[isin] || 0) + buyAmt;
        if (f.fund_name || f.fundName) fundNameMap[isin] = f.fund_name || f.fundName;
      }
    });
  });

  // 4. Calculate target fund allocation % from targetsConfig
  const plannedMap = {};
  let activeVersion = null;
  if (targetsConfig && targetsConfig.versions && targetsConfig.versions.length > 0) {
    activeVersion = targetsConfig.versions[targetsConfig.versions.length - 1];
  }
  if (activeVersion && activeVersion.targets) {
    activeVersion.targets.forEach(t => {
      const bucketTargetPct = parseFloat(t.target_pct || t.targetPct) || 0;
      const prefFunds = t.preferred_funds || t.preferredFunds || [];
      prefFunds.forEach(pf => {
        const isin = pf.fund_id || pf.fundId;
        const weight = parseFloat(pf.allocation_weight || pf.allocationWeight) || 0;
        const plannedPct = Math.round(bucketTargetPct * weight * 100) / 100;
        if (isin) plannedMap[isin] = plannedPct;
      });
    });
  }

  // 5. Build combined list of all funds grouped by unique shortName to prevent duplicate badges
  const allIsins = new Set([...Object.keys(currentFundVal), ...Object.keys(fundBuyMap), ...Object.keys(plannedMap)]);
  const fundMap = {};

  allIsins.forEach(isin => {
    const rawName = fundNameMap[isin] || isin;
    const shortName = shortenFundName(rawName);
    const curVal = currentFundVal[isin] || 0;
    const sellAmt = fundSellMap[isin] || 0;
    const buyAmt = fundBuyMap[isin] || 0;
    const targetPct = plannedMap[isin] || 0.0;

    if (!fundMap[shortName]) {
      fundMap[shortName] = {
        shortName,
        curVal: 0,
        sellAmt: 0,
        buyAmt: 0,
        targetPct: 0
      };
    }
    fundMap[shortName].curVal += curVal;
    fundMap[shortName].sellAmt += sellAmt;
    fundMap[shortName].buyAmt += buyAmt;
    fundMap[shortName].targetPct = Math.max(fundMap[shortName].targetPct, targetPct);
  });

  const fundItems = Object.values(fundMap).map(f => {
    const postVal = Math.max(0, f.curVal - f.sellAmt + f.buyAmt);
    const curPct = totalNetWorth > 0 ? (f.curVal / totalNetWorth) * 100 : 0;
    const postPct = totalNetWorth > 0 ? (postVal / totalNetWorth) * 100 : 0;
    return {
      shortName: f.shortName,
      curPct,
      postPct,
      targetPct: f.targetPct,
      isTarget: f.targetPct > 0
    };
  });

  // Sort: Target funds first (by targetPct desc), then legacy funds (by curPct desc)
  fundItems.sort((a, b) => {
    if (a.isTarget && !b.isTarget) return -1;
    if (!a.isTarget && b.isTarget) return 1;
    if (a.isTarget && b.isTarget) return b.targetPct - a.targetPct;
    return b.curPct - a.curPct;
  });

  container.innerHTML = fundItems.map(f => {
    let deltaColor = '#34d399'; // Green
    if (f.postPct < f.curPct) deltaColor = '#f87171'; // Red for trim
    if (!f.isTarget) deltaColor = '#64748b'; // Muted for legacy 0% target

    const targetBadgeText = f.isTarget ? `Target ${f.targetPct.toFixed(1)}%` : 'Legacy (0.0%)';

    return `
      <div style="background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 6px; padding: 6px 12px; font-size: 0.75rem; display: flex; align-items: center; gap: 8px;">
        <span style="font-weight: 700; color: #f8fafc;">${f.shortName}:</span>
        <span style="color: #94a3b8;">${f.curPct.toFixed(1)}%</span>
        <span style="color: #64748b;">➔</span>
        <span style="font-weight: 800; color: ${deltaColor};">${f.postPct.toFixed(1)}%</span>
        <span style="color: #64748b; font-size: 0.7rem;">(${targetBadgeText})</span>
      </div>
    `;
  }).join('');
}

function renderRebalanceMicroSankey(sellSide, buySide) {
  const container = document.getElementById('rebalanceSankeyChart');
  if (!container || typeof echarts === 'undefined') return;

  container.style.width = '100%';
  container.style.height = '240px';

  let chart = echarts.getInstanceByDom(container);
  if (!chart) {
    chart = echarts.init(container);
  }

  const nodesMap = new Map();
  const links = [];
  const poolNodeName = 'Rebalance Cash Pool';
  nodesMap.set(poolNodeName, { name: poolNodeName, itemStyle: { color: '#38bdf8' } });

  // 1. Group Sell Lots by Source Fund & Determine Link Color by Tax Regime
  const sellFundProceeds = new Map();
  const sellFundRegimes = new Map();

  const shortenFundName = rawName => {
    if (!rawName) return '';
    return rawName
      .replace(/\s*-\s*Direct Plan Growth\s*\(Non Demat\)/gi, '')
      .replace(/\s*-\s*DIRECT GROWTH PLAN GROWTH OPTION\s*\(Non Demat\)/gi, '')
      .replace(/\s*Direct Plan\s*-\s*Growth/gi, '')
      .replace(/\s*-\s*Direct Plan Growth/gi, '')
      .replace(/\s*Direct Growth Plan Growth Option\s*\(Non Demat\)/gi, '')
      .replace(/\s*-\s*Direct Growth/gi, '')
      .trim();
  };

  (sellSide.waterfall || []).forEach(tier => {
    (tier.lots || []).forEach(lot => {
      const rawName = lot.fundName || lot.fund_name || lot.fundId || lot.fund_id;
      const fName = shortenFundName(rawName);
      const proceeds = parseFloat(lot.saleProceeds || lot.sale_proceeds || 0);
      if (proceeds > 0) {
        sellFundProceeds.set(fName, (sellFundProceeds.get(fName) || 0) + proceeds);
        
        const ti = lot.tax_impact || lot.taxImpact || {};
        const regime = ti.regime || ti.regime_type || lot.tax_term || lot.taxTerm || 'SEC_112A_EXEMPT';
        const currentRegime = sellFundRegimes.get(fName) || 'SEC_112A_EXEMPT';
        if (regime === 'SLAB_RATE_STCG' || currentRegime === 'SLAB_RATE_STCG') {
          sellFundRegimes.set(fName, 'SLAB_RATE_STCG');
        } else if (regime === 'SEC_112A_TAXABLE_12_5' || currentRegime === 'SEC_112A_TAXABLE_12_5') {
          sellFundRegimes.set(fName, 'SEC_112A_TAXABLE_12_5');
        } else {
          sellFundRegimes.set(fName, 'SEC_112A_EXEMPT');
        }
      }
    });
  });

  if (sellFundProceeds.size > 0) {
    sellFundProceeds.forEach((amount, fundName) => {
      const regime = sellFundRegimes.get(fundName);
      let linkColor = '#10b981'; // Green for SEC_112A_EXEMPT
      if (regime === 'SEC_112A_TAXABLE_12_5') linkColor = '#f59e0b'; // Amber for taxable LTCG
      if (regime === 'SLAB_RATE_STCG') linkColor = '#ef4444'; // Red for STCG

      const sellNodeName = `${fundName} (Sell)`;
      nodesMap.set(sellNodeName, { name: sellNodeName, itemStyle: { color: linkColor } });

      links.push({
        source: sellNodeName,
        target: poolNodeName,
        value: amount,
        lineStyle: { color: linkColor, opacity: 0.6 }
      });
    });
  } else {
    const freshCapNode = 'Available Cash';
    nodesMap.set(freshCapNode, { name: freshCapNode, itemStyle: { color: '#10b981' } });
    const poolAmt = parseFloat(buySide.totalToInvest || buySide.total_to_invest || 0);
    if (poolAmt > 0) {
      links.push({ source: freshCapNode, target: poolNodeName, value: poolAmt, lineStyle: { color: '#10b981', opacity: 0.6 } });
    }
  }

  // 2. Tax Friction Node
  const taxSum = sellSide.tax_summary || sellSide.taxSummary || {};
  const estTax = parseFloat(taxSum.total_tax_estimate ?? taxSum.totalTaxEstimate ?? 0);
  if (estTax > 0) {
    const taxNodeName = 'Estimated Tax';
    nodesMap.set(taxNodeName, { name: taxNodeName, itemStyle: { color: '#ef4444' } });
    links.push({ source: poolNodeName, target: taxNodeName, value: estTax, lineStyle: { color: '#ef4444', opacity: 0.7 } });
  }

  // 3. Buy-Side Target Funds
  (buySide.buckets || []).forEach(b => {
    const funds = b.fund_breakdown || b.fundBreakdown || [];
    funds.forEach(f => {
      const rawName = f.fundName || f.fund_name || f.fundId || f.fund_id;
      const fName = shortenFundName(rawName);
      const buyNodeName = `${fName} (Buy)`;
      const amount = parseFloat(f.amount || 0);
      if (amount > 0) {
        nodesMap.set(buyNodeName, { name: buyNodeName, itemStyle: { color: '#38bdf8' } });
        links.push({ source: poolNodeName, target: buyNodeName, value: amount, lineStyle: { color: '#38bdf8', opacity: 0.6 } });
      }
    });
  });

  if (links.length === 0) {
    container.innerHTML = '<div style="text-align: center; color: #64748b; padding-top: 80px;">No capital flow required for active drawdown state</div>';
    return;
  }

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      triggerOn: 'mousemove',
      formatter: params => {
        if (params.dataType === 'node') return `<b>${params.name}</b>`;
        return `Flow: <b>${params.data.source}</b> → <b>${params.data.target}</b><br/>Amount: <b>₹${params.data.value.toLocaleString('en-IN')}</b>`;
      }
    },
    series: [{
      type: 'sankey',
      left: '3%',
      right: '28%',
      top: 15,
      bottom: 15,
      nodeWidth: 14,
      nodeGap: 12,
      emphasis: { focus: 'adjacency' },
      data: Array.from(nodesMap.values()),
      links: links,
      lineStyle: { curveness: 0.5 },
      label: { color: '#f8fafc', fontSize: 11, distance: 6 }
    }]
  };

  chart.setOption(option, true);
  chart.resize();

  if (!container.__ro) {
    container.__ro = new ResizeObserver(() => {
      if (chart) chart.resize();
    });
    container.__ro.observe(container);
  }
}

function renderTacticalActionMatrix(plan) {
  const tbody = document.getElementById('matrixLotTableBody');
  if (!tbody || !plan || !plan.sell_side) return;

  const sellSide = plan.sell_side;
  const buySide = plan.buy_side || {};
  const allLots = [];

  (sellSide.waterfall || []).forEach(tier => {
    (tier.lots || []).forEach(lot => {
      allLots.push({ ...lot, tierLabel: tier.tier_label || tier.tierLabel });
    });
  });

  if (allLots.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="9" style="text-align: center; color: #64748b; padding: 20px;">
          No open lots selected for trade — portfolio drawdown (4.0%) below 10% threshold.
        </td>
      </tr>
    `;
    document.getElementById('matrixLiveProceeds').textContent = '₹0';
    document.getElementById('matrixLiveTaxDrag').textContent = '₹0';
    return;
  }

  const selectedLotIds = new Set(allLots.map(l => l.lot_id || l.lotId));

  function recalculateMetrics() {
    let liveProceeds = 0;
    let liveTax = 0;

    allLots.forEach(lot => {
      const id = lot.lot_id || lot.lotId;
      const rowEl = document.getElementById(`matrix-row-${id}`);
      if (selectedLotIds.has(id)) {
        liveProceeds += parseFloat(lot.sale_proceeds || lot.saleProceeds || 0);
        liveTax += parseFloat(lot.tax_impact?.tax_amount || lot.taxImpact?.taxAmount || 0);
        if (rowEl) {
          rowEl.style.opacity = '1';
          rowEl.style.filter = 'none';
        }
      } else {
        if (rowEl) {
          rowEl.style.opacity = '0.35';
          rowEl.style.filter = 'grayscale(100%)';
        }
      }
    });

    const liveProcEl = document.getElementById('matrixLiveProceeds');
    const liveTaxEl = document.getElementById('matrixLiveTaxDrag');

    if (liveProcEl) liveProcEl.textContent = `₹${Math.round(liveProceeds).toLocaleString('en-IN')}`;
    if (liveTaxEl) liveTaxEl.textContent = `₹${Math.round(liveTax).toLocaleString('en-IN')}`;

    // Reactive buy-side allocation scaling
    renderBuySideAllocationGrid(buySide, liveProceeds);
  }

  tbody.innerHTML = allLots.map(lot => {
    const id = lot.lot_id || lot.lotId;
    const name = lot.fund_name || lot.fundName;
    const acq = lot.acquisition_date || lot.acquisitionDate;
    const days = lot.holding_days || lot.holdingDays;
    const proceeds = parseFloat(lot.sale_proceeds || lot.saleProceeds || 0);
    const cost = parseFloat(lot.cost_basis || lot.costBasis || 0);
    const gain = parseFloat(lot.realized_gain || lot.realizedGain || 0);
    const regime = lot.tax_impact?.regime || lot.taxImpact?.regime || 'SEC_112A_EXEMPT';
    const tax = parseFloat(lot.tax_impact?.tax_amount || lot.taxImpact?.taxAmount || 0);

    let regimeBadge = `<span class="cat-badge cat-EQUITY">EXEMPT</span>`;
    if (regime === 'SLAB_RATE_STCG') {
      regimeBadge = `<span class="cat-badge cat-DEBT_SPECIFIED_50AA">STCG (20%)</span>`;
    } else if (regime === 'SEC_112A_TAXABLE_12_5') {
      regimeBadge = `<span class="cat-badge" style="background: rgba(245, 158, 11, 0.2); color: #f59e0b; border: 1px solid #f59e0b;">LTCG (12.5%)</span>`;
    }

    return `
      <tr id="matrix-row-${id}" style="border-bottom: 1px solid rgba(255,255,255,0.06); transition: all 0.2s ease;">
        <td style="text-align: center;">
          <input type="checkbox" class="matrix-lot-cb" data-lot-id="${id}" checked style="accent-color: #06b6d4; cursor: pointer;">
        </td>
        <td style="font-weight: 600; color: #f8fafc;">${name}</td>
        <td style="color: #94a3b8; font-size: 0.75rem;">${acq}</td>
        <td style="color: #94a3b8; font-size: 0.75rem;">${days}d</td>
        <td style="color: #cbd5e1;">₹${Math.round(cost).toLocaleString('en-IN')}</td>
        <td style="font-weight: 700; color: #10b981;">₹${Math.round(proceeds).toLocaleString('en-IN')}</td>
        <td style="color: #38bdf8;">+₹${Math.round(gain).toLocaleString('en-IN')}</td>
        <td>${regimeBadge}</td>
        <td style="color: ${tax > 0 ? '#ef4444' : '#34d399'}; font-weight: 700;">₹${Math.round(tax).toLocaleString('en-IN')}</td>
      </tr>
    `;
  }).join('');

  // Attach Checkbox Change Listeners
  document.querySelectorAll('.matrix-lot-cb').forEach(cb => {
    cb.addEventListener('change', (e) => {
      const id = e.target.getAttribute('data-lot-id');
      if (e.target.checked) {
        selectedLotIds.add(id);
      } else {
        selectedLotIds.delete(id);
      }
      recalculateMetrics();
    });
  });

  const selectAllCb = document.getElementById('matrixSelectAllLots');
  if (selectAllCb) {
    selectAllCb.checked = true;
    selectAllCb.onclick = (e) => {
      const isChecked = e.target.checked;
      document.querySelectorAll('.matrix-lot-cb').forEach(cb => {
        cb.checked = isChecked;
        const id = cb.getAttribute('data-lot-id');
        if (isChecked) selectedLotIds.add(id);
        else selectedLotIds.delete(id);
      });
      recalculateMetrics();
    };
  }

  const btnExecute = document.getElementById('btnExecuteTradeOverride');
  if (btnExecute) {
    btnExecute.onclick = () => {
      alert(`⚡ Trade Execution Override Confirmed!\n\nSelected Lots: ${selectedLotIds.size} of ${allLots.length}\nExecuting trade payload back to core-node engine.`);
    };
  }

  // Keyboard shortcut: Ctrl + Enter to execute override
  window.onkeydown = (e) => {
    if (e.ctrlKey && e.key === 'Enter') {
      e.preventDefault();
      if (btnExecute) btnExecute.click();
    }
  };

  recalculateMetrics();
}

document.addEventListener('DOMContentLoaded', () => {
  populateFundDropdowns();
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
      window.openLumpsumModal && window.openLumpsumModal();
    });
  }

  const btnDaily = document.getElementById('btnNetWorthDaily');
  const btnMonthly = document.getElementById('btnNetWorthMonthly');

  if (btnDaily && btnMonthly) {
    btnDaily.addEventListener('click', () => {
      btnDaily.classList.add('active');
      btnDaily.style.background = 'rgba(56, 189, 248, 0.2)';
      btnDaily.style.color = '#38bdf8';
      btnDaily.style.borderColor = '#38bdf8';

      btnMonthly.classList.remove('active');
      btnMonthly.style.background = 'rgba(255,255,255,0.05)';
      btnMonthly.style.color = '#94a3b8';
      btnMonthly.style.borderColor = 'rgba(255,255,255,0.1)';

      loadNetWorthTrend(false);
    });

    btnMonthly.addEventListener('click', () => {
      btnMonthly.classList.add('active');
      btnMonthly.style.background = 'rgba(56, 189, 248, 0.2)';
      btnMonthly.style.color = '#38bdf8';
      btnMonthly.style.borderColor = '#38bdf8';

      btnDaily.classList.remove('active');
      btnDaily.style.background = 'rgba(255,255,255,0.05)';
      btnDaily.style.color = '#94a3b8';
      btnDaily.style.borderColor = 'rgba(255,255,255,0.1)';

      loadNetWorthTrend(true);
    });
  }

  loadActionRecommendations();
  render2FundVennDiagram();
  loadNetWorthTrend(false);
  loadUnifiedRebalancePlan('INDUCED');
});

export function renderSchemeGroupedTaxLotsUI(holdings, containerId = 'groupedTaxLotsContainer') {
  const container = document.getElementById(containerId);
  if (!container) return;

  if (!holdings || holdings.length === 0) {
    container.innerHTML = `<div style="color:#94a3b8; font-size:13px; padding:16px; text-align:center;">No open holdings or tax lots found in ledger.</div>`;
    return;
  }

  const html = holdings.map((h, schemeIdx) => {
    const isin = h.asset_id || h.assetId || '';
    const name = h.asset_name || h.assetName || isin;
    const category = h.category || 'EQUITY';
    const inv = Math.round(parseFloat(h.invested_value || h.investedValue) || 0);
    const cur = Math.round(parseFloat(h.current_value || h.currentValue) || 0);
    const gain = Math.round(parseFloat(h.unrealized_gain || h.unrealizedGain) || 0);
    const gainPct = h.unrealized_gain_pct || h.unrealizedGainPct || '0.00';
    const lots = h.lots || [];

    const ltcgLots = lots.filter(l => l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg);
    const stcgLots = lots.filter(l => !(l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg));

    let lotRowsHtml = lots.map((l, lotIdx) => {
      const acqDate = l.acquisition_date || l.acquisitionDate;
      const units = parseFloat(l.remaining_units || l.remainingUnits || '0');
      const costPerUnit = parseFloat(l.cost_per_unit || l.costPerUnit || '0');
      const totalCost = Math.round(units * costPerUnit);
      const lotVal = Math.round(parseFloat(l.current_value || l.currentValue || '0'));
      const lotGain = Math.round(parseFloat(l.unrealized_gain || l.unrealizedGain || '0'));
      const daysHeld = l.holding_days !== undefined ? l.holding_days : l.holdingDays;
      const daysToLtcg = l.days_to_ltcg !== undefined ? l.days_to_ltcg : l.daysToLtcg;
      const isLtcg = l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg;

      const badgeStyle = isLtcg
        ? 'background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid #10b981;'
        : 'background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid #f59e0b;';
      const badgeText = isLtcg ? 'LTCG Free' : `STCG Locked (${daysToLtcg}d to LTCG)`;

      return `
        <tr style="border-bottom: 1px solid rgba(255,255,255,0.05); font-size:12px;">
          <td style="padding:10px 12px; font-weight:600; color:#f8fafc;">Lot #${lotIdx + 1}</td>
          <td style="padding:10px 12px; color:#cbd5e1;">${acqDate} <span style="font-size:10px; color:#64748b;">(${daysHeld}d)</span></td>
          <td style="padding:10px 12px; color:#cbd5e1;" class="font-mono">${units.toFixed(4)}</td>
          <td style="padding:10px 12px; color:#cbd5e1;" class="font-mono">₹${costPerUnit.toFixed(2)}</td>
          <td style="padding:10px 12px; color:#cbd5e1;" class="font-mono">${formatINR(totalCost)}</td>
          <td style="padding:10px 12px; color:#38bdf8;" class="font-mono">${formatINR(lotVal)}</td>
          <td style="padding:10px 12px; font-weight:700; color:${lotGain >= 0 ? '#10b981' : '#ef4444'};" class="font-mono">${lotGain >= 0 ? '+' : ''}${formatINR(lotGain)}</td>
          <td style="padding:10px 12px;">
            <span style="${badgeStyle} font-size:10px; padding:3px 8px; border-radius:4px; font-weight:700;">${badgeText}</span>
          </td>
        </tr>
      `;
    }).join('');

    return `
      <div class="scheme-lot-accordion-card" style="background: rgba(15, 23, 42, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; overflow: hidden; margin-bottom: 12px;">
        <div class="accordion-header" onclick="window.toggleSchemeLotCard('${containerId}_${schemeIdx}')" style="padding: 16px; display: flex; justify-content: space-between; align-items: center; cursor: pointer; background: rgba(255,255,255,0.02);">
          <div style="flex: 1;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <h3 style="margin: 0; font-size: 1rem; color: #f8fafc;">${name}</h3>
              <span class="cat-badge cat-${category}">${category.replace('_SPECIFIED_50AA', '')}</span>
            </div>
            <div style="font-size: 11px; color: #94a3b8; margin-top: 4px;" class="font-mono">ISIN: ${isin}</div>
          </div>

          <div style="display: flex; gap: 12px; align-items: center; margin-right: 16px;">
            <span style="background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid #10b981; font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;">${ltcgLots.length} LTCG Lots</span>
            <span style="background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid #f59e0b; font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;">${stcgLots.length} STCG Lots</span>
            <div style="text-align: right;">
              <div style="font-size: 14px; font-weight: 700; color: #38bdf8;" class="font-mono">${formatINR(cur)}</div>
              <div style="font-size: 11px; color: ${gain >= 0 ? '#10b981' : '#ef4444'};" class="font-mono">${gain >= 0 ? '+' : ''}${formatINR(gain)} (${gainPct}%)</div>
            </div>
          </div>
          <div id="schemeAccIcon_${containerId}_${schemeIdx}" style="color: #06b6d4; font-size: 16px; font-weight: bold;">▶</div>
        </div>

        <div id="schemeAccBody_${containerId}_${schemeIdx}" style="display: none; padding: 0 16px 16px 16px; border-top: 1px solid rgba(255,255,255,0.06);">
          <div style="overflow-x: auto; margin-top: 12px;">
            <table style="width: 100%; border-collapse: collapse; text-align: left;">
              <thead>
                <tr style="border-bottom: 1px solid rgba(255,255,255,0.1); font-size: 11px; color: #94a3b8; text-transform: uppercase;">
                  <th style="padding: 8px 12px;">Lot</th>
                  <th style="padding: 8px 12px;">Acquisition Date</th>
                  <th style="padding: 8px 12px;">Units</th>
                  <th style="padding: 8px 12px;">Cost NAV</th>
                  <th style="padding: 8px 12px;">Invested Cost</th>
                  <th style="padding: 8px 12px;">Current Value</th>
                  <th style="padding: 8px 12px;">Unrealized Gain</th>
                  <th style="padding: 8px 12px;">Tax Classification</th>
                </tr>
              </thead>
              <tbody>
                ${lotRowsHtml}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    `;
  }).join('');

  container.innerHTML = html;
}

window.toggleSchemeLotCard = (key) => {
  const body = document.getElementById(`schemeAccBody_${key}`);
  const icon = document.getElementById(`schemeAccIcon_${key}`);
  if (body) {
    const isHidden = body.style.display === 'none';
    body.style.display = isHidden ? 'block' : 'none';
    if (icon) icon.textContent = isHidden ? '▼' : '▶';
  }
};

if (typeof window !== 'undefined') {
  window.loadOverlapAnalytics = loadOverlapAnalytics;
  window.loadUpSetAnalytics = loadUpSetAnalytics;
  window.loadActionRecommendations = loadActionRecommendations;
  window.render2FundVennDiagram = render2FundVennDiagram;
  window.loadUnifiedRebalancePlan = loadUnifiedRebalancePlan;
  window.renderSchemeGroupedTaxLotsUI = renderSchemeGroupedTaxLotsUI;

  window.openLumpsumModal = () => {
    const backdrop = document.getElementById('lumpsumModalBackdrop');
    const modal = document.getElementById('lumpsumModal');
    if (backdrop) backdrop.style.display = 'block';
    if (modal) modal.style.display = 'block';
  };

  window.closeLumpsumModal = () => {
    const backdrop = document.getElementById('lumpsumModalBackdrop');
    const modal = document.getElementById('lumpsumModal');
    if (backdrop) backdrop.style.display = 'none';
    if (modal) modal.style.display = 'none';
  };

  window.submitLumpsumSim = () => {
    const input = document.getElementById('lumpsumAmountInput');
    const amt = parseFloat(input ? input.value : '100000') || 100000;
    const selectedOpt = document.querySelector('input[name="lumpsumRebalanceOption"]:checked');
    const includeRebal = selectedOpt ? selectedOpt.value === 'true' : false;

    window.closeLumpsumModal();
    loadUnifiedRebalancePlan('MANUAL_LUMPSUM', amt, includeRebal);
  };
}



