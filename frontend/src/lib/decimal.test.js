import { describe, expect, it } from 'vitest';
import {
  DECIMAL_PATTERN,
  fractionTextToPercentText,
  isValidDecimalString,
  isValidPercentString,
  percentTextToFractionText,
} from './decimal.js';

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

describe('isValidPercentString', () => {
  it.each(['0', '1', '0.05', '100', '1.5', '0.0005'])('accepts %s', (value) => {
    expect(isValidPercentString(value)).toBe(true);
  });

  it.each(['-1', '-0.5', '01', '1.', '.5', 'abc', '', '1e5', '1,0'])('rejects %s', (value) => {
    expect(isValidPercentString(value)).toBe(false);
  });

  it('trims surrounding whitespace before validating', () => {
    expect(isValidPercentString(' 1 ')).toBe(true);
  });

  it('rejects non-string values without coercing them', () => {
    expect(isValidPercentString(1)).toBe(false);
    expect(isValidPercentString(null)).toBe(false);
    expect(isValidPercentString(undefined)).toBe(false);
  });
});

describe('percentTextToFractionText', () => {
  it.each([
    ['0', '0'],
    ['1', '0.01'],
    ['0.05', '0.0005'],
    ['100', '1'],
    ['0.0005', '0.000005'],
    ['5', '0.05'],
    ['12.5', '0.125'],
    ['0.5', '0.005'],
    ['50', '0.5'],
  ])('converts %s%% to fraction %s', (percent, fraction) => {
    expect(percentTextToFractionText(percent)).toBe(fraction);
  });

  it('produces no floating-point drift for values that are lossy in binary floating point', () => {
    // 0.1 * 0.01 in native floating-point arithmetic is 0.0010000000000000002,
    // not "0.001" - this must never happen here.
    expect(percentTextToFractionText('0.1')).toBe('0.001');
  });

  it('returns undefined for a negative or malformed percent string', () => {
    expect(percentTextToFractionText('-1')).toBeUndefined();
    expect(percentTextToFractionText('abc')).toBeUndefined();
    expect(percentTextToFractionText('')).toBeUndefined();
    expect(percentTextToFractionText('1e5')).toBeUndefined();
  });
});

describe('fractionTextToPercentText', () => {
  it.each([
    ['0', '0'],
    ['0.01', '1'],
    ['0.0005', '0.05'],
    ['1', '100'],
    ['0.000005', '0.0005'],
  ])('converts fraction %s to percent %s', (fraction, percent) => {
    expect(fractionTextToPercentText(fraction)).toBe(percent);
  });

  it('round-trips exactly through percentTextToFractionText', () => {
    for (const percent of ['0', '1', '0.05', '12.5', '0.0005', '100']) {
      expect(fractionTextToPercentText(percentTextToFractionText(percent))).toBe(percent);
    }
  });

  it('returns undefined for a negative or malformed fraction string', () => {
    expect(fractionTextToPercentText('-1')).toBeUndefined();
    expect(fractionTextToPercentText('abc')).toBeUndefined();
  });
});
