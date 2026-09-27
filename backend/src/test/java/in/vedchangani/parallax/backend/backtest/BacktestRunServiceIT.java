package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.AdjustmentBasis;
import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.dataset.DatasetSummary;
import in.vedchangani.parallax.backend.strategy.StrategyService;
import in.vedchangani.parallax.backend.strategy.StrategySummary;
import in.vedchangani.parallax.backend.strategy.StrategyVersionNotFoundException;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.dataset.DatasetVersionNotFoundException;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * D-34 Batch 2 {@link BacktestRunService} orchestration behavior against
 * real PostgreSQL (Testcontainers): the create-run flow end to end, the
 * transaction boundary around engine execution and around persistence,
 * range-validation enforcement, ownership isolation, atomic rollback on a
 * failure during either engine execution or child-row insertion, and a
 * numeric edge case the engine itself cannot represent. Mirrors {@code
 * StrategyServiceIT}/{@code DatasetServiceIT}'s own style.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BacktestRunServiceIT {

    @Autowired
    private BacktestRunService backtestRunService;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private Backtester backtester;

    @MockitoSpyBean
    private BacktestFillRepository fillRepository;

    // --- fixtures: a strategy that genuinely enters and exits -----------------

    /** Enters when close > 102, exits when close < 98, full cash fraction. */
    private static StrategyDefinition tradingStrategy() {
        return new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(102)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(98)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    /** Six daily bars producing exactly one BUY (bar 3 open) then one SELL (bar 5 open). */
    private static byte[] sixBarCsv() {
        return ("date,open,high,low,close,volume\n"
                + "2024-01-02,100,101,99,100,1000\n"
                + "2024-01-03,100,106,99,105,1000\n"
                + "2024-01-04,105,109,104,108,1000\n"
                + "2024-01-05,108,110,95,96,1000\n"
                + "2024-01-08,96,99,90,92,1000\n"
                + "2024-01-09,92,95,88,90,1000\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static BacktestConfig tradingConfig() {
        return new BacktestConfig(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 9));
    }

    private record OwnedRefs(UserId owner, StrategyVersionRef strategy, DatasetVersionRef dataset) {
    }

    private OwnedRefs createOwnedStrategyAndDataset(String label, StrategyDefinition definition, byte[] csv) {
        UserId owner = TestUsers.create(jdbcTemplate, label);
        StrategySummary strategy = strategyService.createStrategy(owner, label + "-strategy-" + System.nanoTime(),
                "", definition);
        DatasetSummary dataset = datasetService.createDataset(owner, label + "-dataset-" + System.nanoTime(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), csv, AdjustmentBasis.RAW, "bars.csv");
        return new OwnedRefs(owner, new StrategyVersionRef(strategy.id(), 1), new DatasetVersionRef(dataset.id(), 1));
    }

    // --- orchestration flow -----------------------------------------------------

    @Test
    void createRunPersistsAndReturnsASummaryMatchingTheStoredRow() {
        OwnedRefs refs = createOwnedStrategyAndDataset("orch", tradingStrategy(), sixBarCsv());

        BacktestRunSummary summary = backtestRunService.createRun(refs.owner(), refs.strategy(), refs.dataset(),
                tradingConfig());

        assertEquals(refs.strategy().strategyId(), summary.strategyId());
        assertEquals(refs.strategy().versionNumber(), summary.strategyVersionNumber());
        assertEquals(refs.dataset().datasetId(), summary.datasetId());
        assertEquals(refs.dataset().versionNumber(), summary.datasetVersionNumber());
        assertEquals(Backtester.SEMANTICS_VERSION, summary.engineSemanticsVersion());

        BacktestRunDetail detail = backtestRunService.getRun(refs.owner(), summary.id());
        assertEquals(6, detail.equityCurve().size());
        assertEquals(2, detail.fills().size());
        assertEquals(0, detail.rejections().size());
        assertEquals(1, detail.metrics().closedTradeCount());
    }

    // --- transaction boundary (D-34 Batch 2 §3, §15) ----------------------------

    @Test
    void engineExecutesOutsideAnyTransaction() {
        OwnedRefs refs = createOwnedStrategyAndDataset("txn", tradingStrategy(), sixBarCsv());
        AtomicBoolean transactionActiveDuringEngineRun = new AtomicBoolean(true);

        doAnswer(invocation -> {
            transactionActiveDuringEngineRun.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(backtester).run(any(BarSeries.class), any(StrategyDefinition.class), any(BacktestConfig.class));

        backtestRunService.createRun(refs.owner(), refs.strategy(), refs.dataset(), tradingConfig());

        verify(backtester).run(any(BarSeries.class), any(StrategyDefinition.class), any(BacktestConfig.class));
        assertFalse(transactionActiveDuringEngineRun.get());
    }

    // --- range validation --------------------------------------------------------

    @Test
    void aRangeOutsideDatasetCoverageIsRejectedAndPersistsNothing() {
        OwnedRefs refs = createOwnedStrategyAndDataset("range", tradingStrategy(), sixBarCsv());
        BacktestConfig outOfRange = new BacktestConfig(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2023, 12, 1), LocalDate.of(2024, 1, 9));

        assertThrows(BacktestRangeException.class,
                () -> backtestRunService.createRun(refs.owner(), refs.strategy(), refs.dataset(), outOfRange));

        assertTrue(backtestRunService.listRuns(refs.owner()).isEmpty());
    }

    // --- ownership (D-34 Batch 2 §11) --------------------------------------------

    @Test
    void anotherOwnersStrategyVersionIsNotFoundAndPersistsNothing() {
        OwnedRefs refs = createOwnedStrategyAndDataset("own-a", tradingStrategy(), sixBarCsv());
        UserId otherOwner = TestUsers.create(jdbcTemplate, "own-b");
        DatasetSummary otherDataset = datasetService.createDataset(otherOwner, "own-b-dataset-" + System.nanoTime(),
                "AAPL");
        datasetService.createVersionFromCsv(otherOwner, otherDataset.id(), sixBarCsv(), AdjustmentBasis.RAW, "b.csv");

        assertThrows(StrategyVersionNotFoundException.class, () -> backtestRunService.createRun(otherOwner,
                refs.strategy(), new DatasetVersionRef(otherDataset.id(), 1), tradingConfig()));

        assertTrue(backtestRunService.listRuns(otherOwner).isEmpty());
    }

    @Test
    void anotherOwnersDatasetVersionIsNotFoundAndPersistsNothing() {
        OwnedRefs refs = createOwnedStrategyAndDataset("own-c", tradingStrategy(), sixBarCsv());
        UserId otherOwner = TestUsers.create(jdbcTemplate, "own-d");
        StrategySummary otherStrategy = strategyService.createStrategy(otherOwner, "own-d-strategy-" + System.nanoTime(),
                "", tradingStrategy());

        assertThrows(DatasetVersionNotFoundException.class, () -> backtestRunService.createRun(otherOwner,
                new StrategyVersionRef(otherStrategy.id(), 1), refs.dataset(), tradingConfig()));

        assertTrue(backtestRunService.listRuns(otherOwner).isEmpty());
    }

    @Test
    void anotherOwnerCannotReadTheRun() {
        OwnedRefs refs = createOwnedStrategyAndDataset("own-e", tradingStrategy(), sixBarCsv());
        BacktestRunSummary summary = backtestRunService.createRun(refs.owner(), refs.strategy(), refs.dataset(),
                tradingConfig());
        UserId otherOwner = TestUsers.create(jdbcTemplate, "own-f");

        assertThrows(BacktestRunNotFoundException.class, () -> backtestRunService.getRun(otherOwner, summary.id()));
    }

    @Test
    void listRunsOnlyContainsOwnRuns() {
        OwnedRefs a = createOwnedStrategyAndDataset("list-a", tradingStrategy(), sixBarCsv());
        OwnedRefs b = createOwnedStrategyAndDataset("list-b", tradingStrategy(), sixBarCsv());
        BacktestRunSummary runA = backtestRunService.createRun(a.owner(), a.strategy(), a.dataset(), tradingConfig());
        backtestRunService.createRun(b.owner(), b.strategy(), b.dataset(), tradingConfig());

        List<BacktestRunSummary> aRuns = backtestRunService.listRuns(a.owner());
        assertEquals(1, aRuns.size());
        assertEquals(runA.id(), aRuns.get(0).id());
    }

    // --- atomic rollback (D-34 Batch 2 §6) ---------------------------------------

    @Test
    void aChildInsertFailureRollsBackTheEntireRun() {
        OwnedRefs refs = createOwnedStrategyAndDataset("rollback-child", tradingStrategy(), sixBarCsv());
        doThrow(new RuntimeException("simulated child insert failure"))
                .when(fillRepository).insertAll(anyLong(), anyList());

        assertThrows(RuntimeException.class,
                () -> backtestRunService.createRun(refs.owner(), refs.strategy(), refs.dataset(), tradingConfig()));

        assertTrue(backtestRunService.listRuns(refs.owner()).isEmpty());
        Long equityCount = jdbcTemplate.queryForObject(
                "select count(*) from backtest_equity_point e join backtest_run r on r.id = e.run_id "
                        + "where r.owner_id = ?", Long.class, refs.owner().value());
        assertEquals(0L, equityCount);
    }

    @Test
    void anEngineFailureResultsInZeroPersistedRows() {
        OwnedRefs refs = createOwnedStrategyAndDataset("rollback-engine", tradingStrategy(), sixBarCsv());
        doThrow(new RuntimeException("simulated engine failure"))
                .when(backtester).run(any(BarSeries.class), any(StrategyDefinition.class), any(BacktestConfig.class));

        assertThrows(RuntimeException.class,
                () -> backtestRunService.createRun(refs.owner(), refs.strategy(), refs.dataset(), tradingConfig()));

        assertTrue(backtestRunService.listRuns(refs.owner()).isEmpty());
    }

    // --- numerical edge: an extreme valid value the engine cannot represent -----

    @Test
    void anExtremeInitialCapitalCausingEngineOverflowPersistsNothing() {
        // A CashFraction(1) entry sized against an astronomically large initialCapital
        // overflows Backtester.enterQuantity's longValueExact() - an engine-side
        // ArithmeticException, not a BacktestConfig validation failure. CLAUDE.md/D-34:
        // this may remain an uncaught 500-class failure; no arbitrary magnitude cap is
        // introduced to prevent it.
        OwnedRefs refs = createOwnedStrategyAndDataset("numeric-edge", tradingStrategy(), sixBarCsv());
        BacktestConfig extreme = new BacktestConfig(new BigDecimal("1" + "0".repeat(25)), BigDecimal.ZERO,
                BigDecimal.ZERO, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 9));

        assertThrows(ArithmeticException.class,
                () -> backtestRunService.createRun(refs.owner(), refs.strategy(), refs.dataset(), extreme));

        assertTrue(backtestRunService.listRuns(refs.owner()).isEmpty());
    }
}
