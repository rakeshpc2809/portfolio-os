import { API_BASE, fetchJson, getAuthHeaders } from '../api.js';
import { showToast } from '../utils.js';

export async function fetchInsuranceChecklist() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/insurance`).catch(() => null);
    if (data) {
      renderInsuranceBanner(data);
    }
  } catch (e) {
    console.error('Insurance checklist error:', e);
  }
}

export function renderInsuranceBanner(data) {
  const banner = document.getElementById('insuranceBanner');
  const itemsContainer = document.getElementById('insuranceItemsList');
  const badge = document.getElementById('insuranceStatusBadge');
  if (!banner || !itemsContainer) return;

  if (data.isAllPurchased) {
    banner.style.display = 'none';
    return;
  }

  banner.style.display = 'block';
  if (badge) badge.textContent = 'ACTION REQUIRED';

  let html = '';
  data.items.forEach(item => {
    const isPurchased = item.status === 'PURCHASED';
    html += `
      <div class="insurance-card">
        <div class="insurance-info">
          <div class="title">${item.name}</div>
          <div class="desc">${item.description}</div>
        </div>
        <button class="action-btn ${isPurchased ? 'purchased-btn' : ''}" data-id="${item.id}" data-status="${isPurchased ? 'NOT_PURCHASED' : 'PURCHASED'}">
          ${isPurchased ? '✓ Purchased' : 'Mark Purchased'}
        </button>
      </div>
    `;
  });
  itemsContainer.innerHTML = html;

  itemsContainer.querySelectorAll('.action-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-id');
      const status = btn.getAttribute('data-status');
      toggleInsuranceStatus(id, status);
    });
  });
}

export async function toggleInsuranceStatus(id, status) {
  try {
    const res = await fetch(`${API_BASE}/portfolio/insurance`, {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ id, status })
    });
    if (res.ok) {
      const updated = await res.json();
      renderInsuranceBanner(updated);
      showToast(`Updated ${id} insurance status`, 'success');
    }
  } catch (e) {
    showToast(`Error updating insurance: ${e.message}`, 'error');
  }
}

window.toggleInsuranceStatus = toggleInsuranceStatus;
