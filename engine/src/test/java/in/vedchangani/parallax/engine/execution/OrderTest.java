package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);
    private static final BigDecimal CLOSE = new BigDecimal("101.50");
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);

    private static IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(DATE, CLOSE, Map.of(SMA_20, 100.0));
    }

    private static final SignalEvent ENTER_SIGNAL = new SignalEvent(SignalType.ENTER, snapshot());
    private static final SignalEvent EXIT_SIGNAL = new SignalEvent(SignalType.EXIT, snapshot());

    @Test
    void validConstructionExposesComponents() {
        Order order = new Order(1, 10, ENTER_SIGNAL);

        assertEquals(1, order.id());
        assertEquals(10, order.quantity());
        assertEquals(ENTER_SIGNAL, order.signal());
    }

    @Test
    void multipleExplicitSequentialIdsAreAccepted() {
        assertEquals(1, new Order(1, 5, ENTER_SIGNAL).id());
        assertEquals(2, new Order(2, 5, ENTER_SIGNAL).id());
        assertEquals(3, new Order(3, 5, ENTER_SIGNAL).id());
    }

    @Test
    void idZeroRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Order(0, 10, ENTER_SIGNAL));
    }

    @Test
    void negativeIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Order(-1, 10, ENTER_SIGNAL));
    }

    @Test
    void zeroQuantityRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Order(1, 0, ENTER_SIGNAL));
    }

    @Test
    void negativeQuantityRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Order(1, -5, ENTER_SIGNAL));
    }

    @Test
    void nullSignalRejected() {
        assertThrows(NullPointerException.class, () -> new Order(1, 10, null));
    }

    @Test
    void enterSignalMapsToBuy() {
        Order order = new Order(1, 10, ENTER_SIGNAL);

        assertEquals(OrderSide.BUY, order.side());
    }

    @Test
    void exitSignalMapsToSell() {
        Order order = new Order(1, 10, EXIT_SIGNAL);

        assertEquals(OrderSide.SELL, order.side());
    }

    @Test
    void equalOrdersHaveEqualHashCode() {
        Order a = new Order(1, 10, ENTER_SIGNAL);
        Order b = new Order(1, 10, new SignalEvent(SignalType.ENTER, snapshot()));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentIdChangesEquality() {
        Order a = new Order(1, 10, ENTER_SIGNAL);
        Order b = new Order(2, 10, ENTER_SIGNAL);

        assertNotEquals(a, b);
    }

    @Test
    void differentQuantityChangesEquality() {
        Order a = new Order(1, 10, ENTER_SIGNAL);
        Order b = new Order(1, 20, ENTER_SIGNAL);

        assertNotEquals(a, b);
    }

    @Test
    void differentSignalChangesEquality() {
        Order a = new Order(1, 10, ENTER_SIGNAL);
        Order b = new Order(1, 10, EXIT_SIGNAL);

        assertNotEquals(a, b);
    }

    @Test
    void orderSignalSnapshotRemainsTheOriginalImmutableSnapshot() {
        Order order = new Order(1, 10, ENTER_SIGNAL);

        assertEquals(100.0, order.signal().snapshot().value(SMA_20));
        assertEquals(DATE, order.signal().date());
    }

    @Test
    void orderSideValuesIsExactlyBuyAndSell() {
        assertArrayEquals(new OrderSide[] {OrderSide.BUY, OrderSide.SELL}, OrderSide.values());
    }
}
