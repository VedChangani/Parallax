package in.vedchangani.parallax.backend.dataset;

/**
 * Where a {@link DatasetVersion}'s bars came from. {@link #CSV_UPLOAD} is
 * D-32's original source; {@link #ALPHA_VANTAGE} is D-33 Batch 3's
 * provider-backed source, fetched through the {@code
 * in.vedchangani.parallax.backend.marketdata.MarketDataProvider}
 * abstraction and persisted through the same {@code DatasetService}
 * pipeline as a CSV upload.
 */
public enum DatasetSource {
    CSV_UPLOAD,
    ALPHA_VANTAGE
}
