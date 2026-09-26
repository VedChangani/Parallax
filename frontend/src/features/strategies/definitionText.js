import { fractionToPercentText } from './definitionMapping.js';

/**
 * Pure, human-readable rendering of a strategy definition tree. Every
 * function here accepts either a transport DTO node or a builder node (the
 * builder node is DTO-shaped plus an ignored `id` - see definitionMapping.js)
 * so the same rendering logic backs both the read-only version display and
 * the live builder preview. This is presentation only: it never evaluates a
 * condition or reimplements engine/strategy logic.
 */

const OPERATOR_SYMBOLS = { GT: '>', LT: '<' };

/** @param {object} operand */
export function describeOperand(operand) {
  switch (operand.type) {
    case 'indicator':
      return `${operand.indicator}(${operand.period})`;
    case 'close':
      return 'Close';
    case 'constant':
      return operand.value === '' || operand.value === undefined || operand.value === null ? '…' : operand.value;
    default:
      return '…';
  }
}

/**
 * A single-line rendering, e.g. "SMA(20) > SMA(50) AND RSI(14) < 70". A
 * nested group is wrapped in parentheses so operator precedence stays
 * unambiguous, e.g. "(SMA(20) > SMA(50)) OR RSI(14) < 70".
 *
 * @param {object} condition
 */
export function describeConditionOneLine(condition) {
  switch (condition.type) {
    case 'compare':
      return `${describeOperand(condition.left)} ${OPERATOR_SYMBOLS[condition.operator] ?? condition.operator} ${describeOperand(condition.right)}`;
    case 'all':
    case 'any': {
      const joiner = condition.type === 'all' ? ' AND ' : ' OR ';
      return condition.conditions
        .map((child) => (child.type === 'compare' ? describeConditionOneLine(child) : `(${describeConditionOneLine(child)})`))
        .join(joiner);
    }
    default:
      return '';
  }
}

/** @param {object} sizing */
export function describePositionSizing(sizing) {
  if (sizing.type !== 'cashFraction') return '';
  const percent = fractionToPercentText(sizing.fraction);
  return percent === '' ? '… % of available cash' : `${percent}% of available cash`;
}

/**
 * The short strategy summary shown on the list/detail pages (D-31's own
 * strategy-summary concept): the entry condition alone, one line.
 *
 * @param {import('../../api/types.js').StrategyDefinitionDto} definition
 */
export function summarizeStrategyDefinition(definition) {
  return describeConditionOneLine(definition.entryCondition);
}

/**
 * A multi-line block rendering of one condition tree - "AND"/"OR" on their
 * own line between siblings, nested groups indented - matching the layout
 * `describeConditionOneLine` collapses onto a single line.
 *
 * @param {object} condition
 * @returns {string[]} lines
 */
export function describeConditionLines(condition) {
  if (condition.type === 'compare') {
    return [`${describeOperand(condition.left)} ${OPERATOR_SYMBOLS[condition.operator] ?? condition.operator} ${describeOperand(condition.right)}`];
  }
  const joiner = condition.type === 'all' ? 'AND' : 'OR';
  const lines = [];
  condition.conditions.forEach((child, index) => {
    if (index > 0) lines.push(joiner);
    lines.push(...describeConditionLines(child));
  });
  return lines;
}
