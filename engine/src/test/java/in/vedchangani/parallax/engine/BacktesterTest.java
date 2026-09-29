package in.vedchangani.parallax.engine;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.result.Trade;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktesterTest {

    // --- fixture helpers -------------------------------------------------

    private static LocalDate date(int day) {
        return LocalDate.of(2024, 1, day);
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static Bar bar(int day, String open, String high, String low, String close) {
        return new Bar(date(day), d(open), d(high), d(low), d(close), 0);
    }

    private static Bar flatBar(int day, String price) {
        return bar(day, price, price, price, price);
    }

    private static BarSeries series(Bar... bars) {
        return new BarSeries("TEST", List.of(bars));
    }

    private static Condition closeAbove(double threshold) {
        return new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(threshold));
    }

    private static Condition indicatorAbove(IndicatorSpec spec, double threshold) {
        return new Condition.Compare(new Operand.IndicatorRef(spec), Operator.GT, new Operand.Constant(threshold));
    }

    private static StrategyDefinition strategy(Condition entry, Condition exit) {
        return new StrategyDefinition(entry, exit, new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    /** Always-flat-to-long and always-long-to-flat conditions (any positive close satisfies both). */
    private static final Condition ALWAYS_ENTER = closeAbove(0);
    private static final Condition ALWAYS_EXIT = closeAbove(-1);

    private static BacktestConfig config(String capital, String commission, String slippage,
                                          LocalDate start, LocalDate end) {
        return new BacktestConfig(d(capital), d(commission), d(slippage), start, end);
    }

    private static void assertIdentities(EquityPoint point, BigDecimal initialCapital) {
        assertEquals(0, point.equity().compareTo(point.cash().add(point.marketValue())));
        assertEquals(0, point.equity().compareTo(
                initialCapital.add(point.realizedPnl()).add(point.unrealizedPnl())));
    }

    // --- basic / range -----------------------------------------------------

    @Test
    void oneBarNoIndicatorRun() {
        BarSeries s = series(flatBar(1, "100"));
        StrategyDefinition strat = strategy(ALWAYS_ENTER, ALWAYS_EXIT);
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(1));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(1, result.equityCurve().size());
        assertEquals(0, result.equityCurve().get(0).equity().compareTo(d("1000")));
        assertEquals(List.of(), result.fills());
        assertEquals(List.of(), result.rejections());
        assertEquals(List.of(), result.trades());
        assertTrue(result.firstEvaluableDate().isPresent());
    }

    @Test
    void sameDayRangeIsValidAndNotEvaluated() {
        BarSeries s = series(flatBar(5, "50"));
        BacktestConfig cfg = config("1000", "5", "0", date(5), date(5));

        BacktestResult result = new Backtester().run(s, strategy(ALWAYS_ENTER, ALWAYS_EXIT), cfg);

        assertEquals(1, result.equityCurve().size());
        assertEquals(List.of(), result.fills());
    }

    @Test
    void lookbackBarsProduceNoEquityOrActivity() {
        BarSeries s = series(flatBar(1, "10"), flatBar(2, "20"), flatBar(3, "30"));
        BacktestConfig cfg = config("1000", "5", "0", date(3), date(3));

        BacktestResult result = new Backtester().run(s, strategy(ALWAYS_ENTER, ALWAYS_EXIT), cfg);

        assertEquals(1, result.equityCurve().size());
        assertEquals(date(3), result.equityCurve().get(0).date());
        assertEquals(List.of(), result.fills());
        assertEquals(List.of(), result.rejections());
    }

    @Test
    void barsAfterEndDateAreIgnoredAndCannotEstablishReadiness() {
        IndicatorSpec sma5 = new IndicatorSpec(IndicatorType.SMA, 5);
        // Only 3 in-range bars: SMA(5) never becomes ready from them alone.
        // 5 trailing bars after endDate would make it ready if wrongly processed.
        BarSeries s = series(
                flatBar(1, "10"), flatBar(2, "20"), flatBar(3, "30"),
                flatBar(4, "40"), flatBar(5, "50"), flatBar(6, "60"), flatBar(7, "70"), flatBar(8, "80"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(3));
        StrategyDefinition strat = strategy(indicatorAbove(sma5, 1e9), indicatorAbove(sma5, 1e9));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(3, result.equityCurve().size());
        assertTrue(result.firstEvaluableDate().isEmpty());
    }

    @Test
    void noInRangeBarThrows() {
        BarSeries s = series(flatBar(1, "10"), flatBar(2, "20"));
        BacktestConfig cfg = config("1000", "5", "0", date(10), date(20));

        assertThrows(IllegalArgumentException.class,
                () -> new Backtester().run(s, strategy(ALWAYS_ENTER, ALWAYS_EXIT), cfg));
    }

    @Test
    void nullArgumentsRejected() {
        BarSeries s = series(flatBar(1, "10"));
        StrategyDefinition strat = strategy(ALWAYS_ENTER, ALWAYS_EXIT);
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(1));
        Backtester backtester = new Backtester();

        assertThrows(NullPointerException.class, () -> backtester.run(null, strat, cfg));
        assertThrows(NullPointerException.class, () -> backtester.run(s, null, cfg));
        assertThrows(NullPointerException.class, () -> backtester.run(s, strat, null));
    }

    // --- warm-up -----------------------------------------------------------

    @Test
    void smaWarmUpAndFirstEvaluableDate() {
        IndicatorSpec sma3 = new IndicatorSpec(IndicatorType.SMA, 3);
        BarSeries s = series(flatBar(1, "10"), flatBar(2, "11"), flatBar(3, "12"), flatBar(4, "13"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(4));
        StrategyDefinition strat = strategy(indicatorAbove(sma3, 1e9), indicatorAbove(sma3, 1e9));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(date(3), result.firstEvaluableDate().orElseThrow());
        assertEquals(4, result.equityCurve().size());
    }

    @Test
    void emaWarmUpAndFirstEvaluableDate() {
        IndicatorSpec ema2 = new IndicatorSpec(IndicatorType.EMA, 2);
        BarSeries s = series(flatBar(1, "10"), flatBar(2, "20"), flatBar(3, "30"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(3));
        StrategyDefinition strat = strategy(indicatorAbove(ema2, 1e9), indicatorAbove(ema2, 1e9));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(date(2), result.firstEvaluableDate().orElseThrow());
    }

    @Test
    void rsiWarmUpAndFirstEvaluableDate() {
        IndicatorSpec rsi2 = new IndicatorSpec(IndicatorType.RSI, 2);
        BarSeries s = series(flatBar(1, "100"), flatBar(2, "101"), flatBar(3, "102"), flatBar(4, "103"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(4));
        StrategyDefinition strat = strategy(indicatorAbove(rsi2, 1e9), indicatorAbove(rsi2, 1e9));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(date(3), result.firstEvaluableDate().orElseThrow());
    }

    @Test
    void atrWarmUpAndFirstEvaluableDate() {
        IndicatorSpec atr2 = new IndicatorSpec(IndicatorType.ATR, 2);
        BarSeries s = series(flatBar(1, "100"), flatBar(2, "101"), flatBar(3, "102"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(3));
        StrategyDefinition strat = strategy(indicatorAbove(atr2, 1e9), indicatorAbove(atr2, 1e9));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(date(2), result.firstEvaluableDate().orElseThrow());
    }

    @Test
    void rocWarmUpAndFirstEvaluableDate() {
        IndicatorSpec roc2 = new IndicatorSpec(IndicatorType.ROC, 2);
        BarSeries s = series(flatBar(1, "100"), flatBar(2, "101"), flatBar(3, "102"), flatBar(4, "103"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(4));
        StrategyDefinition strat = strategy(indicatorAbove(roc2, 1e9), indicatorAbove(roc2, 1e9));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(date(3), result.firstEvaluableDate().orElseThrow());
    }

    // ATR must receive each bar's high/low (not just the close): ATR(1) is
    // the true range, 3 on day 1 and 5 on day 2 (gap up from close 11 to
    // high 16), so ATR(1) > 4 first holds on day 2 and fills at day 3's open.
    @Test
    void atrStrategyReceivesHighAndLowFromBacktester() {
        IndicatorSpec atr1 = new IndicatorSpec(IndicatorType.ATR, 1);
        BarSeries s = series(
                bar(1, "10", "12", "9", "11"),
                bar(2, "15", "16", "14", "15"),
                bar(3, "15", "16", "14", "15"));
        BacktestConfig cfg = config("1000", "0", "0", date(1), date(3));
        StrategyDefinition strat = strategy(indicatorAbove(atr1, 4), indicatorAbove(atr1, 1e9));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(1, result.fills().size());
        assertEquals(date(3), result.fills().get(0).date());
    }

    @Test
    void neverReadyGivesEmptyFirstEvaluableDate() {
        IndicatorSpec sma5 = new IndicatorSpec(IndicatorType.SMA, 5);
        BarSeries s = series(flatBar(1, "10"), flatBar(2, "20"), flatBar(3, "30"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(3));
        StrategyDefinition strat = strategy(indicatorAbove(sma5, 1e9), indicatorAbove(sma5, 1e9));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertTrue(result.firstEvaluableDate().isEmpty());
    }

    @Test
    void readinessDuringLookbackGivesFirstInRangeBar() {
        IndicatorSpec sma3 = new IndicatorSpec(IndicatorType.SMA, 3);
        // 3 lookback bars warm up SMA(3); range starts on bar 4.
        BarSeries s = series(flatBar(1, "10"), flatBar(2, "20"), flatBar(3, "30"), flatBar(4, "40"));
        BacktestConfig cfg = config("1000", "5", "0", date(4), date(4));
        StrategyDefinition strat = strategy(indicatorAbove(sma3, 1e9), indicatorAbove(sma3, 1e9));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(date(4), result.firstEvaluableDate().orElseThrow());
    }

    // --- ENTER / EXIT / open-position timing (Cases C, D, H) --------------

    @Test
    void enterExitReEnterLeftOpen() {
        BarSeries s = series(
                flatBar(1, "100"),
                bar(2, "100", "110", "100", "110"),
                bar(3, "112", "112", "112", "112"),
                bar(4, "112", "112", "112", "112"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(4));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(105));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        List<Fill> fills = result.fills();
        assertEquals(3, fills.size());

        Fill fill1 = fills.get(0);
        assertEquals(1, fill1.orderId());
        assertEquals(date(2), fill1.date());
        assertEquals(9, fill1.quantity());
        assertEquals(0, fill1.fillPrice().compareTo(d("100")));

        Fill fill2 = fills.get(1);
        assertEquals(2, fill2.orderId());
        assertEquals(date(3), fill2.date());
        assertEquals(9, fill2.quantity());
        assertEquals(0, fill2.fillPrice().compareTo(d("112")));

        Fill fill3 = fills.get(2);
        assertEquals(3, fill3.orderId());
        assertEquals(date(4), fill3.date());
        assertEquals(9, fill3.quantity());

        // Equity timing, per bar.
        List<EquityPoint> curve = result.equityCurve();
        assertEquals(0, curve.get(0).equity().compareTo(d("1000"))); // d1: flat before any fill
        assertEquals(0, curve.get(1).equity().compareTo(d("1085"))); // d2: after fill1, marked to 110
        assertEquals(0, curve.get(2).equity().compareTo(d("1098"))); // d3: after fill2 (SELL), marked to 112
        assertEquals(0, curve.get(3).equity().compareTo(d("1093"))); // d4: after fill3, marked to 112

        for (EquityPoint point : curve) {
            assertIdentities(point, d("1000"));
        }

        // Trade reconciliation.
        List<Trade> trades = result.trades();
        assertEquals(2, trades.size());
        assertInstanceOf(Trade.Closed.class, trades.get(0));
        Trade.Closed closed = (Trade.Closed) trades.get(0);
        assertEquals(0, closed.realizedPnl().compareTo(d("98")));
        assertInstanceOf(Trade.Open.class, trades.get(1)); // final position remains open

        assertEquals(0, result.finalPoint().realizedPnl().compareTo(closed.realizedPnl()));
    }

    // --- final-bar semantics: execution happens, evaluation does not -----

    @Test
    void pendingOrderFromPriorBarExecutesAtFinalBarOpen() {
        BarSeries s = series(flatBar(1, "100"), flatBar(2, "100"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(2));
        // Exit would be true at d2 close too, but d2 is the final bar: no evaluation.
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(0));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(1, result.fills().size());
        Fill fill = result.fills().get(0);
        assertEquals(1, fill.orderId());
        assertEquals(date(2), fill.date());
        assertEquals(date(1), fill.signal().date());

        assertEquals(List.of(), result.rejections());
        assertEquals(1, result.trades().size());
        assertInstanceOf(Trade.Open.class, result.trades().get(0));
    }

    @Test
    void finalBarCloseCreatesNoNewSignalOrderOrRejection() {
        BarSeries s = series(flatBar(1, "100"), bar(2, "115", "115", "115", "115"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(2));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(0));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(1, result.rejections().size());
        OrderRejection rejection = result.rejections().get(0);
        assertInstanceOf(OrderRejection.InsufficientCash.class, rejection);
        OrderRejection.InsufficientCash insufficientCash = (OrderRejection.InsufficientCash) rejection;
        assertEquals(1, insufficientCash.orderId());
        assertEquals(date(2), insufficientCash.date());
        assertEquals(date(1), insufficientCash.signal().date());

        assertEquals(List.of(), result.fills());
    }

    // --- ZeroQuantity: no ID consumed (Case F) -----------------------------

    @Test
    void zeroQuantityConsumesNoOrderId() {
        BarSeries s = series(flatBar(1, "100"), bar(2, "50", "50", "50", "50"), flatBar(3, "50"));
        BacktestConfig cfg = config("100", "5", "0", date(1), date(3));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(0));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(1, result.rejections().size());
        assertInstanceOf(OrderRejection.ZeroQuantity.class, result.rejections().get(0));
        assertEquals(date(1), result.rejections().get(0).date());

        assertEquals(1, result.fills().size());
        assertEquals(1, result.fills().get(0).orderId()); // the first real order still gets id 1
    }

    // --- InsufficientCash keeps its ID; next order gets the next one (Case G) --

    @Test
    void insufficientCashKeepsIdAndNextOrderGetsNextId() {
        BarSeries s = series(
                flatBar(1, "100"),
                bar(2, "115", "115", "115", "115"),
                flatBar(3, "115"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(3));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(0));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(1, result.rejections().size());
        assertInstanceOf(OrderRejection.InsufficientCash.class, result.rejections().get(0));
        OrderRejection.InsufficientCash rejection = (OrderRejection.InsufficientCash) result.rejections().get(0);
        assertEquals(1, rejection.orderId());

        assertEquals(1, result.fills().size());
        assertEquals(2, result.fills().get(0).orderId());
        assertEquals(8, result.fills().get(0).quantity());
    }

    // --- exact affordability boundary --------------------------------------

    @Test
    void requiredEqualsAvailableProducesFillNotRejection() {
        BarSeries s = series(flatBar(1, "99"), flatBar(2, "99"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(2));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(0));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(List.of(), result.rejections());
        assertEquals(1, result.fills().size());
        Fill fill = result.fills().get(0);
        assertEquals(10, fill.quantity());

        EquityPoint finalPoint = result.finalPoint();
        assertEquals(0, finalPoint.cash().compareTo(d("5")));
        assertEquals(10, finalPoint.quantity());
        assertEquals(0, finalPoint.costBasis().compareTo(d("995")));
    }

    @Test
    void justAboveAffordabilityBoundaryRejects() {
        BarSeries s = series(flatBar(1, "99"), bar(2, "99.001", "99.001", "99.001", "99.001"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(2));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(0));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        assertEquals(List.of(), result.fills());
        assertEquals(1, result.rejections().size());
        assertInstanceOf(OrderRejection.InsufficientCash.class, result.rejections().get(0));
    }

    // --- D-23 reserve boundary: cash == commission exactly, no double charge --

    @Test
    void d23ReserveBoundaryLeavesCashExactlyAtCommission() {
        BarSeries s = series(flatBar(1, "100"), flatBar(2, "100"));
        BacktestConfig cfg = config("1000", "5", "0.1", date(1), date(2));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(0));

        BacktestResult result = new Backtester().run(s, strat, cfg);

        Fill fill = result.fills().get(0);
        assertEquals(9, fill.quantity());
        assertEquals(0, fill.commission().compareTo(d("5")));
        assertEquals(0, fill.fillPrice().compareTo(d("110")));

        EquityPoint finalPoint = result.finalPoint();
        // Only ONE commission was charged: 1000 - (9*110 + 5) = 5, not 0.
        assertEquals(0, finalPoint.cash().compareTo(d("5")));
        assertEquals(0, finalPoint.costBasis().compareTo(d("995")));
    }

    @Test
    void d23KeepsCashNonNegativeEvenAtAnExtremelyLowExitPrice() {
        BarSeries s = series(
                flatBar(1, "99.5"),
                bar(2, "99.5", "99.5", "99.5", "99.5"),
                bar(3, "0.01", "0.01", "0.01", "0.01"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(3));
        StrategyDefinition strat = strategy(ALWAYS_ENTER, ALWAYS_EXIT);

        BacktestResult result = new Backtester().run(s, strat, cfg);

        // Entry sized to 9 (D-23), not 10 (pre-D-23 would size to 10 and
        // leave exactly 0 in cash before the exit).
        assertEquals(9, result.fills().get(0).quantity());
        assertEquals(2, result.fills().size());

        EquityPoint finalPoint = result.finalPoint();
        assertTrue(finalPoint.cash().compareTo(BigDecimal.ZERO) >= 0);
        assertEquals(0, finalPoint.cash().compareTo(d("94.59")));
    }

    // --- future-open independence ------------------------------------------

    @Test
    void sizingUsesOnlyTheSignalBarCloseRegardlessOfTheNextOpen() {
        BarSeries seriesA = series(flatBar(1, "100"), bar(2, "90", "100", "90", "100"));
        BarSeries seriesB = series(flatBar(1, "100"), bar(2, "115", "115", "100", "100"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(2));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(0));

        BacktestResult resultA = new Backtester().run(seriesA, strat, cfg);
        BacktestResult resultB = new Backtester().run(seriesB, strat, cfg);

        // Identical d1 equity point (nothing yet depends on d2's open).
        assertEquals(resultA.equityCurve().get(0), resultB.equityCurve().get(0));

        // A affords the gap-down; B does not afford the gap-up.
        assertEquals(1, resultA.fills().size());
        assertEquals(9, resultA.fills().get(0).quantity());
        assertEquals(1, resultA.fills().get(0).orderId());

        assertEquals(1, resultB.rejections().size());
        OrderRejection.InsufficientCash rejectionB = (OrderRejection.InsufficientCash) resultB.rejections().get(0);
        assertEquals(9, rejectionB.quantity());
        assertEquals(1, rejectionB.orderId());

        // Same order id and same quantity in both: sizing was identical.
        assertEquals(resultA.fills().get(0).quantity(), rejectionB.quantity());
        assertEquals(resultA.fills().get(0).orderId(), rejectionB.orderId());
        assertEquals(resultA.fills().get(0).signal(), rejectionB.signal());
    }

    // --- determinism ---------------------------------------------------------

    @Test
    void repeatedRunProducesAnEqualResult() {
        BarSeries s = series(
                flatBar(1, "100"),
                bar(2, "100", "110", "100", "110"),
                bar(3, "112", "112", "112", "112"),
                bar(4, "112", "112", "112", "112"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(4));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(105));

        BacktestResult first = new Backtester().run(s, strat, cfg);
        BacktestResult second = new Backtester().run(s, strat, cfg);

        assertEquals(first, second);
    }

    // --- no pyramiding / full exit -------------------------------------------

    @Test
    void noPyramidingEntryIsNotReevaluatedWhileLong() {
        // Entry always true; once long, only exit is ever considered.
        // If pyramiding occurred, a second BUY order would be created
        // (and Order construction / Portfolio.apply(BUY while long) would
        // throw), so simply completing without exception is the proof,
        // together with only one BUY fill for the position.
        BarSeries s = series(
                flatBar(1, "100"), flatBar(2, "100"), flatBar(3, "100"), flatBar(4, "100"));
        BacktestConfig cfg = config("1000", "5", "0", date(1), date(4));
        StrategyDefinition strat = strategy(closeAbove(0), closeAbove(1e9)); // exit never true

        BacktestResult result = new Backtester().run(s, strat, cfg);

        long buyFills = result.fills().stream()
                .filter(f -> f.side() == in.vedchangani.parallax.engine.execution.OrderSide.BUY)
                .count();
        assertEquals(1, buyFills);
    }

    // --- trading-cost totals reconcile with cash (D-27) ------------------------

    /**
     * Churning always-enter/always-exit run with commission 5 and slippage
     * 1%: BUY 98 @ 90.9 (day 2), SELL 98 @ 108.9 (day 3), BUY 105 @ 101
     * (day 4), SELL 105 @ 128.7 (day 5), BUY 111 @ 126.25 (day 6, left
     * open — day 6 is the last in-range bar). Every entry's next open is at
     * or below its sizing close, so no BUY is rejected.
     */
    private static BacktestResult costReconciliationRun() {
        BarSeries s = series(flatBar(1, "100"), flatBar(2, "90"), flatBar(3, "110"),
                flatBar(4, "100"), flatBar(5, "130"), flatBar(6, "125"));
        BacktestConfig cfg = config("10000", "5", "0.01", date(1), date(6));
        return new Backtester().run(s, strategy(ALWAYS_ENTER, ALWAYS_EXIT), cfg);
    }

    @Test
    void finalCashEqualsCapitalMinusBuysPlusSellsMinusTotalCommission() {
        BacktestResult result = costReconciliationRun();

        BigDecimal expectedCash = result.config().initialCapital();
        for (Fill fill : result.fills()) {
            BigDecimal notional = fill.fillPrice().multiply(BigDecimal.valueOf(fill.quantity()));
            expectedCash = switch (fill.side()) {
                case BUY -> expectedCash.subtract(notional);
                case SELL -> expectedCash.add(notional);
            };
        }
        expectedCash = expectedCash.subtract(result.totalCommission());

        assertEquals(5, result.fills().size());
        assertEquals(0, expectedCash.compareTo(result.finalPoint().cash()));
        assertEquals(0, d("633.75").compareTo(result.finalPoint().cash()));
    }

    @Test
    void totalCommissionEqualsCommissionPerFillTimesFillCount() {
        BacktestResult result = costReconciliationRun();

        BigDecimal expected = result.config().commissionPerFill()
                .multiply(BigDecimal.valueOf(result.fills().size()));

        assertEquals(0, expected.compareTo(result.totalCommission()));
        assertEquals(0, d("25").compareTo(result.totalCommission()));
    }

    @Test
    void totalSlippageCostWithNonZeroSlippageIsExact() {
        BacktestResult result = costReconciliationRun();

        // 0.9×98 + 1.1×98 + 1×105 + 1.3×105 + 1.25×111
        //   = 88.2 + 107.8 + 105 + 136.5 + 138.75
        assertEquals(0, d("576.25").compareTo(result.totalSlippageCost()));
    }
}
