import { describe, expect, it } from 'vitest';
import {
  validateBacktestConfigForm,
  validateCommissionPerFill,
  validateDateRange,
  validateInitialCapital,
  validateSlippagePercent,
} from './backtestConfigValidation.js';

describe('validateInitialCapital', () => {
  it('accepts a valid positive amount', () => {
    expect(validateInitialCapital('100000')).toBeUndefined();
    expect(validateInitialCapital('1.5')).toBeUndefined();
  });

  it('rejects empty, zero, negative, or malformed input', () => {
    expect(validateInitialCapital('')).toBe('Initial capital is required.');
    expect(validateInitialCapital('   ')).toBe('Initial capital is required.');
    expect(validateInitialCapital('0')).toBe('Initial capital must be greater than zero.');
    expect(validateInitialCapital('0.00')).toBe('Initial capital must be greater than zero.');
    expect(validateInitialCapital('-100')).toBe('Initial capital must be greater than zero.');
    expect(validateInitialCapital('abc')).toBe('Enter a valid amount, e.g. 100000.');
    expect(validateInitialCapital('1,000')).toBe('Enter a valid amount, e.g. 100000.');
  });
});

describe('validateCommissionPerFill', () => {
  it('accepts zero and positive amounts', () => {
    expect(validateCommissionPerFill('0')).toBeUndefined();
    expect(validateCommissionPerFill('1.5')).toBeUndefined();
  });

  it('rejects empty, negative, or malformed input', () => {
    expect(validateCommissionPerFill('')).toBe('Commission per fill is required.');
    expect(validateCommissionPerFill('-1')).toBe('Commission per fill cannot be negative.');
    expect(validateCommissionPerFill('abc')).toBe('Enter a valid amount, e.g. 0 or 1.5.');
  });
});

describe('validateSlippagePercent', () => {
  it('converts a valid percentage to its exact transport fraction', () => {
    expect(validateSlippagePercent('0')).toEqual({ fraction: '0', error: undefined });
    expect(validateSlippagePercent('0.05')).toEqual({ fraction: '0.0005', error: undefined });
    expect(validateSlippagePercent('1')).toEqual({ fraction: '0.01', error: undefined });
  });

  it('accepts an optional trailing % sign', () => {
    expect(validateSlippagePercent('0.05%')).toEqual({ fraction: '0.0005', error: undefined });
    expect(validateSlippagePercent('1%')).toEqual({ fraction: '0.01', error: undefined });
  });

  it('rejects empty, negative, or malformed input', () => {
    expect(validateSlippagePercent('')).toEqual({ fraction: undefined, error: 'Slippage is required.' });
    expect(validateSlippagePercent('-1')).toEqual({
      fraction: undefined,
      error: 'Enter a valid percentage, e.g. 0.05% or 1%.',
    });
    expect(validateSlippagePercent('abc')).toEqual({
      fraction: undefined,
      error: 'Enter a valid percentage, e.g. 0.05% or 1%.',
    });
  });
});

describe('validateDateRange', () => {
  it('accepts a valid range with start <= end', () => {
    expect(validateDateRange('2021-01-01', '2026-09-25')).toEqual({});
    expect(validateDateRange('2021-01-01', '2021-01-01')).toEqual({});
  });

  it('requires both dates', () => {
    expect(validateDateRange('', '2021-01-01')).toEqual({ startDate: 'Start date is required.' });
    expect(validateDateRange('2021-01-01', '')).toEqual({ endDate: 'End date is required.' });
  });

  it('rejects a shape that is not a real calendar date', () => {
    expect(validateDateRange('2024-02-30', '2024-03-01')).toEqual({ startDate: 'Enter a valid date (YYYY-MM-DD).' });
    expect(validateDateRange('not-a-date', '2024-03-01')).toEqual({ startDate: 'Enter a valid date (YYYY-MM-DD).' });
  });

  it('rejects start date after end date', () => {
    expect(validateDateRange('2026-01-01', '2021-01-01')).toEqual({
      endDate: 'End date must be on or after the start date.',
    });
  });
});

describe('validateBacktestConfigForm', () => {
  const VALID_FIELDS = {
    initialCapital: '100000',
    commissionPerFill: '1',
    slippagePercent: '0.05',
    startDate: '2021-01-01',
    endDate: '2026-09-25',
  };

  it('returns the exact BacktestConfigRequest shape when every field is valid', () => {
    const result = validateBacktestConfigForm(VALID_FIELDS);
    expect(result.fieldErrors).toEqual({});
    expect(result.config).toEqual({
      initialCapital: '100000',
      commissionPerFill: '1',
      slippageRate: '0.0005',
      startDate: '2021-01-01',
      endDate: '2026-09-25',
    });
  });

  it('never produces a config when any field is invalid', () => {
    const result = validateBacktestConfigForm({ ...VALID_FIELDS, initialCapital: '' });
    expect(result.config).toBeUndefined();
    expect(result.fieldErrors.initialCapital).toBe('Initial capital is required.');
  });

  it('collects every field error at once, not just the first', () => {
    const result = validateBacktestConfigForm({
      initialCapital: '',
      commissionPerFill: '-1',
      slippagePercent: '',
      startDate: '',
      endDate: '',
    });
    expect(Object.keys(result.fieldErrors).sort()).toEqual(
      ['initialCapital', 'commissionPerFill', 'slippage', 'startDate', 'endDate'].sort(),
    );
  });
});
