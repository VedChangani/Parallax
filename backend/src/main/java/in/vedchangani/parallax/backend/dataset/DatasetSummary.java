package in.vedchangani.parallax.backend.dataset;

import java.time.Instant;

/**
 * An immutable, backend-internal view of a {@link Dataset} (D-32). Never
 * the entity itself — entities do not leave the {@code dataset} package.
 */
public record DatasetSummary(long id, String name, String symbol, int latestVersionNumber, Instant createdAt) {

    static DatasetSummary of(Dataset dataset) {
        return new DatasetSummary(dataset.id(), dataset.name(), dataset.symbol(), dataset.latestVersionNumber(),
                dataset.createdAt());
    }
}
