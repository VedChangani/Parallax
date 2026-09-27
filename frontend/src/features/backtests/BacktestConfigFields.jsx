import { FieldError } from '../../components/FieldError.jsx';
import { inputClasses } from '../strategies/formStyles.js';

/**
 * The "Configuration" section of the New Backtest form (D-34 Batch 4):
 * initialCapital/commissionPerFill/slippageRate/startDate/endDate, the exact
 * `BacktestConfig` fields. Every monetary/rate value stays a raw string the
 * whole way from input to transport (see backtestConfigValidation.js) -
 * this component never parses or formats them as numbers. `coverage` is
 * purely informational (the selected snapshot's own date range) and is
 * never used to clamp or silently rewrite an entered date - CLAUDE.md
 * ("do not silently change entered dates").
 *
 * @param {object} props
 * @param {string} props.initialCapital
 * @param {(value: string) => void} props.onInitialCapitalChange
 * @param {string} props.commissionPerFill
 * @param {(value: string) => void} props.onCommissionPerFillChange
 * @param {string} props.slippagePercent
 * @param {(value: string) => void} props.onSlippagePercentChange
 * @param {string} props.startDate
 * @param {(value: string) => void} props.onStartDateChange
 * @param {string} props.endDate
 * @param {(value: string) => void} props.onEndDateChange
 * @param {{initialCapital?: string, commissionPerFill?: string, slippage?: string, startDate?: string, endDate?: string}} props.fieldErrors
 * @param {{firstDate: string, lastDate: string} | undefined} props.coverage
 */
export function BacktestConfigFields({
  initialCapital,
  onInitialCapitalChange,
  commissionPerFill,
  onCommissionPerFillChange,
  slippagePercent,
  onSlippagePercentChange,
  startDate,
  onStartDateChange,
  endDate,
  onEndDateChange,
  fieldErrors,
  coverage,
}) {
  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          id="backtest-initial-capital"
          label="Initial capital"
          value={initialCapital}
          onChange={onInitialCapitalChange}
          placeholder="100000"
          error={fieldErrors.initialCapital}
        />
        <Field
          id="backtest-commission"
          label="Commission per fill"
          value={commissionPerFill}
          onChange={onCommissionPerFillChange}
          placeholder="0"
          error={fieldErrors.commissionPerFill}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          id="backtest-slippage"
          label="Slippage"
          value={slippagePercent}
          onChange={onSlippagePercentChange}
          placeholder="0.05%"
          error={fieldErrors.slippage}
          hint="Percentage of the reference price applied on every fill."
        />
        <div aria-hidden="true" />
      </div>

      {coverage ? (
        <p className="text-xs text-ink-muted">
          Available data:{' '}
          <span className="font-medium text-ink-secondary">
            {coverage.firstDate} → {coverage.lastDate}
          </span>
        </p>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          id="backtest-start-date"
          label="Start date"
          type="date"
          value={startDate}
          onChange={onStartDateChange}
          error={fieldErrors.startDate}
        />
        <Field
          id="backtest-end-date"
          label="End date"
          type="date"
          value={endDate}
          onChange={onEndDateChange}
          error={fieldErrors.endDate}
        />
      </div>

      {/* C1 (Phase 9 Batch 1): a factual note only - this never changes the
          engine's start/lookback semantics (D-6) or silently rewrites either
          date; it just explains why a run's evaluable range can start later
          than the date entered above. */}
      <p className="text-xs text-ink-muted">
        Indicators may need bars before the start date to warm up. Those lookback bars never produce trades, equity,
        or signals - the strategy only becomes evaluable once every indicator is ready, which can be later than the
        start date above.
      </p>
    </div>
  );
}

function Field({ id, label, value, onChange, placeholder, type = 'text', error, hint }) {
  const errorId = error ? `${id}-error` : undefined;
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-ink">
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className={`${inputClasses} mt-1 w-full`}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={errorId}
      />
      {hint && !error ? <p className="mt-1 text-xs text-ink-muted">{hint}</p> : null}
      <FieldError message={error} id={errorId} />
    </div>
  );
}
