package in.vedchangani.parallax.backend.strategy.definition;

/**
 * A client-supplied strategy definition is shaped correctly but violates
 * an engine semantic rule (D-30) — finiteness, the {@code -0.0} fold,
 * non-empty groups, indicator period bounds, {@code CashFraction} bounds,
 * or a mapper-owned semantic rule such as {@code Constant} underflow. The
 * engine constructors remain the source of truth for these rules; this
 * exception only carries the engine's own {@code
 * IllegalArgumentException} message forward with a field path attached,
 * never the engine exception type itself. {@code path} names the
 * offending node (for example {@code "entryCondition.left"}).
 */
public final class InvalidStrategyDefinitionException extends RuntimeException {

    private final String path;

    public InvalidStrategyDefinitionException(String path, String engineMessage) {
        super(path.isEmpty() ? engineMessage : path + ": " + engineMessage);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
