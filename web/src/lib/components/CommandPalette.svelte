<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { 
    Search, 
    LayoutDashboard, 
    Receipt, 
    Sliders, 
    PieChart, 
    Coins, 
    Eye, 
    EyeOff, 
    RefreshCw, 
    Download, 
    FileSpreadsheet,
    CornerDownLeft,
    Command,
    Database
  } from '@lucide/svelte';

  interface Props {
    open?: boolean;
    quietMode?: boolean;
    onclose?: () => void;
    onselecttab?: (tab: 'overview' | 'tax-lots' | 'rebalance') => void;
    ontogglequiet?: () => void;
    onopenoverlap?: () => void;
    onopenlumpsum?: () => void;
    onopensql?: () => void;
    onsync?: () => void;
    onexportitr?: () => void;
  }

  let { 
    open = false, 
    quietMode = false, 
    onclose, 
    onselecttab, 
    ontogglequiet, 
    onopenoverlap, 
    onopenlumpsum, 
    onopensql,
    onsync, 
    onexportitr 
  }: Props = $props();

  let query = $state('');
  let selectedIndex = $state(0);


  interface CommandItem {
    id: string;
    title: string;
    subtitle: string;
    icon: any;
    shortcut?: string;
    action: () => void;
  }

  const allCommands = $derived.by<CommandItem[]>(() => [
    {
      id: 'tab-overview',
      title: 'Switch to Executive Overview',
      subtitle: 'View portfolio HUD, asset allocation buckets, and historical net worth trend',
      icon: LayoutDashboard,
      shortcut: '⌘1',
      action: () => { onselecttab?.('overview'); onclose?.(); }
    },
    {
      id: 'tab-tax-lots',
      title: 'Switch to Tax Lots & Capital Gains',
      subtitle: 'Inspect Scheme FIFO lots, Sec 112A headroom, and loss-harvest opportunities',
      icon: Receipt,
      shortcut: '⌘2',
      action: () => { onselecttab?.('tax-lots'); onclose?.(); }
    },
    {
      id: 'tab-rebalance',
      title: 'Switch to Unified Rebalance & FIRE',
      subtitle: 'Analyze portfolio drift, drawdown triggers, and retirement milestones',
      icon: Sliders,
      shortcut: '⌘3',
      action: () => { onselecttab?.('rebalance'); onclose?.(); }
    },
    {
      id: 'action-overlap',
      title: 'Inspect Portfolio Overlap & Lookthrough',
      subtitle: 'DuckDB pairwise scheme overlap matrix and top stock concentration',
      icon: PieChart,
      shortcut: '⌘O',
      action: () => { onopenoverlap?.(); onclose?.(); }
    },
    {
      id: 'action-lumpsum',
      title: 'Deploy Lump-Sum Inflow',
      subtitle: 'Simulate fresh capital allocation to restore target allocation tax-free',
      icon: Coins,
      shortcut: '⌘L',
      action: () => { onopenlumpsum?.(); onclose?.(); }
    },
    {
      id: 'action-quiet',
      title: quietMode ? 'Disable Quiet Mode (Reveal Figures)' : 'Enable Quiet Mode (Mask Financial Figures)',
      subtitle: 'Shield confidential net worth and rupee amounts from shoulder surfers',
      icon: quietMode ? Eye : EyeOff,
      shortcut: '⌘.',
      action: () => { ontogglequiet?.(); onclose?.(); }
    },
    {
      id: 'action-sync',
      title: 'Sync AMFI NAVs & Validate Ledger HMAC',
      subtitle: 'Trigger background NAV update from official AMFI daily data feeds',
      icon: RefreshCw,
      action: () => { onsync?.(); onclose?.(); }
    },
    {
      id: 'action-itr',
      title: 'Export Schedule 112A (ITR-2 CSV)',
      subtitle: 'Generate compliant capital gains report with FMV 2018 grandfathering',
      icon: FileSpreadsheet,
      action: () => { onexportitr?.(); onclose?.(); }
    },
    {
      id: 'action-sql',
      title: 'Open DuckDB-Wasm SQL Cockpit',
      subtitle: 'Run zero-copy, in-browser SQL queries over portfolio holdings and tax lots',
      icon: Database,
      shortcut: '⌘S',
      action: () => { onopensql?.(); onclose?.(); }
    }
  ]);

  const filteredCommands = $derived.by<CommandItem[]>(() => {
    const q = query.toLowerCase().trim();
    if (!q) return allCommands;
    return allCommands.filter((c: CommandItem) => 
      c.title.toLowerCase().includes(q) || 
      c.subtitle.toLowerCase().includes(q) ||
      (c.shortcut && c.shortcut.toLowerCase().includes(q))
    );
  });

  function handleKeydown(e: KeyboardEvent) {
    if (!open) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      selectedIndex = (selectedIndex + 1) % Math.max(1, filteredCommands.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      selectedIndex = (selectedIndex - 1 + filteredCommands.length) % Math.max(1, filteredCommands.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const list = filteredCommands;
      if (list[selectedIndex]) {
        list[selectedIndex].action();
      }
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onclose?.();
    }
  }


  $effect(() => {
    // Reset index on search query change
    query;
    selectedIndex = 0;
  });
</script>

<svelte:window onkeydown={handleKeydown} />

{#if open}
  <div 
    class="palette-backdrop" 
    role="presentation"
    onclick={onclose}
  >
    <div 
      class="palette-card"
      role="dialog"
      aria-modal="true"
      aria-label="Command Palette"
      tabindex="-1"
      onclick={(e) => e.stopPropagation()}
    >
      <!-- Search Input -->
      <div class="search-header">
        <Search size={18} class="search-icon" />
        <input 
          type="text" 
          placeholder="Type a command or jump to view..." 
          bind:value={query}
          class="palette-input"
          autofocus
        />
        <span class="esc-badge">ESC</span>
      </div>

      <!-- Command List -->
      <div class="command-list">
        {#if filteredCommands.length === 0}
          <div class="no-results">
            <p>No matching commands for "{query}"</p>
          </div>
        {:else}
          {#each filteredCommands as cmd, i (cmd.id)}
            {@const isSelected = i === selectedIndex}
            {@const Icon = cmd.icon}
            <div 
              class="command-item" 
              class:selected={isSelected}
              role="button"
              tabindex="0"
              onclick={() => cmd.action()}
              onmouseenter={() => selectedIndex = i}
              onkeydown={(e) => { if (e.key === 'Enter') cmd.action(); }}
            >
              <div class="cmd-icon-wrapper" class:selected={isSelected}>
                <Icon size={16} />
              </div>

              <div class="cmd-text">
                <span class="cmd-title">{cmd.title}</span>
                <span class="cmd-subtitle">{cmd.subtitle}</span>
              </div>

              {#if cmd.shortcut}
                <span class="cmd-shortcut mono">{cmd.shortcut}</span>
              {/if}

              {#if isSelected}
                <div class="enter-hint">
                  <CornerDownLeft size={12} />
                </div>
              {/if}
            </div>
          {/each}
        {/if}
      </div>

      <!-- Footer -->
      <div class="palette-footer">
        <div class="nav-tips">
          <span class="tip"><kbd>↑</kbd> <kbd>↓</kbd> to navigate</span>
          <span class="tip"><kbd>↵</kbd> to execute</span>
          <span class="tip"><kbd>esc</kbd> to close</span>
        </div>
        <div class="brand-tag">
          <Command size={11} />
          <span>Portfolio OS</span>
        </div>
      </div>
    </div>
  </div>
{/if}

<style>
  .palette-backdrop {
    position: fixed;
    inset: 0;
    background: var(--color-paper-3);
    backdrop-filter: blur(12px);
    display: flex;
    align-items: flex-start;
    justify-content: center;
    padding-top: 12vh;
    z-index: var(--z-modal);
    animation: fadeIn 0.15s ease-out;
  }

  .palette-card {
    background: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-card);
    width: 100%;
    max-width: 620px;
    box-shadow: none;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  .search-header {
    display: flex;
    align-items: center;
    position: relative;
    padding: 0.85rem 1.25rem;
    border-bottom: 1px solid var(--color-rule);
    background: var(--color-paper);
  }

  :global(.search-icon) {
    color: var(--color-muted);
    margin-right: 0.85rem;
    flex-shrink: 0;
  }

  .palette-input {
    flex: 1;
    background: transparent;
    border: none;
    color: var(--color-ink);
    font-size: 1rem;
    outline: none;
    font-family: inherit;
  }

  .palette-input:focus-visible {
    outline: none;
  }

  .esc-badge {
    padding: 0.15rem 0.4rem;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    font-size: 0.75rem;
    font-family: var(--font-outlier);
    color: var(--color-muted);
  }

  .command-list {
    max-height: 380px;
    overflow-y: auto;
    padding: 0.5rem;
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }

  .command-item {
    display: flex;
    align-items: center;
    gap: 0.85rem;
    padding: 0.75rem 0.85rem;
    border-radius: var(--radius-pill);
    cursor: pointer;
    border: 1px solid transparent;
    transition: background 0.15s ease, border-color 0.15s ease;
  }

  .command-item:focus-visible {
    outline: 2px solid var(--color-focus);
    outline-offset: 2px;
  }

  .command-item.selected {
    background: var(--color-cool-soft);
    border-color: var(--color-cool);
  }

  .cmd-icon-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    color: var(--color-muted);
    flex-shrink: 0;
  }

  .cmd-icon-wrapper.selected {
    background: var(--color-cool);
    color: var(--color-paper-3);
    border-color: var(--color-cool);
  }

  .cmd-text {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 0.15rem;
    min-width: 0;
  }

  .cmd-title {
    font-size: 0.88rem;
    font-weight: 500;
    color: var(--color-ink);
  }

  .cmd-subtitle {
    font-size: 0.75rem;
    color: var(--color-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .cmd-shortcut {
    padding: 0.15rem 0.45rem;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    font-size: 0.75rem;
    color: var(--color-muted);
    flex-shrink: 0;
  }

  .enter-hint {
    color: var(--color-cool);
    display: flex;
    align-items: center;
    padding-left: 0.25rem;
  }

  .no-results {
    padding: 2.5rem 1rem;
    text-align: center;
    color: var(--color-muted);
    font-size: 0.85rem;
  }

  .palette-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.6rem 1.25rem;
    border-top: 1px solid var(--color-rule);
    background: var(--color-paper);
    font-size: 0.75rem;
    color: var(--color-muted);
  }

  .nav-tips {
    display: flex;
    gap: 1rem;
  }

  .tip kbd {
    padding: 0.1rem 0.35rem;
    background: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-pill);
    font-family: var(--font-outlier);
  }

  .brand-tag {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    font-family: var(--font-outlier);
    font-size: 0.75rem;
    color: var(--color-cool);
  }

  @keyframes fadeIn {
    from { opacity: 0; transform: translateY(-8px); }
    to { opacity: 1; transform: translateY(0); }
  }

  @media (prefers-reduced-motion: reduce) {
    .palette-backdrop {
      animation: none !important;
    }
    .command-item {
      transition: none !important;
    }
  }
</style>
