import { describe, expect, it } from 'vitest';
import {
  conditionHasErrors,
  conditionToDto,
  definitionHasErrors,
  definitionToDto,
  dtoToCondition,
  dtoToDefinitionState,
  dtoToOperand,
  dtoToPositionSizing,
  fractionToPercentText,
  newCashFractionPositionSizing,
  newCloseOperand,
  newCompareCondition,
  newConstantOperand,
  newGroupCondition,
  newIndicatorOperand,
  operandError,
  operandToDto,
  percentTextToFraction,
  positionSizingError,
  positionSizingToDto,
  requiredLookbackBars,
} from './definitionMapping.js';

describe('operand serialization', () => {
  it('serializes an indicator operand, parsing period to an integer', () => {
    expect(operandToDto(newIndicatorOperand('SMA', '20'))).toEqual({ type: 'indicator', indicator: 'SMA', period: 20 });
  });

  it('serializes a close operand with no extra fields', () => {
    expect(operandToDto(newCloseOperand())).toEqual({ type: 'close' });
  });

  it('serializes a constant operand, preserving the exact string - never Number()', () => {
    expect(operandToDto(newConstantOperand('70.50'))).toEqual({ type: 'constant', value: '70.50' });
  });

  it('round-trips an indicator operand through dtoToOperand/operandToDto', () => {
    const dto = { type: 'indicator', indicator: 'RSI', period: 14 };
    expect(operandToDto(dtoToOperand(dto))).toEqual(dto);
  });

  it('round-trips a constant operand without normalizing the decimal text', () => {
    const dto = { type: 'constant', value: '0.500' };
    expect(operandToDto(dtoToOperand(dto))).toEqual(dto);
  });
});

describe('condition serialization', () => {
  it('serializes SMA(20) > SMA(50)', () => {
    const condition = newCompareCondition();
    expect(conditionToDto(condition)).toEqual({
      type: 'compare',
      left: { type: 'indicator', indicator: 'SMA', period: 20 },
      operator: 'GT',
      right: { type: 'indicator', indicator: 'SMA', period: 50 },
    });
  });

  it('serializes an ALL group of SMA(20) > SMA(50) AND RSI(14) < 70', () => {
    const group = newGroupCondition('all');
    group.conditions = [
      newCompareCondition(),
      { ...newCompareCondition(), left: newIndicatorOperand('RSI', '14'), operator: 'LT', right: newConstantOperand('70') },
    ];
    expect(conditionToDto(group)).toEqual({
      type: 'all',
      conditions: [
        { type: 'compare', left: { type: 'indicator', indicator: 'SMA', period: 20 }, operator: 'GT', right: { type: 'indicator', indicator: 'SMA', period: 50 } },
        { type: 'compare', left: { type: 'indicator', indicator: 'RSI', period: 14 }, operator: 'LT', right: { type: 'constant', value: '70' } },
      ],
    });
  });

  it('serializes a Close comparison', () => {
    const condition = { ...newCompareCondition(), left: newCloseOperand(), right: newConstantOperand('100') };
    expect(conditionToDto(condition)).toEqual({
      type: 'compare',
      left: { type: 'close' },
      operator: 'GT',
      right: { type: 'constant', value: '100' },
    });
  });

  it('serializes nested ANY containing an ALL group', () => {
    const inner = newGroupCondition('all');
    const outer = newGroupCondition('any');
    outer.conditions = [newCompareCondition(), inner];

    const dto = conditionToDto(outer);
    expect(dto.type).toBe('any');
    expect(dto.conditions).toHaveLength(2);
    expect(dto.conditions[0].type).toBe('compare');
    expect(dto.conditions[1].type).toBe('all');
    expect(dto.conditions[1].conditions).toHaveLength(1);
  });

  it('round-trips a nested ALL/ANY structure through dtoToCondition/conditionToDto', () => {
    const dto = {
      type: 'any',
      conditions: [
        { type: 'compare', left: { type: 'indicator', indicator: 'SMA', period: 20 }, operator: 'GT', right: { type: 'indicator', indicator: 'SMA', period: 50 } },
        {
          type: 'all',
          conditions: [
            { type: 'compare', left: { type: 'indicator', indicator: 'RSI', period: 14 }, operator: 'LT', right: { type: 'constant', value: '30' } },
            { type: 'compare', left: { type: 'close' }, operator: 'GT', right: { type: 'constant', value: '100' } },
          ],
        },
      ],
    };
    expect(conditionToDto(dtoToCondition(dto))).toEqual(dto);
  });
});

describe('position sizing serialization', () => {
  it('serializes cash fraction, preserving the exact fraction string', () => {
    expect(positionSizingToDto(newCashFractionPositionSizing('0.5'))).toEqual({ type: 'cashFraction', fraction: '0.5' });
  });

  it('round-trips cash fraction without normalizing the decimal text', () => {
    const dto = { type: 'cashFraction', fraction: '0.500' };
    expect(positionSizingToDto(dtoToPositionSizing(dto))).toEqual(dto);
  });
});

describe('definitionToDto / dtoToDefinitionState round-trip', () => {
  it('round-trips a full definition (SMA(20) > SMA(50) AND RSI(14) < 70)', () => {
    const dto = {
      entryCondition: {
        type: 'all',
        conditions: [
          { type: 'compare', left: { type: 'indicator', indicator: 'SMA', period: 20 }, operator: 'GT', right: { type: 'indicator', indicator: 'SMA', period: 50 } },
          { type: 'compare', left: { type: 'indicator', indicator: 'RSI', period: 14 }, operator: 'LT', right: { type: 'constant', value: '70' } },
        ],
      },
      exitCondition: { type: 'compare', left: { type: 'indicator', indicator: 'RSI', period: 14 }, operator: 'GT', right: { type: 'constant', value: '70' } },
      positionSizing: { type: 'cashFraction', fraction: '1' },
    };
    expect(definitionToDto(dtoToDefinitionState(dto))).toEqual(dto);
  });
});

describe('operandError', () => {
  it('accepts a valid indicator period', () => {
    expect(operandError(newIndicatorOperand('SMA', '20'))).toBeUndefined();
  });

  it('rejects a non-integer period', () => {
    expect(operandError(newIndicatorOperand('SMA', '20.5'))).toBeTruthy();
  });

  it('rejects a period below 1', () => {
    expect(operandError(newIndicatorOperand('SMA', '0'))).toBeTruthy();
  });

  it('rejects an RSI period below 2, but allows SMA/EMA at 1', () => {
    expect(operandError(newIndicatorOperand('RSI', '1'))).toBeTruthy();
    expect(operandError(newIndicatorOperand('SMA', '1'))).toBeUndefined();
    expect(operandError(newIndicatorOperand('EMA', '1'))).toBeUndefined();
  });

  it('allows ATR and ROC at period 1 and rejects them below 1', () => {
    expect(operandError(newIndicatorOperand('ATR', '1'))).toBeUndefined();
    expect(operandError(newIndicatorOperand('ROC', '1'))).toBeUndefined();
    expect(operandError(newIndicatorOperand('ATR', '0'))).toBeTruthy();
    expect(operandError(newIndicatorOperand('ROC', '0'))).toBeTruthy();
    expect(operandError(newIndicatorOperand('ATR', '14.5'))).toBeTruthy();
    expect(operandError(newIndicatorOperand('ROC', ''))).toBeTruthy();
  });

  it('serializes ATR and ROC operands with a numeric period', () => {
    expect(operandToDto(newIndicatorOperand('ATR', '14'))).toEqual({ type: 'indicator', indicator: 'ATR', period: 14 });
    expect(operandToDto(newIndicatorOperand('ROC', '12'))).toEqual({ type: 'indicator', indicator: 'ROC', period: 12 });
  });

  it('accepts a well-formed constant and rejects a malformed one', () => {
    expect(operandError(newConstantOperand('70.5'))).toBeUndefined();
    expect(operandError(newConstantOperand('abc'))).toBeTruthy();
    expect(operandError(newConstantOperand(''))).toBeTruthy();
  });

  it('never flags a Close operand', () => {
    expect(operandError(newCloseOperand())).toBeUndefined();
  });
});

describe('positionSizingError', () => {
  it('accepts a valid decimal fraction and rejects a malformed one', () => {
    expect(positionSizingError(newCashFractionPositionSizing('0.5'))).toBeUndefined();
    expect(positionSizingError(newCashFractionPositionSizing('abc'))).toBeTruthy();
    expect(positionSizingError(newCashFractionPositionSizing(''))).toBeTruthy();
  });
});

describe('conditionHasErrors / definitionHasErrors', () => {
  it('detects an invalid operand nested inside a group', () => {
    const group = newGroupCondition('all');
    group.conditions = [newCompareCondition(), { ...newCompareCondition(), left: newIndicatorOperand('RSI', '1') }];
    expect(conditionHasErrors(group)).toBe(true);
  });

  it('reports no errors for a well-formed definition', () => {
    const state = {
      entryCondition: newCompareCondition(),
      exitCondition: newCompareCondition(),
      positionSizing: newCashFractionPositionSizing('1'),
    };
    expect(definitionHasErrors(state)).toBe(false);
  });

  it('reports an error when position sizing is malformed', () => {
    const state = {
      entryCondition: newCompareCondition(),
      exitCondition: newCompareCondition(),
      positionSizing: newCashFractionPositionSizing('not-a-number'),
    };
    expect(definitionHasErrors(state)).toBe(true);
  });
});

describe('fraction <-> percent display', () => {
  it('converts 100% display <-> "1" transport exactly', () => {
    expect(fractionToPercentText('1')).toBe('100');
    expect(percentTextToFraction('100')).toBe('1');
  });

  it('converts 50% display <-> "0.5" transport exactly', () => {
    expect(fractionToPercentText('0.5')).toBe('50');
    expect(percentTextToFraction('50')).toBe('0.5');
  });

  it('converts a small fraction without floating-point artifacts', () => {
    expect(fractionToPercentText('0.1')).toBe('10');
    expect(percentTextToFraction('10')).toBe('0.1');
    expect(percentTextToFraction('5')).toBe('0.05');
  });

  it('returns an empty string for blank input rather than throwing', () => {
    expect(fractionToPercentText('')).toBe('');
    expect(percentTextToFraction('')).toBe('');
  });
});

describe('requiredLookbackBars (I-3)', () => {
  const indicatorRef = (indicator, period) => ({ type: 'indicator', indicator, period });
  const definitionWith = (entryCondition, exitCondition) => ({
    entryCondition,
    exitCondition,
    positionSizing: { type: 'cashFraction', fraction: '1' },
  });

  it('is 0 for a Close-only definition with no indicator at all', () => {
    const definition = definitionWith(
      { type: 'compare', left: { type: 'close' }, operator: 'GT', right: { type: 'constant', value: '0' } },
      { type: 'compare', left: { type: 'close' }, operator: 'LT', right: { type: 'constant', value: '0' } },
    );
    expect(requiredLookbackBars(definition)).toBe(0);
  });

  it('is the SMA/EMA period exactly - ready after exactly that many closes', () => {
    const definition = definitionWith(
      { type: 'compare', left: indicatorRef('SMA', 50), operator: 'GT', right: indicatorRef('SMA', 20) },
      { type: 'compare', left: { type: 'close' }, operator: 'LT', right: { type: 'constant', value: '0' } },
    );
    expect(requiredLookbackBars(definition)).toBe(50);
  });

  it('is the RSI period plus one - RSI needs one extra close to seed the first price change', () => {
    const definition = definitionWith(
      { type: 'compare', left: indicatorRef('RSI', 14), operator: 'LT', right: { type: 'constant', value: '30' } },
      { type: 'compare', left: { type: 'close' }, operator: 'LT', right: { type: 'constant', value: '0' } },
    );
    expect(requiredLookbackBars(definition)).toBe(15);
  });

  it('is the ATR period exactly - one true range per bar, so ready after that many bars', () => {
    const definition = definitionWith(
      { type: 'compare', left: indicatorRef('ATR', 14), operator: 'GT', right: { type: 'constant', value: '2' } },
      { type: 'compare', left: { type: 'close' }, operator: 'LT', right: { type: 'constant', value: '0' } },
    );
    expect(requiredLookbackBars(definition)).toBe(14);
  });

  it('is the ROC period plus one - ROC needs the close `period` bars ago', () => {
    const definition = definitionWith(
      { type: 'compare', left: indicatorRef('ROC', 12), operator: 'GT', right: { type: 'constant', value: '0' } },
      { type: 'compare', left: { type: 'close' }, operator: 'LT', right: { type: 'constant', value: '0' } },
    );
    expect(requiredLookbackBars(definition)).toBe(13);
  });

  it('takes the maximum across every indicator in both conditions, including nested groups', () => {
    const definition = definitionWith(
      {
        type: 'all',
        conditions: [
          { type: 'compare', left: indicatorRef('SMA', 20), operator: 'GT', right: indicatorRef('SMA', 50) },
          { type: 'compare', left: indicatorRef('RSI', 14), operator: 'LT', right: { type: 'constant', value: '70' } },
        ],
      },
      { type: 'compare', left: indicatorRef('EMA', 200), operator: 'LT', right: { type: 'close' } },
    );
    expect(requiredLookbackBars(definition)).toBe(200);
  });

  it('accepts a builder node (string period) exactly like a DTO (numeric period)', () => {
    const definition = definitionWith(
      { type: 'compare', left: indicatorRef('SMA', '30'), operator: 'GT', right: { type: 'close' } },
      { type: 'compare', left: { type: 'close' }, operator: 'LT', right: { type: 'constant', value: '0' } },
    );
    expect(requiredLookbackBars(definition)).toBe(30);
  });
});
