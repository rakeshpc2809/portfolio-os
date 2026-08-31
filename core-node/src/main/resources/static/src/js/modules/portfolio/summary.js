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
}

