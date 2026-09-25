package in.vedchangani.parallax.backend.strategy;

/**
 * No {@link Strategy} with the requested id is visible to the requesting
 * owner (D-31 §10) — either it does not exist at all, or it belongs to a
 * different owner. Both cases are represented identically and map to HTTP
 * 404, so a client can never distinguish "does not exist" from "exists but
 * isn't yours."
 */
public final class StrategyNotFoundException extends RuntimeException {

    public StrategyNotFoundException(long strategyId) {
        super("strategy not found: " + strategyId);
    }
}
