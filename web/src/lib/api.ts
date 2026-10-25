/**
 * Portfolio OS - API Client & Type Definitions
 */

export interface DailyBriefDto {
  status: string;
  headline: string;
  narration: string;
  verified: boolean;
  generated_at?: string;
  metrics?: Record<string, any>;
}

export interface SnapshotDto {
  generated_at?: string;
  data_freshness_status?: string;
  portfolio_summary?: PortfolioSummaryDto;
  rebalance_plan?: RebalancePlanDto;
  networth_trend?: NetworthTrendDto;
  holdings?: HoldingDto[];
  sync_info?: SyncInfoDto;
  quiet_mode?: boolean;
  daily_brief?: DailyBriefDto;
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
  days_to_ltcg?: number;
  is_harvest_candidate?: boolean;
  estimated_tax_drag?: number;
}

export interface FundAllocationOrderDto {
  fund_id: string;
  fund_name: string;
  amount: number;
  current_pct?: number;
  post_pct?: number;
  pre_valuation?: number;
  post_valuation?: number;
}

export interface RebalanceOrderDto {
  fund_id: string;
  fund_name: string;
  total_proceeds: number;
  total_units?: number;
  total_realized_gain?: number;
  total_exemption_applied?: number;
  total_tax_amount?: number;
  tax_classification?: string;
  summary_label?: string;
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
  orders?: RebalanceOrderDto[];
  buckets?: BucketAllocationDto[];
  buy_side?: {
    total_to_invest: number;
    is_manual_lumpsum: boolean;
    buckets: BucketAllocationDto[];
  };
  sell_side?: {
    total_required: number;
    waterfall_tiers: any[];
    orders?: RebalanceOrderDto[];
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
  fund_breakdown?: FundAllocationOrderDto[];
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

export interface ActionRecommendationDto {
  card_id?: string;
  cardId?: string;
  category: string;
  title: string;
  status: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO' | string;
  summary: string;
  detailed_rationale?: string;
  detailedRationale?: string;
  metrics?: Record<string, any>;
  provenance_footer?: string;
  provenanceFooter?: string;
}

export async function fetchActionRecommendations(): Promise<ActionRecommendationDto[]> {
  return fetchJson<ActionRecommendationDto[]>('/rules/action-recommendations');
}

export interface UpSetStockItem {
  stock_symbol: string;
  min_weight: number;
  total_weight: number;
}

export interface UpSetCombination {
  combination_key: string;
  participating_funds: string[];
  stock_count: number;
  total_overlap_weight: number;
  stocks: UpSetStockItem[];
}

export interface MultiFundUpSetResponse {
  status: string;
  holding_coverage_type: string;
  constituent_source: string;
  disclosure_date: string;
  is_live_download: boolean;
  upset_combinations: UpSetCombination[];
  evaluated_funds: string[];
}

export async function fetchMultiFundUpSetAnalytics(): Promise<MultiFundUpSetResponse> {
  return fetchJson<MultiFundUpSetResponse>('/analytics/overlap/upset');
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
  const snapshotRes = await fetchJson<any>('/sync/snapshot');
  if (!snapshotRes) {
    throw new Error('Could not connect to Portfolio OS backend. Please check if portfolio-os-core is running.');
  }

  const syncInfo = snapshotRes?.sync_info || {};
  const currentNetWorth = Number(syncInfo.current_value) || 0;
  const totalInvested = Number(syncInfo.total_invested) || 0;
  const unrealizedGain = Number(syncInfo.unrealized_gain) || (currentNetWorth - totalInvested);
  const unrealizedGainPct = Number(syncInfo.unrealized_gain_pct) || (totalInvested > 0 ? (unrealizedGain / totalInvested) * 100 : 0);

  // Group tax lots by ISIN so each holding has its nested lots
  const rawTaxLots = snapshotRes?.tax_lots || [];
  const lotsByIsin: Record<string, any[]> = {};
  for (const lot of rawTaxLots) {
    const isin = lot.isin || lot.asset_id;
    if (isin) {
      if (!lotsByIsin[isin]) lotsByIsin[isin] = [];
      lotsByIsin[isin].push(lot);
    }
  }

  // Normalize holdings & lots
  const rawHoldings = snapshotRes?.holdings || [];
  const normalizedHoldings: HoldingDto[] = rawHoldings.map((h: any) => {
    const isin = h.isin || h.asset_id || '';
    const name = h.asset_name || h.fund_name || isin;
    const bucket = h.category || h.asset_bucket || 'EQUITY_CORE';
    const cVal = Number(h.current_value) || 0;
    const cBasis = Number(h.invested_value) || 0;
    const uGain = Number(h.unrealized_gain) || (cVal - cBasis);
    const uGainPct = Number(h.unrealized_gain_pct) || (cBasis > 0 ? (uGain / cBasis) * 100 : 0);
    const currentNav = Number(h.current_nav || 0);

    const holdingLots = (h.lots && h.lots.length > 0) ? h.lots : (lotsByIsin[isin] || []);

    const lots: LotDto[] = holdingLots.map((l: any, idx: number) => {
      const u = Number(l.remaining_units ?? l.units ?? 0);
      const cNav = Number(l.cost_per_unit ?? l.cost_nav ?? 0);
      const basis = Number(l.total_cost_basis ?? l.cost_basis ?? (u * cNav));
      const val = Number(l.current_value ?? (currentNav > 0 ? u * currentNav : basis));
      const gain = Number(l.unrealized_gain ?? l.realized_or_unrealized_gain ?? (val - basis));

      return {
        lot_id: l.lot_id || `${isin}-${l.buy_date || l.acquisition_date || idx}-${u}`,
        acquisition_date: l.acquisition_date || l.buy_date || '',
        units: u,
        cost_nav: cNav,
        cost_basis: basis,
        current_value: val,
        realized_or_unrealized_gain: gain,
        tax_term: (l.is_ltcg || l.is_long_term || l.tax_classification?.includes('LTCG')) ? 'LONG_TERM' : 'SHORT_TERM',
        is_grandfathered: Boolean(l.grandfathered_nav),
        fmv_2018: l.grandfathered_nav ? Number(l.grandfathered_nav) : undefined,
        holding_period_days: Number(l.holding_days ?? l.holding_period_days ?? 0),
        days_to_ltcg: Number(l.days_to_ltcg ?? 0),
        is_harvest_candidate: Boolean(l.is_harvest_candidate),
        estimated_tax_drag: Number(l.estimated_tax_drag ?? 0)
      };
    });

    return {
      asset_id: isin,
      isin: isin,
      asset_name: name,
      bucket: bucket,
      current_units: Number(h.total_units || 0),
      current_nav: currentNav || lots[0]?.cost_nav || 0,
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
  const rawBuckets = snapshotRes?.bucket_allocation || [];
  const normalizedBuckets: BucketAllocationDto[] = rawBuckets.map((b: any) => {
    const rawBucketName = String(b.bucket || '');
    let funds = (b.funds || []).map((f: any) => ({
      ...f,
      fund_name: f.fund_name || f.asset_name || f.name || f.isin,
      asset_name: f.fund_name || f.asset_name || f.name || f.isin,
      isin: f.isin || f.asset_id,
      current_value: Number(f.current_value || f.value || 0),
      current_nav: Number(f.current_nav || f.nav || 0),
      total_units: Number(f.total_units || f.units || 0),
      invested_value: Number(f.invested_value || f.cost_basis || 0),
      unrealized_gain: Number(f.unrealized_gain || 0),
      gain_pct: Number(f.gain_pct || 0)
    }));

    // If backend did not provide nested funds for this bucket, match from normalizedHoldings
    if (funds.length === 0) {
      const matchingHoldings = normalizedHoldings.filter(h => {
        const hBucket = (h.bucket || '').toUpperCase();
        const bName = rawBucketName.toUpperCase();
        return hBucket === bName || 
          (bName === 'GOLD_SILVER' && (hBucket.includes('GOLD') || hBucket.includes('SILVER'))) ||
          (bName === 'LIQUID_BUFFER' && (hBucket.includes('LIQUID') || hBucket.includes('ARBITRAGE'))) ||
          (bName === 'EQUITY_CORE' && hBucket.includes('CORE')) ||
          (bName === 'EQUITY_SATELLITE' && hBucket.includes('SATELLITE')) ||
          (bName === 'LEGACY_HOLDINGS' && hBucket.includes('LEGACY'));
      });

      funds = matchingHoldings.map(h => ({
        fund_name: h.asset_name,
        asset_name: h.asset_name,
        isin: h.isin,
        current_value: h.current_value,
        current_nav: h.current_nav,
        total_units: h.current_units,
        invested_value: h.cost_basis,
        unrealized_gain: h.unrealized_gain,
        gain_pct: h.unrealized_gain_pct,
        lots: h.lots
      }));
    }

    return {
      bucket: rawBucketName,
      target_pct: Number(b.target_pct || 0),
      current_pct: Number(b.current_pct || 0),
      current_value: Number(b.current_value || 0),
      is_drifted: Boolean(b.is_drifted),
      funds: funds
    };
  });

  // Rebalance plan with normalized buy-side buckets & fund breakdowns
  const rawBuyBuckets = snapshotRes?.rebalance_plan?.buy_side?.buckets || [];
  const normalizedBuyBuckets: BucketAllocationDto[] = rawBuyBuckets.length > 0
    ? rawBuyBuckets.map((b: any) => ({
        bucket: b.bucket || '',
        target_pct: Number(b.target_pct || 0),
        current_pct: Number(b.current_pct || 0),
        post_pct: Number(b.post_rebalance_pct ?? b.post_pct ?? b.target_pct ?? 0),
        amount_allocated: Number(b.amount_allocated || 0),
        fund_breakdown: (b.fund_breakdown || []).map((f: any) => ({
          fund_id: f.fund_id || f.isin || '',
          fund_name: f.fund_name || f.name || '',
          amount: Number(f.amount || 0),
          current_pct: Number(f.current_pct || 0),
          post_pct: Number(f.post_pct || 0),
          pre_valuation: Number(f.pre_valuation || 0),
          post_valuation: Number(f.post_valuation || 0)
        }))
      }))
    : normalizedBuckets;

  const rebalancePlan: RebalancePlanDto = {
    ...(snapshotRes?.rebalance_plan || {}),
    orders: snapshotRes?.rebalance_plan?.orders || snapshotRes?.rebalance_plan?.sell_side?.orders || [],
    buckets: normalizedBuckets,
    buy_side: snapshotRes?.rebalance_plan?.buy_side ? {
      ...snapshotRes.rebalance_plan.buy_side,
      buckets: normalizedBuyBuckets
    } : undefined
  };

  // Trend data from unified snapshot
  const history = snapshotRes?.net_worth_history || [];
  const trendDates = history.length > 0 ? history.map((p: any) => p.date) : [syncInfo.generated_at?.split('T')?.[0] || '2026-10-24'];
  const trendValues = history.length > 0 ? history.map((p: any) => p.valuation) : [currentNetWorth];
  const trendInvested = history.length > 0 ? history.map((p: any) => p.invested) : [totalInvested];

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
    quiet_mode: Boolean(syncInfo.quiet_mode),
    daily_brief: snapshotRes?.daily_brief || undefined
  };
}

export async function fetchParquetTable(table: string = 'fund_holdings'): Promise<ArrayBuffer> {
  const token = getAuthToken();
  const headers = new Headers();
  if (token) headers.set('X-Api-Auth-Token', token);
  const resp = await fetch(`/api/v1/sync/parquet?table=${encodeURIComponent(table)}`, { headers });
  if (!resp.ok) {
    throw new Error(`Failed to fetch parquet table ${table}: ${resp.statusText}`);
  }
  return resp.arrayBuffer();
}

export async function fetchParquetBundle(): Promise<ArrayBuffer> {
  const token = getAuthToken();
  const headers = new Headers();
  if (token) headers.set('X-Api-Auth-Token', token);
  const resp = await fetch('/api/v1/sync/parquet-bundle', { headers });
  if (!resp.ok) {
    throw new Error(`Failed to fetch parquet bundle: ${resp.statusText}`);
  }
  return resp.arrayBuffer();
}

export interface FactorBenchmarkMetric {
  isin: string;
  fundName: string;
  benchmarkId: string;
  benchmarkName: string;
  beta: number;
  trackingErrorPct: number;
  alphaPct: number;
  informationRatio: number;
  outperformancePct: number;
  sampleStatus: string;
  sampleDays: number;
}

export interface FactorBenchmarksResponse {
  status: string;
  as_of: string;
  count: number;
  factors: FactorBenchmarkMetric[];
}

export async function fetchFactorBenchmarks(): Promise<FactorBenchmarksResponse> {
  const token = getAuthToken();
  const headers = new Headers();
  if (token) headers.set('X-Api-Auth-Token', token);
  const resp = await fetch('/api/v1/analytics/factor-benchmarks', { headers });
  if (!resp.ok) {
    throw new Error(`Failed to fetch factor benchmarks: ${resp.statusText}`);
  }
  const data = await resp.json();
  const rawList = Array.isArray(data?.factors) ? data.factors : [];
  const factors: FactorBenchmarkMetric[] = rawList.map((f: any) => ({
    isin: String(f.isin || ''),
    fundName: String(f.fundName || f.fund_name || ''),
    benchmarkId: String(f.benchmarkId || f.benchmark_id || 'NIFTY_500_TRI'),
    benchmarkName: String(f.benchmarkName || f.benchmark_name || 'Standard Factor Benchmark'),
    beta: Number(f.beta ?? 1.0),
    trackingErrorPct: Number(f.trackingErrorPct ?? f.tracking_error_pct ?? 0),
    alphaPct: Number(f.alphaPct ?? f.alpha_pct ?? 0),
    informationRatio: Number(f.informationRatio ?? f.information_ratio ?? 0),
    outperformancePct: Number(f.outperformancePct ?? f.outperformance_pct ?? 0),
    sampleStatus: String(f.sampleStatus || f.sample_status || 'PROVISIONAL'),
    sampleDays: Number(f.sampleDays ?? f.sample_days ?? 0),
  }));
  return {
    status: data?.status || 'OK',
    as_of: data?.as_of || '',
    count: factors.length,
    factors
  };
}

