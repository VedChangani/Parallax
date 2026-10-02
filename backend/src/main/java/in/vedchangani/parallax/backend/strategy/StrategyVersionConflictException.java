package in.vedchangani.parallax.backend.strategy;

public final class StrategyVersionConflictException extends RuntimeException {

    public StrategyVersionConflictException(long strategyId) {
        super("version allocation conflict for strategy " + strategyId);
    }
}
