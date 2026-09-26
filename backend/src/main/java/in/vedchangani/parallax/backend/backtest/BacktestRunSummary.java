package in.vedchangani.parallax.backend.backtest;

import java.time.Instant;

/**
 * An immutable, backend-internal identity view of a {@link BacktestRun}
 * (D-34 Batch 2, mirroring {@code StrategyVersionSummary}/{@code
 * DatasetVersionSummary}), without its equity/fill/rejection children or
 * reconstructed engine values. Never the entity itself.
 */
public record BacktestRunSummary(long id, long strategyId, int strategyVersionNumber, String strategyDefinitionHash,
                                  long datasetId, int datasetVersionNumber, String datasetContentHash,
                                  int engineSemanticsVersion, Instant createdAt) {

    static BacktestRunSummary of(BacktestRun run) {
        return new BacktestRunSummary(run.id(), run.strategyId(), run.strategyVersionNumber(),
                run.strategyDefinitionHash(), run.datasetId(), run.datasetVersionNumber(), run.datasetContentHash(),
                run.engineSemanticsVersion(), run.createdAt());
    }
}
