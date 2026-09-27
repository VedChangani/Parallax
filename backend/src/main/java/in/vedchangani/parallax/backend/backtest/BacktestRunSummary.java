package in.vedchangani.parallax.backend.backtest;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An immutable, backend-internal identity view of a {@link BacktestRun}
 * (D-34 Batch 2, mirroring {@code StrategyVersionSummary}/{@code
 * DatasetVersionSummary}), without its equity/fill/rejection children or
 * reconstructed engine values. Never the entity itself.
 *
 * <p>{@code startDate}/{@code endDate}/{@code totalReturn}/{@code
 * benchmarkTotalReturn} (Phase 9 Batch 1, I8) are read directly off the
 * {@code backtest_run} parent row — the same row every other field here
 * already comes from — so {@link #of(BacktestRun)} stays a cheap,
 * child-row-free mapping and {@code BacktestRunService#listRuns} never
 * touches equity/fill/rejection rows merely to make the run history usable
 * as a research log.
 */
public record BacktestRunSummary(long id, long strategyId, int strategyVersionNumber, String strategyDefinitionHash,
                                  long datasetId, int datasetVersionNumber, String datasetContentHash,
                                  int engineSemanticsVersion, LocalDate startDate, LocalDate endDate,
                                  double totalReturn, double benchmarkTotalReturn, Instant createdAt) {

    static BacktestRunSummary of(BacktestRun run) {
        return new BacktestRunSummary(run.id(), run.strategyId(), run.strategyVersionNumber(),
                run.strategyDefinitionHash(), run.datasetId(), run.datasetVersionNumber(), run.datasetContentHash(),
                run.engineSemanticsVersion(), run.startDate(), run.endDate(), run.totalReturn(),
                run.benchmarkTotalReturn(), run.createdAt());
    }
}
