package in.vedchangani.parallax.backend.strategy.definition;

/**
 * The root transport representation of an engine {@code
 * StrategyDefinition} (D-30). This is the request/response shape — it
 * intentionally carries no {@code schemaVersion}; that field belongs only
 * to the stored document (see {@code StrategyDefinitionCodec}), so a
 * client that mistakenly includes it is rejected by strict unknown-property
 * checking rather than silently ignored.
 */
public record StrategyDefinitionDto(ConditionDto entryCondition, ConditionDto exitCondition,
                                     PositionSizingDto positionSizing) {
}
