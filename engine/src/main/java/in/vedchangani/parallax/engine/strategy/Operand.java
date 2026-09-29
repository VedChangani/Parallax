package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;

import java.util.Objects;

public sealed interface Operand {

    double resolve(IndicatorSnapshot snapshot);

    record IndicatorRef(IndicatorSpec spec) implements Operand {

        public IndicatorRef {
            Objects.requireNonNull(spec, "spec must not be null");
        }

        @Override
        public double resolve(IndicatorSnapshot snapshot) {
            return snapshot.value(spec);
        }
    }

    record Close() implements Operand {

        @Override
        public double resolve(IndicatorSnapshot snapshot) {
            return snapshot.close().doubleValue();
        }
    }

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
