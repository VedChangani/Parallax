import { useCallback, useState } from 'react';
import { Link } from 'react-router';
import { getDataset, getDatasetVersion } from '../../api/datasets.js';
import { Badge } from '../../components/Badge.jsx';
import { Button } from '../../components/Button.jsx';
import { CopyButton } from '../../components/CopyButton.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { LoadingState } from '../../components/LoadingState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { ADJUSTMENT_BASIS_LABELS, SOURCE_LABELS } from '../../lib/datasetLabels.js';
import { DatasetBarsTable } from './DatasetBarsTable.jsx';

/**
 * One immutable data snapshot - the user-facing name for the backend's
 * DatasetVersion (D-32). Metadata is fetched through the session immutable
 * cache (it can never change); bars are fetched only once the user opens
 * "View historical bars" below.
 *
 * @param {object} props
 * @param {number} props.datasetId
 * @param {number} props.versionNumber
 */
export function DatasetVersionPage({ datasetId, versionNumber }) {
  const [barsOpen, setBarsOpen] = useState(false);

  const fetchDataset = useCallback((signal) => getDataset(datasetId, signal), [datasetId]);
  const dataset = useApiResource(fetchDataset, [datasetId]);

  const fetchVersion = useCallback(
    (signal) => getDatasetVersion(datasetId, versionNumber, signal),
    [datasetId, versionNumber],
  );
  const version = useApiResource(fetchVersion, [datasetId, versionNumber], {
    cacheKey: `/api/datasets/${datasetId}/versions/${versionNumber}`,
  });

  if (version.loading && !version.data) {
    return <LoadingState label="Loading snapshot…" />;
  }

  if (version.error) {
    return <ErrorState error={version.error} title="Could not load this data snapshot" onRetry={version.reload} />;
  }

  const v = version.data;

  return (
    <div>
      <p className="mb-2 text-sm">
        <Link to={`/datasets/${datasetId}`} className="text-ink-secondary hover:text-accent">
          ← Back to {dataset.data?.name ?? v.symbol}
        </Link>
      </p>

      <PageHeader
        title={v.symbol}
        description={`Data snapshot v${v.versionNumber} · immutable`}
        actions={<Badge>Immutable</Badge>}
      />

      <p className="mb-8 max-w-2xl text-sm text-ink-secondary">
        This immutable snapshot identifies the exact historical data used by a backtest - it can never change once
        created.
      </p>

      <dl className="mb-8 grid grid-cols-2 gap-6 sm:grid-cols-4">
        <Stat label="Source" value={<Badge tone="accent">{SOURCE_LABELS[v.source] ?? v.source}</Badge>} />
        <Stat label="Coverage" value={`${v.firstDate} → ${v.lastDate}`} />
        <Stat label="Bars" value={v.barCount.toLocaleString()} />
        <Stat
          label="Adjustment"
          value={<Badge>{ADJUSTMENT_BASIS_LABELS[v.adjustmentBasis] ?? v.adjustmentBasis}</Badge>}
        />
      </dl>

      {v.sourceDetail ? (
        <p className="mb-8 text-sm text-ink-secondary">
          <span className="font-medium text-ink">Detail: </span>
          {v.sourceDetail}
        </p>
      ) : null}

      <div className="mb-8 flex flex-wrap items-center gap-2 rounded-md border border-border bg-surface p-4">
        <span className="text-xs font-medium uppercase tracking-wide text-ink-muted">Snapshot hash</span>
        <code className="truncate font-mono text-xs text-ink-secondary">{v.contentHash}</code>
        <CopyButton value={v.contentHash} label="Copy" />
      </div>

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold tracking-tight text-ink">Historical bars</h2>
          {!barsOpen ? (
            <Button variant="secondary" onClick={() => setBarsOpen(true)}>
              View historical bars
            </Button>
          ) : null}
        </div>
        {barsOpen ? (
          <DatasetBarsTable datasetId={datasetId} versionNumber={versionNumber} />
        ) : (
          <p className="text-sm text-ink-secondary">
            This snapshot has {v.barCount.toLocaleString()} bars. Load them on demand - Parallax never fetches the
            full bar list automatically.
          </p>
        )}
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
