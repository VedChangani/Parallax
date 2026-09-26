package in.vedchangani.parallax.backend.marketdata;

/**
 * The request exceeded what the provider account/plan is able to serve —
 * for Alpha Vantage, {@link HistoryDepth#FULL} requested against an account
 * limited to compact history. Distinct from {@link
 * MarketDataUnavailableException}: this is a standing capability limit, not
 * a temporary condition that a retry could resolve.
 */
public final class MarketDataCapabilityException extends MarketDataException {

    public MarketDataCapabilityException(String message) {
        super(message);
    }
}
