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

    // A. Readiness boundary: not ready through the 3rd close, ready after the 4th.
    @Test
    void isNotReadyUntilPeriodPlusOneCloses() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        assertFalse(rsi.isReady()); // first close only establishes previousClose

        rsi.update(price("101"));
        assertFalse(rsi.isReady()); // 1 change observed

        rsi.update(price("102"));
        assertFalse(rsi.isReady()); // 2 changes observed

        rsi.update(price("103"));
        assertTrue(rsi.isReady()); // 3rd change observed -> ready
    }

    // B. Monotonic increase: all gains, no losses -> RSI = 100.
    @Test
    void monotonicIncreaseProducesRsiOfOneHundred() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        rsi.update(price("101"));
        rsi.update(price("102"));
        rsi.update(price("103"));

        assertEquals(100.0, rsi.value(), TOLERANCE);
    }

    // C. Flat market: no gains, no losses -> RSI = 50.
    @Test
    void flatMarketProducesRsiOfFifty() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        rsi.update(price("100"));
        rsi.update(price("100"));
        rsi.update(price("100"));

        assertEquals(50.0, rsi.value(), TOLERANCE);
    }

    // D. Monotonic decrease: all losses, no gains -> RSI = 0.
    @Test
    void monotonicDecreaseProducesRsiOfZero() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("103"));
        rsi.update(price("102"));
        rsi.update(price("101"));
        rsi.update(price("100"));

        assertEquals(0.0, rsi.value(), TOLERANCE);
    }

    // E. Hand-calculated mixed fixture: changes +2, -1, +2 -> avgGain 4/3, avgLoss 1/3, RS 4, RSI 80.
    @Test
    void mixedFixtureMatchesHandCalculatedRsi() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        rsi.update(price("102")); // +2
        rsi.update(price("101")); // -1
        rsi.update(price("103")); // +2

        assertTrue(rsi.isReady());
        assertEquals(80.0, rsi.value(), TOLERANCE);
    }

    // F. Wilder smoothing: one more change applied on top of the mixed fixture.
    @Test
    void wilderSmoothingAppliesToTheNextChangeAfterSeeding() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(3);

        rsi.update(price("100"));
        rsi.update(price("102")); // +2
        rsi.update(price("101")); // -1
        rsi.update(price("103")); // +2, seeds avgGain=4/3, avgLoss=1/3

        rsi.update(price("105")); // +2 change

        // avgGain = (4/3 * 2 + 2) / 3 = 14/9
        // avgLoss = (1/3 * 2 + 0) / 3 = 2/9
        // RS = 7, RSI = 100 - 100/8 = 87.5
        assertEquals(87.5, rsi.value(), TOLERANCE);
    }

    // G. value() before readiness must fail per the Indicator contract.
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

    // H. Period validation: the RSI runtime itself enforces period >= 2
    // (not only IndicatorSpec), so RSI(0) and RSI(1) are rejected directly
    // at construction.
    @Test
    void rejectsAPeriodBelowTwo() {
        assertThrows(IllegalArgumentException.class, () -> new RelativeStrengthIndex(0));
        assertThrows(IllegalArgumentException.class, () -> new RelativeStrengthIndex(1));
    }

    // H2. Period validation: 2 is the minimum accepted period.
    @Test
    void acceptsAPeriodOfTwo() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(2);

        rsi.update(price("100"));
        rsi.update(price("101"));
        rsi.update(price("102"));

        assertTrue(rsi.isReady());
    }

    // I. Instance independence.
    @Test
    void twoInstancesWithTheSamePeriodAreIndependent() {
        RelativeStrengthIndex first = new RelativeStrengthIndex(3);
        RelativeStrengthIndex second = new RelativeStrengthIndex(3);

        first.update(price("100"));
        first.update(price("101"));
        first.update(price("102"));
        first.update(price("103")); // all gains -> RSI 100

        assertFalse(second.isReady());

        second.update(price("100"));
        second.update(price("100"));
        second.update(price("100"));
        second.update(price("100")); // flat -> RSI 50

        assertEquals(100.0, first.value(), TOLERANCE);
        assertEquals(50.0, second.value(), TOLERANCE);
    }

    // J. Repeated/flat closes must not create spurious gain/loss state.
    @Test
    void repeatedValuesDuringWarmUpProduceNoGainOrLoss() {
        RelativeStrengthIndex rsi = new RelativeStrengthIndex(2);

        rsi.update(price("50"));
        rsi.update(price("50"));
        rsi.update(price("50"));

        assertTrue(rsi.isReady());
        assertEquals(50.0, rsi.value(), TOLERANCE);

        // A real gain after a flat run must be reflected, proving no leftover
        // gain/loss state was accumulated from the repeated values.
        rsi.update(price("60"));
        // avgGain = (0 * 1 + 10) / 2 = 5, avgLoss = (0 * 1 + 0) / 2 = 0 -> RSI 100
        assertEquals(100.0, rsi.value(), TOLERANCE);
    }

    // --- Phase 9 Batch 2a: RSI was already O(1) memory (no array of any size) -
    // this only confirms construction/update for a huge period stays unaffected.

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
