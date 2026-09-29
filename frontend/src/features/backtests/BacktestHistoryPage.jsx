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

export function BacktestHistoryPage() {
  const [query, setQuery] = useState('');

  const fetchRuns = useCallback((signal) => listBacktestRuns(signal), []);
  const { data: runs, error, loading, reload } = useApiResource(fetchRuns, []);

  const datasets = useApiResource(listDatasets, []);
  const strategies = useApiResource(listStrategies, []);

  const datasetById = useMemo(() => new Map((datasets.data ?? []).map((d) => [d.id, d])), [datasets.data]);
  const strategyById = useMemo(() => new Map((strategies.data ?? []).map((s) => [s.id, s])), [strategies.data]);

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
