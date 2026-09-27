package in.vedchangani.parallax.backend.marketdata;

/**
 * The provider explicitly rejected the request itself (Alpha Vantage's
 * {@code "Error Message"} response) — an invalid symbol, an invalid
 * function/parameter combination, or a similar request-level rejection.
 * The provider's own message text is never exposed here.
 */
public final class MarketDataRequestRejectedException extends MarketDataException {

    public MarketDataRequestRejectedException(String message) {
        super(message);
    }
}
