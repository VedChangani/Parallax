package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllTest {

    private static final IndicatorSnapshot SNAPSHOT =
            new IndicatorSnapshot(LocalDate.of(2024, 1, 2), new BigDecimal("100"), Map.of());

    private static final Condition TRUE = new Condition.Compare(new Operand.Constant(1), Operator.GT, new Operand.Constant(0));
    private static final Condition FALSE = new Condition.Compare(new Operand.Constant(0), Operator.GT, new Operand.Constant(1));
    private static final Condition POISON = new Condition.Compare(
            new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.RSI, 14)), Operator.GT, new Operand.Constant(0));

    @Test
    void allTrue() {
        Condition.All all = new Condition.All(List.of(TRUE, TRUE, TRUE));

        assertTrue(all.evaluate(SNAPSHOT));
    }

    @Test
    void oneFalse() {
        Condition.All all = new Condition.All(List.of(TRUE, FALSE, TRUE));

        assertFalse(all.evaluate(SNAPSHOT));
    }

    @Test
    void nestedConditions() {
        Condition.Any inner = new Condition.Any(List.of(FALSE, TRUE));
        Condition.All outer = new Condition.All(List.of(TRUE, inner));

        assertTrue(outer.evaluate(SNAPSHOT));
    }

    @Test
    void emptyListRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Condition.All(List.of()));
    }

    @Test
    void nullListRejected() {
        assertThrows(NullPointerException.class, () -> new Condition.All(null));
    }

    @Test
    void nullChildRejected() {
        List<Condition> withNull = new ArrayList<>();
        withNull.add(TRUE);
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> new Condition.All(withNull));
    }

    @Test
    void defensiveCopy() {
        List<Condition> source = new ArrayList<>();
        source.add(TRUE);

        Condition.All all = new Condition.All(source);
        source.add(FALSE);

        assertTrue(all.evaluate(SNAPSHOT));
        assertEquals(1, all.conditions().size());
    }

    @Test
    void listCannotBeExternallyMutated() {
        Condition.All all = new Condition.All(List.of(TRUE));

        assertThrows(UnsupportedOperationException.class, () -> all.conditions().add(FALSE));
    }

    @Test
    void poisonFailsWhenItIsEvaluated() {
        assertThrows(IllegalArgumentException.class, () -> POISON.evaluate(SNAPSHOT));
        assertThrows(IllegalArgumentException.class,
                () -> new Condition.All(List.of(TRUE, POISON)).evaluate(SNAPSHOT));
    }

    @Test
    void shortCircuitsOnFirstFalse() {
        assertFalse(new Condition.All(List.of(FALSE, POISON)).evaluate(SNAPSHOT));
        assertFalse(new Condition.All(List.of(TRUE, FALSE, POISON)).evaluate(SNAPSHOT));
    }
}
