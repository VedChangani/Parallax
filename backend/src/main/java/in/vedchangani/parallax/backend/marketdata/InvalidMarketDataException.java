package in.vedchangani.parallax.backend.marketdata;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The provider's response was correctly shaped, but the market data itself
 * is semantically invalid — an empty daily series, or a single bar that
 * fails {@link in.vedchangani.parallax.engine.data.Bar}'s own validation
 * (non-positive price, inconsistent high/low, negative volume). {@code Bar}
 * remains the sole semantic authority (CLAUDE.md): its {@code
 * IllegalArgumentException} message is reused here, never duplicated.
 *
 * <p>{@code date} is present when the failure is tied to one specific bar,
 * and empty for a whole-series problem (e.g. an empty time series).
 */
public final class InvalidMarketDataException extends MarketDataException {

    private final LocalDate date;

    public InvalidMarketDataException(String message) {
        super(message);
        this.date = null;
    }

    public InvalidMarketDataException(LocalDate date, String message) {
        super(Objects.requireNonNull(date, "date must not be null") + ": " + message);
        this.date = date;
    }

    public Optional<LocalDate> date() {
        return Optional.ofNullable(date);
    }
}
