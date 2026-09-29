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

class OrderRejectionTest {

    private static final LocalDate SIGNAL_DATE = LocalDate.of(2024, 1, 2);
    private static final LocalDate EXECUTION_DATE = LocalDate.of(2024, 1, 3);
    private static final BigDecimal CLOSE = new BigDecimal("101.50");
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);

    private static IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(SIGNAL_DATE, CLOSE, Map.of(SMA_20, 100.0));
    }

    private static final SignalEvent ENTER_SIGNAL = new SignalEvent(SignalType.ENTER, snapshot());
    private static final SignalEvent EXIT_SIGNAL = new SignalEvent(SignalType.EXIT, snapshot());

    @Test
    void rejectionReasonValuesIsExactlyZeroQuantityAndInsufficientCash() {
        assertArrayEquals(new RejectionReason[] {RejectionReason.ZERO_QUANTITY, RejectionReason.INSUFFICIENT_CASH},
                RejectionReason.values());
    }

    @Test
    void zeroQuantityValidConstructionWithEnterSignal() {
        OrderRejection.ZeroQuantity rejection = new OrderRejection.ZeroQuantity(ENTER_SIGNAL);

        assertEquals(ENTER_SIGNAL, rejection.signal());
    }

    @Test
    void zeroQuantityDateEqualsSignalDate() {
        OrderRejection.ZeroQuantity rejection = new OrderRejection.ZeroQuantity(ENTER_SIGNAL);

        assertEquals(SIGNAL_DATE, rejection.date());
    }

    @Test
    void zeroQuantityReasonIsZeroQuantity() {
        OrderRejection.ZeroQuantity rejection = new OrderRejection.ZeroQuantity(ENTER_SIGNAL);

        assertEquals(RejectionReason.ZERO_QUANTITY, rejection.reason());
    }

    @Test
    void zeroQuantityNullSignalRejected() {
        assertThrows(NullPointerException.class, () -> new OrderRejection.ZeroQuantity(null));
    }

    @Test
    void zeroQuantityExitSignalRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OrderRejection.ZeroQuantity(EXIT_SIGNAL));
    }

    @Test
    void zeroQuantityEqualityAndHashCode() {
        OrderRejection.ZeroQuantity a = new OrderRejection.ZeroQuantity(ENTER_SIGNAL);
        OrderRejection.ZeroQuantity b = new OrderRejection.ZeroQuantity(
                new SignalEvent(SignalType.ENTER, snapshot()));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    private static OrderRejection.InsufficientCash insufficientCash(BigDecimal requiredCash,
                                                                     BigDecimal availableCash) {
        return new OrderRejection.InsufficientCash(3, EXECUTION_DATE, 10, requiredCash, availableCash,
                ENTER_SIGNAL);
    }

    @Test
    void insufficientCashValidConstructionExposesAllFields() {
        OrderRejection.InsufficientCash rejection =
                insufficientCash(new BigDecimal("1000"), new BigDecimal("500"));

        assertEquals(3, rejection.orderId());
        assertEquals(EXECUTION_DATE, rejection.date());
        assertEquals(10, rejection.quantity());
        assertEquals(new BigDecimal("1000"), rejection.requiredCash());
        assertEquals(new BigDecimal("500"), rejection.availableCash());
        assertEquals(ENTER_SIGNAL, rejection.signal());
    }

    @Test
    void insufficientCashReasonIsInsufficientCash() {
        OrderRejection.InsufficientCash rejection =
                insufficientCash(new BigDecimal("1000"), new BigDecimal("500"));

        assertEquals(RejectionReason.INSUFFICIENT_CASH, rejection.reason());
    }

    @Test
    void insufficientCashExecutionDateCanDifferFromSignalDate() {
        OrderRejection.InsufficientCash rejection =
                insufficientCash(new BigDecimal("1000"), new BigDecimal("500"));

        assertNotEquals(rejection.signal().date(), rejection.date());
        assertEquals(EXECUTION_DATE, rejection.date());
    }

    @Test
    void insufficientCashOrderIdZeroRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderRejection.InsufficientCash(0, EXECUTION_DATE, 10, new BigDecimal("1000"),
                        new BigDecimal("500"), ENTER_SIGNAL));
    }

    @Test
    void insufficientCashNegativeOrderIdRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderRejection.InsufficientCash(-1, EXECUTION_DATE, 10, new BigDecimal("1000"),
                        new BigDecimal("500"), ENTER_SIGNAL));
    }

    @Test
    void insufficientCashZeroQuantityRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderRejection.InsufficientCash(3, EXECUTION_DATE, 0, new BigDecimal("1000"),
                        new BigDecimal("500"), ENTER_SIGNAL));
    }

    @Test
    void insufficientCashNegativeQuantityRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderRejection.InsufficientCash(3, EXECUTION_DATE, -5, new BigDecimal("1000"),
                        new BigDecimal("500"), ENTER_SIGNAL));
    }

    @Test
    void insufficientCashRequiredCashNullRejected() {
        assertThrows(NullPointerException.class, () -> insufficientCash(null, new BigDecimal("500")));
    }

    @Test
    void insufficientCashAvailableCashNullRejected() {
        assertThrows(NullPointerException.class, () -> insufficientCash(new BigDecimal("1000"), null));
    }

    @Test
    void insufficientCashRequiredCashZeroOrNegativeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> insufficientCash(BigDecimal.ZERO, new BigDecimal("-100")));
        assertThrows(IllegalArgumentException.class,
                () -> insufficientCash(new BigDecimal("-1"), new BigDecimal("-100")));
    }

    @Test
    void insufficientCashRequiredEqualToAvailableRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> insufficientCash(new BigDecimal("500"), new BigDecimal("500")));
    }

    @Test
    void insufficientCashRequiredLessThanAvailableRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> insufficientCash(new BigDecimal("400"), new BigDecimal("500")));
    }

    @Test
    void insufficientCashRequiredGreaterThanAvailableAccepted() {
        insufficientCash(new BigDecimal("501"), new BigDecimal("500"));
    }

    @Test
    void insufficientCashAvailableZeroWithPositiveRequiredAccepted() {
        insufficientCash(new BigDecimal("1"), BigDecimal.ZERO);
    }

    @Test
    void insufficientCashExitSignalRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderRejection.InsufficientCash(3, EXECUTION_DATE, 10, new BigDecimal("1000"),
                        new BigDecimal("500"), EXIT_SIGNAL));
    }

    @Test
    void insufficientCashNullDateRejected() {
        assertThrows(NullPointerException.class,
                () -> new OrderRejection.InsufficientCash(3, null, 10, new BigDecimal("1000"),
                        new BigDecimal("500"), ENTER_SIGNAL));
    }

    @Test
    void insufficientCashEqualityAndHashCode() {
        OrderRejection.InsufficientCash a = insufficientCash(new BigDecimal("1000"), new BigDecimal("500"));
        OrderRejection.InsufficientCash b = new OrderRejection.InsufficientCash(3, EXECUTION_DATE, 10,
                new BigDecimal("1000"), new BigDecimal("500"), new SignalEvent(SignalType.ENTER, snapshot()));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
