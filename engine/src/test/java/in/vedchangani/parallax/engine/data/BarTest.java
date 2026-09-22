package in.vedchangani.parallax.engine.data;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BarTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);

    private static Bar bar(String open, String high, String low, String close, long volume) {
        return new Bar(DATE, new BigDecimal(open), new BigDecimal(high), new BigDecimal(low), new BigDecimal(close),
                volume);
    }

    @Test
    void acceptsAValidBar() {
        assertDoesNotThrow(() -> bar("10", "12", "9", "11", 1000));
    }

    @Test
    void acceptsAFlatBarWithZeroVolume() {
        assertDoesNotThrow(() -> bar("10", "10", "10", "10", 0));
    }

    @Test
    void rejectsNullDate() {
        assertThrows(NullPointerException.class,
                () -> new Bar(null, new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("9"),
                        new BigDecimal("11"), 1000));
    }

    @Test
    void rejectsZeroOpen() {
        assertThrows(IllegalArgumentException.class, () -> bar("0", "12", "9", "11", 1000));
    }

    @Test
    void rejectsZeroHigh() {
        assertThrows(IllegalArgumentException.class, () -> bar("10", "0", "9", "11", 1000));
    }

    @Test
    void rejectsZeroLow() {
        assertThrows(IllegalArgumentException.class, () -> bar("10", "12", "0", "11", 1000));
    }

    @Test
    void rejectsZeroClose() {
        assertThrows(IllegalArgumentException.class, () -> bar("10", "12", "9", "0", 1000));
    }

    @Test
    void rejectsNegativePrice() {
        assertThrows(IllegalArgumentException.class, () -> bar("-10", "12", "9", "11", 1000));
    }

    @Test
    void rejectsLowAboveOpen() {
        assertThrows(IllegalArgumentException.class, () -> bar("10", "12", "10.5", "11", 1000));
    }

    @Test
    void rejectsLowAboveClose() {
        assertThrows(IllegalArgumentException.class, () -> bar("10", "12", "10.5", "9.5", 1000));
    }

    @Test
    void rejectsHighBelowOpen() {
        assertThrows(IllegalArgumentException.class, () -> bar("10", "9.5", "9", "8", 1000));
    }

    @Test
    void rejectsHighBelowClose() {
        assertThrows(IllegalArgumentException.class, () -> bar("8", "9.5", "8", "10", 1000));
    }

    @Test
    void rejectsNegativeVolume() {
        assertThrows(IllegalArgumentException.class, () -> bar("10", "12", "9", "11", -1));
    }
}
