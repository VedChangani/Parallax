package in.vedchangani.parallax.engine.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The immutable configuration for one backtest run: starting capital,
 * per-fill commission, adverse slippage, and the inclusive date range to
 * evaluate.
 *
 * <p>{@code initialCapital} must be strictly positive — a zero-capital
 * run could never trade, and it is the denominator for every future
 * return calculation. This is stricter than {@code Portfolio}, which
 * still allows zero initial cash as a runtime value; that is not
 * weakened, this configuration type is simply stricter about what a
 * meaningful backtest request looks like.
 *
 * <p>{@code commissionPerFill} is a fixed monetary amount charged once
 * per {@code Fill}, the same for BUY and SELL — never a percentage or a
 * per-share charge.
 *
 * <p>{@code slippageRate} is the adverse fraction applied to a bar's
 * open: {@code BUY -> open * (1 + slippageRate)},
 * {@code SELL -> open * (1 - slippageRate)}. It must be strictly less
 * than 1 so that a SELL fill price is always positive for any valid
 * (positive) open.
 *
 * <p>Every {@link BigDecimal} component is canonicalized at construction
 * ({@link BigDecimal#stripTrailingZeros()}, with scale clamped to a
 * minimum of 0) so that equal configurations — for example
 * {@code 10000} and {@code 10000.00} — are equal values. This is exact:
 * no rounding, no {@link java.math.MathContext}.
 */
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
