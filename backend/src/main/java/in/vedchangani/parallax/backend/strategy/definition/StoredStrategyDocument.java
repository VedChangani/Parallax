package in.vedchangani.parallax.backend.strategy.definition;

/**
 * The root shape of a <em>stored</em> canonical strategy document (D-30):
 * identical to {@link StrategyDefinitionDto} plus the leading {@code
 * schemaVersion} property that only ever appears in persisted documents,
 * never in a transport request/response. Used only internally by {@code
 * StrategyDefinitionCodec#decode}.
 */
record StoredStrategyDocument(int schemaVersion, ConditionDto entryCondition, ConditionDto exitCondition,
                               PositionSizingDto positionSizing) {
}
