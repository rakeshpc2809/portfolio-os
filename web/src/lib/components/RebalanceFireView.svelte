<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchJson, fetchActionRecommendations, type RebalancePlanDto, type ActionRecommendationDto } from '../api';
  import { formatINR, formatPercent } from '../formatters';
  import { 
    Flame, 
    Sliders, 
    ShieldCheck, 
    TrendingUp, 
    AlertCircle, 
    CheckCircle2, 
    ArrowUpRight, 
    ArrowDownRight, 
    Coins, 
    Sparkles, 
    Target,
    Activity,
    Compass
  } from '@lucide/svelte';

  interface Props {
    rebalancePlan?: RebalancePlanDto;
    quietMode?: boolean;
    onopenlumpsum?: () => void;
  }

  let { rebalancePlan, quietMode = false, onopenlumpsum }: Props = $props();

  interface FireSummary {
    active_scenario_label?: string;
    monthly_expense_today?: string;
    annual_expense?: string;
    required_corpus?: string;
    total_net_worth?: string;
    fire_investable_net_worth?: string;
    projected_corpus_at_target_age?: string;
    years_remaining?: number;
    status?: string;
    shortage_or_surplus_amount?: string;
    monte_carlo_success_rate_pct?: number;
    monte_carlo_median_corpus?: string;
    monte_carlo_data_source_label?: string;
    scenarios?: Array<{
      scenario_id: string;
      label: string;
      target_corpus: string;
      monthly_expense: string;
      swr_pct: string;
    }>;
  }

  interface HrpAuditDto {
    status: string;
    trading_days?: number;
    start_date?: string;
    end_date?: string;
    allocations?: Array<{
      isin: string;
      name: string;
      bucket: string;
      target_pct: number;
      hrp_pct: number;
      drift_pct: number;
    }>;
  }

  let fireData = $state<FireSummary | null>(null);
  let loadingFire = $state(false);
  let actionRecommendations = $state<ActionRecommendationDto[]>([]);
  let loadingRecommendations = $state(false);
  let hrpData = $state<HrpAuditDto | null>(null);
  let showHrpComparison = $state(false);

  async function loadFireSummary() {
    loadingFire = true;
    try {
      fireData = await fetchJson<FireSummary>('/fire/summary');
    } catch {
      // fallback gracefully if endpoint unavailable
    } finally {
      loadingFire = false;
    }
  }

  async function loadActionRecommendations() {
    loadingRecommendations = true;
    try {
      actionRecommendations = await fetchActionRecommendations();
    } catch {
      // fallback gracefully
    } finally {
      loadingRecommendations = false;
    }
  }

  async function loadHrpAudit() {
    try {
      hrpData = await fetchJson<HrpAuditDto>('/analytics/hrp-audit');
    } catch {
      // fallback gracefully
    }
  }

  onMount(() => {
    loadFireSummary();
    loadActionRecommendations();
    loadHrpAudit();
  });
</script>

<div class="rebalance-fire-view">
  <!-- Top Action Row -->
  <div class="action-banner">
    <div class="banner-left">
      <div class="plan-trigger-badge">
        <Sliders size={14} />
        <span>PLAN TRIGGER: {rebalancePlan?.trigger?.reason_label || 'DRIFT RESTORATION'}</span>
      </div>
      <p class="plan-desc">
        Deterministic rebalancing algorithm with tax-loss harvesting and Sec 112A annual exemption shield.
      </p>
    </div>
    <button 
      type="button" 
      class="lumpsum-cta-btn" 
      onclick={onopenlumpsum}
    >
      <Coins size={16} />
      <span>Deploy Fresh Lump-Sum Inflow</span>
    </button>
  </div>

  <!-- Rebalance Summary Grid -->
  <div class="rebalance-grid">
    <!-- Buy-Side Inflow Card -->
    <div class="plan-card buy-card">
      <div class="card-header">
        <div class="header-tag buy">BUY-SIDE DEPLOYMENT</div>
        <h3 class="card-title">Capital Routing</h3>
      </div>
      <div class="card-main-metric">
        <span class="metric-label">Total to Deploy</span>
        <span class="metric-val mono bold text-emerald">
          {formatINR(rebalancePlan?.buy_side?.total_to_invest ?? 0, quietMode)}
        </span>
      </div>

      <div class="orders-list">
        <div class="orders-header-row">
          <span class="orders-title">Target Bucket Allocation:</span>
          <button 
            type="button" 
            class="hrp-toggle-pill" 
            class:active={showHrpComparison}
            onclick={() => showHrpComparison = !showHrpComparison}
          >
            <Compass size={12} />
            <span>{showHrpComparison ? 'Hide HRP (95% CVaR)' : 'Compare HRP (95% CVaR)'}</span>
          </button>
        </div>
        {#if rebalancePlan?.buy_side?.buckets && rebalancePlan.buy_side.buckets.length > 0}
          {#each rebalancePlan.buy_side.buckets as b}
            {@const alloc = b.amount_allocated ?? 0}
            {@const bNameUpper = b.bucket.toUpperCase().replace(/\s+/g, '_')}
            {@const hrpAlloc = hrpData?.allocations?.filter(a => a.bucket.toUpperCase() === bNameUpper || bNameUpper.includes(a.bucket.toUpperCase()))}
            {@const hrpPct = hrpAlloc && hrpAlloc.length > 0 ? hrpAlloc.reduce((acc, curr) => acc + curr.hrp_pct, 0) : null}
            <div class="order-row">
              <div class="order-info">
                <span class="bucket-name font-medium">{b.bucket}</span>
                <span class="pct-detail mono text-muted">
                  {formatPercent(b.current_pct)} → <strong class="text-cyan">{formatPercent(b.post_pct ?? b.target_pct)}</strong>
                  {#if showHrpComparison && hrpPct !== null}
                    <span class="hrp-badge mono">HRP CVaR: <strong class="text-emerald">{hrpPct.toFixed(1)}%</strong></span>
                  {/if}
                </span>
              </div>
              <div class="order-amount mono bold" class:allocated={alloc > 0}>
                {alloc > 0 ? `+${formatINR(alloc, quietMode)}` : '₹0'}
              </div>
            </div>
          {/each}
        {:else}
          <p class="empty-note">All buckets currently within nominal tolerance bounds.</p>
        {/if}
      </div>
    </div>

    <!-- Sell-Side & Tax Impact Card -->
    <div class="plan-card tax-card">
      <div class="card-header">
        <div class="header-tag tax">TAX SHIELD & LIQUIDATION</div>
        <h3 class="card-title">Execution Tax Drag</h3>
      </div>

      <div class="tax-metrics-row">
        <div class="tax-metric">
          <span class="tax-label">Sec 112A Exempt Harvest</span>
          <span class="tax-val mono bold text-emerald">
            {formatINR(rebalancePlan?.summary?.ltcg_exempt_gain ?? 0, quietMode)}
          </span>
        </div>
        <div class="tax-metric">
          <span class="tax-label">STCG Taxable Gain</span>
          <span class="tax-val mono text-muted">
            {formatINR(rebalancePlan?.summary?.stcg_taxable_gain ?? 0, quietMode)}
          </span>
        </div>
        <div class="tax-metric">
          <span class="tax-label">Projected Total Tax Drag</span>
          <span class="tax-val mono bold text-cyan">
            {formatINR(rebalancePlan?.summary?.projected_tax ?? 0, quietMode)}
          </span>
        </div>
      </div>

      <div class="tax-rule-note">
        <ShieldCheck size={16} class="text-emerald" />
        <span>
          Liquidation engine strictly exhausts <strong>Tier-1 Exempt LTCG</strong> up to ₹1.25 Lakh before incurring any 12.5% LTCG drag.
        </span>
      </div>
    </div>
  </div>

  <!-- FIRE & Retirement Section -->
  <div class="fire-section">
    <div class="section-title-bar">
      <div class="title-with-icon">
        <Flame size={20} class="text-amber" />
        <h2 class="fire-title">Financial Independence (FIRE) Engine</h2>
      </div>
      {#if fireData?.monte_carlo_success_rate_pct !== undefined}
        <div class="mc-badge">
          <Activity size={14} />
          <span>MONTE CARLO PROBABILITY: <strong class="mono bold text-emerald">{fireData.monte_carlo_success_rate_pct.toFixed(1)}%</strong></span>
        </div>
      {/if}
    </div>

    {#if fireData}
      <div class="fire-kpi-grid">
        <div class="fire-kpi-card">
          <span class="kpi-label">Active Scenario</span>
          <span class="kpi-val bold text-cyan">{fireData.active_scenario_label || 'Standard FIRE'}</span>
          <span class="kpi-sub mono text-muted">SWR: 3.50%</span>
        </div>

        <div class="fire-kpi-card">
          <span class="kpi-label">Monthly Expense Today</span>
          <span class="kpi-val mono bold">{quietMode ? '••••••••' : `₹${fireData.monthly_expense_today || '—'}`}</span>
          <span class="kpi-sub">Annual: {quietMode ? '••••••••' : `₹${fireData.annual_expense || '—'}`}</span>
        </div>

        <div class="fire-kpi-card">
          <span class="kpi-label">Target Corpus Required</span>
          <span class="kpi-val mono bold text-amber">{quietMode ? '••••••••' : `₹${fireData.required_corpus || '—'}`}</span>
          <span class="kpi-sub mono">{fireData.years_remaining ?? '—'} years remaining</span>
        </div>

        <div class="fire-kpi-card highlight">
          <span class="kpi-label">Projected Corpus at Target</span>
          <span class="kpi-val mono bold text-emerald">{quietMode ? '••••••••' : `₹${fireData.projected_corpus_at_target_age || '—'}`}</span>
          <span class="kpi-sub surplus">
            {fireData.status === 'ON_TRACK' ? '✓ ' : ''}{fireData.shortage_or_surplus_amount || 'On Track'}
          </span>
        </div>
      </div>

      <!-- Decumulation Guardrails & Action Rules Grid -->
      {#if actionRecommendations && actionRecommendations.length > 0}
        <div class="guardrails-panel">
          <div class="guardrails-header">
            <div class="guardrails-header-left">
              <ShieldCheck size={16} class="text-cyan" />
              <span class="guardrails-heading">Decumulation Guardrails & Action Rules</span>
            </div>
            <span class="guardrails-badge">RULE ENGINE v4.1</span>
          </div>
          <div class="guardrails-grid">
            {#each actionRecommendations as rec}
              <div class="guardrail-card {rec.severity?.toLowerCase() || 'info'}">
                <div class="gr-top">
                  <span class="gr-category">{rec.category?.replace(/_/g, ' ') || 'ACTION RULE'}</span>
                  <span class="gr-status-pill {rec.severity?.toLowerCase() || 'info'}">
                    {rec.status?.replace(/_/g, ' ') || 'INFORMATIONAL'}
                  </span>
                </div>
                <h4 class="gr-title">{rec.title}</h4>
                <p class="gr-summary">{rec.summary}</p>
                {#if rec.metrics && Object.keys(rec.metrics).length > 0}
                  <div class="gr-metrics">
                    {#each Object.entries(rec.metrics) as [k, v]}
                      <div class="gr-metric-item">
                        <span class="gr-metric-k">{k.replace(/_/g, ' ')}:</span>
                        <span class="gr-metric-v mono">{typeof v === 'number' ? (Number.isInteger(v) ? v : v.toFixed(2)) : v}</span>
                      </div>
                    {/each}
                  </div>
                {/if}
                {#if rec.provenance_footer || rec.provenanceFooter}
                  <div class="gr-footer">{rec.provenance_footer || rec.provenanceFooter}</div>
                {/if}
              </div>
            {/each}
          </div>
        </div>
      {/if}

      <!-- Scenarios Selector -->
      {#if fireData.scenarios && fireData.scenarios.length > 0}
        <div class="scenarios-panel">
          <span class="scenarios-heading">Retirement Scenarios:</span>
          <div class="scenarios-grid">
            {#each fireData.scenarios as sc}
              <div class="scenario-card" class:active={sc.label === fireData.active_scenario_label}>
                <div class="sc-header">
                  <span class="sc-label font-medium">{sc.label}</span>
                  <span class="sc-swr mono">{sc.swr_pct}% SWR</span>
                </div>
                <div class="sc-corpus mono bold text-cyan">
                  {quietMode ? '••••••••' : `₹${sc.target_corpus}`}
                </div>
                <div class="sc-monthly text-muted">
                  {quietMode ? '••••••••' : `₹${sc.monthly_expense}/mo`}
                </div>
              </div>
            {/each}
          </div>
        </div>
      {/if}
    {:else if loadingFire}
      <div class="fire-loading">
        <Activity size={24} class="spin" />
        <p>Projecting Monte Carlo paths...</p>
      </div>
    {/if}
  </div>
</div>

<style>
  .rebalance-fire-view {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
  }

  .action-banner {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 1rem 1.25rem;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    gap: 1rem;
    flex-wrap: wrap;
  }

  .banner-left {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
  }

  .plan-trigger-badge {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    font-size: 0.72rem;
    font-family: var(--font-mono);
    color: var(--accent-cyan);
    font-weight: 700;
  }

  .plan-desc {
    font-size: 0.8rem;
    color: var(--text-muted);
    margin: 0;
  }

  .lumpsum-cta-btn {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.55rem 1.2rem;
    background: var(--surface-glass);
    border: 1px solid var(--accent-cyan);
    border-radius: var(--radius-md);
    color: var(--accent-cyan);
    font-size: 0.85rem;
    font-weight: 600;
    cursor: pointer;
    transition: all var(--transition-fast);
  }

  .lumpsum-cta-btn:hover {
    background: var(--accent-cyan);
    color: var(--surface-void);
    box-shadow: 0 0 16px var(--glow-cyan);
  }

  /* Grid */
  .rebalance-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1.25rem;
  }

  .plan-card {
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  .card-header {
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
  }

  .header-tag {
    font-size: 0.65rem;
    font-family: var(--font-mono);
    letter-spacing: 0.06em;
    font-weight: 700;
  }

  .header-tag.buy { color: var(--accent-emerald); }
  .header-tag.tax { color: var(--accent-cyan); }

  .card-title {
    font-size: 1.1rem;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0;
  }

  .card-main-metric {
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
    padding: 0.85rem 1rem;
    background: var(--surface-void);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
  }

  .metric-label {
    font-size: 0.72rem;
    text-transform: uppercase;
    color: var(--text-muted);
  }

  .metric-val {
    font-size: 1.4rem;
  }

  .orders-list {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .orders-header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .hrp-toggle-pill {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.2rem 0.6rem;
    font-size: 0.68rem;
    font-family: var(--font-mono, monospace);
    background: var(--surface-void);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    color: var(--text-muted);
    cursor: pointer;
    transition: all var(--transition-fast, 0.15s ease);
  }

  .hrp-toggle-pill:hover {
    border-color: var(--accent-emerald);
    color: var(--accent-emerald);
  }

  .hrp-toggle-pill.active {
    background: hsla(158, 85%, 45%, 0.12);
    border-color: var(--accent-emerald);
    color: var(--accent-emerald);
  }

  .hrp-badge {
    margin-left: 0.5rem;
    font-size: 0.68rem;
    padding: 0.1rem 0.35rem;
    background: hsla(158, 85%, 45%, 0.1);
    border-radius: var(--radius-sm);
    border: 1px solid hsla(158, 85%, 45%, 0.2);
  }

  .orders-title {
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-muted);
  }

  .order-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.6rem 0.75rem;
    background: var(--surface-void);
    border-radius: var(--radius-md);
  }

  .order-info {
    display: flex;
    flex-direction: column;
    gap: 0.15rem;
  }

  .bucket-name {
    font-size: 0.85rem;
    color: var(--text-primary);
  }

  .pct-detail {
    font-size: 0.75rem;
  }

  .order-amount {
    font-size: 0.95rem;
    color: var(--text-secondary);
  }

  .order-amount.allocated {
    color: var(--accent-emerald);
  }

  /* Tax Card */
  .tax-metrics-row {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0.6rem;
  }

  .tax-metric {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.6rem 0.85rem;
    background: var(--surface-void);
    border-radius: var(--radius-md);
  }

  .tax-label {
    font-size: 0.8rem;
    color: var(--text-secondary);
  }

  .tax-val {
    font-size: 0.95rem;
  }

  .tax-rule-note {
    display: flex;
    align-items: flex-start;
    gap: 0.65rem;
    padding: 0.75rem 1rem;
    background: hsla(158, 85%, 45%, 0.08);
    border: 1px solid hsla(158, 85%, 45%, 0.25);
    border-radius: var(--radius-md);
    color: var(--text-secondary);
    font-size: 0.8rem;
    line-height: 1.4;
  }

  /* FIRE Section */
  .fire-section {
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    padding: 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 1.25rem;
  }

  .section-title-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .title-with-icon {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .fire-title {
    font-size: 1.15rem;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0;
  }

  .mc-badge {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.25rem 0.65rem;
    background: hsla(158, 85%, 45%, 0.12);
    border: 1px solid hsla(158, 85%, 45%, 0.3);
    border-radius: var(--radius-full);
    font-size: 0.75rem;
    color: var(--text-secondary);
  }

  .fire-kpi-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 1rem;
  }

  .fire-kpi-card {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
    padding: 1rem;
    background: var(--surface-void);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
  }

  .fire-kpi-card.highlight {
    border-color: hsla(158, 85%, 45%, 0.35);
    box-shadow: 0 0 20px rgba(0, 230, 153, 0.08);
  }

  .kpi-label {
    font-size: 0.72rem;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-muted);
  }

  .kpi-val {
    font-size: 1.25rem;
    color: var(--text-primary);
  }

  .kpi-sub {
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .kpi-sub.surplus {
    color: var(--accent-emerald);
    font-weight: 600;
  }

  .scenarios-panel {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    padding-top: 0.5rem;
    border-top: 1px solid var(--border-subtle);
  }

  .scenarios-heading {
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-muted);
  }

  .scenarios-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 0.75rem;
  }

  .scenario-card {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    padding: 0.75rem 1rem;
    background: var(--surface-void);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
  }

  .scenario-card.active {
    border-color: var(--accent-cyan);
    background: hsla(185, 85%, 52%, 0.08);
  }

  .sc-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .sc-label {
    font-size: 0.82rem;
    color: var(--text-primary);
  }

  .sc-swr {
    font-size: 0.7rem;
    color: var(--text-muted);
  }

  .sc-corpus {
    font-size: 1.05rem;
  }

  .sc-monthly {
    font-size: 0.75rem;
  }

  .empty-note {
    font-size: 0.8rem;
    color: var(--text-muted);
    font-style: italic;
  }

  .fire-loading {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 2.5rem;
    color: var(--text-muted);
    font-size: 0.85rem;
  }

  .text-cyan { color: var(--accent-cyan); }
  .text-emerald { color: var(--accent-emerald); }
  .text-amber { color: var(--accent-amber); }

  .guardrails-panel {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    padding-top: 0.75rem;
    border-top: 1px solid var(--border-subtle);
  }

  .guardrails-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .guardrails-header-left {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .guardrails-heading {
    font-size: 0.78rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-primary);
  }

  .guardrails-badge {
    font-size: 0.65rem;
    font-family: var(--font-mono, monospace);
    padding: 0.15rem 0.5rem;
    border-radius: var(--radius-sm);
    background: hsla(185, 85%, 52%, 0.12);
    color: var(--accent-cyan);
    border: 1px solid hsla(185, 85%, 52%, 0.25);
  }

  .guardrails-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 0.85rem;
  }

  .guardrail-card {
    display: flex;
    flex-direction: column;
    gap: 0.45rem;
    padding: 0.9rem 1rem;
    background: var(--surface-void);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    transition: transform 0.15s ease, border-color 0.15s ease;
  }

  .guardrail-card:hover {
    border-color: var(--border-prominent);
    transform: translateY(-1px);
  }

  .guardrail-card.high, .guardrail-card.alert {
    border-color: hsla(0, 85%, 60%, 0.4);
    box-shadow: 0 0 15px rgba(239, 68, 68, 0.08);
  }

  .guardrail-card.medium, .guardrail-card.warning {
    border-color: hsla(38, 92%, 50%, 0.35);
  }

  .gr-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .gr-category {
    font-size: 0.68rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-muted);
  }

  .gr-status-pill {
    font-size: 0.65rem;
    font-weight: 600;
    padding: 0.1rem 0.4rem;
    border-radius: 9999px;
    background: var(--surface-elevated);
    color: var(--text-muted);
    border: 1px solid var(--border-subtle);
  }

  .gr-status-pill.high, .gr-status-pill.alert {
    background: hsla(0, 85%, 60%, 0.15);
    color: #f87171;
    border-color: hsla(0, 85%, 60%, 0.3);
  }

  .gr-status-pill.medium, .gr-status-pill.warning {
    background: hsla(38, 92%, 50%, 0.15);
    color: var(--accent-amber);
    border-color: hsla(38, 92%, 50%, 0.3);
  }

  .gr-title {
    margin: 0;
    font-size: 0.92rem;
    font-weight: 600;
    color: var(--text-primary);
  }

  .gr-summary {
    margin: 0;
    font-size: 0.78rem;
    line-height: 1.4;
    color: var(--text-secondary);
  }

  .gr-metrics {
    display: flex;
    flex-wrap: wrap;
    gap: 0.4rem 0.8rem;
    padding: 0.4rem 0.6rem;
    background: var(--surface-base, rgba(0,0,0,0.2));
    border-radius: var(--radius-sm);
    font-size: 0.72rem;
    margin-top: 0.2rem;
  }

  .gr-metric-item {
    display: flex;
    align-items: center;
    gap: 0.25rem;
  }

  .gr-metric-k {
    color: var(--text-muted);
    text-transform: capitalize;
  }

  .gr-metric-v {
    color: var(--accent-cyan);
    font-weight: 600;
  }

  .gr-footer {
    font-size: 0.65rem;
    color: var(--text-muted);
    font-style: italic;
    margin-top: 0.2rem;
  }

  @media (max-width: 900px) {
    .rebalance-grid {
      grid-template-columns: 1fr;
    }
    .fire-kpi-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  }
</style>
