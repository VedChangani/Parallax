import { useCallback } from 'react';
import { Link, Outlet } from 'react-router';
import { getBacktestRun } from '../../api/backtests.js';
import { getDataset } from '../../api/datasets.js';
import { getStrategy } from '../../api/strategies.js';
import { ErrorState } from '../../components/ErrorState.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { Skeleton } from '../../components/Skeleton.jsx';
import { Tabs } from '../../components/Tabs.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';

/**
 * The shared run workspace (D-34 Batch 5 §6/§9/§19): fetches the completed
 * run once - an immutable, integrity-verified historical record, cached
 * like any other completed-run resource (see immutableCache.js) - and
 * establishes the research identity in the page header, then hands it down
 * to Overview/Trades/Rejections via router Outlet context so no child tab
 * ever refetches it. The strategy/dataset name lookups here are the cheap,
 * mutable *parent* resources (`GET /api/strategies/{id}`, `GET
 * /api/datasets/{id}`) purely to label the header - never their
 * StrategyVersion/DatasetVersion (the definition tree or bars), which this
 * batch never reloads merely to render a result.
 *
 * @param {object} props
 * @param {number} props.runId
 */
export function BacktestRunWorkspace({ runId }) {
  const fetchRun = useCallback((signal) => getBacktestRun(runId, signal), [runId]);
  const run = useApiResource(fetchRun, [runId], { cacheKey: `/api/backtest-runs/${runId}` });

  const strategyId = run.data?.strategyId;
  const fetchStrategy = useCallback(
    (signal) => (strategyId !== undefined ? getStrategy(strategyId, signal) : Promise.resolve(undefined)),
    [strategyId],
  );
  const strategy = useApiResource(fetchStrategy, [strategyId]);

  const datasetId = run.data?.datasetId;
  const fetchDataset = useCallback(
    (signal) => (datasetId !== undefined ? getDataset(datasetId, signal) : Promise.resolve(undefined)),
    [datasetId],
  );
  const dataset = useApiResource(fetchDataset, [datasetId]);

  if (run.loading && !run.data) {
    return <WorkspaceSkeleton />;
  }

  if (run.error) {
    return (
      <div>
        <BackLink />
        <ErrorState error={run.error} title="Could not load this backtest run" onRetry={run.reload} />
      </div>
    );
  }

  const detail = run.data;
  const tabs = [
    { to: `/backtests/${runId}`, label: 'Overview', end: true },
    { to: `/backtests/${runId}/trades`, label: 'Trades' },
    { to: `/backtests/${runId}/rejections`, label: 'Rejections' },
  ];

  return (
    <div>
      <BackLink />

      <PageHeader
        title={`Backtest #${runId}`}
        description={`${strategyName(strategy, detail)} · ${marketLabel(dataset, detail)} · Snapshot v${detail.datasetVersion} · Strategy v${detail.strategyVersion} · ${detail.startDate} → ${detail.endDate}`}
      />

      <Tabs tabs={tabs} label="Backtest run sections" />

      <Outlet
        context={{
          run: detail,
          runId,
          marketName: dataset.data?.name,
          marketSymbol: dataset.data?.symbol,
          strategyName: strategy.data?.name,
        }}
      />
    </div>
  );
}

function strategyName(strategy, detail) {
  return strategy.data?.name ?? `Strategy #${detail.strategyId}`;
}

function marketLabel(dataset, detail) {
  return dataset.data?.symbol ?? `Market #${detail.datasetId}`;
}

function BackLink() {
  return (
    <p className="mb-2 text-sm">
      <Link to="/backtests" className="text-ink-secondary hover:text-accent">
        ← Back to backtests
      </Link>
    </p>
  );
}

function WorkspaceSkeleton() {
  return (
    <div aria-busy="true" aria-live="polite" className="space-y-4">
      <span className="sr-only">Loading backtest run…</span>
      <Skeleton className="h-4 w-32" />
      <Skeleton className="h-10 w-96 max-w-full" />
      <Skeleton className="h-6 w-full max-w-xl" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}
