package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.metrics.BuyAndHoldBenchmark;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.result.BacktestResult;

import java.util.Objects;

/**
 * Everything {@link BacktestRunWriter} needs to persist one completed run,
 * already computed in memory before any write transaction opens (D-34
 * Batch 2 §4-5) — a pure parameter holder, package-private since only
 * {@link BacktestRunService} produces one and only {@link BacktestRunWriter}
 * consumes it.
 */
record BacktestRunWriteRequest(long ownerId, long strategyId, int strategyVersionNumber,
                                String strategyDefinitionHash, long datasetId, int datasetVersionNumber,
                                String datasetContentHash, int engineSemanticsVersion, BacktestResult result,
                                PerformanceMetrics metrics, BuyAndHoldBenchmark benchmark) {

    BacktestRunWriteRequest {
        Objects.requireNonNull(strategyDefinitionHash, "strategyDefinitionHash must not be null");
        Objects.requireNonNull(datasetContentHash, "datasetContentHash must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(benchmark, "benchmark must not be null");
    }
}
