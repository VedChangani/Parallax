package in.vedchangani.parallax.backend.dataset;

/**
 * No {@link DatasetVersion} with the requested version number is visible to
 * the requesting owner (D-32) — either the parent dataset doesn't
 * exist/isn't owned by the caller, or that version number was never
 * allocated. Both map to HTTP 404, identically.
 */
public final class DatasetVersionNotFoundException extends RuntimeException {

    public DatasetVersionNotFoundException(long datasetId, int versionNumber) {
        super("dataset version not found: dataset " + datasetId + ", version " + versionNumber);
    }
}
