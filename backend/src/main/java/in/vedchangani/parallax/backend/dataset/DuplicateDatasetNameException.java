package in.vedchangani.parallax.backend.dataset;

/**
 * The requesting owner already has a {@link Dataset} with this exact name
 * (D-32: {@code uq_dataset_owner_name}, exact and case-sensitive — no
 * trimming or normalization, mirroring D-31's {@code
 * DuplicateStrategyNameException}). Maps to HTTP 409.
 */
public final class DuplicateDatasetNameException extends RuntimeException {

    public DuplicateDatasetNameException(String name) {
        super("a dataset named " + name + " already exists for this owner");
    }
}
