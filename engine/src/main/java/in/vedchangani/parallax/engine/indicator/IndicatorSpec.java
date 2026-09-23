package in.vedchangani.parallax.engine.indicator;

import java.util.Objects;

/**
 * The immutable definition of what an indicator calculates: its type and
 * period. A spec carries no runtime state and no calculated values; it is
 * an identity, not a calculation. Distinct backtest runs create separate
 * runtime {@link Indicator} instances from the same spec.
 */
public record IndicatorSpec(IndicatorType type, int period) {

    public IndicatorSpec {
        Objects.requireNonNull(type, "type must not be null");
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1, was " + period);
        }
        if (type == IndicatorType.RSI && period < 2) {
            throw new IllegalArgumentException("RSI period must be >= 2, was " + period);
        }
    }
}
