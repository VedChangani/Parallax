import { isValidDecimalString, percentTextToFractionText } from '../../lib/decimal.js';

/**
 * Pure client-side validation for the New Backtest configuration form
 * (D-34). This deliberately only checks syntax and the most basic guards
 * (CLAUDE.md: "Do not duplicate BacktestConfig engine semantics") - the
 * engine's own {@code BacktestConfig} constructor (via
 * {@code BacktestConfigMapper}) remains the sole semantic authority for
 * bounds like "commissionPerFill >= 0" or "slippageRate < 1"; a value that
 * passes here can still be rejected by the backend as a 422.
 */

const ISO_DATE_SHAPE = /^\d{4}-\d{2}-\d{2}$/;
const ZERO_DECIMAL = /^0(\.0+)?$/;

/**
 * @param {string} text
 * @returns {boolean} whether `text` is a real calendar date, not just
 *   shaped like `yyyy-MM-dd` (rejects e.g. "2024-02-30")
 */
function isValidIsoDate(text) {
  if (!ISO_DATE_SHAPE.test(text)) return false;
  const [year, month, day] = text.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

/**
 * @param {string} text
 * @returns {string | undefined} an error message, or `undefined` if valid
 */
export function validateInitialCapital(text) {
  const trimmed = (text ?? '').trim();
  if (trimmed === '') return 'Initial capital is required.';
  if (!isValidDecimalString(trimmed)) return 'Enter a valid amount, e.g. 100000.';
  if (trimmed.startsWith('-') || ZERO_DECIMAL.test(trimmed)) return 'Initial capital must be greater than zero.';
  return undefined;
}

/**
 * @param {string} text
 * @returns {string | undefined} an error message, or `undefined` if valid
 */
export function validateCommissionPerFill(text) {
  const trimmed = (text ?? '').trim();
  if (trimmed === '') return 'Commission per fill is required.';
  if (!isValidDecimalString(trimmed)) return 'Enter a valid amount, e.g. 0 or 1.5.';
  if (trimmed.startsWith('-')) return 'Commission per fill cannot be negative.';
  return undefined;
}

/**
 * Validates the user-facing percentage text and converts it to the
 * transport fraction string in the same pass, via exact string-based
 * digit-shifting (never `Number()`/`parseFloat()` - see lib/decimal.js).
 *
 * @param {string} text - e.g. "0.05" or "0.05%"
 * @returns {{ fraction: string, error: undefined } | { fraction: undefined, error: string }}
 */
export function validateSlippagePercent(text) {
  const trimmed = (text ?? '').trim();
  if (trimmed === '') return { fraction: undefined, error: 'Slippage is required.' };
  const withoutPercentSign = trimmed.endsWith('%') ? trimmed.slice(0, -1).trim() : trimmed;
  const fraction = percentTextToFractionText(withoutPercentSign);
  if (fraction === undefined) {
    return { fraction: undefined, error: 'Enter a valid percentage, e.g. 0.05% or 1%.' };
  }
  return { fraction, error: undefined };
}

/**
 * @param {string} startDate
 * @param {string} endDate
 * @returns {{ startDate?: string, endDate?: string }} field errors, if any
 */
export function validateDateRange(startDate, endDate) {
  const errors = {};

  if (!startDate) {
    errors.startDate = 'Start date is required.';
  } else if (!isValidIsoDate(startDate)) {
    errors.startDate = 'Enter a valid date (YYYY-MM-DD).';
  }

  if (!endDate) {
    errors.endDate = 'End date is required.';
  } else if (!isValidIsoDate(endDate)) {
    errors.endDate = 'Enter a valid date (YYYY-MM-DD).';
  }

  if (!errors.startDate && !errors.endDate && startDate > endDate) {
    errors.endDate = 'End date must be on or after the start date.';
  }

  return errors;
}

/**
 * Validates the full configuration form in one pass.
 *
 * @param {{initialCapital: string, commissionPerFill: string, slippagePercent: string, startDate: string, endDate: string}} fields
 * @returns {{
 *   fieldErrors: {initialCapital?: string, commissionPerFill?: string, slippage?: string, startDate?: string, endDate?: string},
 *   config: import('../../api/types.js').BacktestConfigRequest | undefined
 * }} `config` is only present when `fieldErrors` is empty.
 */
export function validateBacktestConfigForm({ initialCapital, commissionPerFill, slippagePercent, startDate, endDate }) {
  const fieldErrors = {};

  const capitalError = validateInitialCapital(initialCapital);
  if (capitalError) fieldErrors.initialCapital = capitalError;

  const commissionError = validateCommissionPerFill(commissionPerFill);
  if (commissionError) fieldErrors.commissionPerFill = commissionError;

  const slippageResult = validateSlippagePercent(slippagePercent);
  if (slippageResult.error) fieldErrors.slippage = slippageResult.error;

  Object.assign(fieldErrors, validateDateRange(startDate, endDate));

  if (Object.keys(fieldErrors).length > 0) {
    return { fieldErrors, config: undefined };
  }

  return {
    fieldErrors,
    config: {
      initialCapital: initialCapital.trim(),
      commissionPerFill: commissionPerFill.trim(),
      slippageRate: slippageResult.fraction,
      startDate,
      endDate,
    },
  };
}
