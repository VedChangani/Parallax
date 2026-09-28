package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestRunSummary;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The cheap, owner-scoped response shape for {@code GET /api/backtest-runs}
 * (D-34 Batch 3): run identity/metadata only, without equity/fill/rejection
 * children or reconstructed engine values. Never the JPA entity itself.
 * Field names match {@link BacktestRunResponse}'s own (e.g. {@code
 * strategyVersion}, not {@code strategyVersionNumber}) so a client sees one
 * consistent naming scheme across the list and detail views.
 *
 * <p>{@code startDate}/{@code endDate}/{@code totalReturn}/{@code
 * benchmarkTotalReturn} (Phase 9 Batch 1, I8) let the run history read as a
 * useful research log without a per-row detail fetch; they are read from
 * the same cheap {@link BacktestRunSummary} every other field here comes
 * from, so this endpoint still never loads or verifies a child row. {@code
 * totalReturn}/{@code benchmarkTotalReturn} are plain JSON numbers, matching
 * {@link BacktestRunResponse.PerformanceMetricsResponse#totalReturn()} and
 * {@link BacktestRunResponse.BenchmarkResponse#totalReturn()} exactly (D-26:
 * derived statistics are {@code double}, never a decimal string).
 */
public record BacktestRunSummaryResponse(long id, long strategyId, int strategyVersion, String definitionHash,
                                          long datasetId, int datasetVersion, String contentHash,
                                          int engineSemanticsVersion, LocalDate startDate, LocalDate endDate,
                                          double totalReturn, double benchmarkTotalReturn, Instant createdAt) {

    static BacktestRunSummaryResponse of(BacktestRunSummary summary) {
        return new BacktestRunSummaryResponse(summary.id(), summary.strategyId(), summary.strategyVersionNumber(),
                summary.strategyDefinitionHash(), summary.datasetId(), summary.datasetVersionNumber(),
                summary.datasetContentHash(), summary.engineSemanticsVersion(), summary.startDate(),
                summary.endDate(), summary.totalReturn(), summary.benchmarkTotalReturn(), summary.createdAt());
    }
}
