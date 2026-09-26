import { useCallback } from 'react';
import { getDataset, listDatasetVersions } from '../../api/datasets.js';
import { EmptyState } from '../../components/EmptyState.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { LoadingState } from '../../components/LoadingState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { formatInstantDate } from '../../lib/format.js';
import { AlphaVantageImportForm } from './AlphaVantageImportForm.jsx';
import { CsvUploadForm } from './CsvUploadForm.jsx';
import { DatasetVersionHistory } from './DatasetVersionHistory.jsx';

/**
 * The Dataset workspace: metadata, both import tools, and version history.
 * `dataset`/`versions` are both mutable resources (never immutable-cached)
 * and are refetched after every successful CSV/Alpha Vantage import.
 *
 * @param {object} props
 * @param {number} props.datasetId
 */
export function DatasetDetailPage({ datasetId }) {
  const fetchDataset = useCallback((signal) => getDataset(datasetId, signal), [datasetId]);
  const dataset = useApiResource(fetchDataset, [datasetId]);

  const fetchVersions = useCallback((signal) => listDatasetVersions(datasetId, signal), [datasetId]);
  const versions = useApiResource(fetchVersions, [datasetId]);

  function handleImported() {
    dataset.reload();
    versions.reload();
  }

  if (dataset.loading && !dataset.data) {
    return <LoadingState label="Loading dataset…" />;
  }

  if (dataset.error) {
    return <ErrorState error={dataset.error} title="Could not load this dataset" onRetry={dataset.reload} />;
  }

  const summary = dataset.data;

  return (
    <div>
      <PageHeader
        title={summary.name}
        description={`Symbol ${summary.symbol} · created ${formatInstantDate(summary.createdAt)}`}
      />

      <dl className="mb-8 grid grid-cols-2 gap-6 sm:grid-cols-4">
        <Stat label="Symbol" value={<span className="font-mono">{summary.symbol}</span>} />
        <Stat
          label="Latest version"
          value={summary.latestVersionNumber > 0 ? `v${summary.latestVersionNumber}` : '—'}
        />
        <Stat label="Versions" value={versions.data ? versions.data.length : '—'} />
        <Stat label="Created" value={formatInstantDate(summary.createdAt)} />
      </dl>

      <div className="mb-10 grid gap-6 lg:grid-cols-2">
        <CsvUploadForm datasetId={datasetId} onImported={handleImported} />
        <AlphaVantageImportForm datasetId={datasetId} onImported={handleImported} />
      </div>

      <section>
        <h2 className="mb-3 text-lg font-semibold tracking-tight text-ink">Version history</h2>
        {versions.loading && !versions.data ? <LoadingState label="Loading versions…" /> : null}
        {versions.error ? (
          <ErrorState error={versions.error} title="Could not load version history" onRetry={versions.reload} />
        ) : null}
        {!versions.error && versions.data ? (
          versions.data.length === 0 ? (
            <EmptyState
              title="No versions yet"
              description="Import data using CSV or Alpha Vantage above to create the first version."
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
