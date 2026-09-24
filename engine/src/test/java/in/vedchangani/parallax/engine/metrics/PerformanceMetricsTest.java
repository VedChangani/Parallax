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

    // --- fixture helpers ---------------------------------------------------

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

    /** A legal {@link EquityPoint} whose equity is exactly zero (cash=0, flat, any positive close). */
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

    // --- total return --------------------------------------------------------

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

    // --- CAGR ------------------------------------------------------------

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

    // --- volatility / Sharpe -----------------------------------------------

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

    // --- drawdown ----------------------------------------------------------

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

    // --- trade statistics ----------------------------------------------------

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
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"), // +100
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"),  // -50
                buy(5, d(2024, 1, 5), "100", "0"), sell(6, d(2024, 1, 6), "130", "0"), // +300
                buy(7, d(2024, 1, 7), "100", "5"), sell(8, d(2024, 1, 8), "101", "5"));  // 0
        PerformanceMetrics m = metricsOfTrades(fills);
        assertEquals(4, m.closedTradeCount());
        assertEquals(0.5, m.winRate().getAsDouble(), 1e-15);
        assertEquals(200.0, m.averageWin().getAsDouble(), 1e-12);
        assertEquals(-50.0, m.averageLoss().getAsDouble(), 1e-12);
    }

    @Test
    void trailingOpenTradeDoesNotAffectClosedStatistics() {
        List<Fill> fills = List.of(
                buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0"), // +100
                buy(3, d(2024, 1, 3), "100", "0"), sell(4, d(2024, 1, 4), "95", "0"),  // -50
                buy(5, d(2024, 1, 5), "100", "0"), sell(6, d(2024, 1, 6), "130", "0"), // +300
                buy(7, d(2024, 1, 7), "100", "5"), sell(8, d(2024, 1, 8), "101", "5"), // 0
                buy(9, d(2024, 1, 9), "100", "0")); // trailing open BUY
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

    // --- preconditions -------------------------------------------------------

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
        // cash=0, flat (quantity=0, costBasis=0) is a legal EquityPoint whose own
        // equity() is exactly zero, even though close is positive — this is exactly
        // the case PerformanceMetrics' second precondition must reject.
        BacktestConfig cfg = config("10000", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "10000"), zeroEquityPoint(d(2024, 1, 2)));
        assertThrows(IllegalArgumentException.class, () -> PerformanceMetrics.of(result(cfg, curve, List.of())));
    }

    // --- record validation ---------------------------------------------------

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
                OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void maxDrawdownNegativeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), -0.0001, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void negativeClosedTradeCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, -1, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void zeroClosedTradeCountIsValid() {
        assertValid(0.0, 0.0, 0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty());
    }

    @Test
    void winRatePresentWithZeroClosedTradesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 0, OptionalDouble.of(0.5),
                OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void winRateEmptyWithNonZeroClosedTradesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty()));
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
                OptionalDouble.empty(), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(-0.0001),
                OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void averageWinMustBePositiveWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(1.0),
                OptionalDouble.of(0.0), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(1.0),
                OptionalDouble.of(-1.0), OptionalDouble.empty()));
    }

    @Test
    void averageLossMustBeNegativeWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(0.0),
                OptionalDouble.empty(), OptionalDouble.of(0.0)));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 1, OptionalDouble.of(0.0),
                OptionalDouble.empty(), OptionalDouble.of(1.0)));
    }

    @Test
    void volatilityMustBeNonNegativeWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.of(-0.0001), OptionalDouble.empty(), 0.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void nonFiniteTotalReturnIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(Double.NaN, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(Double.POSITIVE_INFINITY,
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 0,
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void nonFiniteOptionalValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.of(Double.NaN),
                OptionalDouble.empty(), OptionalDouble.empty(), 0.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(0.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.of(Double.POSITIVE_INFINITY), 0.0, 0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty()));
    }

    @Test
    void nullOptionalComponentIsRejected() {
        assertThrows(NullPointerException.class, () -> new PerformanceMetrics(0.0, null, OptionalDouble.empty(),
                OptionalDouble.empty(), 0.0, 0, OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty()));
    }

    private static void assertValid(double totalReturn, double maxDrawdown, int closedTradeCount,
                                     OptionalDouble winRate, OptionalDouble averageWin, OptionalDouble averageLoss) {
        PerformanceMetrics m = new PerformanceMetrics(totalReturn, OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), maxDrawdown, closedTradeCount, winRate, averageWin, averageLoss);
        assertEquals(totalReturn, m.totalReturn());
        assertEquals(maxDrawdown, m.maxDrawdown());
    }

    // --- numerical safety / documented emptiness ----------------------------

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

    // --- determinism -----------------------------------------------------

    @Test
    void ofIsDeterministicOnTheSameResult() {
        BacktestResult r = result(config("10000", d(2024, 1, 1), d(2024, 1, 3)),
                List.of(point(d(2024, 1, 1), "10000"), point(d(2024, 1, 2), "11000"), point(d(2024, 1, 3), "10500")),
                List.of(buy(1, d(2024, 1, 1), "100", "0"), sell(2, d(2024, 1, 2), "110", "0")));

        assertEquals(PerformanceMetrics.of(r), PerformanceMetrics.of(r));
    }

    // --- consistency via a real Backtester run --------------------------

    private static Bar flatBar(int day, String price) {
        BigDecimal p = new BigDecimal(price);
        return new Bar(d(2024, 1, day), p, p, p, p, 0);
    }

    /**
     * A churning always-enter/always-exit strategy over six bars, sized at
     * whole cash fraction 1 with zero commission/slippage: two round trips
     * (day1->day2 entry filled day2 open, exit filled day3 open; day3->day4
     * entry filled day4 open, exit filled day5 open) and one trailing open
     * position from the day5 entry filled at day6's open (day6 is the last
     * in-range bar, so it is never evaluated and the position stays open).
     * Each entry's next-bar open is at or below its own sizing close, so
     * every BUY stays affordable.
     */
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
