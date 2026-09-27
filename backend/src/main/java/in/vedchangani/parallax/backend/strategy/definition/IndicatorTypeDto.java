package in.vedchangani.parallax.backend.strategy.definition;

/**
 * The backend transport representation of the engine's {@code
 * IndicatorType} enum (D-30). Kept separate from the engine enum so the
 * stored/transport format never depends on an engine enum's identifiers.
 */
public enum IndicatorTypeDto {
    SMA,
    EMA,
    RSI
}
