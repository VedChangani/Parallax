package in.vedchangani.parallax.engine.indicator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExponentialMovingAverageTest {

    private static final double TOLERANCE = 1e-9;

    private static BigDecimal price(String value) {
        return new BigDecimal(value);
    }

    @Test
    void followsTheHandCalculatedSequenceForPeriodThree() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(3);

        ema.update(price("1"));
        assertFalse(ema.isReady());

        ema.update(price("2"));
        assertFalse(ema.isReady());

        ema.update(price("3"));
        assertTrue(ema.isReady());
        assertEquals(2.0, ema.value(), TOLERANCE);

        ema.update(price("4"));
        assertEquals(3.0, ema.value(), TOLERANCE);

        ema.update(price("5"));
        assertEquals(4.0, ema.value(), TOLERANCE);

        ema.update(price("6"));
        assertEquals(5.0, ema.value(), TOLERANCE);
    }

    @Test
    void becomesReadyExactlyOnTheNthClose() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(3);

        ema.update(price("10"));
        ema.update(price("20"));
        assertFalse(ema.isReady());

        ema.update(price("30"));
        assertTrue(ema.isReady());
    }

    @Test
    void seedEqualsTheSimpleMovingAverageOfTheFirstNValues() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(4);

        ema.update(price("2"));
        ema.update(price("4"));
        ema.update(price("6"));
        ema.update(price("8"));

        assertEquals(5.0, ema.value(), TOLERANCE);
    }

    @Test
    void theSeedMustBeTheAverageNotTheFirstOrLastWarmUpClose() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(3);

        ema.update(price("0"));
        ema.update(price("0"));
        ema.update(price("30"));
        assertEquals(10.0, ema.value(), TOLERANCE);

        ema.update(price("100"));
        assertEquals(55.0, ema.value(), TOLERANCE);

        ema.update(price("100"));
        assertEquals(77.5, ema.value(), TOLERANCE);

        ema.update(price("100"));
        assertEquals(88.75, ema.value(), TOLERANCE);
    }

    @Test
    void recurrenceAppliesToEachSubsequentCloseUsingThePreviousEma() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(2);

        ema.update(price("100"));
        ema.update(price("200"));
        assertEquals(150.0, ema.value(), TOLERANCE);

        ema.update(price("300"));
        assertEquals(150.0 + (2.0 / 3.0) * (300.0 - 150.0), ema.value(), TOLERANCE);

        double previous = ema.value();
        ema.update(price("50"));
        assertEquals(previous + (2.0 / 3.0) * (50.0 - previous), ema.value(), TOLERANCE);
    }

    @Test
    void periodOneTracksTheLatestCloseExactly() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(1);

        ema.update(price("5"));
        assertTrue(ema.isReady());
        assertEquals(5.0, ema.value(), TOLERANCE);

        ema.update(price("9"));
        assertEquals(9.0, ema.value(), TOLERANCE);
    }

    @Test
    void repeatedIdenticalValuesProduceThatSameValue() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(3);

        ema.update(price("10"));
        ema.update(price("10"));
        ema.update(price("10"));
        assertEquals(10.0, ema.value(), TOLERANCE);

        ema.update(price("10"));
        assertEquals(10.0, ema.value(), TOLERANCE);
    }

    @Test
    void decimalClosesProduceTheCorrectSeedAndRecurrenceWithinTolerance() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(2);

        ema.update(price("1.5"));
        ema.update(price("2.5"));
        assertEquals(2.0, ema.value(), 1e-9);

        ema.update(price("3.5"));
        assertEquals(2.0 + (2.0 / 3.0) * (3.5 - 2.0), ema.value(), 1e-9);
    }

    @Test
    void valueBeforeReadinessThrowsIllegalStateException() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(3);

        assertThrows(IllegalStateException.class, ema::value);

        ema.update(price("1"));
        assertThrows(IllegalStateException.class, ema::value);

        ema.update(price("2"));
        assertThrows(IllegalStateException.class, ema::value);
    }

    @Test
    void twoInstancesWithTheSamePeriodAreIndependent() {
        ExponentialMovingAverage first = new ExponentialMovingAverage(2);
        ExponentialMovingAverage second = new ExponentialMovingAverage(2);

        first.update(price("100"));
        first.update(price("200"));

        assertFalse(second.isReady());

        second.update(price("10"));
        second.update(price("20"));

        assertEquals(150.0, first.value(), TOLERANCE);
        assertEquals(15.0, second.value(), TOLERANCE);
    }

    @Test
    void rejectsAPeriodBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new ExponentialMovingAverage(0));
    }

    @Test
    void hugePeriodConstructsAndAcceptsAFewUpdatesWithoutAllocatingPeriodSizedMemory() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(Integer.MAX_VALUE);

        assertFalse(ema.isReady());
        ema.update(price("100"));
        ema.update(price("101"));
        ema.update(price("102"));
        assertFalse(ema.isReady());
        assertThrows(IllegalStateException.class, ema::value);
    }

    @Test
    void alphaForAHugePeriodIsATinyPositiveValueNotANegativeOneFromIntOverflow() throws Exception {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(Integer.MAX_VALUE);

        Field alphaField = ExponentialMovingAverage.class.getDeclaredField("alpha");
        alphaField.setAccessible(true);
        double alpha = (double) alphaField.get(ema);

        assertTrue(alpha > 0.0, "alpha must be positive, was " + alpha);
        assertEquals(2.0 / (Integer.MAX_VALUE + 1.0), alpha, 0.0);
    }

    @Test
    void alphaMatchesTheDocumentedFormulaExactlyForRepresentativePeriods() throws Exception {
        for (int period : new int[] {2, 14, 20, 50}) {
            ExponentialMovingAverage ema = new ExponentialMovingAverage(period);
            Field alphaField = ExponentialMovingAverage.class.getDeclaredField("alpha");
            alphaField.setAccessible(true);
            double alpha = (double) alphaField.get(ema);

            double expectedOldExpression = 2.0 / (period + 1);
            assertEquals(expectedOldExpression, alpha, 0.0,
                    "alpha for period " + period + " must be bit-identical to the pre-change expression");
        }
    }
}
