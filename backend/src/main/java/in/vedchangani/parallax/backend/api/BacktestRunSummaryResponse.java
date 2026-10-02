package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestRunSummary;

import java.time.Instant;
import java.time.LocalDate;

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
