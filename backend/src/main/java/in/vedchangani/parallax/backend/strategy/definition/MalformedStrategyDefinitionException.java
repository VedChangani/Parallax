package in.vedchangani.parallax.backend.strategy.definition;

/**
 * A client-supplied strategy definition (JSON request, or a mapper-owned
 * syntax rule such as the {@code Constant}/{@code CashFraction} decimal
 * grammar) is malformed — a <em>shape</em> problem, not a semantic one
 * (D-30). {@code path} names the offending node (for example {@code
 * "entryCondition.conditions[1].right"}, or {@code ""} for the document
 * root).
 */
public final class MalformedStrategyDefinitionException extends RuntimeException {

    private final String path;

    public MalformedStrategyDefinitionException(String path, String reason) {
        super(path.isEmpty() ? reason : path + ": " + reason);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
