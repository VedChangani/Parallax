package in.vedchangani.parallax.backend.backtest;

/**
 * An owner-scoped reference to one immutable {@code StrategyVersion} (D-34
 * Batch 2): which strategy, and which of its version numbers. Resolution
 * (ownership, existence, and decoding) is entirely {@code StrategyService}'s
 * responsibility; this type carries no engine or persistence state of its
 * own.
 */
public record StrategyVersionRef(long strategyId, int versionNumber) {
}
