package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.backend.dataset.csv.CsvBarParser;
import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasetContentTest {

    private static final String GOLDEN_HASH =
            "27837be1306108d6059a414ef7635511cc15e234b6abc89c1eaa3e3573524fb7";

    private static BarSeries twoBarSeries() {
        return new BarSeries("AAPL", List.of(
                new Bar(LocalDate.of(2024, 1, 2), bd("100"), bd("105"), bd("99"), bd("104"), 1000L),
                new Bar(LocalDate.of(2024, 1, 3), bd("104"), bd("110"), bd("103"), bd("108"), 2000L)));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void goldenPayloadProducesTheIndependentlyComputedHash() {
        DatasetContent content = DatasetContent.of(twoBarSeries());

        assertEquals(GOLDEN_HASH, content.contentHash());
        assertEquals(2, content.barCount());
        assertEquals(LocalDate.of(2024, 1, 2), content.firstDate());
        assertEquals(LocalDate.of(2024, 1, 3), content.lastDate());
    }

    @Test
    void equivalentCsvSpellingsProduceTheSameHash() {
        byte[] plain = ("date,open,high,low,close,volume\n"
                + "2024-01-02,100,105,99,104,1000\n"
                + "2024-01-03,104,110,103,108,2000\n").getBytes(StandardCharsets.US_ASCII);
        byte[] spelledOut = ("date,open,high,low,close,volume\n"
                + "2024-01-02,100.0,105.00,99.000,104.0000,1000\n"
                + "2024-01-03,104.0,110.00,103.000,108.0,2000\n").getBytes(StandardCharsets.US_ASCII);

        String hashA = DatasetContent.of(new BarSeries("AAPL", CsvBarParser.parse(plain))).contentHash();
        String hashB = DatasetContent.of(new BarSeries("AAPL", CsvBarParser.parse(spelledOut))).contentHash();

        assertEquals(hashA, hashB);
        assertEquals(GOLDEN_HASH, hashA);
    }

    @Test
    void differentSymbolProducesADifferentHash() {
        String hashA = DatasetContent.of(twoBarSeries()).contentHash();
        String hashB = DatasetContent.of(new BarSeries("MSFT", twoBarSeries().bars())).contentHash();
        assertNotEquals(hashA, hashB);
    }

    @Test
    void changedBarProducesADifferentHash() {
        BarSeries changed = new BarSeries("AAPL", List.of(
                new Bar(LocalDate.of(2024, 1, 2), bd("100"), bd("105"), bd("99"), bd("104"), 1000L),
                new Bar(LocalDate.of(2024, 1, 3), bd("104"), bd("110"), bd("103"), bd("109"), 2000L)));

        assertNotEquals(DatasetContent.of(twoBarSeries()).contentHash(), DatasetContent.of(changed).contentHash());
    }

    @Test
    void nonCanonicalPriceIsRejectedRatherThanSilentlyNormalized() {
        BarSeries nonCanonical = new BarSeries("AAPL", List.of(
                new Bar(LocalDate.of(2024, 1, 2), new BigDecimal("100.00"), bd("105"), bd("99"), bd("104"), 1000L)));

        assertThrows(IllegalArgumentException.class, () -> DatasetContent.of(nonCanonical));
    }

    @Test
    void canonicalPriceStripsTrailingZerosAndNeverGoesNegativeScale() {
        assertEquals(new BigDecimal("100"), DatasetContent.canonicalPrice(new BigDecimal("100.00")));
        assertEquals(0, DatasetContent.canonicalPrice(new BigDecimal("100.00")).scale());
        assertEquals(new BigDecimal("1.5"), DatasetContent.canonicalPrice(new BigDecimal("1.50")));
        assertEquals(0, DatasetContent.canonicalPrice(new BigDecimal("100")).scale());
    }

    @Test
    void verifySucceedsForMatchingStoredMetadata() {
        DatasetContent content = DatasetContent.of(twoBarSeries());
        DatasetContent.verify(twoBarSeries(), content.barCount(), content.firstDate(), content.lastDate(),
                content.contentHash());
    }

    @Test
    void verifyFailsOnBarCountMismatch() {
        DatasetContent content = DatasetContent.of(twoBarSeries());
        assertThrows(DatasetIntegrityException.class, () -> DatasetContent.verify(
                twoBarSeries(), content.barCount() + 1, content.firstDate(), content.lastDate(),
                content.contentHash()));
    }

    @Test
    void verifyFailsOnFirstDateMismatch() {
        DatasetContent content = DatasetContent.of(twoBarSeries());
        assertThrows(DatasetIntegrityException.class, () -> DatasetContent.verify(
                twoBarSeries(), content.barCount(), content.firstDate().minusDays(1), content.lastDate(),
                content.contentHash()));
    }

    @Test
    void verifyFailsOnLastDateMismatch() {
        DatasetContent content = DatasetContent.of(twoBarSeries());
        assertThrows(DatasetIntegrityException.class, () -> DatasetContent.verify(
                twoBarSeries(), content.barCount(), content.firstDate(), content.lastDate().plusDays(1),
                content.contentHash()));
    }

    @Test
    void verifyFailsOnContentHashMismatch() {
        DatasetContent content = DatasetContent.of(twoBarSeries());
        assertThrows(DatasetIntegrityException.class, () -> DatasetContent.verify(
                twoBarSeries(), content.barCount(), content.firstDate(), content.lastDate(), "0".repeat(64)));
    }

    @Test
    void verifyFailsOnNonCanonicalStoredPrice() {
        BarSeries nonCanonical = new BarSeries("AAPL", List.of(
                new Bar(LocalDate.of(2024, 1, 2), new BigDecimal("100.00"), bd("105"), bd("99"), bd("104"), 1000L)));

        assertThrows(DatasetIntegrityException.class,
                () -> DatasetContent.verify(nonCanonical, 1, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2),
                        "0".repeat(64)));
    }
}
