package in.vedchangani.parallax.engine.indicator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateOfChangeTest {

    private static final double TOLERANCE = 1e-9;

    private static BigDecimal price(String value) {
        return new BigDecimal(value);
    }

    @Test
    void isNotReadyUntilPeriodPlusOneCloses() {
        RateOfChange roc = new RateOfChange(3);

        roc.update(price("100"));
        assertFalse(roc.isReady());

        roc.update(price("101"));
        assertFalse(roc.isReady());

        roc.update(price("102"));
        assertFalse(roc.isReady());

        roc.update(price("103"));
        assertTrue(roc.isReady());
    }

    @Test
    void periodOneIsReadyOnTheSecondClose() {
        RateOfChange roc = new RateOfChange(1);

        roc.update(price("100"));
        assertFalse(roc.isReady());

        roc.update(price("110"));
        assertTrue(roc.isReady());
        assertEquals(10.0, roc.value(), TOLERANCE);
    }

    @Test
    void positiveRateOfChangeIsReturnedInPercentagePoints() {
        RateOfChange roc = new RateOfChange(3);

        roc.update(price("100"));
        roc.update(price("110"));
        roc.update(price("121"));
        roc.update(price("150"));

        assertEquals(50.0, roc.value(), TOLERANCE);
    }

    @Test
    void negativeRateOfChangeIsReturnedInPercentagePoints() {
        RateOfChange roc = new RateOfChange(3);

        roc.update(price("100"));
        roc.update(price("90"));
        roc.update(price("80"));
        roc.update(price("75"));

        assertEquals(-25.0, roc.value(), TOLERANCE);
    }

    @Test
    void unchangedCloseGivesZero() {
        RateOfChange roc = new RateOfChange(2);

        roc.update(price("50"));
        roc.update(price("70"));
        roc.update(price("50"));

        assertEquals(0.0, roc.value(), TOLERANCE);
    }

    @Test
    void referenceCloseSlidesWithEachNewBar() {
        RateOfChange roc = new RateOfChange(2);

        roc.update(price("100"));
        roc.update(price("120"));
        roc.update(price("110"));
        assertEquals(10.0, roc.value(), TOLERANCE);

        roc.update(price("90"));
        assertEquals(-25.0, roc.value(), TOLERANCE);

        roc.update(price("99"));
        assertEquals(-10.0, roc.value(), TOLERANCE);
    }

    @Test
    void valueBeforeReadinessThrowsIllegalStateException() {
        RateOfChange roc = new RateOfChange(2);

        assertThrows(IllegalStateException.class, roc::value);

        roc.update(price("100"));
        assertThrows(IllegalStateException.class, roc::value);

        roc.update(price("101"));
        assertThrows(IllegalStateException.class, roc::value);
    }

    @Test
    void rejectsAPeriodBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new RateOfChange(0));
        assertThrows(IllegalArgumentException.class, () -> new RateOfChange(-1));
    }

    @Test
    void twoInstancesAreIndependent() {
        RateOfChange first = new RateOfChange(1);
        RateOfChange second = new RateOfChange(1);

        first.update(price("100"));
        first.update(price("200"));

        assertFalse(second.isReady());

        second.update(price("100"));
        second.update(price("50"));

        assertEquals(100.0, first.value(), TOLERANCE);
        assertEquals(-50.0, second.value(), TOLERANCE);
    }

    @Test
    void hugePeriodConstructsAndAcceptsAFewUpdatesWithoutAllocatingPeriodSizedMemory() {
        RateOfChange roc = new RateOfChange(Integer.MAX_VALUE);

        roc.update(price("100"));
        roc.update(price("101"));
        roc.update(price("99"));

        assertFalse(roc.isReady());
        assertThrows(IllegalStateException.class, roc::value);
    }
}
