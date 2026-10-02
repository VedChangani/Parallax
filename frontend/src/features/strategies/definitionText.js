import { fractionToPercentText } from './definitionMapping.js';

const OPERATOR_SYMBOLS = { GT: '>', LT: '<' };

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

export function describePositionSizing(sizing) {
  if (sizing.type !== 'cashFraction') return '';
  const percent = fractionToPercentText(sizing.fraction);
  return percent === '' ? '… % of available cash' : `${percent}% of available cash`;
}

export function summarizeStrategyDefinition(definition) {
  return describeConditionOneLine(definition.entryCondition);
}

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
