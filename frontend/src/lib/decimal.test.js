import { describe, expect, it } from 'vitest';
import { DECIMAL_PATTERN, isValidDecimalString } from './decimal.js';

describe('isValidDecimalString', () => {
  it.each(['0', '1', '-1', '0.5', '1.50', '-0.001', '100', '1e10', '1E-10', '-1.5e+3'])(
    'accepts %s as valid D-30 grammar',
    (value) => {
      expect(isValidDecimalString(value)).toBe(true);
    },
  );

  it.each(['01', '1.', '.5', '1e', '1e+', 'abc', '', '1,0', '1_000', 'Infinity', 'NaN', ' 1', '1 '])(
    'rejects %s as invalid D-30 grammar',
    (value) => {
      expect(isValidDecimalString(value)).toBe(false);
    },
  );

  it('rejects non-string values without coercing them', () => {
    expect(isValidDecimalString(1)).toBe(false);
    expect(isValidDecimalString(1.5)).toBe(false);
    expect(isValidDecimalString(null)).toBe(false);
    expect(isValidDecimalString(undefined)).toBe(false);
  });

  it('exposes the same pattern used for validation', () => {
    expect(DECIMAL_PATTERN.test('0.50')).toBe(true);
    expect(DECIMAL_PATTERN.test('00.50')).toBe(false);
  });
});
