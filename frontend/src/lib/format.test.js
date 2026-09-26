import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, formatOrDash, formatPercent } from './format.js';

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
