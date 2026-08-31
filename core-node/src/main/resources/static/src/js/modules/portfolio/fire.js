import { API_BASE, fetchJson } from "../../api.js?t=1788114000";
import { state } from "../../state.js?t=1788114000";
import { formatINR } from "../../utils.js?t=1788114000";

export async function fetchGoalSummary() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/goals`).catch(() => null);
    if (data) {
      renderGoalSummary(data);
    }
  } catch (e) {
    console.error("Goal summary error:", e);
  }
}

export function renderGoalSummary(data) {
  const idleVal = document.querySelector(".idle-cash-val");
  const unallocCash = data.unallocated_cash || data.unallocatedCash;
  if (idleVal && unallocCash) {
    idleVal.textContent = formatINR(unallocCash);
    idleVal.classList.remove("skeleton");
  }
}

export async function fetchFireSummary() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/fire`).catch(() => null);
    if (data) {
      renderFireSummary(data);
    }
  } catch (e) {
    console.error("FIRE summary error:", e);
  }
}
window.fetchFireSummary = fetchFireSummary;

export function renderFireSummary(data) {
  if (!data) return;
  const statusPill = document.getElementById("fireStatusPill");
  const scenarioLabel = document.getElementById("fireScenarioLabel");
  const investableNw = document.getElementById("fireInvestableNw");
  const reqCorpus = document.getElementById("fireRequiredCorpus");
  const projCorpus = document.getElementById("fireProjectedCorpus");

  const status = data.status;
  const shortage = data.shortage_or_surplus_amount || data.shortageOrSurplusAmount;
  const activeLabel = data.active_scenario_label || data.activeScenarioLabel;
  const fireInvestable = data.fire_investable_net_worth || data.fireInvestableNetWorth;
  const requiredCorpus = data.required_corpus || data.requiredCorpus;
  const projectedCorpus = data.projected_corpus_at_target_age || data.projectedCorpusAtTargetAge;

  if (statusPill) {
    statusPill.textContent = status === "ON_TRACK" ? "ON TRACK" : `SHORT BY ${formatINR(shortage)}`;
    statusPill.className = `fire-status-pill ${status === "ON_TRACK" ? "on-track" : "short"}`;
  }

  if (scenarioLabel) scenarioLabel.textContent = `Scenario: ${activeLabel}`;

  if (investableNw) investableNw.textContent = formatINR(fireInvestable);
  if (reqCorpus)
    reqCorpus.textContent = `₹ ${(parseFloat(requiredCorpus) / 10000000).toFixed(2)} Cr`;
  if (projCorpus)
    projCorpus.textContent = `₹ ${(parseFloat(projectedCorpus) / 10000000).toFixed(2)} Cr`;

  const mcSuccess =
    data.monte_carlo_success_rate_pct !== undefined
      ? data.monte_carlo_success_rate_pct
      : data.monteCarloSuccessRatePct;
  const mcP10 = data.monte_carlo_tenth_percentile_corpus || data.monteCarloTenthPercentileCorpus;
  const dsLabel =
    data.monte_carlo_data_source_label ||
    data.monteCarloDataSourceLabel ||
    "Nifty 50 Historical Return Model (Cold Start)";
  const isSynthetic =
    (data.monte_carlo_data_source || data.monteCarloDataSource) === "SYNTHETIC_MARKET_BENCHMARK";

  const mcCard = document.getElementById("fireMonteCarloCard");
  if (mcCard && mcSuccess !== undefined) {
    const p10Cr = mcP10 ? (parseFloat(mcP10) / 10000000).toFixed(2) : "0.00";
    mcCard.innerHTML = `
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
        <div style="font-size: 11px; font-weight: 600; color: #a855f7; text-transform: uppercase; letter-spacing: 0.05em;">10,000-Path Monte Carlo SORR Simulation</div>
        <span style="font-size: 10px; padding: 2px 8px; border-radius: 12px; background: ${isSynthetic ? "rgba(245, 158, 11, 0.15)" : "rgba(16, 185, 129, 0.15)"}; color: ${isSynthetic ? "#f59e0b" : "#10b981"}; font-weight: 500;">
          ${dsLabel}
        </span>
      </div>
      <div style="display: flex; gap: 16px; align-items: center;">
        <div>
          <span style="font-size: 18px; font-weight: 700; color: #d0ff00;">${mcSuccess}%</span>
          <span style="font-size: 11px; color: #94a3b8;"> Success Rate</span>
        </div>
        <div style="border-left: 1px solid rgba(255,255,255,0.1); padding-left: 16px;">
          <span style="font-size: 14px; font-weight: 600; color: #f59e0b;">₹ ${p10Cr} Cr</span>
          <span style="font-size: 11px; color: #94a3b8;"> (10th Percentile Floor)</span>
        </div>
      </div>
    `;
  }

  const successBadge = document.getElementById("fireSuccessRateBadge");
  const dsLabelEl = document.getElementById("fireDataSourceLabel");
  const simulatedMedianEl = document.getElementById("fireSimulatedMedian");
  if (successBadge && mcSuccess !== undefined) {
    successBadge.textContent = `Monte Carlo Success: ${mcSuccess}%`;
  }
  if (dsLabelEl && dsLabel) {
    dsLabelEl.textContent = dsLabel;
  }
  if (simulatedMedianEl && (data.projected_corpus || data.projectedCorpus)) {
    const projCorpus = data.projected_corpus || data.projectedCorpus;
    simulatedMedianEl.textContent = `₹ ${(parseFloat(projCorpus) / 10000000).toFixed(2)} Cr`;
  }

  const trajectories = data.fan_chart_trajectories || data.fanChartTrajectories;
  if (trajectories && trajectories.length > 0) {
    renderFireFanChart(trajectories, requiredCorpus);
  }

  initFireSensitivitySliders();
}
window.renderFireSummary = renderFireSummary;
window.renderFireFanChart = renderFireFanChart;

let fireDebounceTimer = null;

export function initFireSensitivitySliders() {
  const sipSlider = document.getElementById("fireSipSlider");
  const expSlider = document.getElementById("fireExpSlider");
  const yrsSlider = document.getElementById("fireYrsSlider");

  if (!sipSlider || sipSlider.dataset.initialized) return;
  sipSlider.dataset.initialized = "true";

  const updateSim = () => {
    const sip = parseFloat(sipSlider.value);
    const expMonthly = parseFloat(expSlider.value);
    const yrs = parseInt(yrsSlider.value, 10);

    const sipValEl = document.getElementById("sipSliderVal");
    const expValEl = document.getElementById("expSliderVal");
    const yrsValEl = document.getElementById("yrsSliderVal");

    if (sipValEl) sipValEl.textContent = formatINR(sip);
    if (expValEl) expValEl.textContent = formatINR(expMonthly);
    if (yrsValEl) yrsValEl.textContent = `${yrs} Years`;

    clearTimeout(fireDebounceTimer);
    fireDebounceTimer = setTimeout(async () => {
      try {
        const res = await fetchJson(`${API_BASE}/analytics/fire/simulate`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            monthly_sip: sip,
            annual_expense: expMonthly * 12.0,
            years_remaining: yrs,
          }),
        });

        if (res?.fan_chart_trajectories) {
          const successBadge = document.getElementById("fireSuccessRateBadge");
          const simulatedMedianEl = document.getElementById("fireSimulatedMedian");

          if (successBadge && res.success_rate_pct !== undefined) {
            successBadge.textContent = `Monte Carlo Success: ${res.success_rate_pct}%`;
          }
          if (simulatedMedianEl && res.median_ending_corpus) {
            simulatedMedianEl.textContent = `₹ ${(parseFloat(res.median_ending_corpus) / 10000000).toFixed(2)} Cr`;
          }

          renderFireFanChart(res.fan_chart_trajectories, res.required_corpus);
        }
      } catch (err) {
        console.error("Failed to update FIRE sensitivity simulation:", err);
      }
    }, 300);
  };

  sipSlider.addEventListener("input", updateSim);
  expSlider.addEventListener("input", updateSim);
  yrsSlider.addEventListener("input", updateSim);
}

export function renderFireFanChart(trajectories, requiredCorpus) {
  const container = document.getElementById("fanChartSvgContainer");
  if (!container || !trajectories || trajectories.length === 0) return;

  let width = container.clientWidth;
  if (!width || width <= 0) {
    width = container.parentElement ? container.parentElement.clientWidth : 540;
  }
  if (!width || width <= 0) width = 540;

  const height = 280;
  const padding = { top: 20, right: 25, bottom: 35, left: 55 };

  const plotW = width - padding.left - padding.right;
  const plotH = height - padding.top - padding.bottom;

  let maxY = Math.max(...trajectories.map((t) => t.p90));
  if (requiredCorpus && requiredCorpus > maxY) {
    maxY = requiredCorpus * 1.1;
  }
  if (maxY <= 0) maxY = 10000000;

  const totalYears = trajectories.length - 1;

  const getX = (year) => padding.left + (year / totalYears) * plotW;
  const getY = (val) => padding.top + plotH - (Math.max(0, val) / maxY) * plotH;

  // Outer band p10-p90
  let p10_p90_points = "";
  for (let i = 0; i < trajectories.length; i++) {
    const t = trajectories[i];
    p10_p90_points += `${getX(t.year)},${getY(t.p90)} `;
  }
  for (let i = trajectories.length - 1; i >= 0; i--) {
    const t = trajectories[i];
    p10_p90_points += `${getX(t.year)},${getY(t.p10)} `;
  }

  // Inner band p25-p75
  let p25_p75_points = "";
  for (let i = 0; i < trajectories.length; i++) {
    const t = trajectories[i];
    p25_p75_points += `${getX(t.year)},${getY(t.p75)} `;
  }
  for (let i = trajectories.length - 1; i >= 0; i--) {
    const t = trajectories[i];
    p25_p75_points += `${getX(t.year)},${getY(t.p25)} `;
  }

  // Median line p50
  let p50_path = "";
  for (let i = 0; i < trajectories.length; i++) {
    const t = trajectories[i];
    const prefix = i === 0 ? "M" : "L";
    p50_path += `${prefix} ${getX(t.year)} ${getY(t.p50)} `;
  }

  const reqCorpusY = requiredCorpus ? getY(requiredCorpus) : null;

  // Y-axis ticks (4 ticks)
  let yTicksHtml = "";
  for (let i = 0; i <= 4; i++) {
    const val = (maxY / 4) * i;
    const yPos = getY(val);
    const crVal = (val / 10000000).toFixed(1);
    yTicksHtml += `
      <line x1="${padding.left}" y1="${yPos}" x2="${width - padding.right}" y2="${yPos}" stroke="rgba(255,255,255,0.06)" stroke-dasharray="2,2"/>
      <text x="${padding.left - 8}" y="${yPos + 4}" fill="#64748b" font-size="10" font-family="monospace" text-anchor="end">₹${crVal}Cr</text>
    `;
  }

  // X-axis ticks (Year 0, 10, 20, 30, 43)
  let xTicksHtml = "";
  const xYears = [0, 10, 20, 30, totalYears];
  xYears.forEach((y) => {
    const xPos = getX(y);
    xTicksHtml += `
      <line x1="${xPos}" y1="${padding.top + plotH}" x2="${xPos}" y2="${padding.top + plotH + 4}" stroke="rgba(255,255,255,0.2)"/>
      <text x="${xPos}" y="${padding.top + plotH + 18}" fill="#94a3b8" font-size="10" font-family="monospace" text-anchor="middle">Yr ${y}</text>
    `;
  });

  const svgHtml = `
    <svg width="100%" height="${height}" viewBox="0 0 ${width} ${height}" style="overflow: visible;">
      <defs>
        <linearGradient id="fanOuterGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.18"/>
          <stop offset="100%" stop-color="#0284c7" stop-opacity="0.04"/>
        </linearGradient>
        <linearGradient id="fanInnerGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.35"/>
          <stop offset="100%" stop-color="#0284c7" stop-opacity="0.12"/>
        </linearGradient>
      </defs>

      ${yTicksHtml}
      ${xTicksHtml}

      <!-- Outer 10th-90th percentile band -->
      <polygon points="${p10_p90_points}" fill="url(#fanOuterGrad)" stroke="rgba(56, 189, 248, 0.2)" stroke-width="1"/>

      <!-- Inner 25th-75th percentile band -->
      <polygon points="${p25_p75_points}" fill="url(#fanInnerGrad)" stroke="rgba(56, 189, 248, 0.4)" stroke-width="1"/>

      <!-- 50th percentile Median Line -->
      <path d="${p50_path}" fill="none" stroke="#38bdf8" stroke-width="2.5"/>

      <!-- Retirement Date Vertical Line (Year 13) -->
      ${
        totalYears >= 13
          ? `
        <line x1="${getX(13)}" y1="${padding.top}" x2="${getX(13)}" y2="${padding.top + plotH}" stroke="#38bdf8" stroke-width="1" stroke-dasharray="3,3" opacity="0.6"/>
        <text x="${getX(13)}" y="${padding.top - 6}" fill="#38bdf8" font-size="9" font-family="monospace" text-anchor="middle" font-weight="bold">Retire (Yr 13)</text>
      `
          : ""
      }

      <!-- Target Required Corpus Horizontal Line -->
      ${
        reqCorpusY
          ? `
        <line x1="${padding.left}" y1="${reqCorpusY}" x2="${width - padding.right}" y2="${reqCorpusY}" stroke="#ef4444" stroke-width="1.8" stroke-dasharray="4,4"/>
        <text x="${width - padding.right - 4}" y="${reqCorpusY - 6}" fill="#ef4444" font-size="10" font-family="monospace" text-anchor="end" font-weight="bold">Target Corpus</text>
      `
          : ""
      }

      <!-- Ruin Risk Threshold Annotation (First year where 10% of paths deplete) -->
      ${(() => {
        const ruinPoint = trajectories.find((t) => t.p10 === 0.0 && t.year > 0);
        if (!ruinPoint) return "";
        const rx = getX(ruinPoint.year);
        return `
          <line x1="${rx}" y1="${padding.top + plotH - 35}" x2="${rx}" y2="${padding.top + plotH}" stroke="#ef4444" stroke-width="1.2" stroke-dasharray="2,2"/>
          <rect x="${rx - 55}" y="${padding.top + plotH - 32}" width="110" height="18" rx="4" fill="rgba(239, 68, 68, 0.18)" stroke="rgba(239, 68, 68, 0.5)" stroke-width="0.8"/>
          <text x="${rx}" y="${padding.top + plotH - 20}" fill="#fca5a5" font-size="9" font-family="monospace" text-anchor="middle" font-weight="bold">⚠️ 10% Ruin @ Yr ${ruinPoint.year}</text>
        `;
      })()}
    </svg>
  `;

  container.innerHTML = svgHtml;
}

