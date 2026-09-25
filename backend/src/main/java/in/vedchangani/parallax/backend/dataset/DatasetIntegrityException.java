package in.vedchangani.parallax.backend.dataset;

/**
 * A stored {@link DatasetVersion}'s bars failed reconstruction/verification
 * (D-32 §16, mirroring D-30's {@code StrategyDefinitionIntegrityException}):
 * corrupted or tampered data, or a bug that let non-canonical values into
 * storage. Never client-facing detail — the cause is logged, and the
 * response carries only a generic message. Nothing is ever repaired or
 * resaved when this is thrown.
 */
public final class DatasetIntegrityException extends RuntimeException {

    public DatasetIntegrityException(String message) {
        super(message);
    }

    public DatasetIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
