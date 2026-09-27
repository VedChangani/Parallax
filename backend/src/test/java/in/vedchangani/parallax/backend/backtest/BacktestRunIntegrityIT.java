package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.AdjustmentBasis;
import in.vedchangani.parallax.backend.dataset.DatasetFixtures;
import in.vedchangani.parallax.backend.dataset.DatasetIntegrityException;
import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.dataset.DatasetSummary;
import in.vedchangani.parallax.backend.dataset.VerifiedDatasetVersion;
import in.vedchangani.parallax.backend.strategy.StrategyService;
import in.vedchangani.parallax.backend.strategy.StrategySummary;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionCodec;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionIntegrityException;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.metrics.BuyAndHoldBenchmark;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.result.Trade;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-34 Batch 2 read-time integrity behavior against real PostgreSQL
 * (Testcontainers): the tampering matrix (§12), engine result
 * reproducibility through a full create/read round trip (§10), and the
 * metric/benchmark no-recompute-on-read policy (§9). Tampering uses the
 * project's own established technique (see {@code DatasetSchemaIT}): direct
 * {@code INSERT}s that bypass the service, since every backtest table
 * rejects {@code UPDATE}/{@code DELETE} at the database level.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BacktestRunIntegrityIT {

    @Autowired
    private BacktestRunService backtestRunService;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private StrategyDefinitionCodec codec;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // --- golden (self-consistent) baseline row, tampered one field at a time ---

    private Map<String, Object> baselineValues(BacktestFixtures.Inputs in) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("owner_id", in.owner().value());
        values.put("strategy_id", in.strategyId());
        values.put("strategy_version_number", in.strategyVersionNumber());
        values.put("strategy_definition_hash", in.strategyDefinitionHash());
        values.put("dataset_id", in.datasetId());
        values.put("dataset_version_number", in.datasetVersionNumber());
        values.put("dataset_content_hash", in.datasetContentHash());
        values.put("engine_semantics_version", Backtester.SEMANTICS_VERSION);
        values.put("initial_capital", new BigDecimal("10000"));
        values.put("commission_per_fill", new BigDecimal("1"));
        values.put("slippage_rate", new BigDecimal("0.001"));
        values.put("start_date", LocalDate.of(2024, 1, 2));
        values.put("end_date", LocalDate.of(2024, 1, 3));
        values.put("first_evaluable_date", null);
        values.put("total_commission", BigDecimal.ZERO);
        values.put("total_slippage_cost", BigDecimal.ZERO);
        values.put("total_return", 0.0);
        values.put("cagr", null);
        values.put("volatility", null);
        values.put("sharpe_ratio", null);
        values.put("max_drawdown", 0.0);
        values.put("closed_trade_count", 0);
        values.put("win_rate", null);
        values.put("average_win", null);
        values.put("average_loss", null);
        values.put("benchmark_cash", new BigDecimal("100"));
        values.put("benchmark_quantity", 10L);
        values.put("benchmark_cost_basis", new BigDecimal("9900"));
        values.put("benchmark_total_return", 0.0);
        return values;
    }

    private long insertRun(Map<String, Object> values) {
        String columns = String.join(", ", values.keySet());
        String placeholders = values.keySet().stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = "insert into backtest_run (" + columns + ") values (" + placeholders + ") returning id";
        return jdbcTemplate.queryForObject(sql, Long.class, values.values().toArray());
    }

    private void insertEquityPoint(long runId, LocalDate date, String cash, long quantity, String costBasis,
                                    String realizedPnl, String close) {
        jdbcTemplate.update(
                "insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, "
                        + "close) values (?, ?, ?, ?, ?, ?, ?)",
                runId, date, new BigDecimal(cash), quantity, new BigDecimal(costBasis), new BigDecimal(realizedPnl),
                new BigDecimal(close));
    }

    /** The baseline: a flat, no-trade run over the two-bar dataset {@code BacktestFixtures} sets up. */
    private long insertGoldenRun(BacktestFixtures.Inputs in) {
        long runId = insertRun(baselineValues(in));
        insertEquityPoint(runId, LocalDate.of(2024, 1, 2), "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, LocalDate.of(2024, 1, 3), "10000", 0, "0", "0", "101");
        return runId;
    }

    private BacktestFixtures.Inputs inputs(String label) {
        return BacktestFixtures.createInputs(jdbcTemplate, strategyService, datasetService, label);
    }

    // --- sanity: the golden baseline itself passes verification ----------------

    @Test
    void theGoldenBaselineRunPassesIntegrityVerification() {
        BacktestFixtures.Inputs in = inputs("golden");
        long runId = insertGoldenRun(in);

        BacktestRunDetail detail = backtestRunService.getRun(in.owner(), runId);

        assertEquals(2, detail.equityCurve().size());
        assertTrue(detail.fills().isEmpty());
        assertTrue(detail.rejections().isEmpty());
        assertEquals(0, detail.metrics().closedTradeCount());
    }

    // --- A/B: commission and slippage totals ------------------------------------

    @Test
    void tamperedTotalCommissionIsDetected() {
        BacktestFixtures.Inputs in = inputs("tamper-commission");
        Map<String, Object> values = baselineValues(in);
        values.put("total_commission", new BigDecimal("5"));
        long runId = insertRun(values);
        insertEquityPoint(runId, LocalDate.of(2024, 1, 2), "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, LocalDate.of(2024, 1, 3), "10000", 0, "0", "0", "101");

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(in.owner(), runId));
    }

    @Test
    void tamperedTotalSlippageCostIsDetected() {
        BacktestFixtures.Inputs in = inputs("tamper-slippage");
        Map<String, Object> values = baselineValues(in);
        values.put("total_slippage_cost", new BigDecimal("5"));
        long runId = insertRun(values);
        insertEquityPoint(runId, LocalDate.of(2024, 1, 2), "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, LocalDate.of(2024, 1, 3), "10000", 0, "0", "0", "101");

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(in.owner(), runId));
    }

    // --- C: a fill that breaks Trade.fromFills's alternation invariant ---------

    @Test
    void tamperedFillBreakingTradeAlternationIsDetected() {
        BacktestFixtures.Inputs in = inputs("tamper-fill");
        long runId = insertRun(baselineValues(in));
        insertEquityPoint(runId, LocalDate.of(2024, 1, 2), "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, LocalDate.of(2024, 1, 3), "10000", 0, "0", "0", "101");
        insertFill(runId, 1, LocalDate.of(2024, 1, 2), 10, "100", "100", "0", "ENTER", LocalDate.of(2024, 1, 2), "100");
        // Two consecutive BUY (ENTER) fills - never a valid alternating sequence.
        insertFill(runId, 2, LocalDate.of(2024, 1, 3), 10, "100", "100", "0", "ENTER", LocalDate.of(2024, 1, 3), "101");

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(in.owner(), runId));
    }

    private void insertFill(long runId, int orderId, LocalDate fillDate, long quantity, String referenceOpen,
                             String fillPrice, String commission, String signalType, LocalDate signalDate,
                             String signalClose) {
        jdbcTemplate.update("""
                insert into backtest_fill
                    (run_id, order_id, fill_date, quantity, reference_open, fill_price, commission,
                     signal_type, signal_date, signal_close, signal_indicators)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]'::jsonb)
                """, runId, orderId, fillDate, quantity, new BigDecimal(referenceOpen), new BigDecimal(fillPrice),
                new BigDecimal(commission), signalType, signalDate, new BigDecimal(signalClose));
    }

    // --- D: equity point content that fails the engine's own EquityPoint constructor ---

    @Test
    void tamperedEquityPointContentFailsEngineReconstruction() {
        BacktestFixtures.Inputs in = inputs("tamper-equity");
        long runId = insertRun(baselineValues(in));
        insertEquityPoint(runId, LocalDate.of(2024, 1, 2), "10000", 0, "0", "0", "100");
        // quantity = 0 with a non-zero cost basis violates EquityPoint's own invariant.
        insertEquityPoint(runId, LocalDate.of(2024, 1, 3), "9000", 0, "100", "0", "101");

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(in.owner(), runId));
    }

    // --- E: a rejection shape that is valid at the database but not at the engine ---

    @Test
    void tamperedInsufficientCashRejectionShapeIsDetected() {
        BacktestFixtures.Inputs in = inputs("tamper-rejection");
        long runId = insertRun(baselineValues(in));
        insertEquityPoint(runId, LocalDate.of(2024, 1, 2), "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, LocalDate.of(2024, 1, 3), "10000", 0, "0", "0", "101");
        // The database's ck_backtest_rejection_shape only requires every INSUFFICIENT_CASH
        // field to be non-null - it does not check requiredCash > availableCash, which is
        // OrderRejection.InsufficientCash's own application-level invariant.
        jdbcTemplate.update("""
                insert into backtest_rejection
                    (run_id, seq, reason, order_id, execution_date, quantity, required_cash, available_cash,
                     signal_date, signal_close, signal_indicators)
                values (?, 1, 'INSUFFICIENT_CASH', 1, '2024-01-02', 10, 100, 500, '2024-01-02', 100, '[]'::jsonb)
                """, runId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(in.owner(), runId));
    }

    /**
     * The persisted {@code seq} column alone cannot catch every chronology
     * violation: a tampered row can keep {@code seq} valid (1, 2, ...) while
     * its date regresses. {@code OrderRejection} dates must still be
     * non-decreasing in append order, mirroring {@code BacktestResult}'s own
     * engine-level invariant (D-24).
     */
    @Test
    void tamperedRejectionDateOrderingIsDetectedDespiteAValidSeq() {
        BacktestFixtures.Inputs in = inputs("tamper-rejection-date");
        long runId = insertRun(baselineValues(in));
        insertEquityPoint(runId, LocalDate.of(2024, 1, 2), "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, LocalDate.of(2024, 1, 3), "10000", 0, "0", "0", "101");
        // seq is a valid, gap-free 1, 2 - but the second rejection's date is earlier
        // than the first's, which can never happen in genuine engine append order.
        jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, signal_date, signal_close, signal_indicators)
                values (?, 1, 'ZERO_QUANTITY', '2024-01-03', 101, '[]'::jsonb)
                """, runId);
        jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, signal_date, signal_close, signal_indicators)
                values (?, 2, 'ZERO_QUANTITY', '2024-01-02', 100, '[]'::jsonb)
                """, runId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(in.owner(), runId));
    }

    // --- F/G: benchmark accounting identity -------------------------------------

    @Test
    void tamperedBenchmarkCashCostBasisMismatchIsDetected() {
        BacktestFixtures.Inputs in = inputs("tamper-benchmark");
        Map<String, Object> values = baselineValues(in);
        values.put("benchmark_cash", new BigDecimal("50")); // 50 + 9900 != 10000
        long runId = insertRun(values);
        insertEquityPoint(runId, LocalDate.of(2024, 1, 2), "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, LocalDate.of(2024, 1, 3), "10000", 0, "0", "0", "101");

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(in.owner(), runId));
    }

    // --- G: a PerformanceMetrics structural invariant violated ------------------

    @Test
    void tamperedPerformanceMetricsStructuralValueIsDetected() {
        BacktestFixtures.Inputs in = inputs("tamper-metrics");
        Map<String, Object> values = baselineValues(in);
        // closed_trade_count = 0 but win_rate present - PerformanceMetrics's own
        // constructor requires winRate to be empty iff closedTradeCount == 0.
        values.put("win_rate", 0.5);
        long runId = insertRun(values);
        insertEquityPoint(runId, LocalDate.of(2024, 1, 2), "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, LocalDate.of(2024, 1, 3), "10000", 0, "0", "0", "101");

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(in.owner(), runId));
    }

    // --- H: DatasetVersion tampered after a run has completed -------------------

    @Test
    void completedRunSurvivesDatasetTamperingButANewRunAgainstItFails() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-dataset");
        StrategySummary strategy = strategyService.createStrategy(owner, "tamper-dataset-strategy-" + System.nanoTime(),
                "", BacktestFixtures.simpleStrategyDefinition());
        DatasetSummary dataset = datasetService.createDataset(owner, "tamper-dataset-ds-" + System.nanoTime(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");

        BacktestConfig config = new BacktestConfig(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3));
        BacktestRunSummary summary = backtestRunService.createRun(owner, new StrategyVersionRef(strategy.id(), 1),
                new DatasetVersionRef(dataset.id(), 1), config);

        // Tamper directly: insert a third bar the stored barCount/hash metadata does not
        // account for (the same technique DatasetSchemaIT uses).
        long datasetVersionRowId = jdbcTemplate.queryForObject(
                "select id from dataset_version where dataset_id = ? and version_number = 1", Long.class,
                dataset.id());
        jdbcTemplate.update(
                "insert into dataset_bar (dataset_version_id, bar_date, open, high, low, close, volume) "
                        + "values (?, '2024-01-04', 108, 115, 107, 112, 900)", datasetVersionRowId);

        // The already-completed run never reloads the original dataset bars, so it still reads back fine.
        BacktestRunDetail detail = backtestRunService.getRun(owner, summary.id());
        assertEquals(2, detail.equityCurve().size());

        // A brand-new run against the now-tampered dataset version fails with the existing,
        // unrelated dataset-integrity failure - not a backtest-specific one.
        assertThrows(DatasetIntegrityException.class, () -> backtestRunService.createRun(owner,
                new StrategyVersionRef(strategy.id(), 1), new DatasetVersionRef(dataset.id(), 1), config));
    }

    // --- I: StrategyVersion tampered after a run has completed ------------------

    @Test
    void completedRunSurvivesStrategyTamperingButANewRunAgainstItFails() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-strategy");
        StrategySummary strategy = strategyService.createStrategy(owner, "tamper-strategy-s-" + System.nanoTime(),
                "", BacktestFixtures.simpleStrategyDefinition());
        DatasetSummary dataset = datasetService.createDataset(owner, "tamper-strategy-ds-" + System.nanoTime(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");

        BacktestConfig config = new BacktestConfig(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3));
        BacktestRunSummary summary = backtestRunService.createRun(owner, new StrategyVersionRef(strategy.id(), 1),
                new DatasetVersionRef(dataset.id(), 1), config);

        // Tamper: insert a second, never-decoded version of the same strategy directly,
        // bypassing the codec's own encode() call, with a hash that disagrees with its content.
        jdbcTemplate.update(
                "insert into strategy_version (strategy_id, version_number, definition, "
                        + "definition_schema_version, definition_hash) values (?, 2, "
                        + "'{\"schemaVersion\":1,\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":"
                        + "\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"constant\",\"value\":\"0\"}},"
                        + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":"
                        + "\"LT\",\"right\":{\"type\":\"constant\",\"value\":\"0\"}},\"positionSizing\":{\"type\":"
                        + "\"cashFraction\",\"fraction\":\"1\"}}'::jsonb, 1, ?)",
                strategy.id(), "0".repeat(64));

        // The already-completed run never re-decodes the original strategy version, so it
        // still reads back fine.
        BacktestRunDetail detail = backtestRunService.getRun(owner, summary.id());
        assertEquals(2, detail.equityCurve().size());

        // A brand-new run against the tampered version fails with the existing, unrelated
        // strategy-definition-integrity failure - not a backtest-specific one.
        assertThrows(StrategyDefinitionIntegrityException.class, () -> backtestRunService.createRun(owner,
                new StrategyVersionRef(strategy.id(), 2), new DatasetVersionRef(dataset.id(), 1), config));
    }

    // --- §9: stored metrics/benchmark are never recomputed on read --------------

    @Test
    void storedMetricsAndBenchmarkAreReturnedVerbatimNeverRecomputed() {
        BacktestFixtures.Inputs in = inputs("no-recompute");
        Map<String, Object> values = baselineValues(in);
        // A span over 365 days with real growth would, if genuinely recomputed by
        // PerformanceMetrics.of(...), produce a specific cagr close to
        // (12000/10000)^(365/517) - 1 - never this deliberately arbitrary value.
        values.put("start_date", LocalDate.of(2020, 1, 1));
        values.put("end_date", LocalDate.of(2021, 6, 1));
        values.put("cagr", 0.123456);
        values.put("total_return", 0.2);
        values.put("benchmark_total_return", 0.777);
        long runId = insertRun(values);
        insertEquityPoint(runId, LocalDate.of(2020, 1, 1), "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, LocalDate.of(2021, 6, 1), "12000", 0, "0", "0", "120");

        BacktestRunDetail detail = backtestRunService.getRun(in.owner(), runId);

        assertEquals(0.123456, detail.metrics().cagr().orElseThrow());
        assertEquals(0.777, detail.benchmarkTotalReturn());
    }

    // --- §10: full engine result reproducibility through create + read ----------

    private static StrategyDefinition tradingStrategy() {
        return new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(102)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(98)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    private static byte[] sixBarCsv() {
        return ("date,open,high,low,close,volume\n"
                + "2024-01-02,100,101,99,100,1000\n"
                + "2024-01-03,100,106,99,105,1000\n"
                + "2024-01-04,105,109,104,108,1000\n"
                + "2024-01-05,108,110,95,96,1000\n"
                + "2024-01-08,96,99,90,92,1000\n"
                + "2024-01-09,92,95,88,90,1000\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    @Test
    void createdRunRoundTripsExactlyThroughGetRun() {
        UserId owner = TestUsers.create(jdbcTemplate, "repro");
        StrategyDefinition strategyDef = tradingStrategy();
        StrategySummary strategy = strategyService.createStrategy(owner, "repro-strategy-" + System.nanoTime(), "",
                strategyDef);
        DatasetSummary dataset = datasetService.createDataset(owner, "repro-dataset-" + System.nanoTime(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), sixBarCsv(), AdjustmentBasis.RAW, "a.csv");

        BacktestConfig config = new BacktestConfig(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 9));

        VerifiedDatasetVersion verified = datasetService.getVerifiedSeries(owner, dataset.id(), 1);
        BacktestResult expectedResult = new Backtester().run(verified.series(), strategyDef, config);
        PerformanceMetrics expectedMetrics = PerformanceMetrics.of(expectedResult);
        BuyAndHoldBenchmark expectedBenchmark = BuyAndHoldBenchmark.of(verified.series(), expectedResult);

        BacktestRunSummary summary = backtestRunService.createRun(owner, new StrategyVersionRef(strategy.id(), 1),
                new DatasetVersionRef(dataset.id(), 1), config);
        BacktestRunDetail detail = backtestRunService.getRun(owner, summary.id());

        assertEquals(codec.encode(strategyDef).sha256(), detail.summary().strategyDefinitionHash());
        assertEquals(Backtester.SEMANTICS_VERSION, detail.summary().engineSemanticsVersion());
        assertEquals(expectedResult.config(), detail.config());
        assertEquals(expectedResult.firstEvaluableDate(), detail.firstEvaluableDate());
        assertEquals(expectedResult.equityCurve(), detail.equityCurve());
        assertEquals(expectedResult.fills(), detail.fills());
        assertEquals(expectedResult.rejections(), detail.rejections());
        assertEquals(expectedResult.trades(), Trade.fromFills(detail.fills()));
        assertEquals(expectedResult.totalCommission(), detail.totalCommission());
        assertEquals(expectedResult.totalSlippageCost(), detail.totalSlippageCost());

        assertEquals(Double.doubleToRawLongBits(expectedMetrics.totalReturn()),
                Double.doubleToRawLongBits(detail.metrics().totalReturn()));
        assertEquals(expectedMetrics, detail.metrics());

        EquityPoint benchmarkReference = expectedBenchmark.equityCurve().get(0);
        assertEquals(benchmarkReference.cash(), detail.benchmarkCash());
        assertEquals(benchmarkReference.quantity(), detail.benchmarkQuantity());
        assertEquals(benchmarkReference.costBasis(), detail.benchmarkCostBasis());
        assertEquals(Double.doubleToRawLongBits(expectedBenchmark.totalReturn()),
                Double.doubleToRawLongBits(detail.benchmarkTotalReturn()));
    }

    @Test
    void twoIdenticalCreateRequestsProduceIdenticalResultContent() {
        UserId owner = TestUsers.create(jdbcTemplate, "repro-idempotent");
        StrategyDefinition strategyDef = tradingStrategy();
        StrategySummary strategy = strategyService.createStrategy(owner, "repro2-strategy-" + System.nanoTime(), "",
                strategyDef);
        DatasetSummary dataset = datasetService.createDataset(owner, "repro2-dataset-" + System.nanoTime(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), sixBarCsv(), AdjustmentBasis.RAW, "a.csv");
        BacktestConfig config = new BacktestConfig(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 9));

        StrategyVersionRef strategyRef = new StrategyVersionRef(strategy.id(), 1);
        DatasetVersionRef datasetRef = new DatasetVersionRef(dataset.id(), 1);
        BacktestRunSummary first = backtestRunService.createRun(owner, strategyRef, datasetRef, config);
        BacktestRunSummary second = backtestRunService.createRun(owner, strategyRef, datasetRef, config);

        BacktestRunDetail firstDetail = backtestRunService.getRun(owner, first.id());
        BacktestRunDetail secondDetail = backtestRunService.getRun(owner, second.id());

        assertEquals(firstDetail.config(), secondDetail.config());
        assertEquals(firstDetail.firstEvaluableDate(), secondDetail.firstEvaluableDate());
        assertEquals(firstDetail.equityCurve(), secondDetail.equityCurve());
        assertEquals(firstDetail.fills(), secondDetail.fills());
        assertEquals(firstDetail.rejections(), secondDetail.rejections());
        assertEquals(firstDetail.metrics(), secondDetail.metrics());
        assertEquals(firstDetail.totalCommission(), secondDetail.totalCommission());
        assertEquals(firstDetail.totalSlippageCost(), secondDetail.totalSlippageCost());
        assertEquals(firstDetail.benchmarkCash(), secondDetail.benchmarkCash());
        assertEquals(firstDetail.benchmarkQuantity(), secondDetail.benchmarkQuantity());
        assertEquals(firstDetail.benchmarkCostBasis(), secondDetail.benchmarkCostBasis());
        assertEquals(firstDetail.benchmarkTotalReturn(), secondDetail.benchmarkTotalReturn());
        assertTrue(first.id() != second.id());
    }
}
