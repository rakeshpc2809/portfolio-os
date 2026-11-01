<script lang="ts">
  import type { Component } from 'svelte';

  interface Props {
    label: string;
    value?: string;
    formattedValue?: string;
    subLabel?: string;
    subValue?: string;
    subtext?: string;
    subtextColor?: string;
    badgeText?: string;
    badgeType?: 'positive' | 'neutral' | 'negative' | 'drift';
    icon?: Component<any> | any;
    isQuiet?: boolean;
    quietMode?: boolean;
    accentGlow?: 'cyan' | 'emerald' | 'amber' | 'violet' | 'rose';
    accentColor?: 'cyan' | 'emerald' | 'amber' | 'violet' | 'rose';
    progressPct?: number | null;
  }

  let {
    label,
    value,
    formattedValue,
    subLabel,
    subValue,
    subtext = '',
    subtextColor,
    badgeText,
    badgeType = 'positive',
    icon: IconComponent,
    isQuiet = false,
    quietMode = false,
    accentGlow,
    accentColor,
    progressPct = null
  }: Props = $props();

  const displayValue = $derived(formattedValue ?? value ?? '—');
  const effectiveQuiet = $derived(isQuiet || quietMode);
  const effectiveAccent = $derived(accentColor ?? accentGlow ?? 'cyan');
  const computedSubtext = $derived(
    subtext || (subLabel && subValue ? `${subLabel}: ${subValue}` : (subLabel || subValue || ''))
  );
  const isNegativeValue = $derived(
    String(displayValue).startsWith('-') || 
    String(displayValue).includes('-%') || 
    String(displayValue).startsWith('-₹')
  );
  const isNegativeSubvalue = $derived(
    subValue != null ? (String(subValue).startsWith('-') || String(subValue).includes('-%') || String(subValue).startsWith('-₹')) : false
  );
</script>

<div class="hud-card glow-{effectiveAccent}">
  <div class="hud-header">
    <div class="label-with-icon">
      {#if IconComponent}
        <IconComponent size={14} class="hud-icon icon-{effectiveAccent}" />
      {/if}
      <span class="hud-label">{label}</span>
    </div>
    <div class="header-right">
      {#if badgeText}
        <span class="badge badge-{badgeType}">{badgeText}</span>
      {/if}
      <span class="hud-pip pip-{effectiveAccent}"></span>
    </div>
  </div>

  <div class="hud-value-container">
    {#if effectiveQuiet}
      <div class="quiet-mask">
        <span class="mask-text">₹ •••,••,•••</span>
        <span class="mask-badge">Quiet Shield</span>
      </div>
    {:else}
      <div class="hud-value tnum" class:negative={isNegativeValue}>{displayValue}</div>
    {/if}
  </div>

  {#if progressPct !== null}
    <div class="progress-track">
      <div class="progress-fill fill-{effectiveAccent}" style="transform: scaleX({Math.min(1, Math.max(0, (progressPct ?? 0) / 100))});"></div>
    </div>
  {/if}

  {#if computedSubtext}
    <div class="hud-subtext" style={subtextColor ? `color: ${subtextColor};` : ''}>
      {#if subLabel && subValue}
        <span class="sub-label">{subLabel}:</span>
        <span class="sub-value" class:negative={isNegativeSubvalue} class:positive={!isNegativeSubvalue && (subLabel.includes('Return') || subLabel.includes('Gain'))}>{subValue}</span>
      {:else}
        <span class:negative={String(computedSubtext).startsWith('-') || String(computedSubtext).startsWith('-₹')}>{computedSubtext}</span>
      {/if}
    </div>
  {/if}
</div>

<style>
  .hud-card {
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 18px 20px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    min-height: 125px;
    position: relative;
    overflow: hidden;
    transition: border-color 0.15s ease, background-color 0.15s ease;
  }

  .hud-card:hover {
    background: var(--surface-card-hover);
    border-color: var(--border-subtle);
  }

  .hud-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .label-with-icon {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .hud-icon {
    opacity: 0.85;
  }
  .icon-cyan { color: var(--accent-cyan); }
  .icon-emerald { color: var(--accent-emerald); }
  .icon-amber { color: var(--accent-amber); }
  .icon-violet { color: var(--accent-violet); }
  .icon-rose { color: var(--accent-rose); }

  .hud-label {
    font-family: var(--font-sans);
    font-size: 0.82rem;
    font-weight: 500;
    color: var(--text-muted);
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .badge {
    font-family: var(--font-sans);
    font-size: 0.72rem;
    font-weight: 500;
    padding: 2px 7px;
    border-radius: var(--radius-xs);
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
  .badge-positive {
    background: var(--color-ok-soft);
    color: var(--color-ok);
    border: 1px solid var(--border-hairline);
  }
  .badge-neutral {
    background: var(--surface-elevated);
    color: var(--text-secondary);
    border: 1px solid var(--border-hairline);
  }
  .badge-negative, .badge-drift {
    background: var(--color-bad-soft);
    color: var(--color-bad);
    border: 1px solid var(--border-hairline);
  }

  .hud-pip {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }
  .pip-cyan { background: var(--accent-cyan); }
  .pip-emerald { background: var(--accent-emerald); }
  .pip-amber { background: var(--accent-amber); }
  .pip-violet { background: var(--accent-violet); }
  .pip-rose { background: var(--accent-rose); }

  .hud-value-container {
    margin: 8px 0 4px 0;
  }

  .hud-value {
    font-family: var(--font-mono);
    font-size: 1.85rem;
    font-weight: 600;
    color: var(--text-main);
    letter-spacing: -0.02em;
    line-height: 1.15;
  }

  .quiet-mask {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .mask-text {
    font-family: var(--font-mono);
    font-size: 1.5rem;
    font-weight: 600;
    color: var(--text-muted);
    letter-spacing: 0.04em;
  }

  .mask-badge {
    font-family: var(--font-sans);
    font-size: 0.68rem;
    font-weight: 500;
    padding: 2px 6px;
    border-radius: var(--radius-xs);
    background: var(--surface-elevated);
    color: var(--accent-cyan);
    border: 1px solid var(--border-hairline);
  }

  .progress-track {
    width: 100%;
    height: 3px;
    background: var(--surface-void);
    border-radius: var(--radius-xs);
    overflow: hidden;
    margin: 4px 0 6px 0;
  }

  .progress-fill {
    height: 100%;
    width: 100%;
    transform-origin: left;
    border-radius: var(--radius-xs);
    transition: transform 0.3s ease;
  }
  .fill-cyan { background: var(--accent-cyan); }
  .fill-emerald { background: var(--accent-emerald); }
  .fill-amber { background: var(--accent-amber); }
  .fill-violet { background: var(--accent-violet); }
  .fill-rose { background: var(--accent-rose); }

  .hud-value.negative {
    color: var(--accent-rose-bright) !important;
  }

  .hud-subtext {
    font-size: 0.8rem;
    font-family: var(--font-sans);
    font-weight: 500;
    color: var(--text-secondary);
    line-height: 1.3;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .sub-value.negative {
    color: var(--accent-rose-bright) !important;
    font-weight: 600;
  }
  .sub-value.positive {
    color: var(--accent-emerald-bright) !important;
    font-weight: 600;
  }

  @media (prefers-reduced-motion: reduce) {
    .progress-fill {
      transition: none !important;
    }
  }
</style>
