/**
 * Currency and financial number formatters (Indian Numbering System)
 */

export function formatINR(val: number | null | undefined, quietMode = false): string {
  if (quietMode) return '••••••••';
  if (val === null || val === undefined || isNaN(val)) return '₹ 0';
  const isNeg = val < 0;
  const absVal = Math.round(Math.abs(val));
  const str = absVal.toString();

  if (str.length <= 3) {
    return `${isNeg ? '-' : ''}₹ ${str}`;
  }

  const lastThree = str.substring(str.length - 3);
  const otherNumbers = str.substring(0, str.length - 3);
  const formattedOther = otherNumbers.replace(/\B(?=(\d{2})+(?!\d))/g, ',');

  return `${isNeg ? '-' : ''}₹ ${formattedOther},${lastThree}`;
}

export function formatPercent(val: number | null | undefined, decimals = 1): string {
  if (val === null || val === undefined || isNaN(val)) return '--%';
  const prefix = val > 0 ? '+' : '';
  return `${prefix}${val.toFixed(decimals)}%`;
}

export function formatCompactINR(val: number | null | undefined, quietMode = false): string {
  if (quietMode) return '••••••••';
  if (val === null || val === undefined || isNaN(val)) return '₹ 0';
  const abs = Math.abs(val);
  const sign = val < 0 ? '-' : '';

  if (abs >= 10000000) {
    return `${sign}₹ ${(abs / 10000000).toFixed(2)} Cr`;
  }
  if (abs >= 100000) {
    return `${sign}₹ ${(abs / 100000).toFixed(2)} L`;
  }
  if (abs >= 1000) {
    return `${sign}₹ ${(abs / 1000).toFixed(1)} K`;
  }
  return formatINR(val, quietMode);
}
