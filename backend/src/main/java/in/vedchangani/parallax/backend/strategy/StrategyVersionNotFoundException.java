package in.vedchangani.parallax.backend.strategy;

/**
 * No {@link StrategyVersion} with the requested version number is visible
 * to the requesting owner (D-31 §10) — either the parent strategy doesn't
 * exist/isn't owned by the caller, or that version number was never
 * allocated. Both map to HTTP 404, identically.
 */
public final class StrategyVersionNotFoundException extends RuntimeException {

    public StrategyVersionNotFoundException(long strategyId, int versionNumber) {
        super("strategy version not found: strategy " + strategyId + ", version " + versionNumber);
    }
}
