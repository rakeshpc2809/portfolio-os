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

    const expenseRatio = h.expense_ratio ?? h.expenseRatio ?? 0.20;
    const terStatus = h.ter_status || h.terStatus || "OPTIMAL";
    const terAsOf = h.ter_as_of_date || h.terAsOfDate || "Aug 2026";
    const terBadge = `<span style="${terStatus === "ELEVATED_DRAG" ? "background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid #ef4444;" : "background: rgba(56, 189, 248, 0.1); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.3);"} font-size: 10px; padding: 2px 6px; border-radius: 4px; margin-left: 6px; font-weight: 700;">TER ${expenseRatio.toFixed(2)}% (${terAsOf})</span>`;

    html += `
      <tr class="holding-row" onclick="window.openHoldingDrawer && window.openHoldingDrawer(${idx})">
        <td style="font-weight:600;">${assetName}${sipBadge}${terBadge}</td>
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



let cachedGroupedHoldings = null;
let currentTaxLotsFilter = "all";
let currentTaxLotsSort = "fifo_asc";

export function initTaxLotsControls() {
  if (typeof document === "undefined") return;

  const chipsContainer = document.getElementById("taxLotsFilterChips");
  if (chipsContainer && !chipsContainer.dataset.initialized) {
    chipsContainer.dataset.initialized = "true";
    chipsContainer.addEventListener("click", (e) => {
      const btn = e.target.closest("button.filter-chip");
      if (!btn) return;
      chipsContainer.querySelectorAll("button.filter-chip").forEach((b) => {
        b.classList.remove("active");
        b.style.background = "rgba(255,255,255,0.05)";
        b.style.borderColor = "rgba(255,255,255,0.15)";
      });
      btn.classList.add("active");
      btn.style.background = "rgba(56, 189, 248, 0.2)";
      btn.style.borderColor = "#38bdf8";
      currentTaxLotsFilter = btn.dataset.filter || "all";
      if (cachedGroupedHoldings) {
        renderSchemeGroupedTaxLotsUI(cachedGroupedHoldings, "groupedTaxLotsContainer");
      }
    });
  }

  const sortSelect = document.getElementById("taxLotsSortSelect");
  if (sortSelect && !sortSelect.dataset.initialized) {
    sortSelect.dataset.initialized = "true";
    sortSelect.addEventListener("change", (e) => {
      currentTaxLotsSort = e.target.value;
      if (cachedGroupedHoldings) {
        renderSchemeGroupedTaxLotsUI(cachedGroupedHoldings, "groupedTaxLotsContainer");
      }
    });
  }
}

export function renderSchemeGroupedTaxLotsUI(holdings, containerId = "groupedTaxLotsContainer") {
  const container = document.getElementById(containerId);
  if (!container) return;

  if (!Array.isArray(holdings) || holdings.length === 0) {
    container.innerHTML = '<div class="loading-td" style="color: #64748b; padding: 16px;">No active mutual fund holdings found.</div>';
    return;
  }

  cachedGroupedHoldings = holdings;
  initTaxLotsControls();

  let totalVisibleLotsCount = 0;

  const html = holdings
    .map((h, schemeIdx) => {
      const isin = h.asset_id || h.assetId || "";
      const name = h.asset_name || h.assetName || isin;
      const category = h.category || "EQUITY";
      const rawLots = h.lots || [];

      // Filter lots based on active filter chip
      let filteredLots = rawLots.filter((l) => {
        const isLtcg = l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg;
        const isHarvest = l.is_harvest_candidate !== undefined ? l.is_harvest_candidate : l.isHarvestCandidate;
        const lotGain = parseFloat(l.unrealized_gain || l.unrealizedGain || "0");

        if (currentTaxLotsFilter === "ltcg") return isLtcg;
        if (currentTaxLotsFilter === "stcg") return !isLtcg;
        if (currentTaxLotsFilter === "losses") return isHarvest || lotGain < 0;
        return true;
      });

      // Sort filtered lots
      filteredLots.sort((a, b) => {
        const dateA = new Date(a.acquisition_date || a.acquisitionDate || "1970-01-01").getTime();
        const dateB = new Date(b.acquisition_date || b.acquisitionDate || "1970-01-01").getTime();
        const dragA = parseFloat(a.estimated_tax_drag || a.estimatedTaxDrag || "0");
        const dragB = parseFloat(b.estimated_tax_drag || b.estimatedTaxDrag || "0");
        const gainA = parseFloat(a.unrealized_gain || a.unrealizedGain || "0");
        const gainB = parseFloat(b.unrealized_gain || b.unrealizedGain || "0");

        if (currentTaxLotsSort === "fifo_desc") return dateB - dateA;
        if (currentTaxLotsSort === "tax_drag_asc") return dragA - dragB;
        if (currentTaxLotsSort === "tax_drag_desc") return dragB - dragA;
        if (currentTaxLotsSort === "gain_desc") return gainB - gainA;
        return dateA - dateB; // fifo_asc
      });

      totalVisibleLotsCount += filteredLots.length;

      if (filteredLots.length === 0 && currentTaxLotsFilter !== "all") {
        return "";
      }

      // Calculate displayed metrics (bound to filtered lots when filtering is active)
      const displayLots = currentTaxLotsFilter === "all" ? rawLots : filteredLots;
      const totalUnits = displayLots.reduce((acc, l) => acc + (parseFloat(l.remaining_units || l.remainingUnits) || 0), 0);
      const totalInvested = displayLots.reduce((acc, l) => acc + (parseFloat(l.total_cost_basis || l.totalCostBasis) || 0), 0);
      const cur = Math.round(displayLots.reduce((acc, l) => acc + (parseFloat(l.current_value || l.currentValue) || 0), 0));
      const gain = Math.round(displayLots.reduce((acc, l) => acc + (parseFloat(l.unrealized_gain || l.unrealizedGain) || 0), 0));
      const gainPct = totalInvested > 0 ? ((gain / totalInvested) * 100).toFixed(2) : "0.00";
      const weightedCostNav = totalUnits > 0 ? totalInvested / totalUnits : 0;
      const currentNav = rawLots.length > 0 ? parseFloat(rawLots[0].current_nav || rawLots[0].currentNav) || 0 : 0;
      const schemeTaxDrag = displayLots.reduce((acc, l) => acc + (parseFloat(l.estimated_tax_drag || l.estimatedTaxDrag) || 0), 0);

      const ltcgCount = rawLots.filter((l) => l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg).length;
      const stcgCount = rawLots.filter((l) => !(l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg)).length;

      let badgeHtml = "";
      if (currentTaxLotsFilter === "losses") {
        badgeHtml = `<span style="background: rgba(244, 63, 94, 0.15); color: #f43f5e; border: 1px solid #f43f5e; font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;">${filteredLots.length} Loss Lots</span>`;
      } else if (currentTaxLotsFilter === "ltcg") {
        badgeHtml = `<span style="background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid #10b981; font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;">${filteredLots.length} LTCG Lots</span>`;
      } else if (currentTaxLotsFilter === "stcg") {
        badgeHtml = `<span style="background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid #f59e0b; font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;">${filteredLots.length} STCG Lots</span>`;
      } else {
        badgeHtml = `
          <span style="background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid #10b981; font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;">${ltcgCount} LTCG</span>
          <span style="background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid #f59e0b; font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;">${stcgCount} STCG</span>
        `;
      }

      let lotRowsHtml = filteredLots
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
          const taxDrag = Math.round(parseFloat(l.estimated_tax_drag || l.estimatedTaxDrag || "0"));

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
          <td style="padding:10px 12px; color:#f43f5e;" class="font-mono">${taxDrag > 0 ? formatINR(taxDrag) : "₹0"}</td>
          <td style="padding:10px 12px;">
            <span style="${badgeStyle} font-size:10px; padding:3px 8px; border-radius:4px; font-weight:700;">${badgeText}</span>
          </td>
        </tr>
      `;
        })
        .join("");

      return `
      <div class="scheme-lot-accordion-card" style="background: rgba(15, 23, 42, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; overflow: hidden; margin-bottom: 12px;">
        <div class="accordion-header" onclick="window.toggleSchemeLotCard('${containerId}_${schemeIdx}')" style="padding: 16px; display: flex; justify-content: space-between; align-items: center; cursor: pointer; background: rgba(255,255,255,0.02); flex-wrap: wrap; gap: 12px;">
          <div style="flex: 1; min-width: 260px;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <h3 style="margin: 0; font-size: 1rem; color: #f8fafc;">${name}</h3>
              <span class="cat-badge cat-${category}">${category.replace("_SPECIFIED_50AA", "")}</span>
              <span style="${(h.ter_status || h.terStatus) === "ELEVATED_DRAG" ? "background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid #ef4444;" : "background: rgba(56, 189, 248, 0.1); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.3);"} font-size: 10px; padding: 2px 6px; border-radius: 4px; font-weight: 700;">TER ${(h.expense_ratio ?? h.expenseRatio ?? 0.20).toFixed(2)}% (${h.ter_as_of_date || h.terAsOfDate || "Aug 2026"})</span>
            </div>
            <div style="font-size: 11px; color: #94a3b8; margin-top: 4px;" class="font-mono">
              ISIN: ${isin} · ${totalUnits.toFixed(2)} Units · Avg NAV ₹${weightedCostNav.toFixed(2)} · Cur NAV ₹${currentNav.toFixed(2)}
            </div>
          </div>

          <div style="display: flex; gap: 12px; align-items: center; margin-right: 16px; flex-wrap: wrap;">
            ${badgeHtml}
            <span style="background: rgba(244, 63, 94, 0.1); color: #f43f5e; border: 1px solid rgba(244, 63, 94, 0.3); font-size: 11px; padding: 3px 10px; border-radius: 6px; font-weight: 700;" class="font-mono">Tax Drag: ${formatINR(Math.round(schemeTaxDrag))}</span>
            <div style="text-align: right; min-width: 110px;">
              <div style="font-size: 14px; font-weight: 700; color: #38bdf8;" class="font-mono">${formatINR(cur)}</div>
              <div style="font-size: 11px; color: ${gain >= 0 ? "#10b981" : "#ef4444"}; font-weight: 700;" class="font-mono">${gain >= 0 ? "+" : ""}${formatINR(gain)} (${gainPct}%)</div>
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
                  <th style="padding: 8px 12px;">Est. Tax Drag</th>
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

  if (totalVisibleLotsCount === 0) {
    container.innerHTML = `
      <div style="text-align: center; padding: 32px 16px; background: rgba(15, 23, 42, 0.4); border: 1px dashed rgba(255,255,255,0.1); border-radius: 12px; color: #94a3b8;">
        <div style="font-size: 1.1rem; font-weight: 600; color: #f8fafc; margin-bottom: 6px;">No Lots Match Active Filter</div>
        <div style="font-size: 0.85rem;">Zero open tax lots match the filter <strong>"${currentTaxLotsFilter.toUpperCase()}"</strong> across all portfolio schemes.</div>
      </div>
    `;
  } else {
    container.innerHTML = html;
  }
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
  window.initTaxLotsControls = initTaxLotsControls;
}
