import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { getStrategy, getStrategyVersion, listStrategyVersions } from '../../api/strategies.js';
import { Button } from '../../components/Button.jsx';
import { buttonClasses } from '../../components/buttonStyles.js';
import { EmptyState } from '../../components/EmptyState.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { LoadingState } from '../../components/LoadingState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { formatInstantDate } from '../../lib/format.js';
import { DefinitionPreview } from './DefinitionPreview.jsx';
import { StrategyMetadataEditForm } from './StrategyMetadataEditForm.jsx';
import { StrategyVersionHistory } from './StrategyVersionHistory.jsx';

export function StrategyDetailPage({ strategyId }) {
  const [editing, setEditing] = useState(false);
  const navigate = useNavigate();

  const fetchStrategy = useCallback((signal) => getStrategy(strategyId, signal), [strategyId]);
  const strategy = useApiResource(fetchStrategy, [strategyId]);

  const fetchVersions = useCallback((signal) => listStrategyVersions(strategyId, signal), [strategyId]);
  const versions = useApiResource(fetchVersions, [strategyId]);

  const latestVersionNumber = strategy.data?.latestVersionNumber;
  const fetchLatestVersion = useCallback(
    (signal) => {
      if (!latestVersionNumber) return Promise.resolve(undefined);
      return getStrategyVersion(strategyId, latestVersionNumber, signal);
    },
    [strategyId, latestVersionNumber],
  );
  const latestVersion = useApiResource(fetchLatestVersion, [strategyId, latestVersionNumber], {
    cacheKey: latestVersionNumber ? `/api/strategies/${strategyId}/versions/${latestVersionNumber}` : undefined,
  });

  if (strategy.loading && !strategy.data) {
    return <LoadingState label="Loading strategy…" />;
  }

  if (strategy.error) {
    return <ErrorState error={strategy.error} title="Could not load this strategy" onRetry={strategy.reload} />;
  }

  const s = strategy.data;

  function handleMetadataSaved() {
    setEditing(false);
    strategy.reload();
  }

  return (
    <div>
      <p className="mb-2 text-sm">
        <Link to="/strategies" className="text-ink-secondary hover:text-accent">
          ← Back to strategies
        </Link>
      </p>

      <PageHeader
        title={s.name}
        description={s.description || 'No description.'}
        actions={
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setEditing((open) => !open)} aria-expanded={editing}>
              {editing ? 'Cancel' : 'Edit'}
            </Button>
            <Button variant="secondary" onClick={() => navigate(`/strategies/${strategyId}/versions/new`)}>
              Create new version
            </Button>
            <Link to="/backtests/new" state={{ strategyId }} className={buttonClasses('primary')}>
              Create backtest
            </Link>
          </div>
        }
      />

      {editing ? (
        <div className="mb-8">
          <StrategyMetadataEditForm strategy={s} onSaved={handleMetadataSaved} onCancel={() => setEditing(false)} />
        </div>
      ) : null}

      <dl className="mb-8 grid grid-cols-2 gap-6 sm:grid-cols-3">
        <Stat label="Latest version" value={`v${s.latestVersionNumber}`} />
        <Stat label="Created" value={formatInstantDate(s.createdAt)} />
        <Stat label="Versions" value={versions.data?.length ?? '—'} />
      </dl>

      {latestVersion.data ? (
        <section className="mb-8 rounded-md border border-border bg-surface p-5">
          <h2 className="mb-3 text-sm font-semibold text-ink">
            What this strategy does <span className="font-normal text-ink-muted">(latest version)</span>
          </h2>
          <DefinitionPreview definition={latestVersion.data.definition} />
        </section>
      ) : null}

      <section>
        <h2 className="mb-3 text-lg font-semibold tracking-tight text-ink">
          Version history{' '}
          {versions.data ? <span className="text-sm font-normal text-ink-muted">({versions.data.length})</span> : null}
        </h2>
        {versions.loading && !versions.data ? <LoadingState label="Loading versions…" /> : null}
        {versions.error ? (
          <ErrorState error={versions.error} title="Could not load version history" onRetry={versions.reload} />
        ) : null}
        {!versions.error && versions.data ? (
          versions.data.length === 0 ? (
            <EmptyState title="No versions yet" />
          ) : (
            <StrategyVersionHistory strategyId={strategyId} versions={versions.data} />
          )
        ) : null}
      </section>
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className="mt-1 text-lg font-semibold tabular-nums text-ink">{value}</dd>
    </div>
  );
}
