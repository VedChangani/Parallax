import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { listBacktestRuns } from '../../api/backtests.js';
import { listDatasets } from '../../api/datasets.js';
import { listStrategies } from '../../api/strategies.js';
import { buttonClasses } from '../../components/buttonStyles.js';
import { DataTable } from '../../components/DataTable.jsx';
import { EmptyState } from '../../components/EmptyState.jsx';
import { ErrorState } from '../../components/ErrorState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { Skeleton } from '../../components/Skeleton.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { formatInstantDate, formatSignedPercent } from '../../lib/format.js';

const COLUMNS = [
  {
    key: 'run',
    header: 'Run',
    render: (run) => (
      <Link to={`/backtests/${run.id}`} className="font-mono text-sm font-semibold text-ink hover:text-accent hover:underline">
        #{run.id}
      </Link>
    ),
  },
  {
    key: 'market',
    header: 'Market',
    render: (run) => (
      <div>
        <div className="font-medium text-ink">{run.marketLabel}</div>
        <div className="text-xs text-ink-muted">Snapshot v{run.datasetVersion}</div>
      </div>
    ),
  },
  {
    key: 'strategy',
    header: 'Strategy',
    render: (run) => (
      <div>
        <div className="font-medium text-ink">{run.strategyLabel}</div>
        <div className="text-xs text-ink-muted">Version v{run.strategyVersion}</div>
      </div>
    ),
  },
  {
    key: 'period',
    header: 'Period',
    render: (run) => (
      <div className="tabular-nums text-xs text-ink-secondary">
        {run.startDate} → {run.endDate}
      </div>
    ),
  },
  {
    key: 'return',
    header: 'Return',
    render: (run) => (
      <div className="tabular-nums">
        <div className={`font-semibold ${returnTone(run.totalReturn)}`}>{formatSignedPercent(run.totalReturn)}</div>
        <div className={`text-xs ${returnTone(run.benchmarkTotalReturn)}`}>
          B&amp;H {formatSignedPercent(run.benchmarkTotalReturn)}
        </div>
      </div>
    ),
  },
  { key: 'created', header: 'Created', render: (run) => formatInstantDate(run.createdAt) },
  {
    key: 'technical',
    header: 'Technical',
    render: (run) => (
      <details className="text-xs">
        <summary className="cursor-pointer select-none text-ink-muted hover:text-ink">Detail</summary>
        <dl className="mt-1 space-y-0.5 font-mono text-ink-secondary">
          <div>engine v{run.engineSemanticsVersion}</div>
          <div className="truncate" title={run.definitionHash}>
            strategy {run.definitionHash.slice(0, 12)}…
          </div>
          <div className="truncate" title={run.contentHash}>
            dataset {run.contentHash.slice(0, 12)}…
          </div>
        </dl>
      </details>
    ),
  },
  {
    key: 'action',
    header: '',
    render: (run) => (
      <Link to={`/backtests/${run.id}`} className="text-sm font-medium text-accent hover:underline">
        Open run →
      </Link>
    ),
  },
];

function returnTone(value) {
  if (value > 0) return 'text-success';
  if (value < 0) return 'text-danger';
  return 'text-ink';
}

/**
 * The Backtests research history/workspace (D-34 Batch 6, extended by
 * Phase 9 Batch 1 / I8): a compact, searchable table of every completed
 * run. `GET /api/backtest-runs` is a cheap, owner-scoped list - identity/
 * version references plus (I8) each run's own date range and returns, read
 * directly off its parent row - so this page never fetches an individual
 * run's equity/fill/rejection children merely to enrich a row.
 *
 * Market/strategy *names* still are not part of that list response (they
 * are mutable parent-resource labels, not part of a run's historical
 * identity), so this page resolves them from the existing `GET
 * /api/datasets`/`GET /api/strategies` list endpoints - one request per
 * resource *type*, never one per row - and falls back to the authoritative
 * `Market #id`/`Strategy #id` reference when a name is not (yet) known.
 * The true historical identity remains id + version + hash, always shown
 * in the Technical detail; a resolved name is a current, mutable label,
 * not a claim about what the run itself recorded.
 */
export function BacktestHistoryPage() {
  const [query, setQuery] = useState('');

  const fetchRuns = useCallback((signal) => listBacktestRuns(signal), []);
  const { data: runs, error, loading, reload } = useApiResource(fetchRuns, []);

  // Mutable resources (a dataset/strategy's own list can grow or its name
  // can change at any time) - never cached, exactly like every other
  // mutable list in the app (CLAUDE.md: never cache a mutable resource).
  const datasets = useApiResource(listDatasets, []);
  const strategies = useApiResource(listStrategies, []);

  const datasetById = useMemo(() => new Map((datasets.data ?? []).map((d) => [d.id, d])), [datasets.data]);
  const strategyById = useMemo(() => new Map((strategies.data ?? []).map((s) => [s.id, s])), [strategies.data]);

  // N1: sort by the numeric run id, not a string comparison of createdAt.
  // The backend already guarantees ascending-id order matches creation
  // order (BacktestRunService#listRuns: an IDENTITY column, inserted once,
  // never reordered) - a numeric id compare is exact and format-independent,
  // where a lexicographic compare of a timestamp string is not.
  const enrichedAndSorted = useMemo(() => {
    if (!runs) return [];
    return runs
      .map((run) => ({
        ...run,
        marketLabel: datasetById.get(run.datasetId)?.symbol ?? `Market #${run.datasetId}`,
        strategyLabel: strategyById.get(run.strategyId)?.name ?? `Strategy #${run.strategyId}`,
      }))
      .sort((a, b) => b.id - a.id);
  }, [runs, datasetById, strategyById]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return enrichedAndSorted;
    return enrichedAndSorted.filter((run) =>
      [run.id, run.strategyId, run.strategyVersion, run.datasetId, run.datasetVersion, run.engineSemanticsVersion].some(
        (field) => String(field).toLowerCase().includes(q),
      ),
    );
  }, [enrichedAndSorted, query]);

  return (
    <div>
      <PageHeader
        title="Backtests"
        description="Historical research runs and their results."
        actions={
          <Link to="/backtests/new" className={buttonClasses('primary')}>
            New backtest
          </Link>
        }
      />

      {loading && !runs ? <HistorySkeleton /> : null}
      {error ? <ErrorState error={error} title="Could not load backtests" onRetry={reload} /> : null}
      {!error && runs ? (
        runs.length === 0 ? (
          <EmptyState
            title="No backtests yet"
            description="Run a strategy against a market to start your research history."
            action={
              <Link to="/backtests/new" className={buttonClasses('primary')}>
                New backtest
              </Link>
            }
          />
        ) : (
          <>
            <div className="mb-4">
              <label htmlFor="backtest-search" className="sr-only">
                Search backtests
              </label>
              <input
                id="backtest-search"
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search runs…"
                className="w-full max-w-sm rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30 sm:w-72"
              />
            </div>

            {filtered.length === 0 ? (
              <EmptyState title="No runs match your search." />
            ) : (
              <DataTable columns={COLUMNS} rows={filtered} getRowKey={(run) => run.id} caption="Backtest runs" />
            )}
          </>
        )
      ) : null}
    </div>
  );
}

function HistorySkeleton() {
  return (
    <div aria-busy="true" aria-live="polite" className="overflow-hidden rounded-md border border-border bg-surface">
      <span className="sr-only">Loading backtests…</span>
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={index} className="flex items-center gap-6 border-b border-border p-4 last:border-b-0">
          <Skeleton className="h-4 w-12" />
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-4 w-24" />
        </div>
      ))}
    </div>
  );
}
