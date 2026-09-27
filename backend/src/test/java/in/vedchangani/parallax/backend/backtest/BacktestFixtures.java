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
}
