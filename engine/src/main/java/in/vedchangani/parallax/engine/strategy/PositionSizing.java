package in.vedchangani.parallax.engine.strategy;

import java.math.BigDecimal;
import java.util.Objects;

public sealed interface PositionSizing {

    record CashFraction(BigDecimal fraction) implements PositionSizing {

        public CashFraction {
            Objects.requireNonNull(fraction, "fraction must not be null");
            if (fraction.compareTo(BigDecimal.ZERO) <= 0 || fraction.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("fraction must be > 0 and <= 1, was " + fraction);
            }
            fraction = fraction.stripTrailingZeros();
        }
    }
}
