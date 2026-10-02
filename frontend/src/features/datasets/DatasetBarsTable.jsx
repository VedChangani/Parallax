import { useCallback, useMemo, useState } from 'react';
import { getDatasetBars } from '../../api/datasets.js';
import { Button } from '../../components/Button.jsx';
import { DataTable } from '../../components/DataTable.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { LoadingState } from '../../components/LoadingState.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';

const PAGE_SIZE = 100;

export function DatasetBarsTable({ datasetId, versionNumber }) {
  const [page, setPage] = useState(0);

  const fetchBars = useCallback(
    (signal) => getDatasetBars(datasetId, versionNumber, signal),
    [datasetId, versionNumber],
  );
  const { data, error, loading, reload } = useApiResource(fetchBars, [datasetId, versionNumber], {
    cacheKey: `/api/datasets/${datasetId}/versions/${versionNumber}/bars`,
  });

  const bars = data?.bars;
  const totalPages = bars ? Math.max(1, Math.ceil(bars.length / PAGE_SIZE)) : 1;
  const pageRows = useMemo(() => {
    if (!bars) return [];
    const start = page * PAGE_SIZE;
    return bars.slice(start, start + PAGE_SIZE);
  }, [bars, page]);

  if (loading && !data) {
    return <LoadingState label="Loading bars…" />;
  }

  if (error) {
    return <ErrorState error={error} title="Could not load bars" onRetry={reload} />;
  }

  if (!bars || bars.length === 0) {
    return <p className="text-sm text-ink-secondary">This version has no bars.</p>;
  }

  return (
    <div>
      <DataTable
        caption={`Bars for version ${versionNumber}`}
        rows={pageRows}
        getRowKey={(row) => row.date}
        columns={[
          { key: 'date', header: 'Date' },
          {
            key: 'open',
            header: 'Open',
            align: 'right',
            render: (row) => <span className="font-mono">{row.open}</span>,
          },
          {
            key: 'high',
            header: 'High',
            align: 'right',
            render: (row) => <span className="font-mono">{row.high}</span>,
          },
          {
            key: 'low',
            header: 'Low',
            align: 'right',
            render: (row) => <span className="font-mono">{row.low}</span>,
          },
          {
            key: 'close',
            header: 'Close',
            align: 'right',
            render: (row) => <span className="font-mono">{row.close}</span>,
          },
          {
            key: 'volume',
            header: 'Volume',
            align: 'right',
            render: (row) => row.volume.toLocaleString(),
          },
        ]}
      />

      <div className="mt-3 flex items-center justify-between text-sm text-ink-secondary">
        <span>
          Page {page + 1} of {totalPages} · {bars.length} bars
        </span>
        <div className="flex gap-1">
          <Button variant="subtle" onClick={() => setPage(0)} disabled={page === 0}>
            First
          </Button>
          <Button variant="subtle" onClick={() => setPage((current) => current - 1)} disabled={page === 0}>
            Previous
          </Button>
          <Button
            variant="subtle"
            onClick={() => setPage((current) => current + 1)}
            disabled={page >= totalPages - 1}
          >
            Next
          </Button>
          <Button variant="subtle" onClick={() => setPage(totalPages - 1)} disabled={page >= totalPages - 1}>
            Last
          </Button>
        </div>
      </div>
    </div>
  );
}
