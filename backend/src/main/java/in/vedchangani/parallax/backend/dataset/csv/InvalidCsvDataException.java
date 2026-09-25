package in.vedchangani.parallax.backend.dataset.csv;

/**
 * A CSV row was syntactically well-formed but violates market-data
 * semantics — a client-facing, 422-mapped failure. {@code line} is the
 * 1-based physical line number of the offending row.
 *
 * <p>Every case is either {@code Bar}'s own {@code
 * IllegalArgumentException} message (non-positive price, inconsistent
 * high/low, negative volume — never duplicated here, {@code Bar} remains
 * the sole semantic authority per CLAUDE.md), an empty dataset ("no data
 * rows"), or the parser's own duplicate/out-of-order date check (the one
 * deliberate exception to "don't duplicate {@code Bar}/{@code BarSeries}
 * rules" — needed only so the offending line number can be reported;
 * {@code BarSeries} still re-validates the complete, already-checked list
 * as the final authority).
 */
public final class InvalidCsvDataException extends RuntimeException {

    private final int line;

    public InvalidCsvDataException(int line, String reason) {
        super("line " + line + ": " + reason);
        this.line = line;
    }

    public int line() {
        return line;
    }
}
