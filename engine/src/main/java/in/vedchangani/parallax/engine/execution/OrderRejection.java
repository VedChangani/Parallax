package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The immutable outcome for a {@link SignalEvent} whose intended order
 * did not result in a {@link Fill}. Every generated {@code SignalEvent}
 * has exactly one terminal outcome — a {@code Fill} or an
 * {@code OrderRejection} — but the two rejection cases happen at
 * genuinely different points in the lifecycle and carry genuinely
 * different information, so they are two separate records rather than
 * one record with fields that are meaningless in one case or the other:
 *
 * <ul>
 *   <li>{@link ZeroQuantity}: at signal time, sizing produced a quantity
 *   of zero or less. No {@code Order} was ever created, so no order id
 *   was consumed and there is no order-derived information to carry.</li>
 *   <li>{@link InsufficientCash}: an {@code Order} was created, but at
 *   execution the required cash exceeded what was available. The whole
 *   order is rejected — V1 has no partial fills.</li>
 * </ul>
 *
 * <p>Both cases require an {@code ENTER} signal: V1's only rejection
 * paths are zero-quantity sizing and insufficient cash on a BUY, neither
 * of which can happen for a SELL (a SELL sizes to the full held position
 * and needs no cash). There is deliberately no SELL rejection type.
 */
public sealed interface OrderRejection {

    SignalEvent signal();

    LocalDate date();

    RejectionReason reason();

    /**
     * No order was created because the strategy's sizing calculation
     * produced a non-positive quantity. There is no order id, quantity,
     * required cash, or available cash to record — none of that exists
     * for a signal that never became an order. The rejection date equals
     * the signal date, since nothing happens between the signal and this
     * rejection.
     */
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

    /**
     * An {@code Order} was created but, at the execution bar's open,
     * {@code requiredCash} exceeded {@code availableCash}, so the whole
     * order was rejected. {@code orderId} is the id the order had
     * already been assigned, which is why fill ids can show gaps: a
     * rejected order still consumed its id, and this rejection is the
     * explanation. {@code date} is the execution bar's date, stored
     * because it genuinely differs from the signal date — this is not
     * derivable from the signal.
     *
     * <p>{@code requiredCash} is the whole BUY at the execution open,
     * including commission; {@code availableCash} is the cash on hand
     * just before execution. This record does not compute either value —
     * they are supplied already computed by the execution step — but it
     * does check that {@code requiredCash > availableCash}, since an
     * instance that claimed insufficiency while showing enough cash would
     * misrepresent its own defining condition.
     */
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
