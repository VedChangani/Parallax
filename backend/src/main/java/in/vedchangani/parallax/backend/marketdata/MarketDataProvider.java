package in.vedchangani.parallax.backend.marketdata;

/**
 * A source of historical daily market data. Deliberately minimal: a single
 * blocking fetch method, nothing about datasets, persistence, ownership, or
 * hashing. A future provider implementation (e.g. Alpha Vantage) adapts its
 * own transport/response shape into this contract; a normalization/dataset
 * layer built on top decides what to do with the result.
 */
public interface MarketDataProvider {

    /**
     * Fetches daily bars for {@code symbol} at the requested {@code depth}.
     *
     * @throws MarketDataException on any provider-side failure (rejection,
     *                              capability limitation, invalid data,
     *                              temporary unavailability, or an
     *                              unrecognized response)
     */
    DailyBars fetchDailyBars(String symbol, HistoryDepth depth);
}
