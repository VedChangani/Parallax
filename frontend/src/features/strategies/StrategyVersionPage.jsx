import { useCallback } from 'react';
import { Link, useNavigate } from 'react-router';
import { getStrategy, getStrategyVersion } from '../../api/strategies.js';
import { Badge } from '../../components/Badge.jsx';
import { Button } from '../../components/Button.jsx';
import { CopyButton } from '../../components/CopyButton.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { LoadingState } from '../../components/LoadingState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { formatInstantDate } from '../../lib/format.js';
import { DefinitionPreview } from './DefinitionPreview.jsx';

/**
 * One immutable strategy version (D-31) - read-only. A version can never be
 * edited directly: the only way forward is "Create new version", which
 * starts a fresh, editable copy of *this* definition at
 * `/strategies/{id}/versions/new` (passed via router state so the route
 * structure itself stays untouched, per D-31 §17).
 *
 * @param {object} props
 * @param {number} props.strategyId
 * @param {number} props.versionNumber
 */
export function StrategyVersionPage({ strategyId, versionNumber }) {
  const navigate = useNavigate();

  const fetchStrategy = useCallback((signal) => getStrategy(strategyId, signal), [strategyId]);
  const strategy = useApiResource(fetchStrategy, [strategyId]);

  const fetchVersion = useCallback(
    (signal) => getStrategyVersion(strategyId, versionNumber, signal),
    [strategyId, versionNumber],
  );
  const version = useApiResource(fetchVersion, [strategyId, versionNumber], {
    cacheKey: `/api/strategies/${strategyId}/versions/${versionNumber}`,
  });

  if (version.loading && !version.data) {
    return <LoadingState label="Loading version…" />;
  }

  if (version.error) {
    return <ErrorState error={version.error} title="Could not load this version" onRetry={version.reload} />;
  }

  const v = version.data;

  return (
    <div>
      <p className="mb-2 text-sm">
        <Link to={`/strategies/${strategyId}`} className="text-ink-secondary hover:text-accent">
          ← Back to {strategy.data?.name ?? 'strategy'}
        </Link>
      </p>

      <PageHeader
        title={`${strategy.data?.name ?? 'Strategy'} — v${v.versionNumber}`}
        description="This version is immutable and can never be edited."
        actions={<Badge>Immutable</Badge>}
      />

      <div className="mb-8 flex flex-wrap items-center gap-2 rounded-md border border-border bg-surface p-4">
        <span className="text-xs font-medium uppercase tracking-wide text-ink-muted">Definition hash</span>
        <code className="truncate font-mono text-xs text-ink-secondary">{v.definitionHash}</code>
        <CopyButton value={v.definitionHash} label="Copy" />
        <span className="ml-auto text-xs text-ink-muted">Created {formatInstantDate(v.createdAt)}</span>
      </div>

      <section className="mb-8 rounded-md border border-border bg-surface p-5">
        <h2 className="mb-3 text-sm font-semibold text-ink">Definition</h2>
        <DefinitionPreview definition={v.definition} />
      </section>

      <Button
        variant="primary"
        onClick={() => navigate(`/strategies/${strategyId}/versions/new`, { state: { fromVersion: v.versionNumber } })}
      >
        Create new version from v{v.versionNumber}
      </Button>
    </div>
  );
}
