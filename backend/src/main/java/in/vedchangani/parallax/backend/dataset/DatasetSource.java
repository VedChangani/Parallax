package in.vedchangani.parallax.backend.dataset;

/**
 * Where a {@link DatasetVersion}'s bars came from (D-32). {@link
 * #CSV_UPLOAD} is the only value D-32 implements; a provider-backed source
 * (e.g. Alpha Vantage) is a later, separate batch — no {@code
 * MarketDataProvider} abstraction is introduced until a second source
 * actually exists.
 */
public enum DatasetSource {
    CSV_UPLOAD
}
