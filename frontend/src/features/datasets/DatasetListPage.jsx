import { useCallback, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { listDatasets } from '../../api/datasets.js';
import { Button } from '../../components/Button.jsx';
import { EmptyState } from '../../components/EmptyState.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { Skeleton } from '../../components/Skeleton.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { formatInstantDate } from '../../lib/format.js';
import { DatasetCreateForm } from './DatasetCreateForm.jsx';

/**
 * The Markets workspace - the frontend's user-facing name for the backend's
 * `Dataset` resource (D-32). Lists every market, supports a client-side
 * search over the already-fetched list (the backend has no search
 * endpoint), and creates new markets inline via "Add market".
 */
export function DatasetListPage() {
  const [creating, setCreating] = useState(false);
  const [query, setQuery] = useState('');
  const navigate = useNavigate();

  const fetchList = useCallback((signal) => listDatasets(signal), []);
  const { data: markets, error, loading, reload } = useApiResource(fetchList, []);

  const filtered = useMemo(() => {
    if (!markets) return [];
    const q = query.trim().toLowerCase();
    if (!q) return markets;
    return markets.filter((market) => market.symbol.toLowerCase().includes(q) || market.name.toLowerCase().includes(q));
  }, [markets, query]);

  function handleCreated(dataset, { importError } = {}) {
    setCreating(false);
    navigate(`/datasets/${dataset.id}`, importError ? { state: { importError } } : undefined);
  }

  return (
    <div>
      <PageHeader
        title="Markets"
        description="Historical market data available for research and backtesting."
        actions={
          <Button variant="primary" onClick={() => setCreating((open) => !open)} aria-expanded={creating}>
            {creating ? 'Cancel' : 'Add market'}
          </Button>
        }
      />

      {creating ? (
        <div className="mb-8 rounded-md border border-border bg-surface p-5">
          <h2 className="text-sm font-semibold text-ink">Add market</h2>
          <p className="mt-1 text-sm text-ink-secondary">
            Bring one symbol's historical data into Parallax - load it from Alpha Vantage now, or add the market
            first and load data later.
          </p>
          <DatasetCreateForm onCreated={handleCreated} className="mt-4" />
        </div>
      ) : null}

      {loading && !markets ? <ListSkeleton /> : null}
      {error ? <ErrorState error={error} title="Could not load markets" onRetry={reload} /> : null}
      {!error && markets ? (
        markets.length === 0 ? (
          <EmptyState
            title="No markets yet"
            description="Bring a market into Parallax to begin researching strategies."
            action={
              <div className="text-left">
                <ol className="space-y-1 text-sm text-ink-secondary">
                  <li>→ Load historical daily data from Alpha Vantage</li>
                  <li>→ Preserve an immutable snapshot</li>
                  <li>→ Use it in a backtest</li>
                </ol>
                <Button variant="primary" onClick={() => setCreating(true)} className="mt-4">
                  Add market
                </Button>
              </div>
            }
          />
        ) : (
          <>
            <div className="mb-4">
              <label htmlFor="market-search" className="sr-only">
                Search markets
              </label>
              <input
                id="market-search"
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search markets…"
                className="w-full max-w-sm rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30 sm:w-72"
              />
            </div>

            {filtered.length === 0 ? (
              <EmptyState title="No markets match your search." />
            ) : (
              <ul className="divide-y divide-border overflow-hidden rounded-md border border-border bg-surface">
                {filtered.map((market) => (
                  <li key={market.id}>
                    <Link
                      to={`/datasets/${market.id}`}
                      className="group flex items-center justify-between gap-6 px-4 py-3.5 transition-colors duration-150 hover:bg-surface-hover"
                    >
                      <div className="min-w-0">
                        <div className="flex items-baseline gap-2">
                          <span className="font-mono text-sm font-semibold text-ink">{market.symbol}</span>
                          <span className="truncate text-sm text-ink-secondary">{market.name}</span>
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-muted">
                          <span>
                            Latest snapshot{' '}
                            {market.latestVersionNumber > 0 ? `v${market.latestVersionNumber}` : 'none yet'}
                          </span>
                          <span>Added {formatInstantDate(market.createdAt)}</span>
                        </div>
                      </div>
                      <span className="shrink-0 text-sm font-medium text-accent group-hover:underline">Open →</span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </>
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
