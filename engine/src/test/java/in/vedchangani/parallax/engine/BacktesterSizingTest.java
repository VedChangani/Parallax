package in.vedchangani.parallax.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Backtester#enterQuantity}, the D-23 ENTER sizing
 * arithmetic, in isolation from the run loop.
 */
class BacktesterSizingTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void d23CounterexampleSizesToNineNotTen() {
        // capital 1000, commission 5, close 99.5, slippage 0.
        // Pre-D-23: floor(995/99.5) = 10, which left cash at exactly 0 and
        // could later go negative on exit. D-23 must give 9.
        long quantity = Backtester.enterQuantity(d("1000"), d("1"), d("99.5"), d("5"), d("0"));

        assertEquals(9, quantity);
    }

    @Test
    void fractionOneReservesOneCommission() {
        // spendable = min(1000*1, 1000-5) = 995; numerator = 995-5 = 990;
        // perShare = 100; quantity = floor(990/100) = 9.
        long quantity = Backtester.enterQuantity(d("1000"), d("1"), d("100"), d("5"), d("0"));

        assertEquals(9, quantity);
    }

    @Test
    void fractionHalfUsesTheSmallerOfTheTwoSpendableCaps() {
        // spendable = min(1000*0.5, 1000-5) = min(500, 995) = 500;
        // numerator = 500-5 = 495; perShare = 100; quantity = floor(495/100) = 4.
        long quantity = Backtester.enterQuantity(d("1000"), d("0.5"), d("100"), d("5"), d("0"));

        assertEquals(4, quantity);
    }

    @Test
    void slippageEntersThePerShareDenominator() {
        // spendable = min(1000, 995) = 995; numerator = 990.
        // With no slippage: perShare = 90; quantity = floor(990/90) = 11.
        long withoutSlippage = Backtester.enterQuantity(d("1000"), d("1"), d("90"), d("5"), d("0"));
        assertEquals(11, withoutSlippage);

        // With 10% slippage: perShare = 90 * 1.1 = 99; quantity = floor(990/99) = 10.
        long withSlippage = Backtester.enterQuantity(d("1000"), d("1"), d("90"), d("5"), d("0.1"));
        assertEquals(10, withSlippage);
    }

    @Test
    void numeratorExactlyZeroGivesZero() {
        // spendable = min(10, 5) = 5; numerator = 5-5 = 0.
        long quantity = Backtester.enterQuantity(d("10"), d("1"), d("100"), d("5"), d("0"));

        assertEquals(0, quantity);
    }

    @Test
    void numeratorNegativeGivesZero() {
        // spendable = min(2, -3) = -3; numerator = -3-5 = -8.
        long quantity = Backtester.enterQuantity(d("2"), d("1"), d("100"), d("5"), d("0"));

        assertEquals(0, quantity);
    }

    @Test
    void exactIntegerQuotient() {
        // spendable = min(1000, 995) = 995; numerator = 990;
        // perShare = 99; quotient is exactly 10.
        long quantity = Backtester.enterQuantity(d("1000"), d("1"), d("99"), d("5"), d("0"));

        assertEquals(10, quantity);
    }

    @Test
    void returnedQuantityIsWholeShares() {
        long quantity = Backtester.enterQuantity(d("1000"), d("1"), d("97"), d("5"), d("0"));

        // spendable=995, numerator=990, perShare=97 -> 990/97 = 10.20... -> 10
        assertEquals(10, quantity);
    }

    @Test
    void absurdOverflowFailsWithArithmeticException() {
        BigDecimal hugeCash = d("1").movePointRight(30);

        assertThrows(ArithmeticException.class,
                () -> Backtester.enterQuantity(hugeCash, d("1"), d("0.0000000001"), d("0"), d("0")));
    }
}
