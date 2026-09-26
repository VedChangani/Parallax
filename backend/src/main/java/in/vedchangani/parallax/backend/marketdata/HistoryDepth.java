package in.vedchangani.parallax.backend.marketdata;

/**
 * How much daily history a {@link MarketDataProvider} is asked to return.
 * Deliberately provider-agnostic: it says nothing about Alpha Vantage's own
 * {@code outputsize} parameter values, which a provider implementation maps
 * this onto internally.
 */
public enum HistoryDepth {
    COMPACT,
    FULL
}
