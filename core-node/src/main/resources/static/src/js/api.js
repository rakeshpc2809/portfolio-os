export const API_BASE = window.location.origin.includes('http') 
  ? `${window.location.origin}/api/v1` 
  : 'http://127.0.0.1:8080/api/v1';

export async function fetchJson(url, options = {}) {
  const token = localStorage.getItem('API_AUTH_TOKEN') || window.API_AUTH_TOKEN || '';
  const headers = {
    ...(options.headers || {}),
    ...(token ? { 'X-Api-Auth-Token': token } : {})
  };
  const res = await fetch(url, { ...options, headers });
  if (!res.ok) {
    throw new Error(`HTTP error! status: ${res.status}`);
  }
  return await res.json();
}
