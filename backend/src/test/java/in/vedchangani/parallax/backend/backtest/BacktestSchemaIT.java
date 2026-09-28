package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.strategy.StrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D-34 Batch 1 schema-level proof, against real PostgreSQL (Testcontainers):
 * the database-layer immutability triggers on the four new tables, every
 * CHECK constraint, and the composite foreign keys tying a run's strategy
 * and dataset identity to their immutable versions and owners. Mirrors
 * D-31/D-32's own {@code StrategySchemaIT}/{@code DatasetSchemaIT}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BacktestSchemaIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private DatasetService datasetService;

    private BacktestFixtures.Inputs inputs(String label) {
        return BacktestFixtures.createInputs(jdbcTemplate, strategyService, datasetService, label);
    }

    // --- valid row + insert helper -----------------------------------------

    private Map<String, Object> defaultValues(BacktestFixtures.Inputs in) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("owner_id", in.owner().value());
        values.put("strategy_id", in.strategyId());
        values.put("strategy_version_number", in.strategyVersionNumber());
        values.put("strategy_definition_hash", in.strategyDefinitionHash());
        values.put("dataset_id", in.datasetId());
        values.put("dataset_version_number", in.datasetVersionNumber());
        values.put("dataset_content_hash", in.datasetContentHash());
        values.put("engine_semantics_version", 1);
        values.put("initial_capital", new BigDecimal("10000"));
        values.put("commission_per_fill", new BigDecimal("1"));
        values.put("slippage_rate", new BigDecimal("0.001"));
        values.put("start_date", LocalDate.of(2024, 1, 2));
        values.put("end_date", LocalDate.of(2024, 1, 3));
        values.put("first_evaluable_date", null);
        values.put("total_commission", new BigDecimal("2"));
        values.put("total_slippage_cost", new BigDecimal("0.5"));
        values.put("total_return", 0.05);
        values.put("cagr", null);
        values.put("volatility", null);
        values.put("sharpe_ratio", null);
        values.put("max_drawdown", 0.1);
        values.put("closed_trade_count", 0);
        values.put("win_rate", null);
        values.put("average_win", null);
        values.put("average_loss", null);
        values.put("benchmark_cash", new BigDecimal("100"));
        values.put("benchmark_quantity", 10L);
        values.put("benchmark_cost_basis", new BigDecimal("9900"));
        values.put("benchmark_total_return", 0.02);
        return values;
    }

    private long insertRun(BacktestFixtures.Inputs in, Map<String, Object> overrides) {
        Map<String, Object> values = defaultValues(in);
        values.putAll(overrides);
        String columns = String.join(", ", values.keySet());
        String placeholders = values.keySet().stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = "insert into backtest_run (" + columns + ") values (" + placeholders + ") returning id";
        return jdbcTemplate.queryForObject(sql, Long.class, values.values().toArray());
    }

    private void assertRejected(BacktestFixtures.Inputs in, Map<String, Object> overrides) {
        assertThrows(DataAccessException.class, () -> insertRun(in, overrides));
    }

    // --- immutability triggers ----------------------------------------------

    @Test
    void updatingABacktestRunRowIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update backtest_run set closed_trade_count = 999 where id = ?", runId));
    }

    @Test
    void deletingABacktestRunRowIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("delete from backtest_run where id = ?", runId));
    }

    @Test
    void truncatingBacktestRunIsRejectedByTheDatabase() {
        insertRun(inputs("schema"), Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.execute("truncate table backtest_run"));
    }

    @Test
    void updatingABacktestEquityPointRowIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        jdbcTemplate.update(
                "insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, "
                        + "close) values (?, '2024-01-02', 10000, 0, 0, 0, 100)", runId);
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update backtest_equity_point set quantity = 999 where run_id = ?", runId));
    }

    @Test
    void deletingABacktestEquityPointRowIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        jdbcTemplate.update(
                "insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, "
                        + "close) values (?, '2024-01-02', 10000, 0, 0, 0, 100)", runId);
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("delete from backtest_equity_point where run_id = ?", runId));
    }

    @Test
    void truncatingBacktestEquityPointIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        jdbcTemplate.update(
                "insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, "
                        + "close) values (?, '2024-01-02', 10000, 0, 0, 0, 100)", runId);
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.execute("truncate table backtest_equity_point"));
    }

    @Test
    void updatingABacktestFillRowIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        insertSampleFill(runId);
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("update backtest_fill set quantity = 999 where run_id = ?", runId));
    }

    @Test
    void deletingABacktestFillRowIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        insertSampleFill(runId);
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("delete from backtest_fill where run_id = ?", runId));
    }

    @Test
    void truncatingBacktestFillIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        insertSampleFill(runId);
        assertThrows(DataAccessException.class, () -> jdbcTemplate.execute("truncate table backtest_fill"));
    }

    @Test
    void updatingABacktestRejectionRowIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        insertSampleZeroQuantityRejection(runId);
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("update backtest_rejection set seq = 999 where run_id = ?", runId));
    }

    @Test
    void deletingABacktestRejectionRowIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        insertSampleZeroQuantityRejection(runId);
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("delete from backtest_rejection where run_id = ?", runId));
    }

    @Test
    void truncatingBacktestRejectionIsRejectedByTheDatabase() {
        long runId = insertRun(inputs("schema"), Map.of());
        insertSampleZeroQuantityRejection(runId);
        assertThrows(DataAccessException.class, () -> jdbcTemplate.execute("truncate table backtest_rejection"));
    }

    // --- CHECK constraints: backtest_run --------------------------------------

    @Test
    void hashFormatCheckRejectsANonHexStrategyHash() {
        assertRejected(inputs("schema"), Map.of("strategy_definition_hash", "not-a-valid-hash"));
    }

    @Test
    void hashFormatCheckRejectsANonHexDatasetHash() {
        assertRejected(inputs("schema"), Map.of("dataset_content_hash", "not-a-valid-hash"));
    }

    @Test
    void dateRangeCheckRejectsStartDateAfterEndDate() {
        assertRejected(inputs("schema"),
                Map.of("start_date", LocalDate.of(2024, 1, 5), "end_date", LocalDate.of(2024, 1, 2)));
    }

    @Test
    void semanticsVersionCheckRejectsZero() {
        assertRejected(inputs("schema"), Map.of("engine_semantics_version", 0));
    }

    @Test
    void closedTradeCountCheckRejectsNegative() {
        assertRejected(inputs("schema"), Map.of("closed_trade_count", -1));
    }

    @Test
    void benchmarkQuantityCheckRejectsNegative() {
        assertRejected(inputs("schema"), Map.of("benchmark_quantity", -1L));
    }

    @Test
    void totalReturnMustBeFinite_rejectsNaN() {
        assertRejected(inputs("schema"), Map.of("total_return", Double.NaN));
    }

    @Test
    void totalReturnMustBeFinite_rejectsInfinity() {
        assertRejected(inputs("schema"), Map.of("total_return", Double.POSITIVE_INFINITY));
    }

    @Test
    void cagrMustBeFiniteWhenPresent_rejectsInfinity() {
        assertRejected(inputs("schema"), Map.of("cagr", Double.NEGATIVE_INFINITY));
    }

    @Test
    void sharpeRatioMustBeFiniteWhenPresent_rejectsNaN() {
        assertRejected(inputs("schema"), Map.of("sharpe_ratio", Double.NaN));
    }

    // --- CHECK constraints: backtest_fill / backtest_rejection ----------------

    @Test
    void fillSignalTypeCheckRejectsAnUnknownValue() {
        long runId = insertRun(inputs("schema"), Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into backtest_fill
                    (run_id, order_id, fill_date, quantity, reference_open, fill_price, commission,
                     signal_type, signal_date, signal_close, signal_indicators)
                values (?, 1, '2024-01-03', 10, 100, 100.1, 1, 'HOLD', '2024-01-02', 100, '[]'::jsonb)
                """, runId));
    }

    @Test
    void fillSignalIndicatorsCheckRejectsANonArrayValue() {
        long runId = insertRun(inputs("schema"), Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into backtest_fill
                    (run_id, order_id, fill_date, quantity, reference_open, fill_price, commission,
                     signal_type, signal_date, signal_close, signal_indicators)
                values (?, 1, '2024-01-03', 10, 100, 100.1, 1, 'ENTER', '2024-01-02', 100, '{}'::jsonb)
                """, runId));
    }

    @Test
    void rejectionReasonCheckRejectsAnUnknownValue() {
        long runId = insertRun(inputs("schema"), Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, signal_date, signal_close, signal_indicators)
                values (?, 1, 'UNKNOWN_REASON', '2024-01-02', 100, '[]'::jsonb)
                """, runId));
    }

    @Test
    void rejectionShapeCheckRejectsZeroQuantityWithAnOrderId() {
        long runId = insertRun(inputs("schema"), Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into backtest_rejection
                    (run_id, seq, reason, order_id, signal_date, signal_close, signal_indicators)
                values (?, 1, 'ZERO_QUANTITY', 1, '2024-01-02', 100, '[]'::jsonb)
                """, runId));
    }

    @Test
    void rejectionShapeCheckRejectsInsufficientCashMissingOrderFields() {
        long runId = insertRun(inputs("schema"), Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, signal_date, signal_close, signal_indicators)
                values (?, 1, 'INSUFFICIENT_CASH', '2024-01-02', 100, '[]'::jsonb)
                """, runId));
    }

    @Test
    void rejectionSignalIndicatorsCheckRejectsANonArrayValue() {
        long runId = insertRun(inputs("schema"), Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, signal_date, signal_close, signal_indicators)
                values (?, 1, 'ZERO_QUANTITY', '2024-01-02', 100, '{}'::jsonb)
                """, runId));
    }

    // --- composite foreign keys -----------------------------------------------

    @Test
    void mismatchedStrategyHashIsRejected() {
        assertRejected(inputs("schema"), Map.of("strategy_definition_hash", "0".repeat(64)));
    }

    @Test
    void mismatchedDatasetHashIsRejected() {
        assertRejected(inputs("schema"), Map.of("dataset_content_hash", "0".repeat(64)));
    }

    @Test
    void wrongOwnerStrategyIsRejected() {
        BacktestFixtures.Inputs a = inputs("schema-a");
        BacktestFixtures.Inputs b = inputs("schema-b");
        // b's owner, but a's strategy identity: fk_backtest_run_strategy (strategy_id, owner_id) fails.
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("owner_id", b.owner().value());
        overrides.put("dataset_id", b.datasetId());
        overrides.put("dataset_version_number", b.datasetVersionNumber());
        overrides.put("dataset_content_hash", b.datasetContentHash());
        assertRejected(a, overrides);
    }

    @Test
    void wrongOwnerDatasetIsRejected() {
        BacktestFixtures.Inputs a = inputs("schema-a");
        BacktestFixtures.Inputs b = inputs("schema-b");
        // b's owner, but a's dataset identity: fk_backtest_run_dataset (dataset_id, owner_id) fails.
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("owner_id", b.owner().value());
        overrides.put("strategy_id", b.strategyId());
        overrides.put("strategy_version_number", b.strategyVersionNumber());
        overrides.put("strategy_definition_hash", b.strategyDefinitionHash());
        assertRejected(a, overrides);
    }

    @Test
    void deletingAReferencedStrategyVersionIsRejected() {
        BacktestFixtures.Inputs in = inputs("schema");
        insertRun(in, Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "delete from strategy_version where strategy_id = ? and version_number = ?",
                in.strategyId(), in.strategyVersionNumber()));
    }

    @Test
    void deletingAReferencedDatasetVersionIsRejected() {
        BacktestFixtures.Inputs in = inputs("schema");
        insertRun(in, Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "delete from dataset_version where dataset_id = ? and version_number = ?",
                in.datasetId(), in.datasetVersionNumber()));
    }

    /**
     * Phase 9 Batch 2c §14: a {@code strategy_version} row a real
     * {@code backtest_run} references is rejected on UPDATE by the same
     * D-31 immutability trigger every {@code strategy_version} row already
     * carries (unconditionally, referenced or not) - proving the specific
     * invariant this batch's read-time verification depends on: a
     * <em>referenced</em> version cannot silently drift out from under an
     * existing run.
     */
    @Test
    void updatingAReferencedStrategyVersionIsRejectedByTheDatabase() {
        BacktestFixtures.Inputs in = inputs("schema");
        insertRun(in, Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update strategy_version set definition_hash = ? where strategy_id = ? and version_number = ?",
                "0".repeat(64), in.strategyId(), in.strategyVersionNumber()));
    }

    @Test
    void updatingAReferencedDatasetVersionIsRejectedByTheDatabase() {
        BacktestFixtures.Inputs in = inputs("schema");
        insertRun(in, Map.of());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update dataset_version set content_hash = ? where dataset_id = ? and version_number = ?",
                "0".repeat(64), in.datasetId(), in.datasetVersionNumber()));
    }

    // --- FK integrity: child rows require an existing parent run --------------

    @Test
    void anEquityPointCannotExistWithoutItsParentRun() {
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, "
                        + "close) values (-1, '2024-01-02', 10000, 0, 0, 0, 100)"));
    }

    @Test
    void aFillCannotExistWithoutItsParentRun() {
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into backtest_fill
                    (run_id, order_id, fill_date, quantity, reference_open, fill_price, commission,
                     signal_type, signal_date, signal_close, signal_indicators)
                values (-1, 1, '2024-01-03', 10, 100, 100.1, 1, 'ENTER', '2024-01-02', 100, '[]'::jsonb)
                """));
    }

    @Test
    void aRejectionCannotExistWithoutItsParentRun() {
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, signal_date, signal_close, signal_indicators)
                values (-1, 1, 'ZERO_QUANTITY', '2024-01-02', 100, '[]'::jsonb)
                """));
    }

    // --- shared helpers --------------------------------------------------------

    private void insertSampleFill(long runId) {
        jdbcTemplate.update("""
                insert into backtest_fill
                    (run_id, order_id, fill_date, quantity, reference_open, fill_price, commission,
                     signal_type, signal_date, signal_close, signal_indicators)
                values (?, 1, '2024-01-03', 10, 100, 100.1, 1, 'ENTER', '2024-01-02', 100, '[]'::jsonb)
                """, runId);
    }

    private void insertSampleZeroQuantityRejection(long runId) {
        jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, signal_date, signal_close, signal_indicators)
                values (?, 1, 'ZERO_QUANTITY', '2024-01-02', 100, '[]'::jsonb)
                """, runId);
    }
}
