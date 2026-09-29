import { describe, expect, it } from 'vitest';
import { describeConditionOneLine, describeOperand, describePositionSizing, summarizeStrategyDefinition } from './definitionText.js';

const sma20 = { type: 'indicator', indicator: 'SMA', period: 20 };
const sma50 = { type: 'indicator', indicator: 'SMA', period: 50 };
const rsi14 = { type: 'indicator', indicator: 'RSI', period: 14 };
const const70 = { type: 'constant', value: '70' };

describe('describeOperand', () => {
  it('renders an indicator as TYPE(period)', () => {
    expect(describeOperand(sma20)).toBe('SMA(20)');
  });

  it('renders ATR and ROC as TYPE(period)', () => {
    expect(describeOperand({ type: 'indicator', indicator: 'ATR', period: 14 })).toBe('ATR(14)');
    expect(describeOperand({ type: 'indicator', indicator: 'ROC', period: 12 })).toBe('ROC(12)');
  });

  it('renders Close', () => {
    expect(describeOperand({ type: 'close' })).toBe('Close');
  });

  it('renders a constant as its exact text', () => {
    expect(describeOperand(const70)).toBe('70');
  });

  it('renders a placeholder for an empty constant', () => {
    expect(describeOperand({ type: 'constant', value: '' })).toBe('…');
  });
});

describe('describeConditionOneLine', () => {
  it('renders SMA(20) > SMA(50)', () => {
    const condition = { type: 'compare', left: sma20, operator: 'GT', right: sma50 };
    expect(describeConditionOneLine(condition)).toBe('SMA(20) > SMA(50)');
  });

  it('renders ATR(14) > 2 AND ROC(12) < -5', () => {
    const condition = {
      type: 'all',
      conditions: [
        { type: 'compare', left: { type: 'indicator', indicator: 'ATR', period: 14 }, operator: 'GT', right: { type: 'constant', value: '2' } },
        { type: 'compare', left: { type: 'indicator', indicator: 'ROC', period: 12 }, operator: 'LT', right: { type: 'constant', value: '-5' } },
      ],
    };
    expect(describeConditionOneLine(condition)).toBe('ATR(14) > 2 AND ROC(12) < -5');
  });

  it('renders an ALL group joined by AND', () => {
    const condition = {
      type: 'all',
      conditions: [
        { type: 'compare', left: sma20, operator: 'GT', right: sma50 },
        { type: 'compare', left: rsi14, operator: 'LT', right: const70 },
      ],
    };
    expect(describeConditionOneLine(condition)).toBe('SMA(20) > SMA(50) AND RSI(14) < 70');
  });

  it('renders an ANY group joined by OR', () => {
    const condition = {
      type: 'any',
      conditions: [
        { type: 'compare', left: sma20, operator: 'GT', right: sma50 },
        { type: 'compare', left: rsi14, operator: 'LT', right: const70 },
      ],
    };
    expect(describeConditionOneLine(condition)).toBe('SMA(20) > SMA(50) OR RSI(14) < 70');
  });

  it('wraps a nested group in parentheses', () => {
    const condition = {
      type: 'any',
      conditions: [
        { type: 'compare', left: sma20, operator: 'GT', right: sma50 },
        { type: 'all', conditions: [{ type: 'compare', left: rsi14, operator: 'LT', right: const70 }] },
      ],
    };
    expect(describeConditionOneLine(condition)).toBe('SMA(20) > SMA(50) OR (RSI(14) < 70)');
  });
});

describe('describePositionSizing', () => {
  it('renders 100% of available cash for fraction "1"', () => {
    expect(describePositionSizing({ type: 'cashFraction', fraction: '1' })).toBe('100% of available cash');
  });

  it('renders 50% of available cash for fraction "0.5"', () => {
    expect(describePositionSizing({ type: 'cashFraction', fraction: '0.5' })).toBe('50% of available cash');
  });
});

describe('summarizeStrategyDefinition', () => {
  it('summarizes the entry condition only', () => {
    const definition = {
      entryCondition: { type: 'compare', left: sma20, operator: 'GT', right: sma50 },
      exitCondition: { type: 'compare', left: rsi14, operator: 'LT', right: const70 },
      positionSizing: { type: 'cashFraction', fraction: '1' },
    };
    expect(summarizeStrategyDefinition(definition)).toBe('SMA(20) > SMA(50)');
  });
});
