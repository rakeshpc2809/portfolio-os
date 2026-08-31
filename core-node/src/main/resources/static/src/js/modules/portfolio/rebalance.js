import { API_BASE, fetchJson } from "../../api.js?t=1788114000";
import { FUND_REGISTRY, getActionBadgeStyle } from "../../constants.js?t=1788114000";
import { setBadgeStyle, setErrorState, setHtml, setText } from "../../domUtils.js?t=1788114000";
import { state } from "../../state.js?t=1788114000";
import { formatINR, shortenFundName } from "../../utils.js?t=1788114000";

export async function fetchConsolidationPreviewData() {
  try {
    const data = await fetchJson(
      `${API_BASE}/portfolio/consolidation-preview?fy=${state.currentFy}`,
    ).catch(() => null);
    if (data) {
      renderConsolidationPlan(data);
    }
  } catch (e) {
    console.error("Error fetching consolidation preview:", e);
  }
}

export function renderConsolidationPlan(data) {
  const container = document.getElementById("consolidationPlanContainer");
  const badge = document.getElementById("consolidationWindowBadge");
  if (!container) return;

  const isWindowOpen =
    data.is_rebalance_window_open !== undefined
      ? data.is_rebalance_window_open
      : data.isRebalanceWindowOpen;
  const nextWindow = data.next_scheduled_window || data.nextScheduledWindow;
  const totalProceeds = data.total_proceeds || data.totalProceeds;
  const totalTaxDrag = data.total_tax_drag || data.totalTaxDrag;
  const proRata = data.pro_rata_allocations || data.proRataAllocations || [];

  if (badge) {
    badge.textContent = isWindowOpen
      ? "WINDOW OPEN: EXECUTE REBALANCE"
      : `SCHEDULED WINDOW: ${nextWindow}`;
    badge.style.color = isWindowOpen ? "#10b981" : "#06b6d4";
  }

  const parsedProceeds = parseFloat(totalProceeds);
  const proceeds = !Number.isNaN(parsedProceeds) ? Math.round(parsedProceeds) : 0;
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
    const data = await fetchJson(
      `${API_BASE}/portfolio/rebalance-preview?amount=${amount}&fy=${state.currentFy}`,
    ).catch(() => null);
    if (data) {
      updateRebalanceSummary(data);
    }
  } catch (e) {
    console.error("Error fetching rebalance preview:", e);
  }
}

export function updateRebalanceSummary(data) {
  const rebTaxDrag = document.getElementById("rebTaxDrag");
  const rebEffRate = document.getElementById("rebEffRate");
  const rebLtcgHarvested = document.getElementById("rebLtcgHarvested");

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



export async function fetchBucketRebalance() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/buckets/rebalance`).catch(() => null);
    if (data) {
      renderBucketRebalance(data);
    }
  } catch (e) {
    console.error("Bucket rebalance error:", e);
  }
}

export function renderBucketRebalance(data) {
  const drawdownTag = document.getElementById("drawdownTag");
  const bucketGrid = document.getElementById("bucketGrid");

  const dd = data.drawdown_status || data.drawdownStatus;
  const statuses = data.bucket_statuses || data.bucketStatuses;

  if (drawdownTag && dd) {
    const bmName = dd.benchmark_name || dd.benchmarkName;
    const ddPct = dd.drawdown_pct || dd.drawdownPct;
    drawdownTag.textContent = `${bmName}: ${ddPct}% Drawdown`;
  }

  if (bucketGrid && statuses) {
    let html = "";
    statuses.forEach((b) => {
      const isDrifted = b.is_drifted !== undefined ? b.is_drifted : b.isDrifted;
      const driftPct = b.drift_pct || b.driftPct;
      const curVal = b.current_value || b.currentValue;
      const curPct = b.current_pct || b.currentPct;
      const targetPct = b.target_pct || b.targetPct;

      const nameFmt = b.bucket.replace("_", " ");
      html += `
        <div class="bucket-card ${isDrifted ? "drifted" : ""}">
          <div class="bucket-card-header">
            <span class="bucket-name">${nameFmt}</span>
            <span class="drift-badge ${isDrifted ? "warn" : "ok"}">${isDrifted ? "Drift: " + driftPct + "%" : "Target OK"}</span>
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
    holdingsData.forEach((h) => {
      const cur = parseFloat(h.current_value || h.currentValue) || 0;
      const cat = h.category || "";
      if (cat === "EQUITY") totalEquity += cur;
      else if (cat === "GOLD_SILVER" || cat === "SGB") totalGold += cur;
      else totalLiquid += cur;
    });
  }

  if (bucketData?.recommendations) {
    bucketData.recommendations.forEach((r) => {
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
    { name: "Portfolio Capital" },
    { name: "Equity Core" },
    { name: "Liquid Buffer" },
    { name: "Gold & Commodities" },
    { name: "Net Core Wealth" },
    { name: "Est Tax Liability" },
    { name: "Emergency Cash" },
  ];

  const links = [
    { source: "Portfolio Capital", target: "Equity Core", value: totalEquity },
    { source: "Portfolio Capital", target: "Liquid Buffer", value: totalLiquid },
    { source: "Portfolio Capital", target: "Gold & Commodities", value: totalGold },
    { source: "Equity Core", target: "Net Core Wealth", value: netEquity },
    { source: "Equity Core", target: "Est Tax Liability", value: Math.max(10, totalTaxDrag) },
    { source: "Liquid Buffer", target: "Emergency Cash", value: totalLiquid },
  ];

  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: "transparent",
    tooltip: { trigger: "item", triggerOn: "mousemove" },
    series: [
      {
        type: "sankey",
        data: nodes,
        links: links,
        emphasis: { focus: "adjacency" },
        lineStyle: { color: "gradient", curveness: 0.5, opacity: 0.45 },
        label: { color: "#f8fafc", fontFamily: "Inter", fontSize: 11, fontWeight: "bold" },
        itemStyle: { borderWidth: 1, borderColor: "#06b6d4" },
      },
    ],
  };
  instance.setOption(option);
  return instance;
}



export async function loadActionRecommendations() {
  const container = document.getElementById("actionCardsList");
  if (!container) return;

  try {
    const cards = await fetchJson(`${API_BASE}/rules/action-recommendations`);
    if (!cards || cards.length === 0) {
      container.innerHTML = '<div style="color: #64748b;">No rule recommendations generated.</div>';
      return;
    }

    let html = "";
    cards.forEach((c) => {
      let badgeBg = "#3b82f6";
      let badgeColor = "#ffffff";
      if (c.status === "ACTION_RECOMMENDED") {
        badgeBg = c.severity === "HIGH" ? "rgba(239, 68, 68, 0.2)" : "rgba(245, 158, 11, 0.2)";
        badgeColor = c.severity === "HIGH" ? "#f87171" : "#fbbf24";
      } else if (c.status === "GATED_PROVISIONAL") {
        badgeBg = "rgba(100, 116, 139, 0.2)";
        badgeColor = "#94a3b8";
      } else {
        badgeBg = "rgba(16, 185, 129, 0.2)";
        badgeColor = "#34d399";
      }

      html += `
        <div style="background: rgba(15, 23, 42, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; padding: 16px; display: flex; flex-direction: column; justify-content: space-between;">
          <div>
            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px;">
              <h3 style="font-size: 0.95rem; margin: 0; color: #f8fafc; line-height: 1.3;">${c.title}</h3>
              <span style="font-size: 0.65rem; padding: 2px 8px; border-radius: 4px; background: ${badgeBg}; color: ${badgeColor}; font-weight: 600; white-space: nowrap;">
                ${c.status.replace("_", " ")}
              </span>
            </div>
            <p style="font-size: 0.82rem; color: #cbd5e1; margin: 0 0 10px 0; font-weight: 500;">${c.summary}</p>
            <p style="font-size: 0.75rem; color: #94a3b8; margin: 0 0 12px 0; line-height: 1.4;">${c.detailed_rationale || c.detailedRationale}</p>
          </div>
          <div>
            <div style="font-size: 0.65rem; color: #64748b; border-top: 1px dashed rgba(255,255,255,0.08); padding-top: 8px; display: flex; justify-content: space-between; align-items: center;">
              <span>${c.provenance_footer || c.provenanceFooter}</span>
              <button onclick="this.closest('div[style*='background']').style.opacity='0.4';" style="background: transparent; border: 1px solid #475569; color: #94a3b8; font-size: 0.65rem; border-radius: 3px; padding: 1px 6px; cursor: pointer;">Review</button>
            </div>
          </div>
        </div>
      `;
    });

    container.innerHTML = html;
  } catch (err) {
    console.error("Failed to load action recommendations:", err);
    if (container) {
      container.innerHTML = `<div style="color: #f87171;">⚠️ Action Recommendations Unavailable</div>`;
    }
  }
}



export async function loadUnifiedRebalancePlan(
  triggerType = "INDUCED",
  manualAmount = null,
  includeRebalance = false,
) {
  try {
    let url = `/api/v1/sync/rebalance/plan?trigger=${encodeURIComponent(triggerType)}`;
    let options = { method: "GET" };

    if (triggerType === "MANUAL_LUMPSUM") {
      url = `/api/v1/sync/rebalance/simulate-lumpsum`;
      options = {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          amount: manualAmount || 100000.0,
          includeRebalance: Boolean(includeRebalance),
        }),
      };
    }

    const plan = await fetchJson(url, options);
    renderUnifiedRebalancePlanUI(plan);
  } catch (err) {
    console.error("Failed to load Unified Rebalance Plan:", err);
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
  const badgeEl = document.getElementById("rebalanceTriggerBadge");
  const ddPctEl = document.getElementById("stripDrawdownPct");
  const highEl = document.getElementById("stripRollingHigh");
  const windowEl = document.getElementById("stripReconWindow");

  if (badgeEl && trigger) {
    if (trigger.type === "MANUAL_LUMPSUM" || lumpsumMeta) {
      const isIncRebal = lumpsumMeta
        ? (lumpsumMeta.include_rebalance ?? lumpsumMeta.includeRebalance)
        : false;
      badgeEl.textContent = isIncRebal
        ? "MANUAL LUMP-SUM + REBALANCE"
        : "MANUAL LUMP-SUM ONLY (NO SALES)";
      if (isIncRebal) {
        badgeEl.style.background = "rgba(168, 85, 247, 0.2)";
        badgeEl.style.color = "#c084fc";
        badgeEl.style.borderColor = "#a855f7";
      } else {
        badgeEl.style.background = "rgba(56, 189, 248, 0.2)";
        badgeEl.style.color = "#38bdf8";
        badgeEl.style.borderColor = "#0284c7";
      }
    } else {
      badgeEl.textContent = trigger.reason_label || trigger.reasonLabel || "REBALANCE TRIGGERED";
      if (trigger.type === "INDUCED") {
        badgeEl.style.background = "rgba(239, 68, 68, 0.2)";
        badgeEl.style.color = "#f87171";
        badgeEl.style.borderColor = "#ef4444";
      } else if (trigger.type === "SCHEDULED") {
        badgeEl.style.background = "rgba(56, 189, 248, 0.2)";
        badgeEl.style.color = "#38bdf8";
        badgeEl.style.borderColor = "#0284c7";
      } else {
        badgeEl.style.background = "rgba(168, 85, 247, 0.2)";
        badgeEl.style.color = "#c084fc";
        badgeEl.style.borderColor = "#a855f7";
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
    windowEl.textContent =
      trigger.scheduled_window_label || trigger.scheduledWindowLabel || "March 2027 Window";
  }

  // 2. Render Header & Drawdown Gauge
  const titleEl = document.getElementById("planHeadlineTitle");
  const metaEl = document.getElementById("planMetaTimestamp");
  if (titleEl && narrative) {
    titleEl.textContent = narrative.headline || "Unified Rebalance Plan";
  }
  const genAt = plan.generated_at || plan.generatedAt;
  if (metaEl && genAt) {
    metaEl.textContent = `Generated: ${new Date(genAt).toLocaleString()}`;
  }

  // Drawdown Tripwire Depth Gauge
  const ddPct = drawdownCtx.current_drawdown_pct ?? drawdownCtx.currentDrawdownPct ?? 0;
  const barEl = document.getElementById("gaugeProgressBar");
  const markEl = document.getElementById("gaugeIndicatorMarker");
  const statusEl = document.getElementById("gaugeStatusText");
  const distEl = document.getElementById("gaugeNextDistance");

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
    const nextT = drawdownCtx.next_tier ?? drawdownCtx.nextTier ?? "TIER_10";
    distEl.textContent = `${dist}% to ${nextT}`;
  }

  // 3. Exemption Headroom Burndown Bar
  const taxSum = sellSide.tax_summary || sellSide.taxSummary || {};
  const headroomBefore =
    taxSum.exemption_headroom_before ?? taxSum.exemptionHeadroomBefore ?? 125000;
  const tradeExempt = taxSum.total_ltcg_exempt ?? taxSum.totalLtcgExempt ?? 0;
  const taxableSpill = taxSum.total_stcg_taxable ?? taxSum.totalStcgTaxable ?? 0;
  const headroomAfter = taxSum.exemption_headroom_after ?? taxSum.exemptionHeadroomAfter ?? 112580;
  const priorUsed = Math.max(0, 125000 - headroomBefore);

  const burnPriorEl = document.getElementById("burnUsedPrior");
  const burnTradeEl = document.getElementById("burnTradeExempt");
  const burnSpillEl = document.getElementById("burnTaxableSpill");
  const burnRemTag = document.getElementById("burndownHeadroomRemaining");

  if (burnPriorEl) burnPriorEl.style.width = `${(priorUsed / 125000) * 100}%`;
  if (burnTradeEl) burnTradeEl.style.width = `${(tradeExempt / 125000) * 100}%`;
  if (burnSpillEl) burnSpillEl.style.width = `${(taxableSpill / 125000) * 100}%`;
  if (burnRemTag)
    burnRemTag.textContent = `Remaining Headroom: ₹${headroomAfter.toLocaleString("en-IN")}`;

  const burnTextPrior = document.getElementById("burnTextPrior");
  const burnTextTrade = document.getElementById("burnTextTrade");
  const burnTextRem = document.getElementById("burnTextRem");

  if (burnTextPrior)
    burnTextPrior.textContent = `Prior Used: ₹${priorUsed.toLocaleString("en-IN")}`;
  if (burnTextTrade)
    burnTextTrade.textContent = `Trade Exempt: ₹${tradeExempt.toLocaleString("en-IN")}`;
  if (burnTextRem) burnTextRem.textContent = `Remaining: ₹${headroomAfter.toLocaleString("en-IN")}`;

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
  const pContainer = document.getElementById("planReasoningParagraphs");
  if (pContainer && narrative.paragraphs) {
    pContainer.innerHTML = narrative.paragraphs
      .map(
        (p) => `
      <p style="margin: 0 0 6px 0; font-size: 0.8rem; line-height: 1.4;">• ${p}</p>
    `,
      )
      .join("");
  }

  // 7. Render Buy-Side Allocation Grid
  renderBuySideAllocationGrid(buySide);
}

function renderBuySideAllocationGrid(buySide, liveTotalOverride = null) {
  const buyGrid = document.getElementById("buySideAllocationGrid");
  if (!buyGrid || !buySide.buckets) return;

  const totalPool =
    liveTotalOverride !== null
      ? liveTotalOverride
      : (buySide.total_to_invest ?? buySide.totalToInvest ?? 0);

  buyGrid.innerHTML = buySide.buckets
    .map((b) => {
      const tgt = b.target_pct ?? b.targetPct ?? 0;
      const cur = b.current_pct ?? b.currentPct ?? 0;
      const post = b.post_rebalance_pct ?? b.postRebalancePct ?? 0;
      const alloc = totalPool * (tgt / 100.0);

      const fundsHtml = (b.fund_breakdown || b.fundBreakdown || [])
        .map((f) => {
          const fName = f.fund_name || f.fundName || f.fund_id;
          const fAlloc =
            alloc *
            (f.allocation_weight || 1.0 / (b.fund_breakdown || b.fundBreakdown || [1]).length);
          return `
        <div style="display: flex; justify-content: space-between; font-size: 0.7rem; color: #cbd5e1; margin-top: 4px; border-top: 1px dashed rgba(255,255,255,0.06); padding-top: 3px;">
          <span>• ${fName}</span>
          <span style="font-weight: 700; color: #34d399;">+₹${Math.round(fAlloc).toLocaleString("en-IN")}</span>
        </div>
      `;
        })
        .join("");

      return `
      <div style="background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 6px; padding: 12px;">
        <div style="font-size: 0.8rem; font-weight: 700; color: #38bdf8;">${b.bucket.replace("_", " ")}</div>
        <div style="display: flex; justify-content: space-between; font-size: 0.75rem; margin-top: 6px; color: #94a3b8;">
          <span>Target: ${tgt}%</span>
          <span>Current: ${cur}%</span>
          <span style="color: #34d399; font-weight: 700;">Post: ${post}%</span>
        </div>
        <div style="margin-top: 8px; font-size: 0.95rem; font-weight: 800; color: #f8fafc;">
          +₹${Math.round(alloc).toLocaleString("en-IN")}
        </div>
        <div style="margin-top: 6px;">
          ${fundsHtml}
        </div>
      </div>
    `;
    })
    .join("");
}



function renderRebalanceBoxConnector(plan) {
  const sellSide = plan.sell_side || plan.sellSide || {};
  const buySide = plan.buy_side || plan.buySide || {};
  const taxSum = sellSide.tax_summary || sellSide.taxSummary || {};

  const totalRealized = parseFloat(
    taxSum.total_sale_proceeds ??
      taxSum.totalSaleProceeds ??
      buySide.total_to_invest ??
      buySide.totalToInvest ??
      0,
  );
  const tradeExempt = parseFloat(
    taxSum.total_ltcg_exempt ?? taxSum.totalLtcgExempt ?? taxSum.total_ltcg_exemption_applied ?? 0,
  );
  const taxSavedTotal = Math.round(tradeExempt * 0.125);
  const headroomBefore = parseFloat(
    taxSum.exemption_headroom_before ?? taxSum.exemptionHeadroomBefore ?? 125000,
  );
  const headroomAfter = parseFloat(
    taxSum.exemption_headroom_after ??
      taxSum.exemptionHeadroomAfter ??
      headroomBefore - tradeExempt,
  );
  const priorUsed = Math.max(0, 125000 - headroomBefore);
  const totalYtdExempt = priorUsed + tradeExempt;
  const headroomRem = headroomAfter;
  const totalTax = parseFloat(taxSum.total_tax_estimate ?? taxSum.totalTaxEstimate ?? 0);

  // 1. Update Summary Bar
  const elRealized = document.getElementById("sumRealizedProceeds");
  const elTradeEx = document.getElementById("sumTradeExemption");
  const elTaxSaved = document.getElementById("sumTaxSaved");
  const elYtdEx = document.getElementById("sumYtdExemption");
  const elHeadroom = document.getElementById("sumRemainingHeadroom");
  const elTax = document.getElementById("sumTaxOwed");

  if (elRealized) elRealized.textContent = `₹${Math.round(totalRealized).toLocaleString("en-IN")}`;
  if (elTradeEx) elTradeEx.textContent = `₹${Math.round(tradeExempt).toLocaleString("en-IN")}`;
  if (elTaxSaved) elTaxSaved.textContent = `+₹${taxSavedTotal.toLocaleString("en-IN")}`;
  if (elYtdEx)
    elYtdEx.textContent = `₹${Math.round(totalYtdExempt).toLocaleString("en-IN")} of ₹1,25,000`;
  if (elHeadroom) elHeadroom.textContent = `₹${Math.round(headroomRem).toLocaleString("en-IN")}`;
  if (elTax) elTax.textContent = `₹${Math.round(totalTax).toLocaleString("en-IN")}`;

  // 2. Build Sell Cards Column (Fund-Wise Aggregated)
  const sellCol = document.getElementById("rebalanceSellCardsCol");
  const sellFundMap = new Map();

  (sellSide.waterfall || []).forEach((tier) => {
    const tLabel = tier.tier_label || tier.tierLabel || "Waterfall Tier";
    (tier.lots || []).forEach((lot) => {
      const fName = shortenFundName(lot.fundName || lot.fund_name || lot.fundId || lot.fund_id);
      const proceeds = parseFloat(lot.saleProceeds || lot.sale_proceeds || 0);
      const units = parseFloat(lot.units_sold || lot.unitsSold || lot.units || 0);
      const gain = parseFloat(lot.realizedGain || lot.realized_gain || 0);
      const ti = lot.tax_impact || lot.taxImpact || {};
      const regime =
        ti.regime || ti.regime_type || lot.tax_term || lot.taxTerm || "SEC_112A_EXEMPT";

      if (proceeds > 0) {
        if (!sellFundMap.has(fName)) {
          sellFundMap.set(fName, {
            name: fName,
            proceeds: 0,
            units: 0,
            gain: 0,
            regime: regime,
            tierLabel: tLabel,
          });
        }
        const existing = sellFundMap.get(fName);
        existing.proceeds += proceeds;
        existing.units += units;
        existing.gain += gain;
        if (regime === "SLAB_RATE_STCG") existing.regime = "SLAB_RATE_STCG";
        else if (regime === "SEC_112A_TAXABLE_12_5" && existing.regime !== "SLAB_RATE_STCG")
          existing.regime = "SEC_112A_TAXABLE_12_5";
      }
    });
  });

  if (sellCol) {
    if (sellFundMap.size > 0) {
      sellCol.innerHTML = Array.from(sellFundMap.values())
        .map((f) => {
          const fundTaxSaved = Math.round(Math.max(0, f.gain) * 0.125);
          let badgeBg = "rgba(16, 185, 129, 0.15)";
          let badgeColor = "#10b981";
          let badgeBorder = "#10b981";
          let badgeLabel =
            fundTaxSaved > 0
              ? `LTCG EXEMPT (Saved +₹${fundTaxSaved.toLocaleString("en-IN")} Tax)`
              : "LTCG EXEMPT";

          if (f.regime === "SLAB_RATE_STCG") {
            badgeBg = "rgba(239, 68, 68, 0.15)";
            badgeColor = "#ef4444";
            badgeBorder = "#ef4444";
            badgeLabel = "STCG (20%)";
          } else if (f.regime === "SEC_112A_TAXABLE_12_5") {
            badgeBg = "rgba(245, 158, 11, 0.15)";
            badgeColor = "#f59e0b";
            badgeBorder = "#f59e0b";
            badgeLabel = "LTCG (12.5%)";
          }

          return `
          <div class="rebalance-sell-card" style="background: rgba(30, 41, 59, 0.75); border: 1px solid rgba(244,63,94,0.3); border-left: 4px solid #f43f5e; border-radius: 6px; padding: 8px 12px; display: flex; justify-content: space-between; align-items: center; font-size: 0.78rem;">
            <div>
              <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 4px;">
                <span style="background: rgba(244, 63, 94, 0.2); color: #fb7185; border: 1px solid #f43f5e; font-size: 0.6rem; padding: 1px 5px; border-radius: 3px; font-weight: 800;">SELL</span>
                ${f.name}
              </div>
              <div style="font-size: 0.7rem; color: #94a3b8; margin-top: 3px;">
                ${f.units > 0 ? `${f.units.toFixed(1)} units` : ""} · <span style="color: #cbd5e1;">${f.tierLabel}</span>
                <span style="background: ${badgeBg}; color: ${badgeColor}; border: 1px solid ${badgeBorder}; font-size: 0.62rem; padding: 1px 5px; border-radius: 3px; margin-left: 6px; font-weight: 600;">${badgeLabel}</span>
              </div>
            </div>
            <div style="font-weight: 800; color: #fb7185; font-size: 0.85rem;">
              -₹${Math.round(f.proceeds).toLocaleString("en-IN")}
            </div>
          </div>
        `;
        })
        .join("");
    } else {
      sellCol.innerHTML = `
        <div style="background: rgba(30, 41, 59, 0.6); border: 1px dashed rgba(255,255,255,0.1); border-radius: 6px; padding: 12px; text-align: center; color: #94a3b8; font-size: 0.78rem;">
          No liquidations required — using available cash reserves
        </div>
      `;
    }
  }

  // 3. Build Central Pool Amount
  const elPoolAmt = document.getElementById("rebalancePoolAmount");
  if (elPoolAmt) elPoolAmt.textContent = `₹${Math.round(totalRealized).toLocaleString("en-IN")}`;

  // 4. Build Buy Cards Column
  const buyCol = document.getElementById("rebalanceBuyCardsCol");
  const buyFunds = [];

  (buySide.buckets || []).forEach((b) => {
    const bucketName = (b.bucket || "").replace("_", " ");
    (b.fund_breakdown || b.fundBreakdown || []).forEach((f) => {
      const fName = shortenFundName(f.fundName || f.fund_name || f.fundId || f.fund_id);
      const amt = parseFloat(f.amount || 0);
      if (amt > 0) {
        buyFunds.push({ name: fName, amount: amt, bucket: bucketName });
      }
    });
  });

  if (buyCol) {
    if (buyFunds.length > 0) {
      buyCol.innerHTML = buyFunds
        .map(
          (f) => `
        <div class="rebalance-buy-card" style="background: rgba(30, 41, 59, 0.75); border: 1px solid rgba(16, 185, 129, 0.3); border-right: 4px solid #10b981; border-radius: 6px; padding: 8px 12px; display: flex; justify-content: space-between; align-items: center; font-size: 0.78rem;">
          <div>
            <div style="font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 4px;">
              <span style="background: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid #10b981; font-size: 0.6rem; padding: 1px 5px; border-radius: 3px; font-weight: 800;">BUY</span>
              ${f.name}
            </div>
            <div style="font-size: 0.68rem; color: #34d399; margin-top: 3px; font-weight: 600;">${f.bucket}</div>
          </div>
          <div style="font-weight: 800; color: #34d399; font-size: 0.85rem;">
            +₹${Math.round(f.amount).toLocaleString("en-IN")}
          </div>
        </div>
      `,
        )
        .join("");
    } else {
      buyCol.innerHTML = `<div style="text-align: center; color: #64748b; padding: 12px; font-size: 0.78rem;">No target buy allocations</div>`;
    }
  }

  // 5. Draw SVG Bezier Connectors
  setTimeout(drawBoxSvgConnectors, 50);

  // 6. View Toggle Event Listeners
  const btnBox = document.getElementById("btnViewBoxConnector");
  const btnSankey = document.getElementById("btnViewSankey");
  const boxContainer = document.getElementById("rebalanceBoxConnectorContainer");
  const sankeyContainer = document.getElementById("rebalanceSankeyChartContainer");

  if (btnBox && btnSankey && boxContainer && sankeyContainer) {
    btnBox.onclick = () => {
      boxContainer.style.display = "flex";
      sankeyContainer.style.display = "none";
      btnBox.style.background = "rgba(56, 189, 248, 0.2)";
      btnBox.style.color = "#38bdf8";
      btnBox.style.borderColor = "#38bdf8";
      btnSankey.style.background = "rgba(255,255,255,0.05)";
      btnSankey.style.color = "#94a3b8";
      btnSankey.style.borderColor = "rgba(255,255,255,0.1)";
      setTimeout(drawBoxSvgConnectors, 50);
    };

    btnSankey.onclick = () => {
      boxContainer.style.display = "none";
      sankeyContainer.style.display = "block";
      btnSankey.style.background = "rgba(56, 189, 248, 0.2)";
      btnSankey.style.color = "#38bdf8";
      btnSankey.style.borderColor = "#38bdf8";
      btnBox.style.background = "rgba(255,255,255,0.05)";
      btnBox.style.color = "#94a3b8";
      btnBox.style.borderColor = "rgba(255,255,255,0.1)";

      const sankeyEl = document.getElementById("rebalanceSankeyChart");
      if (sankeyEl && typeof echarts !== "undefined") {
        const inst = echarts.getInstanceByDom(sankeyEl);
        if (inst) inst.resize();
      }
    };
  }
}

function drawBoxSvgConnectors() {
  const container = document.getElementById("rebalanceBoxConnectorContainer");
  const poolPill = document.getElementById("rebalancePoolPill");
  const svg = document.getElementById("rebalanceConnectorSvg");
  if (!container || !poolPill || !svg) return;

  const containerRect = container.getBoundingClientRect();
  const poolRect = poolPill.getBoundingClientRect();

  const poolLeftX = poolRect.left - containerRect.left;
  const poolRightX = poolRect.right - containerRect.left;
  const poolY = poolRect.top + poolRect.height / 2 - containerRect.top;

  let pathHtml = "";

  // Sell Cards -> Pool Left (Rose Red dashed bezier)
  const sellCards = document.querySelectorAll(".rebalance-sell-card");
  sellCards.forEach((card) => {
    const cRect = card.getBoundingClientRect();
    const cardX = cRect.right - containerRect.left;
    const cardY = cRect.top + cRect.height / 2 - containerRect.top;

    const dx = (poolLeftX - cardX) * 0.5;
    pathHtml += `<path d="M ${cardX} ${cardY} C ${cardX + dx} ${cardY}, ${poolLeftX - dx} ${poolY}, ${poolLeftX} ${poolY}" fill="none" stroke="rgba(244, 63, 94, 0.6)" stroke-width="2" stroke-dasharray="4 3" />`;
  });

  // Pool Right -> Buy Cards (Emerald Green solid bezier)
  const buyCards = document.querySelectorAll(".rebalance-buy-card");
  buyCards.forEach((card) => {
    const cRect = card.getBoundingClientRect();
    const cardX = cRect.left - containerRect.left;
    const cardY = cRect.top + cRect.height / 2 - containerRect.top;

    const dx = (cardX - poolRightX) * 0.5;
    pathHtml += `<path d="M ${poolRightX} ${poolY} C ${poolRightX + dx} ${poolY}, ${cardX - dx} ${cardY}, ${cardX} ${cardY}" fill="none" stroke="rgba(16, 185, 129, 0.6)" stroke-width="2" />`;
  });

  svg.innerHTML = pathHtml;
}

function renderPrePostAllocationDelta(plan) {
  const container = document.getElementById("rebalanceAllocationDeltaContainer");
  const buySide = plan.buy_side || plan.buySide || {};

  if (!container || !buySide.buckets) return;

  container.innerHTML = buySide.buckets
    .map((b) => {
      const name = (b.bucket || "").replace("_", " ");
      const tgt = b.target_pct ?? b.targetPct ?? 0;
      const cur = b.current_pct ?? b.currentPct ?? 0;
      const post = b.post_rebalance_pct ?? b.postRebalancePct ?? 0;

      let deltaColor = "#34d399"; // Green for increase or match
      if (post < cur) deltaColor = "#f87171"; // Red for decrease

      return `
      <div style="background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 6px; padding: 6px 12px; font-size: 0.75rem; display: flex; align-items: center; gap: 8px;">
        <span style="font-weight: 700; color: #38bdf8;">${name}:</span>
        <span style="color: #94a3b8;">${cur.toFixed(1)}%</span>
        <span style="color: #64748b;">➔</span>
        <span style="font-weight: 800; color: ${deltaColor};">${post.toFixed(1)}%</span>
        <span style="color: #64748b; font-size: 0.7rem;">(Target ${tgt.toFixed(1)}%)</span>
      </div>
    `;
    })
    .join("");
}

export function renderTargetFundProgression(plan, holdings, bucketTargetsConfig) {
  const container = document.getElementById("rebalanceFundProgressionContainer");
  if (!container) return;

  const sellSide = plan.sell_side || plan.sellSide || {};
  const buySide = plan.buy_side || plan.buySide || {};
  const actualHoldings = holdings || state.holdings || [];
  const targetsConfig = bucketTargetsConfig || state.bucketTargetsConfig || null;

  // 1. Calculate current fund valuations & total portfolio net worth
  const currentFundVal = {};
  const fundNameMap = {};
  let totalNetWorth = 0;

  actualHoldings.forEach((h) => {
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
  (sellSide.waterfall || []).forEach((tier) => {
    (tier.lots || []).forEach((lot) => {
      const isin = lot.fundId || lot.fund_id;
      const proceeds = parseFloat(lot.saleProceeds || lot.sale_proceeds) || 0;
      if (isin) {
        fundSellMap[isin] = (fundSellMap[isin] || 0) + proceeds;
      }
    });
  });

  // 3. Calculate buy amounts per fund
  const fundBuyMap = {};
  (buySide.buckets || []).forEach((b) => {
    const bucketAlloc = parseFloat(b.amount_allocated ?? b.amountAllocated) || 0;
    const prefFunds = b.fund_breakdown || b.fundBreakdown || [];
    const fundCount = prefFunds.length > 0 ? prefFunds.length : 1;
    prefFunds.forEach((f) => {
      const isin = f.fund_id || f.fundId;
      const weight = parseFloat(f.allocation_weight || f.allocationWeight) || 1.0 / fundCount;
      const buyAmt = f.amount !== undefined ? parseFloat(f.amount) : bucketAlloc * weight;
      if (isin) {
        fundBuyMap[isin] = (fundBuyMap[isin] || 0) + buyAmt;
        if (f.fund_name || f.fundName) fundNameMap[isin] = f.fund_name || f.fundName;
      }
    });
  });

  // 4. Calculate target fund allocation % from targetsConfig
  const plannedMap = {};
  let activeVersion = null;
  if (targetsConfig?.versions && targetsConfig.versions.length > 0) {
    activeVersion = targetsConfig.versions[targetsConfig.versions.length - 1];
  }
  if (activeVersion?.targets) {
    activeVersion.targets.forEach((t) => {
      const bucketTargetPct = parseFloat(t.target_pct || t.targetPct) || 0;
      const prefFunds = t.preferred_funds || t.preferredFunds || [];
      prefFunds.forEach((pf) => {
        const isin = pf.fund_id || pf.fundId;
        const weight = parseFloat(pf.allocation_weight || pf.allocationWeight) || 0;
        const plannedPct = Math.round(bucketTargetPct * weight * 100) / 100;
        if (isin) plannedMap[isin] = plannedPct;
      });
    });
  }

  // 5. Build combined list of all funds grouped by unique shortName to prevent duplicate badges
  const allIsins = new Set([
    ...Object.keys(currentFundVal),
    ...Object.keys(fundBuyMap),
    ...Object.keys(plannedMap),
  ]);
  const fundMap = {};

  allIsins.forEach((isin) => {
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
        targetPct: 0,
      };
    }
    fundMap[shortName].curVal += curVal;
    fundMap[shortName].sellAmt += sellAmt;
    fundMap[shortName].buyAmt += buyAmt;
    fundMap[shortName].targetPct = Math.max(fundMap[shortName].targetPct, targetPct);
  });

  const totalPostNetWorth = Object.values(fundMap).reduce(
    (sum, f) => sum + Math.max(0, f.curVal - f.sellAmt + f.buyAmt),
    0,
  );

  const fundItems = Object.values(fundMap).map((f) => {
    const postVal = Math.max(0, f.curVal - f.sellAmt + f.buyAmt);
    const curPct = totalNetWorth > 0 ? (f.curVal / totalNetWorth) * 100 : 0;
    const postPct = totalPostNetWorth > 0 ? (postVal / totalPostNetWorth) * 100 : 0;
    return {
      shortName: f.shortName,
      curPct,
      postPct,
      targetPct: f.targetPct,
      isTarget: f.targetPct > 0,
    };
  });

  // Sort: Target funds first (by targetPct desc), then legacy funds (by curPct desc)
  fundItems.sort((a, b) => {
    if (a.isTarget && !b.isTarget) return -1;
    if (!a.isTarget && b.isTarget) return 1;
    if (a.isTarget && b.isTarget) return b.targetPct - a.targetPct;
    return b.curPct - a.curPct;
  });

  container.innerHTML = fundItems
    .map((f) => {
      let deltaColor = "#34d399"; // Green
      if (f.postPct < f.curPct) deltaColor = "#f87171"; // Red for trim
      if (!f.isTarget) deltaColor = "#64748b"; // Muted for legacy 0% target

      const targetBadgeText = f.isTarget ? `Target ${f.targetPct.toFixed(1)}%` : "Legacy (0.0%)";

      return `
      <div style="background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255,255,255,0.08); border-radius: 6px; padding: 6px 12px; font-size: 0.75rem; display: flex; align-items: center; gap: 8px;">
        <span style="font-weight: 700; color: #f8fafc;">${f.shortName}:</span>
        <span style="color: #94a3b8;">${f.curPct.toFixed(1)}%</span>
        <span style="color: #64748b;">➔</span>
        <span style="font-weight: 800; color: ${deltaColor};">${f.postPct.toFixed(1)}%</span>
        <span style="color: #64748b; font-size: 0.7rem;">(${targetBadgeText})</span>
      </div>
    `;
    })
    .join("");
}

function renderRebalanceMicroSankey(sellSide, buySide) {
  const container = document.getElementById("rebalanceSankeyChart");
  if (!container || typeof echarts === "undefined") return;

  container.style.width = "100%";
  container.style.height = "240px";

  let chart = echarts.getInstanceByDom(container);
  if (!chart) {
    chart = echarts.init(container);
  }

  const nodesMap = new Map();
  const links = [];
  const poolNodeName = "Rebalance Cash Pool";
  nodesMap.set(poolNodeName, { name: poolNodeName, itemStyle: { color: "#38bdf8" } });

  // 1. Group Sell Lots by Source Fund & Determine Link Color by Tax Regime
  const sellFundProceeds = new Map();
  const sellFundRegimes = new Map();

  const shortenFundName = (rawName) => {
    if (!rawName) return "";
    return rawName
      .replace(/\s*-\s*Direct Plan Growth\s*\(Non Demat\)/gi, "")
      .replace(/\s*-\s*DIRECT GROWTH PLAN GROWTH OPTION\s*\(Non Demat\)/gi, "")
      .replace(/\s*Direct Plan\s*-\s*Growth/gi, "")
      .replace(/\s*-\s*Direct Plan Growth/gi, "")
      .replace(/\s*Direct Growth Plan Growth Option\s*\(Non Demat\)/gi, "")
      .replace(/\s*-\s*Direct Growth/gi, "")
      .trim();
  };

  (sellSide.waterfall || []).forEach((tier) => {
    (tier.lots || []).forEach((lot) => {
      const rawName = lot.fundName || lot.fund_name || lot.fundId || lot.fund_id;
      const fName = shortenFundName(rawName);
      const proceeds = parseFloat(lot.saleProceeds || lot.sale_proceeds || 0);
      if (proceeds > 0) {
        sellFundProceeds.set(fName, (sellFundProceeds.get(fName) || 0) + proceeds);

        const ti = lot.tax_impact || lot.taxImpact || {};
        const regime =
          ti.regime || ti.regime_type || lot.tax_term || lot.taxTerm || "SEC_112A_EXEMPT";
        const currentRegime = sellFundRegimes.get(fName) || "SEC_112A_EXEMPT";
        if (regime === "SLAB_RATE_STCG" || currentRegime === "SLAB_RATE_STCG") {
          sellFundRegimes.set(fName, "SLAB_RATE_STCG");
        } else if (
          regime === "SEC_112A_TAXABLE_12_5" ||
          currentRegime === "SEC_112A_TAXABLE_12_5"
        ) {
          sellFundRegimes.set(fName, "SEC_112A_TAXABLE_12_5");
        } else {
          sellFundRegimes.set(fName, "SEC_112A_EXEMPT");
        }
      }
    });
  });

  if (sellFundProceeds.size > 0) {
    sellFundProceeds.forEach((amount, fundName) => {
      const regime = sellFundRegimes.get(fundName);
      let linkColor = "#10b981"; // Green for SEC_112A_EXEMPT
      if (regime === "SEC_112A_TAXABLE_12_5") linkColor = "#f59e0b"; // Amber for taxable LTCG
      if (regime === "SLAB_RATE_STCG") linkColor = "#ef4444"; // Red for STCG

      const sellNodeName = `${fundName} (Sell)`;
      nodesMap.set(sellNodeName, { name: sellNodeName, itemStyle: { color: linkColor } });

      links.push({
        source: sellNodeName,
        target: poolNodeName,
        value: amount,
        lineStyle: { color: linkColor, opacity: 0.6 },
      });
    });
  } else {
    const freshCapNode = "Available Cash";
    nodesMap.set(freshCapNode, { name: freshCapNode, itemStyle: { color: "#10b981" } });
    const poolAmt = parseFloat(buySide.totalToInvest || buySide.total_to_invest || 0);
    if (poolAmt > 0) {
      links.push({
        source: freshCapNode,
        target: poolNodeName,
        value: poolAmt,
        lineStyle: { color: "#10b981", opacity: 0.6 },
      });
    }
  }

  // 2. Tax Friction Node
  const taxSum = sellSide.tax_summary || sellSide.taxSummary || {};
  const estTax = parseFloat(taxSum.total_tax_estimate ?? taxSum.totalTaxEstimate ?? 0);
  if (estTax > 0) {
    const taxNodeName = "Estimated Tax";
    nodesMap.set(taxNodeName, { name: taxNodeName, itemStyle: { color: "#ef4444" } });
    links.push({
      source: poolNodeName,
      target: taxNodeName,
      value: estTax,
      lineStyle: { color: "#ef4444", opacity: 0.7 },
    });
  }

  // 3. Buy-Side Target Funds
  (buySide.buckets || []).forEach((b) => {
    const funds = b.fund_breakdown || b.fundBreakdown || [];
    funds.forEach((f) => {
      const rawName = f.fundName || f.fund_name || f.fundId || f.fund_id;
      const fName = shortenFundName(rawName);
      const buyNodeName = `${fName} (Buy)`;
      const amount = parseFloat(f.amount || 0);
      if (amount > 0) {
        nodesMap.set(buyNodeName, { name: buyNodeName, itemStyle: { color: "#38bdf8" } });
        links.push({
          source: poolNodeName,
          target: buyNodeName,
          value: amount,
          lineStyle: { color: "#38bdf8", opacity: 0.6 },
        });
      }
    });
  });

  if (links.length === 0) {
    container.innerHTML =
      '<div style="text-align: center; color: #64748b; padding-top: 80px;">No capital flow required for active drawdown state</div>';
    return;
  }

  const option = {
    backgroundColor: "transparent",
    tooltip: {
      trigger: "item",
      triggerOn: "mousemove",
      formatter: (params) => {
        if (params.dataType === "node") return `<b>${params.name}</b>`;
        return `Flow: <b>${params.data.source}</b> → <b>${params.data.target}</b><br/>Amount: <b>₹${params.data.value.toLocaleString("en-IN")}</b>`;
      },
    },
    series: [
      {
        type: "sankey",
        left: "3%",
        right: "28%",
        top: 15,
        bottom: 15,
        nodeWidth: 14,
        nodeGap: 12,
        emphasis: { focus: "adjacency" },
        data: Array.from(nodesMap.values()),
        links: links,
        lineStyle: { curveness: 0.5 },
        label: { color: "#f8fafc", fontSize: 11, distance: 6 },
      },
    ],
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
  const tbody = document.getElementById("matrixLotTableBody");
  if (!tbody || !plan?.sell_side) return;

  const sellSide = plan.sell_side;
  const buySide = plan.buy_side || {};
  const allLots = [];

  (sellSide.waterfall || []).forEach((tier) => {
    (tier.lots || []).forEach((lot) => {
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
    document.getElementById("matrixLiveProceeds").textContent = "₹0";
    document.getElementById("matrixLiveTaxDrag").textContent = "₹0";
    return;
  }

  const selectedLotIds = new Set(allLots.map((l) => l.lot_id || l.lotId));

  function recalculateMetrics() {
    let liveProceeds = 0;
    let liveTax = 0;

    allLots.forEach((lot) => {
      const id = lot.lot_id || lot.lotId;
      const rowEl = document.getElementById(`matrix-row-${id}`);
      if (selectedLotIds.has(id)) {
        liveProceeds += parseFloat(lot.sale_proceeds || lot.saleProceeds || 0);
        liveTax += parseFloat(lot.tax_impact?.tax_amount || lot.taxImpact?.taxAmount || 0);
        if (rowEl) {
          rowEl.style.opacity = "1";
          rowEl.style.filter = "none";
        }
      } else {
        if (rowEl) {
          rowEl.style.opacity = "0.35";
          rowEl.style.filter = "grayscale(100%)";
        }
      }
    });

    const liveProcEl = document.getElementById("matrixLiveProceeds");
    const liveTaxEl = document.getElementById("matrixLiveTaxDrag");

    if (liveProcEl) liveProcEl.textContent = `₹${Math.round(liveProceeds).toLocaleString("en-IN")}`;
    if (liveTaxEl) liveTaxEl.textContent = `₹${Math.round(liveTax).toLocaleString("en-IN")}`;

    // Reactive buy-side allocation scaling
    renderBuySideAllocationGrid(buySide, liveProceeds);
  }

  tbody.innerHTML = allLots
    .map((lot) => {
      const id = lot.lot_id || lot.lotId;
      const name = lot.fund_name || lot.fundName;
      const acq = lot.acquisition_date || lot.acquisitionDate;
      const days = lot.holding_days || lot.holdingDays;
      const proceeds = parseFloat(lot.sale_proceeds || lot.saleProceeds || 0);
      const cost = parseFloat(lot.cost_basis || lot.costBasis || 0);
      const gain = parseFloat(lot.realized_gain || lot.realizedGain || 0);
      const regime = lot.tax_impact?.regime || lot.taxImpact?.regime || "SEC_112A_EXEMPT";
      const tax = parseFloat(lot.tax_impact?.tax_amount || lot.taxImpact?.taxAmount || 0);

      let regimeBadge = `<span class="cat-badge cat-EQUITY">EXEMPT</span>`;
      if (regime === "SLAB_RATE_STCG") {
        regimeBadge = `<span class="cat-badge cat-DEBT_SPECIFIED_50AA">STCG (20%)</span>`;
      } else if (regime === "SEC_112A_TAXABLE_12_5") {
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
        <td style="color: #cbd5e1;">₹${Math.round(cost).toLocaleString("en-IN")}</td>
        <td style="font-weight: 700; color: #10b981;">₹${Math.round(proceeds).toLocaleString("en-IN")}</td>
        <td style="color: #38bdf8;">+₹${Math.round(gain).toLocaleString("en-IN")}</td>
        <td>${regimeBadge}</td>
        <td style="color: ${tax > 0 ? "#ef4444" : "#34d399"}; font-weight: 700;">₹${Math.round(tax).toLocaleString("en-IN")}</td>
      </tr>
    `;
    })
    .join("");

  // Attach Checkbox Change Listeners
  document.querySelectorAll(".matrix-lot-cb").forEach((cb) => {
    cb.addEventListener("change", (e) => {
      const id = e.target.getAttribute("data-lot-id");
      if (e.target.checked) {
        selectedLotIds.add(id);
      } else {
        selectedLotIds.delete(id);
      }
      recalculateMetrics();
    });
  });

  const selectAllCb = document.getElementById("matrixSelectAllLots");
  if (selectAllCb) {
    selectAllCb.checked = true;
    selectAllCb.onclick = (e) => {
      const isChecked = e.target.checked;
      document.querySelectorAll(".matrix-lot-cb").forEach((cb) => {
        cb.checked = isChecked;
        const id = cb.getAttribute("data-lot-id");
        if (isChecked) selectedLotIds.add(id);
        else selectedLotIds.delete(id);
      });
      recalculateMetrics();
    };
  }

  const btnExecute = document.getElementById("btnExecuteTradeOverride");
  if (btnExecute) {
    btnExecute.onclick = () => {
      alert(
        `⚡ Trade Execution Override Confirmed!\n\nSelected Lots: ${selectedLotIds.size} of ${allLots.length}\nExecuting trade payload back to core-node engine.`,
      );
    };
  }

  recalculateMetrics();
}

if (typeof document !== "undefined") {
  document.addEventListener("DOMContentLoaded", () => {
    const btnViewPlan = document.getElementById("btnViewRebalancePlan");
    const btnLumpsum = document.getElementById("btnSimulateLumpsum");

    if (btnViewPlan) {
      btnViewPlan.addEventListener("click", () => loadUnifiedRebalancePlan("INDUCED"));
    }
    if (btnLumpsum) {
      btnLumpsum.addEventListener("click", () => {
        window.openLumpsumModal?.();
      });
    }

    loadActionRecommendations();
    loadUnifiedRebalancePlan("INDUCED");
  });
}



if (typeof window !== "undefined") {
  window.loadActionRecommendations = loadActionRecommendations;
  window.loadUnifiedRebalancePlan = loadUnifiedRebalancePlan;
  window.onkeydown = (e) => {
    if (e && e.ctrlKey && e.key === "Enter") {
      e.preventDefault();
      const btn = document.getElementById("btnExecuteTacticalOverride");
      if (btn) btn.click();
    }
  };

  window.openLumpsumModal = () => {
    const backdrop = document.getElementById("lumpsumModalBackdrop");
    const modal = document.getElementById("lumpsumModal");
    if (backdrop) backdrop.style.display = "block";
    if (modal) modal.style.display = "block";
  };

  window.closeLumpsumModal = () => {
    const backdrop = document.getElementById("lumpsumModalBackdrop");
    const modal = document.getElementById("lumpsumModal");
    if (backdrop) backdrop.style.display = "none";
    if (modal) modal.style.display = "none";
  };

  window.submitLumpsumSim = () => {
    const input = document.getElementById("lumpsumAmountInput");
    const amt = parseFloat(input ? input.value : "100000") || 100000;
    const selectedOpt = document.querySelector('input[name="lumpsumRebalanceOption"]:checked');
    const includeRebal = selectedOpt ? selectedOpt.value === "true" : false;

    window.closeLumpsumModal();
    loadUnifiedRebalancePlan("MANUAL_LUMPSUM", amt, includeRebal);
  };
}
