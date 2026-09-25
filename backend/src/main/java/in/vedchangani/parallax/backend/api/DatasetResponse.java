package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.dataset.DatasetSummary;

import java.time.Instant;

/**
 * The response shape for a {@code Dataset} resource (D-32). Never the JPA
 * entity itself.
 */
public record DatasetResponse(long id, String name, String symbol, int latestVersionNumber, Instant createdAt) {

    static DatasetResponse of(DatasetSummary summary) {
        return new DatasetResponse(summary.id(), summary.name(), summary.symbol(), summary.latestVersionNumber(),
                summary.createdAt());
    }
}
