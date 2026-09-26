import { useState } from 'react';
import { updateStrategyMetadata } from '../../api/strategies.js';
import { Button } from '../../components/Button.jsx';
import { FieldError } from '../../components/FieldError.jsx';

const NAME_MAX_LENGTH = 100;
const DESCRIPTION_MAX_LENGTH = 2000;

const inputClasses =
  'mt-1 w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30';

/**
 * Edits only a strategy's mutable metadata (`PATCH /api/strategies/{id}`,
 * D-31 §18) - name and description. Never touches any version: the
 * strategy id, latestVersionNumber, and every version's definition/hash
 * are not exposed here as editable fields.
 *
 * @param {object} props
 * @param {import('../../api/types.js').StrategyResponse} props.strategy
 * @param {(updated: import('../../api/types.js').StrategyResponse) => void} props.onSaved
 * @param {() => void} props.onCancel
 */
export function StrategyMetadataEditForm({ strategy, onSaved, onCancel }) {
  const [name, setName] = useState(strategy.name);
  const [description, setDescription] = useState(strategy.description);
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
    if (description.length > DESCRIPTION_MAX_LENGTH) {
      errors.description = `Description must be at most ${DESCRIPTION_MAX_LENGTH} characters.`;
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
      const updated = await updateStrategyMetadata(strategy.id, { name: name.trim(), description: description.trim() });
      onSaved(updated);
    } catch (error) {
      if (error.errors?.length) {
        setFieldErrors(Object.fromEntries(error.errors.map((fieldError) => [fieldError.field, fieldError.message])));
      } else if (error.field) {
        setFieldErrors({ [error.field]: error.detail ?? error.title ?? 'Invalid value.' });
      } else {
        setFormError(error.detail ?? error.title ?? 'Could not update this strategy.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="rounded-md border border-border bg-surface p-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="edit-strategy-name" className="block text-sm font-medium text-ink">
            Name
          </label>
          <input
            id="edit-strategy-name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            aria-invalid={Boolean(fieldErrors.name)}
            aria-describedby={fieldErrors.name ? 'edit-strategy-name-error' : undefined}
            className={inputClasses}
          />
          <FieldError id="edit-strategy-name-error" message={fieldErrors.name} />
        </div>
        <div>
          <label htmlFor="edit-strategy-description" className="block text-sm font-medium text-ink">
            Description
          </label>
          <input
            id="edit-strategy-description"
            type="text"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            aria-invalid={Boolean(fieldErrors.description)}
            aria-describedby={fieldErrors.description ? 'edit-strategy-description-error' : undefined}
            className={inputClasses}
          />
          <FieldError id="edit-strategy-description-error" message={fieldErrors.description} />
        </div>
      </div>

      {formError ? (
        <p role="alert" className="mt-3 text-sm text-danger">
          {formError}
        </p>
      ) : null}

      <div className="mt-4 flex gap-2">
        <Button type="submit" variant="primary" disabled={submitting} aria-busy={submitting}>
          {submitting ? 'Saving…' : 'Save changes'}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
