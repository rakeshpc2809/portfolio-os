export const API_BASE = '/api/v1';

export const DEFAULT_AUTH_TOKEN = 'fintracker-cachyos-default-key-2026';

export function getAuthHeaders(extraHeaders = {}) {
  const token = localStorage.getItem('API_AUTH_TOKEN') || window.API_AUTH_TOKEN || DEFAULT_AUTH_TOKEN;
  return {
    ...extraHeaders,
    'X-Api-Auth-Token': token
  };
}

export async function fetchJson(url, options = {}) {
  const token = localStorage.getItem('API_AUTH_TOKEN') || window.API_AUTH_TOKEN || DEFAULT_AUTH_TOKEN;
  let fullUrl = url.startsWith('http') || url.startsWith('/api/v1') ? url : `${API_BASE}${url.startsWith('/') ? '' : '/'}${url}`;
  const separator = fullUrl.includes('?') ? '&' : '?';
  if (!fullUrl.includes('token=')) {
    fullUrl = `${fullUrl}${separator}token=${encodeURIComponent(token)}`;
  }
  const headers = getAuthHeaders(options.headers || {});
  const res = await fetch(fullUrl, { ...options, headers });
  if (!res.ok) {
    throw new Error(`HTTP error! status: ${res.status}`);
  }
  return await res.json();
}
