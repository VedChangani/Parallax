export const DECIMAL_PATTERN = /^-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?$/;

export function isValidDecimalString(value) {
  return typeof value === 'string' && DECIMAL_PATTERN.test(value);
}

const PLAIN_NONNEGATIVE_DECIMAL = /^(0|[1-9][0-9]*)(\.[0-9]+)?$/;

export function isValidPercentString(value) {
  return typeof value === 'string' && PLAIN_NONNEGATIVE_DECIMAL.test(value.trim());
}

function shiftDecimalPoint(text, places) {
  const dotIndex = text.indexOf('.');
  let digits = dotIndex === -1 ? text : text.slice(0, dotIndex) + text.slice(dotIndex + 1);
  let pointPos = (dotIndex === -1 ? text.length : dotIndex) + places;

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

  return fracPart ? `${intPart}.${fracPart}` : intPart;
}

export function percentTextToFractionText(percentText) {
  if (!isValidPercentString(percentText)) return undefined;
  return shiftDecimalPoint(percentText.trim(), -2);
}

export function fractionTextToPercentText(fractionText) {
  if (!isValidPercentString(fractionText)) return undefined;
  return shiftDecimalPoint(fractionText.trim(), 2);
}
