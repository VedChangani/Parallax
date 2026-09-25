package in.vedchangani.parallax.backend.dataset;

/**
 * The uploader's declared price-adjustment basis for a {@link DatasetVersion}
 * (D-32). This is provenance metadata only — an unverified claim the backend
 * cannot check. D-32 performs no adjustment calculation of any kind; the
 * engine ignores this value entirely, and it is excluded from the content
 * hash (it describes how the data was produced, not what the data is).
 *
 * <p>Required on every version creation request, with no default: silently
 * defaulting to {@link #RAW} would risk mislabeling already-adjusted data.
 */
public enum AdjustmentBasis {
    RAW,
    SPLIT_ADJUSTED,
    SPLIT_AND_DIVIDEND_ADJUSTED
}
