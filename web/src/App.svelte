<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchJson, getAuthToken, setAuthToken, loadDashboardData, type SnapshotDto, type RebalancePlanDto } from './lib/api';
  import { formatINR, formatPercent, formatCompactINR } from './lib/formatters';
  import HudMetricCard from './lib/components/HudMetricCard.svelte';
  import TimelineChart from './lib/components/TimelineChart.svelte';
  import BucketTable from './lib/components/BucketTable.svelte';
  import TaxLotsTable from './lib/components/TaxLotsTable.svelte';
  import RebalanceFireView from './lib/components/RebalanceFireView.svelte';
  import LumpsumModal from './lib/components/LumpsumModal.svelte';
  import CommandPalette from './lib/components/CommandPalette.svelte';
  import { 
    LayoutDashboard, 
    Receipt, 
    Sliders, 
    RefreshCw, 
    Upload, 
    Download, 
    Eye, 
    EyeOff, 
    Search, 
    Coins, 
    ShieldCheck, 
    AlertTriangle,
    Wallet,
    ChevronDown,
    X,
    KeyRound
  } from '@lucide/svelte';

  type TabType = 'overview' | 'taxlots' | 'rebalance';

  function getInitialTab(): TabType {
    if (typeof window !== 'undefined') {
      const qTab = new URLSearchParams(window.location.search).get('tab');
      if (qTab === 'taxlots' || qTab === 'rebalance' || qTab === 'overview') return qTab;
      if (window.location.hash === '#taxlots') return 'taxlots';
      if (window.location.hash === '#rebalance') return 'rebalance';
    }
    return 'overview';
  }

  let activeTab = $state<TabType>(getInitialTab());
  let snapshot = $state<SnapshotDto | null>(null);
  let loading = $state(true);
  let syncing = $state(false);
  let error = $state<string | null>(null);
  let tokenInputValue = $state(getAuthToken());

  // Quiet Mode State (persisted to localStorage)
  let quietMode = $state<boolean>(false);

  // Modal Dialog States
  let showCommandPalette = $state(false);
  let showLumpsumModal = $state(false);
  let showCasModal = $state(false);
  let casPassword = $state('');
  let casFile = $state<File | null>(null);
  let casUploading = $state(false);
  let casStatusMsg = $state<string | null>(null);

  const gainPct = $derived(snapshot?.portfolio_summary?.unrealized_gain_pct);
  const absGain = $derived(snapshot?.portfolio_summary?.unrealized_gain ?? 0);
  const xirrStr = $derived(snapshot?.portfolio_summary?.formatted_xirr || snapshot?.portfolio_summary?.xirr_percentage || "—");
  const isNegativeReturn = $derived((absGain < 0) || (gainPct !== undefined && gainPct !== null && gainPct < 0) || xirrStr.includes('-'));

  let showTimeline = $state(false);

  const liquidReadyValue = $derived.by(() => {
    const liquidHoldings = (snapshot?.holdings || []).filter((h) =>
      (h.bucket || '').toUpperCase().includes('LIQUID') || (h.bucket || '').toUpperCase().includes('ARBITRAGE')
    );
    const freeFromHoldings = liquidHoldings.reduce((sum, h) => sum + (Number(h.exit_load_free_value ?? h.current_value) || 0), 0);
    const totalLiquid = liquidHoldings.reduce((sum, h) => sum + (Number(h.current_value) || 0), 0);
    const unalloc = Number(snapshot?.portfolio_summary?.unallocated_cash ?? 0);
    const ready = freeFromHoldings + unalloc;
    const locked = Math.max(0, totalLiquid - freeFromHoldings);
    const freePct = totalLiquid > 0 ? Math.round((freeFromHoldings / totalLiquid) * 100) : 100;
    return { ready, locked, freePct };
  });

  const verdict = $derived.by(() => {
    const trig = snapshot?.rebalance_plan?.trigger;
    const tType = (trig?.trigger_type || 'NONE').toUpperCase();
    const ddPct = Number(trig?.drawdown_context?.current_drawdown_pct ?? 0);
    const activeTier = Number(trig?.drawdown_context?.active_tier ?? 0);
    if (tType === 'DRAWDOWN' || activeTier > 0) {
      return {
        tone: 'danger',
        headline: `DRAWDOWN RUNG ${activeTier || 1} ARMED — DEPLOYMENT READY`,
        sub: trig?.reason_label || 'Drawdown deployment threshold reached',
        ddPct
      };
    }
    if (tType === 'CASH_INFLOW_NEEDED') {
      return {
        tone: 'warn',
        headline: 'FRESH CASH NEEDED TO RESTORE BANDS',
        sub: trig?.reason_label || 'Underweight bucket below corridor',
        ddPct
      };
    }
    if (tType === 'DRIFT' || tType === 'SCHEDULED') {
      return {
        tone: 'action',
        headline: 'REBALANCE READY — REVIEW EXECUTION PLAN',
        sub: trig?.reason_label || 'Policy rebalance triggered',
        ddPct
      };
    }
    return {
      tone: 'ok',
      headline: 'ALL BUCKETS IN BAND — NO ACTION REQUIRED',
      sub: trig?.reason_label || 'Portfolio within policy corridors',
      ddPct
    };
  });

  async function loadSnapshot() {
    loading = true;
    error = null;
    try {
      const data = await loadDashboardData();
      snapshot = data;
    } catch (e: any) {
      error = e.message || 'Failed to fetch snapshot';
    } finally {
      loading = false;
    }
  }

  function handleSaveToken() {
    if (tokenInputValue && tokenInputValue.trim()) {
      setAuthToken(tokenInputValue.trim());
      loadSnapshot();
    }
  }

  async function triggerSync() {
    syncing = true;
    try {
      await fetchJson('/sync/refresh', { method: 'POST' });
      await loadSnapshot();
    } catch (e: any) {
      alert('AMFI NAV Sync Failed: ' + e.message);
    } finally {
      syncing = false;
    }
  }

  function toggleQuietMode() {
    quietMode = !quietMode;
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('QUIET_MODE', quietMode ? 'true' : 'false');
    }
  }

  async function exportItr2Zip() {
    try {
      const token = getAuthToken();
      const headers: Record<string, string> = {};
      if (token) {
        headers['X-Api-Auth-Token'] = token;
      }
      const response = await fetch('/api/v1/tax/export/itr2/zip', {
        headers
      });
      if (!response.ok) {
        throw new Error(`Export failed with HTTP ${response.status}`);
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `ITR2_Schedule112A_${new Date().toISOString().slice(0, 10)}.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err: any) {
      console.error('Failed to download ITR-2 zip:', err);
      error = err?.message || 'Failed to download ITR-2 zip';
    }
  }

  async function handleCasUpload() {
    if (!casFile) {
      casStatusMsg = 'Please choose a CAS PDF / Excel file first.';
      return;
    }
    casUploading = true;
    casStatusMsg = 'Decrypting and extracting tax lots with Python sidecar...';
    try {
      const formData = new FormData();
      formData.append('file', casFile);
      if (casPassword) {
        formData.append('password', casPassword);
      }
      const token = getAuthToken();
      const headers = new Headers();
      if (token) headers.set('X-Api-Auth-Token', token);

      const resp = await fetch('/api/v1/statements/upload', {
        method: 'POST',
        headers,
        body: formData
      });

      if (!resp.ok) {
        throw new Error(`Upload failed (${resp.status}): ${await resp.text()}`);
      }
      casStatusMsg = 'Statements parsed & ledger synced successfully!';
      setTimeout(() => {
        showCasModal = false;
        casStatusMsg = null;
        casFile = null;
        casPassword = '';
        loadSnapshot();
      }, 1200);
    } catch (e: any) {
      casStatusMsg = `Error: ${e.message}`;
    } finally {
      casUploading = false;
    }
  }

  // Keyboard shortcut listener for ⌘K and Tab jumps
  function handleGlobalKeyDown(e: KeyboardEvent) {
    const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
    const modifier = isMac ? e.metaKey : e.ctrlKey;

    if (modifier && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      showCommandPalette = !showCommandPalette;
    } else if (modifier && e.key === '1') {
      e.preventDefault();
      activeTab = 'overview';
    } else if (modifier && e.key === '2') {
      e.preventDefault();
      activeTab = 'taxlots';
    } else if (modifier && e.key === '3') {
      e.preventDefault();
      activeTab = 'rebalance';
    } else if (modifier && e.key.toLowerCase() === 'l') {
      e.preventDefault();
      showLumpsumModal = true;
    } else if (modifier && e.key === '.') {
      e.preventDefault();
      toggleQuietMode();
    }
  }

  onMount(() => {
    if (typeof localStorage !== 'undefined') {
      const savedQuiet = localStorage.getItem('QUIET_MODE');
      if (savedQuiet === 'true') {
        quietMode = true;
      }
    }

    // Expose window handlers for tests and backward compatibility
    if (typeof window !== 'undefined') {
      (window as any).openTaxLotInspector = () => { activeTab = 'taxlots'; };
      (window as any).closeTaxLotInspector = () => {};
      (window as any).openItrOverlay = exportItr2Zip;
      (window as any).closeItrOverlay = () => {};
      (window as any).openLumpsumModal = () => { showLumpsumModal = true; };
      (window as any).closeLumpsumModal = () => { showLumpsumModal = false; };
      (window as any).closeCasPasswordModal = () => { showCasModal = false; };
      (window as any).toggleBucketRow = () => {};
      (window as any).toggleAllBuckets = () => {};
    }

    loadSnapshot();
  });
</script>

<svelte:window onkeydown={handleGlobalKeyDown} />

<div class="obsidian-app" data-quiet={quietMode ? 'true' : 'false'}>
  <!-- Top App Bar / Header -->
  <header class="app-header">
    <div class="brand-group">
      <div class="brand-pip"></div>
      <div class="brand-text">
        <h1 class="brand-name">Portfolio OS</h1>
        <span class="brand-edition">Console v4.0</span>
      </div>
      {#if snapshot?.sync_info}
        <div class="freshness-tag">
          <span class="live-dot" class:stale={snapshot.sync_info.stale_nav_count > 0}></span>
          <span>{snapshot.sync_info.status}</span>
          {#if snapshot.sync_info.ledger_hmac_verified}
            <span class="hmac-verified" title="Ledger cryptographic integrity verified">HMAC Secure</span>
          {/if}
        </div>
      {/if}
    </div>

    <!-- Quick Action Toolbelt -->
    <div class="toolbelt">
      <!-- Search / Command Palette Button -->
      <button 
        type="button" 
        class="action-btn cmd-palette-trigger" 
        onclick={() => showCommandPalette = true}
        title="Open Command Palette (⌘K)"
      >
        <Search size={15} />
        <span class="btn-text">Commands</span>
        <kbd class="mono">⌘K</kbd>
      </button>

      <!-- Lumpsum Simulator Trigger -->
      <button 
        type="button" 
        class="action-btn highlight-cyan" 
        onclick={() => showLumpsumModal = true}
        title="Simulate Lump-Sum Inflow"
      >
        <Coins size={15} />
        <span class="btn-text">Deploy Cash</span>
      </button>

      <!-- CAS Upload Trigger -->
      <button 
        type="button" 
        class="action-btn" 
        onclick={() => showCasModal = true}
        title="Upload CAS E-Statement"
      >
        <Upload size={15} />
        <span class="btn-text">Import CAS</span>
      </button>

      <!-- ITR-2 Export -->
      <button 
        type="button" 
        class="action-btn" 
        onclick={exportItr2Zip}
        title="Export Schedule 112A & STCG CSV"
      >
        <Download size={15} />
        <span class="btn-text">ITR-2</span>
      </button>

      <!-- Quiet Mode Toggle (Zen Mode) -->
      <button 
        type="button" 
        class="action-btn quiet-toggle" 
        class:active={quietMode}
        onclick={toggleQuietMode}
        title={quietMode ? 'Reveal Figures (Zen Mode Active)' : 'Shield Figures (Zen Mode Inactive)'}
      >
        {#if quietMode}
          <EyeOff size={15} class="text-amber" />
          <span class="btn-text">Zen Mode</span>
        {:else}
          <Eye size={15} />
          <span class="btn-text">Live Mode</span>
        {/if}
      </button>

      <!-- Sync Button -->
      <button 
        type="button" 
        class="action-btn sync-btn highlight-cyan" 
        onclick={triggerSync}
        disabled={syncing || Boolean(snapshot?.is_offline_cache)}
        title={snapshot?.is_offline_cache ? 'Sync disabled while offline' : 'Sync Latest AMFI NAVs'}
      >
        <RefreshCw size={15} class={syncing ? 'spin' : ''} />
        <span class="btn-text">{syncing ? 'Syncing...' : 'Sync NAVs'}</span>
      </button>
    </div>
  </header>

  <!-- Main Content Layout -->
  <main class="main-body">
    {#if snapshot?.is_offline_cache}
      <div class="offline-banner" role="status">
        <AlertTriangle size={15} />
        <span>
          <strong>OFFLINE CACHE · Read-Only</strong>
          {#if snapshot.sync_info?.last_sync_timestamp}
            (Synced {new Date(snapshot.sync_info.last_sync_timestamp).toLocaleString()})
          {/if}
          — Confirm Execution and Sync NAVs are disabled until Core Node reconnects.
        </span>
      </div>
    {/if}

    <!-- Decision Verdict Strip -->
    {#if snapshot}
      <section class="verdict-strip verdict-{verdict.tone}" aria-label="Portfolio Decision Verdict">
        <div class="verdict-left">
          <div class="verdict-headline-row">
            <span class="verdict-dot"></span>
            <strong class="verdict-headline">{verdict.headline}</strong>
            {#if snapshot.is_offline_cache}
              <span class="offline-chip">OFFLINE CACHE</span>
            {/if}
          </div>
          <div class="verdict-meta tnum">
            <span>Policy {snapshot.policy_meta?.version || 'v3.0'}</span>
            <span>·</span>
            <span>{snapshot.sync_info?.ledger_hmac_verified ? 'HMAC Verified' : 'HMAC Unverified'}</span>
            <span>·</span>
            <span>Nifty 500: {verdict.ddPct > 0 ? `-${verdict.ddPct.toFixed(1)}%` : `${verdict.ddPct.toFixed(1)}%`}</span>
          </div>
        </div>
        <div class="verdict-actions">
          {#if verdict.tone !== 'ok'}
            <button type="button" class="verdict-cta" onclick={() => activeTab = 'rebalance'}>
              Review Plan
            </button>
          {/if}
          <button type="button" class="verdict-secondary" onclick={() => showLumpsumModal = true}>
            Deploy Cash
          </button>
        </div>
      </section>
    {/if}

    <!-- 3-KPI Ribbon -->
    <section class="hud-grid hud-grid-3" aria-label="Executive Portfolio Metrics">
      <!-- Card 1: Portfolio Corpus -->
      <HudMetricCard
        label="Portfolio Corpus"
        formattedValue={formatCompactINR(snapshot?.portfolio_summary?.net_worth, quietMode)}
        subLabel="Cost Basis"
        subValue={formatCompactINR(snapshot?.portfolio_summary?.total_invested, quietMode)}
        badgeText={`${xirrStr} XIRR`}
        badgeType={isNegativeReturn ? "negative" : "positive"}
        icon={Wallet}
        quietMode={quietMode}
        accentColor="cyan"
      />

      <!-- Card 2: Liquid Ready (Arbitrage) -->
      <HudMetricCard
        label="Liquid Ready (Arbitrage)"
        formattedValue={formatCompactINR(liquidReadyValue.ready, quietMode)}
        subLabel="Exit-Load Free"
        subValue={`${liquidReadyValue.freePct}% free · ${formatCompactINR(liquidReadyValue.locked, quietMode)} locked`}
        badgeText={liquidReadyValue.ready > 0 ? "DEPLOYABLE" : "EMPTY"}
        badgeType={liquidReadyValue.ready > 0 ? "positive" : "neutral"}
        icon={Coins}
        quietMode={quietMode}
        accentColor="emerald"
      />

      <!-- Card 3: Sec 112A Headroom -->
      <HudMetricCard
        label="Sec 112A Headroom"
        formattedValue={formatINR(snapshot?.portfolio_summary?.ltcg_exemption_remaining, quietMode)}
        subLabel="FY Limit: ₹1.25 L"
        subValue={`Used: ${formatINR(snapshot?.portfolio_summary?.ltcg_exemption_used, quietMode)}`}
        badgeText={(snapshot?.portfolio_summary?.ltcg_exemption_remaining ?? 0) > 0 ? "EXEMPT READY" : "EXHAUSTED"}
        badgeType={(snapshot?.portfolio_summary?.ltcg_exemption_remaining ?? 0) > 0 ? "positive" : "neutral"}
        icon={ShieldCheck}
        quietMode={quietMode}
        accentColor="amber"
      />
    </section>

    <!-- Navigation Tabs Bar -->
    <div class="tabs-nav" role="tablist" aria-label="Portfolio OS Cockpit Views">
      <button 
        type="button" 
        id="tab-overview" 
        role="tab" 
        aria-selected={activeTab === 'overview'} 
        aria-controls="panel-overview"
        class="tab-link" 
        class:active={activeTab === 'overview'}
        onclick={() => activeTab = 'overview'}
      >
        <LayoutDashboard size={15} />
        <span>Executive Overview</span>
        <kbd class="tab-kbd">⌘1</kbd>
      </button>

      <button 
        type="button" 
        id="tab-taxlots" 
        role="tab" 
        aria-selected={activeTab === 'taxlots'} 
        aria-controls="panel-taxlots"
        class="tab-link" 
        class:active={activeTab === 'taxlots'}
        onclick={() => activeTab = 'taxlots'}
      >
        <Receipt size={15} />
        <span>Scheme Tax Lots & FIFO</span>
        {#if snapshot?.holdings}
          <span class="count-pill">{snapshot.holdings.length}</span>
        {/if}
        <kbd class="tab-kbd">⌘2</kbd>
      </button>

      <button 
        type="button" 
        id="tab-rebalance" 
        role="tab" 
        aria-selected={activeTab === 'rebalance'} 
        aria-controls="panel-rebalance"
        class="tab-link" 
        class:active={activeTab === 'rebalance'}
        onclick={() => activeTab = 'rebalance'}
      >
        <Sliders size={15} />
        <span>Unified Rebalance & FIRE</span>
        <kbd class="tab-kbd">⌘3</kbd>
      </button>
    </div>

    <!-- Active Tab Panel Content -->
    <div class="panel-container">
      {#if loading && !snapshot}
        <div class="loading-panel">
          <RefreshCw size={32} class="spin loading-icon" />
          <p>Booting Obsidian Terminal v4.0 & Projecting FIFO Ledger...</p>
        </div>
      {:else if error}
        <div class="error-panel">
          <AlertTriangle size={32} class="error-icon" />
          <h3>System Offline or Unauthorized</h3>
          <p>{error}</p>
          <div class="token-input-row">
            <input 
              type="password" 
              placeholder="API_AUTH_TOKEN" 
              bind:value={tokenInputValue} 
              class="token-field mono"
              onkeydown={(e) => { if (e.key === 'Enter') handleSaveToken(); }}
            />
            <button type="button" class="token-btn" onclick={handleSaveToken}>
              <KeyRound size={14} />
              <span>Save & Connect</span>
            </button>
          </div>
          <button type="button" class="retry-btn" onclick={loadSnapshot}>Retry Connection</button>
        </div>
      {:else if activeTab === 'overview'}
        <div id="panel-overview" role="tabpanel" aria-labelledby="tab-overview" class="tab-panel">
          <!-- Strategic Bucket Drift Matrix -->
          <BucketTable
            buckets={snapshot?.rebalance_plan?.buckets}
            holdings={snapshot?.holdings}
            policyMeta={snapshot?.policy_meta}
            quietMode={quietMode}
            onInspectFund={(_isin) => {
              activeTab = 'taxlots';
            }}
          />

          <!-- Collapsible Net Worth Timeline (Secondary) -->
          <div class="timeline-accordion">
            <button
              type="button"
              class="timeline-toggle"
              aria-expanded={showTimeline}
              onclick={() => showTimeline = !showTimeline}
            >
              <span>Net Worth & Invested Timeline</span>
              <ChevronDown size={15} class="timeline-chev {showTimeline ? 'open' : ''}" />
            </button>
            {#if showTimeline}
              <div class="timeline-body">
                <TimelineChart
                  dates={snapshot?.networth_trend?.dates}
                  netWorths={snapshot?.networth_trend?.net_worth}
                  investeds={snapshot?.networth_trend?.invested}
                  quietMode={quietMode}
                />
              </div>
            {/if}
          </div>
        </div>
      {:else if activeTab === 'taxlots'}
        <div id="panel-taxlots" role="tabpanel" aria-labelledby="tab-taxlots" class="tab-panel">
          <TaxLotsTable
            holdings={snapshot?.holdings}
            quietMode={quietMode}
            isOffline={Boolean(snapshot?.is_offline_cache)}
            ltcgRemaining={snapshot?.portfolio_summary?.ltcg_exemption_remaining}
          />
        </div>
      {:else if activeTab === 'rebalance'}
        <div id="panel-rebalance" role="tabpanel" aria-labelledby="tab-rebalance" class="tab-panel">
          <RebalanceFireView
            rebalancePlan={snapshot?.rebalance_plan}
            holdings={snapshot?.holdings}
            policyMeta={snapshot?.policy_meta}
            netWorth={snapshot?.portfolio_summary?.net_worth}
            quietMode={quietMode}
            isOffline={Boolean(snapshot?.is_offline_cache)}
            onreloadsnapshot={loadSnapshot}
          />
        </div>
      {/if}
    </div>
  </main>

  <!-- Mobile PWA Bottom Tab Bar (<768px) -->
  <nav class="mobile-bottom-nav" aria-label="Mobile Navigation">
    <button
      type="button"
      class="mobile-nav-btn"
      class:active={activeTab === 'overview'}
      onclick={() => activeTab = 'overview'}
    >
      <LayoutDashboard size={18} />
      <span>Overview</span>
    </button>
    <button
      type="button"
      class="mobile-nav-btn"
      class:active={activeTab === 'rebalance'}
      onclick={() => activeTab = 'rebalance'}
    >
      <Sliders size={18} />
      <span>Rebalance</span>
    </button>
    <button
      type="button"
      class="mobile-nav-btn"
      class:active={activeTab === 'taxlots'}
      onclick={() => activeTab = 'taxlots'}
    >
      <Receipt size={18} />
      <span>Tax & Lots</span>
    </button>
  </nav>

  <!-- Modals & Global Dialogs -->
  <CommandPalette
    open={showCommandPalette}
    quietMode={quietMode}
    onclose={() => showCommandPalette = false}
    onselecttab={(tab) => {
      if (tab === 'overview') activeTab = 'overview';
      else if (tab === 'tax-lots') activeTab = 'taxlots';
      else if (tab === 'rebalance') activeTab = 'rebalance';
    }}
    ontogglequiet={toggleQuietMode}
    onopenlumpsum={() => showLumpsumModal = true}
    onsync={triggerSync}
    onexportitr={exportItr2Zip}
  />

  <LumpsumModal
    open={showLumpsumModal}
    holdings={snapshot?.holdings}
    netWorth={snapshot?.portfolio_summary?.net_worth}
    quietMode={quietMode}
    onclose={() => showLumpsumModal = false}
  />

  <!-- CAS Password & Statement Upload Modal -->
  {#if showCasModal}
    <div 
      id="casPasswordModal"
      class="modal-backdrop" 
      role="presentation"
      onclick={() => showCasModal = false}
      onkeydown={(e) => { if (e.key === 'Escape') showCasModal = false; }}
    >
      <div 
        class="modal-card" 
        role="dialog"
        aria-modal="true"
        aria-labelledby="cas-modal-title"
        tabindex="-1"
        onclick={(e) => e.stopPropagation()}
        onkeydown={(e) => e.stopPropagation()}
      >
        <div class="modal-header">
          <h3 id="cas-modal-title" class="modal-title">Import CAS E-Statement</h3>
          <button type="button" class="close-btn" onclick={() => showCasModal = false} aria-label="Close dialog">✕</button>
        </div>

        <div class="modal-body">
          <p class="modal-desc">
            Upload your CAMS / KFintech Consolidated Account Statement (CAS PDF or Excel). Python sidecar parses lots offline and computes verified cryptographic ledger entries.
          </p>

          <div class="file-drop-zone">
            <input 
              type="file" 
              accept=".pdf,.xlsx,.xls,.csv" 
              onchange={(e) => {
                const target = e.target as HTMLInputElement;
                if (target.files && target.files[0]) {
                  casFile = target.files[0];
                }
              }}
              class="file-input"
            />
            <Upload size={24} class="upload-icon" />
            <span class="file-name mono">{casFile ? casFile.name : 'Choose CAS Statement File'}</span>
          </div>

          <div class="field-group">
            <label for="cas-pwd" class="field-label">PDF Decryption Password (PAN uppercase / DOB):</label>
            <input 
              id="cas-pwd"
              type="password" 
              placeholder="e.g. ABCDE1234F" 
              bind:value={casPassword}
              class="password-input mono"
            />
          </div>

          {#if casStatusMsg}
            <div class="status-msg mono">{casStatusMsg}</div>
          {/if}
        </div>

        <div class="modal-footer">
          <button type="button" class="cancel-btn" onclick={() => showCasModal = false}>Cancel</button>
          <button 
            type="button" 
            class="submit-btn" 
            disabled={casUploading || !casFile}
            onclick={handleCasUpload}
          >
            {#if casUploading}
              <RefreshCw size={14} class="spin" />
            {/if}
            <span>Parse & Ingest</span>
          </button>
        </div>
      </div>
    </div>
  {/if}
</div>

<style>
  .obsidian-app {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    background: var(--surface-void);
    color: var(--text-main);
    position: relative;
  }

  /* Header */
  .app-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 1rem 2rem;
    background: var(--surface-void);
    border-bottom: 1px solid var(--border-hairline);
    position: sticky;
    top: 0;
    z-index: 100;
  }

  .brand-group {
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  .brand-pip {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--accent-cyan);
  }

  .brand-text {
    display: flex;
    flex-direction: column;
  }

  .brand-name {
    font-family: var(--font-brand);
    font-size: 1.15rem;
    font-weight: 600;
    color: var(--text-main);
    letter-spacing: -0.01em;
    margin: 0;
    line-height: 1.1;
  }

  .brand-edition {
    font-size: 0.75rem;
    font-family: var(--font-sans);
    font-weight: 500;
    color: var(--text-muted);
    letter-spacing: normal;
  }

  .freshness-tag {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.3rem 0.65rem;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
    font-size: 0.72rem;
    font-family: var(--font-sans);
    font-weight: 500;
    color: var(--text-secondary);
  }

  .live-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--accent-emerald);
  }

  .live-dot.stale {
    background: var(--accent-amber);
  }

  .hmac-verified {
    padding: 2px 6px;
    border-radius: var(--radius-xs);
    background: var(--accent-emerald-bg);
    color: var(--accent-emerald-bright);
    font-size: 0.65rem;
    font-weight: 600;
    border: 1px solid var(--border-hairline);
  }

  /* Toolbelt */
  .toolbelt {
    display: flex;
    align-items: center;
    gap: 0.65rem;
  }

  .action-btn {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.4rem 0.85rem;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    color: var(--text-secondary);
    font-size: 0.82rem;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
  }

  .action-btn:hover {
    color: var(--text-main);
    background: var(--surface-card-hover);
    border-color: var(--border-subtle);
  }

  .action-btn.highlight-cyan {
    border-color: var(--border-subtle);
    color: var(--accent-cyan-bright);
    background: var(--accent-cyan-bg);
  }

  .action-btn.highlight-cyan:hover {
    background: var(--surface-card-hover);
    border-color: var(--accent-cyan);
  }

  .action-btn.quiet-toggle.active {
    border-color: var(--border-subtle);
    background: var(--accent-amber-bg);
    color: var(--accent-amber-bright);
  }

  .action-btn.quiet-toggle.active:hover {
    background: var(--surface-card-hover);
  }

  .sync-btn {
    font-weight: 600;
  }

  .cmd-palette-trigger kbd {
    padding: 1px 5px;
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-xs);
    font-size: 0.65rem;
    color: var(--text-muted);
  }

  /* Main Body */
  .main-body {
    flex: 1;
    max-width: 1440px;
    width: 100%;
    margin: 0 auto;
    padding: 1.8rem 2rem;
    display: flex;
    flex-direction: column;
    gap: 1.8rem;
    z-index: 1;
  }

  /* HUD Grid */
  .hud-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 1.25rem;
  }

  .hud-grid-3 {
    grid-template-columns: repeat(3, 1fr);
  }

  .offline-banner {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    border-radius: var(--radius-md);
    background: var(--state-warn-soft);
    border: 1px solid color-mix(in oklch, var(--state-warn) 40%, transparent);
    color: var(--state-warn);
    font-size: 0.8rem;
  }

  /* Decision Verdict Strip */
  .verdict-strip {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding: 13px 18px;
    border-radius: var(--radius-md);
    background: var(--bg-surface);
    border: 1px solid var(--border-hair);
    flex-wrap: wrap;
  }

  .verdict-strip.verdict-ok {
    border-left: 3px solid var(--state-ok);
  }

  .verdict-strip.verdict-warn {
    border-left: 3px solid var(--state-warn);
  }

  .verdict-strip.verdict-action {
    border-left: 3px solid var(--state-action);
  }

  .verdict-strip.verdict-danger {
    border-left: 3px solid var(--state-danger);
  }

  .verdict-left {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .verdict-headline-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .verdict-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--state-ok);
  }

  .verdict-warn .verdict-dot { background: var(--state-warn); }
  .verdict-action .verdict-dot { background: var(--state-action); }
  .verdict-danger .verdict-dot { background: var(--state-danger); }

  .verdict-headline {
    font-size: 0.88rem;
    font-weight: 600;
    letter-spacing: 0.02em;
    color: var(--ink-primary);
  }

  .offline-chip {
    font-size: 0.65rem;
    font-weight: 600;
    padding: 2px 6px;
    border-radius: var(--radius-sm);
    background: var(--state-warn-soft);
    color: var(--state-warn);
  }

  .verdict-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.75rem;
    color: var(--ink-muted);
    flex-wrap: wrap;
  }

  .verdict-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .verdict-cta,
  .verdict-secondary {
    min-height: 36px;
    padding: 6px 12px;
    border-radius: var(--radius-sm);
    font-size: 0.78rem;
    font-weight: 600;
    cursor: pointer;
  }

  .verdict-cta {
    background: var(--state-action-soft);
    color: var(--state-action);
    border: 1px solid color-mix(in oklch, var(--state-action) 40%, transparent);
  }

  .verdict-secondary {
    background: var(--bg-elevated);
    color: var(--ink-primary);
    border: 1px solid var(--border-hair);
  }

  /* Collapsible Timeline */
  .timeline-accordion {
    margin-top: 14px;
    background: var(--bg-surface);
    border: 1px solid var(--border-hair);
    border-radius: var(--radius-md);
    overflow: hidden;
  }

  .timeline-toggle {
    width: 100%;
    background: transparent;
    border: none;
    color: var(--ink-secondary);
    padding: 12px 18px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 0.84rem;
    font-weight: 500;
    cursor: pointer;
    min-height: 44px;
  }

  .timeline-toggle:hover {
    color: var(--ink-primary);
    background: var(--bg-elevated);
  }

  :global(.timeline-chev) {
    transition: transform 150ms var(--ease-snappy);
  }

  :global(.timeline-chev.open) {
    transform: rotate(180deg);
  }

  .timeline-body {
    border-top: 1px solid var(--border-hair);
    padding: 12px;
  }

  /* Mobile Bottom Nav */
  .mobile-bottom-nav {
    display: none;
  }

  /* Navigation Tabs */
  .tabs-nav {
    display: flex;
    gap: 0.4rem;
    padding: 0.25rem;
    background: var(--surface-void);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    width: fit-content;
  }

  .tab-link {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 1rem;
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--radius-xs);
    color: var(--text-muted);
    font-size: 0.85rem;
    font-weight: 500;
    cursor: pointer;
    transition: color 0.15s ease, background-color 0.15s ease, border-color 0.15s ease;
  }

  .tab-link:hover {
    color: var(--text-main);
    background: var(--surface-card);
  }

  .tab-link.active {
    background: var(--surface-card);
    color: var(--text-main);
    border-color: var(--border-highlight);
  }

  .count-pill {
    padding: 1px 6px;
    background: var(--surface-void);
    border-radius: var(--radius-xs);
    font-size: 0.72rem;
    font-family: var(--font-mono);
    font-weight: 500;
    color: var(--accent-cyan-bright);
    border: 1px solid var(--border-hairline);
  }

  .tab-kbd {
    font-size: 0.68rem;
    font-family: var(--font-mono);
    color: var(--text-muted);
    opacity: 0.8;
    margin-left: 0.2rem;
  }

  /* Panels */
  .panel-container {
    display: flex;
    flex-direction: column;
  }

  .tab-panel {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
  }

  .loading-panel, .error-panel {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 5rem 1rem;
    text-align: center;
    color: var(--text-muted);
  }

  :global(.loading-icon) {
    color: var(--accent-cyan);
    margin-bottom: 1rem;
  }

  :global(.error-icon) {
    color: var(--accent-crimson);
    margin-bottom: 0.75rem;
  }

  .token-input-row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-top: 1rem;
    max-width: 420px;
    width: 100%;
  }

  .token-field {
    flex: 1;
    padding: 0.5rem 0.75rem;
    background: var(--surface-void);
    border: 1px solid var(--border-medium);
    border-radius: var(--radius-md);
    color: var(--text-primary);
    font-size: 0.85rem;
    outline: none;
  }

  .token-field:focus {
    border-color: var(--accent-cyan);
  }

  .token-btn {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.5rem 0.85rem;
    background: var(--accent-cyan);
    color: var(--surface-void);
    border: none;
    border-radius: var(--radius-md);
    font-size: 0.82rem;
    font-weight: 600;
    cursor: pointer;
    white-space: nowrap;
  }

  .retry-btn {
    margin-top: 0.75rem;
    padding: 0.45rem 1.25rem;
    background: var(--surface-glass);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    color: var(--text-secondary);
    cursor: pointer;
  }

  /* Modal */
  .modal-backdrop {
    position: fixed;
    inset: 0;
    background: color-mix(in oklab, var(--color-paper-3) 85%, transparent);
    backdrop-filter: blur(12px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    padding: 1.5rem;
  }

  .modal-card {
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    width: 100%;
    max-width: 480px;
    overflow: hidden;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 1.25rem 1.5rem;
    border-bottom: 1px solid var(--border-subtle);
  }

  .modal-title {
    font-size: 1.15rem;
    margin: 0;
  }

  .close-btn {
    background: transparent;
    border: none;
    color: var(--text-muted);
    font-size: 1.1rem;
    cursor: pointer;
  }

  .modal-body {
    padding: 1.25rem 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  .modal-desc {
    font-size: 0.82rem;
    color: var(--text-secondary);
    line-height: 1.4;
  }

  .file-drop-zone {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    padding: 1.5rem;
    border: 2px dashed var(--border-medium);
    border-radius: var(--radius-md);
    background: var(--surface-void);
    position: relative;
    cursor: pointer;
  }

  .file-input {
    position: absolute;
    inset: 0;
    opacity: 0;
    cursor: pointer;
  }

  .file-name {
    font-size: 0.8rem;
    color: var(--text-secondary);
  }

  .field-group {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
  }

  .field-label {
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .password-input {
    padding: 0.55rem 0.75rem;
    background: var(--surface-void);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    color: var(--text-primary);
    font-size: 0.9rem;
    outline: none;
  }

  .password-input:focus {
    border-color: var(--accent-cyan);
  }

  .status-msg {
    font-size: 0.75rem;
    color: var(--accent-cyan);
    padding: 0.5rem;
    background: var(--surface-void);
    border-radius: var(--radius-sm);
  }

  .modal-footer {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 0.75rem;
    padding: 1rem 1.5rem;
    border-top: 1px solid var(--border-subtle);
    background: var(--surface-card-hover);
  }

  .cancel-btn {
    padding: 0.45rem 1rem;
    background: transparent;
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    color: var(--text-secondary);
    cursor: pointer;
  }

  .submit-btn {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.45rem 1.25rem;
    background: var(--accent-cyan);
    border: none;
    border-radius: var(--radius-md);
    color: var(--surface-void);
    font-weight: 600;
    cursor: pointer;
  }

  .submit-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .dashboard-charts-grid {
    display: grid;
    grid-template-columns: 1.15fr 0.85fr;
    gap: 16px;
    margin-top: 16px;
  }

  @media (max-width: 1024px) {
    .dashboard-charts-grid {
      grid-template-columns: 1fr;
    }
  }

  @keyframes pulse {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.5; transform: scale(0.9); }
  }

  :global(.spin) {
    animation: spin 1s linear infinite;
  }

  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }

  @media (prefers-reduced-motion: reduce) {
    :global(.spin) {
      animation: none !important;
    }
  }

  @media (max-width: 1024px) {
    .hud-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  @media (max-width: 768px) {
    .tabs-nav {
      display: none;
    }
    .main-body {
      padding: 1rem 1rem 5.5rem 1rem;
      gap: 1.1rem;
    }
    .hud-grid,
    .hud-grid-3 {
      grid-template-columns: 1fr;
    }
    .mobile-bottom-nav {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      z-index: var(--z-sticky-nav);
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      background: var(--bg-surface);
      border-top: 1px solid var(--border-hair);
      padding: 6px 8px calc(6px + env(safe-area-inset-bottom, 0px));
    }
    .mobile-nav-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 3px;
      min-height: 44px;
      background: transparent;
      border: none;
      color: var(--ink-muted);
      font-size: 0.7rem;
      font-weight: 500;
      cursor: pointer;
      border-radius: var(--radius-sm);
    }
    .mobile-nav-btn.active {
      color: var(--state-action);
      background: var(--state-action-soft);
    }
  }

  @media (max-width: 640px) {
    .app-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 0.75rem;
    }
    .toolbelt {
      width: 100%;
      overflow-x: auto;
    }
  }
</style>
