package in.vedchangani.parallax.backend.marketdata;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

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
