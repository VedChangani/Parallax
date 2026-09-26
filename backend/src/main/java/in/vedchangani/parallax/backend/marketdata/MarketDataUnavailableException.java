package in.vedchangani.parallax.backend.marketdata;

/**
 * The provider is temporarily unable to serve the request — Alpha Vantage's
 * rate-limit/temporary {@code "Note"}/{@code "Information"} control
 * responses. A retry (later, or with different credentials/quota) may
 * succeed; this is not a standing rejection or capability limit.
 */
public final class MarketDataUnavailableException extends MarketDataException {

    public MarketDataUnavailableException(String message) {
        super(message);
    }
}
