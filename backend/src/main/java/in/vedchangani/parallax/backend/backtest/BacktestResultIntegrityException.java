package in.vedchangani.parallax.backend.backtest;

/**
 * A persisted {@link BacktestRun}'s children failed structural or
 * cross-field integrity verification on read (D-34 Batch 2, mirroring
 * D-30's {@code StrategyDefinitionIntegrityException}/D-32's {@code
 * DatasetIntegrityException}): a row that cannot reconstruct through its
 * engine constructor, equity/fill/rejection ordering that disagrees with
 * the engine's own invariants, a stored cost/metric total that disagrees
 * with the reconstructed fills, an inconsistent benchmark state, or an
 * unsupported {@code engine_semantics_version}. Never a client input
 * error — it means the persisted data was corrupted, tampered with, or
 * written by a version of this codebase that no longer agrees with the
 * current one. Callers must treat it as an internal/integrity failure
 * (500-class, in a later batch's REST mapping), never expose the wrapped
 * cause, and never attempt to repair or resave the stored row.
 */
public final class BacktestResultIntegrityException extends RuntimeException {

    public BacktestResultIntegrityException(String message) {
        super(message);
    }

    public BacktestResultIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
