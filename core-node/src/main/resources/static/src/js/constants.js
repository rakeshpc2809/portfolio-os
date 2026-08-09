export const FUND_REGISTRY = {
  'INF879O01027': 'PPFAS Flexi Cap',
  'INF109KC13X2': 'Value 30',
  'INF109KC12U0': 'LargeMidcap 250',
  'INF204K01K15': 'Nippon Small Cap',
  'INF754K01TN5': 'Edelweiss Nifty500 MQ50',
  'INF174KA1TY2': 'Kotak 100 Equal Weight',
  'INF247L01916': 'Motilal Midcap 150',
  'INF247L01BQ9': 'Motilal Microcap 250',
  'INF247L01BM8': 'Motilal Gold & Silver FoF'
};

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
