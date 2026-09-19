import { API_BASE, fetchJson } from "./js/api.js?t=1788114000";
import { state } from "./js/state.js?t=1788114000";
import { formatINR, showToast } from "./js/utils.js?t=1788114000";
import {
  updatePortfolioSummary,
  renderHoldingsTable,
  renderAllocationChart,
  renderCategoryChart,
  renderBucketAllocationChart,
  renderFundAllocationCompareChart,
  renderSchemeGroupedTaxLotsUI,
  renderNetWorthTrendChart,
  renderCashflowSankey,
  renderBucketRebalance,
  renderUnifiedRebalancePlanUI,
  fetchFireSummary,
} from "./js/modules/portfolio.js?t=1788114000";
import {
  updateExemptionMeter,
  updateReportMetrics,
  renderDecisionRadar,
  fetchDecisionRadar,
  fetchTaxMetrics,
  renderRealizedLogTable,
} from "./js/modules/tax.js?t=1788114000";

const DEFAULT_AUTH_TOKEN = "dev_secret_key_123";

async function initDashboard() {
  try {
    const summaryData = await fetchJson(`/portfolio/summary?fy=${state.currentFy}`).catch(
      () => null,
    );
    if (summaryData) updatePortfolioSummary(summaryData);

    const holdings = await fetchJson(`/portfolio/holdings`).catch(() => []);
    state.holdings = holdings;
    renderHoldingsTable(holdings);
    renderSchemeGroupedTaxLotsUI(holdings, "groupedTaxLotsContainer");

    const bucketTargetsConfig = await fetchJson(`/config/bucket-targets`).catch(() => null);
    state.bucketTargetsConfig = bucketTargetsConfig;
    if (bucketTargetsConfig && holdings) {
      renderFundAllocationCompareChart("fundAllocationCompareChart", holdings, bucketTargetsConfig);
    }

    const bucketAllocData = await fetchJson(`/portfolio/bucket-allocation`).catch(() => null);
    if (bucketAllocData) renderBucketAllocationChart("bucketAllocationChart", bucketAllocData);

    const exemptionData = await fetchJson(`/tax/exemption-status?fy=${state.currentFy}`).catch(
      () => null,
    );
    if (exemptionData) updateExemptionMeter(exemptionData);

    const planData = await fetchJson(`/sync/rebalance/plan?trigger=DRIFT`).catch(() => null);
    if (planData) renderUnifiedRebalancePlanUI(planData);

    const bucketData = await fetchJson(`/rebalance/bucket?fy=${state.currentFy}`).catch(() => null);
    if (bucketData) renderBucketRebalance(bucketData);

    fetchFireSummary();
  } catch (err) {
    console.error("Dashboard initialization failed:", err);
    showToast("Error connecting to Core Node REST service.", "error");
  }
}

async function fetchRebalancePreview(amount) {
  try {
    const preview = await fetchJson(`/rebalance/preview?amount=${amount}&fy=${state.currentFy}`);
    const dragEl = document.getElementById("rebTaxDrag");
    const rateEl = document.getElementById("rebEffRate");
    const ltcgEl = document.getElementById("rebLtcgHarvested");

    if (dragEl)
      dragEl.textContent = formatINR(
        parseFloat(preview.total_tax_drag || preview.totalTaxDrag || "0"),
      );
    if (rateEl)
      rateEl.textContent = `${preview.effective_tax_rate_pct || preview.effectiveTaxRatePct || "0.00"}%`;
    if (ltcgEl)
      ltcgEl.textContent = formatINR(
        parseFloat(preview.ltcg_exemption_harvested || preview.ltcgExemptionHarvested || "0"),
      );
  } catch (err) {
    console.error("Failed to fetch rebalance preview:", err);
  }
}

document.addEventListener("DOMContentLoaded", () => {
  initDashboard();

  window.openCmdPalette = () => {
    const modal = document.getElementById("commandPaletteModal");
    if (modal) modal.style.display = "flex";
    const input = document.getElementById("commandPaletteInput");
    if (input) {
      input.focus();
      input.select();
    }
  };

  window.closeCmdPalette = () => {
    const modal = document.getElementById("commandPaletteModal");
    if (modal) modal.style.display = "none";
  };

  window.openHoldingDrawer = (idx) => {
    const holding = state.holdings[idx];
    if (!holding) return;

    const drawer = document.getElementById("holdingDetailDrawer");
    const backdrop = document.getElementById("holdingDetailDrawerBackdrop");
    const titleEl = document.getElementById("drawerAssetTitle");
    const catEl = document.getElementById("drawerAssetCategory");
    const bodyEl = document.getElementById("drawerBody");

    if (!drawer || !backdrop || !bodyEl) return;

    const assetName = holding.asset_name || holding.assetName || "";
    const category = holding.category || "EQUITY";
    const inv = Math.round(parseFloat(holding.invested_value || holding.investedValue) || 0);
    const cur = Math.round(parseFloat(holding.current_value || holding.currentValue) || 0);
    const gain = Math.round(parseFloat(holding.unrealized_gain || holding.unrealizedGain) || 0);
    const gainPct = holding.unrealized_gain_pct || holding.unrealizedGainPct || "0.00";
    const lots = holding.lots || [];

    if (titleEl) titleEl.textContent = assetName;
    if (catEl) {
      catEl.textContent = category.replace("_SPECIFIED_50AA", "");
      catEl.className = `live-tag cat-${category}`;
    }

    const lotsHtml = lots
      .map((l, lotIdx) => {
        const acqDate = l.acquisition_date || l.acquisitionDate;
        const units = parseFloat(l.remaining_units || l.remainingUnits || "0");
        const costPerUnit = parseFloat(l.cost_per_unit || l.costPerUnit || "0");
        const lotGain = parseFloat(l.unrealized_gain || l.unrealizedGain || "0");
        const daysHeld = l.holding_days !== undefined ? l.holding_days : l.holdingDays;
        const isLtcg = l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg;

        return `
        <div class="drawer-lot-card">
          <div>
            <div style="font-size:12px; font-weight:600; color:#fff;">Lot #${lotIdx + 1} · Acquired ${acqDate} (${daysHeld}d held)</div>
            <div style="font-size:11px; color:#94a3b8; margin-top:3px;" class="font-mono">${units.toFixed(2)} units @ ₹${costPerUnit.toFixed(2)}</div>
          </div>
          <div style="text-align:right; display:flex; flex-direction:column; align-items:flex-end; gap:6px;">
            <div style="font-size:13px; font-weight:700; color:${lotGain >= 0 ? "#10b981" : "#ef4444"};" class="font-mono">${lotGain >= 0 ? "+" : ""}${formatINR(lotGain)}</div>
            <div style="display:flex; gap:6px; align-items:center;">
              <span class="cat-badge ${isLtcg ? "cat-EQUITY" : "cat-DEBT_SPECIFIED_50AA"}">${isLtcg ? "LTCG Free" : "STCG Locked"}</span>
              <button type="button" class="drawer-action-btn" onclick="window.harvestLot('${holding.isin || ""}', '${assetName.replace(/'/g, "\\'")}', ${units}, ${costPerUnit})">Harvest ➔</button>
            </div>
          </div>
        </div>
      `;
      })
      .join("");

    bodyEl.innerHTML = `
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
        <div style="background:rgba(0,0,0,0.3); border:1px solid rgba(255,255,255,0.08); padding:12px; border-radius:10px;">
          <div style="font-size:11px; color:#94a3b8; text-transform:uppercase;">Invested Cost</div>
          <div style="font-size:16px; font-weight:700; color:#fff;" class="font-mono">${formatINR(inv)}</div>
        </div>
        <div style="background:rgba(0,0,0,0.3); border:1px solid rgba(255,255,255,0.08); padding:12px; border-radius:10px;">
          <div style="font-size:11px; color:#94a3b8; text-transform:uppercase;">Current Value</div>
          <div style="font-size:16px; font-weight:700; color:#06b6d4;" class="font-mono">${formatINR(cur)}</div>
        </div>
      </div>
      <div style="background:rgba(0,0,0,0.3); border:1px solid rgba(255,255,255,0.08); padding:12px; border-radius:10px; display:flex; justify-content:space-between; align-items:center;">
        <span style="font-size:12px; color:#94a3b8;">Total Unrealized Gain</span>
        <strong style="font-size:15px; color:${gain >= 0 ? "#10b981" : "#ef4444"};" class="font-mono">${gain >= 0 ? "+" : ""}${formatINR(gain)} (${gainPct}%)</strong>
      </div>
      <h4 style="font-size:13px; font-weight:700; color:#06b6d4; margin-top:8px;">FIFO Open Tax Lots (${lots.length})</h4>
      <div style="display:flex; flex-direction:column; gap:10px;">${lotsHtml || '<div style="color:#94a3b8; font-size:12px;">No open lots available.</div>'}</div>
    `;

    backdrop.classList.add("open");
    drawer.classList.add("open");
  };

  window.closeHoldingDrawer = () => {
    const drawer = document.getElementById("holdingDetailDrawer");
    const backdrop = document.getElementById("holdingDetailDrawerBackdrop");
    if (drawer) drawer.classList.remove("open");
    if (backdrop) backdrop.classList.remove("open");
  };

  window.harvestLot = (isin, schemeName, units, costPerUnit) => {
    window.closeHoldingDrawer();
    window.openCmdPalette();
    const input = document.getElementById("commandPaletteInput");
    if (input) {
      input.value = `rebalance ${Math.max(10000, Math.round(units * costPerUnit))}`;
      window.submitAiPrompt();
    }
  };

  window.submitAiPrompt = async () => {
    const input = document.getElementById("commandPaletteInput");
    const results = document.getElementById("commandPaletteResults");
    if (!input || !results) return;

    const promptText = input.value.trim();
    if (!promptText) return;

    const promptLower = promptText.toLowerCase();

    // Check for explicit numeric waterfall command (e.g., "waterfall 50000", "trim 25000", "rebalance 100000")
    const isExplicitWaterfall =
      (promptLower.startsWith("waterfall") ||
        promptLower.startsWith("trim") ||
        /^rebalance\s+\d+/.test(promptLower)) &&
      /\d+/.test(promptText);

    if (isExplicitWaterfall) {
      const match = promptText.match(/\d+/);
      const amount = match ? parseInt(match[0]) : 50000;
      results.innerHTML = `<div style="padding:12px; color:#06b6d4;">⚙️ Calculating Tax-Aware Waterfall for ₹${formatINR(amount)}...</div>`;

      try {
        const wf = await fetchJson(
          `/rebalance/waterfall?bucket=EQUITY_CORE&amount=${amount}&fy=${state.currentFy}`,
        );
        const stepsHtml = wf.steps
          .map(
            (s) => `
          <div class="cmd-step-row">
            <span><strong style="color:#d0ff00;">${s.tier}</strong>: ${s.asset_name || s.assetName}</span>
            <span class="font-mono">₹ ${formatINR(parseFloat(s.proceeds))} (Tax: ₹ ${formatINR(parseFloat(s.tax_drag || s.taxDrag))})</span>
          </div>
        `,
          )
          .join("");

        results.innerHTML = `
          <div class="cmd-action-card">
            <div class="cmd-action-header">
              <span>⚡ Tax-Aware Rebalance Engine</span>
              <span>Satisfied: ₹ ${formatINR(parseFloat(wf.satisfied_amount || wf.satisfiedAmount))}</span>
            </div>
            <div style="font-size:12px; color:#94a3b8;">Exemption Consumed: <strong style="color:#10b981;" class="font-mono">₹ ${formatINR(parseFloat(wf.ltcg_exemption_consumed || wf.ltcgExemptionConsumed))}</strong> · Tax Drag: <strong style="color:#06b6d4;" class="font-mono">₹ ${formatINR(parseFloat(wf.total_tax_drag || wf.totalTaxDrag))}</strong></div>
            <div class="cmd-action-steps">${stepsHtml || '<div style="font-size:12px; color:#94a3b8;">No trim steps required.</div>'}</div>
          </div>
        `;
        return;
      } catch (err) {
        console.error("Command palette waterfall action error:", err);
      }
    }

    // Conversational Copilot NLP Routing to Agent Tools (/api/v1/agent/tools/execute)
    let toolName = "getPortfolioValuation";
    let toolArgs = {};

    if (
      promptLower.includes("simulate") ||
      promptLower.includes("selling") ||
      promptLower.startsWith("sell") ||
      promptLower.startsWith("buy") ||
      promptLower.includes("what if") ||
      promptLower.includes("what-if")
    ) {
      toolName = "simulateTrade";

      // Detect trade type
      const isBuy = promptLower.includes("buy") || promptLower.includes("acquir");
      const tradeType = isBuy ? "ACQUISITION" : "DISPOSAL";

      // Extract units
      const unitMatch = promptText.match(/(\d+(?:\.\d+)?)\s*(?:units?|shares?|qty)?/i);
      const units = unitMatch ? parseFloat(unitMatch[1]) : 50;

      // Match holding scheme name and ISIN
      let isin = "INF879O01027";
      let schemeName = "Parag Parikh Flexi Cap Fund - Direct Plan - Growth";
      let pricePerUnit = null; // Let backend resolve live NAV from DB / cache

      const holdings = state.holdings || [];
      const matchedHolding = holdings.find((h) => {
        const name = (h.assetName || "").toLowerCase();
        const id = (h.assetId || "").toLowerCase();
        return (
          (promptLower.includes("parag") || promptLower.includes("parikh")) &&
          (name.includes("parag") || id === "inf879o01027") ||
          (promptLower.includes("uti") && name.includes("uti")) ||
          (promptLower.includes("midcap") && name.includes("midcap")) ||
          (promptLower.includes("smallcap") && name.includes("smallcap")) ||
          (promptLower.includes("gold") && name.includes("gold")) ||
          name.split(" ").some((w) => w.length > 3 && promptLower.includes(w))
        );
      });

      if (matchedHolding) {
        isin = matchedHolding.assetId;
        schemeName = matchedHolding.assetName;
        if (matchedHolding.lots && matchedHolding.lots.length > 0) {
          const nav = parseFloat(matchedHolding.lots[0].currentNav);
          if (!isNaN(nav) && nav > 0) pricePerUnit = nav;
        }
      }

      toolArgs = {
        isin: isin,
        schemeName: schemeName,
        units: units,
        tradeType: tradeType,
      };
      if (pricePerUnit != null && pricePerUnit > 0) {
        toolArgs.pricePerUnit = pricePerUnit;
      }
    } else if (
      promptLower.includes("explain rebalance") ||
      promptLower.includes("rebalance trigger") ||
      promptLower.includes("why rebalance") ||
      promptLower.includes("consequence of rebalancing") ||
      promptLower.includes("rebalance plan") ||
      promptLower.includes("rebalance today")
    ) {
      toolName = "getRebalancePlan";
    } else if (
      promptLower.includes("fire") ||
      promptLower.includes("retire") ||
      promptLower.includes("corpus") ||
      promptLower.includes("coast") ||
      promptLower.includes("monte carlo")
    ) {
      toolName = "getFireSummary";
    } else if (
      promptLower.includes("harvest") ||
      promptLower.includes("tax") ||
      promptLower.includes("ltcg") ||
      promptLower.includes("saving") ||
      promptLower.includes("112a")
    ) {
      toolName = "getTaxHarvestOpportunities";
    } else if (
      promptLower.includes("fund") ||
      promptLower.includes("holding") ||
      promptLower.includes("registry") ||
      promptLower.includes("scheme")
    ) {
      toolName = "getFundRegistry";
    } else if (promptLower.includes("overlap")) {
      toolName = "getPairwiseFundOverlap";
      toolArgs = { fundA: "INF109KC12U0", fundB: "INF879O01027" };
    } else if (
      promptLower.includes("valuation") ||
      promptLower.includes("worth") ||
      promptLower.includes("summary")
    ) {
      toolName = "getPortfolioValuation";
    }

    results.innerHTML = `<div style="padding:12px; color:#d0ff00; font-family:monospace;">⚡ Executing Agent Tool [${toolName}]...</div>`;

    try {
      const data = await fetchJson("/api/v1/agent/tools/execute", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tool: toolName, arguments: toolArgs }),
      });

      if (!data || data.status !== "SUCCESS") {
        const errMsg = data?.error_message || data?.errorMessage || "Tool execution failed";
        results.innerHTML = `
          <div class="cmd-action-card" style="border-color: #ef4444;">
            <div class="cmd-action-header" style="color: #ef4444;">
              <span>⚠️ Agent Tool Error [${toolName}]</span>
            </div>
            <div style="font-size:12px; color:#fca5a5; padding:8px 0;">${errMsg}</div>
          </div>
        `;
        return;
      }

      const res = data.result || {};
      let bodyHtml = "";

      if (toolName === "simulateTrade") {
        const sim = res.simulation_result || {};
        const tradeType = sim.trade_type || sim.tradeType || toolArgs.tradeType || "DISPOSAL";
        const scheme = sim.scheme_name || sim.schemeName || toolArgs.schemeName;
        const units = sim.units || toolArgs.units;
        const navUsed = parseFloat(sim.price_per_unit ?? sim.pricePerUnit ?? 0);
        const gross = parseFloat(sim.gross_trade_amount ?? sim.grossTradeAmount ?? 0);
        const gain = parseFloat(sim.gross_capital_gain ?? sim.grossCapitalGain ?? 0);
        const ltcg = parseFloat(sim.ltcg_equity ?? sim.ltcgEquity ?? 0);
        const sec112a = parseFloat(sim.sec112a_exemption_applied ?? sim.sec112aExemptionApplied ?? 0);
        const stcg = parseFloat(sim.stcg_equity ?? sim.stcgEquity ?? 0);
        const taxLiab = parseFloat(sim.estimated_tax_liability ?? sim.estimatedTaxLiability ?? 0);
        const nw = parseFloat(sim.post_trade_net_worth ?? sim.postTradeNetWorth ?? 0);
        const xirr = sim.post_trade_xirr ?? sim.postTradeXirr ?? "0.0";
        const priceSource = res.price_source || (toolArgs.pricePerUnit ? "EXPLICIT_PARAMETER" : "LIVE_LEDGER_NAV");
        const isEstimated = res.is_price_estimated === true || priceSource === "ESTIMATED_FALLBACK";

        const sourceTag = isEstimated
          ? `<span class="live-tag" style="background:rgba(245,158,11,0.15); color:#f59e0b; border:1px solid #f59e0b; font-size:10px; padding:2px 6px;">⚠️ ESTIMATED PRICE (₹${navUsed.toFixed(2)})</span>`
          : `<span class="live-tag" style="background:rgba(16,185,129,0.15); color:#10b981; border:1px solid #10b981; font-size:10px; padding:2px 6px;">LIVE NAV (₹${navUsed.toFixed(2)})</span>`;

        bodyHtml = `
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px; flex-wrap:wrap; gap:6px;">
            <div style="font-size:13px; font-weight:700; color:#f8fafc;">
              ${tradeType} ${units} Units · <span style="color:#38bdf8;">${scheme}</span>
            </div>
            ${sourceTag}
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px; font-size:12px; margin-top:8px;">
            <div>Gross Proceeds: <strong style="color:#d0ff00;" class="font-mono">₹ ${formatINR(gross)}</strong></div>
            <div>Capital Gain: <strong style="color:#38bdf8;" class="font-mono">₹ ${formatINR(gain)}</strong></div>
            <div>LTCG Equity: <strong style="color:#10b981;" class="font-mono">₹ ${formatINR(ltcg)}</strong></div>
            <div>Sec 112A Exempt: <strong style="color:#a78bfa;" class="font-mono">₹ ${formatINR(sec112a)}</strong></div>
            <div>STCG Equity: <strong style="color:#f59e0b;" class="font-mono">₹ ${formatINR(stcg)}</strong></div>
            <div>Est. Tax Liability: <strong style="color:#ef4444;" class="font-mono">₹ ${formatINR(taxLiab)}</strong></div>
            <div>Post-Trade Net Worth: <strong style="color:#f8fafc;" class="font-mono">₹ ${formatINR(nw)}</strong></div>
            <div>Post-Trade XIRR: <strong style="color:#06b6d4;" class="font-mono">${xirr}%</strong></div>
          </div>
          <div style="margin-top:10px; font-size:11px; color:#94a3b8; line-height:1.4; padding:8px 12px; background:rgba(0,0,0,0.3); border-radius:6px; border-left:3px solid ${isEstimated ? '#f59e0b' : '#10b981'};">
            ${sim.tax_summary_notice || sim.taxSummaryNotice || res.notice || "Trade simulation completed without updating ledger."}
          </div>
        `;
      } else if (toolName === "getRebalancePlan") {
        const trigger = res.trigger || {};
        const sellSide = res.sell_side || {};
        const buySide = res.buy_side || {};
        const triggerType = res.derived_trigger_type || trigger.type || "DRIFT";

        const sellSteps = (sellSide.steps || []).slice(0, 4).map((s) => `
          <div class="cmd-step-row">
            <span><strong style="color:#f59e0b;">${s.tier || "TRIM"}</strong>: ${s.asset_name || s.assetName || s.assetId}</span>
            <span class="font-mono">₹ ${formatINR(parseFloat(s.proceeds || 0))}</span>
          </div>
        `).join("");

        bodyHtml = `
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
            <div style="font-size:12px; color:#cbd5e1;">Active Trigger: <strong style="color:#d0ff00;">${triggerType}</strong></div>
            <span style="font-size:11px; color:#94a3b8; font-family:monospace;">FY ${res.fiscal_year || state.currentFy}</span>
          </div>
          <div style="font-size:11px; color:#94a3b8; line-height:1.4; padding:6px 10px; background:rgba(0,0,0,0.25); border-radius:6px; margin-bottom:8px;">
            <strong>Trigger Hierarchy:</strong> Tier 1 Drawdown &gt; Tier 2 Asset Drift (&gt;15% rel) &gt; Tier 3 Scheduled Annual Harvest.
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px; font-size:12px;">
            <div>Sell Proceeds: <strong style="color:#f59e0b;" class="font-mono">₹ ${formatINR(parseFloat(sellSide.totalProceeds || 0))}</strong></div>
            <div>Estimated Tax Drag: <strong style="color:#ef4444;" class="font-mono">₹ ${formatINR(parseFloat(sellSide.totalTaxDrag || 0))}</strong></div>
            <div>Exemption Consumed: <strong style="color:#10b981;" class="font-mono">₹ ${formatINR(parseFloat(sellSide.ltcgExemptionConsumed || 0))}</strong></div>
            <div>Deployable Cash: <strong style="color:#38bdf8;" class="font-mono">₹ ${formatINR(parseFloat(buySide.totalDeployable || 0))}</strong></div>
          </div>
          ${sellSteps ? `<div class="cmd-action-steps" style="margin-top:8px;">${sellSteps}</div>` : ""}
        `;
      } else if (toolName === "getPortfolioValuation") {
        bodyHtml = `
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px; font-size:12px; margin-top:8px;">
            <div>Net Worth: <strong style="color:#d0ff00;" class="font-mono">₹ ${formatINR(parseFloat(res.total_net_worth || 0))}</strong></div>
            <div>Invested: <strong style="color:#94a3b8;" class="font-mono">₹ ${formatINR(parseFloat(res.total_invested_cost || 0))}</strong></div>
            <div>Unrealized: <strong style="color:#10b981;" class="font-mono">₹ ${formatINR(parseFloat(res.total_unrealized_gain || 0))}</strong></div>
            <div>XIRR: <strong style="color:#06b6d4;" class="font-mono">${res.portfolio_xirr || "0.0"}%</strong></div>
          </div>
        `;
      } else if (toolName === "getFireSummary") {
        const netWorth = parseFloat(res.total_net_worth || 0);
        const reqCorpus = parseFloat(res.required_fire_corpus || 0);
        const progressPct = reqCorpus > 0 ? ((netWorth / reqCorpus) * 100).toFixed(1) : "0.0";
        bodyHtml = `
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px; font-size:12px; margin-top:8px;">
            <div>Net Worth: <strong style="color:#d0ff00;" class="font-mono">₹ ${formatINR(netWorth)}</strong></div>
            <div>Required FIRE: <strong style="color:#94a3b8;" class="font-mono">₹ ${formatINR(reqCorpus)}</strong></div>
            <div>Annual Expense: <strong style="color:#f59e0b;" class="font-mono">₹ ${formatINR(parseFloat(res.annual_expense || 0))}</strong></div>
            <div>FIRE Progress: <strong style="color:#10b981;" class="font-mono">${progressPct}%</strong> (${res.years_remaining || 0} yrs left)</div>
            <div>Status: <strong style="color:#06b6d4;">${res.fire_status || "IN_PROGRESS"}</strong> (${res.active_scenario_label || "Base"})</div>
          </div>
        `;
      } else if (toolName === "getTaxHarvestOpportunities") {
        bodyHtml = `
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px; font-size:12px; margin-top:8px;">
            <div>Exemption Left: <strong style="color:#10b981;" class="font-mono">₹ ${formatINR(parseFloat(res.exemption_remaining || 0))}</strong></div>
            <div>Taxable LTCG YTD: <strong style="color:#06b6d4;" class="font-mono">₹ ${formatINR(parseFloat(res.taxable_ltcg_so_far || 0))}</strong></div>
            <div>Available Lots: <strong style="color:#d0ff00;">${res.total_opportunities || 0} lots</strong></div>
            <div>Fiscal Year: <strong style="color:#94a3b8;">FY ${res.fiscal_year || ""}</strong></div>
          </div>
        `;
      } else if (toolName === "getFundRegistry") {
        const funds = res.funds || [];
        const rows = funds.slice(0, 5).map((f) => `
          <div class="cmd-step-row">
            <span>${f.scheme_name} (${f.status})</span>
            <span class="font-mono">₹ ${formatINR(parseFloat(f.current_value || 0))}</span>
          </div>
        `).join("");
        bodyHtml = `
          <div style="font-size:12px; color:#94a3b8; margin-top:6px;">Total Tracked Funds: <strong>${res.total_funds || funds.length}</strong></div>
          <div class="cmd-action-steps">${rows}</div>
        `;
      } else {
        bodyHtml = `
          <pre style="white-space:pre-wrap; font-size:11px; font-family:monospace; color:#cbd5e1; max-height:180px; overflow-y:auto; margin-top:8px; padding:6px; background:rgba(0,0,0,0.3); border-radius:4px;">${JSON.stringify(res, null, 2)}</pre>
        `;
      }

      results.innerHTML = `
        <div class="cmd-action-card">
          <div class="cmd-action-header">
            <span>⚡ Agent Tool Result: <strong style="color:#d0ff00;">${toolName}</strong></span>
            <span style="font-size:11px; color:#10b981;">STATUS: ${data.status}</span>
          </div>
          ${bodyHtml}
        </div>
      `;
    } catch (err) {
      console.error("Agent tool execution error:", err);
      results.innerHTML = `<div style="padding:12px; color:#ef4444; font-family:monospace;">⚠️ Tool execution failed: ${err.message}</div>`;
    }
  };

  document.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      const activeEl = document.activeElement;
      if (activeEl && activeEl.id === "commandPaletteInput") {
        e.preventDefault();
        window.submitAiPrompt();
      }
    }
  });

  // Wire Conversational Copilot quick action chips
  document.querySelectorAll(".cmd-chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      const prompt = chip.getAttribute("data-prompt");
      const input = document.getElementById("commandPaletteInput");
      if (input && prompt) {
        input.value = prompt;
        window.submitAiPrompt();
      }
    });
  });

  // Wire command palette quick actions list
  document.querySelectorAll(".cmd-item").forEach((item) => {
    item.addEventListener("click", () => {
      const action = item.getAttribute("data-action");
      const input = document.getElementById("commandPaletteInput");
      if (action === "whatif") {
        if (input) {
          input.value = "Simulate selling 50 units of Parag Parikh";
          window.submitAiPrompt();
        }
      } else if (action === "schedule-cg") {
        window.closeCmdPalette();
        const tabBtn = document.querySelector('.tab-btn[data-tab="tax-lots"]');
        if (tabBtn) tabBtn.click();
      } else if (action === "rebalance") {
        if (input) {
          input.value = "Explain rebalance triggers";
          window.submitAiPrompt();
        }
      } else if (action === "holdings") {
        window.closeCmdPalette();
        const tabBtn = document.querySelector('.tab-btn[data-tab="holdings"]');
        if (tabBtn) tabBtn.click();
      } else if (action === "radar") {
        window.closeCmdPalette();
        const sentinel = document.getElementById("marketRiskSentinelCard");
        if (sentinel) sentinel.scrollIntoView({ behavior: "smooth" });
      }
    });
  });

  const cmdTrigger = document.getElementById("cmdKTriggerBtn");
  if (cmdTrigger) {
    cmdTrigger.addEventListener("click", window.openCmdPalette);
  }

  const closeCmdBtn = document.getElementById("closeCmdPaletteBtn");
  if (closeCmdBtn) {
    closeCmdBtn.addEventListener("click", window.closeCmdPalette);
  }

  const slider = document.getElementById("rebalanceSlider");
  const sliderVal = document.getElementById("rebalanceSliderVal");
  if (slider && sliderVal) {
    slider.addEventListener("input", () => {
      const val = parseInt(slider.value) || 100000;
      sliderVal.textContent = formatINR(val);
      fetchRebalancePreview(val);
    });
  }

  const tabBtns = document.querySelectorAll(".tab-btn");
  tabBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const tabName = btn.dataset.tab;
      document.querySelectorAll(".tab-btn").forEach((b) => {
        b.classList.remove("active");
      });
      document.querySelectorAll(".tab-content").forEach((c) => {
        c.classList.remove("active");
      });

      btn.classList.add("active");
      const targetContent = document.getElementById(`tab-${tabName}`);
      if (targetContent) targetContent.classList.add("active");

      if (tabName === "fire") {
        fetchFireSummary();
      }
    });
  });
});

async function uploadCasFile(file, password) {
  const statusEl = document.getElementById("casUploadStatus");
  const token =
    localStorage.getItem("API_AUTH_TOKEN") || window.API_AUTH_TOKEN || DEFAULT_AUTH_TOKEN;

  if (statusEl) {
    statusEl.style.display = "block";
    statusEl.style.background = "rgba(6, 182, 212, 0.1)";
    statusEl.style.color = "#06b6d4";
    statusEl.style.border = "1px solid rgba(6, 182, 212, 0.3)";
    statusEl.textContent = "⚡ Decrypting & Parsing CAS transactions...";
  }

  const formData = new FormData();
  formData.append("file", file);
  if (password) formData.append("password", password);

  try {
    const res = await fetch(`/api/v1/statements/upload`, {
      method: "POST",
      headers: {
        "X-Api-Auth-Token": token,
      },
      body: formData,
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => "Upload failed");
      throw new Error(errText || `Server returned ${res.status}`);
    }

    const events = await res.json();
    showToast(
      `✅ Successfully ingested CAS statement! Registered ${events ? events.length || 0 : 0} transaction events.`,
      "success",
    );
    window.closeCasPasswordModal();
    initDashboard();
  } catch (err) {
    console.error("CAS upload failed:", err);
    if (statusEl) {
      statusEl.style.display = "block";
      statusEl.style.background = "rgba(239, 68, 68, 0.1)";
      statusEl.style.color = "#ef4444";
      statusEl.style.border = "1px solid rgba(239, 68, 68, 0.3)";
      statusEl.textContent = `⚠️ CAS Parsing Failed: ${err.message || "Incorrect password or unsupported file format"}`;
    }
  }
}

let currentSelectedCasFile = null;

window.closeCasPasswordModal = () => {
  const modal = document.getElementById("casPasswordModal");
  if (modal) modal.style.display = "none";
  const fileInput = document.getElementById("fileUploadInput");
  if (fileInput) fileInput.value = "";
  currentSelectedCasFile = null;
};

window.handleFileSelect = (e) => {
  const file = e.target ? e.target.files[0] : e.files ? e.files[0] : null;
  if (!file) return;
  currentSelectedCasFile = file;

  if (file.name.toLowerCase().endsWith(".pdf")) {
    const modal = document.getElementById("casPasswordModal");
    const filenameEl = document.getElementById("casModalFilename");
    const passInput = document.getElementById("casPasswordInput");
    const statusEl = document.getElementById("casUploadStatus");

    if (filenameEl) filenameEl.textContent = file.name;
    if (passInput) passInput.value = "";
    if (statusEl) statusEl.style.display = "none";
    if (modal) modal.style.display = "flex";
    if (passInput) setTimeout(() => passInput.focus(), 100);
  } else {
    uploadCasFile(file, "");
  }
};

window.submitCasUpload = () => {
  const passInput = document.getElementById("casPasswordInput");
  const password = passInput ? passInput.value : "";
  if (currentSelectedCasFile) {
    uploadCasFile(currentSelectedCasFile, password);
  }
};
