const DASH = '—';

export function formatDate(value) {
  return value ?? DASH;
}

export function formatPercent(ratio, fractionDigits = 2) {
  if (ratio === null || ratio === undefined) return DASH;
  return `${(ratio * 100).toFixed(fractionDigits)}%`;
}

export function formatMoney(amount) {
  return amount ?? DASH;
}

export function formatInstantDate(value) {
  return value ? value.slice(0, 10) : DASH;
}

export function formatOrDash(value) {
  return value === null || value === undefined || value === '' ? DASH : String(value);
}

export function formatSignedPercent(ratio, fractionDigits = 2) {
  if (ratio === null || ratio === undefined) return DASH;
  const sign = ratio > 0 ? '+' : '';
  return `${sign}${(ratio * 100).toFixed(fractionDigits)}%`;
}

export function formatRatio(value, fractionDigits = 2) {
  if (value === null || value === undefined) return DASH;
  return value.toFixed(fractionDigits);
}

export function formatStatMoney(value, fractionDigits = 2) {
  if (value === null || value === undefined) return DASH;
  return value.toFixed(fractionDigits);
}
