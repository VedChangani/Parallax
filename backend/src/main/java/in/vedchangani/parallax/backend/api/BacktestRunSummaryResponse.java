package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestRunSummary;

import java.time.Instant;

/**
 * The cheap, owner-scoped response shape for {@code GET /api/backtest-runs}
 * (D-34 Batch 3): run identity/metadata only, without equity/fill/rejection
 * children or reconstructed engine values. Never the JPA entity itself.
 * Field names match {@link BacktestRunResponse}'s own (e.g. {@code
 * strategyVersion}, not {@code strategyVersionNumber}) so a client sees one
 * consistent naming scheme across the list and detail views.
 */
public record BacktestRunSummaryResponse(long id, long strategyId, int strategyVersion, String definitionHash,
                                          long datasetId, int datasetVersion, String contentHash,
                                          int engineSemanticsVersion, Instant createdAt) {

    static BacktestRunSummaryResponse of(BacktestRunSummary summary) {
        return new BacktestRunSummaryResponse(summary.id(), summary.strategyId(), summary.strategyVersionNumber(),
                summary.strategyDefinitionHash(), summary.datasetId(), summary.datasetVersionNumber(),
                summary.datasetContentHash(), summary.engineSemanticsVersion(), summary.createdAt());
    }
}
