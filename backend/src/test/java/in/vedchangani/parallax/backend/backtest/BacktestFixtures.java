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

    static IndicatorSnapshot sampleSnapshot(LocalDate date, BigDecimal close) {
        Map<IndicatorSpec, Double> values = new LinkedHashMap<>();
        values.put(new IndicatorSpec(IndicatorType.SMA, 5), 100.0);
        values.put(new IndicatorSpec(IndicatorType.SMA, 20), Double.MIN_VALUE);
        values.put(new IndicatorSpec(IndicatorType.EMA, 12), 1e-300);
        values.put(new IndicatorSpec(IndicatorType.RSI, 14), 0.1 + 0.2);
        return new IndicatorSnapshot(date, close, values);
    }

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

    static void cloneEquityPoints(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId) {
        jdbcTemplate.update("""
                insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, close)
                select ?, bar_date, cash, quantity, cost_basis, realized_pnl, close
                from backtest_equity_point where run_id = ? order by bar_date
                """, targetRunId, sourceRunId);
    }

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

    static void cloneFills(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId) {
        jdbcTemplate.update("""
                insert into backtest_fill (run_id, order_id, fill_date, quantity, reference_open, fill_price,
                    commission, signal_type, signal_date, signal_close, signal_indicators)
                select ?, order_id, fill_date, quantity, reference_open, fill_price, commission, signal_type,
                    signal_date, signal_close, signal_indicators
                from backtest_fill where run_id = ? order by order_id
                """, targetRunId, sourceRunId);
    }

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

    static void cloneRejections(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId) {
        jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, order_id, execution_date, quantity,
                    required_cash, available_cash, signal_date, signal_close, signal_indicators)
                select ?, seq, reason, order_id, execution_date, quantity, required_cash, available_cash,
                    signal_date, signal_close, signal_indicators
                from backtest_rejection where run_id = ? order by seq
                """, targetRunId, sourceRunId);
    }

    static void cloneChildRows(JdbcTemplate jdbcTemplate, long targetRunId, long sourceRunId) {
        cloneEquityPoints(jdbcTemplate, targetRunId, sourceRunId);
        cloneFills(jdbcTemplate, targetRunId, sourceRunId);
        cloneRejections(jdbcTemplate, targetRunId, sourceRunId);
    }

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

    static void insertZeroQuantityRejection(JdbcTemplate jdbcTemplate, long runId, int seq, LocalDate signalDate,
                                             BigDecimal signalClose, String signalIndicatorsJson) {
        jdbcTemplate.update("""
                insert into backtest_rejection (run_id, seq, reason, order_id, execution_date, quantity,
                    required_cash, available_cash, signal_date, signal_close, signal_indicators)
                values (?, ?, 'ZERO_QUANTITY', null, null, null, null, null, ?, ?, ?::jsonb)
                """, runId, seq, signalDate, signalClose, signalIndicatorsJson);
    }

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
