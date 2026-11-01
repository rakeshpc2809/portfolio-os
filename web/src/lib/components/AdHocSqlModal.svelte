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
      try {
        await loadParquetTable('benchmark_history');
        loaded.push('benchmark_history');
      } catch (e) {
        console.warn('Could not load benchmark_history table:', e);
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

  async function selectPreset(p: typeof presets[0]) {
    sqlQuery = p.sql;
    if (p.sql.includes('open_lots') && !tablesLoaded.includes('open_lots')) {
      try {
        statusMsg = 'Loading open_lots table...';
        await loadParquetTable('open_lots');
        if (!tablesLoaded.includes('open_lots')) {
          tablesLoaded = [...tablesLoaded, 'open_lots'];
        }
      } catch (e: any) {
        errorMsg = 'Could not load open_lots: ' + (e.message || e);
        return;
      }
    }
    await runQuery();
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
          <h2 id="sql-modal-title" class="title">Ad-Hoc SQL Cockpit</h2>
          <div class="badge-row">
            <span class="badge duckdb-badge">
              <Database size={13} />
              <span>DuckDB-Wasm engine</span>
            </span>
            <span class="badge zero-copy-badge">
              <Sparkles size={13} />
              <span>Zero-copy in-browser OPFS</span>
            </span>
          </div>
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
              onclick={() => selectPreset(p)}
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
    background: var(--color-paper-3);
    backdrop-filter: blur(8px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: var(--z-modal);
    padding: 1.5rem;
  }

  .sql-modal-card {
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    width: 100%;
    max-width: 950px;
    height: 85vh;
    display: flex;
    flex-direction: column;
    box-shadow: none;
    overflow: hidden;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 1.25rem 1.5rem;
    border-bottom: 1px solid var(--color-rule);
    background: var(--color-paper);
  }

  .badge-row {
    display: flex;
    gap: 0.5rem;
    margin-top: 0.4rem;
  }

  .badge {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    font-size: 0.75rem;
    font-weight: 600;
    font-family: var(--font-outlier);
    padding: 0.2rem 0.5rem;
    border-radius: var(--radius-pill);
  }

  .duckdb-badge {
    background: var(--color-accent-soft);
    color: var(--color-accent);
    border: 1px solid var(--color-accent);
  }

  .zero-copy-badge {
    background: var(--color-cool-soft);
    color: var(--color-cool);
    border: 1px solid var(--color-cool);
  }

  .title {
    font-size: 1.25rem;
    font-weight: 700;
    color: var(--color-ink);
    margin: 0;
  }

  .close-btn {
    background: transparent;
    border: none;
    color: var(--color-muted);
    cursor: pointer;
    padding: 0.4rem;
    border-radius: var(--radius-pill);
    transition: color 0.15s ease, background 0.15s ease;
  }

  .close-btn:hover {
    color: var(--color-ink);
    background: var(--color-paper-3);
  }

  .close-btn:focus-visible,
  .preset-chip:focus-visible,
  .run-btn:focus-visible,
  .export-btn:focus-visible {
    outline: 2px solid var(--color-focus);
    outline-offset: 2px;
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.65rem 1.5rem;
    background: var(--color-paper-3);
    border-bottom: 1px solid var(--color-rule);
    overflow-x: auto;
  }

  .toolbar-label {
    font-size: 0.75rem;
    color: var(--color-muted);
    font-weight: 600;
  }

  .preset-chips {
    display: flex;
    gap: 0.5rem;
  }

  .preset-chip {
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    color: var(--color-ink-2);
    font-size: 0.75rem;
    padding: 0.25rem 0.6rem;
    border-radius: var(--radius-pill);
    cursor: pointer;
    white-space: nowrap;
    transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
  }

  .preset-chip:hover {
    background: var(--color-paper);
    color: var(--color-ink);
    border-color: var(--color-cool);
  }

  .editor-pane {
    padding: 1rem 1.5rem;
    border-bottom: 1px solid var(--color-rule);
    background: var(--color-paper-3);
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
    color: var(--color-muted);
  }

  .run-btn {
    background: var(--color-ok);
    color: var(--color-paper-3);
    border: none;
    font-size: 0.75rem;
    font-weight: 700;
    padding: 0.35rem 0.85rem;
    border-radius: var(--radius-pill);
    display: flex;
    align-items: center;
    gap: 0.4rem;
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .run-btn:hover:not(:disabled) {
    background: var(--color-ok);
    filter: brightness(1.1);
  }

  .run-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .sql-textarea {
    width: 100%;
    background: var(--color-paper);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    color: var(--color-ink);
    font-size: 0.85rem;
    line-height: 1.45;
    padding: 0.75rem;
    resize: none;
    outline: none;
    font-family: var(--font-outlier);
  }

  .sql-textarea:focus {
    border-color: var(--color-cool);
  }

  .status-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.5rem 1.5rem;
    background: var(--color-paper-3);
    border-bottom: 1px solid var(--color-rule);
    font-size: 0.75rem;
  }

  .status-pill {
    display: flex;
    align-items: center;
    gap: 0.4rem;
  }

  .status-pill.ok {
    color: var(--color-ok);
  }

  .status-pill.error {
    color: var(--color-bad);
  }

  .status-right {
    display: flex;
    align-items: center;
    gap: 1rem;
    color: var(--color-muted);
  }

  .timing-badge {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    color: var(--color-cool);
  }

  .export-btn {
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    color: var(--color-ink);
    padding: 0.2rem 0.5rem;
    border-radius: var(--radius-pill);
    display: flex;
    align-items: center;
    gap: 0.3rem;
    font-size: 0.7rem;
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .export-btn:hover {
    background: var(--color-paper);
  }

  .results-container {
    flex: 1;
    overflow: hidden;
    position: relative;
    background: var(--color-paper-3);
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
    background: var(--color-paper);
    padding: 0.6rem 0.85rem;
    color: var(--color-muted);
    border-bottom: 1px solid var(--color-rule);
    font-weight: 600;
    font-size: 0.75rem;
  }

  .results-table td {
    padding: 0.5rem 0.85rem;
    border-bottom: 1px solid var(--color-rule);
    color: var(--color-ink-2);
    white-space: nowrap;
    font-family: var(--font-outlier);
  }

  .alt-row {
    background: var(--color-paper-2);
  }

  .results-loading, .no-results {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: 0.75rem;
    color: var(--color-muted);
  }

  .spinner {
    width: 24px;
    height: 24px;
    border: 2px solid var(--color-rule);
    border-top-color: var(--color-cool);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  @media (prefers-reduced-motion: reduce) {
    .spinner {
      animation: none !important;
    }
    .close-btn,
    .preset-chip,
    .run-btn,
    .export-btn {
      transition: none !important;
    }
  }
</style>
