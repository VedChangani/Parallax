package in.vedchangani.parallax.engine.strategy;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * How much to request when an entry signal fires. This is a strategy
 * configuration value only — it says nothing about whole-share order
 * quantity, commission, slippage, or whether the resulting order is
 * actually affordable at the next bar's open. Translating a sizing
 * request into an executable order is a later Backtester/execution
 * concern (D-7).
 */
public sealed interface PositionSizing {

    /**
     * Requests using {@code fraction} of the cash available when the
     * entry signal is created, at the signal bar's close. V1 supports
     * only this one sizing mode: no fixed shares, fixed notional,
     * percent-of-equity, volatility sizing, leverage, or pyramiding.
     */
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
