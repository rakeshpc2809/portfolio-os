import { API_BASE, fetchJson } from "../api.js";
import { state } from "../state.js";
import { formatINR } from "../utils.js";

export async function fetchTaxMetrics() {
  try {
    const data = await fetchJson(`${API_BASE}/tax/exemption-status?fy=${state.currentFy}`).catch(
      () => null,
    );
    if (data) {
      updateExemptionMeter(data);
    }

    const report = await fetchJson(`${API_BASE}/tax/reports/itr2?fy=${state.currentFy}`).catch(
      () => null,
    );
    if (report) {
      updateReportMetrics(report);
    }
  } catch (e) {
    console.error("Error fetching tax metrics:", e);
  }
}

export function updateExemptionMeter(data) {
  const meterVal = document.querySelector(".ltcg-meter-val");
  const fill = document.querySelector(".progress-fill-gradient");
  const pctText = document.querySelector(".meter-meta .pct-used");
  const remainingText = document.querySelector(".meter-meta .remaining");

  const usedVal = data.exemption_used || data.exemptionUsed;
  const limitVal = data.exemption_limit || data.exemptionLimit;

  if (meterVal && fill && remainingText) {
    const used = Math.round(parseFloat(usedVal) || 0);
    const limit = Math.round(parseFloat(limitVal) || 125000);
    const pct = Math.min(100, Math.round((used / limit) * 100));

    meterVal.innerHTML = `${formatINR(used)} <span class="sub-limit">/ 1.25L</span>`;
    meterVal.classList.remove("skeleton");
    fill.style.width = `${pct}%`;
    if (pctText) pctText.textContent = `${pct}% Used`;
    remainingText.textContent = `${formatINR(limit - used)} Available`;
  }
}

export function updateReportMetrics(report) {
  const stcgVal = document.querySelector(".stcg-val");
  const realizedStcg = report.total_realized_stcg || report.totalRealizedStcg;

  if (stcgVal && realizedStcg) {
    stcgVal.textContent = formatINR(realizedStcg);
    stcgVal.classList.remove("skeleton");
  }
}

export async function fetchDecisionRadar() {
  try {
    const opportunities = await fetchJson(`${API_BASE}/tax/harvest-opportunities`).catch(() => []);
    const ladder = await fetchJson(`${API_BASE}/tax/maturation-ladder`).catch(() => []);

    renderDecisionRadar(opportunities, ladder);
  } catch (e) {
    console.error("Error fetching decision radar:", e);
  }
}

export function renderDecisionRadar(opportunities, ladder) {
  const listContainer = document.querySelector(".radar-list");
  if (!listContainer) return;

  let html = "";

  if (opportunities && opportunities.length > 0) {
    for (const opp of opportunities.slice(0, 2)) {
      const assetName = opp.asset_name || opp.assetName;
      const lossVal = opp.potential_harvestable_loss || opp.potentialHarvestableLoss;
      const loss = Math.round(parseFloat(lossVal) || 0);

      html += `
        <div class="radar-card warning-border">
          <div class="radar-icon warning">⚡</div>
          <div class="radar-content">
            <div class="radar-title">Tax-Loss Harvesting Opportunity</div>
            <div class="radar-desc">Harvest <strong>${formatINR(loss)}</strong> loss in <em>${assetName}</em> before March 31.</div>
          </div>
          <span class="days-badge">Harvest Now</span>
        </div>
      `;
    }
  }

  if (ladder && ladder.length > 0) {
    for (const mat of ladder.slice(0, 2)) {
      const assetName = mat.asset_name || mat.assetName;
      const units = mat.remaining_units || mat.remainingUnits;
      const targetDate = mat.target_ltcg_date || mat.targetLtcgDate;
      const daysRem =
        mat.days_remaining_to_ltcg !== undefined
          ? mat.days_remaining_to_ltcg
          : mat.daysRemainingToLtcg;

      html += `
        <div class="radar-card maturation-border">
          <div class="radar-icon maturation">⏳</div>
          <div class="radar-content">
            <div class="radar-title">LTCG Tax Maturation Coming Up</div>
            <div class="radar-desc">Lot of <em>${assetName}</em> (${units} units) becomes <strong>LTCG</strong> on ${targetDate}.</div>
          </div>
          <span class="days-badge">Wait ${daysRem} Days</span>
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
    const logs = await fetchJson(`${API_BASE}/tax/realized-log?fy=${state.currentFy}`).catch(
      () => [],
    );
    renderRealizedLogTable(logs);
  } catch (e) {
    console.error("Error fetching realized log:", e);
  }
}

export function renderRealizedLogTable(logs) {
  const tableBody = document.querySelector("#realizedLogTable tbody");
  if (!tableBody) return;

  if (!logs || logs.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="8" style="text-align:center; color:#64748b;">No realized disposals recorded for ${state.currentFy}.</td></tr>`;
    return;
  }

  const fragment = document.createDocumentFragment();
  const template = document.createElement("template");

  let html = "";
  logs.forEach((l) => {
    const dispDate = l.disposal_date || l.disposalDate;
    const acqDate = l.acquisition_date || l.acquisitionDate;
    const assetName = l.asset_name || l.assetName;
    const matched = l.units_matched || l.unitsMatched;
    const proceeds = l.sale_proceeds || l.saleProceeds;
    const cost = l.cost_basis || l.costBasis;
    const gainVal = l.realized_gain || l.realizedGain;
    const taxTerm = l.tax_term || l.taxTerm;

    const gain = Math.round(parseFloat(gainVal) || 0);
    const gainSign = gain >= 0 ? "+" : "";
    const gainColor = gain >= 0 ? "color: #10b981;" : "color: #ef4444;";

    html += `
      <tr>
        <td>${dispDate}</td>
        <td>${acqDate}</td>
        <td style="font-weight:600;">${assetName}</td>
        <td class="font-mono">${matched}</td>
        <td class="font-mono">${formatINR(proceeds)}</td>
        <td class="font-mono">${formatINR(cost)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)}</td>
        <td><span class="cat-badge ${taxTerm === "LONG_TERM" ? "cat-EQUITY" : "cat-DEBT_SPECIFIED_50AA"}">${taxTerm}</span></td>
      </tr>
    `;
  });

  template.innerHTML = html;
  fragment.appendChild(template.content);
  tableBody.replaceChildren(fragment);
}
