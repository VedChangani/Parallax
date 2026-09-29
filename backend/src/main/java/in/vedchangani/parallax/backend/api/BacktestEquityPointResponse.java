package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestResultIntegrityException;
import in.vedchangani.parallax.backend.backtest.BacktestRunDetail;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
            throw new BacktestResultIntegrityException(
                    "stored benchmark state failed integrity verification: " + e.getMessage(), e);
        }

        return new BacktestEquityPointResponse(point.date(), point.cash().toPlainString(), point.quantity(),
                point.costBasis().toPlainString(), point.realizedPnl().toPlainString(), point.close().toPlainString(),
                point.marketValue().toPlainString(), point.equity().toPlainString(),
                point.unrealizedPnl().toPlainString(), benchmarkPoint.equity().toPlainString(), drawdown);
    }
}
