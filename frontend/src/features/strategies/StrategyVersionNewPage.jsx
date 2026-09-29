import { useCallback, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { createStrategyVersion, getStrategy, getStrategyVersion } from '../../api/strategies.js';
import { Button } from '../../components/Button.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { LoadingState } from '../../components/LoadingState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { definitionHasErrors, definitionToDto, dtoToDefinitionState } from './definitionMapping.js';
import { StrategyBuilder } from './StrategyBuilder.jsx';

export function StrategyVersionNewPage({ strategyId }) {
  const location = useLocation();
  const navigate = useNavigate();
  const fromVersion = location.state?.fromVersion;

  const fetchStrategy = useCallback((signal) => getStrategy(strategyId, signal), [strategyId]);
  const strategy = useApiResource(fetchStrategy, [strategyId]);

  const sourceVersionNumber = fromVersion ?? strategy.data?.latestVersionNumber;
  const fetchSourceVersion = useCallback(
    (signal) => {
      if (!sourceVersionNumber) return Promise.resolve(undefined);
      return getStrategyVersion(strategyId, sourceVersionNumber, signal);
    },
    [strategyId, sourceVersionNumber],
  );
  const sourceVersion = useApiResource(fetchSourceVersion, [strategyId, sourceVersionNumber], {
    cacheKey: sourceVersionNumber ? `/api/strategies/${strategyId}/versions/${sourceVersionNumber}` : undefined,
  });

  const [definition, setDefinition] = useState(null);
  const [seededFrom, setSeededFrom] = useState(null);
  if (sourceVersion.data && seededFrom !== sourceVersion.data) {
    setSeededFrom(sourceVersion.data);
    setDefinition(dtoToDefinitionState(sourceVersion.data.definition));
  }

  const [definitionError, setDefinitionError] = useState('');
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (strategy.error) {
    return <ErrorState error={strategy.error} title="Could not load this strategy" onRetry={strategy.reload} />;
  }
  if (sourceVersion.error) {
    return (
      <ErrorState error={sourceVersion.error} title="Could not load the starting version" onRetry={sourceVersion.reload} />
    );
  }
  if (!definition) {
    return <LoadingState label="Loading strategy version…" />;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    setFormError('');
    setDefinitionError('');

    if (definitionHasErrors(definition)) {
      setDefinitionError('Fix the highlighted fields in the strategy rules before creating this version.');
      return;
    }

    setSubmitting(true);
    try {
      const version = await createStrategyVersion(strategyId, definitionToDto(definition));
      navigate(`/strategies/${strategyId}/versions/${version.versionNumber}`);
    } catch (error) {
      if (typeof error.field === 'string') {
        setDefinitionError(error.detail ?? error.title ?? 'This strategy definition is invalid.');
      } else {
        setFormError(error.detail ?? error.title ?? 'Could not create this version.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <p className="mb-2 text-sm">
        <Link to={`/strategies/${strategyId}`} className="text-ink-secondary hover:text-accent">
          ← Back to {strategy.data?.name ?? 'strategy'}
        </Link>
      </p>

      <PageHeader
        title={`New version${strategy.data ? ` — ${strategy.data.name}` : ''}`}
        description={`Starting from v${sourceVersionNumber}. The next version number is allocated automatically.`}
      />

      <form onSubmit={handleSubmit} noValidate>
        {definitionError ? (
          <p role="alert" className="mb-3 text-sm text-danger">
            {definitionError}
          </p>
        ) : null}
        <StrategyBuilder state={definition} onChange={setDefinition} />

        {formError ? (
          <p role="alert" className="mt-4 text-sm text-danger">
            {formError}
          </p>
        ) : null}

        <div className="mt-6">
          <Button type="submit" variant="primary" disabled={submitting} aria-busy={submitting}>
            {submitting ? 'Creating version…' : 'Create version'}
          </Button>
        </div>
      </form>
    </div>
  );
}
