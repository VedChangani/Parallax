package in.vedchangani.parallax.engine.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

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

    public BigDecimal marketValue() {
        return close.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal equity() {
        return cash.add(marketValue());
    }

    public BigDecimal unrealizedPnl() {
        return marketValue().subtract(costBasis);
    }
}
