package in.vedchangani.parallax.engine.indicator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndicatorSpecTest {

    @Test
    void acceptsAValidSmaSpecification() {
        IndicatorSpec spec = assertDoesNotThrow(() -> new IndicatorSpec(IndicatorType.SMA, 20));
        assertEquals(IndicatorType.SMA, spec.type());
        assertEquals(20, spec.period());
    }

    @Test
    void acceptsAValidEmaSpecification() {
        assertDoesNotThrow(() -> new IndicatorSpec(IndicatorType.EMA, 20));
    }

    @Test
    void acceptsAValidRsiSpecification() {
        assertDoesNotThrow(() -> new IndicatorSpec(IndicatorType.RSI, 14));
    }

    @Test
    void rejectsNullType() {
        assertThrows(NullPointerException.class, () -> new IndicatorSpec(null, 20));
    }

    @Test
    void rejectsZeroPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new IndicatorSpec(IndicatorType.SMA, 0));
    }

    @Test
    void rejectsNegativePeriod() {
        assertThrows(IllegalArgumentException.class, () -> new IndicatorSpec(IndicatorType.EMA, -1));
    }

    @Test
    void rejectsRsiPeriodOfOne() {
        assertThrows(IllegalArgumentException.class, () -> new IndicatorSpec(IndicatorType.RSI, 1));
    }

    @Test
    void acceptsRsiPeriodOfTwo() {
        assertDoesNotThrow(() -> new IndicatorSpec(IndicatorType.RSI, 2));
    }

    @Test
    void acceptsSmaPeriodOfOne() {
        assertDoesNotThrow(() -> new IndicatorSpec(IndicatorType.SMA, 1));
    }

    @Test
    void specsWithTheSameTypeAndPeriodAreEqual() {
        assertEquals(new IndicatorSpec(IndicatorType.SMA, 20), new IndicatorSpec(IndicatorType.SMA, 20));
        assertEquals(new IndicatorSpec(IndicatorType.SMA, 20).hashCode(),
                new IndicatorSpec(IndicatorType.SMA, 20).hashCode());
    }

    @Test
    void specsWithDifferentPeriodsAreNotEqual() {
        assertNotEquals(new IndicatorSpec(IndicatorType.SMA, 20), new IndicatorSpec(IndicatorType.SMA, 50));
    }

    @Test
    void specsWithDifferentTypesAreNotEqual() {
        assertNotEquals(new IndicatorSpec(IndicatorType.SMA, 14), new IndicatorSpec(IndicatorType.RSI, 14));
    }
}
