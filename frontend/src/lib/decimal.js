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
