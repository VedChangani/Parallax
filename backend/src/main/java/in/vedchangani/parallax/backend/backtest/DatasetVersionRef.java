package in.vedchangani.parallax.backend.backtest;

/**
 * An owner-scoped reference to one immutable {@code DatasetVersion} (D-34
 * Batch 2): which dataset, and which of its version numbers. Resolution
 * (ownership, existence, and integrity verification) is entirely {@code
 * DatasetService}'s responsibility; this type carries no engine or
 * persistence state of its own.
 */
public record DatasetVersionRef(long datasetId, int versionNumber) {
}
