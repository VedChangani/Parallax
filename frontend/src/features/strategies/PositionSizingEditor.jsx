import { fractionToPercentText, percentTextToFraction, positionSizingError } from './definitionMapping.js';
import { inputClasses } from './formStyles.js';

export function PositionSizingEditor({ sizing, onChange }) {
  const error = positionSizingError(sizing);
  const percentText = fractionToPercentText(sizing.fraction);

  function handlePercentChange(text) {
    onChange({ ...sizing, fraction: percentTextToFraction(text) });
  }

  return (
    <div>
      <label htmlFor="position-sizing-percent" className="block text-sm font-medium text-ink">
        Use
      </label>
      <div className="mt-1 flex items-center gap-2">
        <input
          id="position-sizing-percent"
          type="text"
          inputMode="decimal"
          value={percentText}
          onChange={(event) => handlePercentChange(event.target.value)}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? 'position-sizing-error' : undefined}
          className={`${inputClasses} w-24 text-right tabular-nums`}
        />
        <span className="text-sm text-ink-secondary">% of available cash on entry</span>
      </div>
      {error ? (
        <p id="position-sizing-error" role="alert" className="mt-1 text-sm text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
