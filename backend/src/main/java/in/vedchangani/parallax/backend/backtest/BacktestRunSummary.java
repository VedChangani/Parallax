package in.vedchangani.parallax.backend.backtest;

import java.time.Instant;
import java.time.LocalDate;

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
