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
import { DatasetBarsTable } from './DatasetBarsTable.jsx';

const SOURCE_LABELS = { CSV_UPLOAD: 'CSV upload', ALPHA_VANTAGE: 'Alpha Vantage' };
const ADJUSTMENT_BASIS_LABELS = {
  RAW: 'Raw',
  SPLIT_ADJUSTED: 'Split-adjusted',
  SPLIT_AND_DIVIDEND_ADJUSTED: 'Split & dividend-adjusted',
};

/**
 * One immutable DatasetVersion (D-32). Metadata is fetched through the
 * session immutable cache (it can never change); bars are fetched only
 * once the user opens the "View bars" section below.
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
    return <LoadingState label="Loading version…" />;
  }

  if (version.error) {
    return <ErrorState error={version.error} title="Could not load this dataset version" onRetry={version.reload} />;
  }

  const v = version.data;

  return (
    <div>
      <p className="mb-2 text-sm">
        <Link to={`/datasets/${datasetId}`} className="text-ink-secondary hover:text-accent">
          ← Back to {dataset.data?.name ?? `dataset #${datasetId}`}
        </Link>
      </p>

      <PageHeader
        title={`${v.symbol} — version ${v.versionNumber}`}
        description="An immutable, integrity-verified dataset snapshot."
        actions={<Badge>Immutable</Badge>}
      />

      <dl className="mb-8 grid grid-cols-2 gap-6 sm:grid-cols-4">
        <Stat label="Source" value={<Badge tone="accent">{SOURCE_LABELS[v.source] ?? v.source}</Badge>} />
        <Stat
          label="Adjustment basis"
          value={<Badge>{ADJUSTMENT_BASIS_LABELS[v.adjustmentBasis] ?? v.adjustmentBasis}</Badge>}
        />
        <Stat label="Bars" value={v.barCount} />
        <Stat label="Date range" value={`${v.firstDate} → ${v.lastDate}`} />
      </dl>

      {v.sourceDetail ? (
        <p className="mb-8 text-sm text-ink-secondary">
          <span className="font-medium text-ink">Source detail: </span>
          {v.sourceDetail}
        </p>
      ) : null}

      <div className="mb-8 flex flex-wrap items-center gap-2 rounded-md border border-border bg-surface p-4">
        <span className="text-xs font-medium uppercase tracking-wide text-ink-muted">Content hash</span>
        <code className="truncate font-mono text-xs text-ink-secondary">{v.contentHash}</code>
        <CopyButton value={v.contentHash} label="Copy" />
      </div>

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold tracking-tight text-ink">Bars</h2>
          {!barsOpen ? (
            <Button variant="secondary" onClick={() => setBarsOpen(true)}>
              View bars
            </Button>
          ) : null}
        </div>
        {barsOpen ? (
          <DatasetBarsTable datasetId={datasetId} versionNumber={versionNumber} />
        ) : (
          <p className="text-sm text-ink-secondary">
            This version has {v.barCount} bars. Load them on demand - Parallax never fetches the full bar list
            automatically.
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
