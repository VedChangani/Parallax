package in.vedchangani.parallax.backend.backtest;

/**
 * A client-supplied {@code BacktestConfig} request field is malformed — a
 * <em>shape</em> problem, not a semantic one (D-34 Batch 2, mirroring D-30's
 * {@code MalformedStrategyDefinitionException} split exactly): bad JSON, a
 * JSON number where a decimal string is required, a decimal literal that
 * does not match the D-30 grammar, or a date string that is not a valid
 * ISO-8601 date. {@code path} names the offending field (for example
 * {@code "config.initialCapital"}).
 */
public final class MalformedBacktestConfigException extends RuntimeException {

    private final String path;

    public MalformedBacktestConfigException(String path, String reason) {
        super(path.isEmpty() ? reason : path + ": " + reason);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
