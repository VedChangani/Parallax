package in.vedchangani.parallax.backend.strategy;

/**
 * The database's {@code uq_strategy_version_number} backstop rejected a
 * version insert (D-31 §4) — only reachable through an out-of-band write,
 * since the pessimistic lock in {@code StrategyService#createVersion}
 * otherwise serializes every allocation. Maps to HTTP 409. The failed
 * transaction is rolled back in full, so no version number is consumed.
 */
public final class StrategyVersionConflictException extends RuntimeException {

    public StrategyVersionConflictException(long strategyId) {
        super("version allocation conflict for strategy " + strategyId);
    }
}
