import { useCallback } from 'react';
import { useOutletContext } from 'react-router';
import { getBacktestEquity } from '../../api/backtests.js';
import { ErrorState } from '../../components/ErrorState.jsx';
import { Skeleton } from '../../components/Skeleton.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { BenchmarkComparison } from './BenchmarkComparison.jsx';
import { EquityCurve } from './charts/EquityCurve.jsx';
import { PerformanceSummary } from './PerformanceSummary.jsx';
import { ResearchInputsPanel } from './ResearchInputsPanel.jsx';

/**
 * The run Overview tab (D-34 Batch 5 §15): performance summary, equity
 * chart, benchmark comparison, then research inputs, in that order. `run`
 * comes from the parent BacktestRunWorkspace via Outlet context - this
 * page never refetches it. Equity is the one child resource loaded
 * immediately (the chart is the Overview's own centerpiece, per §5's "load
 * when Overview needs it"); Trades/Rejections load only when their own tab
 * is opened.
 */
export function BacktestOverviewPage() {
  const { run, runId, marketName, marketSymbol, strategyName } = useOutletContext();

  const fetchEquity = useCallback((signal) => getBacktestEquity(runId, signal), [runId]);
  const equity = useApiResource(fetchEquity, [runId], { cacheKey: `/api/backtest-runs/${runId}/equity-curve` });

  return (
    <div className="space-y-8">
      <section>
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-wide text-ink-muted">Performance</h2>
        <PerformanceSummary metrics={run.metrics} totalCommission={run.totalCommission} totalSlippageCost={run.totalSlippageCost} />
      </section>

      <section>
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-wide text-ink-muted">Equity curve</h2>
        {equity.loading && !equity.data ? (
          <EquitySkeleton />
        ) : equity.error ? (
          <ErrorState error={equity.error} title="Could not load the equity curve" onRetry={equity.reload} />
        ) : equity.data.length === 0 ? (
          <p className="text-sm text-ink-secondary">No equity points were recorded for this run.</p>
        ) : (
          <EquityCurve points={equity.data} />
        )}
      </section>

      <section>
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-wide text-ink-muted">Benchmark</h2>
        <BenchmarkComparison strategyReturn={run.metrics.totalReturn} benchmarkReturn={run.benchmark.totalReturn} />
      </section>

      <ResearchInputsPanel run={run} marketName={marketName} marketSymbol={marketSymbol} strategyName={strategyName} />
    </div>
  );
}

function EquitySkeleton() {
  return (
    <div aria-busy="true" aria-live="polite">
      <span className="sr-only">Loading equity curve…</span>
      <Skeleton className="h-80 w-full" />
    </div>
  );
}
