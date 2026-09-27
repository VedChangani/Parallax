package in.vedchangani.parallax.backend;

import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-29 proof that the backend can actually reach the {@code engine} Maven
 * dependency and its Spring-managed {@link Backtester} bean: a tiny,
 * deterministic, in-memory run with no persistence and no HTTP involved.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EngineSmokeTest {

    @Autowired
    private Backtester backtester;

    @Test
    void backendCanRunTheEngineBacktesterBean() {
        assertNotNull(backtester);

        BarSeries series = new BarSeries("AAPL", List.of(
                new Bar(LocalDate.of(2024, 1, 1), new BigDecimal("100"), new BigDecimal("100"),
                        new BigDecimal("100"), new BigDecimal("100"), 0),
                new Bar(LocalDate.of(2024, 1, 2), new BigDecimal("100"), new BigDecimal("100"),
                        new BigDecimal("100"), new BigDecimal("100"), 0)));

        StrategyDefinition strategy = new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(0)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));

        BacktestConfig config = new BacktestConfig(new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));

        BacktestResult result = backtester.run(series, strategy, config);

        assertNotNull(result);
        assertTrue(result.equityCurve().size() >= 1);
        assertTrue(result.equityCurve().get(0).equity().compareTo(new BigDecimal("1000")) == 0);
    }
}
