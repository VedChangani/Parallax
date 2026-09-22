package in.vedchangani.parallax.engine.indicator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleMovingAverageTest {

    private static final double TOLERANCE = 1e-9;

    private static BigDecimal price(String value) {
        return new BigDecimal(value);
    }

    @Test
    void followsTheHandCalculatedSequenceForPeriodThree() {
        SimpleMovingAverage sma = new SimpleMovingAverage(3);

        sma.update(price("1"));
        assertFalse(sma.isReady());

        sma.update(price("2"));
        assertFalse(sma.isReady());

        sma.update(price("3"));
        assertTrue(sma.isReady());
        assertEquals(2.0, sma.value(), TOLERANCE);

        sma.update(price("4"));
        assertEquals(3.0, sma.value(), TOLERANCE);

        sma.update(price("5"));
        assertEquals(4.0, sma.value(), TOLERANCE);

        sma.update(price("6"));
        assertEquals(5.0, sma.value(), TOLERANCE);
    }

    @Test
    void becomesReadyExactlyOnTheNthClose() {
        SimpleMovingAverage sma = new SimpleMovingAverage(3);

        sma.update(price("10"));
        sma.update(price("20"));
        assertFalse(sma.isReady());

        sma.update(price("30"));
        assertTrue(sma.isReady());
    }

    @Test
    void theWindowRollsPastTheInitialWarmUpValues() {
        SimpleMovingAverage sma = new SimpleMovingAverage(3);

        for (String p : new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"}) {
            sma.update(price(p));
        }

        // last three closes: 8, 9, 10 -> mean 9
        assertEquals(9.0, sma.value(), TOLERANCE);
    }

    @Test
    void valueBeforeReadinessThrowsIllegalStateException() {
        SimpleMovingAverage sma = new SimpleMovingAverage(3);

        assertThrows(IllegalStateException.class, sma::value);

        sma.update(price("1"));
        assertThrows(IllegalStateException.class, sma::value);

        sma.update(price("2"));
        assertThrows(IllegalStateException.class, sma::value);
    }

    @Test
    void periodOneTracksTheLatestCloseExactly() {
        SimpleMovingAverage sma = new SimpleMovingAverage(1);

        sma.update(price("5"));
        assertTrue(sma.isReady());
        assertEquals(5.0, sma.value(), TOLERANCE);

        sma.update(price("7"));
        assertEquals(7.0, sma.value(), TOLERANCE);
    }

    @Test
    void repeatedIdenticalValuesProduceThatSameValue() {
        SimpleMovingAverage sma = new SimpleMovingAverage(4);

        for (int i = 0; i < 4; i++) {
            sma.update(price("10"));
        }

        assertEquals(10.0, sma.value(), TOLERANCE);

        sma.update(price("10"));
        assertEquals(10.0, sma.value(), TOLERANCE);
    }

    @Test
    void decimalClosesProduceTheCorrectMeanWithinTolerance() {
        SimpleMovingAverage sma = new SimpleMovingAverage(3);

        sma.update(price("1.1"));
        sma.update(price("2.2"));
        sma.update(price("3.3"));

        // (1.1 + 2.2 + 3.3) / 3 = 2.2
        assertEquals(2.2, sma.value(), 1e-9);
    }

    @Test
    void twoInstancesWithTheSamePeriodAreIndependent() {
        SimpleMovingAverage first = new SimpleMovingAverage(2);
        SimpleMovingAverage second = new SimpleMovingAverage(2);

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
        assertThrows(IllegalArgumentException.class, () -> new SimpleMovingAverage(0));
    }
}
