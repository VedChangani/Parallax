package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacktestRangeValidatorTest {

    private static BarSeries weekdaySeries() {
        return new BarSeries("AAPL", List.of(
                bar(LocalDate.of(2024, 1, 2)),
                bar(LocalDate.of(2024, 1, 3)),
                bar(LocalDate.of(2024, 1, 4)),
                bar(LocalDate.of(2024, 1, 5)),
                bar(LocalDate.of(2024, 1, 8)),
                bar(LocalDate.of(2024, 1, 9))));
    }

    private static Bar bar(LocalDate date) {
        return new Bar(date, new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("99"),
                new BigDecimal("100.5"), 1000);
    }

    private static BacktestConfig config(LocalDate start, LocalDate end) {
        return new BacktestConfig(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, start, end);
    }

    @Test
    void exactFirstAndLastCoverageBoundaryIsAccepted() {
        assertDoesNotThrow(() -> BacktestRangeValidator.validate(weekdaySeries(),
                config(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 9))));
    }

    @Test
    void startBeforeTheDatasetsFirstDateIsRejected() {
        BacktestRangeException e = assertThrows(BacktestRangeException.class, () -> BacktestRangeValidator.validate(
                weekdaySeries(), config(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 9))));
        assertEquals(LocalDate.of(2024, 1, 2), e.datasetFirstDate());
        assertEquals(LocalDate.of(2024, 1, 9), e.datasetLastDate());
    }

    @Test
    void endAfterTheDatasetsLastDateIsRejected() {
        BacktestRangeException e = assertThrows(BacktestRangeException.class, () -> BacktestRangeValidator.validate(
                weekdaySeries(), config(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 10))));
        assertEquals(LocalDate.of(2024, 1, 2), e.requestedStartDate());
        assertEquals(LocalDate.of(2024, 1, 10), e.requestedEndDate());
    }

    @Test
    void aRangeFallingEntirelyInsideTheWeekendGapIsRejected() {
        assertThrows(BacktestRangeException.class, () -> BacktestRangeValidator.validate(weekdaySeries(),
                config(LocalDate.of(2024, 1, 6), LocalDate.of(2024, 1, 7))));
    }

    @Test
    void aValidRangeWhereAnIndicatorNeverBecomesReadyIsAcceptedForEngineExecution() {
        BarSeries series = weekdaySeries();
        BacktestConfig cfg = config(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 9));

        assertDoesNotThrow(() -> BacktestRangeValidator.validate(series, cfg));

        StrategyDefinition neverReadyStrategy = new StrategyDefinition(
                new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 20)),
                        Operator.GT, new Operand.Constant(0)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));

        var result = new Backtester().run(series, neverReadyStrategy, cfg);
        assertEquals(true, result.firstEvaluableDate().isEmpty());
    }
}
