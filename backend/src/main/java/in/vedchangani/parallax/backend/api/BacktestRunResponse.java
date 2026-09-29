package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestRunDetail;
import in.vedchangani.parallax.backend.backtest.BacktestRunSummary;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.result.BacktestConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.util.OptionalDouble;

/**
 * The response shape for one completed, integrity-verified backtest run
 * (D-34 Batch 3) — every {@link java.math.BigDecimal} field as a JSON
 * string via {@code toPlainString()} (the D-30 precedent, preserving the
 * service's own canonical value exactly), every {@code double} metric as a
 * JSON number, and an empty {@link OptionalDouble} as JSON {@code null}.
 * Never the JPA entity, and never a field derived independently of what
 * {@link BacktestRunDetail} already reconstructed and verified.
 */
public record BacktestRunResponse(long id, long strategyId, int strategyVersion, String definitionHash,
                                   long datasetId, int datasetVersion, String contentHash,
                                   int engineSemanticsVersion, String initialCapital, String commissionPerFill,
                                   String slippageRate, LocalDate startDate, LocalDate endDate,
                                   LocalDate firstEvaluableDate, String totalCommission, String totalSlippageCost,
                                   PerformanceMetricsResponse metrics, BenchmarkResponse benchmark,
                                   Instant createdAt) {

    public record PerformanceMetricsResponse(double totalReturn, Double cagr, Double volatility, Double sharpeRatio,
                                              double maxDrawdown, int closedTradeCount, Double winRate,
                                              Double averageWin, Double averageLoss, Double profitFactor) {

        static PerformanceMetricsResponse of(PerformanceMetrics metrics) {
            return new PerformanceMetricsResponse(metrics.totalReturn(), orNull(metrics.cagr()),
                    orNull(metrics.volatility()), orNull(metrics.sharpeRatio()), metrics.maxDrawdown(),
                    metrics.closedTradeCount(), orNull(metrics.winRate()), orNull(metrics.averageWin()),
                    orNull(metrics.averageLoss()), orNull(metrics.profitFactor()));
        }

        private static Double orNull(OptionalDouble value) {
            return value.isPresent() ? value.getAsDouble() : null;
        }
    }

    /**
     * The persisted benchmark reference state (D-34 Batch 1/2): the same
     * {@code cash}/{@code quantity}/{@code costBasis} at every date (the
     * benchmark never sells) plus its overall {@code totalReturn}. Not a
     * reconstructed {@code BuyAndHoldBenchmark} — its full equity curve was
     * never stored (see {@code /equity-curve} for the per-date view).
     */
    public record BenchmarkResponse(String cash, long quantity, String costBasis, double totalReturn) {
    }

    static BacktestRunResponse of(BacktestRunDetail detail) {
        BacktestRunSummary summary = detail.summary();
        BacktestConfig config = detail.config();
        return new BacktestRunResponse(summary.id(), summary.strategyId(), summary.strategyVersionNumber(),
                summary.strategyDefinitionHash(), summary.datasetId(), summary.datasetVersionNumber(),
                summary.datasetContentHash(), summary.engineSemanticsVersion(),
                config.initialCapital().toPlainString(), config.commissionPerFill().toPlainString(),
                config.slippageRate().toPlainString(), config.startDate(), config.endDate(),
                detail.firstEvaluableDate().orElse(null), detail.totalCommission().toPlainString(),
                detail.totalSlippageCost().toPlainString(), PerformanceMetricsResponse.of(detail.metrics()),
                new BenchmarkResponse(detail.benchmarkCash().toPlainString(), detail.benchmarkQuantity(),
                        detail.benchmarkCostBasis().toPlainString(), detail.benchmarkTotalReturn()),
                summary.createdAt());
    }
}
