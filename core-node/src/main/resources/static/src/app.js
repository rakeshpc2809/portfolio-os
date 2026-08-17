import { API_BASE, fetchJson } from './js/api.js';
import { state } from './js/state.js';
import { formatINR, showToast } from './js/utils.js';
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
  fetchFireSummary
} from './js/modules/portfolio.js?v=4.0.0';
import { updateExemptionMeter, updateReportMetrics, renderDecisionRadar, fetchDecisionRadar, fetchTaxMetrics, renderRealizedLogTable } from './js/modules/tax.js';

const DEFAULT_AUTH_TOKEN = 'dev_secret_key_123';

async function initDashboard() {
  try {
    const summaryData = await fetchJson(`/portfolio/summary?fy=${state.currentFy}`).catch(() => null);
    if (summaryData) updatePortfolioSummary(summaryData);

    const holdings = await fetchJson(`/portfolio/holdings`).catch(() => []);
    state.holdings = holdings;
    renderHoldingsTable(holdings);
    renderSchemeGroupedTaxLotsUI(holdings, 'groupedTaxLotsContainer');
    renderSchemeGroupedTaxLotsUI(holdings, 'groupedTaxLotsContainerTaxTab');

    const bucketTargetsConfig = await fetchJson(`/config/bucket-targets`).catch(() => null);
    state.bucketTargetsConfig = bucketTargetsConfig;
    if (bucketTargetsConfig && holdings) {
      renderFundAllocationCompareChart('fundAllocationCompareChart', holdings, bucketTargetsConfig);
    }

    const navTrendData = await fetchJson(`/portfolio/net-worth-trend`).catch(() => null);
    if (navTrendData && navTrendData.dates && navTrendData.values) {
      if (state.charts.trendChart) state.charts.trendChart.dispose();
      state.charts.trendChart = renderNetWorthTrendChart('netWorthTrendChart', navTrendData.dates, navTrendData.values);
    }

    const allocData = await fetchJson(`/portfolio/allocation`).catch(() => null);
    if (allocData) renderAllocationChart(allocData);

    const catData = await fetchJson(`/portfolio/category-allocation`).catch(() => null);
    if (catData) renderCategoryChart(catData);

    const bucketAllocData = await fetchJson(`/portfolio/bucket-allocation`).catch(() => null);
    if (bucketAllocData) renderBucketAllocationChart('bucketAllocationChart', bucketAllocData);

    const exemptionData = await fetchJson(`/tax/exemption-status?fy=${state.currentFy}`).catch(() => null);
    if (exemptionData) updateExemptionMeter(exemptionData);

    const planData = await fetchJson(`/sync/rebalance/plan?trigger=DRIFT`).catch(() => null);
    if (planData) renderUnifiedRebalancePlanUI(planData);

    const bucketData = await fetchJson(`/rebalance/bucket?fy=${state.currentFy}`).catch(() => null);
    if (bucketData) renderBucketRebalance(bucketData);

    // Render Cashflow Sankey Flow Diagram
    if (state.charts.sankeyChart) state.charts.sankeyChart.dispose();
    state.charts.sankeyChart = renderCashflowSankey('sankeyChart', holdings, bucketData);

    const eventsData = await fetchJson(`/tax/realized-log?fy=${state.currentFy}`).catch(() => null);
    if (eventsData) renderRealizedLogTable(eventsData);

    fetchDecisionRadar();
    fetchTaxMetrics();
    fetchFireSummary();
  } catch (err) {
    console.error("Dashboard initialization failed:", err);
    showToast("Error connecting to Core Node REST service.", "error");
  }
}

async function fetchRebalancePreview(amount) {
  try {
    const preview = await fetchJson(`/rebalance/preview?amount=${amount}&fy=${state.currentFy}`);
    const dragEl = document.getElementById('rebTaxDrag');
    const rateEl = document.getElementById('rebEffRate');
    const ltcgEl = document.getElementById('rebLtcgHarvested');

    if (dragEl) dragEl.textContent = formatINR(parseFloat(preview.total_tax_drag || preview.totalTaxDrag || '0'));
    if (rateEl) rateEl.textContent = `${preview.effective_tax_rate_pct || preview.effectiveTaxRatePct || '0.00'}%`;
    if (ltcgEl) ltcgEl.textContent = formatINR(parseFloat(preview.ltcg_exemption_harvested || preview.ltcgExemptionHarvested || '0'));
  } catch (err) {
    console.error("Failed to fetch rebalance preview:", err);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  initDashboard();

  window.openCmdPalette = () => {
    const modal = document.getElementById('commandPaletteModal');
    if (modal) modal.style.display = 'flex';
    const input = document.getElementById('commandPaletteInput');
    if (input) { input.focus(); input.select(); }
  };

  window.closeCmdPalette = () => {
    const modal = document.getElementById('commandPaletteModal');
    if (modal) modal.style.display = 'none';
  };

  window.openHoldingDrawer = (idx) => {
    const holding = state.holdings[idx];
    if (!holding) return;

    const drawer = document.getElementById('holdingDetailDrawer');
    const backdrop = document.getElementById('holdingDetailDrawerBackdrop');
    const titleEl = document.getElementById('drawerAssetTitle');
    const catEl = document.getElementById('drawerAssetCategory');
    const bodyEl = document.getElementById('drawerBody');

    if (!drawer || !backdrop || !bodyEl) return;

    const assetName = holding.asset_name || holding.assetName || '';
    const category = holding.category || 'EQUITY';
    const inv = Math.round(parseFloat(holding.invested_value || holding.investedValue) || 0);
    const cur = Math.round(parseFloat(holding.current_value || holding.currentValue) || 0);
    const gain = Math.round(parseFloat(holding.unrealized_gain || holding.unrealizedGain) || 0);
    const gainPct = holding.unrealized_gain_pct || holding.unrealizedGainPct || '0.00';
    const lots = holding.lots || [];

    if (titleEl) titleEl.textContent = assetName;
    if (catEl) {
      catEl.textContent = category.replace('_SPECIFIED_50AA', '');
      catEl.className = `live-tag cat-${category}`;
    }

    let lotsHtml = lots.map((l, lotIdx) => {
      const acqDate = l.acquisition_date || l.acquisitionDate;
      const units = parseFloat(l.remaining_units || l.remainingUnits || '0');
      const costPerUnit = parseFloat(l.cost_per_unit || l.costPerUnit || '0');
      const lotGain = parseFloat(l.unrealized_gain || l.unrealizedGain || '0');
      const daysHeld = l.holding_days !== undefined ? l.holding_days : l.holdingDays;
      const isLtcg = l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg;

      return `
        <div class="drawer-lot-card">
          <div>
            <div style="font-size:12px; font-weight:600; color:#fff;">Lot #${lotIdx + 1} · Acquired ${acqDate} (${daysHeld}d held)</div>
            <div style="font-size:11px; color:#94a3b8; margin-top:3px;" class="font-mono">${units.toFixed(2)} units @ ₹${costPerUnit.toFixed(2)}</div>
          </div>
          <div style="text-align:right; display:flex; flex-direction:column; align-items:flex-end; gap:6px;">
            <div style="font-size:13px; font-weight:700; color:${lotGain >= 0 ? '#10b981' : '#ef4444'};" class="font-mono">${lotGain >= 0 ? '+' : ''}${formatINR(lotGain)}</div>
            <div style="display:flex; gap:6px; align-items:center;">
              <span class="cat-badge ${isLtcg ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${isLtcg ? 'LTCG Free' : 'STCG Locked'}</span>
              <button type="button" class="drawer-action-btn" onclick="window.harvestLot('${holding.isin || ''}', '${assetName.replace(/'/g, "\\'")}', ${units}, ${costPerUnit})">Harvest ➔</button>
            </div>
          </div>
        </div>
      `;
    }).join('');

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
        <strong style="font-size:15px; color:${gain >= 0 ? '#10b981' : '#ef4444'};" class="font-mono">${gain >= 0 ? '+' : ''}${formatINR(gain)} (${gainPct}%)</strong>
      </div>
      <h4 style="font-size:13px; font-weight:700; color:#06b6d4; margin-top:8px;">FIFO Open Tax Lots (${lots.length})</h4>
      <div style="display:flex; flex-direction:column; gap:10px;">${lotsHtml || '<div style="color:#94a3b8; font-size:12px;">No open lots available.</div>'}</div>
    `;

    backdrop.classList.add('open');
    drawer.classList.add('open');
  };

  window.closeHoldingDrawer = () => {
    const drawer = document.getElementById('holdingDetailDrawer');
    const backdrop = document.getElementById('holdingDetailDrawerBackdrop');
    if (drawer) drawer.classList.remove('open');
    if (backdrop) backdrop.classList.remove('open');
  };

  window.harvestLot = (isin, schemeName, units, costPerUnit) => {
    window.closeHoldingDrawer();
    window.openCmdPalette();
    const input = document.getElementById('commandPaletteInput');
    if (input) {
      input.value = `rebalance ${Math.max(10000, Math.round(units * costPerUnit))}`;
      window.submitAiPrompt();
    }
  };

  window.submitAiPrompt = async () => {
    const input = document.getElementById('commandPaletteInput');
    const results = document.getElementById('commandPaletteResults');
    if (!input || !results) return;

    const promptText = input.value.trim();
    if (!promptText) return;

    const promptLower = promptText.toLowerCase();

    // Raycast Action Interception for Rebalance & Waterfall
    if (promptLower.includes("rebalance") || promptLower.includes("waterfall") || promptLower.includes("trim")) {
      const match = promptText.match(/\d+/);
      const amount = match ? parseInt(match[0]) : 50000;
      results.innerHTML = `<div style="padding:12px; color:#06b6d4;">⚙️ Calculating Tax-Aware Waterfall for ₹${formatINR(amount)}...</div>`;

      try {
        const wf = await fetchJson(`/rebalance/waterfall?bucket=EQUITY_CORE&amount=${amount}&fy=${state.currentFy}`);
        const stepsHtml = wf.steps.map(s => `
          <div class="cmd-step-row">
            <span><strong style="color:#d0ff00;">${s.tier}</strong>: ${s.asset_name || s.assetName}</span>
            <span class="font-mono">₹ ${formatINR(parseFloat(s.proceeds))} (Tax: ₹ ${formatINR(parseFloat(s.tax_drag || s.taxDrag))})</span>
          </div>
        `).join('');

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

    // Default SSE AI prompt stream
    results.innerHTML = '<div style="padding:12px; color:#d0ff00; font-family:monospace;">⚡ Streaming response from Qwen LLM...</div><div id="cmdKOutput" style="white-space:pre-wrap; font-size:13px; font-family:monospace; color:#f8fafc; max-height:280px; overflow-y:auto; padding:10px; background:rgba(0,0,0,0.4); border-radius:8px; border:1px solid rgba(255,255,255,0.1);"></div>';
    
    const resEl = document.getElementById('cmdKOutput');
    const token = localStorage.getItem('API_AUTH_TOKEN') || window.API_AUTH_TOKEN || DEFAULT_AUTH_TOKEN;
    const url = `${API_BASE}/llm/stream?prompt=${encodeURIComponent(promptText)}&token=${encodeURIComponent(token)}`;

    const eventSource = new EventSource(url);
    let outputText = '';

    eventSource.onmessage = (event) => {
      if (event.data) {
        outputText += event.data;
        if (resEl) {
          resEl.textContent = outputText;
          resEl.scrollTop = resEl.scrollHeight;
        }
      }
    };

    eventSource.onerror = (err) => {
      console.error("SSE stream error:", err);
      eventSource.close();
      if (resEl && !outputText) {
        resEl.innerHTML = '<div style="padding:12px; color:#ef4444; font-family:monospace;">⚠️ Streaming failed. Verify connection or authentication token.</div>';
      }
    };
  };

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      const activeEl = document.activeElement;
      if (activeEl && activeEl.id === 'commandPaletteInput') {
        e.preventDefault();
        window.submitAiPrompt();
      }
    }
  });

  const cmdTrigger = document.getElementById('cmdKTriggerBtn');
  if (cmdTrigger) {
    cmdTrigger.addEventListener('click', window.openCmdPalette);
  }

  const closeCmdBtn = document.getElementById('closeCmdPaletteBtn');
  if (closeCmdBtn) {
    closeCmdBtn.addEventListener('click', window.closeCmdPalette);
  }

  const slider = document.getElementById('rebalanceSlider');
  const sliderVal = document.getElementById('rebalanceSliderVal');
  if (slider && sliderVal) {
    slider.addEventListener('input', () => {
      const val = parseInt(slider.value) || 100000;
      sliderVal.textContent = formatINR(val);
      fetchRebalancePreview(val);
    });
  }

  const tabBtns = document.querySelectorAll('.tab-btn');
  tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const tabName = btn.dataset.tab;
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

      btn.classList.add('active');
      const targetContent = document.getElementById(`tab-${tabName}`);
      if (targetContent) targetContent.classList.add('active');

      if (tabName === 'fire') {
        fetchFireSummary();
      }
    });
  });
});

async function uploadCasFile(file, password) {
  const statusEl = document.getElementById('casUploadStatus');
  const token = localStorage.getItem('API_AUTH_TOKEN') || window.API_AUTH_TOKEN || DEFAULT_AUTH_TOKEN;
  
  if (statusEl) {
    statusEl.style.display = 'block';
    statusEl.style.background = 'rgba(6, 182, 212, 0.1)';
    statusEl.style.color = '#06b6d4';
    statusEl.style.border = '1px solid rgba(6, 182, 212, 0.3)';
    statusEl.textContent = '⚡ Decrypting & Parsing CAS transactions...';
  }

  const formData = new FormData();
  formData.append('file', file);
  if (password) formData.append('password', password);

  try {
    const res = await fetch(`/api/v1/statements/upload`, {
      method: 'POST',
      headers: {
        'X-Api-Auth-Token': token
      },
      body: formData
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => 'Upload failed');
      throw new Error(errText || `Server returned ${res.status}`);
    }

    const events = await res.json();
    showToast(`✅ Successfully ingested CAS statement! Registered ${events ? events.length || 0 : 0} transaction events.`, 'success');
    window.closeCasPasswordModal();
    initDashboard();
  } catch (err) {
    console.error("CAS upload failed:", err);
    if (statusEl) {
      statusEl.style.display = 'block';
      statusEl.style.background = 'rgba(239, 68, 68, 0.1)';
      statusEl.style.color = '#ef4444';
      statusEl.style.border = '1px solid rgba(239, 68, 68, 0.3)';
      statusEl.textContent = `⚠️ CAS Parsing Failed: ${err.message || 'Incorrect password or unsupported file format'}`;
    }
  }
}

let currentSelectedCasFile = null;

window.closeCasPasswordModal = () => {
  const modal = document.getElementById('casPasswordModal');
  if (modal) modal.style.display = 'none';
  const fileInput = document.getElementById('fileUploadInput');
  if (fileInput) fileInput.value = '';
  currentSelectedCasFile = null;
};

window.handleFileSelect = (e) => {
  const file = e.target ? e.target.files[0] : (e.files ? e.files[0] : null);
  if (!file) return;
  currentSelectedCasFile = file;

  if (file.name.toLowerCase().endsWith('.pdf')) {
    const modal = document.getElementById('casPasswordModal');
    const filenameEl = document.getElementById('casModalFilename');
    const passInput = document.getElementById('casPasswordInput');
    const statusEl = document.getElementById('casUploadStatus');

    if (filenameEl) filenameEl.textContent = file.name;
    if (passInput) passInput.value = '';
    if (statusEl) statusEl.style.display = 'none';
    if (modal) modal.style.display = 'flex';
    if (passInput) setTimeout(() => passInput.focus(), 100);
  } else {
    uploadCasFile(file, '');
  }
};

window.submitCasUpload = () => {
  const passInput = document.getElementById('casPasswordInput');
  const password = passInput ? passInput.value : '';
  if (currentSelectedCasFile) {
    uploadCasFile(currentSelectedCasFile, password);
  }
};
