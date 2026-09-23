package in.vedchangani.parallax.engine.portfolio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EquityPointTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);

    private static EquityPoint flat(BigDecimal cash) {
        return new EquityPoint(DATE, cash, 0, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"));
    }

    private static EquityPoint longPosition(BigDecimal cash, long quantity, BigDecimal costBasis,
                                             BigDecimal realizedPnl, BigDecimal close) {
        return new EquityPoint(DATE, cash, quantity, costBasis, realizedPnl, close);
    }

    // --- derived methods -----------------------------------------------------

    @Test
    void derivedValuesCorrectWhenLong() {
        EquityPoint point = longPosition(new BigDecimal("8995"), 10, new BigDecimal("1005"),
                BigDecimal.ZERO, new BigDecimal("110"));

        assertEquals(0, point.marketValue().compareTo(new BigDecimal("1100")));
        assertEquals(0, point.unrealizedPnl().compareTo(new BigDecimal("95")));
        assertEquals(0, point.equity().compareTo(new BigDecimal("10095")));
    }

    @Test
    void derivedValuesCorrectWhenFlat() {
        EquityPoint point = flat(new BigDecimal("10190"));

        assertEquals(0, point.marketValue().compareTo(BigDecimal.ZERO));
        assertEquals(0, point.unrealizedPnl().compareTo(BigDecimal.ZERO));
        assertEquals(0, point.equity().compareTo(new BigDecimal("10190")));
    }

    // --- validation ------------------------------------------------------------

    @Test
    void nullDateRejected() {
        assertThrows(NullPointerException.class,
                () -> new EquityPoint(null, BigDecimal.TEN, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.TEN));
    }

    @Test
    void nullCashRejected() {
        assertThrows(NullPointerException.class,
                () -> new EquityPoint(DATE, null, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN));
    }

    @Test
    void nullCostBasisRejected() {
        assertThrows(NullPointerException.class,
                () -> new EquityPoint(DATE, BigDecimal.TEN, 0, null, BigDecimal.ZERO, BigDecimal.TEN));
    }

    @Test
    void nullRealizedPnlRejected() {
        assertThrows(NullPointerException.class,
                () -> new EquityPoint(DATE, BigDecimal.TEN, 0, BigDecimal.ZERO, null, BigDecimal.TEN));
    }

    @Test
    void nullCloseRejected() {
        assertThrows(NullPointerException.class,
                () -> new EquityPoint(DATE, BigDecimal.TEN, 0, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Test
    void negativeCashRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EquityPoint(DATE, new BigDecimal("-1"), 0, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.TEN));
    }

    @Test
    void negativeQuantityRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EquityPoint(DATE, BigDecimal.TEN, -1, new BigDecimal("5"), BigDecimal.ZERO,
                        BigDecimal.TEN));
    }

    @Test
    void nonPositiveCloseRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EquityPoint(DATE, BigDecimal.TEN, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new EquityPoint(DATE, BigDecimal.TEN, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("-1")));
    }

    @Test
    void flatWithNonZeroCostBasisRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EquityPoint(DATE, BigDecimal.TEN, 0, new BigDecimal("5"), BigDecimal.ZERO,
                        BigDecimal.TEN));
    }

    @Test
    void longWithZeroCostBasisRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EquityPoint(DATE, BigDecimal.TEN, 10, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.TEN));
    }

    @Test
    void longWithNegativeCostBasisRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EquityPoint(DATE, BigDecimal.TEN, 10, new BigDecimal("-5"), BigDecimal.ZERO,
                        BigDecimal.TEN));
    }

    @Test
    void negativeRealizedPnlAccepted() {
        EquityPoint point = flat(new BigDecimal("9890"));
        new EquityPoint(DATE, new BigDecimal("9890"), 0, BigDecimal.ZERO, new BigDecimal("-110"),
                new BigDecimal("100"));

        assertEquals(0, point.cash().compareTo(new BigDecimal("9890")));
    }

    // --- equality --------------------------------------------------------------

    @Test
    void equalPointsAreEqualAndHaveEqualHashCode() {
        EquityPoint a = longPosition(new BigDecimal("8995"), 10, new BigDecimal("1005"), BigDecimal.ZERO,
                new BigDecimal("100"));
        EquityPoint b = longPosition(new BigDecimal("8995"), 10, new BigDecimal("1005"), BigDecimal.ZERO,
                new BigDecimal("100"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
