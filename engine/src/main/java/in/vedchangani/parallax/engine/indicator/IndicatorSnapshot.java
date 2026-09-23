package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The immutable, read-only view of indicator state as of a bar's close.
 * This is the only market-derived input visible to strategy evaluation: it
 * exposes the bar's date and close plus the ready indicator values keyed by
 * {@link IndicatorSpec}, and nothing else — no {@code Bar}, no symbol, no
 * open/high/low/volume, no {@code Portfolio} state, no pending order, and
 * no runtime {@link Indicator} object.
 *
 * <p>A snapshot only ever holds ready values: the caller (the Backtester,
 * in a later batch) is responsible for building one only once every
 * referenced indicator is ready. There is no representation of "not
 * ready" inside this type.
 *
 * <p>The stored {@code values} map is defensively copied into canonical,
 * deterministic order — {@link IndicatorType} declaration order, then
 * period ascending — and exposed as unmodifiable. This makes two
 * snapshots built from equal content, in any insertion order, compare
 * equal, hash equal, and print identically, which matters for
 * reproducibility.
 */
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

    /**
     * Looks up the value for {@code spec}.
     *
     * @throws NullPointerException     if {@code spec} is null
     * @throws IllegalArgumentException if this snapshot has no value for
     *                                  {@code spec}
     */
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
