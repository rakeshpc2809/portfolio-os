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
    accentGlow?: 'cyan' | 'emerald' | 'amber' | 'violet';
    accentColor?: 'cyan' | 'emerald' | 'amber' | 'violet';
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
        <span class="mask-badge">QUIET SHIELD</span>
      </div>
    {:else}
      <div class="hud-value tnum">{displayValue}</div>
    {/if}
  </div>

  {#if progressPct !== null}
    <div class="progress-track">
      <div class="progress-fill fill-{effectiveAccent}" style="width: {Math.min(100, Math.max(0, progressPct))}%;"></div>
    </div>
  {/if}

  {#if computedSubtext}
    <div class="hud-subtext" style={subtextColor ? `color: ${subtextColor};` : ''}>
      {computedSubtext}
    </div>
  {/if}
</div>

<style>
  .hud-card {
    background: var(--surface-card);
    backdrop-filter: blur(28px) saturate(190%);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-lg);
    padding: 22px 24px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    min-height: 130px;
    position: relative;
    overflow: hidden;
    box-shadow: 0 10px 25px -8px rgba(0, 0, 0, 0.4);
    transition: transform 0.28s var(--ease-bounce), 
                border-color 0.2s ease, 
                box-shadow 0.28s var(--ease-bounce);
  }

  .hud-card:hover {
    transform: translateY(-5px) scale(1.018);
    border-color: var(--border-subtle);
  }

  /* Radiant accent glow on hover */
  .glow-cyan:hover {
    box-shadow: 0 18px 40px -10px rgba(6, 182, 212, 0.35);
    border-color: rgba(6, 182, 212, 0.45);
  }
  .glow-emerald:hover {
    box-shadow: 0 18px 40px -10px rgba(16, 185, 129, 0.35);
    border-color: rgba(16, 185, 129, 0.45);
  }
  .glow-amber:hover {
    box-shadow: 0 18px 40px -10px rgba(245, 158, 11, 0.35);
    border-color: rgba(245, 158, 11, 0.45);
  }
  .glow-violet:hover {
    box-shadow: 0 18px 40px -10px rgba(168, 85, 247, 0.35);
    border-color: rgba(168, 85, 247, 0.45);
  }

  /* Vibrant top accent line */
  .hud-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 2px;
    opacity: 0.6;
    transition: opacity 0.2s ease, height 0.2s ease;
  }
  .glow-cyan::before { background: linear-gradient(90deg, transparent, var(--accent-cyan), transparent); }
  .glow-emerald::before { background: linear-gradient(90deg, transparent, var(--accent-emerald), transparent); }
  .glow-amber::before { background: linear-gradient(90deg, transparent, var(--accent-amber), transparent); }
  .glow-violet::before { background: linear-gradient(90deg, transparent, var(--accent-violet), transparent); }

  .hud-card:hover::before {
    opacity: 1;
    height: 3px;
  }

  .hud-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .label-with-icon {
    display: flex;
    align-items: center;
    gap: 9px;
  }

  .hud-icon {
    opacity: 0.95;
    transition: transform 0.25s var(--ease-bounce);
  }
  .hud-card:hover .hud-icon {
    transform: scale(1.15) rotate(4deg);
  }
  .icon-cyan { color: var(--accent-cyan); }
  .icon-emerald { color: var(--accent-emerald); }
  .icon-amber { color: var(--accent-amber); }
  .icon-violet { color: var(--accent-violet); }

  .hud-label {
    font-family: var(--font-sans);
    font-size: 0.8rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    color: var(--text-muted);
    text-transform: uppercase;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .badge {
    font-family: var(--font-sans);
    font-size: 0.72rem;
    font-weight: 700;
    padding: 3px 9px;
    border-radius: var(--radius-pill);
    letter-spacing: 0.03em;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    transition: transform 0.2s var(--ease-bounce);
  }
  .badge:hover {
    transform: scale(1.05);
  }
  .badge-positive {
    background: rgba(16, 185, 129, 0.16);
    color: var(--accent-emerald-bright);
    border: 1px solid rgba(16, 185, 129, 0.35);
  }
  .badge-neutral {
    background: rgba(148, 163, 184, 0.15);
    color: var(--text-secondary);
    border: 1px solid rgba(255, 255, 255, 0.14);
  }
  .badge-negative, .badge-drift {
    background: rgba(244, 63, 94, 0.16);
    color: var(--accent-rose-bright);
    border: 1px solid rgba(244, 63, 94, 0.35);
  }

  .hud-pip {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    animation: pipPulse 2.4s infinite ease-in-out;
  }
  .pip-cyan { background: var(--accent-cyan); box-shadow: 0 0 10px var(--accent-cyan); }
  .pip-emerald { background: var(--accent-emerald); box-shadow: 0 0 10px var(--accent-emerald); }
  .pip-amber { background: var(--accent-amber); box-shadow: 0 0 10px var(--accent-amber); }
  .pip-violet { background: var(--accent-violet); box-shadow: 0 0 10px var(--accent-violet); }

  @keyframes pipPulse {
    0%, 100% { transform: scale(1); opacity: 0.9; }
    50% { transform: scale(1.25); opacity: 1; }
  }

  .hud-value-container {
    margin: 10px 0 6px 0;
  }

  .hud-value {
    font-family: var(--font-brand);
    font-size: 2.15rem;
    font-weight: 800;
    color: #FFFFFF;
    letter-spacing: -0.025em;
    line-height: 1.1;
  }

  .quiet-mask {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .mask-text {
    font-family: var(--font-brand);
    font-size: 1.75rem;
    font-weight: 800;
    color: var(--text-muted);
    letter-spacing: 0.08em;
  }

  .mask-badge {
    font-family: var(--font-sans);
    font-size: 0.68rem;
    font-weight: 800;
    padding: 3px 8px;
    border-radius: var(--radius-pill);
    background: rgba(6, 182, 212, 0.16);
    color: var(--accent-cyan-bright);
    border: 1px solid rgba(6, 182, 212, 0.4);
  }

  .progress-track {
    width: 100%;
    height: 4px;
    background: rgba(255, 255, 255, 0.08);
    border-radius: var(--radius-pill);
    overflow: hidden;
    margin: 6px 0 8px 0;
  }

  .progress-fill {
    height: 100%;
    border-radius: var(--radius-pill);
    transition: width 0.5s var(--ease-bounce);
  }
  .fill-cyan { background: linear-gradient(90deg, var(--accent-cyan), var(--accent-cyan-bright)); }
  .fill-emerald { background: linear-gradient(90deg, var(--accent-emerald), var(--accent-emerald-bright)); }
  .fill-amber { background: linear-gradient(90deg, var(--accent-amber), var(--accent-amber-bright)); }
  .fill-violet { background: linear-gradient(90deg, var(--accent-violet), var(--accent-violet-bright)); }

  .hud-subtext {
    font-size: 0.82rem;
    font-family: var(--font-sans);
    font-weight: 500;
    color: var(--text-secondary);
    line-height: 1.3;
    display: flex;
    align-items: center;
    gap: 8px;
  }
</style>
