package in.vedchangani.parallax.backend.strategy;

public final class StrategyVersionNotFoundException extends RuntimeException {

    public StrategyVersionNotFoundException(long strategyId, int versionNumber) {
        super("strategy version not found: strategy " + strategyId + ", version " + versionNumber);
    }
}
