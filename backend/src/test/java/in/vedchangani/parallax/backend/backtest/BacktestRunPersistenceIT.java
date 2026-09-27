package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.strategy.StrategyService;
import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.metrics.BuyAndHoldBenchmark;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-34 Batch 1 persistence proof, against real PostgreSQL (Testcontainers):
 * numeric/float fidelity through both the JPA ({@code BacktestRun}) and
 * plain-JDBC (equity point/fill/rejection) paths, the persisted engine
 * semantics version, and the {@link IndicatorSnapshot} JSON round trip.
 * Builds every engine value directly (never via {@code Backtester}), then
 * persists and re-reads it exactly as a later orchestration batch would.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BacktestRunPersistenceIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private BacktestRunRepository backtestRunRepository;

    @Autowired
    private BacktestEquityPointRepository equityPointRepository;

    @Autowired
    private BacktestFillRepository fillRepository;

    @Autowired
    private BacktestRejectionRepository rejectionRepository;

    // --- a single, fully-consistent scenario, built directly from engine types ---

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000.01");
    private static final BigDecimal COMMISSION_PER_FILL = new BigDecimal("1.25");
    private static final BigDecimal SLIPPAGE_RATE = new BigDecimal("0.0015");
    private static final LocalDate START_DATE = LocalDate.of(2024, 1, 2);
    private static final LocalDate END_DATE = LocalDate.of(2024, 1, 4);

    private static final double TRICKY_TOTAL_RETURN = 100.0 / 3.0;
    private static final double TRICKY_SHARPE_RATIO = 0.1 + 0.2;

    private BacktestConfig config() {
        return new BacktestConfig(INITIAL_CAPITAL, COMMISSION_PER_FILL, SLIPPAGE_RATE, START_DATE, END_DATE);
    }

    private List<EquityPoint> equityCurve() {
        BigDecimal cash = new BigDecimal("9000.00");
        BigDecimal costBasis = new BigDecimal("1000.01");
        return List.of(
                new EquityPoint(START_DATE, cash, 10, costBasis, BigDecimal.ZERO, new BigDecimal("100.50")),
                new EquityPoint(LocalDate.of(2024, 1, 3), cash, 10, costBasis, BigDecimal.ZERO,
                        new BigDecimal("101.75")),
                new EquityPoint(END_DATE, cash, 10, costBasis, BigDecimal.ZERO, new BigDecimal("99.25")));
    }

    private Fill sampleFill() {
        IndicatorSnapshot snapshot = BacktestFixtures.sampleSnapshot(START_DATE, new BigDecimal("100.50"));
        SignalEvent signal = new SignalEvent(SignalType.ENTER, snapshot);
        return new Fill(1, LocalDate.of(2024, 1, 3), 10, new BigDecimal("100.75"), new BigDecimal("101.00"),
                COMMISSION_PER_FILL, signal);
    }

    private List<OrderRejection> rejections() {
        IndicatorSnapshot zeroQtySnapshot = BacktestFixtures.sampleSnapshot(START_DATE, new BigDecimal("100.50"));
        SignalEvent zeroQtySignal = new SignalEvent(SignalType.ENTER, zeroQtySnapshot);
        OrderRejection.ZeroQuantity zeroQuantity = new OrderRejection.ZeroQuantity(zeroQtySignal);

        IndicatorSnapshot insufficientCashSnapshot =
                BacktestFixtures.sampleSnapshot(LocalDate.of(2024, 1, 3), new BigDecimal("101.75"));
        SignalEvent insufficientCashSignal = new SignalEvent(SignalType.ENTER, insufficientCashSnapshot);
        OrderRejection.InsufficientCash insufficientCash = new OrderRejection.InsufficientCash(2,
                LocalDate.of(2024, 1, 3), 5, new BigDecimal("600.00"), new BigDecimal("500.00"),
                insufficientCashSignal);

        return List.of(zeroQuantity, insufficientCash);
    }

    private BacktestResult result() {
        return new BacktestResult("AAPL", BacktestFixtures.simpleStrategyDefinition(), config(),
                Optional.of(START_DATE), equityCurve(), List.of(sampleFill()), rejections());
    }

    private PerformanceMetrics metrics() {
        return new PerformanceMetrics(TRICKY_TOTAL_RETURN, OptionalDouble.empty(), OptionalDouble.of(0.05),
                OptionalDouble.of(TRICKY_SHARPE_RATIO), 0.2, 0, OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty());
    }

    private BuyAndHoldBenchmark benchmark() {
        BigDecimal capital = new BigDecimal("10000.01");
        BigDecimal cash = new BigDecimal("9000.00");
        BigDecimal costBasis = new BigDecimal("1000.01");
        List<EquityPoint> curve = List.of(
                new EquityPoint(START_DATE, cash, 99, costBasis, BigDecimal.ZERO, new BigDecimal("101.00")),
                new EquityPoint(LocalDate.of(2024, 1, 3), cash, 99, costBasis, BigDecimal.ZERO,
                        new BigDecimal("102.00")),
                new EquityPoint(END_DATE, cash, 99, costBasis, BigDecimal.ZERO, new BigDecimal("98.50")));
        return new BuyAndHoldBenchmark(capital, curve);
    }

    /** Persists the full scenario and returns the generated run id plus the owner. */
    private record Persisted(long runId, BacktestFixtures.Inputs inputs) {
    }

    private Persisted persistScenario(String label) {
        BacktestFixtures.Inputs inputs =
                BacktestFixtures.createInputs(jdbcTemplate, strategyService, datasetService, label);

        BacktestRun run = new BacktestRun(inputs.owner().value(), inputs.strategyId(), inputs.strategyVersionNumber(),
                inputs.strategyDefinitionHash(), inputs.datasetId(), inputs.datasetVersionNumber(),
                inputs.datasetContentHash(), Backtester.SEMANTICS_VERSION, result(), metrics(), benchmark());
        BacktestRun saved = backtestRunRepository.saveAndFlush(run);
        long runId = saved.id();

        equityPointRepository.insertAll(runId, equityCurve().stream().map(BacktestEquityPointRow::of).toList());
        fillRepository.insertAll(runId, List.of(BacktestFillRow.of(sampleFill())));

        List<OrderRejection> rejections = rejections();
        rejectionRepository.insertAll(runId, List.of(
                BacktestRejectionRow.of(1, rejections.get(0)),
                BacktestRejectionRow.of(2, rejections.get(1))));

        return new Persisted(runId, inputs);
    }

    // --- E: numeric (BigDecimal) fidelity --------------------------------------

    @Test
    void bigDecimalValuesPreserveExactScaleThroughJpa() {
        Persisted persisted = persistScenario("numeric-jpa");

        BacktestRun fetched = backtestRunRepository.findByIdAndOwnerId(persisted.runId(),
                persisted.inputs().owner().value()).orElseThrow();

        assertEquals(new BigDecimal("10000.01"), fetched.initialCapital());
        assertEquals(2, fetched.initialCapital().scale());
        assertEquals(new BigDecimal("1.25"), fetched.commissionPerFill());
        assertEquals(2, fetched.commissionPerFill().scale());
        assertEquals(new BigDecimal("0.0015"), fetched.slippageRate());
        assertEquals(4, fetched.slippageRate().scale());
    }

    @Test
    void bigDecimalValuesPreserveExactScaleThroughJdbc() {
        Persisted persisted = persistScenario("numeric-jdbc");

        List<BacktestEquityPointRow> points =
                equityPointRepository.findOwned(persisted.runId(), persisted.inputs().owner().value());
        BacktestEquityPointRow first = points.get(0);

        assertEquals(new BigDecimal("9000.00"), first.cash());
        assertEquals(2, first.cash().scale());
        assertEquals(new BigDecimal("1000.01"), first.costBasis());
        assertEquals(2, first.costBasis().scale());

        List<BacktestFillRow> fills = fillRepository.findOwned(persisted.runId(), persisted.inputs().owner().value());
        BacktestFillRow fill = fills.get(0);
        assertEquals(new BigDecimal("1.25"), fill.commission());
        assertEquals(2, fill.commission().scale());
    }

    // --- F: float bit fidelity --------------------------------------------------

    @Test
    void metricDoublesRoundTripBitExactly() {
        Persisted persisted = persistScenario("float-fidelity");

        BacktestRun fetched = backtestRunRepository.findByIdAndOwnerId(persisted.runId(),
                persisted.inputs().owner().value()).orElseThrow();

        assertEquals(Double.doubleToRawLongBits(TRICKY_TOTAL_RETURN), Double.doubleToRawLongBits(fetched.totalReturn()));
        assertEquals(Double.doubleToRawLongBits(TRICKY_SHARPE_RATIO),
                Double.doubleToRawLongBits(fetched.sharpeRatio().orElseThrow()));
        assertTrue(fetched.cagr().isEmpty());
    }

    // --- G: engine semantics ----------------------------------------------------

    @Test
    void persistedEngineSemanticsVersionIsAccepted() {
        assertEquals(1, Backtester.SEMANTICS_VERSION);

        Persisted persisted = persistScenario("semantics-version");
        BacktestRun fetched = backtestRunRepository.findByIdAndOwnerId(persisted.runId(),
                persisted.inputs().owner().value()).orElseThrow();

        assertEquals(Backtester.SEMANTICS_VERSION, fetched.engineSemanticsVersion());
    }

    // --- H: signal snapshot JSON round trip --------------------------------------

    @Test
    void indicatorSnapshotJsonRoundTripsExactly() {
        Persisted persisted = persistScenario("signal-snapshot");

        List<Map<String, Object>> entries = jdbcTemplate.queryForList("""
                select t.elem ->> 'type' as type, (t.elem ->> 'period')::int as period, t.elem ->> 'value' as value
                from backtest_fill f, jsonb_array_elements(f.signal_indicators) with ordinality as t(elem, ord)
                where f.run_id = ? and f.order_id = ?
                order by t.ord
                """, persisted.runId(), 1);

        assertEquals(4, entries.size());

        assertEquals("SMA", entries.get(0).get("type"));
        assertEquals(5, entries.get(0).get("period"));
        assertEquals(Double.toString(100.0), entries.get(0).get("value"));

        assertEquals("SMA", entries.get(1).get("type"));
        assertEquals(20, entries.get(1).get("period"));
        assertEquals(Double.toString(Double.MIN_VALUE), entries.get(1).get("value"));

        assertEquals("EMA", entries.get(2).get("type"));
        assertEquals(12, entries.get(2).get("period"));
        assertEquals(Double.toString(1e-300), entries.get(2).get("value"));

        assertEquals("RSI", entries.get(3).get("type"));
        assertEquals(14, entries.get(3).get("period"));
        assertEquals(Double.toString(0.1 + 0.2), entries.get(3).get("value"));
    }

    // --- I: valid parent/child insert + ownership isolation ----------------------

    @Test
    void aFullyValidScenarioPersistsAndReadsBackConsistently() {
        Persisted persisted = persistScenario("valid-scenario");
        long ownerId = persisted.inputs().owner().value();

        assertTrue(backtestRunRepository.findByIdAndOwnerId(persisted.runId(), ownerId).isPresent());
        assertEquals(3, equityPointRepository.findOwned(persisted.runId(), ownerId).size());
        assertEquals(1, fillRepository.findOwned(persisted.runId(), ownerId).size());
        assertEquals(2, rejectionRepository.findOwned(persisted.runId(), ownerId).size());
    }

    @Test
    void anotherOwnerCannotReadASomeoneElsesRunThroughOwnerScopedLookup() {
        Persisted persisted = persistScenario("ownership-isolation");
        long otherOwnerId = BacktestFixtures.createInputs(jdbcTemplate, strategyService, datasetService,
                "ownership-isolation-other").owner().value();

        assertTrue(backtestRunRepository.findByIdAndOwnerId(persisted.runId(), otherOwnerId).isEmpty());
        assertTrue(equityPointRepository.findOwned(persisted.runId(), otherOwnerId).isEmpty());
        assertTrue(fillRepository.findOwned(persisted.runId(), otherOwnerId).isEmpty());
        assertTrue(rejectionRepository.findOwned(persisted.runId(), otherOwnerId).isEmpty());
    }
}
