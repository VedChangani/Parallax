package in.vedchangani.parallax.engine.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * An immutable observation of {@link Portfolio} state marked to a
 * specific bar's close. It carries the portfolio's four owned facts
 * (cash, quantity, cost basis, realized P&amp;L) plus the date and close
 * used to mark it; {@link #marketValue()}, {@link #equity()} and
 * {@link #unrealizedPnl()} are derived, not stored, so an EquityPoint can
 * never carry an equity figure inconsistent with its own cash and close.
 * Keeping cost basis and realized P&amp;L on every point makes the
 * accounting identity ({@code equity == initialCapital + realizedPnl +
 * unrealizedPnl}) checkable from the equity curve alone.
 *
 * <p>It holds no {@code Order}, {@code Fill}, runtime {@code Indicator},
 * {@code BarSeries}, or {@code StrategyDefinition} — it is a pure
 * snapshot of accounting state, not a record of how that state came to
 * be.
 */
public record EquityPoint(LocalDate date, BigDecimal cash, long quantity, BigDecimal costBasis,
                           BigDecimal realizedPnl, BigDecimal close) {

    public EquityPoint {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(cash, "cash must not be null");
        if (cash.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("cash must be >= 0, was " + cash);
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be >= 0, was " + quantity);
        }
        Objects.requireNonNull(costBasis, "costBasis must not be null");
        Objects.requireNonNull(realizedPnl, "realizedPnl must not be null");
        Objects.requireNonNull(close, "close must not be null");
        if (close.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("close must be > 0, was " + close);
        }
        if (quantity == 0) {
            if (costBasis.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException(
                        "costBasis must be 0 when quantity is 0, was " + costBasis);
            }
        } else if (costBasis.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "costBasis must be > 0 when quantity is > 0, was " + costBasis);
        }
    }

    /**
     * {@code close × quantity}: the current market value of the held
     * position, zero when flat.
     */
    public BigDecimal marketValue() {
        return close.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * {@code cash + marketValue()}: total portfolio value at this point.
     */
    public BigDecimal equity() {
        return cash.add(marketValue());
    }

    /**
     * {@code marketValue() − costBasis}: unrealized P&amp;L on the held
     * position, zero when flat.
     */
    public BigDecimal unrealizedPnl() {
        return marketValue().subtract(costBasis);
    }
}
