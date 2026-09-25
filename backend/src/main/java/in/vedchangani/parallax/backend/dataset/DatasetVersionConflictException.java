package in.vedchangani.parallax.backend.dataset;

/**
 * The database's {@code uq_dataset_version_number} backstop rejected a
 * version insert (mirroring D-31's {@code StrategyVersionConflictException})
 * — only reachable through an out-of-band write, since the pessimistic lock
 * in {@code DatasetService#createVersionFromCsv} otherwise serializes every
 * allocation. Maps to HTTP 409. The failed transaction is rolled back in
 * full, so no version number is consumed.
 */
public final class DatasetVersionConflictException extends RuntimeException {

    public DatasetVersionConflictException(long datasetId) {
        super("version allocation conflict for dataset " + datasetId);
    }
}
