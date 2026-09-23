package in.vedchangani.parallax.engine.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CashFractionTest {

    @Test
    void halfIsAccepted() {
        PositionSizing.CashFraction fraction = new PositionSizing.CashFraction(new BigDecimal("0.5"));

        assertEquals(new BigDecimal("0.5"), fraction.fraction());
    }

    @Test
    void oneIsAccepted() {
        new PositionSizing.CashFraction(BigDecimal.ONE);
    }

    @Test
    void aSmallFractionIsAccepted() {
        new PositionSizing.CashFraction(new BigDecimal("0.0001"));
    }

    @Test
    void zeroIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PositionSizing.CashFraction(BigDecimal.ZERO));
    }

    @Test
    void negativeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PositionSizing.CashFraction(new BigDecimal("-0.5")));
    }

    @Test
    void aboveOneIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PositionSizing.CashFraction(new BigDecimal("1.0001")));
    }

    @Test
    void nullIsRejected() {
        assertThrows(NullPointerException.class, () -> new PositionSizing.CashFraction(null));
    }

    @Test
    void halfEqualsHalfWithTrailingZero() {
        PositionSizing.CashFraction a = new PositionSizing.CashFraction(new BigDecimal("0.5"));
        PositionSizing.CashFraction b = new PositionSizing.CashFraction(new BigDecimal("0.50"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void oneEqualsOneWithTrailingZeros() {
        PositionSizing.CashFraction a = new PositionSizing.CashFraction(BigDecimal.ONE);
        PositionSizing.CashFraction b = new PositionSizing.CashFraction(new BigDecimal("1.00"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void storedValueIsNormalized() {
        PositionSizing.CashFraction fraction = new PositionSizing.CashFraction(new BigDecimal("0.500"));

        assertEquals(0, new BigDecimal("0.5").compareTo(fraction.fraction()));
    }
}
