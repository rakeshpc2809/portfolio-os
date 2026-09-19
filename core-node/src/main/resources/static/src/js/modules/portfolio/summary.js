import { formatINR } from "../../utils.js?t=1788114000";
import { fetchJson } from "../../api.js?t=1788114000";

export function updatePortfolioSummary(summary) {
  const netWorthVal = document.querySelector(".net-worth-val");
  const gainText = document.querySelector(".net-worth-gain");
  const subText = document.querySelector(".net-worth-sub");
  const xirrVal = document.querySelector(".xirr-val");

  const curVal = summary.total_current_value || summary.totalCurrentValue;
  const gainVal = summary.total_unrealized_gain || summary.totalUnrealizedGain;
  const countVal =
    summary.active_holding_count !== undefined
      ? summary.active_holding_count
      : summary.activeHoldingCount;
  const xirr = summary.xirr_percentage || summary.xirrPercentage;

  if (netWorthVal && curVal) {
    netWorthVal.textContent = formatINR(curVal);
    netWorthVal.classList.remove("skeleton");
  }
  if (gainText && gainVal) {
    const gain = Math.round(parseFloat(gainVal) || 0);
    const sign = gain >= 0 ? "+" : "";
    gainText.textContent = `Unrealized gain: ${sign}${formatINR(gain)}`;
    gainText.className = `metric-delta ${gain >= 0 ? "positive" : "negative"}`;
  }
  if (subText && countVal !== undefined) {
    subText.innerHTML = `Active Holdings: <strong>${countVal} Schemes</strong>`;
  }
  if (xirrVal && xirr) {
    xirrVal.textContent = xirr;
    xirrVal.classList.remove("skeleton");
  }

  const staleCount = summary.stale_nav_count ?? summary.staleNavCount ?? 0;
  const freshnessPill = document.getElementById("navFreshnessPill");
  if (freshnessPill) {
    if (staleCount > 0) {
      freshnessPill.innerHTML = `<span class="status-dot" style="background: #F59E0B; box-shadow: 0 0 8px #F59E0B;"></span> <span style="color: #F59E0B;">${staleCount} NAV${staleCount > 1 ? "s" : ""} Stale (>3d)</span>`;
      freshnessPill.title = `${staleCount} mutual fund holding(s) have not received an updated AMFI NAV for >3 business days. Valuations reflect last known closing prices.`;
    } else {
      freshnessPill.innerHTML = `<span class="status-dot" style="background: #10B981; box-shadow: 0 0 8px #10B981;"></span> <span>All NAVs Fresh</span>`;
      freshnessPill.title = `All active holdings have received current AMFI NAV updates within the last 3 business days.`;
    }
  }

  const beerCtx = summary.beer_spread_context || summary.beerSpreadContext;
  renderMarketRiskSentinelCard(beerCtx);

  initQuietModeToggle();
}

export async function renderMarketRiskSentinelCard(beerCtx) {
  if (beerCtx) {
    renderBeerValuationCard(beerCtx);
  }

  try {
    const regimeData = await fetchJson("/macro-regime/buffer-status");
    if (regimeData) {
      updateSentinelRegimeUI(regimeData);
    }
  } catch (e) {
    console.debug("Advisory macro regime fetch skipped:", e);
  }
}

export function updateSentinelRegimeUI(regimeData) {
  if (!regimeData) return;

  const regimeBadge = document.getElementById("macroRegimeBadge");
  const slopeVal = document.getElementById("yieldCurveSlopeVal");
  const runwayVal = document.getElementById("recommendedRunwayVal");
  const rationaleText = document.getElementById("regimeRationaleText");
  const caveatText = document.getElementById("sentinelScopeCaveatText");

  const regimeName = regimeData.regime || "CORRIDOR_NEUTRAL";
  const displayName = regimeData.displayName || regimeName.replace(/_/g, " ");

  if (regimeBadge) {
    regimeBadge.textContent = `REGIME: ${displayName.toUpperCase()}`;
    if (regimeName === "EXPANSION_RISK_OFF") {
      regimeBadge.style.background = "rgba(147, 51, 234, 0.15)";
      regimeBadge.style.color = "#c084fc";
      regimeBadge.style.borderColor = "#a855f7";
    } else if (regimeName === "ACCUMULATION_RISK_ON") {
      regimeBadge.style.background = "rgba(16, 185, 129, 0.15)";
      regimeBadge.style.color = "#10b981";
      regimeBadge.style.borderColor = "#10b981";
    } else {
      regimeBadge.style.background = "rgba(6, 182, 212, 0.15)";
      regimeBadge.style.color = "#06b6d4";
      regimeBadge.style.borderColor = "#06b6d4";
    }
  }

  if (slopeVal && regimeData.yieldCurveSlopePct !== undefined) {
    const slope = regimeData.yieldCurveSlopePct;
    slopeVal.textContent = `${slope >= 0 ? "+" : ""}${slope.toFixed(2)}%`;
    slopeVal.style.color = slope >= 0 ? "#34d399" : "#f87171";
  }

  if (runwayVal && regimeData.recommendedRunwayMonths !== undefined) {
    const bufPct = regimeData.liquidBufferTargetPct || "20";
    runwayVal.textContent = `${regimeData.recommendedRunwayMonths} Mo (${bufPct}% Liquid)`;
  }

  if (rationaleText && regimeData.rationale) {
    rationaleText.textContent = regimeData.rationale;
  }

  if (caveatText && regimeData.scopeCaveat) {
    caveatText.innerHTML = `<span>ℹ️</span> ${regimeData.scopeCaveat}`;
  }
}

export function renderBeerValuationCard(beerCtx) {
  if (!beerCtx) return;
  const zoneBadge = document.getElementById("beerValuationZoneBadge");
  const asOfText = document.getElementById("beerAsOfDateText");
  const gsecVal = document.getElementById("beerGsecYieldVal");
  const niftyPeVal = document.getElementById("beerNiftyPeVal");
  const eyVal = document.getElementById("beerEarningsYieldVal");
  const spreadVal = document.getElementById("beerSpreadVal");

  const gsec = beerCtx.gsec_10y_yield_pct ?? beerCtx.gsec10yYieldPct ?? null;
  const pe = beerCtx.nifty50_pe ?? beerCtx.nifty50Pe ?? null;
  const ey = beerCtx.nifty50_earnings_yield_pct ?? beerCtx.nifty50EarningsYieldPct ?? null;
  const spread = beerCtx.beer_spread_pct ?? beerCtx.beerSpreadPct ?? null;
  const zone = beerCtx.valuation_zone ?? beerCtx.valuationZone ?? "UNKNOWN";
  const asOf = beerCtx.as_of_date || beerCtx.asOfDate || null;
  const isFallback = beerCtx.is_fallback ?? beerCtx.isFallback ?? false;

  const fallbackBadgeHtml = isFallback 
    ? `<span style="font-size: 0.65rem; padding: 1px 5px; border-radius: 3px; background: rgba(245, 158, 11, 0.2); color: #f59e0b; border: 1px solid rgba(245, 158, 11, 0.4); margin-left: 6px;">FALLBACK</span>`
    : "";

  if (asOfText) asOfText.innerHTML = `As of: ${asOf || "--"}${fallbackBadgeHtml}`;
  if (gsecVal) gsecVal.textContent = gsec != null ? `${Number(gsec).toFixed(2)}%` : "--";
  if (niftyPeVal) niftyPeVal.textContent = pe != null ? `${Number(pe).toFixed(2)}` : "--";
  if (eyVal) eyVal.textContent = ey != null ? `${Number(ey).toFixed(2)}%` : "--";
  if (spreadVal) spreadVal.textContent = spread != null ? `${spread >= 0 ? "+" : ""}${Number(spread).toFixed(2)}%` : "--";

  if (zoneBadge) {
    zoneBadge.textContent = zone.replace(/_/g, " ");
    if (zone === "EQUITY_EXPENSIVE") {
      zoneBadge.style.background = "rgba(245, 158, 11, 0.15)";
      zoneBadge.style.color = "#f59e0b";
      zoneBadge.style.borderColor = "#f59e0b";
    } else if (zone === "EQUITY_ATTRACTIVE") {
      zoneBadge.style.background = "rgba(16, 185, 129, 0.15)";
      zoneBadge.style.color = "#10b981";
      zoneBadge.style.borderColor = "#10b981";
    } else {
      zoneBadge.style.background = "rgba(56, 189, 248, 0.15)";
      zoneBadge.style.color = "#38bdf8";
      zoneBadge.style.borderColor = "#38bdf8";
    }
  }
}

export function initQuietModeToggle() {
  const toggleBtn = document.getElementById("quietModeToggle");
  if (!toggleBtn) return;

  function updateQuietModeUI() {
    const isQuiet = localStorage.getItem("portfolio_os_quiet_mode") === "true";
    const dot = document.getElementById("quietModeDot");
    const label = document.getElementById("quietModeLabel");
    if (isQuiet) {
      toggleBtn.style.background = "rgba(0, 240, 255, 0.15)";
      toggleBtn.style.borderColor = "#00F0FF";
      toggleBtn.style.color = "#00F0FF";
      if (dot) {
        dot.style.background = "#00F0FF";
        dot.style.boxShadow = "0 0 8px #00F0FF";
      }
      if (label) label.textContent = "Quiet Mode: ON";
      document.body.classList.add("quiet-mode-active");
    } else {
      toggleBtn.style.background = "rgba(255, 255, 255, 0.05)";
      toggleBtn.style.borderColor = "rgba(255, 255, 255, 0.15)";
      toggleBtn.style.color = "#94A3B8";
      if (dot) {
        dot.style.background = "#64748B";
        dot.style.boxShadow = "none";
      }
      if (label) label.textContent = "Quiet Mode: OFF";
      document.body.classList.remove("quiet-mode-active");
    }
  }

  toggleBtn.onclick = () => {
    const current = localStorage.getItem("portfolio_os_quiet_mode") === "true";
    localStorage.setItem("portfolio_os_quiet_mode", (!current).toString());
    updateQuietModeUI();
  };

  updateQuietModeUI();
}


