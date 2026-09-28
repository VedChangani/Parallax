package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.backend.dataset.AdjustmentBasis;
import in.vedchangani.parallax.backend.dataset.DatasetFixtures;
import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.dataset.DatasetVersionSummary;
import in.vedchangani.parallax.backend.strategy.StrategyService;
import in.vedchangani.parallax.backend.strategy.StrategySummary;
import in.vedchangani.parallax.backend.strategy.StrategyVersionDetail;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Test-only fixtures shared by the D-34 Batch 1 backtest-persistence test
 * suite (mirroring {@code strategy.StrategyFixtures}/{@code TestUsers} and
 * {@code dataset.DatasetFixtures}): a tiny deterministic strategy
 * definition, a helper that creates an owned strategy version 1 and
 * dataset version 1 to satisfy {@code backtest_run}'s composite foreign
 * keys, and a sample {@link IndicatorSnapshot}.
 */
final class BacktestFixtures {

    private static final AtomicLong COUNTER = new AtomicLong();

    private BacktestFixtures() {
    }

    static String uniqueName(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + COUNTER.incrementAndGet();
    }

    static StrategyDefinition simpleStrategyDefinition() {
        return new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(0)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    /**
     * Identity of one owned, persisted {@code strategy_version} and
     * {@code dataset_version} pair — the minimum {@code backtest_run}
     * needs to satisfy its composite foreign keys.
     */
    record Inputs(UserId owner, long strategyId, int strategyVersionNumber, String strategyDefinitionHash,
                   long datasetId, int datasetVersionNumber, String datasetContentHash) {
    }

    static Inputs createInputs(JdbcTemplate jdbcTemplate, StrategyService strategyService,
                                DatasetService datasetService, String label) {
        UserId owner = TestUsers.create(jdbcTemplate, label);

        StrategySummary strategy = strategyService.createStrategy(owner, uniqueName(label + "-strategy"), "d",
                simpleStrategyDefinition());
        StrategyVersionDetail strategyVersion = strategyService.getVersion(owner, strategy.id(), 1);

        long datasetId = datasetService.createDataset(owner, uniqueName(label + "-dataset"), "AAPL").id();
        DatasetVersionSummary datasetVersion = datasetService.createVersionFromCsv(owner, datasetId,
                DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW, "a.csv");

        return new Inputs(owner, strategy.id(), strategyVersion.summary().versionNumber(),
                strategyVersion.summary().definitionHash(), datasetId, datasetVersion.versionNumber(),
                datasetVersion.contentHash());
    }

    /**
     * A snapshot exercising several representative {@code Double.toString}
     * shapes (D-34 Batch 1 §13.H): {@link Double#MIN_VALUE} (subnormal),
     * {@code 1e-300}, {@code 0.1 + 0.2} (a value with no exact decimal
     * representation), and a round value.
     */
    static IndicatorSnapshot sampleSnapshot(LocalDate date, BigDecimal close) {
        Map<IndicatorSpec, Double> values = new LinkedHashMap<>();
        values.put(new IndicatorSpec(IndicatorType.SMA, 5), 100.0);
        values.put(new IndicatorSpec(IndicatorType.SMA, 20), Double.MIN_VALUE);
        values.put(new IndicatorSpec(IndicatorType.EMA, 12), 1e-300);
        values.put(new IndicatorSpec(IndicatorType.RSI, 14), 0.1 + 0.2);
        return new IndicatorSnapshot(date, close, values);
    }

    // --- Phase 9 Batch 2c: clone-and-tamper tamper-detection infrastructure ----
    //
    // Fabricating every backtest_run/child-row field by hand (as the pre-Batch-2c
    // "golden baseline" fixture did) is no longer self-consistent once getRun
    // fully recomputes/replays a stored result: a hand-picked benchmark return,
    // metric, or ledger state has no reason to agree with what the engine would
    // actually derive from the same fills/config. These helpers instead clone a
    // REAL, engine-produced backtest_run row (and its equity/fill/rejection
    // children) - obtained by actually calling BacktestRunService.createRun -
    // into a NEW row, with exactly one field overridden on the clone. Every
    // source table rejects UPDATE (D-34 immutability triggers), so tampering
    // is always done by inserting a new, independent row - the original run
    // (and any other test's rows) is never touched.

    /**
     * Clones {@code sourceRunId}'s {@code backtest_run} parent row into a new
     * row, applying {@code runColumnOverrides} to the clone only, and returns
     * the new row's id. Does not clone child rows - see {@link
     * #cloneChildRows}.
     */
    static long cloneRunRow(JdbcTemplate jdbcTemplate, long sourceRunId, Map<String, Object> runColumnOverrides) {
        Map<String, Object> row = new LinkedHashMap<>(
                jdbcTemplate.queryForMap("select * from backtest_run where id = ?", sourceRunId));
        row.remove("id");
        row.remove("created_at");
        row.putAll(runColumnOverrides);

        String columns = String.join(", ", row.keySet());
        String placeholders = row.keySet().stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = "insert into backtest_run (" + columns + ") values (" + placeholders + ") returning id";
        return jdbcTemplate.queryForObject(sql, Long.class, row.values().toArray());
    }

    /** Clones every equity point from {@code sourceRunId} to {@code targetRunId}, unchanged. */
    static void cloneEquityPoints(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId) {
        jdbcTemplate.update("""
                insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, close)
                select ?, bar_date, cash, quantity, cost_basis, realized_pnl, close
                from backtest_equity_point where run_id = ? order by bar_date
                """, targetRunId, sourceRunId);
    }

    /**
     * Clones every equity point from {@code sourceRunId} to {@code targetRunId}
     * <strong>except</strong> the one dated {@code tamperedDate}, which is
     * inserted with {@code cash} overridden to {@code tamperedCash} instead.
     */
    static void cloneEquityPointsWithCashOverride(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId,
                                                    LocalDate tamperedDate, BigDecimal tamperedCash) {
        jdbcTemplate.update("""
                insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, close)
                select ?, bar_date, cash, quantity, cost_basis, realized_pnl, close
                from backtest_equity_point where run_id = ? and bar_date <> ?
                """, targetRunId, sourceRunId, tamperedDate);
        jdbcTemplate.update("""
                insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, close)
                select ?, bar_date, ?, quantity, cost_basis, realized_pnl, close
                from backtest_equity_point where run_id = ? and bar_date = ?
                """, targetRunId, tamperedCash, sourceRunId, tamperedDate);
    }

    /** Clones every fill from {@code sourceRunId} to {@code targetRunId}, unchanged. */
    static void cloneFills(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId) {
        jdbcTemplate.update("""
                insert into backtest_fill (run_id, order_id, fill_date, quantity, reference_open, fill_price,
                    commission, signal_type, signal_date, signal_close, signal_indicators)
                select ?, order_id, fill_date, quantity, reference_open, fill_price, commission, signal_type,
                    signal_date, signal_close, signal_indicators
                from backtest_fill where run_id = ? order by order_id
                """, targetRunId, sourceRunId);
    }

    /**
     * Clones every fill from {@code sourceRunId} to {@code targetRunId}
     * <strong>except</strong> the one with {@code tamperedOrderId}, which is
     * inserted with {@code orderId}/{@code fillDate}/{@code fillPrice}/{@code
     * commission} overridden where a non-null override is supplied (a
     * {@code null} override keeps that column's original value).
     */
    static void cloneFillsWithOverride(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId,
                                        int tamperedOrderId, Integer orderIdOverride, LocalDate fillDateOverride,
                                        BigDecimal fillPriceOverride, BigDecimal commissionOverride) {
        jdbcTemplate.update("""
                insert into backtest_fill (run_id, order_id, fill_date, quantity, reference_open, fill_price,
                    commission, signal_type, signal_date, signal_close, signal_indicators)
                select ?, order_id, fill_date, quantity, reference_open, fill_price, commission, signal_type,
                    signal_date, signal_close, signal_indicators
                from backtest_fill where run_id = ? and order_id <> ?
                """, targetRunId, sourceRunId, tamperedOrderId);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select * from backtest_fill where run_id = ? and order_id = ?", sourceRunId, tamperedOrderId);
        jdbcTemplate.update("""
                insert into backtest_fill (run_id, order_id, fill_date, quantity, reference_open, fill_price,
                    commission, signal_type, signal_date, signal_close, signal_indicators)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, targetRunId, orderIdOverride != null ? orderIdOverride : row.get("order_id"),
                fillDateOverride != null ? fillDateOverride : row.get("fill_date"), row.get("quantity"),
                row.get("reference_open"), fillPriceOverride != null ? fillPriceOverride : row.get("fill_price"),
                commissionOverride != null ? commissionOverride : row.get("commission"), row.get("signal_type"),
                row.get("signal_date"), row.get("signal_close"), row.get("signal_indicators").toString());
    }

    /** Clones every rejection from {@code sourceRunId} to {@code targetRunId}, unchanged. */
    static void cloneRejections(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId) {
        jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, order_id, execution_date, quantity,
                    required_cash, available_cash, signal_date, signal_close, signal_indicators)
                select ?, seq, reason, order_id, execution_date, quantity, required_cash, available_cash,
                    signal_date, signal_close, signal_indicators
                from backtest_rejection where run_id = ? order by seq
                """, targetRunId, sourceRunId);
    }

    /** Clones every equity/fill/rejection child row from {@code sourceRunId} to {@code targetRunId}, unchanged. */
    static void cloneChildRows(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId) {
        cloneEquityPoints(jdbcTemplate, targetRunId, sourceRunId);
        cloneFills(jdbcTemplate, targetRunId, sourceRunId);
        cloneRejections(jdbcTemplate, targetRunId, sourceRunId);
    }

    // --- Phase 10 Batch 1: causal-verification tamper infrastructure -----------
    //
    // These generalize the Phase 9 Batch 2c clone-and-tamper helpers above to
    // the fill/rejection SIGNAL-side columns (signal_date, signal_close,
    // signal_indicators) and to fabricating a rejection that never existed in
    // any real run at all - needed to exercise position-state and
    // condition-truth checks that have no equivalent in the pre-existing
    // suite.

    /**
     * Clones every fill from {@code sourceRunId} to {@code targetRunId}
     * <strong>except</strong> the one with {@code tamperedOrderId}, whose
     * clone has every column named in {@code columnOverrides} (keyed by the
     * exact {@code backtest_fill} column name, e.g. {@code "signal_date"})
     * replaced — every other column keeps its original value. More general
     * than {@link #cloneFillsWithOverride}, which only covers
     * orderId/fillDate/fillPrice/commission.
     */
    static void cloneFillsWithColumnOverrides(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId,
                                               int tamperedOrderId, Map<String, Object> columnOverrides) {
        jdbcTemplate.update("""
                insert into backtest_fill (run_id, order_id, fill_date, quantity, reference_open, fill_price,
                    commission, signal_type, signal_date, signal_close, signal_indicators)
                select ?, order_id, fill_date, quantity, reference_open, fill_price, commission, signal_type,
                    signal_date, signal_close, signal_indicators
                from backtest_fill where run_id = ? and order_id <> ?
                """, targetRunId, sourceRunId, tamperedOrderId);

        Map<String, Object> row = new LinkedHashMap<>(jdbcTemplate.queryForMap(
                "select * from backtest_fill where run_id = ? and order_id = ?", sourceRunId, tamperedOrderId));
        row.putAll(columnOverrides);

        jdbcTemplate.update("""
                insert into backtest_fill (run_id, order_id, fill_date, quantity, reference_open, fill_price,
                    commission, signal_type, signal_date, signal_close, signal_indicators)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, targetRunId, row.get("order_id"), row.get("fill_date"), row.get("quantity"),
                row.get("reference_open"), row.get("fill_price"), row.get("commission"), row.get("signal_type"),
                row.get("signal_date"), row.get("signal_close"), row.get("signal_indicators").toString());
    }

    /**
     * Inserts a brand-new, fabricated {@code ZERO_QUANTITY} rejection row —
     * not cloned from any source run — for tests that need a stored signal
     * the real engine never actually produced (a position-state or
     * condition-truth violation with no equivalent among real fills).
     * {@code signalIndicatorsJson} is the raw JSON array text {@link
     * IndicatorSnapshotJson#write} would produce, e.g. {@code "[]"} for a
     * strategy with no required indicators.
     */
    static void insertZeroQuantityRejection(JdbcTemplate jdbcTemplate, long runId, int seq, LocalDate signalDate,
                                             BigDecimal signalClose, String signalIndicatorsJson) {
        jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, order_id, execution_date, quantity,
                    required_cash, available_cash, signal_date, signal_close, signal_indicators)
                values (?, ?, 'ZERO_QUANTITY', null, null, null, null, null, ?, ?, ?::jsonb)
                """, runId, seq, signalDate, signalClose, signalIndicatorsJson);
    }

    /**
     * Clones every rejection from {@code sourceRunId} to {@code targetRunId}
     * <strong>except</strong> the one with {@code tamperedSeq}, whose clone
     * has every column named in {@code columnOverrides} (keyed by the exact
     * {@code backtest_rejection} column name, e.g. {@code "available_cash"})
     * replaced — every other column keeps its original value.
     */
    static void cloneRejectionsWithColumnOverride(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId,
                                                   int tamperedSeq, Map<String, Object> columnOverrides) {
        jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, order_id, execution_date, quantity,
                    required_cash, available_cash, signal_date, signal_close, signal_indicators)
                select ?, seq, reason, order_id, execution_date, quantity, required_cash, available_cash,
                    signal_date, signal_close, signal_indicators
                from backtest_rejection where run_id = ? and seq <> ?
                """, targetRunId, sourceRunId, tamperedSeq);

        Map<String, Object> row = new LinkedHashMap<>(jdbcTemplate.queryForMap(
                "select * from backtest_rejection where run_id = ? and seq = ?", sourceRunId, tamperedSeq));
        row.putAll(columnOverrides);

        jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, order_id, execution_date, quantity,
                    required_cash, available_cash, signal_date, signal_close, signal_indicators)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, targetRunId, row.get("seq"), row.get("reason"), row.get("order_id"), row.get("execution_date"),
                row.get("quantity"), row.get("required_cash"), row.get("available_cash"), row.get("signal_date"),
                row.get("signal_close"), row.get("signal_indicators").toString());
    }
}
