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

public record BacktestRunDetail(BacktestRunSummary summary, BacktestConfig config,
                                 Optional<LocalDate> firstEvaluableDate, List<EquityPoint> equityCurve,
                                 List<Fill> fills, List<OrderRejection> rejections, PerformanceMetrics metrics,
                                 BigDecimal totalCommission, BigDecimal totalSlippageCost, BigDecimal benchmarkCash,
                                 long benchmarkQuantity, BigDecimal benchmarkCostBasis, double benchmarkTotalReturn) {

    public List<Trade> trades() {
        return Trade.fromFills(fills);
    }
}
