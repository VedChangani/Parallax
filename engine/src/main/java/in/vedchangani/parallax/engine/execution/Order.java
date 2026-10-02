package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalEvent;

import java.util.Objects;

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

    public OrderSide side() {
        return OrderSide.forSignal(signal.type());
    }
}
