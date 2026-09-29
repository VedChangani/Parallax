package in.vedchangani.parallax.engine.metrics;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.portfolio.Portfolio;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuyAndHoldBenchmarkTest {

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private static Bar bar(LocalDate date, String open, String close) {
        BigDecimal o = n(open);
        BigDecimal c = n(close);
        BigDecimal high = o.max(c);
        BigDecimal low = o.min(c);
        return new Bar(date, o, high, low, c, 0);
    }

    private static Bar flatBar(LocalDate date, String price) {
        return bar(date, price, price);
    }

    private static BarSeries series(String symbol, Bar... bars) {
        return new BarSeries(symbol, List.of(bars));
    }

    private static BacktestConfig config(String capital, String commission, String slippage,
                                          LocalDate start, LocalDate end) {
        return new BacktestConfig(n(capital), n(commission), n(slippage), start, end);
    }

    private static final StrategyDefinition STRATEGY_A = new StrategyDefinition(
            new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(0)),
            new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
            new PositionSizing.CashFraction(BigDecimal.ONE));

    private static final StrategyDefinition STRATEGY_B = new StrategyDefinition(
            new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(1000000)),
            new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(-1000000)),
            new PositionSizing.CashFraction(new BigDecimal("0.5")));

    private static EquityPoint point(LocalDate date, String close) {
        return new EquityPoint(date, n("10000"), 0, BigDecimal.ZERO, BigDecimal.ZERO, n(close));
    }

    private static EquityPoint point(LocalDate date, String close, String capital) {
        return new EquityPoint(date, n(capital), 0, BigDecimal.ZERO, BigDecimal.ZERO, n(close));
    }

    private static BacktestResult result(String symbol, StrategyDefinition strategy, BacktestConfig cfg,
                                          List<EquityPoint> curve) {
        return new BacktestResult(symbol, strategy, cfg, Optional.empty(), curve, List.of(), List.of());
    }

    private static BacktestResult result(BacktestConfig cfg, List<EquityPoint> curve) {
        return result("AAPL", STRATEGY_A, cfg, curve);
    }

    @Test
    void normalBenchmarkWithCommissionAndSlippage() {
        BarSeries s = series("AAPL",
                bar(d(2024, 1, 1), "99", "100"),
                flatBar(d(2024, 1, 2), "110"),
                flatBar(d(2024, 1, 3), "120"));
        BacktestConfig cfg = config("10000", "5", "0.01", d(2024, 1, 1), d(2024, 1, 3));
        List<EquityPoint> curve = List.of(
                point(d(2024, 1, 1), "100"), point(d(2024, 1, 2), "110"), point(d(2024, 1, 3), "120"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        EquityPoint p0 = benchmark.equityCurve().get(0);
        assertEquals(99, p0.quantity());
        assertEquals(0, n("9904.01").compareTo(p0.costBasis()));
        assertEquals(0, n("95.99").compareTo(p0.cash()));
        assertEquals(0, n("9995.99").compareTo(p0.equity()));

        assertEquals(0, n("10985.99").compareTo(benchmark.equityCurve().get(1).equity()));
        assertEquals(0, n("11975.99").compareTo(benchmark.equityCurve().get(2).equity()));

        assertEquals(0.197599, benchmark.totalReturn(), 1e-12);
    }

    @Test
    void regressionSteadilyRisingSeriesActuallyInvests() {
        List<Bar> bars = new ArrayList<>();
        LocalDate date = d(2024, 1, 1);
        for (int price = 50; price <= 99; price++) {
            bars.add(flatBar(date, String.valueOf(price)));
            date = date.plusDays(1);
        }
        BarSeries s = series("AAPL", bars.toArray(new Bar[0]));
        BacktestConfig cfg = config("10000", "0", "0", d(2024, 1, 1), date.minusDays(1));

        List<EquityPoint> curve = new ArrayList<>();
        LocalDate d2 = d(2024, 1, 1);
        for (int price = 50; price <= 99; price++) {
            curve.add(point(d2, String.valueOf(price)));
            d2 = d2.plusDays(1);
        }

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        assertEquals(200, benchmark.equityCurve().get(0).quantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(benchmark.equityCurve().get(0).cash()));
        assertEquals(0.98, benchmark.totalReturn(), 1e-12);
    }

    @Test
    void gapUpIntoFirstInRangeOpenStillBuysAtActualFillPrice() {
        BarSeries s = series("AAPL",
                bar(d(2024, 1, 1), "120", "125"),
                flatBar(d(2024, 1, 2), "130"));
        BacktestConfig cfg = config("10000", "0", "0", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "125"), point(d(2024, 1, 2), "130"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        assertEquals(83, benchmark.equityCurve().get(0).quantity());
        assertEquals(0, n("40").compareTo(benchmark.equityCurve().get(0).cash()));
    }

    @Test
    void gapDownEntryUsesActualLowerOpen() {
        BarSeries s = series("AAPL",
                bar(d(2024, 1, 1), "80", "85"),
                flatBar(d(2024, 1, 2), "90"));
        BacktestConfig cfg = config("10000", "0", "0", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "85"), point(d(2024, 1, 2), "90"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        assertEquals(125, benchmark.equityCurve().get(0).quantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(benchmark.equityCurve().get(0).cash()));
    }

    @Test
    void oneInRangeBarBuysAtOpenAndMarksAtClose() {
        BarSeries s = series("AAPL", bar(d(2024, 1, 1), "100", "105"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 1), d(2024, 1, 1));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "105", "1000"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        assertEquals(1, benchmark.equityCurve().size());
        assertEquals(10, benchmark.equityCurve().get(0).quantity());
        assertEquals(0, n("1050").compareTo(benchmark.equityCurve().get(0).equity()));
        assertEquals(0.05, benchmark.totalReturn(), 1e-12);
    }

    @Test
    void quantityZeroWhenCapitalTooSmall() {
        BarSeries s = series("AAPL", flatBar(d(2024, 1, 1), "100"), flatBar(d(2024, 1, 2), "110"));
        BacktestConfig cfg = config("50", "0", "0", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "100", "50"), point(d(2024, 1, 2), "110", "50"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        for (EquityPoint p : benchmark.equityCurve()) {
            assertEquals(0, p.quantity());
            assertEquals(0, n("50").compareTo(p.cash()));
            assertEquals(0, BigDecimal.ZERO.compareTo(p.costBasis()));
        }
        assertEquals(0.0, benchmark.totalReturn());
    }

    @Test
    void commissionAtLeastCapitalGivesNoTradeAndNoChargedCommission() {
        BarSeries s = series("AAPL", flatBar(d(2024, 1, 1), "50"));
        BacktestConfig cfg = config("100", "100", "0", d(2024, 1, 1), d(2024, 1, 1));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "50", "100"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        EquityPoint p = benchmark.equityCurve().get(0);
        assertEquals(0, p.quantity());
        assertEquals(0, n("100").compareTo(p.cash()));
        assertEquals(0.0, benchmark.totalReturn());
    }

    @Test
    void zeroCommissionAndZeroSlippageExactValues() {
        BarSeries s = series("AAPL", flatBar(d(2024, 1, 1), "100"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 1), d(2024, 1, 1));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "100", "1000"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        EquityPoint p = benchmark.equityCurve().get(0);
        assertEquals(10, p.quantity());
        assertEquals(0, n("1000").compareTo(p.costBasis()));
        assertEquals(0, BigDecimal.ZERO.compareTo(p.cash()));
    }

    @Test
    void lookbackBarsBeforeStartDateAreIgnored() {
        BarSeries s = series("AAPL",
                flatBar(d(2023, 12, 1), "10"),
                bar(d(2024, 1, 1), "100", "105"),
                flatBar(d(2024, 1, 2), "110"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "105", "1000"), point(d(2024, 1, 2), "110", "1000"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        assertEquals(10, benchmark.equityCurve().get(0).quantity());
    }

    @Test
    void barsAfterEndDateAreIgnored() {
        BarSeries s = series("AAPL",
                bar(d(2024, 1, 1), "100", "105"),
                flatBar(d(2024, 1, 2), "110"),
                flatBar(d(2024, 1, 3), "99999"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "105", "1000"), point(d(2024, 1, 2), "110", "1000"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        assertEquals(2, benchmark.equityCurve().size());
    }

    @Test
    void startDateOnNonTradingDayEntersAtFirstInRangeTradingBarOpen() {
        BarSeries s = series("AAPL", bar(d(2024, 1, 8), "100", "105"), flatBar(d(2024, 1, 9), "110"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 6), d(2024, 1, 9));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 8), "105", "1000"), point(d(2024, 1, 9), "110", "1000"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        assertEquals(d(2024, 1, 8), benchmark.equityCurve().get(0).date());
        assertEquals(10, benchmark.equityCurve().get(0).quantity());
    }

    @Test
    void endDateOnNonTradingDayUsesFinalInRangeTradingClose() {
        BarSeries s = series("AAPL", bar(d(2024, 1, 5), "100", "105"), flatBar(d(2024, 1, 6), "108"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 5), d(2024, 1, 7));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 5), "105", "1000"), point(d(2024, 1, 6), "108", "1000"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        assertEquals(2, benchmark.equityCurve().size());
        assertEquals(d(2024, 1, 6), benchmark.equityCurve().get(1).date());
    }

    @Test
    void independentOfStrategyDefinition() {
        BarSeries s = series("AAPL", bar(d(2024, 1, 1), "100", "105"), flatBar(d(2024, 1, 2), "110"));
        BacktestConfig cfg = config("1000", "1", "0.005", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "105", "1000"), point(d(2024, 1, 2), "110", "1000"));

        BuyAndHoldBenchmark a = BuyAndHoldBenchmark.of(s, result("AAPL", STRATEGY_A, cfg, curve));
        BuyAndHoldBenchmark b = BuyAndHoldBenchmark.of(s, result("AAPL", STRATEGY_B, cfg, curve));

        assertEquals(a, b);
    }

    @Test
    void differentSymbolThrowsIae() {
        BarSeries s = series("MSFT", flatBar(d(2024, 1, 1), "100"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 1), d(2024, 1, 1));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "100", "1000"));

        assertThrows(IllegalArgumentException.class, () -> BuyAndHoldBenchmark.of(s, result(cfg, curve)));
    }

    @Test
    void missingInRangeDateThrowsIae() {
        BarSeries s = series("AAPL", flatBar(d(2024, 1, 1), "100"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "100", "1000"), point(d(2024, 1, 2), "110", "1000"));

        assertThrows(IllegalArgumentException.class, () -> BuyAndHoldBenchmark.of(s, result(cfg, curve)));
    }

    @Test
    void extraInRangeDateThrowsIae() {
        BarSeries s = series("AAPL", flatBar(d(2024, 1, 1), "100"), flatBar(d(2024, 1, 2), "110"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "100", "1000"));

        assertThrows(IllegalArgumentException.class, () -> BuyAndHoldBenchmark.of(s, result(cfg, curve)));
    }

    @Test
    void differentCloseThrowsIae() {
        BarSeries s = series("AAPL", flatBar(d(2024, 1, 1), "100"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 1), d(2024, 1, 1));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "999", "1000"));

        assertThrows(IllegalArgumentException.class, () -> BuyAndHoldBenchmark.of(s, result(cfg, curve)));
    }

    @Test
    void nullArgumentsThrowNpe() {
        BarSeries s = series("AAPL", flatBar(d(2024, 1, 1), "100"));
        BacktestConfig cfg = config("1000", "0", "0", d(2024, 1, 1), d(2024, 1, 1));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "100", "1000"));
        BacktestResult r = result(cfg, curve);

        assertThrows(NullPointerException.class, () -> BuyAndHoldBenchmark.of(null, r));
        assertThrows(NullPointerException.class, () -> BuyAndHoldBenchmark.of(s, null));
    }

    @Test
    void emptyCurveThrowsIae() {
        assertThrows(IllegalArgumentException.class, () -> new BuyAndHoldBenchmark(n("1000"), List.of()));
    }

    @Test
    void nonAscendingDatesThrowsIae() {
        List<EquityPoint> curve = List.of(
                new EquityPoint(d(2024, 1, 2), n("1000"), 0, BigDecimal.ZERO, BigDecimal.ZERO, n("100")),
                new EquityPoint(d(2024, 1, 1), n("1000"), 0, BigDecimal.ZERO, BigDecimal.ZERO, n("100")));
        assertThrows(IllegalArgumentException.class, () -> new BuyAndHoldBenchmark(n("1000"), curve));
    }

    @Test
    void inconsistentCashThrowsIae() {
        List<EquityPoint> curve = List.of(
                new EquityPoint(d(2024, 1, 1), n("500"), 5, n("500"), BigDecimal.ZERO, n("100")),
                new EquityPoint(d(2024, 1, 2), n("400"), 5, n("500"), BigDecimal.ZERO, n("110")));
        assertThrows(IllegalArgumentException.class, () -> new BuyAndHoldBenchmark(n("1000"), curve));
    }

    @Test
    void inconsistentQuantityThrowsIae() {
        List<EquityPoint> curve = List.of(
                new EquityPoint(d(2024, 1, 1), n("500"), 5, n("500"), BigDecimal.ZERO, n("100")),
                new EquityPoint(d(2024, 1, 2), n("500"), 6, n("500"), BigDecimal.ZERO, n("110")));
        assertThrows(IllegalArgumentException.class, () -> new BuyAndHoldBenchmark(n("1000"), curve));
    }

    @Test
    void inconsistentCostBasisThrowsIae() {
        List<EquityPoint> curve = List.of(
                new EquityPoint(d(2024, 1, 1), n("500"), 5, n("500"), BigDecimal.ZERO, n("100")),
                new EquityPoint(d(2024, 1, 2), n("500"), 5, n("501"), BigDecimal.ZERO, n("110")));
        assertThrows(IllegalArgumentException.class, () -> new BuyAndHoldBenchmark(n("1000"), curve));
    }

    @Test
    void nonZeroRealizedPnlThrowsIae() {
        List<EquityPoint> curve = List.of(
                new EquityPoint(d(2024, 1, 1), n("500"), 5, n("500"), n("1"), n("100")));
        assertThrows(IllegalArgumentException.class, () -> new BuyAndHoldBenchmark(n("1000"), curve));
    }

    @Test
    void cashPlusCostBasisNotEqualToInitialCapitalThrowsIae() {
        List<EquityPoint> curve = List.of(
                new EquityPoint(d(2024, 1, 1), n("500"), 5, n("500"), BigDecimal.ZERO, n("100")));
        assertThrows(IllegalArgumentException.class, () -> new BuyAndHoldBenchmark(n("999"), curve));
    }

    @Test
    void nonPositiveInitialCapitalThrowsIae() {
        List<EquityPoint> curve = List.of(
                new EquityPoint(d(2024, 1, 1), BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, n("100")));
        assertThrows(IllegalArgumentException.class, () -> new BuyAndHoldBenchmark(BigDecimal.ZERO, curve));
        assertThrows(IllegalArgumentException.class, () -> new BuyAndHoldBenchmark(n("-1"), curve));
    }

    @Test
    void accountingIdentitiesHoldAtEveryPoint() {
        BarSeries s = series("AAPL",
                bar(d(2024, 1, 1), "99", "100"), flatBar(d(2024, 1, 2), "110"), flatBar(d(2024, 1, 3), "120"));
        BacktestConfig cfg = config("10000", "5", "0.01", d(2024, 1, 1), d(2024, 1, 3));
        List<EquityPoint> curve = List.of(
                point(d(2024, 1, 1), "100"), point(d(2024, 1, 2), "110"), point(d(2024, 1, 3), "120"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        for (EquityPoint p : benchmark.equityCurve()) {
            assertEquals(0, p.cash().add(p.costBasis()).compareTo(benchmark.initialCapital()));
            assertEquals(0, p.equity().compareTo(benchmark.initialCapital().add(p.unrealizedPnl())));
        }
    }

    @Test
    void ofIsDeterministic() {
        BarSeries s = series("AAPL", bar(d(2024, 1, 1), "100", "105"), flatBar(d(2024, 1, 2), "110"));
        BacktestConfig cfg = config("1000", "1", "0.005", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "105", "1000"), point(d(2024, 1, 2), "110", "1000"));
        BacktestResult r = result(cfg, curve);

        assertEquals(BuyAndHoldBenchmark.of(s, r), BuyAndHoldBenchmark.of(s, r));
    }

    @Test
    void crossCheckAgainstPortfolioAccounting() {
        BarSeries s = series("AAPL", bar(d(2024, 1, 1), "99", "100"), flatBar(d(2024, 1, 2), "110"));
        BacktestConfig cfg = config("10000", "5", "0.01", d(2024, 1, 1), d(2024, 1, 2));
        List<EquityPoint> curve = List.of(point(d(2024, 1, 1), "100"), point(d(2024, 1, 2), "110"));

        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(s, result(cfg, curve));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(d(2024, 1, 1), n("100"), Map.of());
        SignalEvent enterSignal = new SignalEvent(SignalType.ENTER, snapshot);
        BigDecimal fillPrice = n("99").multiply(new BigDecimal("1.01"));
        Fill fill = new Fill(1, d(2024, 1, 1), 99, n("99"), fillPrice, n("5"), enterSignal);

        Portfolio portfolio = new Portfolio(n("10000"));
        portfolio.apply(fill);

        EquityPoint expected0 = portfolio.markToMarket(d(2024, 1, 1), n("100"));
        EquityPoint expected1 = portfolio.markToMarket(d(2024, 1, 2), n("110"));

        assertEquals(0, expected0.equity().compareTo(benchmark.equityCurve().get(0).equity()));
        assertEquals(0, expected1.equity().compareTo(benchmark.equityCurve().get(1).equity()));
    }
}
