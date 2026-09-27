package in.vedchangani.parallax.backend.strategy;

import in.vedchangani.parallax.engine.strategy.StrategyDefinition;

/**
 * A {@link StrategyVersionSummary} plus its verified engine {@link
 * StrategyDefinition} (D-31), produced only via D-30's {@code
 * StrategyDefinitionCodec#decode} — never by trusting the stored {@code
 * jsonb} text directly.
 */
public record StrategyVersionDetail(StrategyVersionSummary summary, StrategyDefinition definition) {
}
