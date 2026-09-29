import { useState } from 'react';
import { createStrategy } from '../../api/strategies.js';
import { Button } from '../../components/Button.jsx';
import { FieldError } from '../../components/FieldError.jsx';
import { definitionHasErrors, definitionToDto, newDefinitionState } from './definitionMapping.js';
import { StrategyBuilder } from './StrategyBuilder.jsx';

const NAME_MAX_LENGTH = 100;
const DESCRIPTION_MAX_LENGTH = 2000;

const inputClasses =
  'mt-1 w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30';

export function StrategyCreateForm({ onCreated }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [definition, setDefinition] = useState(newDefinitionState);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [definitionError, setDefinitionError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function validateMetadata() {
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
    setDefinitionError('');

    const errors = validateMetadata();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    if (definitionHasErrors(definition)) {
      setDefinitionError('Fix the highlighted fields in the strategy rules before creating this strategy.');
      return;
    }

    setSubmitting(true);
    try {
      const strategy = await createStrategy({
        name: name.trim(),
        description: description.trim(),
        definition: definitionToDto(definition),
      });
      onCreated(strategy);
    } catch (error) {
      applyServerError(error, setFieldErrors, setDefinitionError, setFormError);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <section className="rounded-md border border-border bg-surface p-5">
        <h2 className="text-sm font-semibold text-ink">Strategy details</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="strategy-name" className="block text-sm font-medium text-ink">
              Name
            </label>
            <input
              id="strategy-name"
              type="text"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Momentum Cross"
              aria-invalid={Boolean(fieldErrors.name)}
              aria-describedby={fieldErrors.name ? 'strategy-name-error' : undefined}
              className={inputClasses}
            />
            <FieldError id="strategy-name-error" message={fieldErrors.name} />
          </div>
          <div>
            <label htmlFor="strategy-description" className="block text-sm font-medium text-ink">
              Description
            </label>
            <input
              id="strategy-description"
              type="text"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="SMA trend-following strategy"
              aria-invalid={Boolean(fieldErrors.description)}
              aria-describedby={fieldErrors.description ? 'strategy-description-error' : undefined}
              className={inputClasses}
            />
            <FieldError id="strategy-description-error" message={fieldErrors.description} />
          </div>
        </div>
      </section>

      <section className="mt-6">
        <h2 className="mb-3 text-sm font-semibold text-ink">Strategy rules</h2>
        {definitionError ? (
          <p role="alert" className="mb-3 text-sm text-danger">
            {definitionError}
          </p>
        ) : null}
        <StrategyBuilder state={definition} onChange={setDefinition} />
      </section>

      {formError ? (
        <p role="alert" className="mt-4 text-sm text-danger">
          {formError}
        </p>
      ) : null}

      <div className="mt-6">
        <Button type="submit" variant="primary" disabled={submitting} aria-busy={submitting}>
          {submitting ? 'Creating strategy…' : 'Create strategy'}
        </Button>
      </div>
    </form>
  );
}

function applyServerError(error, setFieldErrors, setDefinitionError, setFormError) {
  if (error.errors?.length) {
    setFieldErrors(Object.fromEntries(error.errors.map((fieldError) => [fieldError.field, fieldError.message])));
    return;
  }
  if (typeof error.field === 'string' && error.field.startsWith('definition')) {
    setDefinitionError(error.detail ?? error.title ?? 'This strategy definition is invalid.');
    return;
  }
  if (error.field) {
    setFieldErrors({ [error.field]: error.detail ?? error.title ?? 'Invalid value.' });
    return;
  }
  setFormError(error.detail ?? error.title ?? 'Could not create this strategy.');
}
