package in.vedchangani.parallax.backend.strategy.definition;

public record StrategyDefinitionDto(ConditionDto entryCondition, ConditionDto exitCondition,
                                     PositionSizingDto positionSizing) {
}
