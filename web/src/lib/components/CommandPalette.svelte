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
    Command
  } from '@lucide/svelte';

  interface Props {
    open?: boolean;
    quietMode?: boolean;
    onclose?: () => void;
    onselecttab?: (tab: 'overview' | 'tax-lots' | 'rebalance') => void;
    ontogglequiet?: () => void;
    onopenoverlap?: () => void;
    onopenlumpsum?: () => void;
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

  const allCommands = $derived<CommandItem[]>(() => [
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
    }
  ]);

  const filteredCommands = $derived<CommandItem[]>(() => {
    const q = query.toLowerCase().trim();
    if (!q) return allCommands();
    return allCommands().filter(c => 
      c.title.toLowerCase().includes(q) || 
      c.subtitle.toLowerCase().includes(q) ||
      (c.shortcut && c.shortcut.toLowerCase().includes(q))
    );
  });

  function handleKeydown(e: KeyboardEvent) {
    if (!open) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      selectedIndex = (selectedIndex + 1) % Math.max(1, filteredCommands().length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      selectedIndex = (selectedIndex - 1 + filteredCommands().length) % Math.max(1, filteredCommands().length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const list = filteredCommands();
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
        {#if filteredCommands().length === 0}
          <div class="no-results">
            <p>No matching commands for "{query}"</p>
          </div>
        {:else}
          {#each filteredCommands() as cmd, i (cmd.id)}
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
          <span>PORTFOLIO OS</span>
        </div>
      </div>
    </div>
  </div>
{/if}

<style>
  .palette-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(4, 7, 13, 0.85);
    backdrop-filter: blur(12px);
    display: flex;
    align-items: flex-start;
    justify-content: center;
    padding-top: 12vh;
    z-index: 2000;
    animation: fadeIn var(--transition-fast) ease-out;
  }

  .palette-card {
    background: var(--surface-card);
    border: 1px solid var(--border-medium);
    border-radius: var(--radius-xl);
    width: 100%;
    max-width: 620px;
    box-shadow: 0 25px 60px rgba(0, 0, 0, 0.9), 0 0 35px var(--glow-cyan);
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  .search-header {
    display: flex;
    align-items: center;
    position: relative;
    padding: 0.85rem 1.25rem;
    border-bottom: 1px solid var(--border-subtle);
    background: var(--surface-card-hover);
  }

  :global(.search-icon) {
    color: var(--text-muted);
    margin-right: 0.85rem;
    flex-shrink: 0;
  }

  .palette-input {
    flex: 1;
    background: transparent;
    border: none;
    color: var(--text-primary);
    font-size: 1rem;
    outline: none;
    font-family: inherit;
  }

  .esc-badge {
    padding: 0.15rem 0.4rem;
    background: var(--surface-glass);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    font-size: 0.65rem;
    font-family: var(--font-mono);
    color: var(--text-muted);
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
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
  }

  .command-item.selected {
    background: hsla(185, 85%, 52%, 0.12);
    box-shadow: inset 0 0 0 1px hsla(185, 85%, 52%, 0.35);
  }

  .cmd-icon-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    background: var(--surface-glass);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    color: var(--text-secondary);
    flex-shrink: 0;
  }

  .cmd-icon-wrapper.selected {
    background: var(--accent-cyan);
    color: var(--surface-void);
    border-color: var(--accent-cyan);
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
    color: var(--text-primary);
  }

  .cmd-subtitle {
    font-size: 0.72rem;
    color: var(--text-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .cmd-shortcut {
    padding: 0.15rem 0.45rem;
    background: var(--surface-void);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    font-size: 0.68rem;
    color: var(--text-muted);
    flex-shrink: 0;
  }

  .enter-hint {
    color: var(--accent-cyan);
    display: flex;
    align-items: center;
    padding-left: 0.25rem;
  }

  .no-results {
    padding: 2.5rem 1rem;
    text-align: center;
    color: var(--text-muted);
    font-size: 0.85rem;
  }

  .palette-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.6rem 1.25rem;
    border-top: 1px solid var(--border-subtle);
    background: var(--surface-card-hover);
    font-size: 0.7rem;
    color: var(--text-muted);
  }

  .nav-tips {
    display: flex;
    gap: 1rem;
  }

  .tip kbd {
    padding: 0.1rem 0.35rem;
    background: var(--surface-glass);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    font-family: var(--font-mono);
  }

  .brand-tag {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    font-family: var(--font-mono);
    font-size: 0.65rem;
    color: var(--accent-cyan);
  }

  @keyframes fadeIn {
    from { opacity: 0; transform: translateY(-8px); }
    to { opacity: 1; transform: translateY(0); }
  }
</style>
