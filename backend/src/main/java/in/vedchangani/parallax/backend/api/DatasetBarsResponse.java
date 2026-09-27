package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.dataset.DatasetVersionSummary;
import in.vedchangani.parallax.backend.dataset.VerifiedDatasetVersion;

import java.time.LocalDate;
import java.util.List;

/**
 * The response shape for {@code GET .../versions/{version}/bars} (D-32):
 * the complete, integrity-verified bar list for one dataset version. No
 * pagination. Prices are JSON strings (the canonical {@code
 * BigDecimal.toPlainString()}, following the D-30 precedent of never
 * letting a monetary value pass through a JSON number); volume is a JSON
 * number.
 *
 * <p>Re-uploading this response's {@code bars} as a CSV file reproduces
 * exactly {@code contentHash} — the content hash is computed from the
 * normalized {@code BarSeries}, not from upload byte formatting.
 */
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
