package in.vedchangani.parallax.backend.marketdata;

import in.vedchangani.parallax.engine.data.Bar;

import java.util.List;
import java.util.Objects;

public record DailyBars(List<Bar> bars, String sourceDetail) {

    public DailyBars {
        Objects.requireNonNull(bars, "bars must not be null");
        Objects.requireNonNull(sourceDetail, "sourceDetail must not be null");
        if (bars.isEmpty()) {
            throw new IllegalArgumentException("bars must not be empty");
        }
        bars = List.copyOf(bars);
    }
}
