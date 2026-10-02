package in.vedchangani.parallax.backend.marketdata;

public abstract class MarketDataException extends RuntimeException {

    protected MarketDataException(String message) {
        super(message);
    }

    protected MarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
