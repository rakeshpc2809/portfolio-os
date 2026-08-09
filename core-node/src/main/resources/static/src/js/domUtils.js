export function setText(selectorOrEl, text) {
  const el = typeof selectorOrEl === 'string' ? document.querySelector(selectorOrEl) : selectorOrEl;
  if (el) {
    el.textContent = text !== null && text !== undefined ? text : '—';
  }
}

export function setHtml(selectorOrEl, html) {
  const el = typeof selectorOrEl === 'string' ? document.querySelector(selectorOrEl) : selectorOrEl;
  if (el) {
    el.innerHTML = html;
  }
}

export function setBadgeStyle(selectorOrEl, text, className) {
  const el = typeof selectorOrEl === 'string' ? document.querySelector(selectorOrEl) : selectorOrEl;
  if (el) {
    if (text) el.textContent = text;
    if (className) el.className = className;
  }
}

export function setErrorState(selectorOrEl, errorText = '—', badgeSelector = null, badgeText = 'OFFLINE') {
  setText(selectorOrEl, errorText);
  if (badgeSelector) {
    setBadgeStyle(badgeSelector, badgeText, 'live-tag warning-tag');
  }
}
