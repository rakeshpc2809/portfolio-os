import { formatINR } from "../../utils.js?t=1788114000";

export function renderHoldingsTable(holdings) {
  const tableBody = document.querySelector("#holdingsTable tbody");
  if (!tableBody) return;

  if (!holdings || holdings.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:#64748b;">No open holdings found in ledger.</td></tr>`;
    return;
  }

  const fragment = document.createDocumentFragment();
  const template = document.createElement("template");

  let html = "";
  holdings.forEach((h, idx) => {
    const assetName = h.asset_name || h.assetName || "";
    const category = h.category || "";
    const inv = Math.round(parseFloat(h.invested_value || h.investedValue) || 0);
    const cur = Math.round(parseFloat(h.current_value || h.currentValue) || 0);
    const gain = Math.round(parseFloat(h.unrealized_gain || h.unrealizedGain) || 0);
    const gainPct = h.unrealized_gain_pct || h.unrealizedGainPct || "0.00";
    const allocPct = h.allocation_pct || h.allocationPct || "0.00";
    const lots = h.lots || [];

    const gainSign = gain >= 0 ? "+" : "";
    const gainColor = gain >= 0 ? "color: #10b981;" : "color: #ef4444;";

    const isSip =
      h.has_sip ||
      h.hasSip ||
      lots?.some((l) => (l.event_type || l.eventType) === "SIP_INSTALMENT");
    const sipBadge = isSip
      ? ' <span style="background:rgba(208,255,0,0.15); color:#d0ff00; border:1px solid rgba(208,255,0,0.3); font-size:10px; padding:2px 6px; border-radius:4px; margin-left:6px; font-weight:700;">🔄 Active SIP</span>'
      : "";

    html += `
      <tr class="holding-row" onclick="window.openHoldingDrawer && window.openHoldingDrawer(${idx})">
        <td style="font-weight:600;">${assetName}${sipBadge}</td>
        <td><span class="cat-badge cat-${category}">${category.replace("_SPECIFIED_50AA", "")}</span></td>
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

export function toggleLotDetails(idx) {
  const row = document.getElementById(`lotRow-${idx}`);
  if (row) {
    row.style.display = row.style.display === "none" ? "table-row" : "none";
  }
}



export function renderSchemeGroupedTaxLotsUI(holdings, containerId = "groupedTaxLotsContainer") {
  const container = document.getElementById(containerId);
  if (!container) return;

  if (!holdings || holdings.length === 0) {
    container.innerHTML = `<div style="color:#94a3b8; font-size:13px; padding:16px; text-align:center;">No open holdings or tax lots found in ledger.</div>`;
    return;
  }

  const html = holdings
    .map((h, schemeIdx) => {
      const isin = h.asset_id || h.assetId || "";
      const name = h.asset_name || h.assetName || isin;
      const category = h.category || "EQUITY";
      const inv = Math.round(parseFloat(h.invested_value || h.investedValue) || 0);
      const cur = Math.round(parseFloat(h.current_value || h.currentValue) || 0);
      const gain = Math.round(parseFloat(h.unrealized_gain || h.unrealizedGain) || 0);
      const gainPct = h.unrealized_gain_pct || h.unrealizedGainPct || "0.00";
      const lots = h.lots || [];

      const ltcgLots = lots.filter((l) => (l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg));
      const stcgLots = lots.filter((l) => !(l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg));

      const lotRowsHtml = lots
        .map((l, lotIdx) => {
          const acqDate = l.acquisition_date || l.acquisitionDate;
          const units = parseFloat(l.remaining_units || l.remainingUnits || "0");
          const costPerUnit = parseFloat(l.cost_per_unit || l.costPerUnit || "0");
          const totalCost = Math.round(units * costPerUnit);
          const lotVal = Math.round(parseFloat(l.current_value || l.currentValue || "0"));
          const lotGain = Math.round(parseFloat(l.unrealized_gain || l.unrealizedGain || "0"));
          const daysHeld = l.holding_days !== undefined ? l.holding_days : l.holdingDays;
          const daysToLtcg = l.days_to_ltcg !== undefined ? l.days_to_ltcg : l.daysToLtcg;
          const isLtcg = l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg;

          const badgeStyle = isLtcg
            ? "background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid #10b981;"
            : "background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid #f59e0b;";
          const badgeText = isLtcg ? "LTCG Free" : `STCG Locked (${daysToLtcg}d to LTCG)`;

          return `
        <tr style="border-bottom: 1px solid rgba(255,255,255,0.05); font-size:12px;">
          <td style="padding:10px 12px; font-weight:600; color:#f8fafc;">Lot #${lotIdx + 1}</td>
          <td style="padding:10px 12px; color:#cbd5e1;">${acqDate} <span style="font-size:10px; color:#64748b;">(${daysHeld}d)</span></td>
          <td style="padding:10px 12px; color:#cbd5e1;" class="font-mono">${units.toFixed(4)}</td>
          <td style="padding:10px 12px; color:#cbd5e1;" class="font-mono">₹${costPerUnit.toFixed(2)}</td>
          <td style="padding:10px 12px; color:#cbd5e1;" class="font-mono">${formatINR(totalCost)}</td>
          <td style="padding:10px 12px; color:#38bdf8;" class="font-mono">${formatINR(lotVal)}</td>
          <td style="padding:10px 12px; font-weight:700; color:${lotGain >= 0 ? "#10b981" : "#ef4444"};" class="font-mono">${lotGain >= 0 ? "+" : ""}${formatINR(lotGain)}</td>
          <td style="padding:10px 12px;">
            <span style="${badgeStyle} font-size:10px; padding:3px 8px; border-radius:4px; font-weight:700;">${badgeText}</span>
          </td>
        </tr>
      `;
        })
        .join("");

      return `
      <div class="scheme-lot-accordion-card" style="background: rgba(15, 23, 42, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; overflow: hidden; margin-bottom: 12px;">
        <div class="accordion-header" onclick="window.toggleSchemeLotCard('${containerId}_${schemeIdx}')" style="padding: 16px; display: flex; justify-content: space-between; align-items: center; cursor: pointer; background: rgba(255,255,255,0.02);">
          <div style="flex: 1;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <h3 style="margin: 0; font-size: 1rem; color: #f8fafc;">${name}</h3>
              <span class="cat-badge cat-${category}">${category.replace("_SPECIFIED_50AA", "")}</span>
            </div>
            <div style="font-size: 11px; color: #94a3b8; margin-top: 4px;" class="font-mono">ISIN: ${isin}</div>
          </div>

          <div style="display: flex; gap: 12px; align-items: center; margin-right: 16px;">
            <span style="background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid #10b981; font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;">${ltcgLots.length} LTCG Lots</span>
            <span style="background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid #f59e0b; font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;">${stcgLots.length} STCG Lots</span>
            <div style="text-align: right;">
              <div style="font-size: 14px; font-weight: 700; color: #38bdf8;" class="font-mono">${formatINR(cur)}</div>
              <div style="font-size: 11px; color: ${gain >= 0 ? "#10b981" : "#ef4444"};" class="font-mono">${gain >= 0 ? "+" : ""}${formatINR(gain)} (${gainPct}%)</div>
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
    })
    .join("");

  container.innerHTML = html;
}

export function toggleSchemeLotCard(key) {
  const body = document.getElementById(`schemeAccBody_${key}`);
  const icon = document.getElementById(`schemeAccIcon_${key}`);
  if (body) {
    const isHidden = body.style.display === "none";
    body.style.display = isHidden ? "block" : "none";
    if (icon) icon.textContent = isHidden ? "▼" : "▶";
  }
}



if (typeof window !== "undefined") {
  window.toggleLotDetails = toggleLotDetails;
  window.toggleSchemeLotCard = toggleSchemeLotCard;
  window.renderSchemeGroupedTaxLotsUI = renderSchemeGroupedTaxLotsUI;
}
