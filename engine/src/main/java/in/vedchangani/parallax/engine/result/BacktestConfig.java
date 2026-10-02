package in.vedchangani.parallax.engine.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record BacktestConfig(BigDecimal initialCapital, BigDecimal commissionPerFill,
                              BigDecimal slippageRate, LocalDate startDate, LocalDate endDate) {

    public BacktestConfig {
        Objects.requireNonNull(initialCapital, "initialCapital must not be null");
        Objects.requireNonNull(commissionPerFill, "commissionPerFill must not be null");
        Objects.requireNonNull(slippageRate, "slippageRate must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");

        if (initialCapital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("initialCapital must be > 0, was " + initialCapital);
        }
        if (commissionPerFill.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("commissionPerFill must be >= 0, was " + commissionPerFill);
        }
        if (slippageRate.compareTo(BigDecimal.ZERO) < 0 || slippageRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("slippageRate must be >= 0 and < 1, was " + slippageRate);
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "startDate (%s) must not be after endDate (%s)".formatted(startDate, endDate));
        }

        initialCapital = canonicalize(initialCapital);
        commissionPerFill = canonicalize(commissionPerFill);
        slippageRate = canonicalize(slippageRate);
    }

    private static BigDecimal canonicalize(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
