package in.vedchangani.parallax.backend.dataset;

public final class DatasetIntegrityException extends RuntimeException {

    public DatasetIntegrityException(String message) {
        super(message);
    }

    public DatasetIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
