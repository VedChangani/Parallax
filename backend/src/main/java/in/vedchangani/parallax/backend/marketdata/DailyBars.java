package in.vedchangani.parallax.backend.marketdata;

import in.vedchangani.parallax.engine.data.Bar;

import java.util.List;
import java.util.Objects;

/**
 * A validated, provider-normalized batch of daily bars returned by a
 * {@link MarketDataProvider}.
 *
 * <p>{@code bars} are semantically validated (each is a constructed {@link
 * Bar}) and strictly ascending by date, but they are <strong>not</strong>
 * canonicalized — price scale/formatting is whatever the provider parser
 * produced. Canonicalization, deduplication against existing data, and
 * anything Dataset-shaped is a later, separate concern; this type knows
 * nothing about {@code Dataset}, persistence, or ownership.
 *
 * <p>{@code sourceDetail} is a short, deterministic description of exactly
 * what was fetched (e.g. {@code "TIME_SERIES_DAILY;outputsize=compact"}) —
 * never a fetch timestamp, an API key, or raw provider text.
 */
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
