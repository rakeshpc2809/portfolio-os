import { API_BASE, fetchJson } from "../../api.js?t=1788114000";
import { FUND_REGISTRY } from "../../constants.js?t=1788114000";
import { setBadgeStyle, setErrorState, setHtml, setText } from "../../domUtils.js?t=1788114000";
import { state } from "../../state.js?t=1788114000";
import { formatINR } from "../../utils.js?t=1788114000";

export async function loadBenchmarkAnalytics() {
  try {
    const res = await fetchJson(`${API_BASE}/analytics/benchmark?benchmark=NIFTY_50_TRI`);
    if (res && res.status === "OK") {
      const star = res.is_provisional ? "*" : "";
      setText("#benchmarkAlphaVal", `${res.alpha_pct > 0 ? "+" : ""}${res.alpha_pct}%${star}`);
      setText("#benchmarkBetaVal", `${res.beta}${star}`);
      setText("#benchmarkSharpeVal", `${res.sharpe_ratio}${star}`);
      setText("#benchmarkTrackingVal", `${res.tracking_error_pct}%${star}`);
      setText(
        "#benchmarkOutperformVal",
        `${res.outperformance_pct > 0 ? "+" : ""}${res.outperformance_pct}%${star}`,
      );

      const cardGrid = document.querySelector("#benchmarkMetricsGrid");
      if (cardGrid) {
        cardGrid.style.opacity = res.is_provisional ? "0.82" : "1.0";
      }

      if (res.is_provisional) {
        setBadgeStyle(
          "#benchmarkSampleBadge",
          `PROVISIONAL (${res.sample_days} DAYS)`,
          "live-tag warning-tag",
        );
      } else {
        setBadgeStyle(
          "#benchmarkSampleBadge",
          `MATURE (${res.sample_days} DAYS)`,
          "live-tag positive-tag",
        );
      }

      if (res.data_source_label) {
        setText("#benchmarkProvenanceSub", res.data_source_label);
      }
    }
  } catch (err) {
    console.error("Failed to load benchmark analytics:", err);
    setErrorState("#benchmarkAlphaVal", "—");
    setErrorState("#benchmarkBetaVal", "—");
    setErrorState("#benchmarkSharpeVal", "—");
    setErrorState("#benchmarkTrackingVal", "—");
    setErrorState("#benchmarkOutperformVal", "—");
    setBadgeStyle("#benchmarkSampleBadge", "OFFLINE", "live-tag warning-tag");
  }
}

export async function populateFundDropdowns() {
  const selA = document.getElementById("vennFundA");
  const selB = document.getElementById("vennFundB");
  if (!selA || !selB) return;

  try {
    const res = await fetchJson(`${API_BASE}/funds/registry`);
    if (res && res.status === "OK" && res.funds) {
      // Clear static FUND_REGISTRY and populate from live ingested tax_events response
      Object.keys(FUND_REGISTRY).forEach((key) => {
        delete FUND_REGISTRY[key];
      });
      res.funds.forEach((f) => {
        if (f.isin && f.name) {
          FUND_REGISTRY[f.isin] = f.name;
        }
      });
    }
  } catch (err) {
    console.warn("Failed to load live fund registry from backend, using fallback:", err);
  }

  const currentA = selA.value || "INF879O01027";
  const currentB = selB.value || "INF109KC13X2";

  let optionsHtml = "";
  Object.keys(FUND_REGISTRY).forEach((key) => {
    optionsHtml += `<option value="${key}">${FUND_REGISTRY[key]}</option>`;
  });

  selA.innerHTML = optionsHtml;
  selB.innerHTML = optionsHtml;

  selA.value = currentA;
  selB.value = currentB;
}

export function openOverlapModal() {
  const modal = document.getElementById("overlapInspectorModal");
  const backdrop = document.getElementById("overlapModalBackdrop");
  if (modal) modal.style.display = "block";
  if (backdrop) backdrop.style.display = "block";
  populateFundDropdowns().then(() => {
    loadOverlapAnalytics();
  });
}

export function closeOverlapModal() {
  const modal = document.getElementById("overlapInspectorModal");
  const backdrop = document.getElementById("overlapModalBackdrop");
  if (modal) modal.style.display = "none";
  if (backdrop) backdrop.style.display = "none";
}

let activeOverlapRequestId = 0;

export async function loadOverlapAnalytics(fundAOverride = null, fundBOverride = null) {
  const currentRequestId = ++activeOverlapRequestId;

  const selA = document.getElementById("vennFundA");
  const selB = document.getElementById("vennFundB");
  const chkIncludeUnverified = document.getElementById("chkIncludeUnverified");
  const includeUnverified = chkIncludeUnverified ? chkIncludeUnverified.checked : false;

  const fundAKey = fundAOverride || (selA ? selA.value : "INF879O01027");
  const fundBKey = fundBOverride || (selB ? selB.value : "INF109KC13X2");

  const nameA = FUND_REGISTRY[fundAKey] || fundAKey;
  const nameB = FUND_REGISTRY[fundBKey] || fundBKey;

  const tableBody = document.querySelector("#topStockConcentrationTable tbody");
  const container = document.getElementById("vennContainer");

  setText("#overlapPairName", `${nameA} vs ${nameB}`);

  // Same Fund Selected Case (Strict raw ISIN string comparison)
  if (fundAKey === fundBKey) {
    setText("#pairwiseOverlapVal", "100.00%");
    setText("#commonStockCountSub", "Identical Fund Selected (100% Stock Overlap)");
    setBadgeStyle("#overlapProvenanceBadge", "SAME FUND (100%)", "live-tag positive-tag");
    const warn = document.getElementById("unverifiedPairWarning");
    if (warn) warn.style.display = "none";
    renderVennSvg(container, nameA, nameB, 100.0);
  } else {
    setText("#pairwiseOverlapVal", "...");
    setText("#commonStockCountSub", "Calculating live stock overlap...");
  }

  try {
    const res = await fetchJson(
      `${API_BASE}/analytics/overlap?fundA=${encodeURIComponent(fundAKey)}&fundB=${encodeURIComponent(fundBKey)}&includeUnverified=${includeUnverified}`,
    );
    if (currentRequestId !== activeOverlapRequestId) return; // Stale fetch race guard

    if (res && res.status === "OK") {
      const pairwise = res.pairwise_overlap;
      const concentrations = res.portfolio_top_stock_concentrations;
      const telemetry = res.coverage_telemetry;

      // 1. Render Pairwise Overlap & Provenance
      if (fundAKey !== fundBKey && pairwise) {
        const isUnverified = pairwise.is_unverified_estimate;
        const warn = document.getElementById("unverifiedPairWarning");
        if (warn) {
          warn.style.display = isUnverified ? "block" : "none";
        }

        if (isUnverified) {
          setBadgeStyle("#overlapProvenanceBadge", "PROVISIONAL SAMPLE", "live-tag warning-tag");
        } else if (pairwise.source_type_a === "FACTSHEET_POI_PARSED" || pairwise.source_type_b === "FACTSHEET_POI_PARSED") {
          setBadgeStyle("#overlapProvenanceBadge", "FACTSHEET AUDITED", "live-tag positive-tag");
        } else {
          setBadgeStyle("#overlapProvenanceBadge", "NSE BENCHMARK", "live-tag positive-tag");
        }

        // Source tags under selectors
        const tagA = document.getElementById("fundASourceTag");
        const tagB = document.getElementById("fundBSourceTag");
        if (tagA) tagA.innerHTML = formatSourceTag(pairwise.source_type_a);
        if (tagB) tagB.innerHTML = formatSourceTag(pairwise.source_type_b);

        if (pairwise.common_stock_count === 0) {
          setText("#pairwiseOverlapVal", "0.00%");
          setText("#commonStockCountSub", "Common Holdings: 0 Stocks (No Shared Holdings)");
          renderVennSvg(container, nameA, nameB, 0.0);
        } else {
          setText("#pairwiseOverlapVal", `${pairwise.overlap_percentage}%`);
          const topSymbols = (pairwise.common_stocks || [])
            .slice(0, 4)
            .map((s) => s.stock_symbol)
            .join(", ");
          const extraStr = topSymbols ? ` (${topSymbols})` : "";
          setText(
            "#commonStockCountSub",
            `Common Holdings: ${pairwise.common_stock_count} Stocks${extraStr}`,
          );
          renderVennSvg(container, nameA, nameB, pairwise.overlap_percentage);
        }
      }

      // 2. Render Coverage Telemetry
      if (telemetry) {
        setText("#coveragePctVal", `${telemetry.audited_coverage_pct}%`);
        setText("#coverageRupeeVal", `₹${formatINR(telemetry.audited_aum)} / ₹${formatINR(telemetry.total_equity_aum)}`);
        setBadgeStyle(
          "#coverageModeTag",
          includeUnverified ? "ALL FUNDS (PROVISIONAL)" : "AUDITED ONLY",
          includeUnverified ? "live-tag warning-tag" : "live-tag positive-tag"
        );
        const concWarn = document.getElementById("unverifiedConcentrationWarning");
        if (concWarn) {
          concWarn.style.display = includeUnverified ? "block" : "none";
        }
      }

      // 3. Render Concentrations Table
      if (tableBody && concentrations) {
        if (concentrations.length === 0) {
          setHtml(
            tableBody,
            `<tr><td colspan="4" style="text-align:center; color:#64748b; padding:12px;">No stock concentrations calculated.</td></tr>`,
          );
        } else {
          let html = "";
          concentrations.forEach((item) => {
            const provBadge = item.is_audited 
              ? `<span class="live-tag positive-tag" style="font-size:0.68rem; padding:2px 6px;">AUDITED</span>` 
              : `<span class="live-tag warning-tag" style="font-size:0.68rem; padding:2px 6px;">PROVISIONAL</span>`;
            html += `<tr>
              <td><strong>${item.stock_symbol}</strong></td>
              <td style="text-align: right;">${formatINR(item.rupee_exposure)}</td>
              <td style="text-align: right;"><span class="metric-delta positive">${item.portfolio_percentage}%</span></td>
              <td style="text-align: center;">${provBadge}</td>
            </tr>`;
          });
          setHtml(tableBody, html);
        }
      }

      await loadUpSetAnalytics();
      await loadActionRecommendations();
    } else {
      throw new Error(res ? res.message : "Invalid API response");
    }
  } catch (err) {
    if (currentRequestId !== activeOverlapRequestId) return;
    console.error("Failed to load overlap analytics:", err);
    setErrorState("#pairwiseOverlapVal", "—");
    setText("#commonStockCountSub", "⚠️ Overlap Fetch Failed (Check Backend Service)");
    setBadgeStyle("#overlapProvenanceBadge", "OFFLINE", "live-tag warning-tag");
    if (container) {
      setHtml(
        container,
        `<div style="text-align: center; color: #f87171; padding: 12px;">⚠️ Failed to load overlap graph from backend.</div>`,
      );
    }
  }
}

function formatSourceTag(src) {
  if (src === "FACTSHEET_POI_PARSED") {
    return `<span style="color: #10b981; font-weight: 600;">● Full Factsheet Audited</span>`;
  }
  if (src === "MANUAL_ESTIMATE_UNVERIFIED") {
    return `<span style="color: #fbbf24; font-weight: 600;">▲ Provisional Sample (~30-60%)</span>`;
  }
  return `<span style="color: #38bdf8; font-weight: 600;">● Official Index Benchmark</span>`;
}

function renderVennSvg(container, nameA, nameB, overlapPct) {
  if (!container) return;
  const numOverlap = typeof overlapPct === "number" ? overlapPct : parseFloat(overlapPct) || 0;

  const svg = `
    <svg viewBox="0 0 500 180" style="max-width: 460px; height: auto;">
      <defs>
        <linearGradient id="circleGradA" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.3"/>
          <stop offset="100%" stop-color="#0284c7" stop-opacity="0.15"/>
        </linearGradient>
        <linearGradient id="circleGradB" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#a855f7" stop-opacity="0.3"/>
          <stop offset="100%" stop-color="#7e22ce" stop-opacity="0.15"/>
        </linearGradient>
      </defs>
      <!-- Circle A -->
      <circle cx="190" cy="90" r="70" fill="url(#circleGradA)" stroke="#38bdf8" stroke-width="2" />
      <!-- Circle B -->
      <circle cx="310" cy="90" r="70" fill="url(#circleGradB)" stroke="#a855f7" stroke-width="2" />
      
      <!-- Labels -->
      <text x="140" y="85" fill="#f8fafc" font-size="12" font-weight="700" text-anchor="middle">${nameA}</text>
      <text x="140" y="105" fill="#94a3b8" font-size="10" text-anchor="middle">Exclusive Sleeve</text>
      
      <text x="360" y="85" fill="#f8fafc" font-size="12" font-weight="700" text-anchor="middle">${nameB}</text>
      <text x="360" y="105" fill="#94a3b8" font-size="10" text-anchor="middle">Exclusive Sleeve</text>

      <!-- Intersection -->
      <text x="250" y="85" fill="#d0ff00" font-size="14" font-weight="800" text-anchor="middle">${numOverlap.toFixed(2)}%</text>
      <text x="250" y="105" fill="#e2e8f0" font-size="9" font-weight="600" text-anchor="middle">Shared Overlap</text>
    </svg>
  `;

  container.innerHTML = svg;
}

export async function loadUpSetAnalytics() {
  const tableBody = document.querySelector("#upsetMatrixTable tbody");
  const container = document.querySelector("#upsetContainer");
  if (!tableBody && !container) return;

  try {
    const res = await fetchJson(`${API_BASE}/analytics/overlap/upset`);
    if (res && res.status === "OK" && res.upset_combinations) {
      const combos = res.upset_combinations;
      const allFundKeys = Object.keys(FUND_REGISTRY);

      if (combos.length === 0) {
        if (tableBody) {
          tableBody.innerHTML = `<tr><td colspan="3" style="text-align:center; color:#64748b;">No multi-set intersections found.</td></tr>`;
        }
        if (container) {
          container.innerHTML = `<div style="text-align:center; color:#64748b;">No multi-set intersections found.</div>`;
        }
        return;
      }

      if (tableBody) {
        let rowsHtml = "";
        combos.forEach((c) => {
          const participatingNames = (c.participating_funds || [])
            .map((k) => FUND_REGISTRY[k] || k)
            .map((name) => `<span class="badge" style="background: rgba(56, 189, 248, 0.12); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.25); padding: 2px 6px; border-radius: 4px; font-size: 0.72rem; margin-right: 4px; display: inline-block; margin-bottom: 2px; font-weight: 500;">${name}</span>`)
            .join(" ");

          const topStocks = (c.stocks || [])
            .slice(0, 4)
            .map((s) => `<span style="color: #e2e8f0; font-weight: 500;">${s.stock_symbol}</span> <span style="color: #94a3b8; font-size: 0.70rem;">(${s.min_weight}%)</span>`)
            .join(", ");

          const stockSuffix = (c.stocks && c.stocks.length > 4) ? ` <span style="color: #64748b; font-size: 0.70rem;">+${c.stocks.length - 4} more</span>` : "";

          rowsHtml += `
            <tr>
              <td style="font-weight: 700; color: #38bdf8; text-align: center; width: 18%;">
                <span style="font-size: 0.92rem;">${c.stock_count}</span> <span style="color: #94a3b8; font-size: 0.72rem;">stocks</span>
                <div style="font-size: 0.68rem; color: #64748b;">${c.total_overlap_weight}% overlap</div>
              </td>
              <td style="width: 47%;">${participatingNames}</td>
              <td style="width: 35%; font-family: monospace; font-size: 0.75rem;">${topStocks}${stockSuffix}</td>
            </tr>
          `;
        });
        tableBody.innerHTML = rowsHtml;
      }

      if (container) {
        const maxCount = Math.max(...combos.map((c) => c.stock_count));
        let html = `<div style="display: flex; gap: 20px; font-family: monospace; font-size: 0.78rem;">`;
        html += `<div style="display: flex; flex-direction: column; justify-content: flex-end; gap: 8px; font-weight: 600; color: #94a3b8; padding-bottom: 22px;">`;
        allFundKeys.forEach((key) => {
          html += `<div style="height: 18px; line-height: 18px; text-align: right; white-space: nowrap;">${FUND_REGISTRY[key]}</div>`;
        });
        html += `</div>`;
        html += `<div style="display: flex; gap: 14px; overflow-x: auto; padding-bottom: 6px;">`;
        combos.forEach((c) => {
          const participating = c.participating_funds;
          const participatingNames = participating.map((k) => FUND_REGISTRY[k] || k);
          const stockList = c.stocks.map((s) => s.stock_symbol).join(", ");
          const barPct = Math.round((c.stock_count / maxCount) * 100);
          html += `<div style="display: flex; flex-direction: column; items: center; min-width: 55px;" title="Intersection Set: [${participatingNames.join(" + ")}]\nShared Stocks (${c.stock_count}): ${stockList}\nWeighted Overlap: ${c.total_overlap_weight}%">`;
          html += `<div style="height: 60px; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; margin-bottom: 8px; width: 100%;">`;
          html += `<span style="font-size: 0.72rem; color: #38bdf8; font-weight: bold; margin-bottom: 2px;">${c.stock_count}</span>`;
          html += `<div style="width: 14px; height: ${Math.max(barPct * 0.45, 4)}px; background: linear-gradient(180deg, #38bdf8, #0284c7); border-radius: 3px;"></div>`;
          html += `</div>`;
          html += `<div style="display: flex; flex-direction: column; gap: 8px; align-items: center;">`;
          allFundKeys.forEach((fKey) => {
            const isActive = participating.includes(fKey);
            if (isActive) {
              html += `<div style="width: 18px; height: 18px; border-radius: 50%; background: #38bdf8; box-shadow: 0 0 6px rgba(56, 189, 248, 0.6);"></div>`;
            } else {
              html += `<div style="width: 18px; height: 18px; border-radius: 50%; background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12);"></div>`;
            }
          });
          html += `</div>`;
          html += `<div style="font-size: 0.65rem; color: #64748b; margin-top: 6px; text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 60px;">${c.total_overlap_weight}%</div>`;
          html += `</div>`;
        });
        html += `</div></div>`;
        container.innerHTML = html;
      }
    }
  } catch (err) {
    console.error("Failed to load UpSet analytics:", err);
    if (tableBody) {
      tableBody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: #f87171; padding: 8px;">⚠️ UpSet Analytics Unavailable</td></tr>`;
    }
    if (container) {
      container.innerHTML = `<div style="text-align: center; color: #f87171; padding: 8px;">⚠️ UpSet Analytics Unavailable</div>`;
    }
  }
}



function render2FundVennDiagram() {
  const selA = document.getElementById("vennFundA");
  const selB = document.getElementById("vennFundB");
  if (selA && selB) {
    loadOverlapAnalytics(selA.value, selB.value);
  } else {
    loadOverlapAnalytics();
  }
}



if (typeof window !== "undefined") {
  window.loadOverlapAnalytics = loadOverlapAnalytics;
  window.loadUpSetAnalytics = loadUpSetAnalytics;
  window.render2FundVennDiagram = render2FundVennDiagram;
  window.openOverlapModal = openOverlapModal;
  window.closeOverlapModal = closeOverlapModal;
}

if (typeof document !== "undefined") {
  document.addEventListener("DOMContentLoaded", async () => {
    const selA = document.getElementById("vennFundA");
    const selB = document.getElementById("vennFundB");
    const chk = document.getElementById("chkIncludeUnverified");
    if (selA && selB) {
      await populateFundDropdowns();
      selA.addEventListener("change", render2FundVennDiagram);
      selB.addEventListener("change", render2FundVennDiagram);
    }
    if (chk) {
      chk.addEventListener("change", render2FundVennDiagram);
    }
    if (document.getElementById("benchmarkMetricsGrid") || document.getElementById("benchmarkAlphaVal")) {
      loadBenchmarkAnalytics();
    }
    if (document.getElementById("upsetMatrixTable")) {
      loadUpSetAnalytics();
    }
  });
}
