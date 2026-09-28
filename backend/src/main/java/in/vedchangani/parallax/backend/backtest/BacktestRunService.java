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
     * verifies it end to end (Phase 9 Batch 2c, revising D-34 Batch 2's
     * never-recompute policy): the referenced immutable {@code
     * StrategyVersion} <strong>is now decoded</strong> (via {@code
     * StrategyService#getVersion}, the existing owner-scoped, D-30
     * hash-verified read path — never trusting the stored {@code jsonb}
     * directly) and the referenced {@code DatasetVersion}'s <strong>
     * metadata only</strong> is loaded (via {@code DatasetService#getVersion}
     * — never its bars, never {@link #datasetService}{@code
     * .getVerifiedSeries}); both are used only to reconstruct and verify the
     * stored result, never re-persisted, never exposed in the response
     * beyond what {@link BacktestRunDetail} already carries. A missing,
     * corrupted, unsupported-schema, or hash-mismatched referenced strategy
     * version — or a missing referenced dataset version — is a stored-run
     * integrity failure here (500), <strong>never</strong> the 404/500 it
     * would be during {@code createRun}: the run itself was found; its
     * <em>reference</em> is what failed. This is the only place any of this
     * verification happens; {@link #listRuns} deliberately does not perform
     * it, and {@link #createRun} never reruns because of it.
     *
     * @throws BacktestRunNotFoundException      if no such run is owned by {@code owner}
     * @throws BacktestResultIntegrityException if the stored run, or either of its
     *                                            referenced immutable versions, fails verification
     */
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

    /**
     * Owner-scoped (the run's own owner, already established by {@link
     * #getRun}), D-30-integrity-verified decode of the run's referenced
     * {@code StrategyVersion} — never its mutable parent {@code Strategy}'s
     * metadata, and never the run's own copied {@code strategyDefinitionHash}
     * column, which the database's own composite foreign key already
     * guarantees agrees with what {@code strategy_version} stores.
     */
    private StrategyDefinition loadReferencedStrategyDefinition(UserId owner, BacktestRun run) {
        try {
            return strategyService.getVersion(owner, run.strategyId(), run.strategyVersionNumber()).definition();
        } catch (StrategyNotFoundException | StrategyVersionNotFoundException | StrategyDefinitionIntegrityException e) {
            throw new BacktestResultIntegrityException(
                    "stored backtest run " + run.id() + " references a strategy version that failed its own "
                            + "integrity verification: " + e.getMessage(), e);
        }
    }

    /**
     * Owner-scoped metadata-only read of the run's referenced {@code
     * DatasetVersion} — its bars are never loaded here (D-32's {@code
     * getVerifiedSeries} remains {@link #createRun}'s own concern only).
     */
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
