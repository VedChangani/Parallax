package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.dataset.DatasetSummary;

import java.time.Instant;

public record DatasetResponse(long id, String name, String symbol, int latestVersionNumber, Instant createdAt) {

    static DatasetResponse of(DatasetSummary summary) {
        return new DatasetResponse(summary.id(), summary.name(), summary.symbol(), summary.latestVersionNumber(),
                summary.createdAt());
    }
}
