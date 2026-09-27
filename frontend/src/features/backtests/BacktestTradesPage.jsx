import { useCallback } from 'react';
import { useOutletContext } from 'react-router';
import { getBacktestTrades } from '../../api/backtests.js';
import { ErrorState } from '../../components/ErrorState.jsx';
import { Skeleton } from '../../components/Skeleton.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { TradesTable } from './TradesTable.jsx';

/**
 * The run Trades tab (D-34 Batch 5 §12): loads `GET
 * /api/backtest-runs/{id}/trades` only once this tab is opened, then caches
 * it (a completed run's trades never change).
 */
export function BacktestTradesPage() {
  const { runId } = useOutletContext();

  const fetchTrades = useCallback((signal) => getBacktestTrades(runId, signal), [runId]);
  const trades = useApiResource(fetchTrades, [runId], { cacheKey: `/api/backtest-runs/${runId}/trades` });

  if (trades.loading && !trades.data) {
    return <TableSkeleton label="Loading trades…" />;
  }

  if (trades.error) {
    return <ErrorState error={trades.error} title="Could not load trades" onRetry={trades.reload} />;
  }

  return <TradesTable trades={trades.data} />;
}

function TableSkeleton({ label }) {
  return (
    <div aria-busy="true" aria-live="polite" className="space-y-2">
      <span className="sr-only">{label}</span>
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}
