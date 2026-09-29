import { useCallback } from 'react';
import { useOutletContext } from 'react-router';
import { getBacktestEquity } from '../../api/backtests.js';
import { getDatasetVersion } from '../../api/datasets.js';
import { ErrorState } from '../../components/ErrorState.jsx';
import { Skeleton } from '../../components/Skeleton.jsx';
import { useApiResource } from '../../hooks/useApiResource.js';
import { AssumptionsPanel } from './AssumptionsPanel.jsx';
import { BenchmarkComparison } from './BenchmarkComparison.jsx';
import { DrawdownChart } from './charts/DrawdownChart.jsx';
import { EquityCurve } from './charts/EquityCurve.jsx';
import { PerformanceSummary } from './PerformanceSummary.jsx';
import { ResearchInputsPanel } from './ResearchInputsPanel.jsx';
import { WarmupNotice } from './WarmupNotice.jsx';

export function BacktestOverviewPage() {
  const { run, runId, marketName, marketSymbol, strategyName } = useOutletContext();

  const fetchEquity = useCallback((signal) => getBacktestEquity(runId, signal), [runId]);
  const equity = useApiResource(fetchEquity, [runId], { cacheKey: `/api/backtest-runs/${runId}/equity-curve` });

  const fetchSnapshot = useCallback(
    (signal) => getDatasetVersion(run.datasetId, run.datasetVersion, signal),
    [run.datasetId, run.datasetVersion],
  );
  const snapshot = useApiResource(fetchSnapshot, [run.datasetId, run.datasetVersion], {
    cacheKey: `/api/datasets/${run.datasetId}/versions/${run.datasetVersion}`,
  });

  const totalBarCount = equity.data ? equity.data.length : undefined;
  const inactiveBarCount = equity.data
    ? run.firstEvaluableDate
      ? equity.data.filter((point) => point.date < run.firstEvaluableDate).length
      : equity.data.length
    : undefined;

  return (
    <div className="space-y-8">
      <WarmupNotice
        startDate={run.startDate}
        firstBarDate={equity.data?.[0]?.date}
        firstEvaluableDate={run.firstEvaluableDate}
        inactiveBarCount={inactiveBarCount}
        totalBarCount={totalBarCount}
      />

      <section>
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-wide text-ink-muted">Performance</h2>
        <PerformanceSummary
          metrics={run.metrics}
          totalCommission={run.totalCommission}
          totalSlippageCost={run.totalSlippageCost}
          returnCount={equity.data ? equity.data.length - 1 : undefined}
        />
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

      {equity.data && equity.data.length > 0 ? (
        <section>
          <h2 className="mb-4 text-xs font-semibold uppercase tracking-wide text-ink-muted">Drawdown</h2>
          <DrawdownChart points={equity.data} />
        </section>
      ) : null}

      <section>
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-wide text-ink-muted">Benchmark</h2>
        <BenchmarkComparison strategyReturn={run.metrics.totalReturn} benchmarkReturn={run.benchmark.totalReturn} />
      </section>

      <AssumptionsPanel snapshot={snapshot.data} />

      <ResearchInputsPanel
        run={run}
        marketName={marketName}
        marketSymbol={marketSymbol}
        strategyName={strategyName}
      />
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
