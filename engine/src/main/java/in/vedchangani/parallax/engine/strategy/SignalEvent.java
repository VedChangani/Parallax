package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;

import java.time.LocalDate;
import java.util.Objects;

public record SignalEvent(SignalType type, IndicatorSnapshot snapshot) {

    public SignalEvent {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    public LocalDate date() {
        return snapshot.date();
    }
}
