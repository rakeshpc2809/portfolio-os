import { API_BASE } from './js/api.js';
import { state, setCurrentFy } from './js/state.js';
import { showToast, formatINR } from './js/utils.js';
import {
  fetchTaxMetrics,
  fetchRealizedLog,
  fetchDecisionRadar
} from './js/modules/tax.js';
import {
  fetchInsuranceChecklist
} from './js/modules/insurance.js';
import {
  updatePortfolioSummary,
  renderHoldingsTable,
  renderPerformanceChart,
  renderAllocationChart,
  renderCategoryChart,
  fetchConsolidationPreviewData,
  fetchRebalancePreview,
  fetchGoalSummary,
  fetchFireSummary,
  fetchBucketRebalance,
  initConfigurator
} from './js/modules/portfolio.js';

document.addEventListener('DOMContentLoaded', () => {
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
  initConfigurator();

  // Export ZIP button listener
  const exportZipBtn = document.getElementById('exportZipBtn');
  if (exportZipBtn) {
    exportZipBtn.addEventListener('click', () => {
      window.location.href = `${API_BASE}/tax/export/itr2/zip?fy=${state.currentFy}`;
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
          body: formData
        });

        const result = await res.json().catch(() => null);

        if (res.ok) {
          showToast('Statement ingested successfully.', 'success');
          fetchLiveMetrics();
        } else {
          const msg = (result && result.message) ? result.message : 'Statement parsing failed. Please check file format.';
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
    const summaryRes = await fetch(`${API_BASE}/portfolio/summary`);
    if (summaryRes.ok) {
      const summary = await summaryRes.json();
      updatePortfolioSummary(summary);
    }

    fetchTaxMetrics();

    const allocRes = await fetch(`${API_BASE}/portfolio/allocation`);
    if (allocRes.ok) {
      const allocations = await allocRes.json();
      renderAllocationChart(allocations);
    }

    const catRes = await fetch(`${API_BASE}/portfolio/category-allocation`);
    if (catRes.ok) {
      const catAllocations = await catRes.json();
      renderCategoryChart(catAllocations);
    }

    const holdingsRes = await fetch(`${API_BASE}/portfolio/holdings`);
    if (holdingsRes.ok) {
      const holdings = await holdingsRes.json();
      renderHoldingsTable(holdings);
    }

    fetchDecisionRadar();
    fetchRealizedLog();
    fetchInsuranceChecklist();
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

// Global debounced resize listener for GPU-accelerated ECharts
let resizeTimer = null;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    if (state.charts.perfChart) state.charts.perfChart.resize();
    if (state.charts.allocChart) state.charts.allocChart.resize();
    if (state.charts.categoryChart) state.charts.categoryChart.resize();
  }, 150);
});
