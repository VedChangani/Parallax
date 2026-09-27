package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.dataset.VerifiedDatasetVersion;
import in.vedchangani.parallax.backend.strategy.StrategyService;
import in.vedchangani.parallax.backend.strategy.StrategyVersionDetail;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.metrics.BuyAndHoldBenchmark;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * The complete synchronous application-service path for a backtest run
 * (D-34 Batch 2, approved design): verify the owned, immutable strategy
 * version and dataset version; validate the requested range against the
 * dataset's actual bars; run the engine; compute performance metrics and
 * the buy-and-hold benchmark; map everything into persistence rows; and
 * commit them all in one atomic write transaction. Also exposes the
 * owner-scoped read path for a completed run.
 *
 * <p><strong>This class itself carries no {@code @Transactional}
 * annotation.</strong> Steps 1-2 each run inside {@code StrategyService}/
 * {@code DatasetService}'s own short read-only transactions; range
 * validation and engine execution (steps 3-4) run under no transaction at
 * all; only the final persistence step (step 5) opens a transaction, and it
 * does so on a separate bean ({@link BacktestRunWriter}) so the boundary is
 * real rather than relying on Spring's self-invocation proxying (D-34
 * Batch 2 §15). No pessimistic lock is acquired anywhere in this class — a
 * run has no version counter to allocate.
 */
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

    /**
     * Runs and persists one backtest, following the approved D-34 flow
     * exactly:
     *
     * <pre>
     * Step 1: StrategyService.getVersion (owner-scoped; 404/500 semantics unchanged)
     * Step 2: DatasetService.getVerifiedSeries (owner-scoped; 404/500 semantics unchanged)
     * Step 3: BacktestRangeValidator.validate (no transaction)
     * Step 4: Backtester.run + PerformanceMetrics.of + BuyAndHoldBenchmark.of (no transaction)
     * Step 5: BacktestRunWriter.persist (one short write transaction)
     * </pre>
     *
     * @throws in.vedchangani.parallax.backend.strategy.StrategyNotFoundException
     *         or {@code StrategyVersionNotFoundException} if {@code strategy}
     *         is missing or not owned by {@code owner}
     * @throws in.vedchangani.parallax.backend.dataset.DatasetNotFoundException
     *         or {@code DatasetVersionNotFoundException} if {@code dataset}
     *         is missing or not owned by {@code owner}
     * @throws in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionIntegrityException
     *         or {@code DatasetIntegrityException} if the referenced version's
     *         stored data fails its own integrity verification
     * @throws BacktestRangeException if the requested range fails strict raw
     *                                 dataset coverage containment
     */
    public BacktestRunSummary createRun(UserId owner, StrategyVersionRef strategy, DatasetVersionRef dataset,
                                         BacktestConfig config) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(dataset, "dataset must not be null");
        Objects.requireNonNull(config, "config must not be null");

        // Step 1: owner-scoped, integrity-verified strategy version. No write
        // transaction is opened by this call or by this method.
        StrategyVersionDetail strategyVersion =
                strategyService.getVersion(owner, strategy.strategyId(), strategy.versionNumber());

        // Step 2: owner-scoped, integrity-verified dataset version. The full
        // DatasetVersion's BarSeries is used as-is — this method never trims
        // its lookback portion.
        VerifiedDatasetVersion datasetVersion =
                datasetService.getVerifiedSeries(owner, dataset.datasetId(), dataset.versionNumber());

        // Step 3: strict raw dataset coverage containment. No transaction.
        BacktestRangeValidator.validate(datasetVersion.series(), config);

        // Step 4: engine execution and post-run analysis. No transaction, and
        // no database or network access of any kind happens in this step.
        BacktestResult result = backtester.run(datasetVersion.series(), strategyVersion.definition(), config);
        PerformanceMetrics metrics = PerformanceMetrics.of(result);
        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(datasetVersion.series(), result);

        BacktestRunWriteRequest writeRequest = new BacktestRunWriteRequest(owner.value(), strategy.strategyId(),
                strategyVersion.summary().versionNumber(), strategyVersion.summary().definitionHash(),
                dataset.datasetId(), datasetVersion.summary().versionNumber(), datasetVersion.summary().contentHash(),
                Backtester.SEMANTICS_VERSION, result, metrics, benchmark);

        // Step 5: the only write transaction in this entire flow.
        BacktestRun saved = writer.persist(writeRequest);

        return BacktestRunSummary.of(saved);
    }

    /**
     * Owner-scoped run identity/metadata only, read directly off the parent
     * {@code backtest_run} row — deliberately cheap. This never loads or
     * verifies any equity/fill/rejection child row, so listing an owner's
     * run history never touches the potentially many thousands of child
     * rows behind it. A returned {@link BacktestRunSummary} is therefore
     * <strong>not</strong> a guarantee that a run's children pass integrity
     * verification; only {@link #getRun} establishes that, for one run at a
     * time, on demand.
     */
    @Transactional(readOnly = true)
    public List<BacktestRunSummary> listRuns(UserId owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        return runRepository.findByOwnerIdOrderByIdAsc(owner.value()).stream()
                .map(BacktestRunSummary::of)
                .toList();
    }

    /**
     * Reconstructs one completed run as engine-backed immutable views and
     * verifies its structural and cross-field integrity (D-34 Batch 2 §7-9)
     * — never by reloading the original {@code DatasetVersion} bars or
     * decoding the original {@code StrategyVersion}; the persisted run is a
     * historical result snapshot. This is the only place that verification
     * happens; {@link #listRuns} deliberately does not perform it.
     *
     * @throws BacktestRunNotFoundException      if no such run is owned by {@code owner}
     * @throws BacktestResultIntegrityException if the stored run fails verification
     */
    @Transactional(readOnly = true)
    public BacktestRunDetail getRun(UserId owner, long runId) {
        Objects.requireNonNull(owner, "owner must not be null");

        BacktestRun run = runRepository.findByIdAndOwnerId(runId, owner.value())
                .orElseThrow(() -> new BacktestRunNotFoundException(runId));

        List<BacktestEquityPointRow> equityRows = equityPointRepository.findOwned(runId, owner.value());
        List<BacktestFillRow> fillRows = fillRepository.findOwned(runId, owner.value());
        List<BacktestRejectionRow> rejectionRows = rejectionRepository.findOwned(runId, owner.value());

        return BacktestResultReconstructor.reconstruct(run, equityRows, fillRows, rejectionRows);
    }
}
