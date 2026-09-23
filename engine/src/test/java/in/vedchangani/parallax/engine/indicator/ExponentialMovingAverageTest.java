package in.vedchangani.parallax.engine.indicator;

import org.junit.jupiter.api.Test;

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
        // seed = SMA(1, 2, 3) = 2
        assertEquals(2.0, ema.value(), TOLERANCE);

        // alpha = 2 / (3 + 1) = 0.5
        ema.update(price("4"));
        assertEquals(3.0, ema.value(), TOLERANCE); // 0.5*4 + 0.5*2

        ema.update(price("5"));
        assertEquals(4.0, ema.value(), TOLERANCE); // 0.5*5 + 0.5*3

        ema.update(price("6"));
        assertEquals(5.0, ema.value(), TOLERANCE); // 0.5*6 + 0.5*4
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

        // SMA(2, 4, 6, 8) = 5, not the last close (8) and not the first close (2)
        assertEquals(5.0, ema.value(), TOLERANCE);
    }

    @Test
    void theSeedMustBeTheAverageNotTheFirstOrLastWarmUpClose() {
        // First 3 closes average to 10, but the first close is 0 and the last is 30.
        // An implementation that (incorrectly) seeds from the first or last warm-up
        // close, rather than their average, will diverge visibly from this point on.
        ExponentialMovingAverage ema = new ExponentialMovingAverage(3);

        ema.update(price("0"));
        ema.update(price("0"));
        ema.update(price("30"));
        assertEquals(10.0, ema.value(), TOLERANCE); // correct seed: SMA(0, 0, 30) = 10

        // alpha = 2 / (3 + 1) = 0.5
        ema.update(price("100"));
        // correct:            0.5*100 + 0.5*10   = 55
        // wrong seed = last:  0.5*100 + 0.5*30   = 65
        // wrong seed = first: 0.5*100 + 0.5*0    = 50
        assertEquals(55.0, ema.value(), TOLERANCE);

        ema.update(price("100"));
        assertEquals(77.5, ema.value(), TOLERANCE); // 0.5*100 + 0.5*55

        ema.update(price("100"));
        assertEquals(88.75, ema.value(), TOLERANCE); // 0.5*100 + 0.5*77.5
    }

    @Test
    void recurrenceAppliesToEachSubsequentCloseUsingThePreviousEma() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(2);

        ema.update(price("100"));
        ema.update(price("200"));
        assertEquals(150.0, ema.value(), TOLERANCE); // seed SMA(100, 200)

        // alpha = 2 / (2 + 1) = 2/3
        ema.update(price("300"));
        assertEquals(150.0 + (2.0 / 3.0) * (300.0 - 150.0), ema.value(), TOLERANCE);

        double previous = ema.value();
        ema.update(price("50"));
        assertEquals(previous + (2.0 / 3.0) * (50.0 - previous), ema.value(), TOLERANCE);
    }

    @Test
    void periodOneTracksTheLatestCloseExactly() {
        // alpha = 2 / (1 + 1) = 1, so EMA(1) reduces to the latest close.
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
        // seed = SMA(1.5, 2.5) = 2.0
        assertEquals(2.0, ema.value(), 1e-9);

        // alpha = 2/3
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
}
