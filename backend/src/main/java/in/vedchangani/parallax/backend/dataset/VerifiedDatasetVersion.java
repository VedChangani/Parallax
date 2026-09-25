package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.engine.data.BarSeries;

/**
 * A {@link DatasetVersionSummary} plus its verified {@link BarSeries}
 * (D-32), produced only via {@code DatasetService#getVerifiedSeries} —
 * which reconstructs the series from stored {@code dataset_bar} rows and
 * checks it against {@link DatasetContent#verify} before returning it.
 * This is the only way a {@link BarSeries} leaves the {@code dataset}
 * package; {@code DatasetBarRepository} itself is package-private.
 */
public record VerifiedDatasetVersion(DatasetVersionSummary summary, BarSeries series) {
}
