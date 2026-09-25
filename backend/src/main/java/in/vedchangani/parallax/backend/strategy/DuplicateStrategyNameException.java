package in.vedchangani.parallax.backend.strategy;

/**
 * The requesting owner already has a {@link Strategy} with this exact name
 * (D-31: {@code uq_strategy_owner_name}, exact and case-sensitive — no
 * trimming or normalization). Maps to HTTP 409.
 */
public final class DuplicateStrategyNameException extends RuntimeException {

    public DuplicateStrategyNameException(String name) {
        super("a strategy named " + name + " already exists for this owner");
    }
}
