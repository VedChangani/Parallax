package in.vedchangani.parallax.engine.indicator;

import org.junit.jupiter.api.Test;

import in.vedchangani.parallax.engine.data.Bar;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Indicator#create(IndicatorSpec)}, the only mapping
 * from an {@link IndicatorSpec} definition to a runtime {@link Indicator}
 * instance (D-25).
 */
class IndicatorTest {

    private static BigDecimal price(String value) {
        return new BigDecimal(value);
    }

    @Test
    void smaSpecCreatesSimpleMovingAverage() {
        Indicator indicator = Indicator.create(new IndicatorSpec(IndicatorType.SMA, 3));

        assertInstanceOf(SimpleMovingAverage.class, indicator);
    }

    @Test
    void emaSpecCreatesExponentialMovingAverage() {
        Indicator indicator = Indicator.create(new IndicatorSpec(IndicatorType.EMA, 3));

        assertInstanceOf(ExponentialMovingAverage.class, indicator);
    }

    @Test
    void rsiSpecCreatesRelativeStrengthIndex() {
        Indicator indicator = Indicator.create(new IndicatorSpec(IndicatorType.RSI, 3));

        assertInstanceOf(RelativeStrengthIndex.class, indicator);
    }

    @Test
    void atrSpecCreatesAverageTrueRange() {
        Indicator indicator = Indicator.create(new IndicatorSpec(IndicatorType.ATR, 3));

        assertInstanceOf(AverageTrueRange.class, indicator);
    }

    @Test
    void rocSpecCreatesRateOfChange() {
        Indicator indicator = Indicator.create(new IndicatorSpec(IndicatorType.ROC, 3));

        assertInstanceOf(RateOfChange.class, indicator);
    }

    @Test
    void atrAndRocPeriodsArePreservedAndObservableThroughReadiness() {
        // ATR(2): not ready until the 2nd bar.
        Indicator atr = Indicator.create(new IndicatorSpec(IndicatorType.ATR, 2));
        Bar bar = new Bar(LocalDate.of(2024, 1, 1), price("10"), price("12"), price("9"), price("11"), 0);
        atr.update(bar);
        assertFalse(atr.isReady());
        atr.update(bar);
        assertTrue(atr.isReady());

        // ROC(2): not ready until the 3rd close.
        Indicator roc = Indicator.create(new IndicatorSpec(IndicatorType.ROC, 2));
        roc.update(price("100"));
        roc.update(price("101"));
        assertFalse(roc.isReady());
        roc.update(price("102"));
        assertTrue(roc.isReady());
    }

    @Test
    void periodIsPreservedAndObservableThroughReadiness() {
        // SMA(3): not ready until the 3rd close.
        Indicator sma = Indicator.create(new IndicatorSpec(IndicatorType.SMA, 3));
        sma.update(price("1"));
        sma.update(price("2"));
        assertFalse(sma.isReady());
        sma.update(price("3"));
        assertTrue(sma.isReady());
        assertEquals(2.0, sma.value(), 1e-9);

        // EMA(2): not ready until the 2nd close.
        Indicator ema = Indicator.create(new IndicatorSpec(IndicatorType.EMA, 2));
        ema.update(price("10"));
        assertFalse(ema.isReady());
        ema.update(price("20"));
        assertTrue(ema.isReady());

        // RSI(2): not ready until 3 closes (1 to seed previousClose + 2 changes).
        Indicator rsi = Indicator.create(new IndicatorSpec(IndicatorType.RSI, 2));
        rsi.update(price("100"));
        rsi.update(price("101"));
        assertFalse(rsi.isReady());
        rsi.update(price("102"));
        assertTrue(rsi.isReady());
    }

    @Test
    void twoCallsWithTheSameSpecReturnDistinctIndependentInstances() {
        IndicatorSpec spec = new IndicatorSpec(IndicatorType.SMA, 2);

        Indicator first = Indicator.create(spec);
        Indicator second = Indicator.create(spec);

        assertNotSame(first, second);

        first.update(price("100"));
        first.update(price("200"));

        assertFalse(second.isReady());

        second.update(price("10"));
        second.update(price("20"));

        assertEquals(150.0, first.value(), 1e-9);
        assertEquals(15.0, second.value(), 1e-9);
    }

    @Test
    void nullSpecRejected() {
        assertThrows(NullPointerException.class, () -> Indicator.create(null));
    }

    @Test
    void switchCoversAllCurrentIndicatorTypeValues() {
        // If a new IndicatorType is ever added without updating
        // Indicator.create's switch, this test starts failing to compile
        // (or, if create() were to gain a default branch, this loop would
        // catch the omission at runtime instead).
        for (IndicatorType type : IndicatorType.values()) {
            int period = (type == IndicatorType.RSI) ? 2 : 1;
            Indicator indicator = Indicator.create(new IndicatorSpec(type, period));
            assertTrue(indicator != null);
        }
    }
}
