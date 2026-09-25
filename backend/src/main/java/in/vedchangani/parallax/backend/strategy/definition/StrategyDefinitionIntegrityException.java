package in.vedchangani.parallax.backend.strategy.definition;

/**
 * A <em>stored</em> strategy definition failed to decode: an unsupported
 * schema version, a malformed or semantically invalid stored document, or
 * a hash that does not match the recomputed canonical hash (D-30). This is
 * never a client input error — it means the persisted data was corrupted,
 * tampered with, or written by a codec that no longer agrees with this
 * one. Callers must treat it as an internal/integrity failure (500-class),
 * never report it as a request validation error, and never expose the
 * wrapped engine or Jackson exception type.
 */
public final class StrategyDefinitionIntegrityException extends RuntimeException {

    public StrategyDefinitionIntegrityException(String message) {
        super(message);
    }

    public StrategyDefinitionIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
