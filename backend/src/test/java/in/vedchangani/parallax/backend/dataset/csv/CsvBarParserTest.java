package in.vedchangani.parallax.backend.dataset.csv;

import in.vedchangani.parallax.engine.data.Bar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvBarParserTest {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String HEADER = "date,open,high,low,close,volume";

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] withBom(String text) {
        byte[] body = bytes(text);
        byte[] result = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, result, UTF8_BOM.length, body.length);
        return result;
    }

    @Test
    void parsesAMinimalOneRowFile() {
        List<Bar> bars = CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,100,105,99,104,1000\n"));

        assertEquals(1, bars.size());
        Bar bar = bars.get(0);
        assertEquals(LocalDate.of(2024, 1, 2), bar.date());
        assertEquals(new BigDecimal("100"), bar.open());
        assertEquals(new BigDecimal("105"), bar.high());
        assertEquals(new BigDecimal("99"), bar.low());
        assertEquals(new BigDecimal("104"), bar.close());
        assertEquals(1000L, bar.volume());
    }

    @Test
    void parsesMultipleRowsInOrder() {
        String csv = HEADER + "\n"
                + "2024-01-02,100,105,99,104,1000\n"
                + "2024-01-03,104,110,103,108,2000\n"
                + "2024-01-04,108,112,107,111,1500\n";

        List<Bar> bars = CsvBarParser.parse(bytes(csv));

        assertEquals(3, bars.size());
        assertEquals(LocalDate.of(2024, 1, 2), bars.get(0).date());
        assertEquals(LocalDate.of(2024, 1, 3), bars.get(1).date());
        assertEquals(LocalDate.of(2024, 1, 4), bars.get(2).date());
    }

    @Test
    void canonicalizesEquivalentDecimalSpellingsToTheSameScale() {
        String csv = HEADER + "\n2024-01-02,100.0,105.00,99.000,104.500,1000\n";

        Bar bar = CsvBarParser.parse(bytes(csv)).get(0);

        assertEquals(new BigDecimal("100"), bar.open());
        assertEquals(0, bar.open().scale());
        assertEquals(new BigDecimal("105"), bar.high());
        assertEquals(0, bar.high().scale());
        assertEquals(new BigDecimal("99"), bar.low());
        assertEquals(0, bar.low().scale());
        assertEquals(new BigDecimal("104.5"), bar.close());
        assertEquals(1, bar.close().scale());
    }

    @Test
    void acceptsLfLineEndings() {
        String csv = HEADER + "\n2024-01-02,100,105,99,104,1000\n2024-01-03,104,110,103,108,2000\n";
        assertEquals(2, CsvBarParser.parse(bytes(csv)).size());
    }

    @Test
    void acceptsCrlfLineEndings() {
        String csv = HEADER + "\r\n2024-01-02,100,105,99,104,1000\r\n2024-01-03,104,110,103,108,2000\r\n";
        assertEquals(2, CsvBarParser.parse(bytes(csv)).size());
    }

    @Test
    void acceptsMixedLfAndCrlfLineEndings() {
        String csv = HEADER + "\r\n2024-01-02,100,105,99,104,1000\n2024-01-03,104,110,103,108,2000\r\n";
        assertEquals(2, CsvBarParser.parse(bytes(csv)).size());
    }

    @Test
    void finalLineTerminatorIsOptional() {
        String csv = HEADER + "\n2024-01-02,100,105,99,104,1000";
        assertEquals(1, CsvBarParser.parse(bytes(csv)).size());
    }

    @Test
    void finalLineTerminatorMayBePresent() {
        String csv = HEADER + "\n2024-01-02,100,105,99,104,1000\n";
        assertEquals(1, CsvBarParser.parse(bytes(csv)).size());
    }

    @Test
    void validFinalUnterminatedLineWithNoCrIsAccepted() {
        String csv = HEADER + "\n2024-01-02,100,105,99,104,1000";
        assertEquals(1, CsvBarParser.parse(bytes(csv)).size());
    }

    @Test
    void aFinalLoneCrWithNoFollowingLfIsRejected() {
        String csv = HEADER + "\n2024-01-02,100,105,99,104,1000\r";
        assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(bytes(csv)));
    }

    @Test
    void crlfImmediatelyBeforeEofRemainsAccepted() {
        String csv = HEADER + "\n2024-01-02,100,105,99,104,1000\r\n";
        assertEquals(1, CsvBarParser.parse(bytes(csv)).size());
    }

    @Test
    void loneCrInAFieldStillFailsGrammarAfterTheEofFix() {
        String csv = HEADER + "\n2024-01-02,100\r,105,99,104,1000\n";
        assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(bytes(csv)));
    }

    @Test
    void acceptsALeadingUtf8Bom() {
        String csv = HEADER + "\n2024-01-02,100,105,99,104,1000\n";
        assertEquals(1, CsvBarParser.parse(withBom(csv)).size());
    }

    @Test
    void bomDoesNotAffectLineNumbering() {
        byte[] csv = withBom(HEADER + "\nnot-a-date,100,105,99,104,1000\n");
        MalformedCsvException e = assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(csv));
        assertEquals(2, e.line());
    }

    @Test
    void aBomNotAtTheStartIsRejectedAsNonAscii() {
        byte[] header = bytes(HEADER + "\n");
        byte[] withEmbeddedBom = new byte[header.length + UTF8_BOM.length];
        System.arraycopy(header, 0, withEmbeddedBom, 0, header.length);
        System.arraycopy(UTF8_BOM, 0, withEmbeddedBom, header.length, UTF8_BOM.length);

        assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(withEmbeddedBom));
    }

    @Test
    void nonAsciiByteIsRejectedWithItsLine() {
        byte[] csv = new byte[]{'d', 'a', 't', 'e', '\n', (byte) 0xC3, (byte) 0xA9};
        MalformedCsvException e = assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(csv));
        assertEquals(2, e.line());
    }

    @Test
    void zeroByteInputIsMalformedMissingHeader() {
        MalformedCsvException e = assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(new byte[0]));
        assertEquals(1, e.line());
    }

    @Test
    void bomOnlyInputIsMalformedMissingHeader() {
        assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(UTF8_BOM.clone()));
    }

    @Test
    void wrongCaseHeaderIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes("Date,Open,High,Low,Close,Volume\n2024-01-02,1,1,1,1,1\n")));
    }

    @Test
    void reorderedHeaderIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes("date,close,high,low,open,volume\n2024-01-02,1,1,1,1,1\n")));
    }

    @Test
    void missingColumnHeaderIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes("date,open,high,low,close\n2024-01-02,1,1,1,1\n")));
    }

    @Test
    void extraColumnHeaderIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes("date,open,high,low,close,volume,extra\n2024-01-02,1,1,1,1,1,x\n")));
    }

    @Test
    void headerOnlyIsInvalidNoDataRows() {
        InvalidCsvDataException e =
                assertThrows(InvalidCsvDataException.class, () -> CsvBarParser.parse(bytes(HEADER + "\n")));
        assertEquals(1, e.line());
    }

    @Test
    void tooFewFieldsIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,100,105,99,104\n")));
    }

    @Test
    void tooManyFieldsIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,100,105,99,104,1000,extra\n")));
    }

    @Test
    void blankLineInTheMiddleIsMalformed() {
        MalformedCsvException e = assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(bytes(
                HEADER + "\n2024-01-02,100,105,99,104,1000\n\n2024-01-03,104,110,103,108,2000\n")));
        assertEquals(3, e.line());
    }

    @Test
    void twoTrailingBlankLinesAreRejected() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,100,105,99,104,1000\n\n\n")));
    }

    @Test
    void whitespaceAroundAFieldIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02, 100,105,99,104,1000\n")));
    }

    @Test
    void quotedFieldIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,\"100\",105,99,104,1000\n")));
    }

    @Test
    void loneCrInAFieldFailsGrammar() {
        String csv = HEADER + "\n2024-01-02,100\r,105,99,104,1000\n";
        assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(bytes(csv)));
    }

    @Test
    void impossibleCalendarDateIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-02-30,100,105,99,104,1000\n")));
    }

    @Test
    void looselyFormattedDateIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-1-2,100,105,99,104,1000\n")));
    }

    @Test
    void exponentNotationIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,1e2,105,99,104,1000\n")));
    }

    @Test
    void leadingPlusIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,+100,105,99,104,1000\n")));
    }

    @Test
    void leadingZeroIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,0100,105,99,104,1000\n")));
    }

    @Test
    void missingIntegerPartIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,.5,105,99,104,1000\n")));
    }

    @Test
    void trailingDecimalPointWithNoDigitsIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,5.,105,99,104,1000\n")));
    }

    @Test
    void tooManyDigitsIsMalformed() {
        String bigInteger = "1".repeat(19);
        assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(
                bytes(HEADER + "\n2024-01-02," + bigInteger + ",105,99,104,1000\n")));
    }

    @Test
    void decimalVolumeIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,100,105,99,104,10.5\n")));
    }

    @Test
    void negativeZeroVolumeIsMalformed() {
        assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\n2024-01-02,100,105,99,104,-0\n")));
    }

    @Test
    void overflowingVolumeIsMalformed() {
        String hugeVolume = "9".repeat(19);
        assertThrows(MalformedCsvException.class, () -> CsvBarParser.parse(
                bytes(HEADER + "\n2024-01-02,100,105,99,104," + hugeVolume + "\n")));
    }

    @Test
    void nonPositivePriceIsInvalidWithLine() {
        InvalidCsvDataException e = assertThrows(InvalidCsvDataException.class, () -> CsvBarParser.parse(
                bytes(HEADER + "\n2024-01-02,0,105,99,104,1000\n")));
        assertEquals(2, e.line());
    }

    @Test
    void negativePriceIsInvalid() {
        assertThrows(InvalidCsvDataException.class, () -> CsvBarParser.parse(
                bytes(HEADER + "\n2024-01-02,-5,105,99,104,1000\n")));
    }

    @Test
    void highBelowMaxOpenCloseIsInvalid() {
        assertThrows(InvalidCsvDataException.class, () -> CsvBarParser.parse(
                bytes(HEADER + "\n2024-01-02,100,99,90,104,1000\n")));
    }

    @Test
    void lowAboveMinOpenCloseIsInvalid() {
        assertThrows(InvalidCsvDataException.class, () -> CsvBarParser.parse(
                bytes(HEADER + "\n2024-01-02,100,110,101,104,1000\n")));
    }

    @Test
    void negativeVolumeIsInvalid() {
        assertThrows(InvalidCsvDataException.class, () -> CsvBarParser.parse(
                bytes(HEADER + "\n2024-01-02,100,105,99,104,-5\n")));
    }

    @Test
    void duplicateDateIsInvalidAndNamesBothLines() {
        InvalidCsvDataException e = assertThrows(InvalidCsvDataException.class, () -> CsvBarParser.parse(bytes(
                HEADER + "\n2024-01-02,100,105,99,104,1000\n2024-01-02,104,110,103,108,2000\n")));
        assertEquals(3, e.line());
        assertTrue(e.getMessage().contains("2024-01-02"));
        assertTrue(e.getMessage().contains("line 2"));
    }

    @Test
    void outOfOrderDateIsInvalid() {
        InvalidCsvDataException e = assertThrows(InvalidCsvDataException.class, () -> CsvBarParser.parse(bytes(
                HEADER + "\n2024-01-03,100,105,99,104,1000\n2024-01-02,104,110,103,108,2000\n")));
        assertEquals(3, e.line());
    }

    @Test
    void headerIsLineOneAndFirstDataRowIsLineTwo() {
        MalformedCsvException e = assertThrows(MalformedCsvException.class,
                () -> CsvBarParser.parse(bytes(HEADER + "\nnot-a-date,100,105,99,104,1000\n")));
        assertEquals(2, e.line());
    }
}
