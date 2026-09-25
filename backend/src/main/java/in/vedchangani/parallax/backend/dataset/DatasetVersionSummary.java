package in.vedchangani.parallax.backend.dataset;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An immutable, backend-internal view of a {@link DatasetVersion} (D-32)
 * without its bars. Never the entity itself.
 */
public record DatasetVersionSummary(long datasetId, int versionNumber, String symbol, DatasetSource source,
                                     String sourceDetail, AdjustmentBasis adjustmentBasis, int barCount,
                                     LocalDate firstDate, LocalDate lastDate, String contentHash,
                                     Instant createdAt) {

    static DatasetVersionSummary of(DatasetVersion version) {
        return new DatasetVersionSummary(version.datasetId(), version.versionNumber(), version.symbol(),
                version.source(), version.sourceDetail(), version.adjustmentBasis(), version.barCount(),
                version.firstDate(), version.lastDate(), version.contentHash(), version.createdAt());
    }
}
