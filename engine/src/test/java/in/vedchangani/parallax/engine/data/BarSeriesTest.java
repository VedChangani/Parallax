package in.vedchangani.parallax.engine.data;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BarSeriesTest {

    private static Bar bar(String date) {
        return new Bar(LocalDate.parse(date), new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("9"),
                new BigDecimal("11"), 1000);
    }

    @Test
    void acceptsAValidSeriesWithAscendingDates() {
        BarSeries series = assertDoesNotThrow(
                () -> new BarSeries("AAPL", List.of(bar("2024-01-02"), bar("2024-01-03"), bar("2024-01-04"))));
        assertEquals("AAPL", series.symbol());
        assertEquals(3, series.bars().size());
    }

    @Test
    void rejectsNullSymbol() {
        assertThrows(NullPointerException.class, () -> new BarSeries(null, List.of(bar("2024-01-02"))));
    }

    @Test
    void rejectsBlankSymbol() {
        assertThrows(IllegalArgumentException.class, () -> new BarSeries("   ", List.of(bar("2024-01-02"))));
    }

    @Test
    void rejectsNullBarList() {
        assertThrows(NullPointerException.class, () -> new BarSeries("AAPL", null));
    }

    @Test
    void rejectsEmptyBarList() {
        assertThrows(IllegalArgumentException.class, () -> new BarSeries("AAPL", List.of()));
    }

    @Test
    void rejectsDuplicateDate() {
        assertThrows(IllegalArgumentException.class,
                () -> new BarSeries("AAPL", List.of(bar("2024-01-02"), bar("2024-01-02"))));
    }

    @Test
    void rejectsOutOfOrderDates() {
        assertThrows(IllegalArgumentException.class,
                () -> new BarSeries("AAPL", List.of(bar("2024-01-03"), bar("2024-01-02"))));
    }

    @Test
    void returnedBarListCannotBeModified() {
        BarSeries series = new BarSeries("AAPL", List.of(bar("2024-01-02"), bar("2024-01-03")));
        assertThrows(UnsupportedOperationException.class, () -> series.bars().add(bar("2024-01-04")));
    }

    @Test
    void takesADefensiveCopyOfTheInputList() {
        List<Bar> mutableInput = new ArrayList<>(List.of(bar("2024-01-02"), bar("2024-01-03")));
        BarSeries series = new BarSeries("AAPL", mutableInput);

        mutableInput.add(bar("2024-01-04"));

        assertEquals(2, series.bars().size());
    }
}
