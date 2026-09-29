package in.vedchangani.parallax.engine.indicator;

import in.vedchangani.parallax.engine.data.Bar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageTrueRangeTest {

    private static final double TOLERANCE = 1e-9;

    private static Bar bar(String open, String high, String low, String close) {
        return new Bar(LocalDate.of(2024, 1, 1), new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), 0);
    }

    private static final Bar B1 = bar("10", "12", "9", "11");
    private static final Bar B2 = bar("15", "16", "14", "15");
    private static final Bar B3 = bar("9", "10", "8", "9");
    private static final Bar B4 = bar("9", "11", "9", "10");
    private static final Bar B5 = bar("10", "11", "9", "10");

    @Test
    void firstBarTrueRangeIsHighMinusLow() {
        AverageTrueRange atr = new AverageTrueRange(1);

        atr.update(B1);

        assertEquals(3.0, atr.value(), TOLERANCE);
    }

    @Test
    void firstBarIgnoresAnyNotionOfPreviousClose() {
        AverageTrueRange atr = new AverageTrueRange(1);

        atr.update(bar("100", "101", "99", "100"));

        assertEquals(2.0, atr.value(), TOLERANCE);
    }

    @Test
    void trueRangeUsesHighMinusPreviousCloseOnAGapUp() {
        AverageTrueRange atr = new AverageTrueRange(1);
        atr.update(B1);

        atr.update(B2);

        assertEquals(5.0, atr.value(), TOLERANCE);
    }

    @Test
    void trueRangeUsesPreviousCloseMinusLowOnAGapDown() {
        AverageTrueRange atr = new AverageTrueRange(1);
        atr.update(B1);
        atr.update(B2);

        atr.update(B3);

        assertEquals(7.0, atr.value(), TOLERANCE);
    }

    @Test
    void trueRangeUsesHighMinusLowWhenItIsTheLargest() {
        AverageTrueRange atr = new AverageTrueRange(1);
        atr.update(B4);

        atr.update(B5);

        assertEquals(2.0, atr.value(), TOLERANCE);
    }

    @Test
    void isNotReadyUntilPeriodBars() {
        AverageTrueRange atr = new AverageTrueRange(3);

        atr.update(B1);
        assertFalse(atr.isReady());

        atr.update(B2);
        assertFalse(atr.isReady());

        atr.update(B3);
        assertTrue(atr.isReady());
    }

    @Test
    void periodOneIsReadyOnTheFirstBar() {
        AverageTrueRange atr = new AverageTrueRange(1);

        assertFalse(atr.isReady());
        atr.update(B1);
        assertTrue(atr.isReady());
    }

    @Test
    void seedIsTheSimpleMeanOfTheFirstPeriodTrueRanges() {
        AverageTrueRange atr = new AverageTrueRange(3);

        atr.update(B1);
        atr.update(B2);
        atr.update(B3);

        assertEquals(5.0, atr.value(), TOLERANCE);
    }

    @Test
    void appliesWilderSmoothingAfterSeeding() {
        AverageTrueRange atr = new AverageTrueRange(3);
        atr.update(B1);
        atr.update(B2);
        atr.update(B3);

        atr.update(B4);
        assertEquals((5.0 * 2 + 2) / 3, atr.value(), TOLERANCE);

        atr.update(B5);
        assertEquals((4.0 * 2 + 2) / 3, atr.value(), TOLERANCE);
    }

    @Test
    void valueBeforeReadinessThrowsIllegalStateException() {
        AverageTrueRange atr = new AverageTrueRange(2);

        assertThrows(IllegalStateException.class, atr::value);

        atr.update(B1);
        assertThrows(IllegalStateException.class, atr::value);
    }

    @Test
    void closeOnlyUpdateIsUnsupported() {
        AverageTrueRange atr = new AverageTrueRange(2);

        assertThrows(UnsupportedOperationException.class, () -> atr.update(new BigDecimal("100")));
    }

    @Test
    void rejectsAPeriodBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new AverageTrueRange(0));
        assertThrows(IllegalArgumentException.class, () -> new AverageTrueRange(-1));
    }

    @Test
    void twoInstancesAreIndependent() {
        AverageTrueRange first = new AverageTrueRange(1);
        AverageTrueRange second = new AverageTrueRange(1);

        first.update(B1);
        first.update(B2);

        assertFalse(second.isReady());
        second.update(B3);

        assertEquals(5.0, first.value(), TOLERANCE);
        assertEquals(2.0, second.value(), TOLERANCE);
    }

    @Test
    void hugePeriodConstructsAndAcceptsAFewUpdatesWithoutAllocatingPeriodSizedMemory() {
        AverageTrueRange atr = new AverageTrueRange(Integer.MAX_VALUE);

        atr.update(B1);
        atr.update(B2);

        assertFalse(atr.isReady());
        assertThrows(IllegalStateException.class, atr::value);
    }

    @Test
    void defaultBarUpdateFeedsCloseBasedIndicatorsTheirClose() {
        Indicator viaBar = new SimpleMovingAverage(2);
        Indicator viaClose = new SimpleMovingAverage(2);

        viaBar.update(B1);
        viaBar.update(B2);
        viaClose.update(B1.close());
        viaClose.update(B2.close());

        assertEquals(viaClose.value(), viaBar.value(), 0.0);
        assertEquals(13.0, viaBar.value(), TOLERANCE);
    }
}
