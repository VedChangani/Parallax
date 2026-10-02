package in.vedchangani.parallax.backend.dataset;

import java.time.Instant;

public record DatasetSummary(long id, String name, String symbol, int latestVersionNumber, Instant createdAt) {

    static DatasetSummary of(Dataset dataset) {
        return new DatasetSummary(dataset.id(), dataset.name(), dataset.symbol(), dataset.latestVersionNumber(),
                dataset.createdAt());
    }
}
