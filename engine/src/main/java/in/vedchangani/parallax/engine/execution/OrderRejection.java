package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public sealed interface OrderRejection {

    SignalEvent signal();

    LocalDate date();

    RejectionReason reason();

    record ZeroQuantity(SignalEvent signal) implements OrderRejection {

        public ZeroQuantity {
            Objects.requireNonNull(signal, "signal must not be null");
            if (signal.type() != SignalType.ENTER) {
                throw new IllegalArgumentException(
                        "ZeroQuantity requires an ENTER signal, was " + signal.type());
            }
        }

        @Override
        public LocalDate date() {
            return signal.date();
        }

        @Override
        public RejectionReason reason() {
            return RejectionReason.ZERO_QUANTITY;
        }
    }

    record InsufficientCash(int orderId, LocalDate date, long quantity, BigDecimal requiredCash,
                             BigDecimal availableCash, SignalEvent signal) implements OrderRejection {

        public InsufficientCash {
            if (orderId < 1) {
                throw new IllegalArgumentException("orderId must be >= 1, was " + orderId);
            }
            Objects.requireNonNull(date, "date must not be null");
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be > 0, was " + quantity);
            }
            Objects.requireNonNull(requiredCash, "requiredCash must not be null");
            if (requiredCash.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("requiredCash must be > 0, was " + requiredCash);
            }
            Objects.requireNonNull(availableCash, "availableCash must not be null");
            if (requiredCash.compareTo(availableCash) <= 0) {
                throw new IllegalArgumentException(
                        "requiredCash (%s) must be > availableCash (%s)".formatted(requiredCash, availableCash));
            }
            Objects.requireNonNull(signal, "signal must not be null");
            if (signal.type() != SignalType.ENTER) {
                throw new IllegalArgumentException(
                        "InsufficientCash requires an ENTER signal, was " + signal.type());
            }
        }

        @Override
        public RejectionReason reason() {
            return RejectionReason.INSUFFICIENT_CASH;
        }
    }
}
