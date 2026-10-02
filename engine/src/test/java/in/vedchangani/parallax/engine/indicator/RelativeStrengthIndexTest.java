package in.vedchangani.parallax.engine.indicator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelativeStrengthIndexTest {

    private static final double TOLERANCE = 1e-6;

    private static BigDecimal price(String value) {
        return new BigDecimal(value);
    }

    @Test
    void isNotReadyUntilPeriodPlusOneCloses() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        assertFalse(rsi.isReady());

        rsi.update(price("101"));
        assertFalse(rsi.isReady());

        rsi.update(price("102"));
        assertFalse(rsi.isReady());

        rsi.update(price("103"));
        assertTrue(rsi.isReady());
    }

    @Test
    void monotonicIncreaseProducesRsiOfOneHundred() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        rsi.update(price("101"));
        rsi.update(price("102"));
        rsi.update(price("103"));

        assertEquals(100.0, rsi.value(), TOLERANCE);
    }

    @Test
    void flatMarketProducesRsiOfFifty() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        rsi.update(price("100"));
        rsi.update(price("100"));
        rsi.update(price("100"));

        assertEquals(50.0, rsi.value(), TOLERANCE);
    }

    @Test
    void monotonicDecreaseProducesRsiOfZero() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("103"));
        rsi.update(price("102"));
        rsi.update(price("101"));
        rsi.update(price("100"));

        assertEquals(0.0, rsi.value(), TOLERANCE);
    }

    @Test
    void mixedFixtureMatchesHandCalculatedRsi() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        rsi.update(price("102"));
        rsi.update(price("101"));
        rsi.update(price("103"));

        assertTrue(rsi.isReady());
        assertEquals(80.0, rsi.value(), TOLERANCE);
    }

    @Test
    void wilderSmoothingAppliesToTheNextChangeAfterSeeding() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        rsi.update(price("102"));
        rsi.update(price("101"));
        rsi.update(price("103"));

        rsi.update(price("105"));

        assertEquals(87.5, rsi.value(), TOLERANCE);
    }

    @Test
    void valueBeforeReadinessThrowsIllegalStateException() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        assertThrows(IllegalStateException.class, rsi::value);

        rsi.update(price("100"));
        assertThrows(IllegalStateException.class, rsi::value);

        rsi.update(price("101"));
        assertThrows(IllegalStateException.class, rsi::value);

        rsi.update(price("102"));
        assertThrows(IllegalStateException.class, rsi::value);
    }

    @Test
    void rejectsAPeriodBelowTwo() {
        assertThrows(IllegalArgumentException.class, () -> new RelativeStrengthIndex(0));
        assertThrows(IllegalArgumentException.class, () -> new RelativeStrengthIndex(1));
    }

    @Test
    void acceptsAPeriodOfTwo() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(2);

        rsi.update(price("100"));
        rsi.update(price("101"));
        rsi.update(price("102"));

        assertTrue(rsi.isReady());
    }

    @Test
    void twoInstancesWithTheSamePeriodAreIndependent() {
        RelativeStrengthIndex first = new RelativeStrengthIndex(3);
        RelativeStrengthIndex second = new RelativeStrengthIndex(3);

        first.update(price("100"));
        first.update(price("101"));
        first.update(price("102"));
        first.update(price("103"));

        assertFalse(second.isReady());

        second.update(price("100"));
        second.update(price("100"));
        second.update(price("100"));
        second.update(price("100"));

        assertEquals(100.0, first.value(), TOLERANCE);
        assertEquals(50.0, second.value(), TOLERANCE);
    }

    @Test
    void repeatedValuesDuringWarmUpProduceNoGainOrLoss() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(2);

        rsi.update(price("50"));
        rsi.update(price("50"));
        rsi.update(price("50"));

        assertTrue(rsi.isReady());
        assertEquals(50.0, rsi.value(), TOLERANCE);

        rsi.update(price("60"));
        assertEquals(100.0, rsi.value(), TOLERANCE);
    }

    @Test
    void hugePeriodConstructsAndAcceptsAFewUpdatesWithoutAllocatingPeriodSizedMemory() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(Integer.MAX_VALUE);

        assertFalse(rsi.isReady());
        rsi.update(price("100"));
        rsi.update(price("101"));
        rsi.update(price("99"));
        assertFalse(rsi.isReady());
        assertThrows(IllegalStateException.class, rsi::value);
    }
}
