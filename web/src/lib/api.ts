/**
 * Portfolio OS - API Client & Type Definitions
 */

export interface SnapshotDto {
  generated_at?: string;
  data_freshness_status?: string;
  portfolio_summary?: PortfolioSummaryDto;
  rebalance_plan?: RebalancePlanDto;
  networth_trend?: NetworthTrendDto;
  holdings?: HoldingDto[];
  sync_info?: SyncInfoDto;
  quiet_mode?: boolean;
}

export interface PortfolioSummaryDto {
  net_worth?: number;
  total_invested?: number;
  unrealized_gain?: number;
  unrealized_gain_pct?: number;
  xirr_percentage?: string;
  formatted_xirr?: string;
  unallocated_cash?: number;
  ltcg_exemption_used?: number;
  ltcg_exemption_remaining?: number;
  ltcg_exemption_limit?: number;
}

export interface HoldingDto {
  asset_id: string;
  isin: string;
  asset_name: string;
  bucket: string;
  current_units: number;
  current_nav: number;
  nav_date: string;
  current_value: number;
  cost_basis: number;
  unrealized_gain: number;
  unrealized_gain_pct: number;
  allocation_pct: number;
  lots?: LotDto[];
}

export interface LotDto {
  lot_id: string;
  acquisition_date: string;
  units: number;
  cost_nav: number;
  cost_basis: number;
  current_value: number;
  realized_or_unrealized_gain: number;
  tax_term: 'LONG_TERM' | 'SHORT_TERM';
  is_grandfathered?: boolean;
  fmv_2018?: number;
  holding_period_days?: number;
}

export interface RebalancePlanDto {
  plan_id?: string;
  generated_at?: string;
  trigger?: {
    trigger_type: string;
    reason_code: string;
    reason_label: string;
  };
  summary?: {
    net_worth: number;
    total_invested: number;
    ltcg_exempt_gain?: number;
    stcg_taxable_gain?: number;
    projected_tax?: number;
  };
  buckets?: BucketAllocationDto[];
  buy_side?: {
    total_to_invest: number;
    is_manual_lumpsum: boolean;
    buckets: BucketAllocationDto[];
  };
  sell_side?: {
    total_required: number;
    waterfall_tiers: any[];
  };
}

export interface BucketAllocationDto {
  bucket: string;
  target_pct: number;
  current_pct: number;
  current_value?: number;
  post_pct?: number;
  amount_allocated?: number;
  is_drifted?: boolean;
  funds?: any[];
}

export interface NetworthTrendDto {
  dates: string[];
  net_worth: number[];
  invested: number[];
}

export interface SyncInfoDto {
  status: string;
  last_sync_timestamp: string;
  stale_nav_count: number;
  ledger_hmac_verified: boolean;
}

export interface OverlapReportDto {
  status: string;
  overlap_summary?: {
    max_overlap_percentage: number;
    fund_a_name: string;
    fund_b_name: string;
    threshold_breached: boolean;
  };
  portfolio_top_stock_concentrations?: Array<{
    stock_symbol: string;
    portfolio_percentage: number;
    rupee_exposure: number;
    is_audited: boolean;
  }>;
  pairwise_overlap?: Array<{
    fund_a_name: string;
    fund_b_name: string;
    overlap_percentage: number;
    common_stock_count: number;
  }>;
}

export function getAuthToken(): string {
  if (typeof window === 'undefined') return 'dev_secret_key_123';
  let token = localStorage.getItem('API_AUTH_TOKEN');
  if (!token || token === 'undefined' || token === 'null' || token.trim() === '') {
    // Check if provided via query param ?token=...
    try {
      const urlParams = new URLSearchParams(window.location.search);
      const qToken = urlParams.get('token');
      if (qToken && qToken.trim()) {
        token = qToken.trim();
        localStorage.setItem('API_AUTH_TOKEN', token);
        return token;
      }
    } catch {
      // ignore
    }

    // Default to the standard configured secret key for local single-user daemon
    token = 'dev_secret_key_123';
    localStorage.setItem('API_AUTH_TOKEN', token);
  }
  return token || 'dev_secret_key_123';
}

export function setAuthToken(token: string): void {
  if (typeof window !== 'undefined') {
    if (token && token.trim()) {
      localStorage.setItem('API_AUTH_TOKEN', token.trim());
    } else {
      localStorage.removeItem('API_AUTH_TOKEN');
    }
  }
}

export async function fetchJson<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = getAuthToken();
  const headers = new Headers(options.headers || {});
  if (token) {
    headers.set('X-Api-Auth-Token', token);
  }

  const url = endpoint.startsWith('http') ? endpoint : `/api/v1${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;
  const resp = await fetch(url, { ...options, headers });

  if (resp.status === 401) {
    throw new Error('Unauthorized: Invalid or missing API_AUTH_TOKEN. Please update your token in settings.');
  }

  if (!resp.ok) {
    let errMsg = `HTTP ${resp.status} ${resp.statusText}`;
    try {
      const errJson = await resp.json();
      if (errJson?.message) errMsg = errJson.message;
      else if (errJson?.error) errMsg = errJson.error;
    } catch {
      // ignore
    }
    throw new Error(errMsg);
  }

  return resp.json() as Promise<T>;
}

/**
 * Loads and normalizes complete portfolio data across Core Node endpoints.
 */
export async function loadDashboardData(): Promise<SnapshotDto> {
  const [snapshotRes, holdingsRes, bucketsRes, trendRes] = await Promise.all([
    fetchJson<any>('/sync/snapshot').catch(e => { console.warn('Snapshot error:', e); return null; }),
    fetchJson<any[]>('/reports/holdings').catch(e => { console.warn('Holdings error:', e); return []; }),
    fetchJson<any[]>('/reports/allocations/bucket').catch(e => { console.warn('Buckets error:', e); return []; }),
    fetchJson<any>('/portfolio/net-worth-trend').catch(e => { console.warn('Trend error:', e); return null; }),
  ]);

  if (!snapshotRes && (!holdingsRes || holdingsRes.length === 0)) {
    throw new Error('Could not connect to Portfolio OS backend. Please check if portfolio-os-core is running.');
  }

  const syncInfo = snapshotRes?.sync_info || {};
  const currentNetWorth = Number(syncInfo.current_value) || 0;
  const totalInvested = Number(syncInfo.total_invested) || 0;
  const unrealizedGain = Number(syncInfo.unrealized_gain) || (currentNetWorth - totalInvested);
  const unrealizedGainPct = Number(syncInfo.unrealized_gain_pct) || (totalInvested > 0 ? (unrealizedGain / totalInvested) * 100 : 0);

  // Normalize holdings & lots
  const rawHoldings = (holdingsRes && holdingsRes.length > 0) ? holdingsRes : (snapshotRes?.holdings || []);
  const normalizedHoldings: HoldingDto[] = rawHoldings.map((h: any) => {
    const isin = h.isin || h.asset_id || '';
    const name = h.asset_name || h.fund_name || isin;
    const bucket = h.category || h.asset_bucket || 'EQUITY_CORE';
    const cVal = Number(h.current_value) || 0;
    const cBasis = Number(h.invested_value) || 0;
    const uGain = Number(h.unrealized_gain) || (cVal - cBasis);
    const uGainPct = Number(h.unrealized_gain_pct) || (cBasis > 0 ? (uGain / cBasis) * 100 : 0);

    const lots: LotDto[] = (h.lots || []).map((l: any) => ({
      lot_id: l.lot_id || '',
      acquisition_date: l.acquisition_date || l.buy_date || '',
      units: Number(l.remaining_units ?? l.units ?? 0),
      cost_nav: Number(l.cost_per_unit ?? l.cost_nav ?? 0),
      cost_basis: Number(l.total_cost_basis ?? l.cost_basis ?? 0),
      current_value: Number(l.current_value ?? 0),
      realized_or_unrealized_gain: Number(l.unrealized_gain ?? l.realized_or_unrealized_gain ?? 0),
      tax_term: (l.is_ltcg || l.is_long_term || l.tax_classification?.includes('LTCG')) ? 'LONG_TERM' : 'SHORT_TERM',
      is_grandfathered: Boolean(l.grandfathered_nav),
      fmv_2018: l.grandfathered_nav ? Number(l.grandfathered_nav) : undefined,
      holding_period_days: Number(l.holding_days ?? 0)
    }));

    return {
      asset_id: isin,
      isin: isin,
      asset_name: name,
      bucket: bucket,
      current_units: Number(h.total_units || 0),
      current_nav: Number(h.current_nav || lots[0]?.cost_nav || 0),
      nav_date: h.nav_date || syncInfo.generated_at?.split('T')?.[0] || '2026-10-24',
      current_value: cVal,
      cost_basis: cBasis,
      unrealized_gain: uGain,
      unrealized_gain_pct: uGainPct,
      allocation_pct: Number(h.allocation_pct || (currentNetWorth > 0 ? (cVal / currentNetWorth) * 100 : 0)),
      lots: lots
    };
  });

  // Normalize buckets
  const rawBuckets = (bucketsRes && bucketsRes.length > 0) ? bucketsRes : (snapshotRes?.bucket_allocation || []);
  const normalizedBuckets: BucketAllocationDto[] = rawBuckets.map((b: any) => ({
    bucket: b.bucket || '',
    target_pct: Number(b.target_pct || 0),
    current_pct: Number(b.current_pct || 0),
    current_value: Number(b.current_value || 0),
    is_drifted: Boolean(b.is_drifted),
    funds: (b.funds || []).map((f: any) => ({
      ...f,
      asset_name: f.fund_name || f.asset_name || f.isin,
      isin: f.isin || f.asset_id,
      current_value: Number(f.current_value || f.value || 0),
      current_nav: Number(f.current_nav || 0)
    }))
  }));

  // Rebalance plan with normalized buckets
  const rebalancePlan: RebalancePlanDto = {
    ...(snapshotRes?.rebalance_plan || {}),
    buckets: normalizedBuckets,
    buy_side: snapshotRes?.rebalance_plan?.buy_side ? {
      ...snapshotRes.rebalance_plan.buy_side,
      buckets: normalizedBuckets
    } : undefined
  };

  // Trend data
  const trendDates = trendRes?.dates?.length ? trendRes.dates : [syncInfo.generated_at?.split('T')?.[0] || '2026-10-24'];
  const trendValues = trendRes?.values?.length ? trendRes.values : [currentNetWorth];
  const trendInvested = trendRes?.invested_values?.length ? trendRes.invested_values : [totalInvested];

  return {
    generated_at: syncInfo.generated_at || new Date().toISOString(),
    data_freshness_status: syncInfo.status || 'HEALTHY',
    portfolio_summary: {
      net_worth: currentNetWorth,
      total_invested: totalInvested,
      unrealized_gain: unrealizedGain,
      unrealized_gain_pct: unrealizedGainPct,
      xirr_percentage: syncInfo.xirr_percentage || '—',
      formatted_xirr: syncInfo.xirr_percentage || '—',
      unallocated_cash: Number(syncInfo.unallocated_cash || 0),
      ltcg_exemption_remaining: Number(syncInfo.ltcg_exemption_remaining ?? 125000),
      ltcg_exemption_used: Number(syncInfo.ltcg_exemption_used ?? 0),
      ltcg_exemption_limit: 125000
    },
    rebalance_plan: rebalancePlan,
    networth_trend: {
      dates: trendDates,
      net_worth: trendValues,
      invested: trendInvested
    },
    holdings: normalizedHoldings,
    sync_info: {
      status: syncInfo.status || 'HEALTHY',
      last_sync_timestamp: syncInfo.generated_at || '',
      stale_nav_count: Number(syncInfo.stale_nav_count || 0),
      ledger_hmac_verified: Boolean(syncInfo.ledger_hmac_verified)
    },
    quiet_mode: Boolean(syncInfo.quiet_mode)
  };
}
