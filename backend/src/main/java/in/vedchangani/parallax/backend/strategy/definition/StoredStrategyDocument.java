package in.vedchangani.parallax.backend.strategy.definition;

record StoredStrategyDocument(int schemaVersion, ConditionDto entryCondition, ConditionDto exitCondition,
                               PositionSizingDto positionSizing) {
}
