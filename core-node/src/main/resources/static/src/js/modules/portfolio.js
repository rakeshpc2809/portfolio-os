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

export function renderAllocationChart(allocations) {
  if (state.charts.allocChart) state.charts.allocChart.dispose();
  if (!allocations || allocations.length === 0) return;

  const total = allocations.reduce((sum, a) => sum + (parseFloat(a.currentValue) || 0), 0);
  
  const main = [];
  let othersVal = 0;
  let othersCount = 0;

  allocations.forEach(a => {
    const val = parseFloat(a.currentValue) || 0;
    const pct = total > 0 ? (val / total) * 100 : 0;
    if (pct < 3.0 && allocations.length > 6) {
      othersVal += val;
      othersCount++;
    } else {
      main.push({
        name: a.assetName.length > 25 ? a.assetName.substring(0, 23) + '...' : a.assetName,
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
  if (idleVal && data.unallocatedCash) {
    idleVal.textContent = formatINR(data.unallocatedCash);
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

  if (statusPill) {
    statusPill.textContent = data.status === 'ON_TRACK' ? 'ON TRACK' : `SHORT BY ${formatINR(data.shortageOrSurplusAmount)}`;
    statusPill.className = `fire-status-pill ${data.status === 'ON_TRACK' ? 'on-track' : 'short'}`;
  }

  if (scenarioLabel) scenarioLabel.textContent = `Scenario: ${data.activeScenarioLabel}`;

  if (investableNw) investableNw.textContent = formatINR(data.fireInvestableNetWorth);
  if (reqCorpus) reqCorpus.textContent = `₹ ${(parseFloat(data.requiredCorpus) / 10000000).toFixed(2)} Cr`;
  if (projCorpus) projCorpus.textContent = `₹ ${(parseFloat(data.projectedCorpusAtTargetAge) / 10000000).toFixed(2)} Cr`;
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

  if (drawdownTag && data.drawdownStatus) {
    const dd = data.drawdownStatus;
    drawdownTag.textContent = `${dd.benchmarkName}: ${dd.drawdownPct}% Drawdown`;
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
}
