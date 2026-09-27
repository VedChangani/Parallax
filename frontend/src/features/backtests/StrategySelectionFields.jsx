import { Link } from 'react-router';
import { buttonClasses } from '../../components/buttonStyles.js';
import { ErrorState } from '../../components/ErrorState.jsx';
import { LoadingState } from '../../components/LoadingState.jsx';
import { DefinitionPreview } from '../strategies/DefinitionPreview.jsx';
import { selectClasses } from '../strategies/formStyles.js';

/**
 * The "Strategy" section of the New Backtest form (D-34 Batch 4): choose an
 * existing strategy, then choose one of its immutable versions. Never edits
 * a strategy - the "Edit strategy" link navigates to the Strategy workflow
 * instead of embedding the builder here. `versionDetail` is a cached,
 * immutable single-version fetch (see BacktestNewPage.jsx) used purely to
 * render a read-only preview via the same `DefinitionPreview` the Strategy
 * Builder itself uses.
 *
 * @param {object} props
 * @param {{data?: import('../../api/types.js').StrategyResponse[], loading: boolean, error?: import('../../api/apiError.js').ApiError, reload: () => void}} props.strategies
 * @param {number | undefined} props.strategyId
 * @param {(id: number) => void} props.onStrategyChange
 * @param {{data?: import('../../api/types.js').StrategyVersionSummaryResponse[], loading: boolean, error?: import('../../api/apiError.js').ApiError}} props.versions
 * @param {number | undefined} props.strategyVersion
 * @param {(version: number) => void} props.onVersionChange
 * @param {{data?: import('../../api/types.js').StrategyVersionResponse, loading: boolean, error?: import('../../api/apiError.js').ApiError}} props.versionDetail
 */
export function StrategySelectionFields({
  strategies,
  strategyId,
  onStrategyChange,
  versions,
  strategyVersion,
  onVersionChange,
  versionDetail,
}) {
  if (strategies.loading && !strategies.data) {
    return <LoadingState label="Loading strategies…" />;
  }

  if (strategies.error) {
    return <ErrorState error={strategies.error} title="Could not load strategies" onRetry={strategies.reload} />;
  }

  if (strategies.data.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-border bg-surface p-6 text-center">
        <p className="text-sm font-semibold text-ink">No strategies available</p>
        <p className="mt-1 text-sm text-ink-secondary">Create a strategy first, then come back to configure a backtest.</p>
        <Link to="/strategies" className={`${buttonClasses('secondary')} mt-4`}>
          Go to Strategies
        </Link>
      </div>
    );
  }

  const sortedVersions = versions.data ? [...versions.data].sort((a, b) => b.versionNumber - a.versionNumber) : [];
  const selectedStrategy = strategies.data.find((strategy) => strategy.id === strategyId);

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="backtest-strategy" className="block text-sm font-medium text-ink">
            Choose strategy
          </label>
          <select
            id="backtest-strategy"
            value={strategyId ?? ''}
            onChange={(event) => onStrategyChange(Number(event.target.value))}
            className={`${selectClasses} mt-1 w-full`}
          >
            {strategies.data.map((strategy) => (
              <option key={strategy.id} value={strategy.id}>
                {strategy.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="backtest-strategy-version" className="block text-sm font-medium text-ink">
            Version
          </label>
          {versions.loading && !versions.data ? (
            <div className="mt-1">
              <LoadingState label="Loading versions…" />
            </div>
          ) : versions.error ? (
            <p className="mt-1 text-sm text-danger">Could not load strategy versions.</p>
          ) : sortedVersions.length === 0 ? (
            <p className="mt-1 text-sm text-ink-secondary">No versions yet.</p>
          ) : (
            <select
              id="backtest-strategy-version"
              value={strategyVersion ?? ''}
              onChange={(event) => onVersionChange(Number(event.target.value))}
              className={`${selectClasses} mt-1 w-full`}
            >
              {sortedVersions.map((version) => (
                <option key={version.versionNumber} value={version.versionNumber}>
                  v{version.versionNumber}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      {versionDetail.loading && !versionDetail.data ? <LoadingState label="Loading strategy definition…" /> : null}
      {versionDetail.error ? (
        <ErrorState error={versionDetail.error} title="Could not load this strategy version" />
      ) : null}
      {versionDetail.data ? (
        <div className="rounded-md border border-border bg-page/60 p-4">
          <DefinitionPreview definition={versionDetail.data.definition} />
        </div>
      ) : null}

      {selectedStrategy ? (
        <Link to={`/strategies/${selectedStrategy.id}`} className="inline-flex text-sm font-medium text-accent hover:underline">
          Edit strategy →
        </Link>
      ) : null}
    </div>
  );
}
