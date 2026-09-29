package in.vedchangani.parallax.engine.indicator;

import java.util.Objects;

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
