package in.vedchangani.parallax.backend.marketdata;

/**
 * The provider's response could not be understood as a valid response at
 * all — malformed JSON, a shape that does not match the documented success
 * or control-response contract, or a provider control message this backend
 * does not recognize. This is the fallback classification: a genuine parse
 * or shape failure, not a market-data semantic problem (see {@link
 * InvalidMarketDataException}) and not a recognized rejection, capability
 * limit, or temporary-unavailability signal.
 *
 * <p>{@code message} is always one of a small, backend-owned set of stable
 * reason strings — never the provider's raw response text or a Jackson
 * internal diagnostic message.
 */
public final class MarketDataResponseException extends MarketDataException {

    public MarketDataResponseException(String message) {
        super(message);
    }
}
