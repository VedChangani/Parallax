package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;

import java.time.LocalDate;
import java.util.Objects;

/**
 * An actionable ENTER/EXIT intent: "at the close of this bar, the
 * strategy condition evaluated to true." It is not an order, a fill, a
 * trade, or a portfolio mutation; it carries no quantity, no price, and
 * no guarantee that anything executes.
 *
 * <p>The exact {@link IndicatorSnapshot} that caused the signal is
 * retained unchanged, so a later {@code Fill} or {@code OrderRejection}
 * can preserve the explanation for the trade. The signal's date is not
 * stored separately — it is {@link #date() derived} from the snapshot,
 * since a signal has no meaningful date other than the bar close that
 * produced it, and storing a second copy would allow it to disagree with
 * the snapshot's own date.
 *
 * <p>{@code SignalEvent} holds no {@code StrategyDefinition}, no
 * {@code PositionSizing}, no position or portfolio state, no {@code Order},
 * no runtime {@code Indicator}, and no information from any bar after the
 * one that produced it.
 */
public record SignalEvent(SignalType type, IndicatorSnapshot snapshot) {

    public SignalEvent {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    /**
     * The bar close at which this signal was produced, equal to
     * {@code snapshot.date()}.
     */
    public LocalDate date() {
        return snapshot.date();
    }
}
