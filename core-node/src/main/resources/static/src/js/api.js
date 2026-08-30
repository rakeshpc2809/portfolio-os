export const API_BASE = "/api/v1";

export const DEFAULT_AUTH_TOKEN = "dev_secret_key_123";

export function getAuthToken() {
  let token = localStorage.getItem("API_AUTH_TOKEN") || window.API_AUTH_TOKEN;
  if (!token || token === "undefined" || token === "null") {
    token = DEFAULT_AUTH_TOKEN;
    localStorage.setItem("API_AUTH_TOKEN", DEFAULT_AUTH_TOKEN);
  }
  return token;
}

export function getAuthHeaders(extraHeaders = {}) {
  return {
    ...extraHeaders,
    "X-Api-Auth-Token": getAuthToken(),
  };
}

export async function fetchJson(url, options = {}) {
  let token = getAuthToken();
  const fullUrl =
    url.startsWith("http") || url.startsWith("/api/v1")
      ? url
      : `${API_BASE}${url.startsWith("/") ? "" : "/"}${url}`;

  const headers = { ...getAuthHeaders(options.headers || {}) };
  let res = await fetch(fullUrl, { ...options, headers });

  if (res.status === 401 && token !== DEFAULT_AUTH_TOKEN) {
    // Stale token in localStorage -> reset to default & retry
    console.warn(
      "Received 401 Unauthorized with cached token, resetting to DEFAULT_AUTH_TOKEN and retrying...",
    );
    token = DEFAULT_AUTH_TOKEN;
    localStorage.setItem("API_AUTH_TOKEN", DEFAULT_AUTH_TOKEN);
    headers["X-Api-Auth-Token"] = DEFAULT_AUTH_TOKEN;
    res = await fetch(fullUrl, { ...options, headers });
  }

  if (!res.ok) {
    throw new Error(`HTTP error! status: ${res.status}`);
  }
  return await res.json();
}
