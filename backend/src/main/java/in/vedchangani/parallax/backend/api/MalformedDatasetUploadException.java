package in.vedchangani.parallax.backend.api;

/**
 * The {@code POST .../versions} multipart request itself does not conform
 * to the D-32 contract (missing/duplicate {@code file} part, missing or
 * non-exact {@code adjustmentBasis} value, an unexpected extra part, or an
 * invalid uploaded filename) — distinct from a malformed CSV body, which
 * is reported as {@link in.vedchangani.parallax.backend.dataset.csv.MalformedCsvException}
 * instead. Maps to HTTP 400.
 */
public final class MalformedDatasetUploadException extends RuntimeException {

    private final String field;

    public MalformedDatasetUploadException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
