import { API_BASE, fetchJson } from '../api.js';
import { state } from '../state.js';
import { formatINR } from '../utils.js';

export function updatePortfolioSummary(summary) {
  const netWorthVal = document.querySelector('.net-worth-val');
  const gainText = document.querySelector('.net-worth-gain');
  const subText = document.querySelector('.net-worth-sub');
  const xirrVal = document.querySelector('.xirr-val');

  if (netWorthVal && summary.totalCurrentValue) {
    netWorthVal.textContent = formatINR(summary.totalCurrentValue);
    netWorthVal.classList.remove('skeleton');
  }
  if (gainText && summary.totalUnrealizedGain) {
    const gain = Math.round(parseFloat(summary.totalUnrealizedGain) || 0);
    const sign = gain >= 0 ? '+' : '';
    gainText.textContent = `Unrealized gain: ${sign}${formatINR(gain)}`;
    gainText.className = `metric-delta ${gain >= 0 ? 'positive' : 'negative'}`;
  }
  if (subText && summary.activeHoldingCount !== undefined) {
    subText.innerHTML = `Active Holdings: <strong>${summary.activeHoldingCount} Schemes</strong>`;
  }
  if (xirrVal && summary.xirrPercentage) {
    xirrVal.textContent = summary.xirrPercentage;
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
    const inv = Math.round(parseFloat(h.investedValue) || 0);
    const cur = Math.round(parseFloat(h.currentValue) || 0);
    const gain = Math.round(parseFloat(h.unrealizedGain) || 0);
    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';

    html += `
      <tr class="holding-row" onclick="toggleLotDetails('${idx}')">
        <td style="font-weight:600;">${h.assetName}</td>
        <td><span class="cat-badge cat-${h.category}">${h.category.replace('_SPECIFIED_50AA', '')}</span></td>
        <td class="font-mono">${formatINR(inv)}</td>
        <td class="font-mono" style="font-weight:600;">${formatINR(cur)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)} (${h.unrealizedGainPct}%)</td>
        <td class="font-mono">${h.allocationPct}%</td>
        <td><button class="pill-btn">${h.lots.length} Lots ▼</button></td>
      </tr>
      <tr id="lotRow-${idx}" style="display: none;">
        <td colspan="7" class="lot-expansion-td">
          <table class="lot-subtable">
            <thead>
              <tr>
                <th>Acq Date</th>
                <th>Units</th>
                <th>Cost Basis</th>
                <th>Unrealized Gain</th>
                <th>Days Held</th>
                <th>Tax Term</th>
              </tr>
            </thead>
            <tbody>
              ${h.lots.map(l => `
                <tr>
                  <td>${l.acquisitionDate}</td>
                  <td class="font-mono">${l.remainingUnits}</td>
                  <td class="font-mono">${formatINR(parseFloat(l.costPerUnit) * parseFloat(l.remainingUnits))}</td>
                  <td class="font-mono" style="${parseFloat(l.unrealizedGain) >= 0 ? 'color: #10b981;' : 'color: #ef4444;'}">
                    ${parseFloat(l.unrealizedGain) >= 0 ? '+' : ''}${formatINR(l.unrealizedGain)}
                  </td>
                  <td>${l.holdingDays}d</td>
                  <td><span class="cat-badge ${l.isLtcg ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${l.isLtcg ? 'LTCG' : 'STCG (' + (l.daysToLtcg > 0 ? l.daysToLtcg + 'd left' : 'Always') + ')'}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </td>
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

export function renderPerformanceChart(points) {
  const container = document.getElementById('performanceChart');
  if (!container || !points || points.length === 0 || !window.echarts) return;

  if (state.charts.perfChart) state.charts.perfChart.dispose();
  state.charts.perfChart = window.echarts.init(container);

  const dates = points.map(p => p.date);
  const investedValues = points.map(p => parseFloat(p.invested) || 0);
  const marketValues = points.map(p => parseFloat(p.currentValue || p.marketValue || p.invested) || 0);

  const option = {
    backgroundColor: 'transparent',
    legend: {
      data: ['Market Value', 'Invested Amount'],
      textStyle: { color: '#94a3b8', fontSize: 11 },
      top: 0,
      right: 10
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0f172a',
      borderColor: 'rgba(255, 255, 255, 0.1)',
      textStyle: { color: '#f8fafc' },
      formatter: (params) => {
        let result = `<strong>${params[0].name}</strong><br/>`;
        params.forEach(p => {
          const val = Math.round(p.value);
          result += `${p.seriesName}: <strong>${formatINR(val)}</strong><br/>`;
        });
        return result;
      }
    },
    grid: { top: 30, right: 20, bottom: 30, left: 65 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } },
      axisLabel: { color: '#64748b', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.04)' } },
      axisLabel: {
        color: '#64748b',
        fontSize: 10,
        formatter: (val) => `₹ ${(val / 100000).toFixed(1)}L`
      }
    },
    series: [
      {
        name: 'Market Value',
        data: marketValues,
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#10b981', width: 3 },
        areaStyle: {
          color: new window.echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16, 185, 129, 0.35)' },
            { offset: 1, color: 'rgba(16, 185, 129, 0.0)' }
          ])
        }
      },
      {
        name: 'Invested Amount',
        data: investedValues,
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#06b6d4', width: 2, type: 'dashed' }
      }
    ]
  };
  state.charts.perfChart.setOption(option);
}

export function renderPieChart(containerId, data) {
  const container = document.getElementById(containerId);
  if (!container || !data || data.length === 0 || !window.echarts) return null;

  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}: ₹ {c} ({d}%)' },
    series: [{
      type: 'pie',
      radius: ['45%', '75%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#070a12', borderWidth: 2 },
      label: { show: false },
      data: data
    }]
  };
  instance.setOption(option);
  return instance;
}

export function renderAllocationChart(allocations) {
  if (state.charts.allocChart) state.charts.allocChart.dispose();

  const top6 = allocations.slice(0, 6);
  const remaining = allocations.slice(6);

  const data = top6.map(a => ({
    name: a.assetName.length > 22 ? a.assetName.substring(0, 20) + '...' : a.assetName,
    value: parseFloat(a.currentValue) || 0
  }));

  if (remaining.length > 0) {
    const othersVal = remaining.reduce((sum, a) => sum + (parseFloat(a.currentValue) || 0), 0);
    if (othersVal > 0) {
      data.push({
        name: `Others (${remaining.length})`,
        value: othersVal
      });
    }
  }

  state.charts.allocChart = renderPieChart('allocationChart', data);
}

export function renderCategoryChart(catAllocations) {
  if (state.charts.categoryChart) state.charts.categoryChart.dispose();

  const data = catAllocations.map(c => ({
    name: c.categoryName,
    value: parseFloat(c.currentValue) || 0
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

  if (badge) {
    badge.textContent = data.isRebalanceWindowOpen ? 'WINDOW OPEN: EXECUTE REBALANCE' : `SCHEDULED WINDOW: ${data.nextScheduledWindow}`;
    badge.style.color = data.isRebalanceWindowOpen ? '#10b981' : '#06b6d4';
  }

  const proceeds = Math.round(parseFloat(data.totalProceeds) || 256200);
  const taxDrag = Math.round(parseFloat(data.totalTaxDrag) || 0);

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

  for (const alloc of data.proRataAllocations) {
    const amt = Math.round(parseFloat(alloc.deploymentAmount) || 0);
    html += `
      <tr>
        <td style="font-weight:600;">${alloc.assetName}</td>
        <td><span class="days-badge">${alloc.sipWeightPct}%</span></td>
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

  if (rebTaxDrag && data.totalTaxDrag) {
    rebTaxDrag.textContent = formatINR(data.totalTaxDrag);
  }
  if (rebEffRate && data.effectiveTaxRatePct) {
    rebEffRate.textContent = data.effectiveTaxRatePct;
  }
  if (rebLtcgHarvested && data.ltcgExemptionHarvested) {
    rebLtcgHarvested.textContent = formatINR(data.ltcgExemptionHarvested);
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
  const allocList = document.getElementById('goalAllocList');

  if (idleVal && data.unallocatedCash) {
    idleVal.textContent = formatINR(data.unallocatedCash);
    idleVal.classList.remove('skeleton');
  }

  if (allocList && data.goalAllocations) {
    let html = '';
    data.goalAllocations.forEach(a => {
      html += `
        <div class="goal-row">
          <div>
            <strong>${a.goalTag}</strong> — <span class="text-muted">${a.holdingName}</span>
          </div>
          <div class="font-mono">${formatINR(a.allocatedAmount)}</div>
        </div>
      `;
    });

    html += `
      <div class="goal-row idle-row">
        <div>
          <strong style="color:var(--cyan-bright);">UNALLOCATED (SITTING IDLE)</strong>
        </div>
        <div class="font-mono highlight-cyan" style="font-weight:700;">${formatINR(data.unallocatedCash)}</div>
      </div>
    `;

    allocList.innerHTML = html;
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
  const totalNw = document.getElementById('fireTotalNw');
  const investableNw = document.getElementById('fireInvestableNw');
  const reqCorpus = document.getElementById('fireRequiredCorpus');
  const projCorpus = document.getElementById('fireProjectedCorpus');
  const reviewBanner = document.getElementById('fireReviewBanner');

  if (statusPill) {
    statusPill.textContent = data.status === 'ON_TRACK' ? 'ON TRACK' : `SHORT BY ${formatINR(data.shortageOrSurplusAmount)}`;
    statusPill.className = `fire-status-pill ${data.status === 'ON_TRACK' ? 'on-track' : 'short'}`;
  }

  if (scenarioLabel) scenarioLabel.textContent = `Scenario: ${data.activeScenarioLabel}`;

  if (totalNw) totalNw.textContent = formatINR(data.totalNetWorth);
  if (investableNw) investableNw.textContent = formatINR(data.fireInvestableNetWorth);
  if (reqCorpus) reqCorpus.textContent = `₹ ${(parseFloat(data.requiredCorpus) / 10000000).toFixed(2)} Cr`;
  if (projCorpus) projCorpus.textContent = `₹ ${(parseFloat(data.projectedCorpusAtTargetAge) / 10000000).toFixed(2)} Cr`;

  if (reviewBanner) {
    reviewBanner.style.display = data.reviewDatePassed ? 'block' : 'none';
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
  const recList = document.getElementById('bucketRecList');

  if (drawdownTag && data.drawdownStatus) {
    const dd = data.drawdownStatus;
    drawdownTag.textContent = `${dd.benchmarkName}: ${dd.drawdownPct}% Drawdown (Rungs: ${dd.activeRungsFired.length ? dd.activeRungsFired.join('%, ') + '%' : 'None'})`;
  }

  if (bucketGrid && data.bucketStatuses) {
    let html = '';
    data.bucketStatuses.forEach(b => {
      const nameFmt = b.bucket.replace('_', ' ');
      html += `
        <div class="bucket-card ${b.isDrifted ? 'drifted' : ''}">
          <div class="bucket-card-header">
            <span class="bucket-name">${nameFmt}</span>
            <span class="drift-badge ${b.isDrifted ? 'warn' : 'ok'}">${b.isDrifted ? 'Drift: ' + b.driftPct + '%' : 'Target OK'}</span>
          </div>
          <div class="font-mono" style="font-size:16px; font-weight:700;">${formatINR(b.currentValue)}</div>
          <div class="text-muted" style="font-size:11px; margin-top:4px;">Current: ${b.currentPct}% · Target: ${b.targetPct}%</div>
        </div>
      `;
    });
    bucketGrid.innerHTML = html;
  }

  if (recList && data.recommendations) {
    if (data.recommendations.length === 0) {
      recList.innerHTML = `<div style="color:var(--text-muted); font-size:12px; padding:6px 0;">All bucket allocations are balanced within the 5% drift band. No rebalance needed.</div>`;
      return;
    }

    let html = '';
    data.recommendations.forEach(r => {
      const isBuy = r.action === 'BUY';
      html += `
        <div class="reb-stat" style="padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,0.03);">
          <span>
            <strong style="${isBuy ? 'color:var(--green-positive);' : 'color:var(--amber-warn);'}">[${r.action}]</strong>
            <strong>${r.assetName}</strong> (${r.bucket.replace('_', ' ')})
            <span class="text-muted" style="font-size:11px;">via ${r.triggerType}</span>
          </span>
          <span class="font-mono" style="font-weight:600;">${formatINR(r.amount)} ${r.estimatedTaxDrag !== '0.00' ? '(Tax Drag: ₹' + r.estimatedTaxDrag + ')' : ''}</span>
        </div>
      `;
    });
    recList.innerHTML = html;
  }
}

export function initConfigurator() {
  const inputs = document.querySelectorAll('.config-input');
  inputs.forEach(input => {
    input.addEventListener('input', updateConfiguratorCalculations);
  });
}

export function updateConfiguratorCalculations() {
  const fireAge = parseFloat(document.getElementById('cfgFireAge')?.value) || 45;
  const monthlyExpense = parseFloat(document.getElementById('cfgMonthlyExpense')?.value) || 60000;
  const swrPct = parseFloat(document.getElementById('cfgSwrPct')?.value) || 3.0;

  const eqCore = parseFloat(document.getElementById('cfgEquityCorePct')?.value) || 45;
  const eqSat = parseFloat(document.getElementById('cfgEquitySatPct')?.value) || 25;
  const gold = parseFloat(document.getElementById('cfgGoldPct')?.value) || 15;
  const liquid = parseFloat(document.getElementById('cfgLiquidPct')?.value) || 15;

  const totalTargetPct = eqCore + eqSat + gold + liquid;
  const msgEl = document.getElementById('configValidationMsg');
  if (msgEl) {
    if (Math.abs(totalTargetPct - 100) > 0.01) {
      msgEl.style.color = '#ef4444';
      msgEl.style.background = 'rgba(239, 68, 68, 0.1)';
      msgEl.textContent = `⚠️ Target percentages sum to ${totalTargetPct}% (Must equal 100%).`;
    } else {
      msgEl.style.color = 'var(--cyan-bright)';
      msgEl.style.background = 'rgba(34, 211, 238, 0.1)';
      msgEl.textContent = `✓ Allocation targets sum to 100%. Calculations updated live.`;
    }
  }

  const requiredCorpus = Math.round((monthlyExpense * 12) / (swrPct / 100));
  const fireReqEl = document.getElementById('fireRequiredCorpus');
  const fireExpSub = document.getElementById('fireExpenseSub');
  const scenarioLbl = document.getElementById('fireScenarioLabel');

  if (fireReqEl) {
    fireReqEl.textContent = formatINR(requiredCorpus);
  }
  if (fireExpSub) {
    fireExpSub.textContent = `${swrPct.toFixed(1)}% SWR @ ₹${Math.round(monthlyExpense/1000)}k/mo`;
  }
  if (scenarioLbl) {
    scenarioLbl.textContent = `Target Age ${fireAge} • Custom`;
  }
}
