import { useState } from 'react';
import { createDataset, importAlphaVantage } from '../../api/datasets.js';
import { Button } from '../../components/Button.jsx';
import { FieldError } from '../../components/FieldError.jsx';

const SYMBOL_PATTERN = /^[A-Z0-9][A-Z0-9._-]{0,31}$/;
const NAME_MAX_LENGTH = 100;

const inputClasses =
  'mt-1 w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30';

/**
 * The "Add market" form (backend: `POST /api/datasets`, D-32). User-facing
 * "Display name"/"Symbol" map directly onto the backend's required
 * `name`/`symbol` fields - no field is added or removed, only relabeled.
 *
 * When "Load from Alpha Vantage now" is selected, a successful market
 * creation is immediately followed by an Alpha Vantage import
 * (`POST /api/datasets/{id}/versions/alpha-vantage`) so the common path
 * (create + acquire data) feels like one action. If that second call
 * fails, the market still exists - `onCreated` is still called, carrying
 * the import failure so the caller can surface it on the market's own
 * page rather than losing it.
 *
 * @param {object} props
 * @param {(dataset: import('../../api/types.js').DatasetResponse, info: {importError?: string}) => void} props.onCreated
 * @param {string} [props.className]
 */
export function DatasetCreateForm({ onCreated, className = '' }) {
  const [name, setName] = useState('');
  const [symbol, setSymbol] = useState('');
  const [acquireNow, setAcquireNow] = useState(true);
  const [historyDepth, setHistoryDepth] = useState('COMPACT');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function validate() {
    const errors = {};
    const trimmedName = name.trim();
    if (!trimmedName) {
      errors.name = 'Display name is required.';
    } else if (trimmedName.length > NAME_MAX_LENGTH) {
      errors.name = `Display name must be at most ${NAME_MAX_LENGTH} characters.`;
    }
    if (!symbol) {
      errors.symbol = 'Symbol is required.';
    } else if (!SYMBOL_PATTERN.test(symbol)) {
      errors.symbol = 'Symbol must be uppercase letters/digits, and may include . _ - (max 32 characters).';
    }
    return errors;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    setFormError('');

    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      const dataset = await createDataset({ name: name.trim(), symbol });

      if (acquireNow) {
        try {
          await importAlphaVantage(dataset.id, historyDepth);
        } catch (importError) {
          onCreated(dataset, { importError: describeAcquireError(importError) });
          return;
        }
      }

      onCreated(dataset, {});
    } catch (error) {
      if (error.errors?.length) {
        setFieldErrors(Object.fromEntries(error.errors.map((fieldError) => [fieldError.field, fieldError.message])));
      } else if (error.field) {
        setFieldErrors({ [error.field]: error.detail ?? error.title ?? 'Invalid value.' });
      } else {
        setFormError(error.detail ?? error.title ?? 'Could not add this market.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className={className}>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="dataset-symbol" className="block text-sm font-medium text-ink">
            Symbol
          </label>
          <input
            id="dataset-symbol"
            type="text"
            value={symbol}
            onChange={(event) => setSymbol(event.target.value.toUpperCase())}
            placeholder="AAPL"
            aria-invalid={Boolean(fieldErrors.symbol)}
            aria-describedby={fieldErrors.symbol ? 'dataset-symbol-error' : undefined}
            className={`${inputClasses} font-mono`}
          />
          <p className="mt-1 text-xs text-ink-muted">Uppercase letters/digits, plus . _ - (1-32 characters).</p>
          <FieldError id="dataset-symbol-error" message={fieldErrors.symbol} />
        </div>
        <div>
          <label htmlFor="dataset-name" className="block text-sm font-medium text-ink">
            Display name
          </label>
          <input
            id="dataset-name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Apple Inc."
            aria-invalid={Boolean(fieldErrors.name)}
            aria-describedby={fieldErrors.name ? 'dataset-name-error' : undefined}
            className={inputClasses}
          />
          <FieldError id="dataset-name-error" message={fieldErrors.name} />
        </div>
      </div>

      <fieldset className="mt-4">
        <legend className="block text-sm font-medium text-ink">Historical data</legend>
        <div className="mt-2 space-y-2">
          <label className="flex items-center gap-2 text-sm text-ink">
            <input
              type="radio"
              name="acquireNow"
              checked={acquireNow}
              onChange={() => setAcquireNow(true)}
            />
            Load from Alpha Vantage now
          </label>
          <label className="flex items-center gap-2 text-sm text-ink">
            <input
              type="radio"
              name="acquireNow"
              checked={!acquireNow}
              onChange={() => setAcquireNow(false)}
            />
            Add market first
          </label>
        </div>

        {acquireNow ? (
          <div className="mt-3">
            <label htmlFor="dataset-history-depth" className="block text-sm font-medium text-ink">
              History
            </label>
            <select
              id="dataset-history-depth"
              value={historyDepth}
              onChange={(event) => setHistoryDepth(event.target.value)}
              className={`${inputClasses} sm:w-72`}
            >
              <option value="COMPACT">Compact (recent history)</option>
              <option value="FULL">Full (entire available history)</option>
            </select>
          </div>
        ) : null}
      </fieldset>

      {formError ? (
        <p role="alert" className="mt-3 text-sm text-danger">
          {formError}
        </p>
      ) : null}

      <div className="mt-4">
        <Button type="submit" variant="primary" disabled={submitting} aria-busy={submitting}>
          {submitting ? 'Adding market…' : 'Add market'}
        </Button>
      </div>
    </form>
  );
}

function describeAcquireError(error) {
  if (error.kind === 'network') return 'Could not reach the backend. Check your connection and try again.';
  switch (error.status) {
    case 422:
      return error.detail ?? error.title ?? 'Alpha Vantage rejected this request.';
    case 503:
      return error.detail ?? 'Alpha Vantage is temporarily unavailable. Try again shortly.';
    case 502:
      return error.detail ?? 'Alpha Vantage returned an unexpected response.';
    case 500:
      return error.detail ?? 'An internal error occurred while storing this version.';
    default:
      return error.detail ?? error.title ?? 'Could not load historical data from Alpha Vantage.';
  }
}
