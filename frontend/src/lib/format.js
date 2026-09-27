/**
 * Small, pure display-formatting helpers. Money always stays a string -
 * never parsed with Number/parseFloat (D-14/D-30's exactness contract).
 * Percent display multiplies a numeric metric by 100 because backend
 * metrics (PerformanceMetrics/BenchmarkResponse) are already JSON doubles,
 * not decimal strings.
 */

const DASH = '—';

/**
 * @param {string | null | undefined} value - an ISO-8601 (yyyy-MM-dd)
 *   LocalDate string, displayed as-is
 * @returns {string}
 */
export function formatDate(value) {
  return value ?? DASH;
}

/**
 * @param {number | null | undefined} ratio - e.g. 0.1532 for "15.32%"
 * @param {number} [fractionDigits]
 * @returns {string}
 */
export function formatPercent(ratio, fractionDigits = 2) {
  if (ratio === null || ratio === undefined) return DASH;
  return `${(ratio * 100).toFixed(fractionDigits)}%`;
}

/**
 * @param {string | null | undefined} amount - an exact decimal string
 *   already produced by the backend (BigDecimal#toPlainString)
 * @returns {string}
 */
export function formatMoney(amount) {
  return amount ?? DASH;
}

/**
 * @param {string | null | undefined} value - an ISO-8601 Instant string
 *   (e.g. "2024-01-15T10:30:00Z"); displays just its date portion.
 * @returns {string}
 */
export function formatInstantDate(value) {
  return value ? value.slice(0, 10) : DASH;
}

/**
 * @param {unknown} value
 * @returns {string}
 */
export function formatOrDash(value) {
  return value === null || value === undefined || value === '' ? DASH : String(value);
}

/**
 * Like {@link formatPercent}, with an explicit leading "+" for a positive
 * value - so a return/excess-return is never legible from color alone
 * (D-34 Batch 5 §17/§24).
 *
 * @param {number | null | undefined} ratio
 * @param {number} [fractionDigits]
 * @returns {string}
 */
export function formatSignedPercent(ratio, fractionDigits = 2) {
  if (ratio === null || ratio === undefined) return DASH;
  const sign = ratio > 0 ? '+' : '';
  return `${sign}${(ratio * 100).toFixed(fractionDigits)}%`;
}

/**
 * A plain ratio metric that is not a percentage (e.g. Sharpe ratio).
 *
 * @param {number | null | undefined} value
 * @param {number} [fractionDigits]
 * @returns {string}
 */
export function formatRatio(value, fractionDigits = 2) {
  if (value === null || value === undefined) return DASH;
  return value.toFixed(fractionDigits);
}

/**
 * A statistical dollar figure the *engine* itself derives as a double
 * (PerformanceMetrics.averageWin/averageLoss - D-26: "derived statistics
 * are double", unlike the exact BigDecimal-string ledger values
 * {@link formatMoney} displays). There is no string to parse here - the
 * backend already returns a JSON number for these two fields specifically.
 *
 * @param {number | null | undefined} value
 * @param {number} [fractionDigits]
 * @returns {string}
 */
export function formatStatMoney(value, fractionDigits = 2) {
  if (value === null || value === undefined) return DASH;
  return value.toFixed(fractionDigits);
}
