package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalType;

import java.util.Objects;

public enum OrderSide {
    BUY,
    SELL;

    public static OrderSide forSignal(SignalType type) {
        Objects.requireNonNull(type, "type must not be null");
        return switch (type) {
            case ENTER -> BUY;
            case EXIT -> SELL;
        };
    }
}
