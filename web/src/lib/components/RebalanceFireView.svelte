<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchJson, fetchActionRecommendations, type RebalancePlanDto, type ActionRecommendationDto } from '../api';
  import { formatINR, formatPercent, formatCompactINR } from '../formatters';
  import { 
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
    Workflow,
    Layers,
    ArrowRight,
    Info,
    Clock
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
    const nw = netWorth || currentNetWorth() || 0;
    const holding = holdings?.find((h: any) => 
      (h.asset_id && fundIdOrName.toLowerCase().includes(h.asset_id.toLowerCase())) ||
      (h.isin && fundIdOrName.toLowerCase().includes(h.isin.toLowerCase())) ||
      (h.asset_name && fundIdOrName.toLowerCase().includes(h.asset_name.toLowerCase())) ||
      (h.asset_name && h.asset_name.toLowerCase().includes(fundIdOrName.toLowerCase()))
    );
    const curVal = Number(holding?.current_value ?? 0);
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
      active?: boolean;
      target_corpus?: string;
      monthly_expense?: string;
      monthly_expense_today?: string;
      annual_expense?: string;
      swr_pct?: string;
    }>;
  }

  let fireData = $state<FireSummary | null>(null);
  let loadingFire = $state(false);
  let actionRecommendations = $state<ActionRecommendationDto[]>([]);
  let loadingRecommendations = $state(false);
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

  onMount(() => {
    loadFireSummary();
    loadActionRecommendations();
  });

  // Calculate live FIRE status, accumulation metrics and Guyton-Klinger thresholds
  const currentNetWorth = $derived(() => {
    return Number(fireData?.fire_investable_net_worth || fireData?.total_net_worth || netWorth || 0);
  });

  const activeScenario = $derived(() => {
    return fireData?.scenarios?.find(s => s.active) || fireData?.scenarios?.[0] || null;
  });

  const annualExpense = $derived(() => {
    return Number(activeScenario()?.annual_expense || fireData?.annual_expense || 0);
  });

  const targetCorpus = $derived(() => {
    return Number(activeScenario()?.target_corpus || 0);
  });

  const yearsRemaining = $derived(() => {
    return fireData?.years_remaining ?? 0;
  });

  const isAccumulationPhase = $derived((yearsRemaining() ?? 0) > 0);

  const fundedRatioPct = $derived(() => {
    const target = targetCorpus();
    return target > 0 ? (currentNetWorth() / target) * 100 : 0;
  });

  const projectedCorpus = $derived(() => {
    return Number(fireData?.projected_corpus_at_target_age || 0);
  });

  const exemptLimit = 125000;
  const exemptGain = $derived(rebalancePlan?.summary?.ltcg_exempt_gain ?? 0);
  const exemptPct = $derived(Math.min(100, (exemptGain / exemptLimit) * 100));

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
        <span>PLAN TRIGGER: {rebalancePlan?.trigger?.reason_label || 'DRIFT RESTORATION (POLICY v3.0)'}</span>
      </div>
      <h2 class="hero-title">Deterministic Capital Rebalance Lab</h2>
      <p class="hero-desc">
        Visual breakdown of automated portfolio rebalancing: Zero-loss legacy phase-outs, and Section 112A ₹1.25L tax exemption shielding.
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

  {#if rebalancePlan?.trigger?.trigger_type === 'CASH_INFLOW_NEEDED'}
    <div class="cash-inflow-banner obsidian-card">
      <div class="cash-inflow-content">
        <div class="cash-inflow-header">
          <AlertCircle size={18} class="text-amber" />
          <strong class="text-amber font-semibold">Underweight Bucket(s) Detected — Fresh Bank Inflow Recommended</strong>
        </div>
        <p class="cash-inflow-desc text-muted">
          {rebalancePlan?.trigger?.reason_label || 'Underweight bucket(s) detected. Fresh capital inflow recommended (0 sells required).'}
          No asset sales required. Click below to deploy fresh bank savings directly into underweight targets without tax drag.
        </p>
      </div>
      <button type="button" class="lumpsum-cta-btn highlight" onclick={onopenlumpsum}>
        <Coins size={16} />
        <span>Deploy Bank Savings</span>
      </button>
    </div>
  {/if}

  <!-- SECTION 1: VISUAL WATERFALL & ROUTING PIPELINE -->
  <div class="visual-panel obsidian-card">
    <div class="panel-header">
      <div class="header-left">
        <div class="icon-bubble cyan">
          <Workflow size={18} />
        </div>
        <div>
          <h3 class="panel-title">Visual Capital Rebalancing Waterfall</h3>
          <span class="panel-sub">Finance Act 2024 & Policy v3.0 execution pipeline: Step-by-step routing flow</span>
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
                        ({formatCompactINR(metrics.curVal, quietMode)} ➔ {formatCompactINR(metrics.postVal, quietMode)})
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
            {/if}
          </div>
        </div>

        <div class="provenance-divider">
          <div class="prov-arrow">➔</div>
          <span class="prov-tax-pill mono">Tax drag: {formatINR(rebalancePlan?.summary?.projected_tax ?? 0, quietMode)}</span>
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
                              ({formatCompactINR(fb.pre_valuation, quietMode)} ➔ {formatCompactINR(fb.post_valuation, quietMode)})
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
    <div class="step-narrative-panel">
      {#if activeWaterfallStep === 0}
        <div class="narrative-content">
          <h4 class="narrative-headline">
            <span class="narrative-tag rose">Step 0: Legacy Phase-out</span>
            Immediate Liquidation of Drifting / High-TER Legacy Holdings
          </h4>
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
          <h4 class="narrative-headline">
            <span class="narrative-tag emerald">Step 1: Capital Preservation</span>
            Maintaining 10% Floor and 15% Cap in Zero-Loss Arbitrage
          </h4>
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
          <h4 class="narrative-headline">
            <span class="narrative-tag amber">Step 2: Real Asset Hedge</span>
            Floor Backstop: 5% Minimum to 10% Tactical Allocation
          </h4>
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
          <h4 class="narrative-headline">
            <span class="narrative-tag cyan">Step 3: Equity Compounding</span>
            Core 50% Anchor & Satellite 35% Factor Alpha Inflows
          </h4>
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
      </div>

      <div class="bars-container">
        {#if rebalancePlan?.buy_side?.buckets && rebalancePlan.buy_side.buckets.length > 0}
          {#each rebalancePlan.buy_side.buckets as b}
            {@const alloc = b.amount_allocated ?? 0}
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
      </div>
    </div>

    <!-- Tax-Shield & Exemption Gate Card -->
    <div class="tax-shield-card obsidian-card">
      <div class="card-top-row">
        <div class="top-title">
          <ShieldCheck size={16} class="text-emerald" />
          <h3 class="card-h">Section 112A Exemption Shield</h3>
        </div>
        <span class="tax-act-pill mono">Finance Act 2024</span>
      </div>

      <!-- Exemption Cap Gauge -->
      <div class="exemption-gauge-box">
        <div class="gauge-head">
          <span class="g-label">Annual Tax-Free LTCG Allowance (₹1.25 Lakh Cap)</span>
          <span class="g-val mono bold text-emerald">{formatINR(exemptGain, quietMode)} / {quietMode ? '••••••' : '₹1.25L'}</span>
        </div>
        <div class="gauge-track">
          <div class="gauge-fill" style="width: {exemptPct}%;"></div>
        </div>
        <span class="gauge-sub text-muted">
          {exemptLimit - exemptGain > 0 ? `${formatINR(exemptLimit - exemptGain, quietMode)} exemption headroom remaining this financial year` : '100% of tax-free harvest capacity utilized!'}
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
          Our FIFO tax engine checks every lot against statutory acquisition dates. Units held &gt;12 months are harvested tax-free up to ₹1.25 Lakh. Rebalance proceeds are immediately recycled into underweight buckets without dividend tax drag.
        </p>
      </div>
    </div>
  </div>

  <!-- SECTION 3: RETIREMENT WEALTH TRAJECTORY -->
  <div class="visual-panel obsidian-card">
    <div class="panel-header">
      <div class="header-left">
        <div class="icon-bubble cyan">
          <Activity size={18} />
        </div>
        <div>
          <h3 class="panel-title">Retirement Corpus Trajectory & Milestones</h3>
          <span class="panel-sub">Inflation-adjusted projection of retirement corpus longevity</span>
        </div>
      </div>
      {#if fireData?.status}
        <div class="confidence-pill mono">
          <span>STATUS:</span>
          <strong class={fireData.status === 'ON_TRACK' || fireData.status === 'FIRE_READY' ? 'text-emerald' : 'text-amber'}>{fireData.status}</strong>
        </div>
      {/if}
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
          <span class="m-sub mono text-cyan">{fireData.years_remaining ?? '—'} Years Remaining to Target Age</span>
        </div>
        <div class="milestone-box highlight">
          <span class="m-title">Projected Corpus at Target</span>
          <span class="m-val mono bold text-emerald">{formatCompactINR(Number(fireData.projected_corpus_at_target_age) || 0, quietMode)}</span>
          {#if Number(fireData.shortage_or_surplus_amount) >= 0}
            <span class="m-sub text-emerald">✓ On Track (Surplus +{formatCompactINR(Number(fireData.shortage_or_surplus_amount) || 0, quietMode)})</span>
          {:else}
            <span class="m-sub text-rose">⚠ Shortfall ({formatCompactINR(Number(fireData.shortage_or_surplus_amount) || 0, quietMode)})</span>
          {/if}
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
            class="scenario-visual-item" 
            class:selected={isSelected}
            onclick={() => selectedScenario = sc.label}
            role="button"
            tabindex="0"
            onkeydown={(e) => { if (e.key === 'Enter') selectedScenario = sc.label; }}
          >
            <div class="sc-top">
              <span class="sc-label font-medium">{sc.label}</span>
              <span class="sc-funded-badge mono bold text-emerald">{fundedPct.toFixed(0)}% funded</span>
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

  :focus-visible {
    outline: 2px solid var(--accent-cyan);
    outline-offset: 2px;
  }

  /* Hero Banner */
  .hero-banner {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 1.25rem 1.5rem;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
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
    font-weight: 500;
    color: var(--accent-cyan);
    background: var(--accent-cyan-bg);
    border: 1px solid var(--border-hairline);
    padding: 2px 8px;
    border-radius: var(--radius-xs);
    width: fit-content;
  }

  .hero-title {
    font-size: 1.15rem;
    font-weight: 600;
    color: var(--text-main);
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
    padding: 0.55rem 1.15rem;
    background: var(--surface-void);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    color: var(--accent-cyan);
    font-size: 0.85rem;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
  }
  .lumpsum-cta-btn:hover {
    background: var(--surface-card-hover);
    color: var(--text-main);
    border-color: var(--accent-cyan);
  }

  .lumpsum-cta-btn.highlight {
    background: rgba(6, 182, 212, 0.1);
    border-color: var(--accent-cyan);
    color: var(--accent-cyan);
  }

  .cash-inflow-banner {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    margin-bottom: 16px;
    background: var(--surface-card);
    border: 1px solid rgba(245, 158, 11, 0.3);
    border-radius: var(--radius-lg);
    gap: 16px;
  }
  .cash-inflow-content {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .cash-inflow-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.95rem;
  }
  .cash-inflow-desc {
    font-size: 0.85rem;
    margin: 0;
    line-height: 1.4;
  }

  /* Panels */
  .visual-panel {
    padding: 18px 20px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    display: flex;
    flex-direction: column;
    gap: 16px;
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
    width: 32px;
    height: 32px;
    border-radius: var(--radius-xs);
  }
  .icon-bubble.cyan {
    background: var(--accent-cyan-bg);
    border: 1px solid var(--border-hairline);
    color: var(--accent-cyan);
  }

  .panel-title {
    font-size: 1rem;
    font-weight: 600;
    color: var(--text-main);
    margin: 0;
  }

  .panel-sub {
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .waterfall-total-badge, .confidence-pill {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.8rem;
    padding: 3px 8px;
    border-radius: var(--radius-xs);
    background: var(--surface-void);
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
    padding: 10px 12px;
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    cursor: pointer;
    transition: background-color 0.15s ease, border-color 0.15s ease;
  }
  .step-node:hover, .step-node.active {
    background: var(--surface-card-hover);
    border-color: var(--border-highlight);
  }

  .step-num {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: var(--radius-xs);
    background: var(--surface-elevated);
    font-family: var(--font-mono);
    font-weight: 600;
    font-size: 0.72rem;
    color: var(--text-main);
  }
  .step-node.active .step-num {
    background: var(--accent-cyan);
    color: var(--surface-void);
  }

  .step-meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .step-title {
    font-size: 0.82rem;
    font-weight: 500;
    color: var(--text-main);
  }
  .step-status {
    font-size: 0.68rem;
    font-family: var(--font-mono);
  }

  .pipeline-arrow {
    color: var(--text-muted);
    font-size: 0.85rem;
  }

  /* Step Narrative Panel */
  .step-narrative-panel {
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    padding: 14px 18px;
  }

  .narrative-tag {
    font-family: var(--font-sans);
    font-size: 0.7rem;
    font-weight: 500;
    padding: 2px 7px;
    border-radius: var(--radius-xs);
    width: fit-content;
    margin-bottom: 6px;
  }
  .narrative-tag.rose { background: var(--color-bad-soft); color: var(--color-bad); border: 1px solid var(--border-hairline); }
  .narrative-tag.emerald { background: var(--color-ok-soft); color: var(--color-ok); border: 1px solid var(--border-hairline); }
  .narrative-tag.amber { background: var(--color-accent-soft); color: var(--color-accent); border: 1px solid var(--border-hairline); }
  .narrative-tag.cyan { background: var(--accent-cyan-bg); color: var(--accent-cyan); border: 1px solid var(--border-hairline); }

  .narrative-headline {
    font-size: 0.95rem;
    font-weight: 600;
    color: var(--text-main);
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
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
    padding: 8px 10px;
  }
  .bullet-label {
    font-size: 0.7rem;
    color: var(--text-muted);
    font-weight: 500;
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
    padding: 18px 20px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
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
    font-weight: 600;
    color: var(--text-main);
    margin: 0;
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
    color: var(--text-main);
    font-weight: 500;
  }
  .b-stats {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.78rem;
  }

  .visual-bar-track {
    position: relative;
    height: 8px;
    background: var(--surface-void);
    border-radius: var(--radius-xs);
    overflow: visible;
  }

  .bar-fill {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    border-radius: var(--radius-xs);
  }
  .current-fill {
    background: var(--color-cool);
    opacity: 0.7;
  }
  .post-fill {
    border: 1px solid var(--color-ok);
  }

  .target-marker {
    position: absolute;
    top: -3px;
    bottom: -3px;
    width: 2px;
    background: var(--text-main);
    border-radius: 1px;
    z-index: 2;
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
    background: var(--surface-void);
    border-radius: var(--radius-xs);
    color: var(--text-muted);
    border: 1px solid var(--border-hairline);
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
  .box { width: 9px; height: 9px; border-radius: 2px; }
  .box.current { background: var(--color-cool); opacity: 0.7; }
  .box.post { border: 1px solid var(--color-ok); }
  .line { width: 12px; height: 2px; }
  .line.target { background: var(--text-main); }

  /* Tax Shield Card */
  .tax-act-pill {
    font-size: 0.65rem;
    font-weight: 500;
    padding: 2px 6px;
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
  }

  .exemption-gauge-box {
    display: flex;
    flex-direction: column;
    gap: 6px;
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    padding: 12px 14px;
  }
  .gauge-head {
    display: flex;
    justify-content: space-between;
    font-size: 0.75rem;
  }
  .gauge-track {
    height: 6px;
    background: var(--surface-elevated);
    border-radius: var(--radius-xs);
    overflow: hidden;
  }
  .gauge-fill {
    height: 100%;
    background: var(--color-ok);
    border-radius: var(--radius-xs);
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
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
  }
  .tax-tile.highlight {
    background: var(--surface-elevated);
    border-color: var(--border-subtle);
  }
  .t-label {
    font-size: 0.68rem;
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
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
    padding: 10px 12px;
  }
  .tax-expl-text {
    font-size: 0.75rem;
    color: var(--text-muted);
    line-height: 1.4;
    margin: 0;
  }


  .milestones-story-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }
  .milestone-box {
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .milestone-box.highlight {
    background: var(--surface-card-hover);
    border-color: var(--border-subtle);
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

  /* Scenario Items */
  .scenarios-flex-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 14px;
  }

  .scenario-visual-item {
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    padding: 14px 16px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    cursor: pointer;
    transition: background-color 0.15s ease, border-color 0.15s ease;
  }
  .scenario-visual-item:hover, .scenario-visual-item.selected {
    background: var(--surface-card-hover);
    border-color: var(--border-highlight);
  }

  .sc-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .sc-label {
    font-size: 0.88rem;
    color: var(--text-main);
  }
  .sc-funded-badge {
    font-size: 0.68rem;
    padding: 1px 6px;
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border-radius: var(--radius-xs);
    border: 1px solid var(--border-hairline);
  }

  .sc-corpus {
    font-size: 1.25rem;
  }

  .sc-progress-bar {
    height: 5px;
    background: var(--surface-elevated);
    border-radius: var(--radius-xs);
    overflow: hidden;
  }
  .sc-progress-fill {
    height: 100%;
    background: var(--accent-cyan);
    border-radius: var(--radius-xs);
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
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
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
    font-weight: 500;
    padding: 2px 7px;
    border-radius: var(--radius-xs);
    align-self: flex-start;
  }
  .prov-badge.rose {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--border-hairline);
  }
  .prov-badge.cyan {
    background: var(--accent-cyan-bg);
    color: var(--accent-cyan);
    border: 1px solid var(--border-hairline);
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
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
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
    color: var(--text-main);
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
    font-size: 1.1rem;
    color: var(--accent-cyan);
  }

  .prov-tax-pill {
    font-size: 0.65rem;
    background: var(--color-ok-soft);
    color: var(--color-ok);
    padding: 2px 7px;
    border-radius: var(--radius-xs);
    border: 1px solid var(--border-hairline);
    white-space: nowrap;
  }

  .prov-bucket-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
    padding: 8px 10px;
  }

  .prov-item.bucket-level {
    background: transparent;
    border: none;
    padding: 2px 0 6px 0;
    border-bottom: 1px solid var(--border-hairline);
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
    background: var(--surface-void);
    padding: 4px 8px;
    border-radius: var(--radius-xs);
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
    font-weight: 600;
  }

  .fund-name {
    color: var(--text-main);
  }

  .fund-impact-tag {
    font-size: 0.68rem;
    padding: 1px 6px;
    border-radius: var(--radius-xs);
    background: var(--surface-card);
  }
  .fund-impact-tag.buy {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--border-hairline);
  }
  .fund-impact-tag.sell {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--border-hairline);
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
    .narrative-bullet-grid, .milestones-story-grid, .scenarios-flex-grid {
      grid-template-columns: 1fr;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .lumpsum-cta-btn, .step-node, .scenario-visual-item {
      transition: none !important;
    }
  }
</style>
