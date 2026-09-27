package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.Trade;

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

    /**
     * The trades derived from {@link #fills()} (D-34 Batch 3), parsed on
     * every call via {@link Trade#fromFills(List)} — the same engine
     * derivation {@code BacktestResult.trades()} uses (D-24), never a
     * separate backend calculation. {@code BacktestResultReconstructor}
     * already calls this once during read-time integrity verification, so
     * a caller reaching this method on an already-returned {@link
     * BacktestRunDetail} can never see it throw.
     */
    public List<Trade> trades() {
        return Trade.fromFills(fills);
    }
}
