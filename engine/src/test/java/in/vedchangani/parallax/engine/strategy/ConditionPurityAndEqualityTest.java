package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionPurityAndEqualityTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);
    private static final BigDecimal CLOSE = new BigDecimal("101.50");
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);
    private static final IndicatorSpec SMA_50 = new IndicatorSpec(IndicatorType.SMA, 50);
    private static final IndicatorSpec RSI_14 = new IndicatorSpec(IndicatorType.RSI, 14);

    private static IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(DATE, CLOSE, Map.of(SMA_20, 100.0, SMA_50, 95.0, RSI_14, 60.0));
    }

    @Test
    void evaluatingTwiceWithTheSameSnapshotGivesTheSameResult() {
        Condition condition = new Condition.All(List.of(
                new Condition.Compare(new Operand.IndicatorRef(SMA_20), Operator.GT, new Operand.IndicatorRef(SMA_50)),
                new Condition.Compare(new Operand.IndicatorRef(RSI_14), Operator.LT, new Operand.Constant(70))));

        IndicatorSnapshot snapshot = snapshot();

        assertEquals(condition.evaluate(snapshot), condition.evaluate(snapshot));
        assertTrue(condition.evaluate(snapshot));
    }

    @Test
    void evaluationDoesNotMutateTheSnapshot() {
        Condition condition = new Condition.Compare(new Operand.IndicatorRef(SMA_20), Operator.GT, new Operand.IndicatorRef(SMA_50));
        IndicatorSnapshot snapshot = snapshot();
        IndicatorSnapshot expected = snapshot();

        condition.evaluate(snapshot);

        assertEquals(expected, snapshot);
    }

    @Test
    void structurallyEqualTreesAreEqualAndHaveEqualHashCodes() {
        Condition a = new Condition.All(List.of(
                new Condition.Compare(new Operand.IndicatorRef(SMA_20), Operator.GT, new Operand.IndicatorRef(SMA_50)),
                new Condition.Compare(new Operand.IndicatorRef(RSI_14), Operator.LT, new Operand.Constant(70))));
        Condition b = new Condition.All(List.of(
                new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 20)), Operator.GT,
                        new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 50))),
                new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.RSI, 14)), Operator.LT,
                        new Operand.Constant(70))));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void changingChildOrderChangesEquality() {
        Condition first = new Condition.Compare(new Operand.IndicatorRef(SMA_20), Operator.GT, new Operand.IndicatorRef(SMA_50));
        Condition second = new Condition.Compare(new Operand.IndicatorRef(RSI_14), Operator.LT, new Operand.Constant(70));

        Condition ab = new Condition.All(List.of(first, second));
        Condition ba = new Condition.All(List.of(second, first));

        assertNotEquals(ab, ba);
    }

    @Test
    void nestedConditionsRetainStructuralEquality() {
        Condition a = new Condition.All(List.of(
                new Condition.Any(List.of(
                        new Condition.Compare(new Operand.IndicatorRef(SMA_20), Operator.GT, new Operand.IndicatorRef(SMA_50)),
                        new Condition.Compare(new Operand.IndicatorRef(RSI_14), Operator.LT, new Operand.Constant(70))))));
        Condition b = new Condition.All(List.of(
                new Condition.Any(List.of(
                        new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 20)), Operator.GT,
                                new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 50))),
                        new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.RSI, 14)), Operator.LT,
                                new Operand.Constant(70))))));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
