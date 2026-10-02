package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.backend.dataset.DatasetNotFoundException;
import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.dataset.DatasetVersionNotFoundException;
import in.vedchangani.parallax.backend.dataset.VerifiedDatasetVersion;
import in.vedchangani.parallax.backend.strategy.StrategyNotFoundException;
import in.vedchangani.parallax.backend.strategy.StrategyService;
import in.vedchangani.parallax.backend.strategy.StrategyVersionDetail;
import in.vedchangani.parallax.backend.strategy.StrategyVersionNotFoundException;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionIntegrityException;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.metrics.BuyAndHoldBenchmark;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class BacktestRunService {

    private final StrategyService strategyService;
    private final DatasetService datasetService;
    private final Backtester backtester;
    private final BacktestRunWriter writer;
    private final BacktestRunRepository runRepository;
    private final BacktestEquityPointRepository equityPointRepository;
    private final BacktestFillRepository fillRepository;
    private final BacktestRejectionRepository rejectionRepository;

    public BacktestRunService(StrategyService strategyService, DatasetService datasetService, Backtester backtester,
                               BacktestRunWriter writer, BacktestRunRepository runRepository,
                               BacktestEquityPointRepository equityPointRepository,
                               BacktestFillRepository fillRepository,
                               BacktestRejectionRepository rejectionRepository) {
        this.strategyService = strategyService;
        this.datasetService = datasetService;
        this.backtester = backtester;
        this.writer = writer;
        this.runRepository = runRepository;
        this.equityPointRepository = equityPointRepository;
        this.fillRepository = fillRepository;
        this.rejectionRepository = rejectionRepository;
    }

    public BacktestRunSummary createRun(UserId owner, StrategyVersionRef strategy, DatasetVersionRef dataset,
                                         BacktestConfig config) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(dataset, "dataset must not be null");
        Objects.requireNonNull(config, "config must not be null");

        StrategyVersionDetail strategyVersion =
                strategyService.getVersion(owner, strategy.strategyId(), strategy.versionNumber());

        VerifiedDatasetVersion datasetVersion =
                datasetService.getVerifiedSeries(owner, dataset.datasetId(), dataset.versionNumber());

        BacktestRangeValidator.validate(datasetVersion.series(), config);

        BacktestResult result = backtester.run(datasetVersion.series(), strategyVersion.definition(), config);
        PerformanceMetrics metrics = PerformanceMetrics.of(result);
        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(datasetVersion.series(), result);

        BacktestRunWriteRequest writeRequest = new BacktestRunWriteRequest(owner.value(), strategy.strategyId(),
                strategyVersion.summary().versionNumber(), strategyVersion.summary().definitionHash(),
                dataset.datasetId(), datasetVersion.summary().versionNumber(), datasetVersion.summary().contentHash(),
                Backtester.SEMANTICS_VERSION, result, metrics, benchmark);

        BacktestRun saved = writer.persist(writeRequest);

        return BacktestRunSummary.of(saved);
    }

    @Transactional(readOnly = true)
    public List<BacktestRunSummary> listRuns(UserId owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        return runRepository.findByOwnerIdOrderByIdAsc(owner.value()).stream()
                .map(BacktestRunSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public BacktestRunDetail getRun(UserId owner, long runId) {
        Objects.requireNonNull(owner, "owner must not be null");

        BacktestRun run = runRepository.findByIdAndOwnerId(runId, owner.value())
                .orElseThrow(() -> new BacktestRunNotFoundException(runId));

        StrategyDefinition strategyDefinition = loadReferencedStrategyDefinition(owner, run);
        String datasetSymbol = loadReferencedDatasetSymbol(owner, run);

        List<BacktestEquityPointRow> equityRows = equityPointRepository.findOwned(runId, owner.value());
        List<BacktestFillRow> fillRows = fillRepository.findOwned(runId, owner.value());
        List<BacktestRejectionRow> rejectionRows = rejectionRepository.findOwned(runId, owner.value());

        return BacktestResultReconstructor.reconstruct(run, strategyDefinition, datasetSymbol, equityRows, fillRows,
                rejectionRows);
    }

    private StrategyDefinition loadReferencedStrategyDefinition(UserId owner, BacktestRun run) {
        try {
            return strategyService.getVersion(owner, run.strategyId(), run.strategyVersionNumber()).definition();
        } catch (StrategyNotFoundException | StrategyVersionNotFoundException | StrategyDefinitionIntegrityException e) {
            throw new BacktestResultIntegrityException(
                    "stored backtest run " + run.id() + " references a strategy version that failed its own "
                            + "integrity verification: " + e.getMessage(), e);
        }
    }

    private String loadReferencedDatasetSymbol(UserId owner, BacktestRun run) {
        try {
            return datasetService.getVersion(owner, run.datasetId(), run.datasetVersionNumber()).symbol();
        } catch (DatasetNotFoundException | DatasetVersionNotFoundException e) {
            throw new BacktestResultIntegrityException(
                    "stored backtest run " + run.id() + " references a dataset version that no longer exists: "
                            + e.getMessage(), e);
        }
    }
}
