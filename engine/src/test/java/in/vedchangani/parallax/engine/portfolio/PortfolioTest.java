package in.vedchangani.parallax.engine.portfolio;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioTest {

    private static final LocalDate SIGNAL_DATE = LocalDate.of(2024, 1, 2);
    private static final LocalDate FILL_DATE = LocalDate.of(2024, 1, 3);
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);

    private static IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(SIGNAL_DATE, new BigDecimal("100"), Map.of(SMA_20, 100.0));
    }

    private static final SignalEvent ENTER_SIGNAL = new SignalEvent(SignalType.ENTER, snapshot());
    private static final SignalEvent EXIT_SIGNAL = new SignalEvent(SignalType.EXIT, snapshot());

    private static Fill buy(int orderId, long quantity, BigDecimal price, BigDecimal commission) {
        return new Fill(orderId, FILL_DATE, quantity, price, price, commission, ENTER_SIGNAL);
    }

    private static Fill sell(int orderId, long quantity, BigDecimal price, BigDecimal commission) {
        return new Fill(orderId, FILL_DATE, quantity, price, price, commission, EXIT_SIGNAL);
    }

    private static void assertInvariants(EquityPoint point, BigDecimal initialCapital) {
        assertEquals(0, point.equity().compareTo(point.cash().add(point.marketValue())));
        assertEquals(0, point.equity().compareTo(
                initialCapital.add(point.realizedPnl()).add(point.unrealizedPnl())));
    }

    @Test
    void initialStateIsFlatWithInitialCash() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));

        assertTrue(portfolio.isFlat());
        assertEquals(new BigDecimal("10000"), portfolio.cash());
        assertEquals(0, portfolio.quantity());
        assertEquals(0, portfolio.costBasis().compareTo(BigDecimal.ZERO));
        assertEquals(0, portfolio.realizedPnl().compareTo(BigDecimal.ZERO));

        EquityPoint point = portfolio.markToMarket(FILL_DATE, new BigDecimal("100"));
        assertEquals(0, point.equity().compareTo(new BigDecimal("10000")));
        assertInvariants(point, new BigDecimal("10000"));
    }

    @Test
    void buyTenAtHundredWithCommissionFive() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));

        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));

        assertEquals(new BigDecimal("8995"), portfolio.cash());
        assertEquals(10, portfolio.quantity());
        assertEquals(0, portfolio.costBasis().compareTo(new BigDecimal("1005")));
        assertEquals(0, portfolio.realizedPnl().compareTo(BigDecimal.ZERO));
        assertFalse(portfolio.isFlat());
    }

    @Test
    void markingAfterBuyAtDifferentCloses() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));
        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));

        EquityPoint at100 = portfolio.markToMarket(FILL_DATE, new BigDecimal("100"));
        assertEquals(0, at100.marketValue().compareTo(new BigDecimal("1000")));
        assertEquals(0, at100.unrealizedPnl().compareTo(new BigDecimal("-5")));
        assertEquals(0, at100.equity().compareTo(new BigDecimal("9995")));
        assertInvariants(at100, new BigDecimal("10000"));

        EquityPoint at110 = portfolio.markToMarket(FILL_DATE, new BigDecimal("110"));
        assertEquals(0, at110.unrealizedPnl().compareTo(new BigDecimal("95")));
        assertEquals(0, at110.equity().compareTo(new BigDecimal("10095")));
        assertInvariants(at110, new BigDecimal("10000"));

        EquityPoint at90 = portfolio.markToMarket(FILL_DATE, new BigDecimal("90"));
        assertEquals(0, at90.unrealizedPnl().compareTo(new BigDecimal("-105")));
        assertEquals(0, at90.equity().compareTo(new BigDecimal("9895")));
        assertInvariants(at90, new BigDecimal("10000"));
    }

    @Test
    void winningExit() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));
        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));

        portfolio.apply(sell(2, 10, new BigDecimal("120"), new BigDecimal("5")));

        assertEquals(0, portfolio.cash().compareTo(new BigDecimal("10190")));
        assertEquals(0, portfolio.realizedPnl().compareTo(new BigDecimal("190")));
        assertEquals(0, portfolio.quantity());
        assertEquals(0, portfolio.costBasis().compareTo(BigDecimal.ZERO));
        assertTrue(portfolio.isFlat());

        EquityPoint point = portfolio.markToMarket(FILL_DATE, new BigDecimal("120"));
        assertEquals(0, point.unrealizedPnl().compareTo(BigDecimal.ZERO));
        assertEquals(0, point.equity().compareTo(new BigDecimal("10190")));
        assertInvariants(point, new BigDecimal("10000"));
    }

    @Test
    void losingExit() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));
        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));

        portfolio.apply(sell(2, 10, new BigDecimal("90"), new BigDecimal("5")));

        assertEquals(0, portfolio.cash().compareTo(new BigDecimal("9890")));
        assertEquals(0, portfolio.realizedPnl().compareTo(new BigDecimal("-110")));
        assertEquals(0, portfolio.quantity());
        assertEquals(0, portfolio.costBasis().compareTo(BigDecimal.ZERO));
    }

    @Test
    void zeroCommissionRoundTrip() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));
        portfolio.apply(buy(1, 10, new BigDecimal("100"), BigDecimal.ZERO));
        portfolio.apply(sell(2, 10, new BigDecimal("100"), BigDecimal.ZERO));

        assertEquals(0, portfolio.cash().compareTo(new BigDecimal("10000")));
        assertEquals(0, portfolio.realizedPnl().compareTo(BigDecimal.ZERO));
    }

    @Test
    void commissionRoundTrip() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));
        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));
        portfolio.apply(sell(2, 10, new BigDecimal("100"), new BigDecimal("5")));

        assertEquals(0, portfolio.cash().compareTo(new BigDecimal("9990")));
        assertEquals(0, portfolio.realizedPnl().compareTo(new BigDecimal("-10")));
    }

    @Test
    void multipleRoundTripsAccumulateRealizedPnl() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));

        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));
        portfolio.apply(sell(2, 10, new BigDecimal("120"), new BigDecimal("5")));
        assertEquals(0, portfolio.costBasis().compareTo(BigDecimal.ZERO));

        portfolio.apply(buy(3, 5, new BigDecimal("50"), new BigDecimal("2")));
        assertEquals(0, portfolio.costBasis().compareTo(new BigDecimal("252")));
        portfolio.apply(sell(4, 5, new BigDecimal("40"), new BigDecimal("2")));

        assertEquals(0, portfolio.realizedPnl().compareTo(new BigDecimal("136")));
        assertEquals(0, portfolio.costBasis().compareTo(BigDecimal.ZERO));
        assertTrue(portfolio.isFlat());
    }

    private static void assertUnchanged(Portfolio portfolio, BigDecimal cash, long quantity,
                                         BigDecimal costBasis, BigDecimal realizedPnl) {
        assertEquals(0, portfolio.cash().compareTo(cash));
        assertEquals(quantity, portfolio.quantity());
        assertEquals(0, portfolio.costBasis().compareTo(costBasis));
        assertEquals(0, portfolio.realizedPnl().compareTo(realizedPnl));
    }

    @Test
    void buyWhileAlreadyLongRejectedAndStateUnchanged() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));
        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));

        assertThrows(IllegalStateException.class,
                () -> portfolio.apply(buy(2, 10, new BigDecimal("120"), new BigDecimal("5"))));

        assertUnchanged(portfolio, new BigDecimal("8995"), 10, new BigDecimal("1005"), BigDecimal.ZERO);
    }

    @Test
    void sellWhileFlatRejectedAndStateUnchanged() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));

        assertThrows(IllegalStateException.class,
                () -> portfolio.apply(sell(1, 10, new BigDecimal("100"), new BigDecimal("5"))));

        assertUnchanged(portfolio, new BigDecimal("10000"), 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void partialSellRejectedAndStateUnchanged() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));
        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));

        assertThrows(IllegalStateException.class,
                () -> portfolio.apply(sell(2, 5, new BigDecimal("100"), new BigDecimal("5"))));

        assertUnchanged(portfolio, new BigDecimal("8995"), 10, new BigDecimal("1005"), BigDecimal.ZERO);
    }

    @Test
    void sellMoreThanHeldRejectedAndStateUnchanged() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));
        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));

        assertThrows(IllegalStateException.class,
                () -> portfolio.apply(sell(2, 15, new BigDecimal("100"), new BigDecimal("5"))));

        assertUnchanged(portfolio, new BigDecimal("8995"), 10, new BigDecimal("1005"), BigDecimal.ZERO);
    }

    @Test
    void buyCostingMoreThanCashRejectedAndStateUnchanged() {
        Portfolio portfolio = new Portfolio(new BigDecimal("1000"));

        assertThrows(IllegalStateException.class,
                () -> portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5"))));

        assertUnchanged(portfolio, new BigDecimal("1000"), 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void sellProducingNegativeCashRejectedAndStateUnchanged() {
        Portfolio portfolio = new Portfolio(new BigDecimal("100"));
        portfolio.apply(buy(1, 1, new BigDecimal("100"), BigDecimal.ZERO));

        assertThrows(IllegalStateException.class,
                () -> portfolio.apply(sell(2, 1, new BigDecimal("1"), new BigDecimal("5"))));

        assertUnchanged(portfolio, BigDecimal.ZERO, 1, new BigDecimal("100"), BigDecimal.ZERO);
    }

    @Test
    void nullInitialCashRejected() {
        assertThrows(NullPointerException.class, () -> new Portfolio(null));
    }

    @Test
    void negativeInitialCashRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Portfolio(new BigDecimal("-1")));
    }

    @Test
    void zeroInitialCashAccepted() {
        Portfolio portfolio = new Portfolio(BigDecimal.ZERO);

        assertTrue(portfolio.isFlat());
        assertEquals(0, portfolio.cash().compareTo(BigDecimal.ZERO));
    }

    @Test
    void nullFillRejected() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));

        assertThrows(NullPointerException.class, () -> portfolio.apply(null));
    }

    @Test
    void markToMarketNullDateRejected() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));

        assertThrows(NullPointerException.class, () -> portfolio.markToMarket(null, new BigDecimal("100")));
    }

    @Test
    void markToMarketNullCloseRejected() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));

        assertThrows(NullPointerException.class, () -> portfolio.markToMarket(FILL_DATE, null));
    }

    @Test
    void markToMarketNonPositiveCloseRejected() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));

        assertThrows(IllegalArgumentException.class,
                () -> portfolio.markToMarket(FILL_DATE, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> portfolio.markToMarket(FILL_DATE, new BigDecimal("-1")));
    }

    @Test
    void markToMarketDoesNotMutate() {
        Portfolio portfolio = new Portfolio(new BigDecimal("10000"));
        portfolio.apply(buy(1, 10, new BigDecimal("100"), new BigDecimal("5")));

        portfolio.markToMarket(FILL_DATE, new BigDecimal("200"));
        portfolio.markToMarket(FILL_DATE, new BigDecimal("50"));

        assertUnchanged(portfolio, new BigDecimal("8995"), 10, new BigDecimal("1005"), BigDecimal.ZERO);
    }
}
