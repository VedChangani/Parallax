package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.dataset.AdjustmentBasis;
import in.vedchangani.parallax.backend.dataset.DatasetSource;
import in.vedchangani.parallax.backend.dataset.DatasetVersionSummary;

import java.time.Instant;
import java.time.LocalDate;

public record DatasetVersionResponse(long datasetId, int versionNumber, String symbol, DatasetSource source,
                                      String sourceDetail, AdjustmentBasis adjustmentBasis, int barCount,
                                      LocalDate firstDate, LocalDate lastDate, String contentHash,
                                      Instant createdAt) {

    static DatasetVersionResponse of(DatasetVersionSummary summary) {
        return new DatasetVersionResponse(summary.datasetId(), summary.versionNumber(), summary.symbol(),
                summary.source(), summary.sourceDetail(), summary.adjustmentBasis(), summary.barCount(),
                summary.firstDate(), summary.lastDate(), summary.contentHash(), summary.createdAt());
    }
}
