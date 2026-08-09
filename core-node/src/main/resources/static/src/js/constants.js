export const FUND_REGISTRY = {};

export const BADGE_STYLES = {
  ACTION_RECOMMENDED: {
    HIGH: { bg: 'rgba(239, 68, 68, 0.2)', color: '#f87171' },
    DEFAULT: { bg: 'rgba(245, 158, 11, 0.2)', color: '#fbbf24' }
  },
  GATED_PROVISIONAL: { bg: 'rgba(100, 116, 139, 0.2)', color: '#94a3b8' },
  DEFAULT: { bg: 'rgba(16, 185, 129, 0.2)', color: '#34d399' }
};

export function getActionBadgeStyle(status, severity) {
  if (status === 'ACTION_RECOMMENDED') {
    return severity === 'HIGH' ? BADGE_STYLES.ACTION_RECOMMENDED.HIGH : BADGE_STYLES.ACTION_RECOMMENDED.DEFAULT;
  }
  if (status === 'GATED_PROVISIONAL') {
    return BADGE_STYLES.GATED_PROVISIONAL;
  }
  return BADGE_STYLES.DEFAULT;
}
