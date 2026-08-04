import { API_BASE, fetchJson, getAuthHeaders, DEFAULT_AUTH_TOKEN } from './js/api.js';
import { state, setCurrentFy } from './js/state.js';
import { showToast, formatINR } from './js/utils.js';
import {
  fetchTaxMetrics,
  fetchRealizedLog,
  fetchDecisionRadar
} from './js/modules/tax.js';
import {
  updatePortfolioSummary,
  renderHoldingsTable,
  renderAllocationChart,
  renderCategoryChart,
  fetchConsolidationPreviewData,
  fetchRebalancePreview,
  fetchGoalSummary,
  fetchFireSummary,
  fetchBucketRebalance
} from './js/modules/portfolio.js';

document.addEventListener('DOMContentLoaded', () => {
  // Tab Switching Handler
  const tabBtns = document.querySelectorAll('.tab-btn');
  const tabContents = document.querySelectorAll('.tab-content');

  tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const target = btn.getAttribute('data-tab');
      tabBtns.forEach(b => b.classList.remove('active'));
      tabContents.forEach(c => c.classList.remove('active'));

      btn.classList.add('active');
      const content = document.getElementById(`tab-${target}`);
      if (content) content.classList.add('active');

      setTimeout(() => {
        if (state.charts.allocChart) state.charts.allocChart.resize();
        if (state.charts.categoryChart) state.charts.categoryChart.resize();
      }, 50);
    });
  });

  const fySelect = document.getElementById('fySelect');
  if (fySelect) {
    setCurrentFy(fySelect.value);
    fySelect.addEventListener('change', () => {
      setCurrentFy(fySelect.value);
      fetchTaxMetrics();
      fetchRealizedLog();
      fetchRebalancePreview();
    });
  }

  fetchLiveMetrics();

  // Command Palette Handler (Cmd + K / Ctrl + K / Slash)
  const cmdPaletteModal = document.getElementById('commandPaletteModal');
  const cmdInput = document.getElementById('commandPaletteInput');
  const cmdResults = document.getElementById('commandPaletteResults');

  function openCmdPalette() {
    const modal = document.getElementById('commandPaletteModal') || cmdPaletteModal;
    const input = document.getElementById('commandPaletteInput') || cmdInput;
    if (!modal) return;

    if (modal.hasAttribute('open') || modal.open) {
      return;
    }

    try {
      modal.showModal();
    } catch (e) {
      modal.setAttribute('open', 'true');
    }

    if (input) {
      setTimeout(() => {
        input.focus();
        input.select();
      }, 50);
    }
  }

  function closeCmdPalette() {
    const modal = document.getElementById('commandPaletteModal') || cmdPaletteModal;
    if (!modal) return;

    try {
      if (modal.open) {
        modal.close();
      } else {
        modal.removeAttribute('open');
      }
    } catch (e) {
      modal.removeAttribute('open');
    }
  }

  // Event Delegation for Button, Close X, and Backdrop Click
  document.addEventListener('click', (e) => {
    if (e.target.closest('#cmdKTriggerBtn, .cmd-k-btn')) {
      e.preventDefault();
      openCmdPalette();
      return;
    }

    if (e.target.closest('#closeCmdPaletteBtn')) {
      e.preventDefault();
      closeCmdPalette();
      return;
    }

    const modal = document.getElementById('commandPaletteModal') || cmdPaletteModal;
    if (modal && e.target === modal) {
      closeCmdPalette();
    }
  });

  if (cmdPaletteModal) {
    cmdPaletteModal.addEventListener('cancel', () => closeCmdPalette());
  }

  window.addEventListener('keydown', (e) => {
    const key = e.key ? e.key.toLowerCase() : '';
    const isInputActive = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName);

    if (((e.metaKey || e.ctrlKey || e.altKey) && key === 'k') || (!isInputActive && key === '/')) {
      e.preventDefault();
      e.stopPropagation();
      openCmdPalette();
    }
  }, true);

  if (cmdInput) {
    cmdInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        const query = cmdInput.value.trim();
        if (!query) return;

        if (cmdResults) {
          cmdResults.innerHTML = '<div style="padding:12px; color:#06b6d4; font-family:monospace;">🧠 AI Engine Thinking...</div>';
        }

        const evtSource = new EventSource(`/api/v1/llm/stream?prompt=${encodeURIComponent(query)}`);
        let outputText = '';

        evtSource.onmessage = function(event) {
          outputText += event.data;
          if (cmdResults) {
            cmdResults.innerHTML = `
              <div style="padding:12px; background:#0f172a; border-radius:8px; color:#f8fafc; font-size:13px; white-space:pre-wrap; font-family:monospace; line-height:1.5;">
                <div style="color:#d0ff00; font-weight:bold; margin-bottom:6px;">⚡ PORTFOLIO OS AI RESPONSE</div>
                ${outputText}
              </div>
            `;
          }
        };

        evtSource.onerror = function() {
          evtSource.close();
        };
      }
    });
  }

  if (cmdResults) {
    cmdResults.addEventListener('click', (e) => {
      const item = e.target.closest('.cmd-item');
      if (!item) return;
      const action = item.getAttribute('data-action');
      closeCmdPalette();

      if (action === 'schedule-cg') {
        window.open('/api/v1/tax/schedule-cg/export', '_blank');
        showToast('Downloading Schedule CG Tax Report CSV...', 'success');
      } else if (action === 'rebalance') {
        fetchBucketRebalance();
        showToast('Evaluating Portfolio Rebalance Rungs...', 'info');
      } else if (action === 'whatif' || action === 'holdings') {
        const hTab = document.querySelector('[data-tab="holdings"]');
        if (hTab) hTab.click();
      } else if (action === 'radar') {
        fetchDecisionRadar();
      }
    });
  }

  // Export ZIP button listener
  const exportZipBtn = document.getElementById('exportZipBtn');
  if (exportZipBtn) {
    exportZipBtn.addEventListener('click', () => {
      const token = localStorage.getItem('API_AUTH_TOKEN') || window.API_AUTH_TOKEN || DEFAULT_AUTH_TOKEN;
      window.location.href = `${API_BASE}/tax/export/itr2/zip?fy=${state.currentFy}&token=${encodeURIComponent(token)}`;
      showToast(`Generating ITR-2 CSV Bundle (.zip) for ${state.currentFy}...`, 'success');
    });
  }

  // Rebalance Slider listener
  const slider = document.getElementById('rebalanceSlider');
  const sliderVal = document.getElementById('rebalanceSliderVal');
  if (slider && sliderVal) {
    slider.addEventListener('input', () => {
      const val = parseInt(slider.value) || 100000;
      sliderVal.textContent = formatINR(val);
      fetchRebalancePreview(val);
    });
  }

  // File Upload listener
  const fileInput = document.getElementById('fileUploadInput');
  if (fileInput) {
    fileInput.addEventListener('change', async (e) => {
      const file = e.target.files[0];
      if (!file) return;

      let password = '';
      if (file.name.toLowerCase().endsWith('.pdf')) {
        password = prompt("Enter password for encrypted CAS PDF (usually PAN in lowercase or PAN + DOB):") || '';
      }

      const formData = new FormData();
      formData.append('file', file);
      if (password) {
        formData.append('password', password);
      }

      const uploadBtn = document.querySelector('.upload-btn');
      try {
        if (uploadBtn) uploadBtn.textContent = 'Parsing Statement...';

        const res = await fetch(`${API_BASE}/statements/upload`, {
          method: 'POST',
          headers: getAuthHeaders(),
          body: formData
        });

        const result = await res.json().catch(() => null);

        if (res.ok && result && (result.status === 'SUCCESS' || Array.isArray(result) || result.eventsIngested !== undefined)) {
          const count = Array.isArray(result) ? result.length : (result.eventsIngested || 0);
          showToast(`Statement ingested successfully (${count} events).`, 'success');
          fetchLiveMetrics();
        } else {
          const msg = (result && result.message) ? result.message : 'Statement parsing failed or unauthorized.';
          showToast(msg, 'error');
        }
      } catch (err) {
        showToast(`Upload error: ${err.message}`, 'error');
      } finally {
        if (uploadBtn) uploadBtn.textContent = 'Upload CAS PDF / CSV';
        fileInput.value = '';
      }
    });
  }
});

async function fetchLiveMetrics() {
  try {
    const summary = await fetchJson(`${API_BASE}/portfolio/summary`).catch(() => null);
    if (summary) {
      updatePortfolioSummary(summary);
    }

    fetchTaxMetrics();

    const allocations = await fetchJson(`${API_BASE}/portfolio/allocation`).catch(() => null);
    if (allocations) {
      renderAllocationChart(allocations);
    }

    const catAllocations = await fetchJson(`${API_BASE}/portfolio/category-allocation`).catch(() => null);
    if (catAllocations) {
      renderCategoryChart(catAllocations);
    }

    const holdings = await fetchJson(`${API_BASE}/portfolio/holdings`).catch(() => null);
    if (holdings) {
      renderHoldingsTable(holdings);
    }

    fetchDecisionRadar();
    fetchRealizedLog();
    fetchGoalSummary();
    fetchFireSummary();
    fetchBucketRebalance();
    fetchConsolidationPreviewData();

    const slider = document.getElementById('rebalanceSlider');
    const amt = slider ? slider.value : 100000;
    fetchRebalancePreview(amt);
  } catch (err) {
    console.log('Portfolio OS API starting up, retrying...');
  }
}

// Global debounced resize listener for ECharts
let resizeTimer = null;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    if (state.charts.allocChart) state.charts.allocChart.resize();
    if (state.charts.categoryChart) state.charts.categoryChart.resize();
  }, 150);
});
