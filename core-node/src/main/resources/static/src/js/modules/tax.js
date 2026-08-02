import { API_BASE, fetchJson } from '../api.js';
import { state } from '../state.js';
import { formatINR } from '../utils.js';

export async function fetchTaxMetrics() {
  try {
    const data = await fetchJson(`${API_BASE}/tax/exemption-status?fy=${state.currentFy}`).catch(() => null);
    if (data) {
      updateExemptionMeter(data);
    }

    const report = await fetchJson(`${API_BASE}/tax/reports/itr2?fy=${state.currentFy}`).catch(() => null);
    if (report) {
      updateReportMetrics(report);
    }
  } catch (e) {
    console.error('Error fetching tax metrics:', e);
  }
}

export function updateExemptionMeter(data) {
  const meterVal = document.querySelector('.ltcg-meter-val');
  const fill = document.querySelector('.progress-fill-gradient');
  const pctText = document.querySelector('.meter-meta .pct-used');
  const remainingText = document.querySelector('.meter-meta .remaining');

  if (meterVal && fill && remainingText) {
    const used = Math.round(parseFloat(data.exemptionUsed) || 0);
    const limit = Math.round(parseFloat(data.exemptionLimit) || 125000);
    const pct = Math.min(100, Math.round((used / limit) * 100));

    meterVal.innerHTML = `${formatINR(used)} <span class="sub-limit">/ 1.25L</span>`;
    meterVal.classList.remove('skeleton');
    fill.style.width = `${pct}%`;
    if (pctText) pctText.textContent = `${pct}% Used`;
    remainingText.textContent = `${formatINR(limit - used)} Available`;
  }
}

export function updateReportMetrics(report) {
  const stcgVal = document.querySelector('.stcg-val');
  if (stcgVal && report.totalRealizedStcg) {
    stcgVal.textContent = formatINR(report.totalRealizedStcg);
    stcgVal.classList.remove('skeleton');
  }
}

export async function fetchDecisionRadar() {
  try {
    const opportunities = await fetchJson(`${API_BASE}/tax/harvest-opportunities`).catch(() => []);
    const ladder = await fetchJson(`${API_BASE}/tax/maturation-ladder`).catch(() => []);
    const antigravityData = await fetchJson(`${API_BASE}/portfolio/antigravity`).catch(() => null);

    renderDecisionRadar(opportunities, ladder, antigravityData);
  } catch (e) {
    console.error('Error fetching decision radar:', e);
  }
}

export function renderDecisionRadar(opportunities, ladder, antigravityData) {
  const listContainer = document.querySelector('.radar-list');
  if (!listContainer) return;

  let html = '';

  if (antigravityData && antigravityData.antigravityAssets && antigravityData.antigravityAssets.length > 0) {
    for (const ag of antigravityData.antigravityAssets) {
      html += `
        <div class="radar-card info-border" style="border-left: 3px solid #06b6d4; background: rgba(6, 182, 212, 0.08);">
          <div class="radar-icon info">🚀</div>
          <div class="radar-content">
            <div class="radar-title" style="color:#06b6d4;">ANTIGRAVITY DETECTED (${ag.assetName})</div>
            <div class="radar-desc">Beta: <strong>${ag.beta}</strong> | 30d TWR: <strong>+${ag.twr30dPct}%</strong> during market drawdown (${antigravityData.marketDrawdownPct}%). ${ag.recommendation}</div>
          </div>
          <span class="antigravity-badge">🚀 Low Beta + Alpha</span>
        </div>
      `;
    }
  }

  if (opportunities && opportunities.length > 0) {
    for (const opp of opportunities.slice(0, 2)) {
      const loss = Math.round(parseFloat(opp.potentialHarvestableLoss) || 0);
      html += `
        <div class="radar-card warning-border">
          <div class="radar-icon warning">⚡</div>
          <div class="radar-content">
            <div class="radar-title">Tax-Loss Harvesting Opportunity</div>
            <div class="radar-desc">Harvest <strong>${formatINR(loss)}</strong> loss in <em>${opp.assetName}</em> before March 31.</div>
          </div>
          <span class="days-badge">Harvest Now</span>
        </div>
      `;
    }
  }

  if (ladder && ladder.length > 0) {
    for (const mat of ladder.slice(0, 2)) {
      html += `
        <div class="radar-card maturation-border">
          <div class="radar-icon maturation">⏳</div>
          <div class="radar-content">
            <div class="radar-title">LTCG Tax Maturation Coming Up</div>
            <div class="radar-desc">Lot of <em>${mat.assetName}</em> (${mat.remainingUnits} units) becomes <strong>LTCG</strong> on ${mat.targetLtcgDate}.</div>
          </div>
          <span class="days-badge">Wait ${mat.daysRemainingToLtcg} Days</span>
        </div>
      `;
    }
  }

  if (!html) {
    html = `
      <div class="radar-card info-border">
        <div class="radar-icon info">✓</div>
        <div class="radar-content">
          <div class="radar-title">Portfolio Tax Status Optimal</div>
          <div class="radar-desc">No immediate tax-loss harvesting or pending LTCG transitions in the next 90 days.</div>
        </div>
        <span class="days-badge">Optimum</span>
      </div>
    `;
  }

  listContainer.innerHTML = html;
}

export async function fetchRealizedLog() {
  try {
    const logs = await fetchJson(`${API_BASE}/tax/realized-log?fy=${state.currentFy}`).catch(() => []);
    renderRealizedLogTable(logs);
  } catch (e) {
    console.error('Error fetching realized log:', e);
  }
}

export function renderRealizedLogTable(logs) {
  const tableBody = document.querySelector('#realizedLogTable tbody');
  if (!tableBody) return;

  if (!logs || logs.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="8" style="text-align:center; color:#64748b;">No realized disposals recorded for ${state.currentFy}.</td></tr>`;
    return;
  }

  const fragment = document.createDocumentFragment();
  const template = document.createElement('template');

  let html = '';
  logs.forEach(l => {
    const gain = Math.round(parseFloat(l.realizedGain) || 0);
    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';

    html += `
      <tr>
        <td>${l.disposalDate}</td>
        <td>${l.acquisitionDate}</td>
        <td style="font-weight:600;">${l.assetName}</td>
        <td class="font-mono">${l.unitsMatched}</td>
        <td class="font-mono">${formatINR(l.saleProceeds)}</td>
        <td class="font-mono">${formatINR(l.costBasis)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)}</td>
        <td><span class="cat-badge ${l.taxTerm === 'LONG_TERM' ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${l.taxTerm}</span></td>
      </tr>
    `;
  });

  template.innerHTML = html;
  fragment.appendChild(template.content);
  tableBody.replaceChildren(fragment);
}
