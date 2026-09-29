package in.vedchangani.parallax.backend.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BacktestCsvTest {

    @Test
    void plainFieldsAreNotQuoted() {
        assertEquals("2024-01-02", BacktestCsv.escape("2024-01-02"));
        assertEquals("-855.25", BacktestCsv.escape("-855.25"));
        assertEquals("CLOSED", BacktestCsv.escape("CLOSED"));
    }

    @Test
    void nullIsAnEmptyField() {
        assertEquals("", BacktestCsv.escape(null));
        assertEquals("", BacktestCsv.escape(""));
    }

    @Test
    void fieldsWithCommaQuoteOrLineBreakAreQuotedAndQuotesDoubled() {
        assertEquals("\"a,b\"", BacktestCsv.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", BacktestCsv.escape("say \"hi\""));
        assertEquals("\"line1\nline2\"", BacktestCsv.escape("line1\nline2"));
        assertEquals("\"line1\r\nline2\"", BacktestCsv.escape("line1\r\nline2"));
        assertEquals("\"a\rb\"", BacktestCsv.escape("a\rb"));
    }

    @Test
    void leadingAndTrailingSpacesAreKeptVerbatim() {
        assertEquals(" 1 ", BacktestCsv.escape(" 1 "));
    }

    @Test
    void drawdownIsAPlainDecimalNeverScientific() {
        assertEquals("0", BacktestCsv.plainDecimal(0.0));
        assertEquals("1", BacktestCsv.plainDecimal(1.0));
        assertEquals("0.25", BacktestCsv.plainDecimal(0.25));
        assertEquals("0.081", BacktestCsv.plainDecimal(0.081));
        // Double.toString gives "1.0E-4" and "1.234E-7"; the CSV must not.
        assertEquals("0.0001", BacktestCsv.plainDecimal(1.0E-4));
        assertEquals("0.0000001234", BacktestCsv.plainDecimal(1.234E-7));
    }

    @Test
    void plainDecimalRoundTripsTheExactDouble() {
        double value = 0.1 + 0.2; // 0.30000000000000004
        assertEquals(value, Double.parseDouble(BacktestCsv.plainDecimal(value)));
    }
}
