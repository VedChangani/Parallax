package in.vedchangani.parallax.backend.dataset;

import java.time.Instant;
import java.time.LocalDate;

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
