package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.AdjustmentBasis;
import in.vedchangani.parallax.backend.dataset.DatasetFixtures;
import in.vedchangani.parallax.backend.dataset.DatasetIntegrityException;
import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.dataset.DatasetSummary;
import in.vedchangani.parallax.backend.strategy.StrategyService;
import in.vedchangani.parallax.backend.strategy.StrategySummary;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionCodec;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionIntegrityException;
import in.vedchangani.parallax.backend.dataset.VerifiedDatasetVersion;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                + "2024-01-09,92,95,88,90,1000\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static BacktestConfig tradingConfig() {
        return new BacktestConfig(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 9));
    }

    private long createRealRun(UserId owner, String label) {
        StrategySummary strategy = strategyService.createStrategy(owner, BacktestFixtures.uniqueName(label + "-s"),
                "", tradingStrategy());
        DatasetSummary dataset = datasetService.createDataset(owner, BacktestFixtures.uniqueName(label + "-d"),
                "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), sixBarCsv(), AdjustmentBasis.RAW, "a.csv");
        return backtestRunService.createRun(owner, new StrategyVersionRef(strategy.id(), 1),
                new DatasetVersionRef(dataset.id(), 1), tradingConfig()).id();
    }

    @Test
    void createdRunRoundTripsExactlyThroughGetRun() {
        UserId owner = TestUsers.create(jdbcTemplate, "repro");
        StrategyDefinition strategyDef = tradingStrategy();
        StrategySummary strategy = strategyService.createStrategy(owner, "repro-strategy-" + System.nanoTime(), "",
                strategyDef);
        DatasetSummary dataset = datasetService.createDataset(owner, "repro-dataset-" + System.nanoTime(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), sixBarCsv(), AdjustmentBasis.RAW, "a.csv");
        BacktestConfig config = tradingConfig();

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
    void identicalRunsAreColumnByColumnIdenticalInThePersistedState() {
        UserId owner = TestUsers.create(jdbcTemplate, "repro-column");
        StrategySummary strategy = strategyService.createStrategy(owner, BacktestFixtures.uniqueName("repro-col-s"),
                "", tradingStrategy());
        DatasetSummary dataset = datasetService.createDataset(owner, BacktestFixtures.uniqueName("repro-col-d"),
                "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), sixBarCsv(), AdjustmentBasis.RAW, "a.csv");
        StrategyVersionRef strategyRef = new StrategyVersionRef(strategy.id(), 1);
        DatasetVersionRef datasetRef = new DatasetVersionRef(dataset.id(), 1);

        long firstId = backtestRunService.createRun(owner, strategyRef, datasetRef, tradingConfig()).id();
        long secondId = backtestRunService.createRun(owner, strategyRef, datasetRef, tradingConfig()).id();
        assertTrue(firstId != secondId);

        Map<String, Object> firstRun = new LinkedHashMap<>(
                jdbcTemplate.queryForMap("select * from backtest_run where id = ?", firstId));
        Map<String, Object> secondRun = new LinkedHashMap<>(
                jdbcTemplate.queryForMap("select * from backtest_run where id = ?", secondId));
        firstRun.remove("id");
        firstRun.remove("created_at");
        secondRun.remove("id");
        secondRun.remove("created_at");
        assertEquals(firstRun, secondRun);

        assertEquals(
                jdbcTemplate.queryForList("select bar_date, cash, quantity, cost_basis, realized_pnl, close "
                        + "from backtest_equity_point where run_id = ? order by bar_date", firstId),
                jdbcTemplate.queryForList("select bar_date, cash, quantity, cost_basis, realized_pnl, close "
                        + "from backtest_equity_point where run_id = ? order by bar_date", secondId));

        assertEquals(
                jdbcTemplate.queryForList("select order_id, fill_date, quantity, reference_open, fill_price, "
                        + "commission, signal_type, signal_date, signal_close, signal_indicators::text "
                        + "from backtest_fill where run_id = ? order by order_id", firstId),
                jdbcTemplate.queryForList("select order_id, fill_date, quantity, reference_open, fill_price, "
                        + "commission, signal_type, signal_date, signal_close, signal_indicators::text "
                        + "from backtest_fill where run_id = ? order by order_id", secondId));

        assertEquals(
                jdbcTemplate.queryForList(
                        "select seq, reason from backtest_rejection where run_id = ? order by seq", firstId),
                jdbcTemplate.queryForList(
                        "select seq, reason from backtest_rejection where run_id = ? order by seq", secondId));
    }

    @Test
    void aFreshlyCreatedRunPassesFullIntegrityVerification() {
        UserId owner = TestUsers.create(jdbcTemplate, "valid-run");
        long runId = createRealRun(owner, "valid-run");

        BacktestRunDetail detail = backtestRunService.getRun(owner, runId);

        assertEquals(2, detail.fills().size());
        assertEquals(1, detail.metrics().closedTradeCount());
    }

    @Test
    void anUnchangedFullCloneAlsoPassesFullIntegrityVerification() {
        UserId owner = TestUsers.create(jdbcTemplate, "valid-clone");
        long sourceRunId = createRealRun(owner, "valid-clone");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneChildRows(jdbcTemplate, clonedRunId, sourceRunId);

        BacktestRunDetail original = backtestRunService.getRun(owner, sourceRunId);
        BacktestRunDetail clone = backtestRunService.getRun(owner, clonedRunId);
        assertEquals(original.equityCurve(), clone.equityCurve());
        assertEquals(original.fills(), clone.fills());
        assertEquals(original.metrics(), clone.metrics());
    }

    @Test
    void metricTamperedByOneUlpIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-metric");
        long sourceRunId = createRealRun(owner, "tamper-metric");
        double realTotalReturn = jdbcTemplate.queryForObject(
                "select total_return from backtest_run where id = ?", Double.class, sourceRunId);

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId,
                Map.of("total_return", Math.nextUp(realTotalReturn)));
        BacktestFixtures.cloneChildRows(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void equityLedgerCashTamperingIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-ledger");
        long sourceRunId = createRealRun(owner, "tamper-ledger");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPointsWithCashOverride(jdbcTemplate, clonedRunId, sourceRunId,
                LocalDate.of(2024, 1, 2), new BigDecimal("9999"));
        BacktestFixtures.cloneFills(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneRejections(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void fillDateTamperingIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-fill-date");
        long sourceRunId = createRealRun(owner, "tamper-fill-date");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFillsWithOverride(jdbcTemplate, clonedRunId, sourceRunId, 1, null,
                LocalDate.of(2024, 1, 5), null, null);
        BacktestFixtures.cloneRejections(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void fillPriceTamperingIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-fill-price");
        long sourceRunId = createRealRun(owner, "tamper-fill-price");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFillsWithOverride(jdbcTemplate, clonedRunId, sourceRunId, 1, null, null,
                new BigDecimal("106"), null);
        BacktestFixtures.cloneRejections(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void fillCommissionTamperingIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-fill-commission");
        long sourceRunId = createRealRun(owner, "tamper-fill-commission");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFillsWithOverride(jdbcTemplate, clonedRunId, sourceRunId, 1, null, null, null,
                new BigDecimal("1"));
        BacktestFixtures.cloneRejections(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void invalidFirstEvaluableDateIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-first-evaluable");
        long sourceRunId = createRealRun(owner, "tamper-first-evaluable");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId,
                Map.of("first_evaluable_date", LocalDate.of(2024, 1, 5)));
        BacktestFixtures.cloneChildRows(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void illegalOrderIdSequenceIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-order-id");
        long sourceRunId = createRealRun(owner, "tamper-order-id");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFillsWithOverride(jdbcTemplate, clonedRunId, sourceRunId, 2, 3, null, null, null);
        BacktestFixtures.cloneRejections(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void unsupportedSemanticsVersionIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-semantics");
        long sourceRunId = createRealRun(owner, "tamper-semantics");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId,
                Map.of("engine_semantics_version", Backtester.SEMANTICS_VERSION + 1));

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void benchmarkCashCostBasisMismatchIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-benchmark-identity");
        long sourceRunId = createRealRun(owner, "tamper-benchmark-identity");
        BigDecimal realCash = jdbcTemplate.queryForObject(
                "select benchmark_cash from backtest_run where id = ?", BigDecimal.class, sourceRunId);

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId,
                Map.of("benchmark_cash", realCash.add(BigDecimal.ONE)));
        BacktestFixtures.cloneChildRows(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void benchmarkTotalReturnTamperedByOneUlpIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-benchmark-return");
        long sourceRunId = createRealRun(owner, "tamper-benchmark-return");
        double realReturn = jdbcTemplate.queryForObject(
                "select benchmark_total_return from backtest_run where id = ?", Double.class, sourceRunId);

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId,
                Map.of("benchmark_total_return", Math.nextUp(realReturn)));
        BacktestFixtures.cloneChildRows(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

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

        long datasetVersionRowId = jdbcTemplate.queryForObject(
                "select id from dataset_version where dataset_id = ? and version_number = 1", Long.class,
                dataset.id());
        jdbcTemplate.update(
                "insert into dataset_bar (dataset_version_id, bar_date, open, high, low, close, volume) "
                        + "values (?, '2024-01-04', 108, 115, 107, 112, 900)", datasetVersionRowId);

        BacktestRunDetail detail = backtestRunService.getRun(owner, summary.id());
        assertEquals(2, detail.equityCurve().size());

        assertThrows(DatasetIntegrityException.class, () -> backtestRunService.createRun(owner,
                new StrategyVersionRef(strategy.id(), 1), new DatasetVersionRef(dataset.id(), 1), config));
    }

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

        jdbcTemplate.update(
                "insert into strategy_version (strategy_id, version_number, definition, "
                        + "definition_schema_version, definition_hash) values (?, 2, "
                        + "'{\"schemaVersion\":1,\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":"
                        + "\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"constant\",\"value\":\"0\"}},"
                        + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":"
                        + "\"LT\",\"right\":{\"type\":\"constant\",\"value\":\"0\"}},\"positionSizing\":{\"type\":"
                        + "\"cashFraction\",\"fraction\":\"1\"}}'::jsonb, 1, ?)",
                strategy.id(), "0".repeat(64));

        BacktestRunDetail detail = backtestRunService.getRun(owner, summary.id());
        assertEquals(2, detail.equityCurve().size());

        assertThrows(StrategyDefinitionIntegrityException.class, () -> backtestRunService.createRun(owner,
                new StrategyVersionRef(strategy.id(), 2), new DatasetVersionRef(dataset.id(), 1), config));
    }

    private static StrategyDefinition indicatorStrategy() {
        IndicatorSpec sma2 = new IndicatorSpec(IndicatorType.SMA, 2);
        return new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.IndicatorRef(sma2)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.IndicatorRef(sma2)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    private long createIndicatorStrategyRun(UserId owner, String label) {
        StrategySummary strategy = strategyService.createStrategy(owner, BacktestFixtures.uniqueName(label + "-s"),
                "", indicatorStrategy());
        DatasetSummary dataset = datasetService.createDataset(owner, BacktestFixtures.uniqueName(label + "-d"),
                "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), sixBarCsv(), AdjustmentBasis.RAW, "a.csv");
        return backtestRunService.createRun(owner, new StrategyVersionRef(strategy.id(), 1),
                new DatasetVersionRef(dataset.id(), 1), tradingConfig()).id();
    }

    private static StrategyDefinition alwaysEnterStrategy() {
        return new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(50)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    private static byte[] gapUpCsv() {
        return ("date,open,high,low,close,volume\n"
                + "2024-01-02,100,101,99,100,1000\n"
                + "2024-01-03,200,205,199,200,1000\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static BacktestConfig gapUpConfig() {
        return new BacktestConfig(new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3));
    }

    private long createInsufficientCashRun(UserId owner, String label) {
        StrategySummary strategy = strategyService.createStrategy(owner, BacktestFixtures.uniqueName(label + "-s"),
                "", alwaysEnterStrategy());
        DatasetSummary dataset = datasetService.createDataset(owner, BacktestFixtures.uniqueName(label + "-d"),
                "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), gapUpCsv(), AdjustmentBasis.RAW, "a.csv");
        return backtestRunService.createRun(owner, new StrategyVersionRef(strategy.id(), 1),
                new DatasetVersionRef(dataset.id(), 1), gapUpConfig()).id();
    }

    @Test
    void aValidInsufficientCashRunPassesFullIntegrityVerification() {
        UserId owner = TestUsers.create(jdbcTemplate, "valid-insufficient-cash");
        long runId = createInsufficientCashRun(owner, "valid-insufficient-cash");

        BacktestRunDetail detail = backtestRunService.getRun(owner, runId);

        assertEquals(0, detail.fills().size());
        assertEquals(1, detail.rejections().size());
    }

    @Test
    void aValidIndicatorStrategyRunPassesFullIntegrityVerification() {
        UserId owner = TestUsers.create(jdbcTemplate, "valid-indicator");
        long runId = createIndicatorStrategyRun(owner, "valid-indicator");

        BacktestRunDetail detail = backtestRunService.getRun(owner, runId);

        assertEquals(2, detail.fills().size());
        assertEquals(1, detail.metrics().closedTradeCount());
    }

    @Test
    void fillSignalDateNotBeforeItsOwnExecutionIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-fill-causal");
        long sourceRunId = createRealRun(owner, "tamper-fill-causal");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFillsWithColumnOverrides(jdbcTemplate, clonedRunId, sourceRunId, 1,
                Map.of("signal_date", LocalDate.of(2024, 1, 4), "signal_close", new BigDecimal("108")));
        BacktestFixtures.cloneRejections(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void insufficientCashExecutionDateNotAfterSignalIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-ic-timing");
        long sourceRunId = createInsufficientCashRun(owner, "tamper-ic-timing");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFills(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneRejectionsWithColumnOverride(jdbcTemplate, clonedRunId, sourceRunId, 1,
                Map.of("execution_date", LocalDate.of(2024, 1, 2)));

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void signalSnapshotCloseMismatchIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-signal-close");
        long sourceRunId = createRealRun(owner, "tamper-signal-close");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFills(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.insertZeroQuantityRejection(jdbcTemplate, clonedRunId, 1, LocalDate.of(2024, 1, 2),
                new BigDecimal("999"), "[]");

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void signalSnapshotIndicatorSpecMismatchIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-spec-mismatch");
        long sourceRunId = createIndicatorStrategyRun(owner, "tamper-spec-mismatch");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFillsWithColumnOverrides(jdbcTemplate, clonedRunId, sourceRunId, 1,
                Map.of("signal_indicators", "[]"));
        BacktestFixtures.cloneRejections(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void duplicateIndicatorSpecInSnapshotIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-spec-duplicate");
        long sourceRunId = createIndicatorStrategyRun(owner, "tamper-spec-duplicate");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFillsWithColumnOverrides(jdbcTemplate, clonedRunId, sourceRunId, 1,
                Map.of("signal_indicators",
                        "[{\"type\":\"SMA\",\"period\":2,\"value\":\"102.5\"},"
                                + "{\"type\":\"SMA\",\"period\":2,\"value\":\"102.5\"}]"));
        BacktestFixtures.cloneRejections(jdbcTemplate, clonedRunId, sourceRunId);

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void signalDoesNotSatisfyStrategyConditionIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-condition-truth");
        long sourceRunId = createRealRun(owner, "tamper-condition-truth");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFills(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.insertZeroQuantityRejection(jdbcTemplate, clonedRunId, 1, LocalDate.of(2024, 1, 2),
                new BigDecimal("100"), "[]");

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void insufficientCashAvailableCashMismatchIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-ic-cash");
        long sourceRunId = createInsufficientCashRun(owner, "tamper-ic-cash");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFills(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneRejectionsWithColumnOverride(jdbcTemplate, clonedRunId, sourceRunId, 1,
                Map.of("available_cash", new BigDecimal("999")));

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }

    @Test
    void enterSignalWhilePortfolioLongIsDetected() {
        UserId owner = TestUsers.create(jdbcTemplate, "tamper-position-state");
        long sourceRunId = createRealRun(owner, "tamper-position-state");

        long clonedRunId = BacktestFixtures.cloneRunRow(jdbcTemplate, sourceRunId, Map.of());
        BacktestFixtures.cloneEquityPoints(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.cloneFills(jdbcTemplate, clonedRunId, sourceRunId);
        BacktestFixtures.insertZeroQuantityRejection(jdbcTemplate, clonedRunId, 1, LocalDate.of(2024, 1, 4),
                new BigDecimal("108"), "[]");

        assertThrows(BacktestResultIntegrityException.class, () -> backtestRunService.getRun(owner, clonedRunId));
    }
}
