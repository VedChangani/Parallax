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

/**
 * {@code Condition} is sealed to {@code Compare}, {@code All} and
 * {@code Any}, so test doubles cannot implement it directly. These tests
 * instead build always-true/always-false {@code Compare} conditions from
 * constant-vs-constant comparisons, and observe short-circuiting through a
 * "poison" condition that throws if it is ever evaluated (an
 * {@code IndicatorRef} to a spec absent from the fixture snapshot).
 */
class AnyTest {

    private static final IndicatorSnapshot SNAPSHOT =
            new IndicatorSnapshot(LocalDate.of(2024, 1, 2), new BigDecimal("100"), Map.of());

    private static final Condition TRUE = new Condition.Compare(new Operand.Constant(1), Operator.GT, new Operand.Constant(0));
    private static final Condition FALSE = new Condition.Compare(new Operand.Constant(0), Operator.GT, new Operand.Constant(1));
    private static final Condition POISON = new Condition.Compare(
            new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.RSI, 14)), Operator.GT, new Operand.Constant(0));

    @Test
    void oneTrue() {
        Condition.Any any = new Condition.Any(List.of(FALSE, TRUE, FALSE));

        assertTrue(any.evaluate(SNAPSHOT));
    }

    @Test
    void allFalse() {
        Condition.Any any = new Condition.Any(List.of(FALSE, FALSE));

        assertFalse(any.evaluate(SNAPSHOT));
    }

    @Test
    void nestedConditions() {
        Condition.All inner = new Condition.All(List.of(TRUE, TRUE));
        Condition.Any outer = new Condition.Any(List.of(FALSE, inner));

        assertTrue(outer.evaluate(SNAPSHOT));
    }

    @Test
    void emptyListRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Condition.Any(List.of()));
    }

    @Test
    void nullListRejected() {
        assertThrows(NullPointerException.class, () -> new Condition.Any(null));
    }

    @Test
    void nullChildRejected() {
        List<Condition> withNull = new ArrayList<>();
        withNull.add(FALSE);
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> new Condition.Any(withNull));
    }

    @Test
    void defensiveCopy() {
        List<Condition> source = new ArrayList<>();
        source.add(FALSE);

        Condition.Any any = new Condition.Any(source);
        source.add(TRUE);

        assertFalse(any.evaluate(SNAPSHOT));
        assertEquals(1, any.conditions().size());
    }

    @Test
    void listCannotBeExternallyMutated() {
        Condition.Any any = new Condition.Any(List.of(FALSE));

        assertThrows(UnsupportedOperationException.class, () -> any.conditions().add(TRUE));
    }

    @Test
    void poisonFailsWhenItIsEvaluated() {
        // Guards the short-circuit test below: POISON must genuinely throw
        // when reached, and Any must continue past a false child to reach it.
        assertThrows(IllegalArgumentException.class, () -> POISON.evaluate(SNAPSHOT));
        assertThrows(IllegalArgumentException.class,
                () -> new Condition.Any(List.of(FALSE, POISON)).evaluate(SNAPSHOT));
    }

    @Test
    void shortCircuitsOnFirstTrue() {
        assertTrue(new Condition.Any(List.of(TRUE, POISON)).evaluate(SNAPSHOT));
        assertTrue(new Condition.Any(List.of(FALSE, TRUE, POISON)).evaluate(SNAPSHOT));
    }
}
