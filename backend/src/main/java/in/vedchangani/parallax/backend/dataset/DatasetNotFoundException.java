package in.vedchangani.parallax.backend.dataset;

public final class DatasetNotFoundException extends RuntimeException {

    public DatasetNotFoundException(long datasetId) {
        super("dataset not found: " + datasetId);
    }
}
