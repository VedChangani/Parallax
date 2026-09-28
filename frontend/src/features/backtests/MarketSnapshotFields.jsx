import { Link } from 'react-router';
import { Badge } from '../../components/Badge.jsx';
import { buttonClasses } from '../../components/buttonStyles.js';
import { ErrorState } from '../../components/ErrorState.jsx';
import { LoadingState } from '../../components/LoadingState.jsx';
import { ADJUSTMENT_BASIS_LABELS, SOURCE_LABELS } from '../../lib/datasetLabels.js';
import { selectClasses } from '../strategies/formStyles.js';

/**
 * The "Market" section of the New Backtest form (D-34 Batch 4): choose an
 * existing market, then choose one of its immutable data snapshots. Never
 * creates a market or loads bars - both selects are populated purely from
 * already-fetched metadata (`DatasetResponse`/`DatasetVersionResponse`),
 * and the coverage panel renders `snapshotDetail` (a cached, immutable
 * single-snapshot fetch - see BacktestNewPage.jsx).
 *
 * @param {object} props
 * @param {{data?: import('../../api/types.js').DatasetResponse[], loading: boolean, error?: import('../../api/apiError.js').ApiError, reload: () => void}} props.markets
 * @param {number | undefined} props.marketId
 * @param {(id: number) => void} props.onMarketChange
 * @param {{data?: import('../../api/types.js').DatasetVersionResponse[], loading: boolean, error?: import('../../api/apiError.js').ApiError}} props.versions
 * @param {number | undefined} props.snapshotVersion
 * @param {(version: number) => void} props.onSnapshotChange
 * @param {{data?: import('../../api/types.js').DatasetVersionResponse, loading: boolean, error?: import('../../api/apiError.js').ApiError}} props.snapshotDetail
 */
export function MarketSnapshotFields({
  markets,
  marketId,
  onMarketChange,
  versions,
  snapshotVersion,
  onSnapshotChange,
  snapshotDetail,
}) {
  if (markets.loading && !markets.data) {
    return <LoadingState label="Loading markets…" />;
  }

  if (markets.error) {
    return <ErrorState error={markets.error} title="Could not load markets" onRetry={markets.reload} />;
  }

  if (markets.data.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-border bg-surface p-6 text-center">
        <p className="text-sm font-semibold text-ink">No markets available</p>
        <p className="mt-1 text-sm text-ink-secondary">Add a market first, then load its historical data.</p>
        <Link to="/datasets" className={`${buttonClasses('secondary')} mt-4`}>
          Go to Markets
        </Link>
      </div>
    );
  }

  const sortedVersions = versions.data ? [...versions.data].sort((a, b) => b.versionNumber - a.versionNumber) : [];

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="backtest-market" className="block text-sm font-medium text-ink">
            Choose market
          </label>
          <select
            id="backtest-market"
            value={marketId ?? ''}
            onChange={(event) => onMarketChange(Number(event.target.value))}
            className={`${selectClasses} mt-1 w-full`}
          >
            {markets.data.map((market) => (
              <option key={market.id} value={market.id}>
                {market.name} ({market.symbol})
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="backtest-snapshot" className="block text-sm font-medium text-ink">
            Data snapshot
          </label>
          {versions.loading && !versions.data ? (
            <div className="mt-1">
              <LoadingState label="Loading snapshots…" />
            </div>
          ) : versions.error ? (
            <p className="mt-1 text-sm text-danger">Could not load data snapshots.</p>
          ) : sortedVersions.length === 0 ? (
            <p className="mt-1 text-sm text-ink-secondary">
              No data snapshots yet.{' '}
              <Link to={`/datasets/${marketId}`} className="font-medium text-accent hover:underline">
                Load historical data
              </Link>
            </p>
          ) : (
            <select
              id="backtest-snapshot"
              value={snapshotVersion ?? ''}
              onChange={(event) => onSnapshotChange(Number(event.target.value))}
              className={`${selectClasses} mt-1 w-full`}
            >
              {sortedVersions.map((version) => (
                <option key={version.versionNumber} value={version.versionNumber}>
                  Snapshot v{version.versionNumber} · {SOURCE_LABELS[version.source] ?? version.source}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      {snapshotDetail.loading && !snapshotDetail.data ? <LoadingState label="Loading snapshot details…" /> : null}
      {snapshotDetail.error ? (
        <ErrorState error={snapshotDetail.error} title="Could not load this data snapshot" />
      ) : null}
      {snapshotDetail.data ? (
        <dl className="grid grid-cols-2 gap-4 rounded-md border border-border bg-page/60 p-4 sm:grid-cols-4">
          <Stat label="Source" value={<Badge tone="accent">{SOURCE_LABELS[snapshotDetail.data.source] ?? snapshotDetail.data.source}</Badge>} />
          <Stat label="Coverage" value={`${snapshotDetail.data.firstDate} → ${snapshotDetail.data.lastDate}`} />
          <Stat label="Bars" value={snapshotDetail.data.barCount.toLocaleString()} />
          <Stat
            label="Adjustment"
            value={<Badge>{ADJUSTMENT_BASIS_LABELS[snapshotDetail.data.adjustmentBasis] ?? snapshotDetail.data.adjustmentBasis}</Badge>}
          />
        </dl>
      ) : null}
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className="mt-1 text-sm font-semibold tabular-nums text-ink">{value}</dd>
    </div>
  );
}
