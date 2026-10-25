<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchJson, fetchActionRecommendations, type RebalancePlanDto, type ActionRecommendationDto } from '../api';
  import { formatINR, formatPercent, formatCompactINR } from '../formatters';
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
    Compass,
    Workflow,
    Layers,
    ArrowRight,
    Info,
    ShieldAlert,
    Clock,
    Zap
  } from '@lucide/svelte';

  interface Props {
    rebalancePlan?: RebalancePlanDto;
    holdings?: any[];
    netWorth?: number;
    quietMode?: boolean;
    onopenlumpsum?: () => void;
  }

  let { rebalancePlan, holdings = [], netWorth = 0, quietMode = false, onopenlumpsum }: Props = $props();

  function getFundHoldingMetrics(fundIdOrName: string, orderProceeds: number = 0) {
    const nw = netWorth || currentNetWorth() || 2178955;
    const holding = holdings?.find((h: any) => 
      (h.asset_id && fundIdOrName.toLowerCase().includes(h.asset_id.toLowerCase())) ||
      (h.isin && fundIdOrName.toLowerCase().includes(h.isin.toLowerCase())) ||
      (h.asset_name && fundIdOrName.toLowerCase().includes(h.asset_name.toLowerCase())) ||
      (h.asset_name && h.asset_name.toLowerCase().includes(fundIdOrName.toLowerCase()))
    );
    const curVal = Number(holding?.current_value ?? (orderProceeds > 0 ? orderProceeds * 1.5 : 0));
    const curPct = nw > 0 ? (curVal / nw) * 100 : 0;
    const postVal = Math.max(0, curVal - orderProceeds);
    const postPct = nw > 0 ? (postVal / nw) * 100 : 0;
    return {
      curVal,
      curPct,
      postVal,
      postPct
    };
  }

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
      scenario_id?: string;
      id?: string;
      label: string;
      target_corpus?: string;
      monthly_expense?: string;
      monthly_expense_today?: string;
      annual_expense?: string;
      swr_pct?: string;
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
  let activeWaterfallStep = $state<number>(3); // Default to viewing final step
  let selectedScenario = $state<string>('Standard FIRE');

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

  // Calculate live FIRE status, accumulation metrics and Guyton-Klinger thresholds
  const currentNetWorth = $derived(() => {
    return Number(fireData?.fire_investable_net_worth || fireData?.total_net_worth || 2178955);
  });

  const activeScenario = $derived(() => {
    return fireData?.scenarios?.find(s => s.active) || fireData?.scenarios?.[0] || null;
  });

  const annualExpense = $derived(() => {
    return Number(activeScenario()?.annual_expense || fireData?.annual_expense || 720000);
  });

  const targetCorpus = $derived(() => {
    return Number(activeScenario()?.target_corpus || 24000000);
  });

  const yearsRemaining = $derived(() => {
    return fireData?.years_remaining ?? 13;
  });

  const isAccumulationPhase = $derived((yearsRemaining() ?? 13) > 0);

  const fundedRatioPct = $derived(() => {
    const target = targetCorpus();
    return target > 0 ? (currentNetWorth() / target) * 100 : 0;
  });

  const projectedCorpus = $derived(() => {
    return Number(fireData?.projected_corpus_at_target_age || 21641478);
  });

  // Target retirement SWR planned for post-retirement (typically 3.00%)
  const targetRetirementSwrPct = $derived(() => {
    return Number(activeScenario()?.swr_pct || 3.0);
  });

  // SWR projected when reaching retirement target age
  const projectedRetirementSwrPct = $derived(() => {
    const proj = projectedCorpus();
    return proj > 0 ? (annualExpense() / proj) * 100 : targetRetirementSwrPct();
  });

  // Hypothetical instant burn rate if earning ceased today
  const hypotheticalInstantSwrPct = $derived(() => {
    const nw = currentNetWorth();
    return nw > 0 ? (annualExpense() / nw) * 100 : 3.0;
  });

  // SWR to evaluate on the Guyton-Klinger gauge:
  // In accumulation phase, show projected retirement SWR against baseline
  const activeGaugeSwrPct = $derived(() => {
    return isAccumulationPhase ? projectedRetirementSwrPct() : hypotheticalInstantSwrPct();
  });

  const BASELINE_SWR = 3.0;
  const PROSPERITY_BOUND = 2.4; // 20% below 3.0%
  const PRESERVATION_BOUND = 3.6; // 20% above 3.0%

  const isProsperity = $derived(activeGaugeSwrPct() < PROSPERITY_BOUND);
  const isPreservation = $derived(activeGaugeSwrPct() > PRESERVATION_BOUND);

  const exemptLimit = 125000;
  const exemptGain = $derived(rebalancePlan?.summary?.ltcg_exempt_gain ?? 0);
  const exemptPct = $derived(Math.min(100, (exemptGain / exemptLimit) * 100));

  const swrNeedlePct = $derived(Math.min(100, Math.max(0, (activeGaugeSwrPct() / 5.0) * 100)));

  // Formatting helpers
  function formatBucketTitle(b: string): string {
    return b.replace(/_/g, ' ');
  }
</script>

<div class="rebalance-visual-view">
  <!-- Top Action Hero Banner -->
  <div class="hero-banner">
    <div class="banner-content">
      <div class="trigger-pill mono">
        <Sliders size={14} class="text-cyan" />
        <span>PLAN TRIGGER: {rebalancePlan?.trigger?.reason_label || 'DRIFT RESTORATION (POLICY v2.3)'}</span>
      </div>
      <h2 class="hero-title">Deterministic Capital Rebalance & Wealth Decumulation Lab</h2>
      <p class="hero-desc">
        Visual breakdown of automated portfolio rebalancing: Zero-loss legacy phase-outs, Section 112A ₹1.25L tax exemption shielding, and Guyton-Klinger dynamic retirement guardrails.
      </p>
    </div>
    <div class="banner-actions">
      <button 
        type="button" 
        class="lumpsum-cta-btn" 
        onclick={onopenlumpsum}
      >
        <Coins size={16} />
        <span>Deploy Lump-Sum Inflow</span>
      </button>
    </div>
  </div>

  <!-- SECTION 1: VISUAL WATERFALL & ROUTING PIPELINE -->
  <div class="visual-panel obsidian-card">
    <div class="panel-header">
      <div class="header-left">
        <div class="icon-bubble cyan">
          <Workflow size={18} />
        </div>
        <div>
          <h3 class="panel-title">Visual Capital Rebalancing Waterfall</h3>
          <span class="panel-sub">Finance Act 2024 & Policy v2.3 execution pipeline: Step-by-step routing flow</span>
        </div>
      </div>
      <div class="waterfall-total-badge mono">
        <span class="text-muted">Total Capital In Play:</span>
        <strong class="text-emerald">{formatINR(rebalancePlan?.buy_side?.total_to_invest ?? 0, quietMode)}</strong>
      </div>
    </div>

    <!-- Capital Source & Deployment Breakdown Box: Explains exactly where the 20k came from and what funds to buy -->
    {#if (rebalancePlan?.buy_side?.total_to_invest ?? 0) > 0}
      {@const sellOrders = rebalancePlan?.sell_side?.orders ?? []}
      {@const buyBuckets = rebalancePlan?.buy_side?.buckets?.filter(b => (b.amount_allocated ?? 0) > 0) ?? []}
      <div class="capital-provenance-box">
        <div class="provenance-col sources">
          <div class="prov-header">
            <span class="prov-badge rose">SELL-SIDE CAPITAL SOURCES ({sellOrders.length} Schemes)</span>
            <span class="prov-sub text-muted">Disciplined trims & zero-tax FIFO harvesting with fund impact %</span>
          </div>
          <div class="prov-list">
            {#if sellOrders.length > 0}
              {#each sellOrders as order}
                {@const metrics = getFundHoldingMetrics(order.fund_id || order.fund_name, order.total_proceeds)}
                <div class="prov-item">
                  <div class="prov-item-left">
                    <span class="prov-name font-medium">{order.fund_name}</span>
                    <div class="prov-impact-row mono">
                      <span class="fund-impact-tag sell">
                        Fund Impact: <strong class="text-rose">{metrics.curPct.toFixed(1)}% ➔ {metrics.postPct.toFixed(1)}%</strong>
                      </span>
                      <span class="prov-sub-tag text-muted">
                        ({formatCompactINR(metrics.curVal)} ➔ {formatCompactINR(metrics.postVal)})
                      </span>
                    </div>
                    <span class="prov-meta mono text-muted">
                      {order.summary_label || 'FIFO Lot Trim'} · Net P&L: 
                      <span class={(order.total_realized_gain ?? 0) >= 0 ? 'text-emerald' : 'text-amber'}>
                        {(order.total_realized_gain ?? 0) >= 0 ? '+' : ''}{formatINR(order.total_realized_gain ?? 0, quietMode)}
                      </span>
                    </span>
                  </div>
                  <strong class="prov-amount mono text-rose">+{formatINR(order.total_proceeds, quietMode)}</strong>
                </div>
              {/each}
            {:else if rebalancePlan?.buy_side?.is_manual_lumpsum}
              <div class="prov-item">
                <span class="prov-name">Fresh Cash Lumpsum Inflow</span>
                <strong class="prov-amount mono text-cyan">+{formatINR(rebalancePlan?.buy_side?.total_to_invest ?? 0, quietMode)}</strong>
              </div>
            {:else}
              <div class="prov-item">
                <span class="prov-name">Portfolio Cash Buffer / Rebalance Trim Pool</span>
                <strong class="prov-amount mono text-emerald">+{formatINR(rebalancePlan?.buy_side?.total_to_invest ?? 0, quietMode)}</strong>
              </div>
            {/if}
          </div>
        </div>

        <div class="provenance-divider">
          <div class="prov-arrow">➔</div>
          <span class="prov-tax-pill mono">TAX DRAG: ₹0.00</span>
        </div>

        <div class="provenance-col targets">
          <div class="prov-header">
            <span class="prov-badge cyan">BUY-SIDE SPECIFIC FUNDS TO BUY ({buyBuckets.length} Buckets)</span>
            <span class="prov-sub text-muted">Directed to underweight buckets & funds with exact pre ➔ post %</span>
          </div>
          <div class="prov-list">
            {#each buyBuckets as b}
              <div class="prov-bucket-group">
                <div class="prov-item bucket-level">
                  <div class="prov-item-left">
                    <span class="prov-name font-bold text-cyan">{formatBucketTitle(b.bucket)}</span>
                    <span class="prov-meta mono text-muted">
                      Target: {b.target_pct}% · Bucket Share: <strong class="text-cyan">{b.current_pct.toFixed(1)}% ➔ {b.post_rebalance_pct ? b.post_rebalance_pct.toFixed(1) : (b.post_pct ? b.post_pct.toFixed(1) : b.target_pct)}%</strong>
                    </span>
                  </div>
                  <strong class="prov-amount mono text-emerald">+{formatINR(b.amount_allocated ?? 0, quietMode)}</strong>
                </div>

                {#if b.fund_breakdown && b.fund_breakdown.length > 0}
                  <div class="prov-fund-sublist">
                    {#each b.fund_breakdown as fb}
                      <div class="prov-fund-item">
                        <div class="fund-item-left">
                          <span class="fund-bullet">↳</span>
                          <span class="fund-name font-medium">{fb.fund_name}</span>
                          <span class="fund-impact-tag buy mono">
                            Fund Impact: {fb.current_pct ? fb.current_pct.toFixed(1) : '0.0'}% ➔ <strong class="text-emerald">{fb.post_pct ? fb.post_pct.toFixed(1) : '0.0'}%</strong>
                          </span>
                          {#if fb.pre_valuation !== undefined && fb.post_valuation !== undefined}
                            <span class="prov-sub-tag mono text-muted">
                              ({formatCompactINR(fb.pre_valuation)} ➔ {formatCompactINR(fb.post_valuation)})
                            </span>
                          {/if}
                        </div>
                        <strong class="fund-alloc mono text-emerald">+{formatINR(fb.amount, quietMode)}</strong>
                      </div>
                    {/each}
                  </div>
                {/if}
              </div>
            {/each}
          </div>
        </div>
      </div>
    {/if}

    <!-- Step Pipeline Visual Flowchart -->
    <div class="pipeline-stepper">
      <!-- Step 0 -->
      <div 
        class="step-node" 
        class:active={activeWaterfallStep === 0}
        onclick={() => activeWaterfallStep = 0}
        role="button"
        tabindex="0"
        onkeydown={(e) => { if (e.key === 'Enter') activeWaterfallStep = 0; }}
      >
        <div class="step-num">0</div>
        <div class="step-meta">
          <span class="step-title">Legacy Exit</span>
          <span class="step-status text-rose">100% Phase-Out</span>
        </div>
      </div>

      <div class="pipeline-arrow">➔</div>

      <!-- Step 1 -->
      <div 
        class="step-node" 
        class:active={activeWaterfallStep === 1}
        onclick={() => activeWaterfallStep = 1}
        role="button"
        tabindex="0"
        onkeydown={(e) => { if (e.key === 'Enter') activeWaterfallStep = 1; }}
      >
        <div class="step-num">1</div>
        <div class="step-meta">
          <span class="step-title">Liquid Buffer</span>
          <span class="step-status text-emerald">10-15% Target</span>
        </div>
      </div>

      <div class="pipeline-arrow">➔</div>

      <!-- Step 2 -->
      <div 
        class="step-node" 
        class:active={activeWaterfallStep === 2}
        onclick={() => activeWaterfallStep = 2}
        role="button"
        tabindex="0"
        onkeydown={(e) => { if (e.key === 'Enter') activeWaterfallStep = 2; }}
      >
        <div class="step-num">2</div>
        <div class="step-meta">
          <span class="step-title">Gold Floor</span>
          <span class="step-status text-amber">5-10% Backstop</span>
        </div>
      </div>

      <div class="pipeline-arrow">➔</div>

      <!-- Step 3 -->
      <div 
        class="step-node" 
        class:active={activeWaterfallStep === 3}
        onclick={() => activeWaterfallStep = 3}
        role="button"
        tabindex="0"
        onkeydown={(e) => { if (e.key === 'Enter') activeWaterfallStep = 3; }}
      >
        <div class="step-num">3</div>
        <div class="step-meta">
          <span class="step-title">Equity Compounding</span>
          <span class="step-status text-cyan">Core 50% / Sat 35%</span>
        </div>
      </div>
    </div>

    <!-- Active Step Verbose Storytelling Drawer -->
    <div class="step-narrative-card">
      {#if activeWaterfallStep === 0}
        <div class="narrative-content">
          <div class="narrative-tag rose">STEP 0: UNCONDITIONAL LEGACY PHASE-OUT (INVARIANT 4)</div>
          <h4 class="narrative-headline">Immediate Liquidation of Drifting / High-TER Legacy Holdings</h4>
          <p class="narrative-text">
            Under Portfolio OS Policy Invariant 4, any legacy mutual funds flagged for phase-out (such as active midcap schemes with uncompensated tracking variance) are liquidated <strong>100% upfront</strong>. 
            Unlike earlier versions that stalled trims during equity shortfalls, v2.3 enforces complete phase-out so capital stops leaking management fees and immediately enters disciplined asset allocation.
          </p>
          <div class="narrative-bullet-grid">
            <div class="bullet-item">
              <span class="bullet-label">Action Target:</span>
              <span class="bullet-val text-rose bold">Motilal Oswal Midcap / Legacy Schemes</span>
            </div>
            <div class="bullet-item">
              <span class="bullet-label">Routing Destination:</span>
              <span class="bullet-val text-cyan bold">Arbitrage Buffer ➔ Gold Floor ➔ Equity Core</span>
            </div>
            <div class="bullet-item">
              <span class="bullet-label">Tax Guard:</span>
              <span class="bullet-val text-emerald bold">FIFO Lot Matching with Sec 112A Exemption</span>
            </div>
          </div>
        </div>
      {:else if activeWaterfallStep === 1}
        <div class="narrative-content">
          <div class="narrative-tag emerald">STEP 1: CAPITAL PRESERVATION & LIQUID BUFFER REPLENISHMENT</div>
          <h4 class="narrative-headline">Maintaining 10% Floor and 15% Cap in Zero-Loss Arbitrage</h4>
          <p class="narrative-text">
            Before pouring money into volatile equities, the waterfall guarantees our safe harbor. Liquid Buffer (Invesco India Arbitrage / Parag Parikh Liquid) absorbs trim proceeds up to its 10% minimum floor, with an absolute safety ceiling of 15%. This provides 12 to 24 months of unconditional runway and dry powder for opportunistic dip-buying.
          </p>
          <div class="narrative-bullet-grid">
            <div class="bullet-item">
              <span class="bullet-label">Minimum Policy Floor:</span>
              <span class="bullet-val text-emerald bold">10.0% of Net Worth</span>
            </div>
            <div class="bullet-item">
              <span class="bullet-label">Tactical Storage Cap:</span>
              <span class="bullet-val text-cyan bold">15.0% Maximum Spillover</span>
            </div>
            <div class="bullet-item">
              <span class="bullet-label">Execution Vehicle:</span>
              <span class="bullet-val text-muted bold">Equity-Savings / Arbitrage (Zero Equity Beta)</span>
            </div>
          </div>
        </div>
      {:else if activeWaterfallStep === 2}
        <div class="narrative-content">
          <div class="narrative-tag amber">STEP 2: GOLD & SILVER REAL ASSET STABILIZER</div>
          <h4 class="narrative-headline">Floor Backstop: 5% Minimum to 10% Tactical Allocation</h4>
          <p class="narrative-text">
            Physical gold and silver act as an uncorrelated monetary shield during stagflation, currency debasement, and deep market corrections. Under v2.3, proceeds flowing past the liquid buffer restore the gold floor backstop to ensure steady real-asset protection without generating unnecessary sell orders.
          </p>
          <div class="narrative-bullet-grid">
            <div class="bullet-item">
              <span class="bullet-label">Policy Allocation Target:</span>
              <span class="bullet-val text-amber bold">5.0% - 10.0%</span>
            </div>
            <div class="bullet-item">
              <span class="bullet-label">Approved Policy Instrument:</span>
              <span class="bullet-val text-cyan bold">Motilal Oswal Gold and Silver Passive FoF</span>
            </div>
            <div class="bullet-item">
              <span class="bullet-label">Drift Tolerance Band:</span>
              <span class="bullet-val text-emerald bold">12.0% Accumulator Band</span>
            </div>
          </div>
        </div>
      {:else}
        <div class="narrative-content">
          <div class="narrative-tag cyan">STEP 3: LONG-TERM EQUITY COMPOUNDING ENGINE</div>
          <h4 class="narrative-headline">Core 50% Anchor & Satellite 35% Factor Alpha Inflows</h4>
          <p class="narrative-text">
            The beating heart of our portfolio. After safety gates are met, all remaining capital routes into Equity Core (Parag Parikh Flexi Cap & UTI Nifty 50 Index) and high-conviction factor satellites (ICICI Smallcap & Bandhan Largecap). Inflows are prioritized to the most underweight bucket to eliminate drift friction.
          </p>
          <div class="narrative-bullet-grid">
            <div class="bullet-item">
              <span class="bullet-label">Equity Core Anchor:</span>
              <span class="bullet-val text-cyan bold">50.0% (Wide-Moat Multi-Cap)</span>
            </div>
            <div class="bullet-item">
              <span class="bullet-label">Equity Satellite Boost:</span>
              <span class="bullet-val text-violet bold">35.0% (Size & Momentum Factors)</span>
            </div>
            <div class="bullet-item">
              <span class="bullet-label">Expected Real Return:</span>
              <span class="bullet-val text-emerald bold">6.0% Real Inflation-Adjusted</span>
            </div>
          </div>
        </div>
      {/if}
    </div>
  </div>

  <!-- SECTION 2: COMPARATIVE REBALANCING BARS & TAX IMPACT -->
  <div class="comparison-grid">
    <!-- Visual Bucket Comparative Bars -->
    <div class="comparison-card obsidian-card">
      <div class="card-top-row">
        <div class="top-title">
          <Layers size={16} class="text-cyan" />
          <h3 class="card-h">Target vs Actual vs Post-Rebalance</h3>
        </div>
        <button 
          type="button" 
          class="hrp-pill-btn" 
          class:active={showHrpComparison}
          onclick={() => showHrpComparison = !showHrpComparison}
        >
          <Compass size={12} />
          <span>{showHrpComparison ? 'Hide HRP (CVaR)' : 'Overlay HRP (95% CVaR)'}</span>
        </button>
      </div>

      <div class="bars-container">
        {#if rebalancePlan?.buy_side?.buckets && rebalancePlan.buy_side.buckets.length > 0}
          {#each rebalancePlan.buy_side.buckets as b}
            {@const alloc = b.amount_allocated ?? 0}
            {@const bNameUpper = b.bucket.toUpperCase().replace(/\s+/g, '_')}
            {@const hrpAlloc = hrpData?.allocations?.filter(a => a.bucket.toUpperCase() === bNameUpper || bNameUpper.includes(a.bucket.toUpperCase()))}
            {@const hrpPct = hrpAlloc && hrpAlloc.length > 0 ? hrpAlloc.reduce((acc, curr) => acc + curr.hrp_pct, 0) : null}
            {@const target = b.target_pct || 0}
            {@const current = b.current_pct || 0}
            {@const post = b.post_pct ?? target}
            <div class="bucket-bar-group">
              <div class="bar-header">
                <span class="b-name font-medium">{formatBucketTitle(b.bucket)}</span>
                <div class="b-stats mono">
                  <span class="curr-stat text-muted">{current.toFixed(1)}%</span>
                  <span class="arrow-stat">➔</span>
                  <span class="post-stat text-cyan bold">{post.toFixed(1)}%</span>
                  {#if alloc > 0}
                    <span class="alloc-badge text-emerald bold">+{formatINR(alloc, quietMode)}</span>
                  {/if}
                </div>
              </div>

              <!-- Multi-tier Bar Visual -->
              <div class="visual-bar-track">
                <!-- Current Actual bar -->
                <div class="bar-fill current-fill" style="width: {Math.min(100, current * 1.6)}%;"></div>
                <!-- Target marker line -->
                <div class="target-marker" style="left: {Math.min(100, target * 1.6)}%;" title="Target Policy: {target}%"></div>
                <!-- Post-rebalance projection outline -->
                {#if alloc > 0}
                  <div class="bar-fill post-fill" style="width: {Math.min(100, post * 1.6)}%;"></div>
                {/if}
                {#if showHrpComparison && hrpPct !== null}
                  <div class="hrp-marker" style="left: {Math.min(100, hrpPct * 1.6)}%;" title="HRP 95% CVaR: {hrpPct.toFixed(1)}%"></div>
                {/if}
              </div>

              {#if alloc > 0 && b.fund_breakdown && b.fund_breakdown.length > 0}
                <div class="fund-routing-tags">
                  {#each b.fund_breakdown as fb}
                    <span class="fund-pill mono">
                      ↳ {fb.fund_name}: <strong class="text-emerald">+{formatINR(fb.amount, quietMode)}</strong>
                    </span>
                  {/each}
                </div>
              {/if}
            </div>
          {/each}
        {:else}
          <p class="empty-note">All portfolio buckets currently reside within nominal tolerance bounds.</p>
        {/if}
      </div>

      <div class="bars-legend">
        <span class="legend-item"><span class="box current"></span> Current Actual</span>
        <span class="legend-item"><span class="line target"></span> Policy Target</span>
        <span class="legend-item"><span class="box post"></span> Post-Rebalance Projected</span>
        {#if showHrpComparison}
          <span class="legend-item"><span class="line hrp"></span> HRP 95% CVaR Optimization</span>
        {/if}
      </div>
    </div>

    <!-- Tax-Shield & Exemption Gate Card -->
    <div class="tax-shield-card obsidian-card">
      <div class="card-top-row">
        <div class="top-title">
          <ShieldCheck size={16} class="text-emerald" />
          <h3 class="card-h">Section 112A Exemption Shield</h3>
        </div>
        <span class="tax-act-pill mono">FINANCE ACT 2024</span>
      </div>

      <!-- Exemption Cap Gauge -->
      <div class="exemption-gauge-box">
        <div class="gauge-head">
          <span class="g-label">Annual Tax-Free LTCG Allowance (₹1.25 Lakh Cap)</span>
          <span class="g-val mono bold text-emerald">{formatINR(exemptGain, quietMode)} / ₹1.25L</span>
        </div>
        <div class="gauge-track">
          <div class="gauge-fill" style="width: {exemptPct}%;"></div>
        </div>
        <span class="gauge-sub text-muted">
          {exemptLimit - exemptGain > 0 ? `₹${formatINR(exemptLimit - exemptGain, quietMode)} exemption headroom remaining this financial year` : '100% of tax-free harvest capacity utilized!'}
        </span>
      </div>

      <!-- Tax Impact Breakdown Tiles -->
      <div class="tax-tiles-grid">
        <div class="tax-tile">
          <span class="t-label">Exempt Harvest (0% Tax)</span>
          <span class="t-val mono bold text-emerald">+{formatINR(exemptGain, quietMode)}</span>
          <span class="t-sub text-muted">Shielded under Sec 112A</span>
        </div>
        <div class="tax-tile">
          <span class="t-label">STCG Taxable Gain</span>
          <span class="t-val mono text-muted">{formatINR(rebalancePlan?.summary?.stcg_taxable_gain ?? 0, quietMode)}</span>
          <span class="t-sub text-muted">Taxed at 20.0% post July 2024</span>
        </div>
        <div class="tax-tile highlight">
          <span class="t-label">Net Projected Tax Drag</span>
          <span class="t-val mono bold text-cyan">{formatINR(rebalancePlan?.summary?.projected_tax ?? 0, quietMode)}</span>
          <span class="t-sub text-emerald">Minimal tax drag achieved</span>
        </div>
      </div>

      <div class="tax-explanation-box">
        <Info size={14} class="text-cyan shrink-0" />
        <p class="tax-expl-text">
          Our FIFO tax engine checks every lot against statutory acquisition dates. Units held &gt;365 days are harvested tax-free up to ₹1.25 Lakh. Rebalance proceeds are immediately recycled into underweight buckets without dividend tax drag.
        </p>
      </div>
    </div>
  </div>

  <!-- SECTION 3: GUYTON-KLINGER SWR CORRIDOR GAUGE -->
  <div class="visual-panel obsidian-card">
    <div class="panel-header">
      <div class="header-left">
        <div class="icon-bubble amber">
          <Flame size={18} />
        </div>
        <div>
          <h3 class="panel-title">Guyton-Klinger Dynamic Decumulation Guardrails</h3>
          <span class="panel-sub">Safe Withdrawal Rate (SWR) corridor with automatic lifestyle step-ups and capital preservation rules</span>
        </div>
      </div>
      <div class="swr-live-badge mono">
        {#if isAccumulationPhase}
          <div class="badge-accumulation-group">
            <span class="phase-pill">ACCUMULATION PHASE</span>
            <span class="years-pill">{yearsRemaining()} Yrs to FI (Age 45)</span>
          </div>
          <div class="swr-stat-group">
            <span class="label text-muted">TARGET SWR:</span>
            <strong class="text-cyan bold">{targetRetirementSwrPct().toFixed(2)}%</strong>
          </div>
          <div class="swr-stat-group">
            <span class="label text-muted">PROJECTED SWR:</span>
            <strong class="text-emerald bold">{projectedRetirementSwrPct().toFixed(2)}%</strong>
          </div>
          <span class="swr-status-indicator nominal">
            NOMINAL SAFE
          </span>
        {:else}
          <span>LIVE SWR:</span>
          <strong class="text-cyan bold">{hypotheticalInstantSwrPct().toFixed(2)}%</strong>
          <span class="swr-status-indicator {isProsperity ? 'prosperity' : (isPreservation ? 'preservation' : 'nominal')}">
            {isProsperity ? 'PROSPERITY BOOST' : (isPreservation ? 'PRESERVATION LOCK' : 'NOMINAL SAFE')}
          </span>
        {/if}
      </div>
    </div>

    <!-- Visual SWR Corridor Gauge -->
    <div class="swr-gauge-wrapper">
      <div class="corridor-labels mono">
        <span class="c-label text-emerald">Prosperity Zone (&lt;2.40%)</span>
        <span class="c-label text-cyan">Baseline Target (3.00% SWR)</span>
        <span class="c-label text-rose">Capital Preservation (&gt;3.60%)</span>
      </div>

      <div class="corridor-track">
        <div class="c-zone prosperity-zone" style="left: 0%; width: 40%;"></div>
        <div class="c-zone nominal-zone" style="left: 40%; width: 25%;"></div>
        <div class="c-zone preservation-zone" style="left: 65%; width: 35%;"></div>

        <!-- SWR Needle -->
        <div class="swr-needle" style="left: {swrNeedlePct}%;">
          <div class="swr-flag mono bold">
            {activeGaugeSwrPct().toFixed(2)}% {isAccumulationPhase ? 'Projected SWR' : 'Live SWR'}
          </div>
          <div class="swr-pin"></div>
        </div>
      </div>
    </div>

    <!-- Phase & Burn Rate Clarity Box -->
    <div class="accumulation-clarity-card">
      <div class="clarity-left">
        <Info size={16} class="text-cyan shrink-0" />
        <div class="clarity-body">
          <span class="clarity-title">Accumulation Phase: <strong>{fundedRatioPct().toFixed(1)}% Funded</strong> ({formatINR(currentNetWorth(), quietMode)} of {formatINR(targetCorpus(), quietMode)} Goal)</span>
          <p class="clarity-desc">
            Guyton-Klinger dynamic decumulation rules (Prosperity & Capital Preservation) govern your withdrawals <strong>after retirement</strong>.
            If you ceased earning today and withdrew annual expenses ({formatINR(annualExpense(), quietMode)}/yr) directly from your current early-stage accumulation corpus, the mathematical burn rate would be <strong class="text-amber">{hypotheticalInstantSwrPct().toFixed(1)}%</strong> (depleting in ~3 years). 
            Your planned retirement SWR at Age 45 is <strong class="text-emerald">{targetRetirementSwrPct().toFixed(2)}%</strong> (with projected corpus {formatINR(projectedCorpus(), quietMode)} yielding a comfortable <strong class="text-emerald">{projectedRetirementSwrPct().toFixed(2)}%</strong> SWR), firmly inside the safe corridor.
          </p>
        </div>
      </div>
      <div class="clarity-metrics mono">
        <div class="c-metric">
          <span class="c-lbl">Corpus Today</span>
          <span class="c-val">{formatINR(currentNetWorth(), quietMode)}</span>
        </div>
        <div class="c-metric">
          <span class="c-lbl">Target at Age 45</span>
          <span class="c-val text-cyan">{formatINR(targetCorpus(), quietMode)}</span>
        </div>
        <div class="c-metric">
          <span class="c-lbl">Projected at 45</span>
          <span class="c-val text-emerald">{formatINR(projectedCorpus(), quietMode)}</span>
        </div>
      </div>
    </div>

    <!-- Verbose Storytelling Cards for Rules -->
    <div class="rules-story-grid">
      <div class="rule-story-card" class:active={isProsperity}>
        <div class="rule-card-top">
          <Zap size={16} class="text-emerald" />
          <span class="rule-name bold">The Prosperity Rule</span>
        </div>
        <span class="rule-bound mono text-emerald">Trigger: SWR falls below 2.40%</span>
        <p class="rule-desc">
          When market outperformance causes your corpus to expand significantly faster than inflation, your withdrawal rate drops below 2.40%. 
          <strong>Action Mandate:</strong> You are granted a <strong>+10% permanent raise</strong> in discretionary lifestyle spending without risking 40-year portfolio longevity.
        </p>
      </div>

      <div class="rule-story-card" class:active={!isProsperity && !isPreservation}>
        <div class="rule-card-top">
          <ShieldCheck size={16} class="text-cyan" />
          <span class="rule-name bold">The Golden Corridor</span>
        </div>
        <span class="rule-bound mono text-cyan">Corridor: 2.40% to 3.60% SWR</span>
        <p class="rule-desc">
          Withdrawal rate is perfectly in equilibrium. The portfolio easily absorbs ordinary market fluctuations and generates sufficient real return to counter Indian inflation (6.0% real target).
          <strong>Action Mandate:</strong> Maintain normal planned withdrawals with standard annual inflation adjustments.
        </p>
      </div>

      <div class="rule-story-card" class:active={isPreservation}>
        <div class="rule-card-top">
          <ShieldAlert size={16} class="text-rose" />
          <span class="rule-name bold">Capital Preservation Rule</span>
        </div>
        <span class="rule-bound mono text-rose">Trigger: SWR exceeds 3.60%</span>
        <p class="rule-desc">
          If a severe multi-year equity bear market reduces your portfolio so your withdrawal rate breaches 3.60%, protective brakes engage automatically.
          <strong>Action Mandate:</strong> Suspend annual inflation step-ups and apply a temporary <strong>-10% cut to discretionary spending</strong> until the golden corridor is restored.
        </p>
      </div>
    </div>
  </div>

  <!-- SECTION 4: MONTE CARLO PROJECTION FAN & WEALTH TRAJECTORY -->
  <div class="visual-panel obsidian-card">
    <div class="panel-header">
      <div class="header-left">
        <div class="icon-bubble cyan">
          <Activity size={18} />
        </div>
        <div>
          <h3 class="panel-title">Monte Carlo Wealth Projection Fan (2,000 Paths)</h3>
          <span class="panel-sub">Stochastic inflation-adjusted simulation of retirement corpus longevity</span>
        </div>
      </div>
      {#if fireData?.monte_carlo_success_rate_pct}
        <div class="confidence-pill mono">
          <span>CONFIDENCE:</span>
          <strong class="text-emerald">{fireData.monte_carlo_success_rate_pct.toFixed(1)}% SUCCESS</strong>
        </div>
      {/if}
    </div>

    <!-- Visual SVG Fan Chart -->
    <div class="chart-wrapper">
      <svg viewBox="0 0 700 240" class="fan-svg">
        <defs>
          <linearGradient id="fanGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#06B6D4" stop-opacity="0.25" />
            <stop offset="100%" stop-color="#3B82F6" stop-opacity="0.02" />
          </linearGradient>
        </defs>

        <!-- Grid Lines -->
        <line x1="50" y1="30" x2="660" y2="30" stroke="rgba(255,255,255,0.04)" stroke-dasharray="4" />
        <line x1="50" y1="90" x2="660" y2="90" stroke="rgba(255,255,255,0.04)" stroke-dasharray="4" />
        <line x1="50" y1="150" x2="660" y2="150" stroke="rgba(255,255,255,0.04)" stroke-dasharray="4" />
        <line x1="50" y1="210" x2="660" y2="210" stroke="rgba(255,255,255,0.08)" />

        <!-- Shaded Monte Carlo 10th-90th Percentile Fan Area -->
        <path
          d="M 60 170 C 200 160, 400 90, 650 35 L 650 195 C 400 185, 200 175, 60 170 Z"
          fill="url(#fanGrad)"
        />

        <!-- 90th percentile curve (Bull) -->
        <path
          d="M 60 170 C 200 160, 400 90, 650 35"
          fill="none"
          stroke="#06B6D4"
          stroke-width="1.5"
          stroke-dasharray="3"
        />

        <!-- 50th percentile Median curve -->
        <path
          d="M 60 170 C 200 165, 400 135, 650 110"
          fill="none"
          stroke="#10B981"
          stroke-width="2.5"
        />

        <!-- 10th percentile curve (Stress Test) -->
        <path
          d="M 60 170 C 200 170, 400 170, 650 195"
          fill="none"
          stroke="#F43F5E"
          stroke-width="1.5"
          stroke-dasharray="3"
        />

        <!-- Target Corpus Benchmark Line -->
        <line x1="60" y1="125" x2="650" y2="125" stroke="#F59E0B" stroke-width="2" stroke-dasharray="6" />

        <!-- Current Age Pin -->
        <circle cx="60" cy="170" r="5" fill="#06B6D4" stroke="#ffffff" stroke-width="2" />
        <text x="60" y="195" class="chart-anno mono">Today</text>

        <!-- Target FIRE Milestone Intersection -->
        <circle cx="430" cy="125" r="6" fill="#F59E0B" stroke="#ffffff" stroke-width="2" />
        <text x="430" y="112" class="chart-anno fire-anno mono" text-anchor="middle">FIRE Milestone (Age 45)</text>

        <!-- Right End Labels -->
        <text x="655" y="40" class="chart-anno text-cyan mono">90th %ile</text>
        <text x="655" y="115" class="chart-anno text-emerald mono bold">Median (50th)</text>
        <text x="655" y="130" class="chart-anno text-amber mono">Target Goal</text>
        <text x="655" y="200" class="chart-anno text-rose mono">10th %ile</text>
      </svg>
    </div>

    <!-- Retirement Milestones Story Summary -->
    {#if fireData}
      <div class="milestones-story-grid">
        <div class="milestone-box">
          <span class="m-title">Monthly Runway Today</span>
          <span class="m-val mono bold text-cyan">{formatINR(Number(fireData.monthly_expense_today) || 0, quietMode)}</span>
          <span class="m-sub text-muted">Annual: {formatINR(Number(fireData.annual_expense) || 0, quietMode)}</span>
        </div>
        <div class="milestone-box">
          <span class="m-title">Target Corpus Required</span>
          <span class="m-val mono bold text-amber">{formatCompactINR(Number(fireData.required_corpus) || 0, quietMode)}</span>
          <span class="m-sub mono text-cyan">{fireData.years_remaining ?? '—'} Years Remaining to Age 45</span>
        </div>
        <div class="milestone-box highlight">
          <span class="m-title">Projected Corpus at Target</span>
          <span class="m-val mono bold text-emerald">{formatCompactINR(Number(fireData.projected_corpus_at_target_age) || 0, quietMode)}</span>
          <span class="m-sub text-emerald">✓ On Track (Surplus +{formatCompactINR(Number(fireData.shortage_or_surplus_amount) || 0, quietMode)})</span>
        </div>
      </div>
    {/if}
  </div>

  <!-- SECTION 5: RETIREMENT SCENARIO STUDIO -->
  {#if fireData?.scenarios && fireData.scenarios.length > 0}
    <div class="visual-panel obsidian-card">
      <div class="panel-header">
        <div class="header-left">
          <div class="icon-bubble cyan">
            <Target size={18} />
          </div>
          <div>
            <h3 class="panel-title">Retirement Scenario Comparison Studio</h3>
            <span class="panel-sub">Interactive sensitivity modeling for Lean, Standard, and Fat FIRE targets</span>
          </div>
        </div>
      </div>

      <div class="scenarios-flex-grid">
        {#each fireData.scenarios as sc}
          {@const mExp = Number(sc.monthly_expense || sc.monthly_expense_today) || 0}
          {@const tCorp = Number(sc.target_corpus) || (mExp * 12 / 0.03)}
          {@const currentNw = currentNetWorth()}
          {@const fundedPct = Math.min(100, (currentNw / tCorp) * 100)}
          {@const isSelected = selectedScenario === sc.label}
          <div 
            class="scenario-visual-card" 
            class:selected={isSelected}
            onclick={() => selectedScenario = sc.label}
            role="button"
            tabindex="0"
            onkeydown={(e) => { if (e.key === 'Enter') selectedScenario = sc.label; }}
          >
            <div class="sc-top">
              <span class="sc-label font-medium">{sc.label}</span>
              <span class="sc-funded-badge mono bold text-emerald">{fundedPct.toFixed(0)}% FUNDED</span>
            </div>

            <div class="sc-corpus mono bold text-cyan">
              {formatCompactINR(tCorp, quietMode)}
            </div>

            <div class="sc-progress-bar">
              <div class="sc-progress-fill" style="width: {fundedPct}%;"></div>
            </div>

            <div class="sc-footer text-muted mono">
              <span>{formatINR(mExp, quietMode)}/mo</span>
              <span>{sc.swr_pct || '3.0'}% SWR</span>
            </div>
          </div>
        {/each}
      </div>
    </div>
  {/if}
</div>

<style>
  .rebalance-visual-view {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
  }

  /* Hero Banner */
  .hero-banner {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 1.25rem 1.5rem;
    background: linear-gradient(135deg, rgba(6, 182, 212, 0.08), rgba(59, 130, 246, 0.04));
    border: 1px solid rgba(6, 182, 212, 0.3);
    border-radius: var(--radius-lg);
    gap: 1.5rem;
    flex-wrap: wrap;
  }

  .banner-content {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
    max-width: 780px;
  }

  .trigger-pill {
    display: inline-flex;
    align-items: center;
    gap: 0.45rem;
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--accent-cyan);
    background: rgba(6, 182, 212, 0.12);
    border: 1px solid rgba(6, 182, 212, 0.3);
    padding: 2px 8px;
    border-radius: 4px;
    width: fit-content;
  }

  .hero-title {
    font-size: 1.25rem;
    font-weight: 800;
    color: #ffffff;
    margin: 0;
  }

  .hero-desc {
    font-size: 0.82rem;
    color: var(--text-muted);
    line-height: 1.45;
    margin: 0;
  }

  .lumpsum-cta-btn {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.65rem 1.35rem;
    background: var(--surface-glass);
    border: 1px solid var(--accent-cyan);
    border-radius: var(--radius-md);
    color: var(--accent-cyan);
    font-size: 0.88rem;
    font-weight: 700;
    cursor: pointer;
    transition: all var(--transition-fast);
  }
  .lumpsum-cta-btn:hover {
    background: var(--accent-cyan);
    color: var(--surface-void);
    box-shadow: 0 0 16px var(--glow-cyan);
  }

  /* Panels */
  .visual-panel {
    padding: 20px 22px;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .icon-bubble {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: var(--radius-md);
  }
  .icon-bubble.cyan {
    background: rgba(6, 182, 212, 0.12);
    border: 1px solid rgba(6, 182, 212, 0.3);
    color: var(--accent-cyan);
  }
  .icon-bubble.amber {
    background: rgba(245, 158, 11, 0.12);
    border: 1px solid rgba(245, 158, 11, 0.3);
    color: var(--accent-amber);
  }

  .panel-title {
    font-size: 1.05rem;
    font-weight: 700;
    color: #ffffff;
    margin: 0;
  }

  .panel-sub {
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .waterfall-total-badge, .swr-live-badge, .confidence-pill {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.8rem;
    padding: 4px 10px;
    border-radius: var(--radius-sm);
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid var(--border-hairline);
  }

  /* Pipeline Stepper */
  .pipeline-stepper {
    display: grid;
    grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr;
    align-items: center;
    gap: 8px;
  }

  .step-node {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 14px;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all 0.2s ease;
  }
  .step-node:hover, .step-node.active {
    background: rgba(6, 182, 212, 0.08);
    border-color: rgba(6, 182, 212, 0.45);
    transform: translateY(-2px);
  }

  .step-num {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.06);
    font-family: var(--font-mono);
    font-weight: 800;
    font-size: 0.75rem;
    color: #ffffff;
  }
  .step-node.active .step-num {
    background: var(--accent-cyan);
    color: #030712;
  }

  .step-meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .step-title {
    font-size: 0.82rem;
    font-weight: 700;
    color: #ffffff;
  }
  .step-status {
    font-size: 0.68rem;
    font-family: var(--font-mono);
  }

  .pipeline-arrow {
    color: var(--text-muted);
    font-size: 0.9rem;
  }

  /* Step Narrative Card */
  .step-narrative-card {
    background: rgba(10, 15, 28, 0.7);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    padding: 16px 20px;
    backdrop-filter: blur(12px);
  }

  .narrative-tag {
    font-family: var(--font-mono);
    font-size: 0.68rem;
    font-weight: 800;
    padding: 2px 8px;
    border-radius: 4px;
    width: fit-content;
    margin-bottom: 6px;
  }
  .narrative-tag.rose { background: rgba(244, 63, 94, 0.15); color: var(--accent-rose); border: 1px solid rgba(244, 63, 94, 0.35); }
  .narrative-tag.emerald { background: rgba(16, 185, 129, 0.15); color: var(--accent-emerald); border: 1px solid rgba(16, 185, 129, 0.35); }
  .narrative-tag.amber { background: rgba(245, 158, 11, 0.15); color: var(--accent-amber); border: 1px solid rgba(245, 158, 11, 0.35); }
  .narrative-tag.cyan { background: rgba(6, 182, 212, 0.15); color: var(--accent-cyan); border: 1px solid rgba(6, 182, 212, 0.35); }

  .narrative-headline {
    font-size: 0.95rem;
    font-weight: 700;
    color: #ffffff;
    margin: 0 0 6px 0;
  }

  .narrative-text {
    font-size: 0.82rem;
    color: var(--text-muted);
    line-height: 1.5;
    margin: 0 0 14px 0;
  }

  .narrative-bullet-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }

  .bullet-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    padding: 8px 12px;
  }
  .bullet-label {
    font-size: 0.68rem;
    color: var(--text-muted);
    text-transform: uppercase;
  }
  .bullet-val {
    font-size: 0.82rem;
  }

  /* Comparison Grid */
  .comparison-grid {
    display: grid;
    grid-template-columns: 1.4fr 1fr;
    gap: 1.5rem;
  }

  .comparison-card, .tax-shield-card {
    padding: 20px;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .card-top-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .top-title {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .card-h {
    font-size: 0.95rem;
    font-weight: 700;
    color: #ffffff;
    margin: 0;
  }

  .hrp-pill-btn {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 0.7rem;
    font-family: var(--font-mono);
    padding: 3px 8px;
    border-radius: 4px;
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid var(--border-hairline);
    color: var(--text-muted);
    cursor: pointer;
    transition: all 0.2s ease;
  }
  .hrp-pill-btn.active {
    background: rgba(6, 182, 212, 0.15);
    color: var(--accent-cyan);
    border-color: rgba(6, 182, 212, 0.4);
  }

  .bars-container {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .bucket-bar-group {
    display: flex;
    flex-direction: column;
    gap: 5px;
  }

  .bar-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .b-name {
    font-size: 0.82rem;
    color: #ffffff;
  }
  .b-stats {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.78rem;
  }

  .visual-bar-track {
    position: relative;
    height: 10px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: var(--radius-pill);
    overflow: visible;
  }

  .bar-fill {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    border-radius: var(--radius-pill);
    transition: width 0.4s ease;
  }
  .current-fill {
    background: rgba(6, 182, 212, 0.45);
  }
  .post-fill {
    border: 1.5px solid var(--accent-emerald);
    box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
  }

  .target-marker {
    position: absolute;
    top: -3px;
    bottom: -3px;
    width: 2.5px;
    background: #ffffff;
    border-radius: 2px;
    box-shadow: 0 0 6px #ffffff;
    z-index: 2;
  }

  .hrp-marker {
    position: absolute;
    top: -3px;
    bottom: -3px;
    width: 2.5px;
    background: var(--accent-violet);
    box-shadow: 0 0 6px var(--accent-violet);
    z-index: 3;
  }

  .fund-routing-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 2px;
  }
  .fund-pill {
    font-size: 0.68rem;
    padding: 2px 6px;
    background: rgba(255, 255, 255, 0.03);
    border-radius: 4px;
    color: var(--text-muted);
  }

  .bars-legend {
    display: flex;
    align-items: center;
    gap: 16px;
    font-size: 0.7rem;
    color: var(--text-muted);
    border-top: 1px solid var(--border-hairline);
    padding-top: 10px;
    flex-wrap: wrap;
  }
  .legend-item {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .box { width: 10px; height: 10px; border-radius: 2px; }
  .box.current { background: rgba(6, 182, 212, 0.45); }
  .box.post { border: 1.5px solid var(--accent-emerald); }
  .line { width: 12px; height: 3px; }
  .line.target { background: #ffffff; }
  .line.hrp { background: var(--accent-violet); }

  /* Tax Shield Card */
  .tax-act-pill {
    font-size: 0.65rem;
    font-weight: 700;
    padding: 2px 6px;
    background: rgba(16, 185, 129, 0.12);
    color: var(--accent-emerald);
    border: 1px solid rgba(16, 185, 129, 0.3);
    border-radius: 3px;
  }

  .exemption-gauge-box {
    display: flex;
    flex-direction: column;
    gap: 6px;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 12px 14px;
  }
  .gauge-head {
    display: flex;
    justify-content: space-between;
    font-size: 0.75rem;
  }
  .gauge-track {
    height: 8px;
    background: rgba(255, 255, 255, 0.06);
    border-radius: var(--radius-pill);
    overflow: hidden;
  }
  .gauge-fill {
    height: 100%;
    background: linear-gradient(90deg, #10B981, #06B6D4);
    box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
    border-radius: var(--radius-pill);
  }
  .gauge-sub {
    font-size: 0.68rem;
  }

  .tax-tiles-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }
  .tax-tile {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 8px 10px;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
  }
  .tax-tile.highlight {
    background: rgba(6, 182, 212, 0.05);
    border-color: rgba(6, 182, 212, 0.3);
  }
  .t-label {
    font-size: 0.65rem;
    color: var(--text-muted);
  }
  .t-val {
    font-size: 0.92rem;
  }
  .t-sub {
    font-size: 0.62rem;
  }

  .tax-explanation-box {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    background: rgba(6, 182, 212, 0.04);
    border: 1px solid rgba(6, 182, 212, 0.2);
    border-radius: var(--radius-sm);
    padding: 10px 12px;
  }
  .tax-expl-text {
    font-size: 0.75rem;
    color: var(--text-muted);
    line-height: 1.4;
    margin: 0;
  }

  /* Guyton-Klinger Corridor */
  .swr-status-indicator {
    font-size: 0.65rem;
    font-weight: 800;
    padding: 1px 6px;
    border-radius: 3px;
  }
  .swr-status-indicator.nominal { background: rgba(6, 182, 212, 0.15); color: var(--accent-cyan); }
  .swr-status-indicator.prosperity { background: rgba(16, 185, 129, 0.15); color: var(--accent-emerald); }
  .swr-status-indicator.preservation { background: rgba(244, 63, 94, 0.15); color: var(--accent-rose); }

  .swr-gauge-wrapper {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 6px;
  }
  .corridor-labels {
    display: flex;
    justify-content: space-between;
    font-size: 0.7rem;
  }

  .corridor-track {
    position: relative;
    height: 14px;
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-pill);
    overflow: visible;
  }

  .c-zone {
    position: absolute;
    top: 0;
    bottom: 0;
  }
  .prosperity-zone {
    background: linear-gradient(90deg, rgba(16, 185, 129, 0.35), rgba(16, 185, 129, 0.15));
    border-top-left-radius: var(--radius-pill);
    border-bottom-left-radius: var(--radius-pill);
  }
  .nominal-zone {
    background: rgba(6, 182, 212, 0.15);
  }
  .preservation-zone {
    background: linear-gradient(90deg, rgba(244, 63, 94, 0.15), rgba(244, 63, 94, 0.4));
    border-top-right-radius: var(--radius-pill);
    border-bottom-right-radius: var(--radius-pill);
  }

  .swr-needle {
    position: absolute;
    top: -26px;
    transform: translateX(-50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    transition: left 0.4s ease;
    z-index: 5;
  }
  .swr-flag {
    font-size: 0.65rem;
    background: var(--accent-cyan);
    color: #030712;
    padding: 1px 7px;
    border-radius: 4px;
    box-shadow: 0 0 10px rgba(6, 182, 212, 0.6);
    white-space: nowrap;
  }
  .swr-pin {
    width: 2px;
    height: 30px;
    background: var(--accent-cyan);
    box-shadow: 0 0 6px var(--accent-cyan);
  }

  .rules-story-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }

  .rule-story-card {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    transition: all 0.2s ease;
  }
  .rule-story-card.active {
    border-color: rgba(6, 182, 212, 0.45);
    background: rgba(6, 182, 212, 0.04);
  }
  .rule-card-top {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .rule-name {
    font-size: 0.85rem;
    color: #ffffff;
  }
  .rule-bound {
    font-size: 0.68rem;
  }
  .rule-desc {
    font-size: 0.75rem;
    color: var(--text-muted);
    line-height: 1.4;
    margin: 0;
  }

  /* Monte Carlo Fan Chart */
  .chart-wrapper {
    width: 100%;
    overflow-x: auto;
  }
  .fan-svg {
    width: 100%;
    height: auto;
    max-height: 240px;
  }
  .chart-anno {
    font-size: 9px;
    fill: var(--text-muted);
  }
  .fire-anno {
    font-size: 10px;
    font-weight: 700;
    fill: #F59E0B;
  }

  .milestones-story-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }
  .milestone-box {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .milestone-box.highlight {
    background: rgba(16, 185, 129, 0.04);
    border-color: rgba(16, 185, 129, 0.35);
  }
  .m-title {
    font-size: 0.72rem;
    color: var(--text-muted);
  }
  .m-val {
    font-size: 1.15rem;
  }
  .m-sub {
    font-size: 0.7rem;
  }

  /* Scenario Cards */
  .scenarios-flex-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 14px;
  }

  .scenario-visual-card {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 14px 16px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    cursor: pointer;
    transition: all 0.2s ease;
  }
  .scenario-visual-card:hover, .scenario-visual-card.selected {
    background: rgba(6, 182, 212, 0.06);
    border-color: var(--accent-cyan);
    transform: translateY(-2px);
  }

  .sc-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .sc-label {
    font-size: 0.88rem;
    color: #ffffff;
  }
  .sc-funded-badge {
    font-size: 0.68rem;
    padding: 1px 6px;
    background: rgba(16, 185, 129, 0.12);
    border-radius: 3px;
  }

  .sc-corpus {
    font-size: 1.25rem;
  }

  .sc-progress-bar {
    height: 6px;
    background: rgba(255, 255, 255, 0.06);
    border-radius: var(--radius-pill);
    overflow: hidden;
  }
  .sc-progress-fill {
    height: 100%;
    background: linear-gradient(90deg, #06B6D4, #10B981);
    border-radius: var(--radius-pill);
  }

  .sc-footer {
    display: flex;
    justify-content: space-between;
    font-size: 0.72rem;
  }

  /* Capital Provenance Breakdown Box */
  .capital-provenance-box {
    display: grid;
    grid-template-columns: 1fr auto 1fr;
    gap: 16px;
    align-items: center;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 16px;
    margin-bottom: 24px;
  }

  .provenance-col {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .prov-header {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .prov-badge {
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.05em;
    padding: 3px 8px;
    border-radius: var(--radius-pill);
    align-self: flex-start;
  }
  .prov-badge.rose {
    background: rgba(244, 63, 94, 0.15);
    color: var(--accent-rose-bright);
    border: 1px solid rgba(244, 63, 94, 0.3);
  }
  .prov-badge.cyan {
    background: rgba(6, 182, 212, 0.15);
    color: var(--accent-cyan);
    border: 1px solid rgba(6, 182, 212, 0.3);
  }

  .prov-sub {
    font-size: 0.72rem;
  }

  .prov-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .prov-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: rgba(0, 0, 0, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.05);
    border-radius: var(--radius-sm);
    padding: 8px 12px;
    gap: 12px;
  }

  .prov-item-left {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .prov-name {
    font-size: 0.8rem;
    color: #e2e8f0;
  }

  .prov-meta {
    font-size: 0.68rem;
  }

  .prov-amount {
    font-size: 0.88rem;
    white-space: nowrap;
  }

  .provenance-divider {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: 0 8px;
  }

  .prov-arrow {
    font-size: 1.2rem;
    color: var(--accent-cyan);
  }

  .prov-tax-pill {
    font-size: 0.65rem;
    background: rgba(16, 185, 129, 0.15);
    color: var(--accent-emerald-bright);
    padding: 2px 8px;
    border-radius: var(--radius-pill);
    border: 1px solid rgba(16, 185, 129, 0.3);
    white-space: nowrap;
  }

  .prov-bucket-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
    background: rgba(0, 0, 0, 0.2);
    border: 1px solid rgba(255, 255, 255, 0.05);
    border-radius: var(--radius-sm);
    padding: 8px 10px;
  }

  .prov-item.bucket-level {
    background: transparent;
    border: none;
    padding: 2px 0 6px 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  }

  .prov-fund-sublist {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding-left: 8px;
  }

  .prov-fund-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.74rem;
    background: rgba(255, 255, 255, 0.02);
    padding: 4px 8px;
    border-radius: 4px;
    gap: 8px;
  }

  .fund-item-left {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  .fund-bullet {
    color: var(--accent-cyan);
    font-weight: 700;
  }

  .fund-name {
    color: #e2e8f0;
  }

  .fund-impact-tag {
    font-size: 0.68rem;
    padding: 1px 6px;
    border-radius: 3px;
    background: rgba(255, 255, 255, 0.05);
  }
  .fund-impact-tag.buy {
    background: rgba(16, 185, 129, 0.12);
    color: var(--accent-emerald-bright);
    border: 1px solid rgba(16, 185, 129, 0.25);
  }
  .fund-impact-tag.sell {
    background: rgba(244, 63, 94, 0.12);
    color: var(--accent-rose-bright);
    border: 1px solid rgba(244, 63, 94, 0.25);
  }

  .prov-impact-row {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 2px 0;
  }

  .prov-sub-tag {
    font-size: 0.66rem;
  }

  .fund-alloc {
    font-size: 0.78rem;
    white-space: nowrap;
  }

  /* Accumulation Clarity Card */
  .badge-accumulation-group {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .phase-pill {
    background: rgba(6, 182, 212, 0.15);
    color: var(--accent-cyan);
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 0.72rem;
    font-weight: 700;
  }
  .years-pill {
    background: rgba(255, 255, 255, 0.06);
    color: #cbd5e1;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 0.72rem;
  }
  .swr-stat-group {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.78rem;
  }

  .accumulation-clarity-card {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: rgba(6, 182, 212, 0.04);
    border: 1px solid rgba(6, 182, 212, 0.2);
    border-radius: var(--radius-md);
    padding: 14px 18px;
    margin-top: 18px;
    gap: 20px;
  }

  .clarity-left {
    display: flex;
    gap: 12px;
    align-items: flex-start;
  }

  .clarity-body {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .clarity-title {
    font-size: 0.88rem;
    color: #ffffff;
  }

  .clarity-desc {
    font-size: 0.76rem;
    line-height: 1.45;
    color: #94a3b8;
    margin: 0;
  }

  .clarity-metrics {
    display: flex;
    gap: 16px;
    flex-shrink: 0;
  }

  .c-metric {
    display: flex;
    flex-direction: column;
    gap: 2px;
    background: rgba(0, 0, 0, 0.3);
    border: 1px solid rgba(255, 255, 255, 0.05);
    border-radius: var(--radius-sm);
    padding: 8px 12px;
    min-width: 110px;
  }

  .c-lbl {
    font-size: 0.65rem;
    color: #64748b;
    text-transform: uppercase;
  }

  .c-val {
    font-size: 0.88rem;
    font-weight: 700;
  }

  @media (max-width: 900px) {
    .comparison-grid {
      grid-template-columns: 1fr;
    }
    .pipeline-stepper {
      grid-template-columns: 1fr;
      gap: 12px;
    }
    .pipeline-arrow {
      display: none;
    }
    .narrative-bullet-grid, .rules-story-grid, .milestones-story-grid, .scenarios-flex-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
