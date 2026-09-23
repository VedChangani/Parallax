package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;

import java.util.Objects;

/**
 * A value that a {@link Condition} compares, resolved from an
 * {@link IndicatorSnapshot} alone. This is the entire V1 operand grammar:
 * a reference to an indicator value, the bar's close, or a literal
 * constant. There is no arithmetic and no function operand.
 *
 * <p>Resolution is a pure function of the snapshot. No implementation may
 * hold a runtime {@code Indicator}, a {@code BarSeries}, a {@code Bar}, or
 * any other mutable or time-varying state.
 */
public sealed interface Operand {

    /**
     * Resolves this operand's value from {@code snapshot}.
     */
    double resolve(IndicatorSnapshot snapshot);

    /**
     * A reference to an indicator value in the snapshot, resolved via
     * {@link IndicatorSnapshot#value(IndicatorSpec)}. A missing spec
     * propagates that method's {@link IllegalArgumentException} unchanged;
     * this type does not duplicate the snapshot's lookup validation.
     */
    record IndicatorRef(IndicatorSpec spec) implements Operand {

        public IndicatorRef {
            Objects.requireNonNull(spec, "spec must not be null");
        }

        @Override
        public double resolve(IndicatorSnapshot snapshot) {
            return snapshot.value(spec);
        }
    }

    /**
     * The bar's close, as known at the strategy-evaluation bar. Resolves to
     * {@code snapshot.close().doubleValue()}. Exposes no other {@code Bar}
     * field and no future close.
     */
    record Close() implements Operand {

        @Override
        public double resolve(IndicatorSnapshot snapshot) {
            return snapshot.close().doubleValue();
        }
    }

    /**
     * A literal numeric threshold, such as {@code 70}. Must be finite;
     * {@code -0.0} is normalized to {@code 0.0} so that two constants with
     * the same numeric meaning are also equal values.
     */
    record Constant(double value) implements Operand {

        public Constant {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("value must be finite, was " + value);
            }
            if (Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0)) {
                value = 0.0;
            }
        }

        @Override
        public double resolve(IndicatorSnapshot snapshot) {
            return value;
        }
    }
}
