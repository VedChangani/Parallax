package in.vedchangani.parallax.backend.dataset.csv;

import in.vedchangani.parallax.backend.dataset.DatasetContent;
import in.vedchangani.parallax.engine.data.Bar;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The D-32 V1 CSV parser: plain Java, no CSV library, no Spring dependency,
 * independently unit-testable. Converts an uploaded file's raw bytes into
 * an ordered {@link List} of validated, canonicalized engine {@link Bar}
 * objects.
 *
 * <p>The parser owns syntax only — byte encoding, line structure, header
 * shape, field count, and per-field numeric grammar. {@link Bar} remains
 * the sole semantic authority (positive prices, consistent high/low,
 * non-negative volume); its {@link IllegalArgumentException} is caught and
 * rewrapped as {@link InvalidCsvDataException} carrying the offending
 * line, never duplicated here. The one deliberate exception is strict
 * date ordering: the parser tracks the previous row's date itself, purely
 * so a duplicate or out-of-order date can be reported with its exact line
 * — {@link in.vedchangani.parallax.engine.data.BarSeries} still
 * re-validates the complete sequence as the final authority once every
 * bar is built.
 *
 * <p>Encoding contract: a single leading UTF-8 BOM is accepted and
 * stripped; every remaining byte must be 7-bit ASCII. Lines may be
 * LF-terminated, CRLF-terminated, or a mix of both; the final line
 * terminator is optional; any other blank line is rejected. The header
 * must be exactly {@code date,open,high,low,close,volume} (case-sensitive,
 * fixed order); each data row must have exactly six unquoted,
 * unwhitespaced fields.
 */
public final class CsvBarParser {

    private static final String HEADER = "date,open,high,low,close,volume";

    private static final Pattern DATE_GRAMMAR = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern PRICE_GRAMMAR = Pattern.compile("-?(0|[1-9][0-9]{0,17})(\\.[0-9]{1,18})?");
    private static final Pattern VOLUME_GRAMMAR = Pattern.compile("0|-?[1-9][0-9]{0,18}");

    private static final int BOM_LENGTH = 3;
    private static final int BOM_BYTE_0 = 0xEF;
    private static final int BOM_BYTE_1 = 0xBB;
    private static final int BOM_BYTE_2 = 0xBF;

    private CsvBarParser() {
    }

    /**
     * Parses {@code csv} into an ordered list of validated bars. Never
     * sorts, deduplicates, or repairs; every row that does not conform is
     * reported with its physical line number.
     *
     * @throws MalformedCsvException  for any syntax failure (encoding,
     *                                 line structure, header, field count,
     *                                 numeric grammar/overflow, impossible
     *                                 date)
     * @throws InvalidCsvDataException for a syntactically valid row that
     *                                 fails {@code Bar}'s semantics, an
     *                                 out-of-order/duplicate date, or an
     *                                 empty dataset (header with no rows)
     */
    public static List<Bar> parse(byte[] csv) {
        Objects.requireNonNull(csv, "csv must not be null");
        byte[] content = stripBom(csv);
        String raw = new String(content, StandardCharsets.ISO_8859_1);
        List<String> lines = splitLinesAndCheckAscii(raw);

        if (lines.isEmpty()) {
            throw new MalformedCsvException(1, "missing header");
        }
        if (!HEADER.equals(lines.get(0))) {
            throw new MalformedCsvException(1, "header must be exactly \"" + HEADER + "\"");
        }
        if (lines.size() == 1) {
            throw new InvalidCsvDataException(1, "no data rows");
        }

        List<Bar> bars = new ArrayList<>(lines.size() - 1);
        LocalDate previousDate = null;
        int previousLine = -1;

        for (int i = 1; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String line = lines.get(i);

            if (line.isEmpty()) {
                throw new MalformedCsvException(lineNumber, "blank lines are not allowed");
            }
            if (line.indexOf('"') >= 0) {
                throw new MalformedCsvException(lineNumber, "quoted fields are not supported");
            }

            String[] fields = line.split(",", -1);
            if (fields.length != 6) {
                throw new MalformedCsvException(lineNumber, "expected 6 fields, found " + fields.length);
            }

            LocalDate date = parseDate(fields[0], lineNumber);
            BigDecimal open = parsePrice(fields[1], lineNumber, "open");
            BigDecimal high = parsePrice(fields[2], lineNumber, "high");
            BigDecimal low = parsePrice(fields[3], lineNumber, "low");
            BigDecimal close = parsePrice(fields[4], lineNumber, "close");
            long volume = parseVolume(fields[5], lineNumber);

            if (previousDate != null) {
                if (date.isEqual(previousDate)) {
                    throw new InvalidCsvDataException(lineNumber,
                            "duplicate date " + date + " (first seen on line " + previousLine + ")");
                }
                if (date.isBefore(previousDate)) {
                    throw new InvalidCsvDataException(lineNumber,
                            "date " + date + " is before " + previousDate + " on line " + previousLine);
                }
            }

            Bar bar;
            try {
                bar = new Bar(date, DatasetContent.canonicalPrice(open), DatasetContent.canonicalPrice(high),
                        DatasetContent.canonicalPrice(low), DatasetContent.canonicalPrice(close), volume);
            } catch (IllegalArgumentException e) {
                throw new InvalidCsvDataException(lineNumber, e.getMessage());
            }

            bars.add(bar);
            previousDate = date;
            previousLine = lineNumber;
        }

        return bars;
    }

    private static byte[] stripBom(byte[] csv) {
        if (csv.length >= BOM_LENGTH
                && (csv[0] & 0xFF) == BOM_BYTE_0
                && (csv[1] & 0xFF) == BOM_BYTE_1
                && (csv[2] & 0xFF) == BOM_BYTE_2) {
            byte[] stripped = new byte[csv.length - BOM_LENGTH];
            System.arraycopy(csv, BOM_LENGTH, stripped, 0, stripped.length);
            return stripped;
        }
        return csv;
    }

    /**
     * Splits {@code raw} (decoded 1:1 from bytes via ISO-8859-1, so every
     * char is one input byte) into physical lines on {@code '\n'},
     * stripping one trailing {@code '\r'} per line that is actually
     * terminated by that {@code '\n'} (i.e. a CRLF ending). The optional
     * final line terminator produces no extra trailing empty line; any
     * other empty line (including two consecutive terminators) is
     * preserved as a real, later-rejected blank line. A final,
     * unterminated line's own trailing {@code '\r'} (no following
     * {@code '\n'}) is never stripped — it is not a line terminator, so it
     * remains in the data and fails field grammar downstream. Any byte
     * outside the 7-bit ASCII range fails immediately with its line
     * number.
     */
    private static List<String> splitLinesAndCheckAscii(String raw) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int lineNumber = 1;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c > 0x7F) {
                throw new MalformedCsvException(lineNumber, "input contains a non-ASCII byte");
            }
            if (c == '\n') {
                lines.add(stripTrailingCr(current.toString()));
                current.setLength(0);
                lineNumber++;
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            // The final, unterminated line: a trailing '\r' here is NOT a CRLF line
            // terminator (there is no following '\n'), so it must remain in the data
            // and fail grammar downstream — never silently stripped as if it were one.
            lines.add(current.toString());
        }
        return lines;
    }

    private static String stripTrailingCr(String line) {
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }

    private static LocalDate parseDate(String text, int lineNumber) {
        if (!DATE_GRAMMAR.matcher(text).matches()) {
            throw new MalformedCsvException(lineNumber, "invalid date: " + text);
        }
        try {
            return LocalDate.parse(text, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new MalformedCsvException(lineNumber, "invalid date: " + text);
        }
    }

    private static BigDecimal parsePrice(String text, int lineNumber, String field) {
        if (!PRICE_GRAMMAR.matcher(text).matches()) {
            throw new MalformedCsvException(lineNumber, "invalid " + field + " value: " + text);
        }
        return new BigDecimal(text);
    }

    private static long parseVolume(String text, int lineNumber) {
        if (!VOLUME_GRAMMAR.matcher(text).matches()) {
            throw new MalformedCsvException(lineNumber, "invalid volume value: " + text);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new MalformedCsvException(lineNumber, "volume out of range: " + text);
        }
    }
}
