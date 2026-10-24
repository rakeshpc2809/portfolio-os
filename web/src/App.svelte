<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchJson, getAuthToken, setAuthToken, loadDashboardData, type SnapshotDto, type RebalancePlanDto } from './lib/api';
  import { formatINR, formatPercent, formatCompactINR } from './lib/formatters';
  import { fireConfetti } from './lib/confetti';
  import HudMetricCard from './lib/components/HudMetricCard.svelte';
  import TimelineChart from './lib/components/TimelineChart.svelte';
  import BucketTable from './lib/components/BucketTable.svelte';
  import TaxLotsTable from './lib/components/TaxLotsTable.svelte';
  import RebalanceFireView from './lib/components/RebalanceFireView.svelte';
  import OverlapModal from './lib/components/OverlapModal.svelte';
  import LumpsumModal from './lib/components/LumpsumModal.svelte';
  import CommandPalette from './lib/components/CommandPalette.svelte';
  import AdHocSqlModal from './lib/components/AdHocSqlModal.svelte';

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
    Command, 
    PieChart, 
    Coins, 
    Database,
    ShieldCheck, 
    AlertTriangle,
    Wallet,
    TrendingUp,
    Sparkles,
    CheckCircle2,
    X,
    KeyRound
  } from '@lucide/svelte';

  type TabType = 'overview' | 'taxlots' | 'rebalance';

  let activeTab = $state<TabType>('overview');
  let snapshot = $state<SnapshotDto | null>(null);
  let loading = $state(true);
  let syncing = $state(false);
  let error = $state<string | null>(null);
  let tokenInputValue = $state(getAuthToken());

  // Quiet Mode State (persisted to localStorage)
  let quietMode = $state<boolean>(false);

  // Modal Dialog States
  let showCommandPalette = $state(false);
  let showOverlapModal = $state(false);
  let showLumpsumModal = $state(false);
  let showSqlModal = $state(false);
  let showCasModal = $state(false);
  let casPassword = $state('');
  let casFile = $state<File | null>(null);
  let casUploading = $state(false);
  let casStatusMsg = $state<string | null>(null);
  let briefDismissed = $state(false);

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
      fireConfetti();
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
    if (!quietMode) {
      fireConfetti(window.innerWidth * 0.85, 45);
    }
  }

  function exportItr2Zip() {
    const token = getAuthToken();
    const url = `/api/v1/tax/export/itr2/zip?token=${encodeURIComponent(token)}`;
    window.open(url, '_blank');
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
    } else if (modifier && e.key.toLowerCase() === 'o') {
      e.preventDefault();
      showOverlapModal = true;
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
      (window as any).openOverlapModal = () => { showOverlapModal = true; };
      (window as any).closeOverlapModal = () => { showOverlapModal = false; };
      (window as any).openSqlModal = () => { showSqlModal = true; };
      (window as any).closeSqlModal = () => { showSqlModal = false; };
      (window as any).closeCasPasswordModal = () => { showCasModal = false; };
      (window as any).toggleBucketRow = () => {};
      (window as any).toggleAllBuckets = () => {};
    }

    loadSnapshot();
  });
</script>

<svelte:window onkeydown={handleGlobalKeyDown} />

<div class="obsidian-app">
  <!-- Fluid Floating Ambient Mesh Backdrop -->
  <div class="ambient-mesh">
    <div class="aurora-orb orb-1"></div>
    <div class="aurora-orb orb-2"></div>
    <div class="aurora-orb orb-3"></div>
  </div>

  <!-- Top App Bar / Header -->
  <header class="app-header">
    <div class="brand-group">
      <div class="brand-pip"></div>
      <div class="brand-text">
        <h1 class="brand-name gradient-text-cyan">PORTFOLIO OS</h1>
        <span class="brand-edition">✨ PERSONAL COCKPIT v4.0</span>
      </div>
      {#if snapshot?.sync_info}
        <div class="freshness-tag">
          <span class="live-dot" class:stale={snapshot.sync_info.stale_nav_count > 0}></span>
          <span>{snapshot.sync_info.status}</span>
          {#if snapshot.sync_info.ledger_hmac_verified}
            <span class="hmac-verified" title="Ledger cryptographic integrity verified">HMAC SECURE</span>
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

      <!-- Overlap Modal Trigger -->
      <button 
        type="button" 
        class="action-btn" 
        onclick={() => showOverlapModal = true}
        title="Portfolio Overlap & Lookthrough"
      >
        <PieChart size={15} />
        <span class="btn-text">Overlap</span>
      </button>

      <!-- DuckDB-Wasm SQL Cockpit Trigger -->
      <button 
        type="button" 
        class="action-btn" 
        onclick={() => showSqlModal = true}
        title="Interactive DuckDB-Wasm SQL Cockpit"
      >
        <Database size={15} />
        <span class="btn-text">DuckDB SQL</span>
      </button>

      <!-- Lumpsum Simulator Trigger -->
      <button 
        type="button" 
        class="action-btn highlight-cyan" 
        onclick={() => showLumpsumModal = true}
        title="Simulate Lump-Sum Inflow"
      >
        <Coins size={15} />
        <span class="btn-text">💰 Deploy Cash</span>
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
          <span class="btn-text">🧘 Zen Mode</span>
        {:else}
          <Eye size={15} />
          <span class="btn-text">👁️ Reveal</span>
        {/if}
      </button>

      <!-- Sync Button -->
      <button 
        type="button" 
        class="action-btn sync-btn highlight-cyan" 
        onclick={triggerSync}
        disabled={syncing}
        title="Sync Latest AMFI NAVs"
      >
        <RefreshCw size={15} class={syncing ? 'spin' : ''} />
        <span class="btn-text">⚡ Sync NAVs</span>
      </button>
    </div>
  </header>

  <!-- Main Content Layout -->
  <main class="main-body">
    {#if snapshot?.daily_brief && !briefDismissed}
      <aside class="brief-banner" role="region" aria-label="Morning Executive Brief">
        <div class="brief-content">
          <div class="brief-header">
            <div class="brief-badge-row">
              <span class="brief-tag"><Sparkles size={13} /> MORNING EXECUTIVE BRIEF</span>
              {#if snapshot.daily_brief.verified}
                <span class="brief-verified-tag"><ShieldCheck size={12} /> AUDIT VERIFIED</span>
              {/if}
              {#if snapshot.daily_brief.generated_at}
                <span class="brief-timestamp">{snapshot.daily_brief.generated_at}</span>
              {/if}
            </div>
            <button 
              type="button" 
              class="brief-dismiss" 
              onclick={() => briefDismissed = true} 
              aria-label="Dismiss brief"
              title="Dismiss brief"
            >
              <X size={15} />
            </button>
          </div>
          <h3 class="brief-headline">{snapshot.daily_brief.headline}</h3>
          <p class="brief-narration">{snapshot.daily_brief.narration}</p>
        </div>
      </aside>
    {/if}

    <!-- Executive HUD Cards Grid -->
    <section class="hud-grid" aria-label="Executive Portfolio Metrics">
      <!-- Card 1: Net Worth -->
      <HudMetricCard
        label="Total Portfolio Net Worth"
        formattedValue={formatINR(snapshot?.portfolio_summary?.net_worth, quietMode)}
        subLabel="Cost Basis"
        subValue={formatINR(snapshot?.portfolio_summary?.total_invested, quietMode)}
        badgeText={snapshot?.portfolio_summary?.unrealized_gain_pct ? formatPercent(snapshot.portfolio_summary.unrealized_gain_pct) : undefined}
        badgeType="positive"
        icon={Wallet}
        quietMode={quietMode}
        accentColor="cyan"
      />

      <!-- Card 2: Unallocated Cash -->
      <HudMetricCard
        label="Liquid Cash Reservoir"
        formattedValue={formatINR(snapshot?.portfolio_summary?.unallocated_cash, quietMode)}
        subLabel="Allocation Readiness"
        subValue={(snapshot?.portfolio_summary?.unallocated_cash ?? 0) > 0 ? "BUY SIDE READY" : "FULLY DEPLOYED"}
        badgeText={(snapshot?.portfolio_summary?.unallocated_cash ?? 0) > 0 ? "DEPLOYABLE" : "OPTIMAL"}
        badgeType={(snapshot?.portfolio_summary?.unallocated_cash ?? 0) > 0 ? "neutral" : "positive"}
        icon={Coins}
        quietMode={quietMode}
        accentColor="emerald"
      />

      <!-- Card 3: Sec 112A Exemption Headroom -->
      <HudMetricCard
        label="Sec 112A Tax-Free Headroom"
        formattedValue={formatINR(snapshot?.portfolio_summary?.ltcg_exemption_remaining, quietMode)}
        subLabel="Limit: ₹1.25L / FY"
        subValue={`Used: ${formatINR(snapshot?.portfolio_summary?.ltcg_exemption_used, quietMode)}`}
        badgeText={(snapshot?.portfolio_summary?.ltcg_exemption_remaining ?? 0) > 0 ? "TAX HARVEST READY" : "EXHAUSTED"}
        badgeType={(snapshot?.portfolio_summary?.ltcg_exemption_remaining ?? 0) > 0 ? "positive" : "neutral"}
        icon={ShieldCheck}
        quietMode={quietMode}
        accentColor="amber"
      />

      <!-- Card 4: Portfolio Return & XIRR -->
      <HudMetricCard
        label="Annualized XIRR Performance"
        formattedValue={snapshot?.portfolio_summary?.formatted_xirr || snapshot?.portfolio_summary?.xirr_percentage || "—"}
        subLabel="Absolute Return"
        subValue={formatINR(snapshot?.portfolio_summary?.unrealized_gain, quietMode)}
        badgeText={snapshot?.portfolio_summary?.unrealized_gain_pct ? formatPercent(snapshot.portfolio_summary.unrealized_gain_pct) : undefined}
        badgeType="positive"
        icon={TrendingUp}
        quietMode={quietMode}
        accentColor="cyan"
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
          <!-- Timeline Area Chart -->
          <TimelineChart
            dates={snapshot?.networth_trend?.dates}
            netWorths={snapshot?.networth_trend?.net_worth}
            investeds={snapshot?.networth_trend?.invested}
            quietMode={quietMode}
          />

          <!-- Asset Allocation Bucket Table -->
          <BucketTable
            buckets={snapshot?.rebalance_plan?.buckets}
            quietMode={quietMode}
          />
        </div>
      {:else if activeTab === 'taxlots'}
        <div id="panel-taxlots" role="tabpanel" aria-labelledby="tab-taxlots" class="tab-panel">
          <TaxLotsTable
            holdings={snapshot?.holdings}
            quietMode={quietMode}
            ltcgRemaining={snapshot?.portfolio_summary?.ltcg_exemption_remaining}
          />
        </div>
      {:else if activeTab === 'rebalance'}
        <div id="panel-rebalance" role="tabpanel" aria-labelledby="tab-rebalance" class="tab-panel">
          <RebalanceFireView
            rebalancePlan={snapshot?.rebalance_plan}
            quietMode={quietMode}
            onopenlumpsum={() => showLumpsumModal = true}
          />
        </div>
      {/if}
    </div>
  </main>

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
    onopenoverlap={() => showOverlapModal = true}
    onopenlumpsum={() => showLumpsumModal = true}
    onopensql={() => showSqlModal = true}
    onsync={triggerSync}
    onexportitr={exportItr2Zip}
  />

  <OverlapModal
    open={showOverlapModal}
    quietMode={quietMode}
    onclose={() => showOverlapModal = false}
  />

  <AdHocSqlModal
    open={showSqlModal}
    onclose={() => showSqlModal = false}
  />

  <LumpsumModal
    open={showLumpsumModal}
    quietMode={quietMode}
    onclose={() => showLumpsumModal = false}
    onapply={(plan) => {
      if (snapshot) {
        snapshot = { ...snapshot, rebalance_plan: plan };
      }
    }}
  />

  <!-- CAS Password & Statement Upload Modal -->
  {#if showCasModal}
    <div 
      id="casPasswordModal"
      class="modal-backdrop" 
      role="presentation"
      onclick={() => showCasModal = false}
    >
      <div 
        class="modal-card" 
        role="dialog"
        aria-modal="true"
        aria-labelledby="cas-modal-title"
        tabindex="-1"
        onclick={(e) => e.stopPropagation()}
      >
        <div class="modal-header">
          <div>
            <span class="modal-tag">DATA INGESTION</span>
            <h3 id="cas-modal-title" class="modal-title">Import CAS E-Statement</h3>
          </div>
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
    background: rgba(14, 20, 36, 0.75);
    border-bottom: 1px solid var(--border-hairline);
    position: sticky;
    top: 0;
    z-index: 100;
    backdrop-filter: blur(24px) saturate(190%);
    box-shadow: 0 4px 24px -6px rgba(0, 0, 0, 0.4);
  }

  .brand-group {
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  .brand-pip {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: var(--accent-cyan);
    box-shadow: 0 0 16px var(--accent-cyan);
    animation: pipPulse 2.4s infinite ease-in-out;
  }

  .brand-text {
    display: flex;
    flex-direction: column;
  }

  .brand-name {
    font-family: var(--font-brand);
    font-size: 1.25rem;
    font-weight: 800;
    letter-spacing: -0.01em;
    margin: 0;
    line-height: 1.1;
  }

  .brand-edition {
    font-size: 0.72rem;
    font-family: var(--font-sans);
    font-weight: 700;
    color: var(--accent-cyan-bright);
    letter-spacing: 0.05em;
  }

  .freshness-tag {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.35rem 0.75rem;
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-pill);
    font-size: 0.72rem;
    font-family: var(--font-sans);
    font-weight: 600;
    color: var(--text-secondary);
  }

  .live-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--accent-emerald);
    box-shadow: 0 0 8px var(--accent-emerald);
  }

  .live-dot.stale {
    background: var(--accent-amber);
    box-shadow: 0 0 8px var(--accent-amber);
  }

  .hmac-verified {
    padding: 2px 7px;
    border-radius: var(--radius-pill);
    background: rgba(16, 185, 129, 0.16);
    color: var(--accent-emerald-bright);
    font-size: 0.65rem;
    font-weight: 700;
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
    padding: 0.45rem 0.95rem;
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-pill);
    color: var(--text-secondary);
    font-size: 0.82rem;
    font-weight: 600;
    cursor: pointer;
    box-shadow: 0 2px 8px -2px rgba(0, 0, 0, 0.3);
    transition: transform 0.2s var(--ease-bounce), 
                background 0.2s ease, 
                border-color 0.2s ease, 
                box-shadow 0.2s ease, 
                color 0.2s ease;
  }

  .action-btn:hover {
    color: #FFFFFF;
    background: rgba(255, 255, 255, 0.08);
    border-color: var(--border-subtle);
    transform: translateY(-2px);
    box-shadow: 0 6px 16px -4px rgba(0, 0, 0, 0.4);
  }

  .action-btn.highlight-cyan {
    border-color: rgba(6, 182, 212, 0.35);
    color: var(--accent-cyan-bright);
    background: rgba(6, 182, 212, 0.08);
  }

  .action-btn.highlight-cyan:hover {
    background: rgba(6, 182, 212, 0.2);
    border-color: var(--accent-cyan-bright);
    box-shadow: 0 6px 20px -4px rgba(6, 182, 212, 0.4);
  }

  .action-btn.quiet-toggle.active {
    border-color: rgba(245, 158, 11, 0.4);
    background: rgba(245, 158, 11, 0.14);
    color: var(--accent-amber-bright);
  }

  .action-btn.quiet-toggle.active:hover {
    box-shadow: 0 6px 20px -4px rgba(245, 158, 11, 0.4);
  }

  .sync-btn {
    font-weight: 700;
  }

  .cmd-palette-trigger kbd {
    padding: 1px 5px;
    background: rgba(0, 0, 0, 0.3);
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

  /* Morning Executive Brief Banner */
  .brief-banner {
    background: linear-gradient(135deg, rgba(16, 185, 129, 0.12) 0%, rgba(6, 182, 212, 0.08) 50%, rgba(15, 23, 42, 0.6) 100%);
    border: 1px solid rgba(16, 185, 129, 0.25);
    border-radius: var(--radius-lg, 12px);
    padding: 1.1rem 1.4rem;
    backdrop-filter: blur(16px);
    box-shadow: 0 8px 32px -8px rgba(16, 185, 129, 0.15);
  }

  .brief-content {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .brief-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .brief-badge-row {
    display: flex;
    align-items: center;
    gap: 0.6rem;
  }

  .brief-tag {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    font-size: 0.68rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--accent-emerald, #10b981);
    background: rgba(16, 185, 129, 0.14);
    padding: 0.25rem 0.55rem;
    border-radius: var(--radius-pill, 9999px);
    border: 1px solid rgba(16, 185, 129, 0.25);
  }

  .brief-verified-tag {
    display: inline-flex;
    align-items: center;
    gap: 0.3rem;
    font-size: 0.65rem;
    font-weight: 700;
    letter-spacing: 0.06em;
    color: var(--accent-cyan, #06b6d4);
    background: rgba(6, 182, 212, 0.12);
    padding: 0.2rem 0.5rem;
    border-radius: var(--radius-pill, 9999px);
    border: 1px solid rgba(6, 182, 212, 0.25);
  }

  .brief-timestamp {
    font-size: 0.72rem;
    color: var(--text-muted, #94a3b8);
    font-family: var(--font-mono, monospace);
  }

  .brief-dismiss {
    background: transparent;
    border: none;
    color: var(--text-muted, #94a3b8);
    cursor: pointer;
    padding: 0.3rem;
    border-radius: var(--radius-sm, 6px);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s ease;
  }

  .brief-dismiss:hover {
    color: var(--text-main, #f8fafc);
    background: rgba(255, 255, 255, 0.08);
  }

  .brief-headline {
    font-size: 1.05rem;
    font-weight: 700;
    color: var(--text-main, #f8fafc);
    margin: 0;
    letter-spacing: -0.01em;
  }

  .brief-narration {
    font-size: 0.88rem;
    line-height: 1.5;
    color: rgba(226, 232, 240, 0.88);
    margin: 0;
  }

  /* HUD Grid */
  .hud-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 1.25rem;
  }

  /* Navigation Tabs */
  .tabs-nav {
    display: flex;
    gap: 0.6rem;
    padding: 0.4rem 0.6rem;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-pill);
    width: fit-content;
    box-shadow: 0 6px 20px -6px rgba(0, 0, 0, 0.4);
  }

  .tab-link {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    padding: 0.6rem 1.25rem;
    background: transparent;
    border: none;
    border-radius: var(--radius-pill);
    color: var(--text-secondary);
    font-size: 0.88rem;
    font-weight: 600;
    cursor: pointer;
    transition: transform 0.2s var(--ease-bounce), 
                background 0.2s ease, 
                color 0.2s ease, 
                box-shadow 0.2s ease;
  }

  .tab-link:hover {
    color: #FFFFFF;
    background: rgba(255, 255, 255, 0.05);
    transform: translateY(-1px);
  }

  .tab-link.active {
    background: linear-gradient(135deg, rgba(6, 182, 212, 0.25), rgba(168, 85, 247, 0.25));
    color: #FFFFFF;
    border: 1px solid rgba(6, 182, 212, 0.4);
    box-shadow: 0 4px 18px -4px rgba(6, 182, 212, 0.35);
  }

  .count-pill {
    padding: 2px 7px;
    background: rgba(0, 0, 0, 0.4);
    border-radius: var(--radius-pill);
    font-size: 0.72rem;
    font-family: var(--font-brand);
    font-weight: 700;
    color: var(--accent-cyan-bright);
    border: 1px solid rgba(6, 182, 212, 0.3);
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
    background: rgba(4, 7, 13, 0.82);
    backdrop-filter: blur(12px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    padding: 1.5rem;
  }

  .modal-card {
    background: var(--surface-card);
    border: 1px solid var(--border-medium);
    border-radius: var(--radius-xl);
    width: 100%;
    max-width: 480px;
    box-shadow: 0 25px 60px rgba(0, 0, 0, 0.9);
    overflow: hidden;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 1.25rem 1.5rem;
    border-bottom: 1px solid var(--border-subtle);
  }

  .modal-tag {
    font-size: 0.65rem;
    font-family: var(--font-mono);
    color: var(--accent-cyan);
    letter-spacing: 0.05em;
  }

  .modal-title {
    font-size: 1.15rem;
    margin: 0.2rem 0 0 0;
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

  .text-amber { color: var(--accent-amber); }

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

  @media (max-width: 1024px) {
    .hud-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  @media (max-width: 640px) {
    .hud-grid {
      grid-template-columns: 1fr;
    }
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
