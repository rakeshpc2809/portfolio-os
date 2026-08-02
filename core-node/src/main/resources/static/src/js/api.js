export const API_BASE = window.location.origin.includes('http') 
  ? `${window.location.origin}/api/v1` 
  : 'http://127.0.0.1:8080/api/v1';

export const DEFAULT_AUTH_TOKEN = 'fintracker-cachyos-default-key-2026';

export function getAuthHeaders(extraHeaders = {}) {
  const token = localStorage.getItem('API_AUTH_TOKEN') || window.API_AUTH_TOKEN || DEFAULT_AUTH_TOKEN;
  return {
    ...extraHeaders,
    'X-Api-Auth-Token': token
  };
}

export async function fetchJson(url, options = {}) {
  const headers = getAuthHeaders(options.headers || {});
  const res = await fetch(url, { ...options, headers });
  if (!res.ok) {
    throw new Error(`HTTP error! status: ${res.status}`);
  }
  return await res.json();
}
