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

let isProvisionalMode = false;

export function openOverlapModal() {
  const modal = document.getElementById("overlapInspectorModal");
  const backdrop = document.getElementById("overlapModalBackdrop");
  if (modal) modal.style.display = "block";
  if (backdrop) backdrop.style.display = "block";
  loadFundRegistryForOverlap().then(() => {
    loadOverlapInspectorData();
  });
}

export function closeOverlapModal() {
  const modal = document.getElementById("overlapInspectorModal");
  const backdrop = document.getElementById("overlapModalBackdrop");
  if (modal) modal.style.display = "none";
  if (backdrop) backdrop.style.display = "none";
}

async function loadFundRegistryForOverlap() {
  try {
    const res = await fetchJson(`${API_BASE}/funds/registry`);
    if (res && res.status === "OK" && res.funds) {
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
    console.warn("Failed to load fund registry for overlap:", err);
  }
}

let activeOverlapRequestId = 0;

export async function loadOverlapInspectorData() {
  const currentRequestId = ++activeOverlapRequestId;
  const barsContainer = document.getElementById("pairwiseBarsContainer");
  const zeroContainer = document.getElementById("zeroOverlapContainer");
  const tableBody = document.querySelector("#topStockConcentrationTable tbody");
  const captionLine = document.getElementById("coverageCaptionLine");
  const countLabel = document.getElementById("overlapPairwiseCount");

  if (barsContainer) {
    setHtml(barsContainer, `<div style="text-align: center; color: #64748b; font-size: 0.8rem; padding: 16px;">Loading pairwise overlap...</div>`);
  }
  if (zeroContainer) {
    zeroContainer.style.display = "none";
    setHtml(zeroContainer, "");
  }
  if (tableBody) {
    setHtml(tableBody, `<tr><td colspan="5" style="text-align: center; color: #64748b; padding: 12px;">Loading concentrations...</td></tr>`);
  }

  try {
    const res = await fetchJson(
      `${API_BASE}/analytics/overlap?includeUnverified=${isProvisionalMode}`
    );
    if (currentRequestId !== activeOverlapRequestId) return;

    if (res && res.status === "OK") {
      const matrix = res.pairwise_matrix || [];
      const concentrations = res.portfolio_top_stock_concentrations || [];
      const telemetry = res.coverage_telemetry;

      // 1. Process & Render Pairwise Matrix
      renderPairwiseBars(matrix, barsContainer, zeroContainer, countLabel);

      // 2. Render Top 5 Stock Concentrations
      renderTopStockConcentrations(concentrations, tableBody);

      // 3. Render Coverage Caption Line
      if (captionLine) {
        if (telemetry) {
          const auditedAum = formatINR(telemetry.audited_aum);
          const covPct = `${telemetry.audited_coverage_pct}%`;
          if (isProvisionalMode) {
            captionLine.innerHTML = `Showing all held funds including provisional estimates (Coverage: <strong style="color: #38bdf8;">${covPct}</strong> of equity).`;
          } else {
            captionLine.innerHTML = `Based on <strong style="color: #f8fafc;">${auditedAum}</strong> of audited holdings — <strong style="color: #38bdf8;">${covPct}</strong> of equity.`;
          }
        } else {
          captionLine.innerHTML = `Based on audited holdings.`;
        }
      }

      // Update link text
      const toggleLink = document.getElementById("linkToggleProvisional");
      if (toggleLink) {
        toggleLink.textContent = isProvisionalMode 
          ? "Hide provisional estimates (Audited only)" 
          : "Show provisional estimates too";
        toggleLink.style.color = isProvisionalMode ? "#38bdf8" : "#64748b";
      }

      await loadUpSetAnalytics();
    } else {
      throw new Error(res ? res.message : "Invalid API response");
    }
  } catch (err) {
    if (currentRequestId !== activeOverlapRequestId) return;
    console.error("Failed to load overlap inspector data:", err);
    if (barsContainer) {
      setHtml(barsContainer, `<div style="text-align: center; color: #f87171; font-size: 0.8rem; padding: 12px;">⚠️ Failed to load pairwise overlap data.</div>`);
    }
    if (captionLine) {
      captionLine.innerHTML = `<span style="color: #f87171;">⚠️ Failed to calculate equity coverage.</span>`;
    }
  }
}

function getProvenanceDot(sourceType) {
  const isUnverified = sourceType === "MANUAL_ESTIMATE_UNVERIFIED";
  if (isUnverified) {
    return `<span title="Provisional Sample (~30–60% coverage)" style="width: 7px; height: 7px; border-radius: 50%; background: #f59e0b; display: inline-block; flex-shrink: 0; vertical-align: middle;"></span>`;
  }
  const label = sourceType === "FACTSHEET_POI_PARSED" ? "Factsheet Audited" : "NSE Benchmark";
  return `<span title="${label}" style="width: 7px; height: 7px; border-radius: 50%; background: #38bdf8; display: inline-block; flex-shrink: 0; vertical-align: middle;"></span>`;
}

function renderPairwiseBars(matrix, container, zeroContainer, countLabel) {
  if (!matrix || matrix.length === 0) {
    if (container) setHtml(container, `<div style="text-align: center; color: #64748b; font-size: 0.8rem; padding: 12px;">No held fund pairs to compare.</div>`);
    if (countLabel) countLabel.textContent = "0 pairs";
    return;
  }

  // Deduplicate and filter out same-fund pairs
  const filtered = matrix.filter((p) => p.fund_a !== p.fund_b);
  
  // Sort descending by overlap percentage
  filtered.sort((a, b) => (b.overlap_percentage || 0) - (a.overlap_percentage || 0));

  const positivePairs = filtered.filter((p) => (p.overlap_percentage || 0) > 0);
  const zeroPairs = filtered.filter((p) => (p.overlap_percentage || 0) <= 0);

  if (countLabel) {
    countLabel.textContent = `${filtered.length} pairs (${positivePairs.length} overlapping)`;
  }

  let barsHtml = "";
  if (positivePairs.length === 0) {
    barsHtml = `<div style="text-align: center; color: #64748b; font-size: 0.8rem; padding: 8px;">No overlapping holdings found between funds.</div>`;
  } else {
    positivePairs.forEach((pair) => {
      const nameA = FUND_REGISTRY[pair.fund_a] || pair.fund_a;
      const nameB = FUND_REGISTRY[pair.fund_b] || pair.fund_b;
      const pct = (pair.overlap_percentage || 0).toFixed(2);
      const dotA = getProvenanceDot(pair.source_type_a);
      const dotB = getProvenanceDot(pair.source_type_b);
      const commonCount = pair.common_stock_count || (pair.common_stocks ? pair.common_stocks.length : 0);

      barsHtml += `
        <div style="display: flex; flex-direction: column; gap: 4px; padding: 6px 10px; background: rgba(15, 23, 42, 0.5); border-radius: 6px; border: 1px solid rgba(255,255,255,0.03);">
          <div style="display: flex; justify-content: space-between; align-items: center; font-size: 0.78rem;">
            <div style="display: flex; align-items: center; gap: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 78%;">
              ${dotA}
              <span style="color: #f8fafc; font-weight: 500;">${nameA}</span>
              <span style="color: #64748b; font-size: 0.72rem;">×</span>
              ${dotB}
              <span style="color: #f8fafc; font-weight: 500;">${nameB}</span>
            </div>
            <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
              <span style="font-size: 0.7rem; color: #64748b;">${commonCount} stocks</span>
              <span style="font-size: 0.84rem; font-weight: 700; color: #d0ff00; font-family: monospace;">${pct}%</span>
            </div>
          </div>
          <!-- Horizontal Bar -->
          <div style="width: 100%; height: 5px; background: rgba(255,255,255,0.06); border-radius: 3px; overflow: hidden;">
            <div style="width: ${Math.min(pct, 100)}%; height: 100%; background: linear-gradient(90deg, #0284c7, #38bdf8); border-radius: 3px;"></div>
          </div>
        </div>
      `;
    });
  }

  if (container) setHtml(container, barsHtml);

  // Zero Overlap Collapsed Text Lines
  if (zeroPairs.length > 0 && zeroContainer) {
    let zeroHtml = "";
    zeroPairs.forEach((pair) => {
      const nameA = FUND_REGISTRY[pair.fund_a] || pair.fund_a;
      const nameB = FUND_REGISTRY[pair.fund_b] || pair.fund_b;
      zeroHtml += `
        <div style="font-size: 0.72rem; color: #64748b; padding: 2px 6px;">
          No overlap found between <span style="color: #94a3b8;">${nameA}</span> and <span style="color: #94a3b8;">${nameB}</span>
        </div>
      `;
    });
    setHtml(zeroContainer, zeroHtml);
    zeroContainer.style.display = "flex";
  } else if (zeroContainer) {
    zeroContainer.style.display = "none";
  }
}

function renderTopStockConcentrations(concentrations, tableBody) {
  if (!tableBody) return;

  if (!concentrations || concentrations.length === 0) {
    setHtml(
      tableBody,
      `<tr><td colspan="5" style="text-align:center; color:#64748b; padding:12px;">No stock concentrations calculated.</td></tr>`
    );
    return;
  }

  // Display top 5 exposures matching spec density
  const top5 = concentrations.slice(0, 5);
  let html = "";
  top5.forEach((item) => {
    const provDot = item.is_audited 
      ? `<span title="Audited Holding" style="width: 7px; height: 7px; border-radius: 50%; background: #38bdf8; display: inline-block;"></span>` 
      : `<span title="Provisional Sample" style="width: 7px; height: 7px; border-radius: 50%; background: #f59e0b; display: inline-block;"></span>`;
    const company = item.company_name || "—";
    
    html += `<tr>
      <td><strong>${item.stock_symbol}</strong></td>
      <td style="color: #cbd5e1; font-size: 0.75rem;">${company}</td>
      <td style="text-align: right; font-family: monospace;">${formatINR(item.rupee_exposure)}</td>
      <td style="text-align: right; font-family: monospace;"><span class="metric-delta positive">${item.portfolio_percentage}%</span></td>
      <td style="text-align: center;">${provDot}</td>
    </tr>`;
  });

  setHtml(tableBody, html);
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
  window.loadOverlapAnalytics = loadOverlapInspectorData;
  window.loadOverlapInspectorData = loadOverlapInspectorData;
  window.loadUpSetAnalytics = loadUpSetAnalytics;
  window.openOverlapModal = openOverlapModal;
  window.closeOverlapModal = closeOverlapModal;
}

if (typeof document !== "undefined") {
  document.addEventListener("DOMContentLoaded", async () => {
    const toggleLink = document.getElementById("linkToggleProvisional");
    if (toggleLink) {
      toggleLink.addEventListener("click", () => {
        isProvisionalMode = !isProvisionalMode;
        loadOverlapInspectorData();
      });
    }

    const btnOpen = document.getElementById("btnOpenOverlapModal");
    if (btnOpen) {
      btnOpen.addEventListener("click", openOverlapModal);
    }

    if (document.getElementById("benchmarkMetricsGrid") || document.getElementById("benchmarkAlphaVal")) {
      loadBenchmarkAnalytics();
    }
    if (document.getElementById("upsetMatrixTable")) {
      loadUpSetAnalytics();
    }
  });
}
