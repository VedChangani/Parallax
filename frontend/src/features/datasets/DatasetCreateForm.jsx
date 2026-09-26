import { useState } from 'react';
import { createDataset } from '../../api/datasets.js';
import { Button } from '../../components/Button.jsx';
import { FieldError } from '../../components/FieldError.jsx';

const SYMBOL_PATTERN = /^[A-Z0-9][A-Z0-9._-]{0,31}$/;
const NAME_MAX_LENGTH = 100;

const inputClasses =
  'mt-1 w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30';

/**
 * Inline dataset-creation form (D-32 `POST /api/datasets`). Client-side
 * checks here are lightweight UX only - the backend's Bean Validation and
 * `Dataset.SYMBOL_PATTERN` remain authoritative; a backend field error
 * always overrides what this form guessed.
 *
 * @param {object} props
 * @param {(dataset: import('../../api/types.js').DatasetResponse) => void} props.onCreated
 * @param {string} [props.className]
 */
export function DatasetCreateForm({ onCreated, className = '' }) {
  const [name, setName] = useState('');
  const [symbol, setSymbol] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function validate() {
    const errors = {};
    const trimmedName = name.trim();
    if (!trimmedName) {
      errors.name = 'Name is required.';
    } else if (trimmedName.length > NAME_MAX_LENGTH) {
      errors.name = `Name must be at most ${NAME_MAX_LENGTH} characters.`;
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
      onCreated(dataset);
    } catch (error) {
      if (error.errors?.length) {
        setFieldErrors(Object.fromEntries(error.errors.map((fieldError) => [fieldError.field, fieldError.message])));
      } else if (error.field) {
        setFieldErrors({ [error.field]: error.detail ?? error.title ?? 'Invalid value.' });
      } else {
        setFormError(error.detail ?? error.title ?? 'Could not create the dataset.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className={className}>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="dataset-name" className="block text-sm font-medium text-ink">
            Name
          </label>
          <input
            id="dataset-name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            aria-invalid={Boolean(fieldErrors.name)}
            aria-describedby={fieldErrors.name ? 'dataset-name-error' : undefined}
            className={inputClasses}
          />
          <FieldError id="dataset-name-error" message={fieldErrors.name} />
        </div>
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
      </div>

      {formError ? (
        <p role="alert" className="mt-3 text-sm text-danger">
          {formError}
        </p>
      ) : null}

      <div className="mt-4">
        <Button type="submit" variant="primary" disabled={submitting} aria-busy={submitting}>
          {submitting ? 'Creating…' : 'Create dataset'}
        </Button>
      </div>
    </form>
  );
}
