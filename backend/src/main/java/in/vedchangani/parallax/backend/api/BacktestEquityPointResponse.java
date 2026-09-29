package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestResultIntegrityException;
import in.vedchangani.parallax.backend.backtest.BacktestRunDetail;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The response shape for one persisted, integrity-verified {@link
 * EquityPoint} of a completed run (D-34 Batch 3), plus its passive
 * buy-and-hold {@code benchmarkEquity} for the same date. {@code
 * marketValue}/{@code equity}/{@code unrealizedPnl} are read directly off
 * {@link EquityPoint}'s own derived methods — never recomputed here.
 *
 * <p>{@code benchmarkEquity} reuses the exact D-28 buy-and-hold
 * representation established in D-34 Batch 1/2: the benchmark shares this
 * point's own date and close (D-28 — the benchmark marks to the same
 * underlying bar), so a "virtual" {@link EquityPoint} built from the run's
 * stored constant benchmark {@code cash}/{@code quantity}/{@code costBasis}
 * plus this point's {@code close} is exactly what {@code
 * BuyAndHoldBenchmark}'s own equity curve would hold at this date — its
 * {@link EquityPoint#equity()} is used unchanged, not a new formula.
 *
 * <p>{@code drawdown} is this point's drawdown from the running peak equity,
 * a fraction {@code >= 0} (e.g. {@code 0.25} = 25% below peak; {@code 0.0}
 * at a new high), from the engine's own {@link
 * PerformanceMetrics#drawdownSeries(List)} — the same definition {@code
 * maxDrawdown} is the maximum of. Derived on read from the verified equity
 * curve; it is a JSON number like the other derived statistics (D-26), and
 * is never stored.
 */
public record BacktestEquityPointResponse(LocalDate date, String cash, long quantity, String costBasis,
                                           String realizedPnl, String close, String marketValue, String equity,
                                           String unrealizedPnl, String benchmarkEquity, double drawdown) {

    static List<BacktestEquityPointResponse> listOf(BacktestRunDetail detail) {
        List<EquityPoint> curve = detail.equityCurve();
        List<BacktestEquityPointResponse> result = new ArrayList<>(curve.size());
        if (curve.isEmpty()) {
            return result;
        }
        double[] drawdowns = PerformanceMetrics.drawdownSeries(curve);
        for (int i = 0; i < curve.size(); i++) {
            result.add(of(curve.get(i), detail, drawdowns[i]));
        }
        return result;
    }

    private static BacktestEquityPointResponse of(EquityPoint point, BacktestRunDetail detail, double drawdown) {
        EquityPoint benchmarkPoint;
        try {
            benchmarkPoint = new EquityPoint(point.date(), detail.benchmarkCash(), detail.benchmarkQuantity(),
                    detail.benchmarkCostBasis(), BigDecimal.ZERO, point.close());
        } catch (IllegalArgumentException e) {
            // The stored benchmark cash/quantity/costBasis triple failed EquityPoint's
            // own structural invariant - stored-data corruption, not a client error.
            throw new BacktestResultIntegrityException(
                    "stored benchmark state failed integrity verification: " + e.getMessage(), e);
        }

        return new BacktestEquityPointResponse(point.date(), point.cash().toPlainString(), point.quantity(),
                point.costBasis().toPlainString(), point.realizedPnl().toPlainString(), point.close().toPlainString(),
                point.marketValue().toPlainString(), point.equity().toPlainString(),
                point.unrealizedPnl().toPlainString(), benchmarkPoint.equity().toPlainString(), drawdown);
    }
}
