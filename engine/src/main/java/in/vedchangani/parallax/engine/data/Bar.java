package in.vedchangani.parallax.engine.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A single validated daily OHLCV observation.
 *
 * <p>Invalid input fails fast at construction. A {@code Bar} is never repaired,
 * clamped, or reordered by the engine.
 */
public record Bar(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {

    public Bar {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(open, "open must not be null");
        Objects.requireNonNull(high, "high must not be null");
        Objects.requireNonNull(low, "low must not be null");
        Objects.requireNonNull(close, "close must not be null");

        requirePositive(open, "open");
        requirePositive(high, "high");
        requirePositive(low, "low");
        requirePositive(close, "close");

        BigDecimal minOpenClose = open.min(close);
        if (low.compareTo(minOpenClose) > 0) {
            throw new IllegalArgumentException(
                    "low (%s) must be <= min(open, close) (%s)".formatted(low, minOpenClose));
        }

        BigDecimal maxOpenClose = open.max(close);
        if (high.compareTo(maxOpenClose) < 0) {
            throw new IllegalArgumentException(
                    "high (%s) must be >= max(open, close) (%s)".formatted(high, maxOpenClose));
        }

        if (volume < 0) {
            throw new IllegalArgumentException("volume must be >= 0, was " + volume);
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(name + " must be > 0, was " + value);
        }
    }
}
