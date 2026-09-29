package in.vedchangani.parallax.backend.dataset;

public final class DatasetVersionNotFoundException extends RuntimeException {

    public DatasetVersionNotFoundException(long datasetId, int versionNumber) {
        super("dataset version not found: dataset " + datasetId + ", version " + versionNumber);
    }
}
