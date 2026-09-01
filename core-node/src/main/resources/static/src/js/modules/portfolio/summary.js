import { formatINR } from "../../utils.js?t=1788114000";

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

  const beerCtx = summary.beer_spread_context || summary.beerSpreadContext;
  if (beerCtx) {
    renderBeerValuationCard(beerCtx);
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

  const gsec = beerCtx.gsec_10y_yield_pct ?? beerCtx.gsec10yYieldPct ?? 7.10;
  const pe = beerCtx.nifty50_pe ?? beerCtx.nifty50Pe ?? 22.40;
  const ey = beerCtx.nifty50_earnings_yield_pct ?? beerCtx.nifty50EarningsYieldPct ?? 4.46;
  const spread = beerCtx.beer_spread_pct ?? beerCtx.beerSpreadPct ?? 2.64;
  const zone = beerCtx.valuation_zone ?? beerCtx.valuationZone ?? "EQUITY_EXPENSIVE";
  const asOf = beerCtx.as_of_date || beerCtx.asOfDate || "2026-08-31";

  if (asOfText) asOfText.textContent = `As of: ${asOf}`;
  if (gsecVal) gsecVal.textContent = `${gsec.toFixed(2)}%`;
  if (niftyPeVal) niftyPeVal.textContent = `${pe.toFixed(2)}`;
  if (eyVal) eyVal.textContent = `${ey.toFixed(2)}%`;
  if (spreadVal) spreadVal.textContent = `${spread >= 0 ? "+" : ""}${spread.toFixed(2)}%`;

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

