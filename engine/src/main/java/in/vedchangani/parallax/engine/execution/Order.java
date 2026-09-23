package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalEvent;

import java.util.Objects;

/**
 * A queued command to trade {@code quantity} whole shares, created from a
 * {@link SignalEvent} at that signal's bar close. It executes no earlier
 * than the next available in-range bar's open (a later Backtester
 * concern); this type carries no execution date, execution price, fill
 * price, commission, slippage, or status. It is not itself part of the
 * backtest result — the eventual {@code Fill} or {@code OrderRejection}
 * is.
 *
 * <p>{@code id} is a run-local, sequential, per-backtest value assigned
 * by the Backtester only when an {@code Order} is actually created (a
 * {@code ZERO_QUANTITY} rejection consumes no id):
 *
 * <pre>
 * int id = nextOrderId;
 * Order order = new Order(id, quantity, signal);
 * nextOrderId++;
 * </pre>
 *
 * <p>{@code Order} itself only validates {@code id >= 1}; sequentiality
 * is a Backtester property, not something this type can enforce.
 *
 * <p>{@code quantity} is not computed here — the Backtester sizes ENTER
 * orders at the signal bar's close (D-7) and EXIT orders as the full held
 * position. {@code Order} only validates that the quantity is positive.
 *
 * <p>{@link #side()} is derived from {@code signal.type()} rather than
 * stored, so an order cannot disagree with the signal that produced it:
 * {@code ENTER} always means {@code BUY} and {@code EXIT} always means
 * {@code SELL} in V1's long-only model.
 */
public record Order(int id, long quantity, SignalEvent signal) {

    public Order {
        if (id < 1) {
            throw new IllegalArgumentException("id must be >= 1, was " + id);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0, was " + quantity);
        }
        Objects.requireNonNull(signal, "signal must not be null");
    }

    /**
     * The trade direction, derived from {@link SignalEvent#type()}:
     * {@code ENTER} maps to {@code BUY}, {@code EXIT} to {@code SELL}.
     */
    public OrderSide side() {
        return OrderSide.forSignal(signal.type());
    }
}
