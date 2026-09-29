package in.vedchangani.parallax.backend.strategy;

public final class StrategyNotFoundException extends RuntimeException {

    public StrategyNotFoundException(long strategyId) {
        super("strategy not found: " + strategyId);
    }
}
