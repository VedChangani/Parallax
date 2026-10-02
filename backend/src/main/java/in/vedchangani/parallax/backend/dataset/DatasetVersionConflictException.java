package in.vedchangani.parallax.backend.dataset;

public final class DatasetVersionConflictException extends RuntimeException {

    public DatasetVersionConflictException(long datasetId) {
        super("version allocation conflict for dataset " + datasetId);
    }
}
