<script lang="ts">
  import { onMount } from 'svelte';
  import {
    fetchJson,
    confirmRebalanceExecution,
    type HoldingDto,
    type PolicyMetaDto,
    type RebalancePlanDto
  } from '../api';
  import { formatCompactINR, formatINR } from '../formatters';
  import DrawdownRungsChart from './DrawdownRungsChart.svelte';
  import CashRouterPanel from './CashRouterPanel.svelte';
  import {
    CheckCircle2,
    AlertTriangle,
    ArrowRight,
    ShieldCheck,
    Sliders,
    AlertCircle
  } from '@lucide/svelte';

  interface Props {
    rebalancePlan?: RebalancePlanDto;
    holdings?: HoldingDto[];
    policyMeta?: PolicyMetaDto;
    netWorth?: number;
    quietMode?: boolean;
    isOffline?: boolean;
    onreloadsnapshot?: () => Promise<void> | void;
  }

  let {
    rebalancePlan = undefined,
    holdings = [],
    policyMeta = undefined,
    netWorth = 0,
    quietMode = false,
    isOffline = false,
    onreloadsnapshot
  }: Props = $props();

  interface FireSummary {
    active_scenario_label?: string;
    required_corpus?: string;
    fire_investable_net_worth?: string;
    total_net_worth?: string;
    projected_corpus_at_target_age?: string;
    years_remaining?: number;
    status?: string;
    shortage_or_surplus_amount?: string;
  }

  let fireData = $state<FireSummary | null>(null);
  let fireError = $state<string | null>(null);
  let loadingFire = $state(false);

  let prefillCashAmount = $state<number | null>(null);
  let confirmStep = $state<'idle' | 'pending' | 'submitting'>('idle');
  let confirmMessage = $state<{ tone: 'ok' | 'warn' | 'danger'; text: string } | null>(null);

  async function loadFireSummary() {
    loadingFire = true;
    fireError = null;
    try {
      fireData = await fetchJson<FireSummary>('/fire/summary');
    } catch (e: any) {
      fireError = e?.message || 'Unable to load FIRE summary from Core Node.';
    } finally {
      loadingFire = false;
    }
  }

  onMount(() => {
    loadFireSummary();
  });

  const triggerType = $derived((rebalancePlan?.trigger?.trigger_type || 'NONE').toUpperCase());
  const sellOrders = $derived(rebalancePlan?.sell_side?.orders ?? rebalancePlan?.orders ?? []);
  const buyBuckets = $derived(
    (rebalancePlan?.buy_side?.buckets ?? []).filter((b) => Number(b.amount_allocated ?? 0) > 0)
  );
  const cashOverflow = $derived(Number(rebalancePlan?.buy_side?.cash_overflow_inr ?? 0));
  const taxSummary = $derived(rebalancePlan?.sell_side?.tax_summary);

  const activeCorpus = $derived(
    (rebalancePlan?.buckets ?? [])
      .filter((b) => b.bucket !== 'LEGACY_HOLDINGS')
      .reduce((s, b) => s + (Number(b.current_value) || 0), 0) || netWorth
  );

  const underweightDeficits = $derived.by(() => {
    return (rebalancePlan?.buckets ?? [])
      .filter((b) => b.bucket !== 'LEGACY_HOLDINGS' && (b.current_pct || 0) < (b.target_pct || 0))
      .map((b) => {
        const deficit = ((Number(b.target_pct) - Number(b.current_pct)) / 100) * activeCorpus;
        return {
          bucket: b.bucket,
          currentPct: Number(b.current_pct || 0),
          targetPct: Number(b.target_pct || 0),
          deficitInr: Math.max(0, Math.round(deficit))
        };
      })
      .filter((d) => d.deficitInr > 0);
  });

  const totalUnderweightDeficit = $derived(
    underweightDeficits.reduce((s, d) => s + d.deficitInr, 0)
  );

  function getHoldingPrePost(fundIdOrName: string, proceeds: number) {
    const nw = netWorth || Number(rebalancePlan?.summary?.net_worth ?? 0);
    const h = holdings.find(
      (item) =>
        item.isin?.toLowerCase() === fundIdOrName.toLowerCase() ||
        item.asset_id?.toLowerCase() === fundIdOrName.toLowerCase() ||
        item.asset_name?.toLowerCase().includes(fundIdOrName.toLowerCase())
    );
    if (!h || nw <= 0) return null;
    const prePct = (h.current_value / nw) * 100;
    const postPct = (Math.max(0, h.current_value - proceeds) / nw) * 100;
    return { prePct, postPct };
  }

  async function handleConfirmExecution() {
    if (isOffline) return;
    if (confirmStep === 'idle') {
      confirmStep = 'pending';
      confirmMessage = null;
      return;
    }
    if (confirmStep !== 'pending') return;

    const planId = rebalancePlan?.plan_id || '';
    const digest = rebalancePlan?.plan_digest || '';
    if (!digest) {
      confirmMessage = { tone: 'danger', text: 'Missing plan digest — reload snapshot first.' };
      confirmStep = 'idle';
      return;
    }

    confirmStep = 'submitting';
    try {
      const res = await confirmRebalanceExecution(planId, digest, rebalancePlan?.trigger?.trigger_type);
      if (res.status === 'CONFLICT') {
        confirmMessage = { tone: 'warn', text: 'Plan expired (data changed) — reloaded' };
        confirmStep = 'idle';
        await onreloadsnapshot?.();
        return;
      }
      confirmMessage = { tone: 'ok', text: `Execution confirmed (${digest.slice(0, 10)})` };
      confirmStep = 'idle';
      await onreloadsnapshot?.();
    } catch (e: any) {
      confirmMessage = { tone: 'danger', text: e?.message || 'Confirmation failed' };
      confirmStep = 'idle';
    }
  }

  const fireToday = $derived(Number(fireData?.fire_investable_net_worth ?? fireData?.total_net_worth ?? 0));
  const fireProjected = $derived(Number(fireData?.projected_corpus_at_target_age ?? 0));
  const fireRequired = $derived(Number(fireData?.required_corpus ?? 0));
  const fireDelta = $derived(Number(fireData?.shortage_or_surplus_amount ?? (fireProjected - fireRequired)));
  const fireOnTrack = $derived(fireData?.status === 'ON_TRACK');
</script>

<div class="rebalance-cockpit">
  <!-- 1. Config-Driven Drawdown Ruler -->
  <DrawdownRungsChart
    drawdownPct={Number(rebalancePlan?.trigger?.drawdown_context?.current_drawdown_pct ?? 0)}
    drawdownContext={rebalancePlan?.trigger?.drawdown_context}
    {policyMeta}
    {holdings}
    {quietMode}
    status={rebalancePlan?.trigger?.drawdown_context?.status ?? 'AVAILABLE'}
  />

  <!-- 2. State-Driven Live Execution Plan -->
  <section class="exec-card" aria-label="Live Execution Plan">
    <div class="exec-header">
      <div class="exec-title-group">
        <Sliders size={15} class="exec-icon" />
        <h3 class="exec-title">Live Execution Plan</h3>
        <span class="trigger-pill">{triggerType}</span>
      </div>
      {#if rebalancePlan?.plan_digest}
        <span class="digest-chip mono" title="Plan Digest">#{rebalancePlan.plan_digest.slice(0, 10)}</span>
      {/if}
    </div>

    {#if confirmMessage}
      <div class="confirm-banner banner-{confirmMessage.tone}">
        <span>{confirmMessage.text}</span>
      </div>
    {/if}

    {#if triggerType === 'NONE' || (sellOrders.length === 0 && buyBuckets.length === 0 && triggerType !== 'CASH_INFLOW_NEEDED')}
      <div class="calm-inband">
        <CheckCircle2 size={16} class="ok-icon" />
        <span>All buckets within Policy {policyMeta?.version || 'v3.0'} corridors. Zero trades required.</span>
      </div>
    {:else if triggerType === 'CASH_INFLOW_NEEDED'}
      <div class="cash-needed-box">
        <div class="cash-needed-top">
          <div class="cash-needed-title">
            <AlertTriangle size={16} />
            <strong>Underweight Bucket Deficit · 0 Sells · 0 Buys</strong>
          </div>
          {#if totalUnderweightDeficit > 0}
            <button
              type="button"
              class="route-cta tnum"
              onclick={() => { prefillCashAmount = totalUnderweightDeficit; }}
            >
              Route <span data-sensitive="currency">{formatCompactINR(totalUnderweightDeficit, quietMode)}</span> Fresh Cash
            </button>
          {/if}
        </div>
        <div class="deficit-list tnum">
          {#each underweightDeficits as d}
            <div class="deficit-row">
              <span>{d.bucket.replace(/_/g, ' ')} ({d.currentPct.toFixed(1)}% / {d.targetPct.toFixed(1)}%)</span>
              <span data-sensitive="currency">-{formatINR(d.deficitInr, quietMode)}</span>
            </div>
          {/each}
        </div>
      </div>
    {:else}
      <!-- 3-Column Capital Flow Ledger -->
      <div class="ledger-grid">
        <!-- Column 1: Sell Sources (FIFO) -->
        <div class="ledger-col">
          <div class="col-head">SELL SOURCES (FIFO)</div>
          {#if sellOrders.length === 0}
            <div class="col-empty">No sells required</div>
          {:else}
            {#each sellOrders as o}
              {@const shift = getHoldingPrePost(o.fund_id || o.fund_name, Number(o.total_proceeds || 0))}
              <div class="order-item">
                <div class="order-top">
                  <span class="order-name">{o.fund_name}</span>
                  <span class="order-amt tnum" data-sensitive="currency">{formatINR(o.total_proceeds, quietMode)}</span>
                </div>
                <div class="order-meta tnum">
                  {#if o.lot_count}<span>{o.lot_count} lots</span><span>·</span>{/if}
                  {#if o.summary_label}<span>{o.summary_label}</span>{/if}
                  {#if o.total_units}<span>· {Number(o.total_units).toFixed(2)} u</span>{/if}
                </div>
                {#if shift}
                  <div class="order-shift tnum">
                    Share: {shift.prePct.toFixed(1)}% → {shift.postPct.toFixed(1)}%
                  </div>
                {/if}
              </div>
            {/each}
          {/if}
        </div>

        <!-- Column 2: Tax Gate -->
        <div class="ledger-col">
          <div class="col-head">TAX GATE &amp; SEC 112A</div>
          <div class="tax-box tnum">
            <div class="tax-row">
              <span>Est. Total Tax</span>
              <strong class="tax-est" data-sensitive="currency">
                {formatINR(taxSummary?.total_tax_estimate ?? rebalancePlan?.summary?.projected_tax ?? 0, quietMode)}
              </strong>
            </div>
            <div class="tax-row">
              <span>LTCG Exempt</span>
              <span data-sensitive="currency">{formatINR(taxSummary?.total_ltcg_exempt ?? 0, quietMode)}</span>
            </div>
            <div class="tax-row">
              <span>STCG Taxable</span>
              <span data-sensitive="currency">{formatINR(taxSummary?.total_stcg_taxable ?? 0, quietMode)}</span>
            </div>
            {#if taxSummary?.statutory_exemption_cap}
              {@const cap = taxSummary.statutory_exemption_cap || 125000}
              {@const after = taxSummary.exemption_headroom_after ?? 0}
              <div class="exemption-meter">
                <div class="meter-label">
                  <span>112A Headroom</span>
                  <span data-sensitive="currency">{formatCompactINR(after, quietMode)} left</span>
                </div>
                <div class="meter-track">
                  <div class="meter-fill" style="width: {Math.min(100, Math.max(0, ((cap - after) / cap) * 100))}%;"></div>
                </div>
              </div>
            {/if}
          </div>
        </div>

        <!-- Column 3: Buy Destinations -->
        <div class="ledger-col">
          <div class="col-head">BUY DESTINATIONS</div>
          {#if buyBuckets.length === 0 && cashOverflow <= 0}
            <div class="col-empty">No buys required</div>
          {:else}
            {#each buyBuckets as b}
              <div class="order-item">
                <div class="order-top">
                  <span class="order-name">{b.bucket.replace(/_/g, ' ')}</span>
                  <span class="order-amt buy tnum" data-sensitive="currency">+{formatINR(b.amount_allocated, quietMode)}</span>
                </div>
                <div class="order-meta tnum">
                  <span>{Number(b.current_pct ?? 0).toFixed(1)}% → {Number(b.post_rebalance_pct ?? b.post_pct ?? b.target_pct ?? 0).toFixed(1)}%</span>
                  <span>(Target {Number(b.target_pct ?? 0).toFixed(0)}%)</span>
                </div>
                {#if b.fund_breakdown && b.fund_breakdown.length > 0}
                  <div class="sub-buys">
                    {#each b.fund_breakdown.filter((f) => Number(f.amount) > 0) as f}
                      <div class="sub-buy-row tnum">
                        <span>↳ {f.fund_name}</span>
                        <span data-sensitive="currency">+{formatINR(f.amount, quietMode)}</span>
                      </div>
                    {/each}
                  </div>
                {/if}
              </div>
            {/each}
            {#if cashOverflow > 0}
              <div class="overflow-row tnum">
                <span>Cash Overflow (Unallocated)</span>
                <span data-sensitive="currency">{formatINR(cashOverflow, quietMode)}</span>
              </div>
            {/if}
          {/if}
        </div>
      </div>

      <!-- 2-Step Confirm Execution Control -->
      <div class="confirm-bar">
        {#if confirmStep === 'pending'}
          <div class="confirm-prompt">
            <span class="mono">Digest {rebalancePlan?.plan_digest?.slice(0, 10)}</span>
            <span>Latches 30-day cooldown / active drawdown tier.</span>
          </div>
          <div class="confirm-btns">
            <button type="button" class="cancel-btn" onclick={() => confirmStep = 'idle'}>Cancel</button>
            <button type="button" class="confirm-final-btn" disabled={isOffline} onclick={handleConfirmExecution}>
              Confirm &amp; Latch Plan
            </button>
          </div>
        {:else}
          <button
            type="button"
            class="confirm-init-btn"
            disabled={isOffline || confirmStep === 'submitting' || !rebalancePlan?.plan_digest}
            onclick={handleConfirmExecution}
            title={isOffline ? 'Disabled while viewing offline cache' : 'Confirm execution and latch cooldown'}
          >
            <ShieldCheck size={15} />
            <span>{confirmStep === 'submitting' ? 'Confirming…' : isOffline ? 'Confirm Execution (Offline)' : 'Confirm Execution'}</span>
          </button>
        {/if}
      </div>
    {/if}
  </section>

  <!-- 3. Inline Fresh Bank Cash Router -->
  <CashRouterPanel {holdings} {netWorth} {quietMode} prefillAmount={prefillCashAmount} />

  <!-- 4. Deterministic FIRE Trajectory Strip -->
  <section class="fire-strip" aria-label="Deterministic FIRE Progress">
    {#if fireError}
      <div class="fire-error">
        <AlertCircle size={14} />
        <span>FIRE summary unavailable: {fireError}</span>
      </div>
    {:else if loadingFire && !fireData}
      <div class="fire-loading">Loading deterministic FIRE trajectory…</div>
    {:else if fireData}
      <div class="fire-row tnum">
        <div class="fire-title">
          <strong>FIRE Trajectory</strong>
          <span class="fire-sub">{fireData.active_scenario_label || 'Deterministic · Real Return 6.0% · SWR 3.0%'}</span>
        </div>
        <div class="fire-metrics">
          <span data-sensitive="currency">{formatCompactINR(fireToday, quietMode)} today</span>
          <ArrowRight size={13} class="fire-arrow" />
          <strong data-sensitive="currency">{formatCompactINR(fireProjected, quietMode)} projected</strong>
          <span class="fire-vs">vs</span>
          <span data-sensitive="currency">{formatCompactINR(fireRequired, quietMode)} required</span>
        </div>
        <span class="fire-pill" class:ontrack={fireOnTrack} class:short={!fireOnTrack}>
          {fireOnTrack ? 'ON TRACK' : 'SHORT'}
          <span data-sensitive="currency">
            {fireDelta >= 0 ? '+' : ''}{formatCompactINR(fireDelta, quietMode)}
          </span>
        </span>
      </div>
    {/if}
  </section>
</div>

<style>
  .rebalance-cockpit {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .exec-card,
  .fire-strip {
    background: var(--bg-surface);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-md);
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .exec-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
  }

  .exec-title-group {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  :global(.exec-icon) { color: var(--state-action); }

  .exec-title {
    font-size: 0.92rem;
    font-weight: 600;
    color: var(--ink-primary);
  }

  .trigger-pill,
  .digest-chip {
    font-size: 0.7rem;
    font-weight: 600;
    padding: 2px 7px;
    border-radius: var(--radius-sm);
    background: var(--bg-elevated);
    color: var(--ink-secondary);
    border: 1px solid var(--border-hair);
  }

  .confirm-banner {
    padding: 9px 12px;
    border-radius: var(--radius-sm);
    font-size: 0.8rem;
  }
  .banner-ok { background: var(--state-ok-soft); color: var(--state-ok); }
  .banner-warn { background: var(--state-warn-soft); color: var(--state-warn); }
  .banner-danger { background: var(--state-danger-soft); color: var(--state-danger); }

  .calm-inband {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.84rem;
    color: var(--ink-secondary);
    padding: 4px 0;
  }
  :global(.ok-icon) { color: var(--state-ok); }

  .cash-needed-box {
    background: var(--state-warn-soft);
    border: 1px solid color-mix(in oklch, var(--state-warn) 35%, transparent);
    border-radius: var(--radius-sm);
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .cash-needed-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
  }

  .cash-needed-title {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--state-warn);
    font-size: 0.84rem;
  }

  .route-cta {
    padding: 6px 12px;
    border-radius: var(--radius-sm);
    background: var(--bg-canvas);
    border: 1px solid var(--state-warn);
    color: var(--state-warn);
    font-size: 0.78rem;
    font-weight: 600;
    cursor: pointer;
    min-height: 36px;
  }

  .deficit-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 0.78rem;
    color: var(--ink-primary);
  }

  .deficit-row {
    display: flex;
    justify-content: space-between;
  }

  .ledger-grid {
    display: grid;
    grid-template-columns: 1.1fr 0.8fr 1.1fr;
    gap: 12px;
  }

  .ledger-col {
    background: var(--bg-canvas);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .col-head {
    font-size: 0.68rem;
    font-weight: 600;
    letter-spacing: 0.05em;
    color: var(--ink-muted);
  }

  .col-empty {
    font-size: 0.78rem;
    color: var(--ink-muted);
  }

  .order-item {
    border-top: 1px solid var(--border-hair);
    padding-top: 8px;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .order-top {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    font-size: 0.8rem;
  }

  .order-name {
    font-weight: 600;
    color: var(--ink-primary);
  }

  .order-amt { font-weight: 600; color: var(--state-warn); }
  .order-amt.buy { color: var(--state-action); }

  .order-meta,
  .order-shift {
    display: flex;
    gap: 6px;
    font-size: 0.72rem;
    color: var(--ink-muted);
    flex-wrap: wrap;
  }

  .sub-buys {
    padding-left: 10px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin-top: 3px;
  }

  .sub-buy-row {
    display: flex;
    justify-content: space-between;
    font-size: 0.72rem;
    color: var(--ink-secondary);
  }

  .overflow-row {
    border-top: 1px dashed var(--border-strong);
    padding-top: 6px;
    display: flex;
    justify-content: space-between;
    font-size: 0.76rem;
    color: var(--ink-secondary);
  }

  .tax-box {
    display: flex;
    flex-direction: column;
    gap: 7px;
    font-size: 0.78rem;
  }

  .tax-row {
    display: flex;
    justify-content: space-between;
    color: var(--ink-secondary);
  }

  .tax-est { color: var(--ink-primary); }

  .exemption-meter {
    margin-top: 4px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .meter-label {
    display: flex;
    justify-content: space-between;
    font-size: 0.7rem;
    color: var(--ink-muted);
  }

  .meter-track {
    height: 6px;
    background: var(--bg-elevated);
    border-radius: 999px;
    overflow: hidden;
  }

  .meter-fill {
    height: 100%;
    background: var(--state-ok);
  }

  .confirm-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding-top: 10px;
    border-top: 1px solid var(--border-hair);
    flex-wrap: wrap;
  }

  .confirm-prompt {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 0.78rem;
    color: var(--ink-secondary);
    flex-wrap: wrap;
  }

  .confirm-btns {
    display: flex;
    gap: 8px;
    margin-left: auto;
  }

  .confirm-init-btn,
  .confirm-final-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 7px 14px;
    border-radius: var(--radius-sm);
    background: var(--state-action-soft);
    border: 1px solid color-mix(in oklch, var(--state-action) 40%, transparent);
    color: var(--state-action);
    font-size: 0.8rem;
    font-weight: 600;
    cursor: pointer;
    min-height: 36px;
  }

  .confirm-init-btn:disabled,
  .confirm-final-btn:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }

  .cancel-btn {
    padding: 7px 12px;
    background: var(--bg-elevated);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-sm);
    color: var(--ink-secondary);
    font-size: 0.78rem;
    cursor: pointer;
  }

  .fire-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .fire-title {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .fire-title strong {
    font-size: 0.88rem;
    color: var(--ink-primary);
  }

  .fire-sub {
    font-size: 0.72rem;
    color: var(--ink-muted);
  }

  .fire-metrics {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.82rem;
    color: var(--ink-secondary);
    flex-wrap: wrap;
  }

  .fire-metrics strong {
    color: var(--ink-primary);
  }

  .fire-pill {
    font-size: 0.72rem;
    font-weight: 600;
    padding: 4px 9px;
    border-radius: var(--radius-sm);
    display: inline-flex;
    gap: 5px;
  }

  .fire-pill.ontrack { background: var(--state-ok-soft); color: var(--state-ok); }
  .fire-pill.short { background: var(--state-danger-soft); color: var(--state-danger); }

  .fire-error,
  .fire-loading {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.78rem;
    color: var(--ink-muted);
  }

  .fire-error { color: var(--state-warn); }

  @media (max-width: 768px) {
    .ledger-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
