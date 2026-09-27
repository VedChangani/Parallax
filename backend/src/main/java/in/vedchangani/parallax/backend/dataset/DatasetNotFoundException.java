package in.vedchangani.parallax.backend.dataset;

/**
 * No {@link Dataset} with the requested id is visible to the requesting
 * owner (D-32, mirroring D-31 §10) — either it does not exist at all, or it
 * belongs to a different owner. Both cases are represented identically and
 * map to HTTP 404, so a client can never distinguish "does not exist" from
 * "isn't yours."
 */
public final class DatasetNotFoundException extends RuntimeException {

    public DatasetNotFoundException(long datasetId) {
        super("dataset not found: " + datasetId);
    }
}
