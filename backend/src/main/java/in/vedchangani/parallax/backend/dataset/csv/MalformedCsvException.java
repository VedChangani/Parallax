package in.vedchangani.parallax.backend.dataset.csv;

/**
 * The uploaded CSV does not conform to the D-32 syntax contract (encoding,
 * line structure, header shape, field count/grammar, or numeric overflow) —
 * a client-facing, 400-mapped failure. {@code line} is the 1-based physical
 * line number the problem was found on (header = line 1, first data row =
 * line 2); {@code 1} is used for whole-file structural problems that have
 * no more specific line (e.g. a missing header).
 *
 * <p>Distinct from {@link InvalidCsvDataException}: this reports a syntax
 * failure the parser itself detects, never a semantic (market-data)
 * failure — those are {@code Bar}'s responsibility (CLAUDE.md, D-32 §5).
 */
public final class MalformedCsvException extends RuntimeException {

    private final int line;

    public MalformedCsvException(int line, String reason) {
        super("line " + line + ": " + reason);
        this.line = line;
    }

    public int line() {
        return line;
    }
}
