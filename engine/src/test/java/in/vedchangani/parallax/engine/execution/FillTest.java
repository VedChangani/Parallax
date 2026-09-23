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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FillTest {

    private static final LocalDate SIGNAL_DATE = LocalDate.of(2024, 1, 2);
    private static final LocalDate FILL_DATE = LocalDate.of(2024, 1, 3);
    private static final BigDecimal CLOSE = new BigDecimal("101.50");
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);

    private static IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(SIGNAL_DATE, CLOSE, Map.of(SMA_20, 100.0));
    }

    private static final SignalEvent ENTER_SIGNAL = new SignalEvent(SignalType.ENTER, snapshot());
    private static final SignalEvent EXIT_SIGNAL = new SignalEvent(SignalType.EXIT, snapshot());

    private static Fill fill(BigDecimal referenceOpen, BigDecimal fillPrice, long quantity, BigDecimal commission) {
        return new Fill(1, FILL_DATE, quantity, referenceOpen, fillPrice, commission, ENTER_SIGNAL);
    }

    @Test
    void validConstructionExposesEveryStoredField() {
        Fill fill = new Fill(7, FILL_DATE, 10, new BigDecimal("100"), new BigDecimal("100.10"),
                new BigDecimal("1.00"), ENTER_SIGNAL);

        assertEquals(7, fill.orderId());
        assertEquals(FILL_DATE, fill.date());
        assertEquals(10, fill.quantity());
        assertEquals(new BigDecimal("100"), fill.referenceOpen());
        assertEquals(new BigDecimal("100.10"), fill.fillPrice());
        assertEquals(new BigDecimal("1.00"), fill.commission());
        assertEquals(ENTER_SIGNAL, fill.signal());
    }

    @Test
    void enterSignalGivesBuySide() {
        Fill fill = fill(new BigDecimal("100"), new BigDecimal("100.10"), 10, BigDecimal.ZERO);

        assertEquals(OrderSide.BUY, fill.side());
    }

    @Test
    void exitSignalGivesSellSide() {
        Fill fill = new Fill(1, FILL_DATE, 10, new BigDecimal("100"), new BigDecimal("99.90"),
                BigDecimal.ZERO, EXIT_SIGNAL);

        assertEquals(OrderSide.SELL, fill.side());
    }

    @Test
    void slippageCostForABuyFill() {
        Fill fill = fill(new BigDecimal("100"), new BigDecimal("100.10"), 10, BigDecimal.ZERO);

        assertEquals(new BigDecimal("1.00"), fill.slippageCost());
    }

    @Test
    void slippageCostForASellFill() {
        Fill fill = new Fill(1, FILL_DATE, 10, new BigDecimal("100"), new BigDecimal("99.90"),
                BigDecimal.ZERO, EXIT_SIGNAL);

        assertEquals(new BigDecimal("0.10").multiply(BigDecimal.valueOf(10)), fill.slippageCost());
    }

    @Test
    void slippageCostIsZeroWhenFillPriceEqualsReferenceOpen() {
        Fill fill = fill(new BigDecimal("100"), new BigDecimal("100"), 10, BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(fill.slippageCost()));
    }

    @Test
    void zeroCommissionAccepted() {
        fill(new BigDecimal("100"), new BigDecimal("100.10"), 10, BigDecimal.ZERO);
    }

    @Test
    void orderIdZeroRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Fill(0, FILL_DATE, 10, new BigDecimal("100"), new BigDecimal("100.10"),
                        BigDecimal.ZERO, ENTER_SIGNAL));
    }

    @Test
    void negativeOrderIdRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Fill(-1, FILL_DATE, 10, new BigDecimal("100"), new BigDecimal("100.10"),
                        BigDecimal.ZERO, ENTER_SIGNAL));
    }

    @Test
    void zeroQuantityRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> fill(new BigDecimal("100"), new BigDecimal("100.10"), 0, BigDecimal.ZERO));
    }

    @Test
    void negativeQuantityRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> fill(new BigDecimal("100"), new BigDecimal("100.10"), -5, BigDecimal.ZERO));
    }

    @Test
    void nullDateRejected() {
        assertThrows(NullPointerException.class,
                () -> new Fill(1, null, 10, new BigDecimal("100"), new BigDecimal("100.10"),
                        BigDecimal.ZERO, ENTER_SIGNAL));
    }

    @Test
    void nullReferenceOpenRejected() {
        assertThrows(NullPointerException.class,
                () -> new Fill(1, FILL_DATE, 10, null, new BigDecimal("100.10"), BigDecimal.ZERO, ENTER_SIGNAL));
    }

    @Test
    void nullFillPriceRejected() {
        assertThrows(NullPointerException.class,
                () -> new Fill(1, FILL_DATE, 10, new BigDecimal("100"), null, BigDecimal.ZERO, ENTER_SIGNAL));
    }

    @Test
    void nullCommissionRejected() {
        assertThrows(NullPointerException.class,
                () -> new Fill(1, FILL_DATE, 10, new BigDecimal("100"), new BigDecimal("100.10"), null,
                        ENTER_SIGNAL));
    }

    @Test
    void nullSignalRejected() {
        assertThrows(NullPointerException.class,
                () -> new Fill(1, FILL_DATE, 10, new BigDecimal("100"), new BigDecimal("100.10"),
                        BigDecimal.ZERO, null));
    }

    @Test
    void referenceOpenZeroOrNegativeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> fill(BigDecimal.ZERO, new BigDecimal("100.10"), 10, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> fill(new BigDecimal("-1"), new BigDecimal("100.10"), 10, BigDecimal.ZERO));
    }

    @Test
    void fillPriceZeroOrNegativeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> fill(new BigDecimal("100"), BigDecimal.ZERO, 10, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> fill(new BigDecimal("100"), new BigDecimal("-1"), 10, BigDecimal.ZERO));
    }

    @Test
    void negativeCommissionRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> fill(new BigDecimal("100"), new BigDecimal("100.10"), 10, new BigDecimal("-0.01")));
    }

    @Test
    void equalFillsAreEqualAndHaveEqualHashCode() {
        Fill a = fill(new BigDecimal("100"), new BigDecimal("100.10"), 10, BigDecimal.ZERO);
        Fill b = fill(new BigDecimal("100"), new BigDecimal("100.10"), 10, BigDecimal.ZERO);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentQuantityMakesFillsUnequal() {
        Fill a = fill(new BigDecimal("100"), new BigDecimal("100.10"), 10, BigDecimal.ZERO);
        Fill b = fill(new BigDecimal("100"), new BigDecimal("100.10"), 20, BigDecimal.ZERO);

        assertNotEquals(a, b);
    }

    @Test
    void signalExplanationRecoverableWithoutAnOrderObject() {
        Fill fill = new Fill(9, FILL_DATE, 10, new BigDecimal("100"), new BigDecimal("100.10"),
                BigDecimal.ZERO, ENTER_SIGNAL);

        assertEquals(9, fill.orderId());
        assertEquals(SignalType.ENTER, fill.signal().type());
        assertEquals(snapshot(), fill.signal().snapshot());
        assertEquals(SIGNAL_DATE, fill.signal().date());
        assertEquals(OrderSide.BUY, fill.side());
    }
}
