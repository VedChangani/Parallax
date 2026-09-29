package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

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

    public OrderSide side() {
        return OrderSide.forSignal(signal.type());
    }

    public BigDecimal slippageCost() {
        return fillPrice.subtract(referenceOpen).abs().multiply(BigDecimal.valueOf(quantity));
    }
}
