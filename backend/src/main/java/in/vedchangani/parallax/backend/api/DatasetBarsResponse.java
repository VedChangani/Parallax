package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.dataset.DatasetVersionSummary;
import in.vedchangani.parallax.backend.dataset.VerifiedDatasetVersion;

import java.time.LocalDate;
import java.util.List;

public record DatasetBarsResponse(long datasetId, int versionNumber, String symbol, String contentHash,
                                   List<BarResponse> bars) {

    public record BarResponse(LocalDate date, String open, String high, String low, String close, long volume) {
    }

    static DatasetBarsResponse of(VerifiedDatasetVersion verified) {
        DatasetVersionSummary summary = verified.summary();
        List<BarResponse> bars = verified.series().bars().stream()
                .map(bar -> new BarResponse(bar.date(), bar.open().toPlainString(), bar.high().toPlainString(),
                        bar.low().toPlainString(), bar.close().toPlainString(), bar.volume()))
                .toList();
        return new DatasetBarsResponse(summary.datasetId(), summary.versionNumber(), summary.symbol(),
                summary.contentHash(), bars);
    }
}
