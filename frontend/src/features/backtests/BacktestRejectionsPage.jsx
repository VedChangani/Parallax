import { useCallback } from 'react';
import { useOutletContext } from 'react-router';
import { getBacktestRejections } from '../../api/backtests.js';
import { ErrorState } from '../../components/ErrorState.jsx';
import { Skeleton } from '../../components/Skeleton.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { RejectionsTable } from './RejectionsTable.jsx';

export function BacktestRejectionsPage() {
  const { runId } = useOutletContext();

  const fetchRejections = useCallback((signal) => getBacktestRejections(runId, signal), [runId]);
  const rejections = useApiResource(fetchRejections, [runId], { cacheKey: `/api/backtest-runs/${runId}/rejections` });

  if (rejections.loading && !rejections.data) {
    return <TableSkeleton label="Loading rejections…" />;
  }

  if (rejections.error) {
    return <ErrorState error={rejections.error} title="Could not load rejections" onRetry={rejections.reload} />;
  }

  return <RejectionsTable rejections={rejections.data} />;
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
