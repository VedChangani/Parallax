/**
 * The D-30 decimal grammar, reused verbatim by every decimal-string field
 * the backend accepts (Constant/CashFraction values, and D-34's
 * BacktestConfig initialCapital/commissionPerFill/slippageRate): an
 * optional leading "-", an integer part with no leading zero (unless it is
 * exactly "0"), an optional fractional part, and an optional exponent.
 * Matches `StrategyDefinitionMapper.DECIMAL` exactly.
 *
 * This is syntax only. Semantic bounds (RSI period >= 2, 0 < CashFraction
 * <= 1, BacktestConfig field bounds, etc.) are the backend's responsibility
 * and are never duplicated here.
 */
export const DECIMAL_PATTERN = /^-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?$/;

/**
 * @param {unknown} value
 * @returns {boolean}
 */
export function isValidDecimalString(value) {
  return typeof value === 'string' && DECIMAL_PATTERN.test(value);
}

// --- percent <-> fraction conversion (D-34 slippage transport) -------------
//
// BacktestConfig.slippageRate is transported as a fraction ("0.0005"), but
// the New Backtest form edits it as a percentage ("0.05"). This conversion
// is exact digit-shifting on the decimal string itself - never a
// `Number()`/`parseFloat()` multiply/divide - so "1" -> "0.01" and
// "0.05" -> "0.0005" round-trip exactly rather than drift through
// floating-point arithmetic. Restricted to plain (non-exponent),
// non-negative decimals: slippage can never be negative, and nobody types a
// percentage in exponent notation.

const PLAIN_NONNEGATIVE_DECIMAL = /^(0|[1-9][0-9]*)(\.[0-9]+)?$/;

/**
 * @param {unknown} value
 * @returns {boolean}
 */
export function isValidPercentString(value) {
  return typeof value === 'string' && PLAIN_NONNEGATIVE_DECIMAL.test(value.trim());
}

/**
 * Shifts a plain, non-negative decimal string's point `places` positions to
 * the right (negative `places` shifts left), as exact digit manipulation.
 * `text` must already satisfy {@link PLAIN_NONNEGATIVE_DECIMAL}.
 *
 * @param {string} text
 * @param {number} places
 * @returns {string}
 */
function shiftDecimalPoint(text, places) {
  const dotIndex = text.indexOf('.');
  let digits = dotIndex === -1 ? text : text.slice(0, dotIndex) + text.slice(dotIndex + 1);
  let pointPos = (dotIndex === -1 ? text.length : dotIndex) + places;

  if (pointPos < 0) {
    digits = '0'.repeat(-pointPos) + digits;
    pointPos = 0;
  }
  if (pointPos > digits.length) {
    digits = digits + '0'.repeat(pointPos - digits.length);
  }

  let intPart = digits.slice(0, pointPos).replace(/^0+/, '');
  if (intPart === '') intPart = '0';
  const fracPart = digits.slice(pointPos).replace(/0+$/, '');

  return fracPart ? `${intPart}.${fracPart}` : intPart;
}

/**
 * Converts a percentage decimal string into the equivalent fraction decimal
 * string, e.g. "0.05" -> "0.0005", "1" -> "0.01", "100" -> "1", "0" -> "0".
 *
 * @param {string} percentText
 * @returns {string | undefined} the fraction string, or `undefined` if
 *   `percentText` is not a valid non-negative plain decimal
 */
export function percentTextToFractionText(percentText) {
  if (!isValidPercentString(percentText)) return undefined;
  return shiftDecimalPoint(percentText.trim(), -2);
}

/**
 * The inverse of {@link percentTextToFractionText}: "0.0005" -> "0.05",
 * "0.01" -> "1".
 *
 * @param {string} fractionText
 * @returns {string | undefined} the percent string, or `undefined` if
 *   `fractionText` is not a valid non-negative plain decimal
 */
export function fractionTextToPercentText(fractionText) {
  if (!isValidPercentString(fractionText)) return undefined;
  return shiftDecimalPoint(fractionText.trim(), 2);
}
