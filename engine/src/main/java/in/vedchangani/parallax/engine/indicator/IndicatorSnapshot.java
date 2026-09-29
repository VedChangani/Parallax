package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record IndicatorSnapshot(LocalDate date, BigDecimal close, Map<IndicatorSpec, Double> values) {

    private static final Comparator<IndicatorSpec> CANONICAL_ORDER =
            Comparator.comparing(IndicatorSpec::type).thenComparingInt(IndicatorSpec::period);

    public IndicatorSnapshot {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(close, "close must not be null");
        Objects.requireNonNull(values, "values must not be null");

        Map<IndicatorSpec, Double> ordered = new TreeMap<>(CANONICAL_ORDER);
        for (Map.Entry<IndicatorSpec, Double> entry : values.entrySet()) {
            IndicatorSpec spec = entry.getKey();
            Double value = entry.getValue();
            Objects.requireNonNull(spec, "indicator spec must not be null");
            Objects.requireNonNull(value, "value for " + spec + " must not be null");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "value for " + spec + " must be finite, was " + value);
            }
            ordered.put(spec, value);
        }
        values = Collections.unmodifiableMap(ordered);
    }

    public double value(IndicatorSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        Double value = values.get(spec);
        if (value == null) {
            throw new IllegalArgumentException(
                    "no value for " + spec + "; available: " + values.keySet());
        }
        return value;
    }
}
