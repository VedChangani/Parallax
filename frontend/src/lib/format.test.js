import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, formatOrDash, formatPercent, formatRatio, formatSignedPercent, formatStatMoney } from './format.js';

describe('formatMoney', () => {
  it('renders the exact decimal string unchanged', () => {
    expect(formatMoney('1000.00')).toBe('1000.00');
    expect(formatMoney('-4.90')).toBe('-4.90');
    expect(formatMoney('0')).toBe('0');
  });

  it('renders a missing value as a dash', () => {
    expect(formatMoney(null)).toBe('—');
    expect(formatMoney(undefined)).toBe('—');
  });
});

describe('formatPercent', () => {
  it('multiplies a ratio by 100 and formats with a fixed precision', () => {
    expect(formatPercent(0.1532)).toBe('15.32%');
    expect(formatPercent(-0.05, 1)).toBe('-5.0%');
    expect(formatPercent(0)).toBe('0.00%');
  });

  it('renders a missing value as a dash', () => {
    expect(formatPercent(null)).toBe('—');
    expect(formatPercent(undefined)).toBe('—');
  });
});

describe('formatDate', () => {
  it('passes an ISO date string through unchanged', () => {
    expect(formatDate('2024-01-15')).toBe('2024-01-15');
  });

  it('renders a missing value as a dash', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate(undefined)).toBe('—');
  });
});

describe('formatSignedPercent', () => {
  it('prefixes a positive ratio with a "+"', () => {
    expect(formatSignedPercent(0.1532)).toBe('+15.32%');
  });

  it('leaves a negative ratio with its own "-"', () => {
    expect(formatSignedPercent(-0.05, 1)).toBe('-5.0%');
  });

  it('adds no sign for exactly zero', () => {
    expect(formatSignedPercent(0)).toBe('0.00%');
  });

  it('renders a missing value as a dash', () => {
    expect(formatSignedPercent(null)).toBe('—');
    expect(formatSignedPercent(undefined)).toBe('—');
  });
});

describe('formatRatio', () => {
  it('formats a plain ratio with fixed precision', () => {
    expect(formatRatio(1.4213)).toBe('1.42');
    expect(formatRatio(-0.3, 1)).toBe('-0.3');
  });

  it('renders a missing value as a dash', () => {
    expect(formatRatio(null)).toBe('—');
    expect(formatRatio(undefined)).toBe('—');
  });
});

describe('formatStatMoney', () => {
  it('formats an engine-derived double statistic with fixed precision', () => {
    expect(formatStatMoney(123.4)).toBe('123.40');
    expect(formatStatMoney(-45.678, 1)).toBe('-45.7');
  });

  it('renders a missing value as a dash', () => {
    expect(formatStatMoney(null)).toBe('—');
    expect(formatStatMoney(undefined)).toBe('—');
  });
});

describe('formatOrDash', () => {
  it('renders null, undefined, and empty string as a dash', () => {
    expect(formatOrDash(null)).toBe('—');
    expect(formatOrDash(undefined)).toBe('—');
    expect(formatOrDash('')).toBe('—');
  });

  it('stringifies any other value', () => {
    expect(formatOrDash(42)).toBe('42');
    expect(formatOrDash(0)).toBe('0');
  });
});
