package in.vedchangani.parallax.backend.marketdata;

/**
 * Base type for every failure a {@link MarketDataProvider} can raise.
 *
 * <p>Every subclass carries a stable, backend-owned message. A provider
 * response's raw body, API keys, request URIs, and Jackson's own internal
 * diagnostic messages must never reach a {@code MarketDataException}
 * message — see the concrete subclasses for the exact failure each one
 * represents.
 */
public abstract class MarketDataException extends RuntimeException {

    protected MarketDataException(String message) {
        super(message);
    }

    protected MarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
