package in.vedchangani.parallax.engine.result;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestResultTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END = LocalDate.of(2024, 12, 31);
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);

    private static final BacktestConfig CONFIG = new BacktestConfig(new BigDecimal("10000"),
            new BigDecimal("5"), new BigDecimal("0.01"), START, END);

    private static final StrategyDefinition STRATEGY = new StrategyDefinition(
            new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(0)),
            new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
            new PositionSizing.CashFraction(BigDecimal.ONE));

    private static IndicatorSnapshot snapshot(LocalDate date) {
        return new IndicatorSnapshot(date, new BigDecimal("100"), Map.of(SMA_20, 100.0));
    }

    private static final SignalEvent ENTER = new SignalEvent(SignalType.ENTER, snapshot(LocalDate.of(2024, 1, 2)));
    private static final SignalEvent EXIT = new SignalEvent(SignalType.EXIT, snapshot(LocalDate.of(2024, 1, 4)));

    private static Fill buy(int orderId, LocalDate date, long quantity) {
        return new Fill(orderId, date, quantity, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("5"), ENTER);
    }

    private static Fill sell(int orderId, LocalDate date, long quantity) {
        return new Fill(orderId, date, quantity, new BigDecimal("120"), new BigDecimal("120"),
                new BigDecimal("5"), EXIT);
    }

    private static EquityPoint point(LocalDate date, BigDecimal close) {
        return new EquityPoint(date, new BigDecimal("10000"), 0, BigDecimal.ZERO, BigDecimal.ZERO, close);
    }

    private static BacktestResult result(List<EquityPoint> curve, List<Fill> fills,
                                          List<OrderRejection> rejections) {
        return new BacktestResult("AAPL", STRATEGY, CONFIG, Optional.empty(), curve, fills, rejections);
    }

    @Test
    void validConstructionExposesFields() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));
        BacktestResult r = result(curve, List.of(), List.of());

        assertEquals("AAPL", r.symbol());
        assertEquals(STRATEGY, r.strategy());
        assertEquals(CONFIG, r.config());
        assertEquals(Optional.empty(), r.firstEvaluableDate());
    }

    @Test
    void nullSymbolRejected() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));

        assertThrows(NullPointerException.class,
                () -> new BacktestResult(null, STRATEGY, CONFIG, Optional.empty(), curve, List.of(), List.of()));
    }

    @Test
    void blankSymbolRejected() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));

        assertThrows(IllegalArgumentException.class,
                () -> new BacktestResult("  ", STRATEGY, CONFIG, Optional.empty(), curve, List.of(), List.of()));
    }

    @Test
    void listsAreDefensivelyCopied() {
        List<EquityPoint> source = new ArrayList<>();
        source.add(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));

        BacktestResult r = result(source, List.of(), List.of());
        source.add(point(LocalDate.of(2024, 1, 3), new BigDecimal("101")));

        assertEquals(1, r.equityCurve().size());
    }

    @Test
    void exposedListsAreImmutable() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));
        BacktestResult r = result(curve, List.of(), List.of());

        assertThrows(UnsupportedOperationException.class,
                () -> r.equityCurve().add(point(LocalDate.of(2024, 1, 3), new BigDecimal("101"))));
        assertThrows(UnsupportedOperationException.class,
                () -> r.fills().add(buy(1, LocalDate.of(2024, 1, 2), 10)));
        assertThrows(UnsupportedOperationException.class,
                () -> r.rejections().add(new OrderRejection.ZeroQuantity(ENTER)));
    }

    @Test
    void emptyEquityCurveRejected() {
        assertThrows(IllegalArgumentException.class, () -> result(List.of(), List.of(), List.of()));
    }

    @Test
    void nonAscendingEquityDatesRejected() {
        List<EquityPoint> curve = List.of(
                point(LocalDate.of(2024, 1, 3), new BigDecimal("100")),
                point(LocalDate.of(2024, 1, 2), new BigDecimal("101")));

        assertThrows(IllegalArgumentException.class, () -> result(curve, List.of(), List.of()));
    }

    @Test
    void equalEquityDatesRejected() {
        LocalDate date = LocalDate.of(2024, 1, 2);
        List<EquityPoint> curve = List.of(point(date, new BigDecimal("100")), point(date, new BigDecimal("101")));

        assertThrows(IllegalArgumentException.class, () -> result(curve, List.of(), List.of()));
    }

    @Test
    void equityDateOutsideConfigRangeRejected() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2023, 12, 31), new BigDecimal("100")));

        assertThrows(IllegalArgumentException.class, () -> result(curve, List.of(), List.of()));
    }

    @Test
    void emptyFillsValid() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));
        BacktestResult r = result(curve, List.of(), List.of());

        assertEquals(List.of(), r.fills());
    }

    @Test
    void alternatingFillsAccepted() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 5), new BigDecimal("120")));
        List<Fill> fills = List.of(buy(1, LocalDate.of(2024, 1, 2), 10), sell(2, LocalDate.of(2024, 1, 4), 10));

        result(curve, fills, List.of());
    }

    @Test
    void fillsMustStartWithBuy() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 5), new BigDecimal("120")));
        List<Fill> fills = List.of(sell(1, LocalDate.of(2024, 1, 2), 10));

        assertThrows(IllegalArgumentException.class, () -> result(curve, fills, List.of()));
    }

    @Test
    void nonAlternatingFillsRejected() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 5), new BigDecimal("120")));
        List<Fill> fills = List.of(buy(1, LocalDate.of(2024, 1, 2), 10), buy(2, LocalDate.of(2024, 1, 3), 5));

        assertThrows(IllegalArgumentException.class, () -> result(curve, fills, List.of()));
    }

    @Test
    void nonAscendingFillDatesRejected() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 5), new BigDecimal("120")));
        List<Fill> fills = List.of(buy(1, LocalDate.of(2024, 1, 4), 10), sell(2, LocalDate.of(2024, 1, 2), 10));

        assertThrows(IllegalArgumentException.class, () -> result(curve, fills, List.of()));
    }

    @Test
    void emptyRejectionsValid() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));

        result(curve, List.of(), List.of());
    }

    @Test
    void nonDecreasingRejectionDatesAccepted() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));
        SignalEvent laterSignal = new SignalEvent(SignalType.ENTER, snapshot(LocalDate.of(2024, 1, 2)));
        List<OrderRejection> rejections = List.of(
                new OrderRejection.ZeroQuantity(ENTER),
                new OrderRejection.ZeroQuantity(laterSignal));

        result(curve, List.of(), rejections);
    }

    @Test
    void decreasingRejectionDatesRejected() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));
        SignalEvent laterSignal = new SignalEvent(SignalType.ENTER, snapshot(LocalDate.of(2024, 1, 5)));
        SignalEvent earlierSignal = new SignalEvent(SignalType.ENTER, snapshot(LocalDate.of(2024, 1, 1)));
        List<OrderRejection> rejections = List.of(
                new OrderRejection.ZeroQuantity(laterSignal),
                new OrderRejection.ZeroQuantity(earlierSignal));

        assertThrows(IllegalArgumentException.class, () -> result(curve, List.of(), rejections));
    }

    @Test
    void tradesDerivedFromFills() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 5), new BigDecimal("120")));
        List<Fill> fills = List.of(buy(1, LocalDate.of(2024, 1, 2), 10), sell(2, LocalDate.of(2024, 1, 4), 10));
        BacktestResult r = result(curve, fills, List.of());

        List<Trade> trades = r.trades();
        assertEquals(1, trades.size());
        assertTrue(trades.get(0) instanceof Trade.Closed);
    }

    @Test
    void openTradeDerivedWhenTrailingBuy() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));
        List<Fill> fills = List.of(buy(1, LocalDate.of(2024, 1, 2), 10));
        BacktestResult r = result(curve, fills, List.of());

        List<Trade> trades = r.trades();
        assertEquals(1, trades.size());
        assertTrue(trades.get(0) instanceof Trade.Open);
    }

    @Test
    void finalPointIsLastEquityPoint() {
        EquityPoint last = point(LocalDate.of(2024, 1, 5), new BigDecimal("120"));
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")), last);
        BacktestResult r = result(curve, List.of(), List.of());

        assertEquals(last, r.finalPoint());
    }

    @Test
    void firstEvaluableDatePresentWorks() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));
        LocalDate evaluable = LocalDate.of(2024, 1, 2);

        BacktestResult r = new BacktestResult("AAPL", STRATEGY, CONFIG, Optional.of(evaluable), curve,
                List.of(), List.of());

        assertEquals(Optional.of(evaluable), r.firstEvaluableDate());
    }

    private static Fill fill(int orderId, LocalDate date, long quantity, String referenceOpen, String fillPrice,
                             String commission, SignalEvent signal) {
        return new Fill(orderId, date, quantity, new BigDecimal(referenceOpen), new BigDecimal(fillPrice),
                new BigDecimal(commission), signal);
    }

    private static final Fill SLIPPED_BUY =
            fill(1, LocalDate.of(2024, 1, 3), 10, "100", "100.5", "5", ENTER);
    private static final Fill SLIPPED_SELL =
            fill(2, LocalDate.of(2024, 1, 5), 10, "120", "119.4", "5", EXIT);
    private static final Fill SLIPPED_SECOND_BUY =
            fill(3, LocalDate.of(2024, 1, 8), 8, "110", "110.11", "5", ENTER);

    private static BacktestResult withFills(List<Fill> fills) {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));
        return result(curve, fills, List.of());
    }

    @Test
    void costTotalsAreExactlyZeroWithNoFills() {
        BacktestResult r = withFills(List.of());

        assertEquals(BigDecimal.ZERO, r.totalCommission());
        assertEquals(BigDecimal.ZERO, r.totalSlippageCost());
    }

    @Test
    void costTotalsIncludeTheEntryFillOfAnOpenTrade() {
        BacktestResult r = withFills(List.of(SLIPPED_BUY));

        assertTrue(r.trades().get(0) instanceof Trade.Open);
        assertEquals(0, new BigDecimal("5").compareTo(r.totalCommission()));
        assertEquals(0, new BigDecimal("5.0").compareTo(r.totalSlippageCost()));
    }

    @Test
    void costTotalsForOneClosedTrade() {
        BacktestResult r = withFills(List.of(SLIPPED_BUY, SLIPPED_SELL));

        assertEquals(0, new BigDecimal("10").compareTo(r.totalCommission()));
        assertEquals(0, new BigDecimal("11.0").compareTo(r.totalSlippageCost()));
    }

    @Test
    void costTotalsForClosedTradeThenOpenTrade() {
        BacktestResult r = withFills(List.of(SLIPPED_BUY, SLIPPED_SELL, SLIPPED_SECOND_BUY));

        assertEquals(0, new BigDecimal("15").compareTo(r.totalCommission()));
        assertEquals(0, new BigDecimal("11.88").compareTo(r.totalSlippageCost()));
    }

    @Test
    void slippageTotalIsZeroWhenFillPricesEqualReferenceOpens() {
        LocalDate d = LocalDate.of(2024, 1, 3);
        BacktestResult r = withFills(List.of(buy(1, d, 10), sell(2, d.plusDays(2), 10)));

        assertEquals(0, BigDecimal.ZERO.compareTo(r.totalSlippageCost()));
        assertEquals(0, new BigDecimal("10").compareTo(r.totalCommission()));
    }

    @Test
    void costTotalsReconcileWithClosedTradeTotalsPlusTrailingOpenEntry() {
        BacktestResult r = withFills(List.of(SLIPPED_BUY, SLIPPED_SELL, SLIPPED_SECOND_BUY));

        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal slippage = BigDecimal.ZERO;
        for (Trade trade : r.trades()) {
            switch (trade) {
                case Trade.Closed closed -> {
                    commission = commission.add(closed.totalCommission());
                    slippage = slippage.add(closed.totalSlippageCost());
                }
                case Trade.Open open -> {
                    commission = commission.add(open.entry().commission());
                    slippage = slippage.add(open.entry().slippageCost());
                }
            }
        }

        assertEquals(0, commission.compareTo(r.totalCommission()));
        assertEquals(0, slippage.compareTo(r.totalSlippageCost()));
    }

    @Test
    void costTotalsAreRecomputedIdenticallyOnEveryCall() {
        BacktestResult r = withFills(List.of(SLIPPED_BUY, SLIPPED_SELL, SLIPPED_SECOND_BUY));

        assertEquals(r.totalCommission(), r.totalCommission());
        assertEquals(r.totalSlippageCost(), r.totalSlippageCost());
    }

    @Test
    void equalResultsAreEqual() {
        List<EquityPoint> curve = List.of(point(LocalDate.of(2024, 1, 2), new BigDecimal("100")));

        BacktestResult a = result(curve, List.of(), List.of());
        BacktestResult b = result(curve, List.of(), List.of());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
