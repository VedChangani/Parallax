package in.vedchangani.parallax.backend.dataset;

public final class DuplicateDatasetNameException extends RuntimeException {

    public DuplicateDatasetNameException(String name) {
        super("a dataset named " + name + " already exists for this owner");
    }
}
