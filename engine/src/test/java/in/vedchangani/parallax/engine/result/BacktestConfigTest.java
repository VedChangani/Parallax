package in.vedchangani.parallax.engine.result;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacktestConfigTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END = LocalDate.of(2024, 12, 31);

    private static BacktestConfig config(BigDecimal initialCapital, BigDecimal commission,
                                          BigDecimal slippage, LocalDate start, LocalDate end) {
        return new BacktestConfig(initialCapital, commission, slippage, start, end);
    }

    @Test
    void smallPositiveInitialCapitalAccepted() {
        config(new BigDecimal("0.01"), BigDecimal.ZERO, BigDecimal.ZERO, START, END);
    }

    @Test
    void zeroCommissionAccepted() {
        config(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, START, END);
    }

    @Test
    void zeroSlippageAccepted() {
        config(new BigDecimal("10000"), new BigDecimal("5"), BigDecimal.ZERO, START, END);
    }

    @Test
    void slippageJustBelowOneAccepted() {
        config(new BigDecimal("10000"), new BigDecimal("5"), new BigDecimal("0.999"), START, END);
    }

    @Test
    void sameDayRangeAccepted() {
        config(new BigDecimal("10000"), new BigDecimal("5"), new BigDecimal("0.01"), START, START);
    }

    @Test
    void normalValidRangeAccepted() {
        BacktestConfig cfg = config(new BigDecimal("10000"), new BigDecimal("5"),
                new BigDecimal("0.01"), START, END);

        assertEquals(START, cfg.startDate());
        assertEquals(END, cfg.endDate());
    }

    @Test
    void zeroInitialCapitalRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> config(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, START, END));
    }

    @Test
    void negativeInitialCapitalRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> config(new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO, START, END));
    }

    @Test
    void negativeCommissionRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> config(new BigDecimal("10000"), new BigDecimal("-0.01"), BigDecimal.ZERO, START, END));
    }

    @Test
    void negativeSlippageRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> config(new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("-0.01"), START, END));
    }

    @Test
    void slippageOfOneRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> config(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ONE, START, END));
    }

    @Test
    void slippageAboveOneRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> config(new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("1.5"), START, END));
    }

    @Test
    void startAfterEndRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> config(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, END, START));
    }

    @Test
    void nullFieldsRejected() {
        assertThrows(NullPointerException.class,
                () -> config(null, BigDecimal.ZERO, BigDecimal.ZERO, START, END));
        assertThrows(NullPointerException.class,
                () -> config(new BigDecimal("10000"), null, BigDecimal.ZERO, START, END));
        assertThrows(NullPointerException.class,
                () -> config(new BigDecimal("10000"), BigDecimal.ZERO, null, START, END));
        assertThrows(NullPointerException.class,
                () -> config(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, null, END));
        assertThrows(NullPointerException.class,
                () -> config(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, START, null));
    }

    @Test
    void tenThousandEqualsTenThousandPointZeroZero() {
        BacktestConfig a = config(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, START, END);
        BacktestConfig b = config(new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, START, END);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void pointZeroZeroTenEqualsPointZeroTen() {
        BacktestConfig a = config(new BigDecimal("10000"), new BigDecimal("0.0010"), BigDecimal.ZERO, START, END);
        BacktestConfig b = config(new BigDecimal("10000"), new BigDecimal("0.001"), BigDecimal.ZERO, START, END);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void storedCommissionIsCanonicalized() {
        BacktestConfig cfg = config(new BigDecimal("10000"), new BigDecimal("5.00"), BigDecimal.ZERO, START, END);

        assertEquals(new BigDecimal("5"), cfg.commissionPerFill());
    }

    @Test
    void slippageBoundGuaranteesPositiveSellFillPrice() {
        BigDecimal open = new BigDecimal("0.01");
        BigDecimal maxSlippage = new BigDecimal("0.999999999");
        BigDecimal sellFillPrice = open.multiply(BigDecimal.ONE.subtract(maxSlippage));

        assertEquals(1, sellFillPrice.compareTo(BigDecimal.ZERO));
    }
}
