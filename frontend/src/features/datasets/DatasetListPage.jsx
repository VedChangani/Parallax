import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { listDatasets } from '../../api/datasets.js';
import { Button } from '../../components/Button.jsx';
import { DataTable } from '../../components/DataTable.jsx';
import { EmptyState } from '../../components/EmptyState.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { Skeleton } from '../../components/Skeleton.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { formatInstantDate } from '../../lib/format.js';
import { DatasetCreateForm } from './DatasetCreateForm.jsx';

/** The Dataset workspace: list every owned dataset, and create new ones inline. */
export function DatasetListPage() {
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();

  const fetchList = useCallback((signal) => listDatasets(signal), []);
  const { data: datasets, error, loading, reload } = useApiResource(fetchList, []);

  function handleCreated(dataset) {
    setCreating(false);
    navigate(`/datasets/${dataset.id}`);
  }

  return (
    <div>
      <PageHeader
        title="Datasets"
        description="Historical market-data snapshots available to backtest against."
        actions={
          <Button variant="primary" onClick={() => setCreating((open) => !open)} aria-expanded={creating}>
            {creating ? 'Cancel' : 'New dataset'}
          </Button>
        }
      />

      {creating ? (
        <div className="mb-8 rounded-md border border-border bg-surface p-5">
          <h2 className="text-sm font-semibold text-ink">New dataset</h2>
          <p className="mt-1 text-sm text-ink-secondary">
            A dataset holds one symbol's bars across however many imported versions you give it.
          </p>
          <DatasetCreateForm onCreated={handleCreated} className="mt-4" />
        </div>
      ) : null}

      {loading && !datasets ? <ListSkeleton /> : null}
      {error ? <ErrorState error={error} title="Could not load datasets" onRetry={reload} /> : null}
      {!error && datasets ? (
        datasets.length === 0 ? (
          <EmptyState
            title="No datasets yet"
            description="A dataset is a named, versioned collection of daily bars for one symbol - imported from a CSV file or Alpha Vantage. Create one to get started."
          />
        ) : (
          <DataTable
            caption="Datasets"
            rows={datasets}
            getRowKey={(row) => row.id}
            columns={[
              {
                key: 'name',
                header: 'Name',
                render: (row) => (
                  <Link to={`/datasets/${row.id}`} className="font-medium text-ink hover:text-accent">
                    {row.name}
                  </Link>
                ),
              },
              {
                key: 'symbol',
                header: 'Symbol',
                render: (row) => <span className="font-mono text-ink-secondary">{row.symbol}</span>,
              },
              {
                key: 'latestVersionNumber',
                header: 'Latest version',
                align: 'right',
                render: (row) => (row.latestVersionNumber > 0 ? `v${row.latestVersionNumber}` : '—'),
              },
              {
                key: 'createdAt',
                header: 'Created',
                render: (row) => formatInstantDate(row.createdAt),
              },
            ]}
          />
        )
      ) : null}
    </div>
  );
}

function ListSkeleton() {
  return (
    <div className="overflow-hidden rounded-md border border-border bg-surface">
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={index} className="flex items-center gap-6 border-b border-border p-4 last:border-b-0">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-4 w-16" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-24" />
        </div>
      ))}
    </div>
  );
}
