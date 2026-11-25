<script lang="ts">
  import type { HoldingDto } from '../api';
  import CashRouterPanel from './CashRouterPanel.svelte';
  import { X } from '@lucide/svelte';

  interface Props {
    open?: boolean;
    holdings?: HoldingDto[];
    netWorth?: number;
    quietMode?: boolean;
    onclose?: () => void;
  }

  let {
    open = false,
    holdings = [],
    netWorth = 0,
    quietMode = false,
    onclose
  }: Props = $props();
</script>

{#if open}
  <div
    id="lumpsumModal"
    class="drawer-backdrop"
    role="presentation"
    onclick={onclose}
    onkeydown={(e) => { if (e.key === 'Escape') onclose?.(); }}
  >
    <div
      class="drawer-sheet"
      role="dialog"
      aria-modal="true"
      aria-label="Fresh Bank Cash Router"
      tabindex="-1"
      onclick={(e) => e.stopPropagation()}
      onkeydown={(e) => e.stopPropagation()}
    >
      <div class="drawer-top">
        <span class="drawer-kicker">DEPLOY FRESH BANK CASH</span>
        <button type="button" class="drawer-close" onclick={onclose} aria-label="Close">
          <X size={16} />
        </button>
      </div>
      <div class="drawer-body">
        <CashRouterPanel {holdings} {netWorth} {quietMode} />
      </div>
    </div>
  </div>
{/if}

<style>
  .drawer-backdrop {
    position: fixed;
    inset: 0;
    z-index: var(--z-modal);
    background: rgba(0, 0, 0, 0.72);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 16px;
  }

  .drawer-sheet {
    width: 100%;
    max-width: 780px;
    max-height: 90vh;
    overflow-y: auto;
    background: var(--bg-modal);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-lg);
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .drawer-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .drawer-kicker {
    font-size: 0.72rem;
    font-weight: 600;
    letter-spacing: 0.05em;
    color: var(--ink-muted);
  }

  .drawer-close {
    background: var(--bg-elevated);
    border: 1px solid var(--border-hair);
    color: var(--ink-secondary);
    border-radius: var(--radius-sm);
    width: 34px;
    height: 34px;
    display: grid;
    place-items: center;
    cursor: pointer;
  }

  @media (max-width: 768px) {
    .drawer-backdrop {
      align-items: flex-end;
      padding: 0;
    }
    .drawer-sheet {
      border-bottom-left-radius: 0;
      border-bottom-right-radius: 0;
      max-height: 88vh;
    }
  }
</style>
