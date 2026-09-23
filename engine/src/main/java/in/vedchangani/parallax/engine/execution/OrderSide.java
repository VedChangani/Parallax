package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalType;

import java.util.Objects;

/**
 * Which direction an {@link Order} trades. There is no third side: V1 is
 * long-only, so an order either opens a long position ({@code BUY}) or
 * closes one ({@code SELL}). Short selling would need a genuinely
 * different side/signal relationship and is out of V1 scope.
 */
public enum OrderSide {
    BUY,
    SELL;

    /**
     * The side a {@link SignalType} implies: {@code ENTER} always means
     * {@code BUY}, {@code EXIT} always means {@code SELL}. This is the
     * single mapping every {@code Order} and {@code Fill} uses to derive
     * its side from its signal, rather than storing it separately.
     */
    public static OrderSide forSignal(SignalType type) {
        Objects.requireNonNull(type, "type must not be null");
        return switch (type) {
            case ENTER -> BUY;
            case EXIT -> SELL;
        };
    }
}
