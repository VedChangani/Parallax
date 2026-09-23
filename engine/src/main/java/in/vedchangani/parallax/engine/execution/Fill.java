package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The executed result of an {@link Order} at the next in-range bar's
 * open. {@code orderId} and {@code signal} are copied from the order that
 * produced this fill — the {@code Order} itself is never referenced, so a
 * {@code Fill} never keeps a transient runtime object alive and orders
 * stay out of the backtest result. Everything the order's explanation
 * needs is still recoverable without it: order id via {@link #orderId()},
 * signal type via {@code signal().type()}, the triggering snapshot via
 * {@code signal().snapshot()}, and the signal date via
 * {@code signal().date()}.
 *
 * <p>{@code date} is the execution bar's date, {@code referenceOpen} is
 * that bar's actual open, and {@code fillPrice} is the open already
 * adjusted for slippage by the execution step — {@code Fill} performs
 * none of that calculation itself; it represents an already-computed
 * execution result immutably. It validates only that its own fields are
 * internally sane (positive prices, non-negative commission, and so on),
 * not that the fill obeys the configured slippage rate, that cash was
 * sufficient, or that it occurred after its signal — those are
 * execution/Backtester concerns.
 *
 * <p>{@code Fill} contains no portfolio state, no P&amp;L, no equity, and
 * no trade information; those belong to later layers.
 */
public record Fill(int orderId, LocalDate date, long quantity, BigDecimal referenceOpen,
                    BigDecimal fillPrice, BigDecimal commission, SignalEvent signal) {

    public Fill {
        if (orderId < 1) {
            throw new IllegalArgumentException("orderId must be >= 1, was " + orderId);
        }
        Objects.requireNonNull(date, "date must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0, was " + quantity);
        }
        Objects.requireNonNull(referenceOpen, "referenceOpen must not be null");
        if (referenceOpen.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("referenceOpen must be > 0, was " + referenceOpen);
        }
        Objects.requireNonNull(fillPrice, "fillPrice must not be null");
        if (fillPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fillPrice must be > 0, was " + fillPrice);
        }
        Objects.requireNonNull(commission, "commission must not be null");
        if (commission.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("commission must be >= 0, was " + commission);
        }
        Objects.requireNonNull(signal, "signal must not be null");
    }

    /**
     * The trade direction, derived from {@code signal.type()} via
     * {@link OrderSide#forSignal(in.vedchangani.parallax.engine.strategy.SignalType)}
     * rather than stored, so a fill can never disagree with the signal
     * that produced it.
     */
    public OrderSide side() {
        return OrderSide.forSignal(signal.type());
    }

    /**
     * {@code |fillPrice - referenceOpen| * quantity}: the recorded cost
     * of slippage on this fill, computed exactly with no rounding. This
     * is bookkeeping over the already-recorded {@code fillPrice} and
     * {@code referenceOpen}, not the execution calculation that produced
     * {@code fillPrice} from the configured slippage rate.
     */
    public BigDecimal slippageCost() {
        return fillPrice.subtract(referenceOpen).abs().multiply(BigDecimal.valueOf(quantity));
    }
}
