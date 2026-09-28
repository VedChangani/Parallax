import { isValidDecimalString } from '../../lib/decimal.js';

/**
 * The pure mapping layer between the Strategy Builder's editable UI state
 * (a "builder node" tree) and the backend's transport
 * {@link import('../../api/types.js').StrategyDefinitionDto} tree (D-30/D-31).
 *
 * A builder node is *exactly* the corresponding DTO shape (same `type`
 * discriminator values: `'compare' | 'all' | 'any'`, `'indicator' | 'close' |
 * 'constant'`, `'cashFraction'`, same field names) plus one addition: a
 * stable `id` used only for React keys/editing, stripped by every `*ToDto`
 * function and never sent to the backend. This is deliberate — it is not a
 * second, incompatible strategy model, just the DTO tree with an editing
 * handle attached.
 *
 * The one structural difference is `Operand.Indicator.period`: the DTO
 * carries it as a JSON integer, but the builder keeps it as the exact text
 * the user typed (a string) so a partially-edited value (e.g. an empty
 * field, or "07") never gets silently coerced mid-edit. It is parsed with
 * `Number.parseInt` only at `operandToDto` time.
 */

let nextIdValue = 0;

/** @param {string} prefix */
function nextId(prefix) {
  nextIdValue += 1;
  return `${prefix}-${nextIdValue}`;
}

// --- node factories --------------------------------------------------------

/**
 * @param {import('../../api/types.js').IndicatorTypeDto} [indicator]
 * @param {string} [period]
 */
export function newIndicatorOperand(indicator = 'SMA', period = '20') {
  return { id: nextId('operand'), type: 'indicator', indicator, period };
}

export function newCloseOperand() {
  return { id: nextId('operand'), type: 'close' };
}

/** @param {string} [value] */
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

/** @param {'all' | 'any'} type */
export function newGroupCondition(type) {
  return { id: nextId('condition'), type, conditions: [newCompareCondition()] };
}

/** @param {string} [fraction] */
export function newCashFractionPositionSizing(fraction = '1') {
  return { id: nextId('sizing'), type: 'cashFraction', fraction };
}

/** A fresh, fully-editable starting definition for "New strategy". */
export function newDefinitionState() {
  return {
    entryCondition: newCompareCondition(),
    exitCondition: { ...newCompareCondition(), left: newIndicatorOperand('RSI', '14'), operator: 'LT', right: newConstantOperand('30') },
    positionSizing: newCashFractionPositionSizing('1'),
  };
}

// --- builder node -> DTO -----------------------------------------------------

/** @param {object} node @returns {import('../../api/types.js').OperandDto} */
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

/** @param {object} node @returns {import('../../api/types.js').ConditionDto} */
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

/** @param {object} node @returns {import('../../api/types.js').PositionSizingDto} */
export function positionSizingToDto(node) {
  switch (node.type) {
    case 'cashFraction':
      return { type: 'cashFraction', fraction: node.fraction };
    default:
      throw new Error(`unknown position sizing node type: ${node.type}`);
  }
}

/**
 * @param {{entryCondition: object, exitCondition: object, positionSizing: object}} state
 * @returns {import('../../api/types.js').StrategyDefinitionDto}
 */
export function definitionToDto(state) {
  return {
    entryCondition: conditionToDto(state.entryCondition),
    exitCondition: conditionToDto(state.exitCondition),
    positionSizing: positionSizingToDto(state.positionSizing),
  };
}

// --- DTO -> builder node -----------------------------------------------------

/** @param {import('../../api/types.js').OperandDto} dto */
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

/** @param {import('../../api/types.js').ConditionDto} dto */
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

/** @param {import('../../api/types.js').PositionSizingDto} dto */
export function dtoToPositionSizing(dto) {
  switch (dto.type) {
    case 'cashFraction':
      return { id: nextId('sizing'), type: 'cashFraction', fraction: dto.fraction };
    default:
      throw new Error(`unknown position sizing dto type: ${dto.type}`);
  }
}

/** @param {import('../../api/types.js').StrategyDefinitionDto} dto */
export function dtoToDefinitionState(dto) {
  return {
    entryCondition: dtoToCondition(dto.entryCondition),
    exitCondition: dtoToCondition(dto.exitCondition),
    positionSizing: dtoToPositionSizing(dto.positionSizing),
  };
}

// --- client-side validation --------------------------------------------------
//
// Deliberately minimal (D-31/D-34's own convention): only checks the engine
// has no vocabulary for elsewhere (decimal syntax) and the one bound that is
// safe to mirror client-side (indicator period). Every other semantic rule
// (CashFraction > 0 and <= 1, Constant underflow, non-empty groups already
// enforced structurally by the editor UI) is left to the backend.

/** @param {object} operand @returns {string | undefined} */
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

/** @param {object} sizing @returns {string | undefined} */
export function positionSizingError(sizing) {
  if (sizing.type === 'cashFraction') {
    if (!isValidDecimalString(sizing.fraction)) return 'Enter a valid percentage.';
    return undefined;
  }
  return undefined;
}

/** @param {object} condition @returns {boolean} whether any operand beneath `condition` fails {@link operandError} */
export function conditionHasErrors(condition) {
  if (condition.type === 'compare') {
    return Boolean(operandError(condition.left)) || Boolean(operandError(condition.right));
  }
  return condition.conditions.some(conditionHasErrors);
}

/**
 * @param {{entryCondition: object, exitCondition: object, positionSizing: object}} state
 * @returns {boolean}
 */
export function definitionHasErrors(state) {
  return (
    conditionHasErrors(state.entryCondition) ||
    conditionHasErrors(state.exitCondition) ||
    Boolean(positionSizingError(state.positionSizing))
  );
}

// --- cash fraction <-> percent display --------------------------------------
//
// The builder's position-sizing field is edited as a percentage, but the
// underlying node state always stores the exact transport fraction string
// (D-30 §12/§24) - the percent shown is derived from it for display, and a
// keystroke converts back to a fraction immediately, so the node tree stays
// exactly DTO-shaped at every instant, not just at submission time.

const PLAIN_DECIMAL = /^-?\d+(\.\d+)?$/;

/**
 * Shifts a plain (non-exponent) decimal string's point `places` positions to
 * the right (negative `places` shifts left), as exact string/digit
 * manipulation - never a floating-point multiply/divide, so "1" -> "100"
 * and "100" -> "1" round-trip exactly rather than via `1 * 100` /
 * `100 / 100` floating-point arithmetic.
 *
 * @param {string} text
 * @param {number} places
 */
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

/**
 * @param {string} text
 * @param {number} places
 * @param {(value: number) => number} floatFallback
 */
function shiftDecimalText(text, places, floatFallback) {
  if (typeof text !== 'string' || text.trim() === '') return '';
  const trimmed = text.trim();
  if (PLAIN_DECIMAL.test(trimmed)) return moveDecimalPoint(trimmed, places);
  const num = floatFallback(Number(trimmed));
  return Number.isFinite(num) ? String(num) : '';
}

/** @param {string} fraction - the exact transport fraction string @returns {string} a percent string for display */
export function fractionToPercentText(fraction) {
  return shiftDecimalText(fraction, 2, (n) => n * 100);
}

/** @param {string} percentText - the percent text the user typed @returns {string} the equivalent transport fraction string */
export function percentTextToFraction(percentText) {
  return shiftDecimalText(percentText, -2, (n) => n / 100);
}

// --- indicator warm-up lookback (Phase 10 Batch 4, I-3) ---------------------
//
// A presentation-only mirror of the engine's own warm-up rule (D-8;
// architecture.md §8: SMA(n)/EMA(n) ready after n closes, RSI(n) after n+1).
// Used only to warn the New Backtest form when a strategy's own indicators
// would have no lookback bars to warm up on - never to compute an actual
// date, never sent to the backend, and never a substitute for the engine's
// own firstEvaluableDate.

const RSI_EXTRA_WARMUP_CLOSE = 1;

/** @param {object} operand - an OperandDto or builder node @returns {number} */
function operandLookbackBars(operand) {
  if (operand.type !== 'indicator') return 0;
  const period = Number(operand.period);
  if (!Number.isFinite(period) || period < 1) return 0;
  return operand.indicator === 'RSI' ? period + RSI_EXTRA_WARMUP_CLOSE : period;
}

/** @param {object} condition - a ConditionDto or builder node @returns {number} */
function conditionLookbackBars(condition) {
  if (condition.type === 'compare') {
    return Math.max(operandLookbackBars(condition.left), operandLookbackBars(condition.right));
  }
  return condition.conditions.reduce((max, child) => Math.max(max, conditionLookbackBars(child)), 0);
}

/**
 * The largest number of prior closes any indicator referenced by
 * `definition`'s entry/exit conditions needs before it becomes ready. `0`
 * when the strategy references no indicator at all (e.g. Close-only
 * conditions).
 *
 * @param {import('../../api/types.js').StrategyDefinitionDto} definition
 * @returns {number}
 */
export function requiredLookbackBars(definition) {
  return Math.max(conditionLookbackBars(definition.entryCondition), conditionLookbackBars(definition.exitCondition));
}
