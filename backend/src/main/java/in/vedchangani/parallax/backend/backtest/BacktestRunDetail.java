package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A {@link BacktestRunSummary} plus every reconstructed, integrity-verified
 * value a completed run holds (D-34 Batch 2), produced only by {@code
 * BacktestRunService#getRun} via {@code BacktestResultReconstructor} —
 * never by trusting stored rows directly.
 *
 * <p>Deliberately holds no engine {@code StrategyDefinition} and no
 * {@code BuyAndHoldBenchmark}: reading a completed run never decodes the
 * original {@code StrategyVersion} (CLAUDE.md — the persisted run is a
 * historical result snapshot), and the benchmark's full equity curve was
 * never stored in the first place (only its reference point and total
 * return) — so the benchmark fields here are the exact scalar values D-34
 * Batch 1 persists, not a reconstructed {@code BuyAndHoldBenchmark} object.
 */
public record BacktestRunDetail(BacktestRunSummary summary, BacktestConfig config,
                                 Optional<LocalDate> firstEvaluableDate, List<EquityPoint> equityCurve,
                                 List<Fill> fills, List<OrderRejection> rejections, PerformanceMetrics metrics,
                                 BigDecimal totalCommission, BigDecimal totalSlippageCost, BigDecimal benchmarkCash,
                                 long benchmarkQuantity, BigDecimal benchmarkCostBasis, double benchmarkTotalReturn) {
}
