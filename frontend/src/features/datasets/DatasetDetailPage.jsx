import { useCallback } from 'react';
import { Link, useLocation } from 'react-router';
import { getDataset, listDatasetVersions } from '../../api/datasets.js';
import { Badge } from '../../components/Badge.jsx';
import { buttonClasses } from '../../components/buttonStyles.js';
import { EmptyState } from '../../components/EmptyState.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { LoadingState } from '../../components/LoadingState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { SOURCE_LABELS } from '../../lib/datasetLabels.js';
import { AlphaVantageImportForm } from './AlphaVantageImportForm.jsx';
import { CsvUploadForm } from './CsvUploadForm.jsx';
import { DatasetVersionHistory } from './DatasetVersionHistory.jsx';

export function DatasetDetailPage({ datasetId }) {
  const location = useLocation();
  const importError = location.state?.importError;

  const fetchDataset = useCallback((signal) => getDataset(datasetId, signal), [datasetId]);
  const dataset = useApiResource(fetchDataset, [datasetId]);

  const fetchVersions = useCallback((signal) => listDatasetVersions(datasetId, signal), [datasetId]);
  const versions = useApiResource(fetchVersions, [datasetId]);

  function handleImported() {
    dataset.reload();
    versions.reload();
  }

  if (dataset.loading && !dataset.data) {
    return <LoadingState label="Loading market…" />;
  }

  if (dataset.error) {
    return <ErrorState error={dataset.error} title="Could not load this market" onRetry={dataset.reload} />;
  }

  const market = dataset.data;
  const latestVersion = versions.data?.length
    ? versions.data.reduce((latest, candidate) => (candidate.versionNumber > latest.versionNumber ? candidate : latest))
    : undefined;

  return (
    <div>
      <PageHeader
        title={market.symbol}
        description={`${market.name} · daily historical data`}
        actions={
          <Link to="/backtests/new" state={{ marketId: datasetId }} className={buttonClasses('primary')}>
            Create backtest
          </Link>
        }
      />

      {importError ? (
        <div className="mb-8">
          <ErrorState title="Historical data import failed" error={{ detail: importError }} />
        </div>
      ) : null}

      {versions.data ? (
        latestVersion ? (
          <div className="mb-8 rounded-md border border-border bg-surface p-5">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h2 className="text-sm font-semibold text-ink">Latest data snapshot</h2>
                <p className="mt-1 text-sm text-ink-secondary">Snapshot v{latestVersion.versionNumber} · immutable</p>
              </div>
              <Link
                to={`/datasets/${datasetId}/versions/${latestVersion.versionNumber}`}
                className={buttonClasses('secondary')}
              >
                Open snapshot
              </Link>
            </div>
            <dl className="mt-4 grid grid-cols-2 gap-6 sm:grid-cols-3">
              <Stat
                label="Source"
                value={<Badge tone="accent">{SOURCE_LABELS[latestVersion.source] ?? latestVersion.source}</Badge>}
              />
              <Stat label="Coverage" value={`${latestVersion.firstDate} → ${latestVersion.lastDate}`} />
              <Stat label="Bars" value={latestVersion.barCount.toLocaleString()} />
            </dl>
          </div>
        ) : (
          <div className="mb-8 rounded-md border border-dashed border-border bg-surface p-5 text-sm text-ink-secondary">
            No data snapshot yet. Load historical data below to begin.
          </div>
        )
      ) : null}

      <section className="mb-8">
        <AlphaVantageImportForm datasetId={datasetId} onImported={handleImported} />
      </section>

      <section className="mb-10">
        <details className="group rounded-md border border-border bg-surface [&::-webkit-details-marker]:hidden">
          <summary className="flex cursor-pointer list-none items-center justify-between px-4 py-3 text-sm font-medium text-ink-secondary hover:text-ink">
            <span>Advanced data import - custom CSV</span>
            <span aria-hidden="true" className="text-ink-muted transition-transform duration-150 group-open:rotate-180">
              ⌄
            </span>
          </summary>
          <div className="border-t border-border p-4">
            <p className="mb-4 text-sm text-ink-secondary">
              Use your own historical CSV when you need a custom or proprietary dataset.
            </p>
            <CsvUploadForm datasetId={datasetId} onImported={handleImported} />
          </div>
        </details>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold tracking-tight text-ink">
          Data snapshots{' '}
          {versions.data ? <span className="text-sm font-normal text-ink-muted">({versions.data.length})</span> : null}
        </h2>
        {versions.loading && !versions.data ? <LoadingState label="Loading snapshots…" /> : null}
        {versions.error ? (
          <ErrorState error={versions.error} title="Could not load data snapshots" onRetry={versions.reload} />
        ) : null}
        {!versions.error && versions.data ? (
          versions.data.length === 0 ? (
            <EmptyState
              title="No data snapshots yet"
              description="Load historical data above to create the first snapshot."
            />
          ) : (
            <DatasetVersionHistory datasetId={datasetId} versions={versions.data} />
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
