import { isValidDecimalString } from '../../lib/decimal.js';

let nextIdValue = 0;

function nextId(prefix) {
  nextIdValue += 1;
  return `${prefix}-${nextIdValue}`;
}

export function newIndicatorOperand(indicator = 'SMA', period = '20') {
  return { id: nextId('operand'), type: 'indicator', indicator, period };
}

export function newCloseOperand() {
  return { id: nextId('operand'), type: 'close' };
}

export function newConstantOperand(value = '') {
  return { id: nextId('operand'), type: 'constant', value };
}

export function newCompareCondition() {
  return {
    id: nextId('condition'),
    type: 'compare',
    left: newIndicatorOperand('SMA', '20'),
    operator: 'GT',
    right: newIndicatorOperand('SMA', '50'),
  };
}

export function newGroupCondition(type) {
  return { id: nextId('condition'), type, conditions: [newCompareCondition()] };
}

export function newCashFractionPositionSizing(fraction = '1') {
  return { id: nextId('sizing'), type: 'cashFraction', fraction };
}

export function newDefinitionState() {
  return {
    entryCondition: newCompareCondition(),
    exitCondition: { ...newCompareCondition(), left: newIndicatorOperand('RSI', '14'), operator: 'LT', right: newConstantOperand('30') },
    positionSizing: newCashFractionPositionSizing('1'),
  };
}

export function operandToDto(node) {
  switch (node.type) {
    case 'indicator':
      return { type: 'indicator', indicator: node.indicator, period: Number.parseInt(node.period, 10) };
    case 'close':
      return { type: 'close' };
    case 'constant':
      return { type: 'constant', value: node.value };
    default:
      throw new Error(`unknown operand node type: ${node.type}`);
  }
}

export function conditionToDto(node) {
  switch (node.type) {
    case 'compare':
      return { type: 'compare', left: operandToDto(node.left), operator: node.operator, right: operandToDto(node.right) };
    case 'all':
      return { type: 'all', conditions: node.conditions.map(conditionToDto) };
    case 'any':
      return { type: 'any', conditions: node.conditions.map(conditionToDto) };
    default:
      throw new Error(`unknown condition node type: ${node.type}`);
  }
}

export function positionSizingToDto(node) {
  switch (node.type) {
    case 'cashFraction':
      return { type: 'cashFraction', fraction: node.fraction };
    default:
      throw new Error(`unknown position sizing node type: ${node.type}`);
  }
}

export function definitionToDto(state) {
  return {
    entryCondition: conditionToDto(state.entryCondition),
    exitCondition: conditionToDto(state.exitCondition),
    positionSizing: positionSizingToDto(state.positionSizing),
  };
}

export function dtoToOperand(dto) {
  switch (dto.type) {
    case 'indicator':
      return { id: nextId('operand'), type: 'indicator', indicator: dto.indicator, period: String(dto.period) };
    case 'close':
      return { id: nextId('operand'), type: 'close' };
    case 'constant':
      return { id: nextId('operand'), type: 'constant', value: dto.value };
    default:
      throw new Error(`unknown operand dto type: ${dto.type}`);
  }
}

export function dtoToCondition(dto) {
  switch (dto.type) {
    case 'compare':
      return {
        id: nextId('condition'),
        type: 'compare',
        left: dtoToOperand(dto.left),
        operator: dto.operator,
        right: dtoToOperand(dto.right),
      };
    case 'all':
      return { id: nextId('condition'), type: 'all', conditions: dto.conditions.map(dtoToCondition) };
    case 'any':
      return { id: nextId('condition'), type: 'any', conditions: dto.conditions.map(dtoToCondition) };
    default:
      throw new Error(`unknown condition dto type: ${dto.type}`);
  }
}

export function dtoToPositionSizing(dto) {
  switch (dto.type) {
    case 'cashFraction':
      return { id: nextId('sizing'), type: 'cashFraction', fraction: dto.fraction };
    default:
      throw new Error(`unknown position sizing dto type: ${dto.type}`);
  }
}

export function dtoToDefinitionState(dto) {
  return {
    entryCondition: dtoToCondition(dto.entryCondition),
    exitCondition: dtoToCondition(dto.exitCondition),
    positionSizing: dtoToPositionSizing(dto.positionSizing),
  };
}

export function operandError(operand) {
  if (operand.type === 'indicator') {
    const text = String(operand.period ?? '').trim();
    if (!/^[0-9]+$/.test(text)) return 'Period must be a whole number.';
    const period = Number.parseInt(text, 10);
    if (period < 1) return 'Period must be at least 1.';
    if (operand.indicator === 'RSI' && period < 2) return 'RSI period must be at least 2.';
    return undefined;
  }
  if (operand.type === 'constant') {
    if (!isValidDecimalString(operand.value)) return 'Enter a valid number, e.g. 70 or 0.5.';
    return undefined;
  }
  return undefined;
}

export function positionSizingError(sizing) {
  if (sizing.type === 'cashFraction') {
    if (!isValidDecimalString(sizing.fraction)) return 'Enter a valid percentage.';
    return undefined;
  }
  return undefined;
}

export function conditionHasErrors(condition) {
  if (condition.type === 'compare') {
    return Boolean(operandError(condition.left)) || Boolean(operandError(condition.right));
  }
  return condition.conditions.some(conditionHasErrors);
}

export function definitionHasErrors(state) {
  return (
    conditionHasErrors(state.entryCondition) ||
    conditionHasErrors(state.exitCondition) ||
    Boolean(positionSizingError(state.positionSizing))
  );
}

const PLAIN_DECIMAL = /^-?\d+(\.\d+)?$/;

function moveDecimalPoint(text, places) {
  const negative = text.startsWith('-');
  const unsigned = negative ? text.slice(1) : text;
  const dotIndex = unsigned.indexOf('.');
  let digits = dotIndex === -1 ? unsigned : unsigned.slice(0, dotIndex) + unsigned.slice(dotIndex + 1);
  let pointPos = (dotIndex === -1 ? unsigned.length : dotIndex) + places;

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

  const result = fracPart ? `${intPart}.${fracPart}` : intPart;
  return negative && result !== '0' ? `-${result}` : result;
}

function shiftDecimalText(text, places, floatFallback) {
  if (typeof text !== 'string' || text.trim() === '') return '';
  const trimmed = text.trim();
  if (PLAIN_DECIMAL.test(trimmed)) return moveDecimalPoint(trimmed, places);
  const num = floatFallback(Number(trimmed));
  return Number.isFinite(num) ? String(num) : '';
}

export function fractionToPercentText(fraction) {
  return shiftDecimalText(fraction, 2, (n) => n * 100);
}

export function percentTextToFraction(percentText) {
  return shiftDecimalText(percentText, -2, (n) => n / 100);
}

const EXTRA_WARMUP_CLOSE = 1;
const EXTRA_WARMUP_INDICATORS = new Set(['RSI', 'ROC']);

function operandLookbackBars(operand) {
  if (operand.type !== 'indicator') return 0;
  const period = Number(operand.period);
  if (!Number.isFinite(period) || period < 1) return 0;
  return EXTRA_WARMUP_INDICATORS.has(operand.indicator) ? period + EXTRA_WARMUP_CLOSE : period;
}

function conditionLookbackBars(condition) {
  if (condition.type === 'compare') {
    return Math.max(operandLookbackBars(condition.left), operandLookbackBars(condition.right));
  }
  return condition.conditions.reduce((max, child) => Math.max(max, conditionLookbackBars(child)), 0);
}

export function requiredLookbackBars(definition) {
  return Math.max(conditionLookbackBars(definition.entryCondition), conditionLookbackBars(definition.exitCondition));
}
