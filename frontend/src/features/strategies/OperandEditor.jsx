import { newCloseOperand, newConstantOperand, newIndicatorOperand, operandError } from './definitionMapping.js';
import { inputClasses, selectClasses } from './formStyles.js';

const INDICATORS = ['SMA', 'EMA', 'RSI'];

/**
 * A reusable editor for one {@link import('./definitionMapping.js').operandToDto}
 * operand node - Indicator (type + period), Close (no fields), or Constant
 * (a decimal-string text field, preserved exactly - never `Number()`'d).
 *
 * @param {object} props
 * @param {object} props.operand
 * @param {(next: object) => void} props.onChange
 * @param {string} props.label - accessible name prefix, e.g. "Left operand"
 * @param {string} props.idPrefix - unique DOM id prefix for this operand's controls
 */
export function OperandEditor({ operand, onChange, label, idPrefix }) {
  const error = operandError(operand);
  const errorId = `${idPrefix}-error`;

  function handleTypeChange(type) {
    if (type === operand.type) return;
    if (type === 'indicator') onChange(newIndicatorOperand());
    else if (type === 'close') onChange(newCloseOperand());
    else onChange(newConstantOperand());
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <label htmlFor={`${idPrefix}-type`} className="sr-only">
        {label} type
      </label>
      <select
        id={`${idPrefix}-type`}
        value={operand.type}
        onChange={(event) => handleTypeChange(event.target.value)}
        className={selectClasses}
      >
        <option value="indicator">Indicator</option>
        <option value="close">Close</option>
        <option value="constant">Constant</option>
      </select>

      {operand.type === 'indicator' ? (
        <>
          <label htmlFor={`${idPrefix}-indicator`} className="sr-only">
            {label} indicator
          </label>
          <select
            id={`${idPrefix}-indicator`}
            value={operand.indicator}
            onChange={(event) => onChange({ ...operand, indicator: event.target.value })}
            className={selectClasses}
          >
            {INDICATORS.map((indicator) => (
              <option key={indicator} value={indicator}>
                {indicator}
              </option>
            ))}
          </select>
          <label htmlFor={`${idPrefix}-period`} className="sr-only">
            {label} period
          </label>
          <input
            id={`${idPrefix}-period`}
            type="text"
            inputMode="numeric"
            value={operand.period}
            onChange={(event) => onChange({ ...operand, period: event.target.value })}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? errorId : undefined}
            className={`${inputClasses} w-14 text-center tabular-nums`}
          />
        </>
      ) : null}

      {operand.type === 'constant' ? (
        <>
          <label htmlFor={`${idPrefix}-value`} className="sr-only">
            {label} value
          </label>
          <input
            id={`${idPrefix}-value`}
            type="text"
            inputMode="decimal"
            value={operand.value}
            onChange={(event) => onChange({ ...operand, value: event.target.value })}
            placeholder="70"
            aria-invalid={Boolean(error)}
            aria-describedby={error ? errorId : undefined}
            className={`${inputClasses} w-20 tabular-nums`}
          />
        </>
      ) : null}

      {error ? (
        <p id={errorId} role="alert" className="basis-full text-xs text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
