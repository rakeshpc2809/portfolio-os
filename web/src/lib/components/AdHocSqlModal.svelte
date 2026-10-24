<script lang="ts">
  import { onMount } from 'svelte';
  import { queryDuckDb, loadParquetTable, isDuckDbReady } from '../duckdb/duckdb-client';
  import { 
    Database, 
    Play, 
    Download, 
    X, 
    Sparkles, 
    Clock, 
    AlertCircle, 
    CheckCircle2, 
    Terminal, 
    Table as TableIcon 
  } from '@lucide/svelte';

  interface Props {
    open?: boolean;
    onclose?: () => void;
  }

  let { open = false, onclose }: Props = $props();

  let sqlQuery = $state<string>(
    'SELECT stock_symbol, ROUND(SUM(weight_pct), 2) AS total_weight_pct, COUNT(DISTINCT fund_id) AS fund_count\nFROM fund_holdings\nGROUP BY stock_symbol\nORDER BY total_weight_pct DESC\nLIMIT 15;'
  );

  let executing = $state(false);
  let statusMsg = $state('Initializing in-browser DuckDB-Wasm engine...');
  let errorMsg = $state<string | null>(null);
  let executionTimeMs = $state<number | null>(null);
  let queryResults = $state<Record<string, any>[]>([]);
  let columns = $state<string[]>([]);
  let tablesLoaded = $state<string[]>([]);

  const presets = [
    {
      name: 'Top Overlapping Stocks',
      sql: 'SELECT stock_symbol, ROUND(SUM(weight_pct), 2) AS total_weight_pct, COUNT(DISTINCT fund_id) AS fund_count\nFROM fund_holdings\nGROUP BY stock_symbol\nORDER BY total_weight_pct DESC\nLIMIT 15;'
    },
    {
      name: 'Market Cap Tier Weight',
      sql: 'SELECT market, COUNT(*) AS count, ROUND(SUM(weight_pct), 2) AS total_weight_pct\nFROM fund_holdings\nGROUP BY market\nORDER BY total_weight_pct DESC;'
    },
    {
      name: 'Largest Open Tax Lots',
      sql: 'SELECT asset_name, event_date, units, price_per_unit, gross_amount\nFROM open_lots\nORDER BY gross_amount DESC\nLIMIT 20;'
    },
    {
      name: 'Holdings Count per Fund',
      sql: 'SELECT fund_id, COUNT(*) AS stock_count, ROUND(SUM(weight_pct), 2) AS total_weight\nFROM fund_holdings\nGROUP BY fund_id\nORDER BY total_weight DESC;'
    }
  ];

  async function prepareTables() {
    try {
      executing = true;
      statusMsg = 'Loading parquet tables into browser memory...';
      const loaded: string[] = [];
      try {
        await loadParquetTable('fund_holdings');
        loaded.push('fund_holdings');
      } catch (e) {
        console.warn('Could not load fund_holdings table:', e);
      }
      try {
        await loadParquetTable('open_lots');
        loaded.push('open_lots');
      } catch (e) {
        console.warn('Could not load open_lots table:', e);
      }
      tablesLoaded = loaded;
      statusMsg = `DuckDB-Wasm Ready. Tables registered: ${loaded.join(', ')}`;
      if (loaded.length > 0 && queryResults.length === 0) {
        await runQuery();
      }
    } catch (err: any) {
      errorMsg = err.message || 'Failed to initialize DuckDB-Wasm tables';
      statusMsg = 'DuckDB Initialization Error';
    } finally {
      executing = false;
    }
  }

  async function runQuery() {
    if (!sqlQuery.trim()) return;
    executing = true;
    errorMsg = null;
    const start = performance.now();
    try {
      const rows = await queryDuckDb(sqlQuery.trim());
      executionTimeMs = Math.round((performance.now() - start) * 10) / 10;
      queryResults = rows;
      if (rows.length > 0) {
        columns = Object.keys(rows[0]);
      } else {
        columns = [];
      }
    } catch (err: any) {
      errorMsg = err.message || 'SQL execution failed';
    } finally {
      executing = false;
    }
  }

  function exportCsv() {
    if (queryResults.length === 0 || columns.length === 0) return;
    const header = columns.join(',');
    const rows = queryResults.map(r => 
      columns.map(c => {
        const val = r[c] ?? '';
        return typeof val === 'string' && val.includes(',') ? `"${val.replace(/"/g, '""')}"` : val;
      }).join(',')
    );
    const csvContent = 'data:text/csv;charset=utf-8,' + encodeURIComponent([header, ...rows].join('\n'));
    const link = document.createElement('a');
    link.setAttribute('href', csvContent);
    link.setAttribute('download', `duckdb_query_${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  $effect(() => {
    if (open && tablesLoaded.length === 0 && !executing) {
      prepareTables();
    }
  });
</script>

{#if open}
  <div 
    class="modal-backdrop" 
    role="presentation"
    onclick={onclose}
  >
    <div 
      class="sql-modal-card"
      role="dialog"
      aria-modal="true"
      aria-labelledby="sql-modal-title"
      tabindex="-1"
      onclick={(e) => e.stopPropagation()}
    >
      <!-- Header -->
      <div class="modal-header">
        <div class="header-left">
          <div class="badge-row">
            <span class="badge duckdb-badge">
              <Database size={13} />
              <span>DUCKDB-WASM ENGINE</span>
            </span>
            <span class="badge zero-copy-badge">
              <Sparkles size={13} />
              <span>ZERO-COPY IN-BROWSER OPFS</span>
            </span>
          </div>
          <h2 id="sql-modal-title" class="title">Ad-Hoc SQL Cockpit</h2>
        </div>
        <button type="button" class="close-btn" onclick={onclose} aria-label="Close modal">
          <X size={18} />
        </button>
      </div>

      <!-- Presets & Toolbar -->
      <div class="toolbar">
        <span class="toolbar-label">Presets:</span>
        <div class="preset-chips">
          {#each presets as p}
            <button 
              type="button" 
              class="preset-chip"
              onclick={() => { sqlQuery = p.sql; runQuery(); }}
            >
              {p.name}
            </button>
          {/each}
        </div>
      </div>

      <!-- SQL Query Editor -->
      <div class="editor-pane">
        <div class="pane-header">
          <div class="pane-title">
            <Terminal size={14} />
            <span>Interactive SQL Editor</span>
          </div>
          <button 
            type="button" 
            class="run-btn" 
            onclick={runQuery}
            disabled={executing}
          >
            <Play size={14} class={executing ? 'spin' : ''} />
            <span>{executing ? 'Executing...' : 'Run Query (⌘↵)'}</span>
          </button>
        </div>
        <textarea 
          bind:value={sqlQuery}
          class="sql-textarea mono"
          rows="5"
          placeholder="Enter custom SQL query against loaded Parquet tables..."
          onkeydown={(e) => {
            if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
              e.preventDefault();
              runQuery();
            }
          }}
        ></textarea>
      </div>

      <!-- Status Bar -->
      <div class="status-bar">
        <div class="status-left">
          {#if errorMsg}
            <div class="status-pill error">
              <AlertCircle size={14} />
              <span>{errorMsg}</span>
            </div>
          {:else}
            <div class="status-pill ok">
              <CheckCircle2 size={14} />
              <span>{statusMsg}</span>
            </div>
          {/if}
        </div>
        <div class="status-right mono">
          {#if executionTimeMs !== null}
            <span class="timing-badge">
              <Clock size={12} />
              <span>{executionTimeMs} ms</span>
            </span>
          {/if}
          <span class="rows-count">{queryResults.length} rows returned</span>
          {#if queryResults.length > 0}
            <button type="button" class="export-btn" onclick={exportCsv}>
              <Download size={13} />
              <span>Export CSV</span>
            </button>
          {/if}
        </div>
      </div>

      <!-- Results Table -->
      <div class="results-container">
        {#if executing}
          <div class="results-loading">
            <div class="spinner"></div>
            <span>Running DuckDB-Wasm query across Parquet buffers...</span>
          </div>
        {:else if queryResults.length === 0}
          <div class="no-results">
            <TableIcon size={32} class="empty-icon" />
            <p>No query results to display.</p>
          </div>
        {:else}
          <div class="table-scroller">
            <table class="results-table mono">
              <thead>
                <tr>
                  {#each columns as col}
                    <th>{col}</th>
                  {/each}
                </tr>
              </thead>
              <tbody>
                {#each queryResults as row, idx}
                  <tr class:alt-row={idx % 2 === 1}>
                    {#each columns as col}
                      <td>{row[col] !== undefined && row[col] !== null ? String(row[col]) : '—'}</td>
                    {/each}
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
        {/if}
      </div>
    </div>
  </div>
{/if}

<style>
  .modal-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.85);
    backdrop-filter: blur(8px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    padding: 1.5rem;
  }

  .sql-modal-card {
    background: var(--bg-card, #11141c);
    border: 1px solid var(--border-color, #232936);
    border-radius: 12px;
    width: 100%;
    max-width: 950px;
    height: 85vh;
    display: flex;
    flex-direction: column;
    box-shadow: 0 25px 60px rgba(0, 0, 0, 0.6);
    overflow: hidden;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 1.25rem 1.5rem;
    border-bottom: 1px solid var(--border-color, #232936);
    background: rgba(255, 255, 255, 0.01);
  }

  .badge-row {
    display: flex;
    gap: 0.5rem;
    margin-bottom: 0.5rem;
  }

  .badge {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    font-size: 0.68rem;
    font-weight: 700;
    letter-spacing: 0.05em;
    padding: 0.2rem 0.5rem;
    border-radius: 4px;
  }

  .duckdb-badge {
    background: rgba(245, 158, 11, 0.15);
    color: #f59e0b;
    border: 1px solid rgba(245, 158, 11, 0.3);
  }

  .zero-copy-badge {
    background: rgba(59, 130, 246, 0.15);
    color: #60a5fa;
    border: 1px solid rgba(59, 130, 246, 0.3);
  }

  .title {
    font-size: 1.25rem;
    font-weight: 700;
    color: #fff;
    margin: 0;
  }

  .close-btn {
    background: transparent;
    border: none;
    color: var(--text-muted, #717d96);
    cursor: pointer;
    padding: 0.4rem;
    border-radius: 6px;
    transition: all 0.15s ease;
  }

  .close-btn:hover {
    color: #fff;
    background: rgba(255, 255, 255, 0.05);
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.65rem 1.5rem;
    background: rgba(0, 0, 0, 0.2);
    border-bottom: 1px solid var(--border-color, #232936);
    overflow-x: auto;
  }

  .toolbar-label {
    font-size: 0.75rem;
    color: var(--text-muted, #717d96);
    text-transform: uppercase;
    font-weight: 600;
  }

  .preset-chips {
    display: flex;
    gap: 0.5rem;
  }

  .preset-chip {
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid var(--border-color, #232936);
    color: #cbd5e1;
    font-size: 0.75rem;
    padding: 0.25rem 0.6rem;
    border-radius: 4px;
    cursor: pointer;
    white-space: nowrap;
    transition: all 0.15s ease;
  }

  .preset-chip:hover {
    background: rgba(255, 255, 255, 0.08);
    color: #fff;
    border-color: rgba(255, 255, 255, 0.2);
  }

  .editor-pane {
    padding: 1rem 1.5rem;
    border-bottom: 1px solid var(--border-color, #232936);
    background: #090b10;
  }

  .pane-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0.5rem;
  }

  .pane-title {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--text-muted, #717d96);
    text-transform: uppercase;
  }

  .run-btn {
    background: #10b981;
    color: #000;
    border: none;
    font-size: 0.75rem;
    font-weight: 700;
    padding: 0.35rem 0.85rem;
    border-radius: 4px;
    display: flex;
    align-items: center;
    gap: 0.4rem;
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .run-btn:hover:not(:disabled) {
    background: #34d399;
  }

  .run-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .sql-textarea {
    width: 100%;
    background: #0d1117;
    border: 1px solid #21262d;
    border-radius: 6px;
    color: #e6edf3;
    font-size: 0.85rem;
    line-height: 1.45;
    padding: 0.75rem;
    resize: none;
    outline: none;
    font-family: 'JetBrains Mono', monospace;
  }

  .sql-textarea:focus {
    border-color: #3b82f6;
  }

  .status-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.5rem 1.5rem;
    background: rgba(0, 0, 0, 0.4);
    border-bottom: 1px solid var(--border-color, #232936);
    font-size: 0.75rem;
  }

  .status-pill {
    display: flex;
    align-items: center;
    gap: 0.4rem;
  }

  .status-pill.ok {
    color: #10b981;
  }

  .status-pill.error {
    color: #ef4444;
  }

  .status-right {
    display: flex;
    align-items: center;
    gap: 1rem;
    color: var(--text-muted, #717d96);
  }

  .timing-badge {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    color: #38bdf8;
  }

  .export-btn {
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid var(--border-color, #232936);
    color: #fff;
    padding: 0.2rem 0.5rem;
    border-radius: 4px;
    display: flex;
    align-items: center;
    gap: 0.3rem;
    font-size: 0.7rem;
    cursor: pointer;
  }

  .export-btn:hover {
    background: rgba(255, 255, 255, 0.1);
  }

  .results-container {
    flex: 1;
    overflow: hidden;
    position: relative;
    background: #080a0f;
  }

  .table-scroller {
    width: 100%;
    height: 100%;
    overflow: auto;
  }

  .results-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.8rem;
    text-align: left;
  }

  .results-table th {
    position: sticky;
    top: 0;
    background: #11141c;
    padding: 0.6rem 0.85rem;
    color: #94a3b8;
    border-bottom: 1px solid var(--border-color, #232936);
    font-weight: 600;
    text-transform: uppercase;
    font-size: 0.7rem;
  }

  .results-table td {
    padding: 0.5rem 0.85rem;
    border-bottom: 1px solid rgba(255, 255, 255, 0.03);
    color: #e2e8f0;
    white-space: nowrap;
  }

  .alt-row {
    background: rgba(255, 255, 255, 0.015);
  }

  .results-loading, .no-results {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: 0.75rem;
    color: var(--text-muted, #717d96);
  }

  .spinner {
    width: 24px;
    height: 24px;
    border: 2px solid rgba(255, 255, 255, 0.1);
    border-top-color: #3b82f6;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }
</style>
