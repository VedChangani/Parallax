package in.vedchangani.parallax.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacktesterSizingTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void d23CounterexampleSizesToNineNotTen() {
        long quantity = Backtester.enterQuantity(d("1000"), d("1"), d("99.5"), d("5"), d("0"));

        assertEquals(9, quantity);
    }

    @Test
    void fractionOneReservesOneCommission() {
        long quantity = Backtester.enterQuantity(d("1000"), d("1"), d("100"), d("5"), d("0"));

        assertEquals(9, quantity);
    }

    @Test
    void fractionHalfUsesTheSmallerOfTheTwoSpendableCaps() {
        long quantity = Backtester.enterQuantity(d("1000"), d("0.5"), d("100"), d("5"), d("0"));

        assertEquals(4, quantity);
    }

    @Test
    void slippageEntersThePerShareDenominator() {
        long withoutSlippage = Backtester.enterQuantity(d("1000"), d("1"), d("90"), d("5"), d("0"));
        assertEquals(11, withoutSlippage);

        long withSlippage = Backtester.enterQuantity(d("1000"), d("1"), d("90"), d("5"), d("0.1"));
        assertEquals(10, withSlippage);
    }

    @Test
    void numeratorExactlyZeroGivesZero() {
        long quantity = Backtester.enterQuantity(d("10"), d("1"), d("100"), d("5"), d("0"));

        assertEquals(0, quantity);
    }

    @Test
    void numeratorNegativeGivesZero() {
        long quantity = Backtester.enterQuantity(d("2"), d("1"), d("100"), d("5"), d("0"));

        assertEquals(0, quantity);
    }

    @Test
    void exactIntegerQuotient() {
        long quantity = Backtester.enterQuantity(d("1000"), d("1"), d("99"), d("5"), d("0"));

        assertEquals(10, quantity);
    }

    @Test
    void returnedQuantityIsWholeShares() {
        long quantity = Backtester.enterQuantity(d("1000"), d("1"), d("97"), d("5"), d("0"));

        assertEquals(10, quantity);
    }

    @Test
    void absurdOverflowFailsWithArithmeticException() {
        BigDecimal hugeCash = d("1").movePointRight(30);

        assertThrows(ArithmeticException.class,
                () -> Backtester.enterQuantity(hugeCash, d("1"), d("0.0000000001"), d("0"), d("0")));
    }
}
