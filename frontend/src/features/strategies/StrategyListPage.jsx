import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { listStrategies } from '../../api/strategies.js';
import { buttonClasses } from '../../components/buttonStyles.js';
import { EmptyState } from '../../components/EmptyState.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { Skeleton } from '../../components/Skeleton.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { formatInstantDate } from '../../lib/format.js';

/**
 * The Strategies research workspace: a compact, searchable list of every
 * strategy (D-31). `strategies` is a mutable resource - it is never
 * immutable-cached (a strategy's `latestVersionNumber` changes as new
 * versions are created) and there is no backend search endpoint, so the
 * search box filters the already-fetched list client-side.
 */
export function StrategyListPage() {
  const [query, setQuery] = useState('');

  const fetchList = useCallback((signal) => listStrategies(signal), []);
  const { data: strategies, error, loading, reload } = useApiResource(fetchList, []);

  const filtered = useMemo(() => {
    if (!strategies) return [];
    const q = query.trim().toLowerCase();
    if (!q) return strategies;
    return strategies.filter(
      (strategy) => strategy.name.toLowerCase().includes(q) || strategy.description.toLowerCase().includes(q),
    );
  }, [strategies, query]);

  return (
    <div>
      <PageHeader
        title="Strategies"
        description="Build, version, and evaluate systematic trading rules."
        actions={
          <Link to="/strategies/new" className={buttonClasses('primary')}>
            New strategy
          </Link>
        }
      />

      {loading && !strategies ? <ListSkeleton /> : null}
      {error ? <ErrorState error={error} title="Could not load strategies" onRetry={reload} /> : null}
      {!error && strategies ? (
        strategies.length === 0 ? (
          <EmptyState
            title="No strategies yet"
            description="Define a structured entry/exit rule set and position sizing to begin researching a strategy."
            action={
              <Link to="/strategies/new" className={buttonClasses('primary')}>
                New strategy
              </Link>
            }
          />
        ) : (
          <>
            <div className="mb-4">
              <label htmlFor="strategy-search" className="sr-only">
                Search strategies
              </label>
              <input
                id="strategy-search"
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search strategies…"
                className="w-full max-w-sm rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30 sm:w-72"
              />
            </div>

            {filtered.length === 0 ? (
              <EmptyState title="No strategies match your search." />
            ) : (
              <ul className="divide-y divide-border overflow-hidden rounded-md border border-border bg-surface">
                {filtered.map((strategy) => (
                  <li key={strategy.id}>
                    <Link
                      to={`/strategies/${strategy.id}`}
                      className="group flex items-center justify-between gap-6 px-4 py-3.5 transition-colors duration-150 hover:bg-surface-hover"
                    >
                      <div className="min-w-0">
                        <div className="font-semibold text-ink">{strategy.name}</div>
                        {strategy.description ? (
                          <p className="mt-0.5 truncate text-sm text-ink-secondary">{strategy.description}</p>
                        ) : null}
                        <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-muted">
                          <span>Latest v{strategy.latestVersionNumber}</span>
                          <span>Created {formatInstantDate(strategy.createdAt)}</span>
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
      {Array.from({ length: 4 }).map((_, index) => (
        <div key={index} className="flex items-center gap-6 border-b border-border p-4 last:border-b-0">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-4 w-56" />
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}
