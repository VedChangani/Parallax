package in.vedchangani.parallax.engine.metrics;

import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.result.Trade;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceMetricsTest {

    private static final StrategyDefinition STRATEGY = new StrategyDefinition(
            new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(0)),
            new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
            new PositionSizing.CashFraction(BigDecimal.ONE));

    private static IndicatorSnapshot snapshot(LocalDate date) {
        return new IndicatorSnapshot(date, new BigDecimal("100"), Map.of());
    }

    private static final SignalEvent ENTER = new SignalEvent(SignalType.ENTER, snapshot(LocalDate.of(2024, 1, 1)));
    private static final SignalEvent EXIT = new SignalEvent(SignalType.EXIT, snapshot(LocalDate.of(2024, 1, 2)));

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static BacktestConfig config(String capital, LocalDate start, LocalDate end) {
        return new BacktestConfig(new BigDecimal(capital), BigDecimal.ZERO, BigDecimal.ZERO, start, end);
    }

    private static EquityPoint point(LocalDate date, String equity) {
        BigDecimal e = new BigDecimal(equity);
        return new EquityPoint(date, e, 0, BigDecimal.ZERO, BigDecimal.ZERO, e);
    }

    private static EquityPoint zeroEquityPoint(LocalDate date) {
        return new EquityPoint(date, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"));
    }

    private static Fill buy(int orderId, LocalDate date, String price, String commission) {
        BigDecimal p = new BigDecimal(price);
        return new Fill(orderId, date, 10, p, p, new BigDecimal(commission), ENTER);
    }

    private static Fill sell(int orderId, LocalDate date, String price, String commission) {
        BigDecimal p = new BigDecimal(price);
        return new Fill(orderId, date, 10, p, p, new BigDecimal(commission), EXIT);
    }

    private static BacktestResult result(BacktestConfig cfg, List<EquityPoint> curve, List<Fill> fills) {
        return new BacktestResult("AAPL", STRATEGY, cfg, Optional.empty(), curve, fills, List.of());
    }

    private static PerformanceMetrics metricsOf(List<EquityPoint> curve) {
        BacktestConfig cfg = config(curve.get(0).equity().toPlainString(), curve.get(0).date(),
                curve.get(curve.size() - 1).date());
        return PerformanceMetrics.of(result(cfg, curve, List.of()));
    }

    private static PerformanceMetrics metricsOfTrades(List<Fill> fills) {
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "10000"));
        BacktestConfig cfg = config("10000", d(2024, 1, 1), d(2024, 1, 2));
        return PerformanceMetrics.of(result(cfg, curve, fills));
    }

    @Test
    void totalReturnTenPercentGain() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "11000")));
        assertEquals(0.1, m.totalReturn(), 1e-15);
    }

    @Test
    void totalReturnTenPercentLoss() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "9000")));
        assertEquals(-0.1, m.totalReturn(), 1e-15);
    }

    @Test
    void totalReturnFlatIsExactlyZero() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "10000")));
        assertEquals(0.0, m.totalReturn());
    }

    @Test
    void cagrExactlyOneYear() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2023, 1, 3), "10000"), point(d(2024, 1, 3), "12100")));
        assertTrue(m.cagr().isPresent());
        assertEquals(0.21, m.cagr().getAsDouble(), 1e-12);
    }

    @Test
    void cagrTwoYearsGain() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2021, 1, 4), "10000"), point(d(2023, 1, 4), "12100")));
        assertTrue(m.cagr().isPresent());
        assertEquals(0.10, m.cagr().getAsDouble(), 1e-12);
    }

    @Test
    void cagrTwoYearsLoss() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2021, 1, 4), "10000"), point(d(2023, 1, 4), "8100")));
        assertTrue(m.cagr().isPresent());
        assertEquals(-0.10, m.cagr().getAsDouble(), 1e-12);
    }

    @Test
    void cagrUnderOneYearIsEmpty() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2023, 1, 1), "10000"), point(d(2023, 12, 31), "12100")));
        assertTrue(m.cagr().isEmpty());
    }

    @Test
    void cagrSameDayIsEmpty() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2024, 1, 1), "10000")));
        assertTrue(m.cagr().isEmpty());
    }

    @Test
    void volatilityAndSharpeGainThenFlat() {
        PerformanceMetrics m = metricsOf(List.of(
                point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "11000"), point(d(2024, 1, 3), "11000")));
        assertTrue(m.volatility().isPresent());
        assertEquals(StrictMath.sqrt(1.26), m.volatility().getAsDouble(), 1e-12);
        assertTrue(m.sharpeRatio().isPresent());
        assertEquals(StrictMath.sqrt(126), m.sharpeRatio().getAsDouble(), 1e-12);
    }

    @Test
    void volatilityAndSharpeGainThenLoss() {
        PerformanceMetrics m = metricsOf(List.of(
                point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "11000"), point(d(2024, 1, 3), "9900")));
        assertTrue(m.volatility().isPresent());
        assertEquals(StrictMath.sqrt(5.04), m.volatility().getAsDouble(), 1e-12);
        assertTrue(m.sharpeRatio().isPresent());
        assertEquals(0.0, m.sharpeRatio().getAsDouble(), 1e-15);
    }

    @Test
    void constantReturnsGiveExactlyZeroVolatilityAndEmptySharpe() {
        PerformanceMetrics m = metricsOf(List.of(
                point(d(2024, 1, 1), "1000"), point(d(2024, 1, 2), "1100"),
                point(d(2024, 1, 3), "1210"), point(d(2024, 1, 4), "1331")));
        assertTrue(m.volatility().isPresent());
        assertEquals(0.0, m.volatility().getAsDouble());
        assertTrue(m.sharpeRatio().isEmpty());
    }

    @Test
    void flatCurveGivesZeroVolatilityAndEmptySharpe() {
        PerformanceMetrics m = metricsOf(List.of(
                point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "10000"), point(d(2024, 1, 3), "10000")));
        assertTrue(m.volatility().isPresent());
        assertEquals(0.0, m.volatility().getAsDouble());
        assertTrue(m.sharpeRatio().isEmpty());
    }

    @Test
    void onePointGivesEmptyVolatilityAndSharpe() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2024, 1, 1), "10000")));
        assertTrue(m.volatility().isEmpty());
        assertTrue(m.sharpeRatio().isEmpty());
    }

    @Test
    void twoPointsGiveEmptyVolatilityAndSharpe() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "11000")));
        assertTrue(m.volatility().isEmpty());
        assertTrue(m.sharpeRatio().isEmpty());
    }

    @Test
    void risingEquityGivesZeroDrawdown() {
        PerformanceMetrics m = metricsOf(List.of(
                point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "11000"), point(d(2024, 1, 3), "12000")));
        assertEquals(0.0, m.maxDrawdown());
    }

    @Test
    void oneDrawdownWithRecovery() {
        PerformanceMetrics m = metricsOf(List.of(
                point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "12000"),
                point(d(2024, 1, 3), "9000"), point(d(2024, 1, 4), "10000")));
        assertEquals(0.25, m.maxDrawdown(), 1e-15);
    }

    @Test
    void multipleDrawdownsKeepTheDeepest() {
        PerformanceMetrics m = metricsOf(List.of(
                point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "12000"), point(d(2024, 1, 3), "10800"),
                point(d(2024, 1, 4), "13000"), point(d(2024, 1, 5), "9100"), point(d(2024, 1, 6), "14000")));
        assertEquals(0.30, m.maxDrawdown(), 1e-12);
    }

    @Test
    void immediateLossDrawdown() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "8000")));
        assertEquals(0.20, m.maxDrawdown(), 1e-15);
    }

    @Test
    void singlePointGivesZeroDrawdown() {
        PerformanceMetrics m = metricsOf(List.of(point(d(2024, 1, 1), "10000")));
        assertEquals(0.0, m.maxDrawdown());
    }

    @Test
    void noFillsGivesEmptyTradeStatistics() {
        PerformanceMetrics m = metricsOfTrades(List.of());
        assertEquals(0, m.closedTradeCount());
        assertTrue(m.winRate().isEmpty());
        assertTrue(m.averageWin().isEmpty());
        assertTrue(m.averageLoss().isEmpty());
    }

    @Test
    void oneWinningClosedTrade() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"),
                sell(2, d(2024, 1, 2), "110", "0"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(1, m.closedTradeCount());
        assertEquals(1.0, m.winRate().getAsDouble());
        assertEquals(100.0, m.averageWin().getAsDouble(), 1e-12);
        assertTrue(m.averageLoss().isEmpty());
    }

    @Test
    void oneLosingClosedTrade() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"),
                sell(2, d(2024, 1, 2), "95", "0"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(1, m.closedTradeCount());
        assertEquals(0.0, m.winRate().getAsDouble());
        assertEquals(-50.0, m.averageLoss().getAsDouble(), 1e-12);
        assertTrue(m.averageWin().isEmpty());
    }

    @Test
    void oneBreakevenClosedTrade() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "5"),
                sell(2, d(2024, 1, 2), "101", "5"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(1, m.closedTradeCount());
        assertEquals(0.0, m.winRate().getAsDouble());
        assertTrue(m.averageWin().isEmpty());
        assertTrue(m.averageLoss().isEmpty());
    }

    @Test
    void mixedWinsLossesAndBreakeven() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"),
                buy(5, d(2024, 1, 5), "100", "0"), sell(6, d(2024, 1, 6), "130", "0"),
                buy(7, d(2024, 1, 7), "100", "5"), sell(8, d(2024, 1, 8), "101", "5"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(4, m.closedTradeCount());
        assertEquals(0.5, m.winRate().getAsDouble(), 1e-15);
        assertEquals(200.0, m.averageWin().getAsDouble(), 1e-12);
        assertEquals(-50.0, m.averageLoss().getAsDouble(), 1e-12);
    }

    @Test
    void trailingOpenTradeDoesNotAffectClosedStatistics() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"),
                buy(5, d(2024, 1, 5), "100", "0"), sell(6, d(2024, 1, 6), "130", "0"),
                buy(7, d(2024, 1, 7), "100", "5"), sell(8, d(2024, 1, 8), "101", "5"),
                buy(9, d(2024, 1, 9), "100", "0"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(4, m.closedTradeCount());
        assertEquals(0.5, m.winRate().getAsDouble(), 1e-15);
        assertEquals(200.0, m.averageWin().getAsDouble(), 1e-12);
        assertEquals(-50.0, m.averageLoss().getAsDouble(), 1e-12);
    }

    @Test
    void onlyAnOpenTradeGivesEmptyTradeStatistics() {
        List<Fill> fills = List.of(buy(1, d(2024, 1, 1), "100", "0"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(0, m.closedTradeCount());
        assertTrue(m.winRate().isEmpty());
        assertTrue(m.averageWin().isEmpty());
        assertTrue(m.averageLoss().isEmpty());
    }

    @Test
    void profitFactorIsGrossProfitOverAbsoluteGrossLoss() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"),
                buy(5, d(2024, 1, 5), "100", "0"), sell(6, d(2024, 1, 6), "130", "0"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(8.0, m.profitFactor().getAsDouble(), 1e-15);
    }

    @Test
    void profitFactorBelowOneWhenLossesOutweighWins() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "105", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "90", "0"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(0.5, m.profitFactor().getAsDouble(), 1e-15);
    }

    @Test
    void onlyWinningTradesGiveUnavailableProfitFactorNotInfinity() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "120", "0"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertTrue(m.profitFactor().isEmpty());
    }

    @Test
    void onlyLosingTradesGiveAProfitFactorOfExactlyZero() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "95", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "97", "0"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertTrue(m.profitFactor().isPresent());
        assertEquals(0.0, m.profitFactor().getAsDouble());
    }

    @Test
    void breakevenTradesContributeToNeitherSideOfProfitFactor() {
        List<Fill> withoutBreakeven = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"));
        List<Fill> withBreakeven = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"),
                buy(5, d(2024, 1, 5), "100", "5"), sell(6, d(2024, 1, 6), "101", "5"));
        assertEquals(2.0, metricsOfTrades(withoutBreakeven).profitFactor().getAsDouble(), 1e-15);
        assertEquals(2.0, metricsOfTrades(withBreakeven).profitFactor().getAsDouble(), 1e-15);
    }

    @Test
    void aBreakevenTradeAloneGivesUnavailableProfitFactor() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "5"),
                sell(2, d(2024, 1, 2), "101", "5"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(1, m.closedTradeCount());
        assertTrue(m.profitFactor().isEmpty());
    }

    @Test
    void openTradeIsIgnoredByProfitFactor() {
        List<Fill> closedOnly = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"));
        List<Fill> withOpen = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"),
                buy(5, d(2024, 1, 5), "100", "0"));
        assertEquals(metricsOfTrades(closedOnly).profitFactor(), metricsOfTrades(withOpen).profitFactor());
        assertEquals(2.0, metricsOfTrades(withOpen).profitFactor().getAsDouble(), 1e-15);
    }

    @Test
    void onlyAnOpenTradeOrNoTradesGiveUnavailableProfitFactor() {
        assertTrue(metricsOfTrades(List.of()).profitFactor().isEmpty());
        assertTrue(metricsOfTrades(List.of(buy(1, d(2024, 1, 1), "100", "0"))).profitFactor().isEmpty());
    }

    @Test
    void profitFactorSumsAreExactNotFloatingPoint() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100.00", "0"), sell(2, d(2024, 1, 2), "100.01", "0"),
                buy(3, d(2024, 1, 3), "100.00", "0"), sell(4, d(2024, 1, 4), "100.02", "0"),
                buy(5, d(2024, 1, 5), "100.00", "0"), sell(6, d(2024, 1, 6), "99.97", "0"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(1.0, m.profitFactor().getAsDouble());
    }

    @Test
    void profitFactorDoesNotChangeAnyOtherTradeMetric() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"),
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"),
                buy(5, d(2024, 1, 5), "100", "0"), sell(6, d(2024, 1, 6), "130", "0"),
                buy(7, d(2024, 1, 7), "100", "5"), sell(8, d(2024, 1, 8), "101", "5"));
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(4, m.closedTradeCount());
        assertEquals(0.5, m.winRate().getAsDouble(), 1e-15);
        assertEquals(200.0, m.averageWin().getAsDouble(), 1e-12);
        assertEquals(-50.0, m.averageLoss().getAsDouble(), 1e-12);
        assertEquals(8.0, m.profitFactor().getAsDouble(), 1e-15);
    }

    @Test
    void profitFactorMustBeNonNegativeAndFiniteWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(0.0),
                OptionalDouble.empty(), OptionalDouble.of(-1.0), OptionalDouble.of(-0.5)));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(0.0),
                OptionalDouble.empty(), OptionalDouble.of(-1.0), OptionalDouble.of(Double.POSITIVE_INFINITY)));
        assertThrows(NullPointerException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), null));
    }

    @Test
    void profitFactorIsPresentExactlyWhenAverageLossIsPresent() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(1.0),
                OptionalDouble.of(1.0), OptionalDouble.empty(), OptionalDouble.of(2.0)));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(0.0),
                OptionalDouble.empty(), OptionalDouble.of(-1.0), OptionalDouble.empty()));
    }

    @Test
    void drawdownSeriesIsAlignedWithTheCurveAndUsesTheRunningPeak() {
        List<EquityPoint> curve = List.of(
                point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "12000"), point(d(2024, 1, 3), "9000"),
                point(d(2024, 1, 4), "9600"), point(d(2024, 1, 5), "13000"));
        double[] series = PerformanceMetrics.drawdownSeries(curve);

        assertEquals(curve.size(), series.length);
        assertEquals(0.0, series[0]);
        assertEquals(0.0, series[1]);
        assertEquals(0.25, series[2], 1e-15);
        assertEquals(0.20, series[3], 1e-15);
        assertEquals(0.0, series[4]);
    }

    @Test
    void drawdownSeriesMaximumIsExactlyMaxDrawdown() {
        List<EquityPoint> curve = List.of(
                point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "12345.67"), point(d(2024, 1, 3), "8765.43"),
                point(d(2024, 1, 4), "11000"), point(d(2024, 1, 5), "9999.99"));
        double max = 0.0;
        for (double dd : PerformanceMetrics.drawdownSeries(curve)) {
            max = Math.max(max, dd);
        }
        assertEquals(metricsOf(curve).maxDrawdown(), max);
    }

    @Test
    void drawdownSeriesOfRisingOrSinglePointCurveIsAllZero() {
        assertEquals(0.0, PerformanceMetrics.drawdownSeries(List.of(point(d(2024, 1, 1), "10000")))[0]);
        double[] rising = PerformanceMetrics.drawdownSeries(List.of(
                point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "10100"), point(d(2024, 1, 3), "10100")));
        assertEquals(0.0, rising[0]);
        assertEquals(0.0, rising[1]);
        assertEquals(0.0, rising[2]);
    }

    @Test
    void drawdownSeriesRejectsNullAndEmptyCurves() {
        assertThrows(NullPointerException.class, () -> PerformanceMetrics.drawdownSeries(null));
        assertThrows(IllegalArgumentException.class, () -> PerformanceMetrics.drawdownSeries(List.of()));
    }

    @Test
    void nullResultThrowsNpe() {
        assertThrows(NullPointerException.class, () -> PerformanceMetrics.of(null));
    }

    @Test
    void firstEquityNotEqualToInitialCapitalThrowsIae() {
        BacktestConfig cfg = config("10000", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "9000"), point(d(2024, 1, 2), "10000"));
        assertThrows(IllegalArgumentException.class, () -> PerformanceMetrics.of(result(cfg, curve, List.of())));
    }

    @Test
    void zeroEquityPointThrowsIae() {
        BacktestConfig cfg = config("10000", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "10000"), zeroEquityPoint(d(2024, 1, 2)));
        assertThrows(IllegalArgumentException.class, () -> PerformanceMetrics.of(result(cfg, curve, List.of())));
    }

    @Test
    void maxDrawdownBoundaryZeroIsValid() {
        assertValid(0.0, 0.0, 0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty());
    }

    @Test
    void maxDrawdownJustBelowOneIsValid() {
        assertValid(0.0, 0.999999, 0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty());
    }

    @Test
    void maxDrawdownOfOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 1.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void maxDrawdownNegativeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), -0.0001, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void negativeClosedTradeCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, -1, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void zeroClosedTradeCountIsValid() {
        assertValid(0.0, 0.0, 0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty());
    }

    @Test
    void winRatePresentWithZeroClosedTradesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 0, OptionalDouble.of(0.5),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void winRateEmptyWithNonZeroClosedTradesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void winRateBoundariesZeroAndOneAreValid() {
        assertValid(0.0, 0.0, 1, OptionalDouble.of(0.0), OptionalDouble.empty(), OptionalDouble.of(-1.0));
        assertValid(0.0, 0.0, 1, OptionalDouble.of(1.0), OptionalDouble.of(1.0), OptionalDouble.empty());
    }

    @Test
    void winRateOutsideRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(1.0001),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(-0.0001),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void averageWinMustBePositiveWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(1.0),
                OptionalDouble.of(0.0), OptionalDouble.empty(), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(1.0),
                OptionalDouble.of(-1.0), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void averageLossMustBeNegativeWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(0.0),
                OptionalDouble.empty(), OptionalDouble.of(0.0), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(0.0),
                OptionalDouble.empty(), OptionalDouble.of(1.0), OptionalDouble.empty()));
    }

    @Test
    void volatilityMustBeNonNegativeWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.of(-0.0001), OptionalDouble.empty(), 0.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void nonFiniteTotalReturnIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(Double.NaN, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(Double.POSITIVE_INFINITY,
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 0,
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void nonFiniteOptionalValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.of(Double.NaN),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.of(Double.POSITIVE_INFINITY), 0.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void nullOptionalComponentIsRejected() {
        assertThrows(NullPointerException.class, () -> new PerformanceMetrics(0.0, null, OptionalDouble.empty(),
                OptionalDouble.empty(), 0.0, 0, OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty()));
    }

    private static void assertValid(double totalReturn, double maxDrawdown, int closedTradeCount,
                                     OptionalDouble winRate, OptionalDouble averageWin, OptionalDouble averageLoss) {
        PerformanceMetrics m = new PerformanceMetrics(totalReturn, OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), maxDrawdown, closedTradeCount, winRate, averageWin, averageLoss,
                averageLoss.isPresent() ? OptionalDouble.of(1.0) : OptionalDouble.empty());
        assertEquals(totalReturn, m.totalReturn());
        assertEquals(maxDrawdown, m.maxDrawdown());
    }

    @Test
    void noNaNOrInfinityInAnyMetric() {
        PerformanceMetrics m = metricsOf(List.of(
                point(d(2023, 1, 3), "10000"), point(d(2023, 6, 1), "11000"), point(d(2024, 1, 3), "12100")));
        assertFalse(Double.isNaN(m.totalReturn()) || Double.isInfinite(m.totalReturn()));
        assertFalse(Double.isNaN(m.maxDrawdown()) || Double.isInfinite(m.maxDrawdown()));
        m.cagr().ifPresent(v -> assertFalse(Double.isNaN(v) || Double.isInfinite(v)));
        m.volatility().ifPresent(v -> assertFalse(Double.isNaN(v) || Double.isInfinite(v)));
        m.sharpeRatio().ifPresent(v -> assertFalse(Double.isNaN(v) || Double.isInfinite(v)));
    }

    @Test
    void ofIsDeterministicOnTheSameResult() {
        BacktestResult r = result(config("10000", d(2024, 1, 1), d(2024, 1, 3)),
                List.of(point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "11000"), point(d(2024, 1, 3), "10500")),
                List.of(buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0")));

        assertEquals(PerformanceMetrics.of(r), PerformanceMetrics.of(r));
    }

    private static Bar flatBar(int day, String price) {
        BigDecimal p = new BigDecimal(price);
        return new Bar(d(2024, 1, day), p, p, p, p, 0);
    }

    private static BacktestResult multiTradeResult() {
        BarSeries series = new BarSeries("TEST", List.of(
                flatBar(1, "100"), flatBar(2, "90"), flatBar(3, "110"),
                flatBar(4, "100"), flatBar(5, "130"), flatBar(6, "125")));
        StrategyDefinition strategy = new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(0)),
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(-1)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
        BacktestConfig cfg = new BacktestConfig(new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO,
                d(2024, 1, 1), d(2024, 1, 6));
        return new Backtester().run(series, strategy, cfg);
    }

    @Test
    void closedTradeCountMatchesClosedTradesInResult() {
        BacktestResult result = multiTradeResult();
        PerformanceMetrics m = PerformanceMetrics.of(result);

        long closedInResult = result.trades().stream().filter(t -> t instanceof Trade.Closed).count();
        assertEquals(closedInResult, m.closedTradeCount());
        assertTrue(m.closedTradeCount() > 0);
    }

    @Test
    void closedPlusOpenCountEqualsTotalTrades() {
        BacktestResult result = multiTradeResult();
        List<Trade> trades = result.trades();

        long closed = trades.stream().filter(t -> t instanceof Trade.Closed).count();
        long open = trades.stream().filter(t -> t instanceof Trade.Open).count();
        assertEquals(trades.size(), closed + open);
        assertTrue(open > 0);
    }

    @Test
    void sumOfClosedRealizedPnlMatchesFinalRealizedPnl() {
        BacktestResult result = multiTradeResult();

        BigDecimal sum = result.trades().stream()
                .filter(t -> t instanceof Trade.Closed)
                .map(t -> ((Trade.Closed) t).realizedPnl())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, sum.compareTo(result.finalPoint().realizedPnl()));
    }

    @Test
    void totalReturnAgreesWithEquityFigures() {
        BacktestResult result = multiTradeResult();
        PerformanceMetrics m = PerformanceMetrics.of(result);

        BigDecimal expected = result.finalPoint().equity().subtract(result.config().initialCapital());
        double expectedReturn = expected.doubleValue() / result.config().initialCapital().doubleValue();
        assertEquals(expectedReturn, m.totalReturn(), 1e-15);
    }

    @Test
    void ofIsDeterministicOnRealBacktesterResult() {
        BacktestResult result = multiTradeResult();
        assertEquals(PerformanceMetrics.of(result), PerformanceMetrics.of(result));
    }

    @Test
    void ofIsDeterministicAcrossIdenticalResultInstances() {
        BacktestConfig cfg = config("10000", d(2024, 1, 1), d(2024, 1, 3));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "11000"),
                point(d(2024, 1, 3), "10500"));
        List<Fill> fills = List.of(buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"));

        BacktestResult r1 = result(cfg, curve, fills);
        BacktestResult r2 = result(cfg, curve, fills);

        assertEquals(PerformanceMetrics.of(r1), PerformanceMetrics.of(r2));
    }
}
