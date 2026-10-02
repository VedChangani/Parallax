package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyDefinitionTest {

    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);
    private static final IndicatorSpec SMA_50 = new IndicatorSpec(IndicatorType.SMA, 50);
    private static final IndicatorSpec EMA_10 = new IndicatorSpec(IndicatorType.EMA, 10);
    private static final IndicatorSpec RSI_14 = new IndicatorSpec(IndicatorType.RSI, 14);

    private static final Operand REF_20 = new Operand.IndicatorRef(SMA_20);
    private static final Operand REF_50 = new Operand.IndicatorRef(SMA_50);
    private static final Operand REF_EMA_10 = new Operand.IndicatorRef(EMA_10);
    private static final Operand REF_RSI_14 = new Operand.IndicatorRef(RSI_14);
    private static final Operand CLOSE_OPERAND = new Operand.Close();
    private static final Operand CONSTANT_70 = new Operand.Constant(70);

    private static final Condition ENTRY = new Condition.Compare(REF_20, Operator.GT, REF_50);
    private static final Condition EXIT = new Condition.Compare(REF_RSI_14, Operator.LT, CONSTANT_70);
    private static final PositionSizing SIZING = new PositionSizing.CashFraction(new BigDecimal("0.5"));

    @Test
    void validConstructionExposesComponents() {
        StrategyDefinition definition = new StrategyDefinition(ENTRY, EXIT, SIZING);

        assertEquals(ENTRY, definition.entryCondition());
        assertEquals(EXIT, definition.exitCondition());
        assertEquals(SIZING, definition.positionSizing());
    }

    @Test
    void nullEntryConditionRejected() {
        assertThrows(NullPointerException.class, () -> new StrategyDefinition(null, EXIT, SIZING));
    }

    @Test
    void nullExitConditionRejected() {
        assertThrows(NullPointerException.class, () -> new StrategyDefinition(ENTRY, null, SIZING));
    }

    @Test
    void nullPositionSizingRejected() {
        assertThrows(NullPointerException.class, () -> new StrategyDefinition(ENTRY, EXIT, null));
    }

    @Test
    void equalDefinitionsAreEqual() {
        StrategyDefinition a = new StrategyDefinition(ENTRY, EXIT, SIZING);
        StrategyDefinition b = new StrategyDefinition(
                new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 20)), Operator.GT,
                        new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 50))),
                new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.RSI, 14)), Operator.LT,
                        new Operand.Constant(70)),
                new PositionSizing.CashFraction(new BigDecimal("0.50")));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentEntryConditionMakesDefinitionsUnequal() {
        StrategyDefinition a = new StrategyDefinition(ENTRY, EXIT, SIZING);
        StrategyDefinition b = new StrategyDefinition(
                new Condition.Compare(REF_20, Operator.LT, REF_50), EXIT, SIZING);

        assertNotEquals(a, b);
    }

    @Test
    void differentExitConditionMakesDefinitionsUnequal() {
        StrategyDefinition a = new StrategyDefinition(ENTRY, EXIT, SIZING);
        StrategyDefinition b = new StrategyDefinition(
                ENTRY, new Condition.Compare(REF_RSI_14, Operator.GT, CONSTANT_70), SIZING);

        assertNotEquals(a, b);
    }

    @Test
    void differentPositionSizingMakesDefinitionsUnequal() {
        StrategyDefinition a = new StrategyDefinition(ENTRY, EXIT, SIZING);
        StrategyDefinition b = new StrategyDefinition(ENTRY, EXIT, new PositionSizing.CashFraction(BigDecimal.ONE));

        assertNotEquals(a, b);
    }

    @Test
    void oneIndicatorInEntryOnly() {
        StrategyDefinition definition = new StrategyDefinition(
                new Condition.Compare(REF_20, Operator.GT, CONSTANT_70),
                new Condition.Compare(CLOSE_OPERAND, Operator.LT, CONSTANT_70),
                SIZING);

        assertEquals(List.of(SMA_20), definition.requiredIndicatorSpecs());
    }

    @Test
    void oneIndicatorInExitOnly() {
        StrategyDefinition definition = new StrategyDefinition(
                new Condition.Compare(CLOSE_OPERAND, Operator.GT, CONSTANT_70),
                new Condition.Compare(REF_50, Operator.LT, CONSTANT_70),
                SIZING);

        assertEquals(List.of(SMA_50), definition.requiredIndicatorSpecs());
    }

    @Test
    void sameIndicatorInBothEntryAndExitProducesOneResult() {
        StrategyDefinition definition = new StrategyDefinition(
                new Condition.Compare(REF_20, Operator.GT, CONSTANT_70),
                new Condition.Compare(REF_20, Operator.LT, CONSTANT_70),
                SIZING);

        assertEquals(List.of(SMA_20), definition.requiredIndicatorSpecs());
    }

    @Test
    void twoDistinctSmaSpecsAreBothDiscovered() {
        StrategyDefinition definition = new StrategyDefinition(ENTRY, EXIT, SIZING);

        assertTrue(definition.requiredIndicatorSpecs().containsAll(List.of(SMA_20, SMA_50)));
    }

    @Test
    void multiplePeriodsOfOneIndicatorTypeAreBothDiscovered() {
        StrategyDefinition definition = new StrategyDefinition(
                new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.RSI, 7)), Operator.GT,
                        new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.RSI, 21))),
                EXIT, SIZING);

        List<IndicatorSpec> specs = definition.requiredIndicatorSpecs();
        assertTrue(specs.contains(new IndicatorSpec(IndicatorType.RSI, 7)));
        assertTrue(specs.contains(new IndicatorSpec(IndicatorType.RSI, 21)));
        assertTrue(specs.contains(RSI_14));
    }

    @Test
    void indicatorOnBothSidesOfOneCompareIsDiscovered() {
        Condition bothSides = new Condition.Compare(REF_20, Operator.GT, REF_50);
        StrategyDefinition definition = new StrategyDefinition(bothSides, bothSides, SIZING);

        assertEquals(List.of(SMA_20, SMA_50), definition.requiredIndicatorSpecs());
    }

    @Test
    void nestedAllAndAnyAreTraversed() {
        Condition entry = new Condition.All(List.of(
                new Condition.Any(List.of(
                        new Condition.Compare(REF_20, Operator.GT, REF_50),
                        new Condition.Compare(REF_EMA_10, Operator.LT, CONSTANT_70))),
                new Condition.Compare(REF_RSI_14, Operator.LT, CONSTANT_70)));
        StrategyDefinition definition = new StrategyDefinition(entry, EXIT, SIZING);

        assertEquals(List.of(SMA_20, SMA_50, EMA_10, RSI_14), definition.requiredIndicatorSpecs());
    }

    @Test
    void indicatorsNestedSeveralLevelsDeepAreDiscovered() {
        Condition deeplyNested = new Condition.All(List.of(
                new Condition.Any(List.of(
                        new Condition.All(List.of(
                                new Condition.Compare(REF_EMA_10, Operator.GT, CONSTANT_70)))))));
        StrategyDefinition definition = new StrategyDefinition(deeplyNested, EXIT, SIZING);

        assertTrue(definition.requiredIndicatorSpecs().contains(EMA_10));
    }

    @Test
    void closeContributesNoSpec() {
        StrategyDefinition definition = new StrategyDefinition(
                new Condition.Compare(CLOSE_OPERAND, Operator.GT, CONSTANT_70),
                new Condition.Compare(CLOSE_OPERAND, Operator.LT, CONSTANT_70),
                SIZING);

        assertEquals(List.of(), definition.requiredIndicatorSpecs());
    }

    @Test
    void constantContributesNoSpec() {
        StrategyDefinition definition = new StrategyDefinition(
                new Condition.Compare(CONSTANT_70, Operator.GT, new Operand.Constant(50)),
                new Condition.Compare(CONSTANT_70, Operator.LT, new Operand.Constant(90)),
                SIZING);

        assertEquals(List.of(), definition.requiredIndicatorSpecs());
    }

    @Test
    void noIndicatorsAtAllGivesAnEmptyList() {
        StrategyDefinition definition = new StrategyDefinition(
                new Condition.Compare(CLOSE_OPERAND, Operator.GT, CONSTANT_70),
                new Condition.Compare(CONSTANT_70, Operator.LT, new Operand.Constant(90)),
                SIZING);

        assertEquals(List.of(), definition.requiredIndicatorSpecs());
    }

    @Test
    void mixedIndicatorTypesComeBackInCanonicalOrder() {
        Condition entry = new Condition.All(List.of(
                new Condition.Compare(REF_RSI_14, Operator.GT, CONSTANT_70),
                new Condition.Compare(REF_EMA_10, Operator.GT, CONSTANT_70)));
        Condition exit = new Condition.All(List.of(
                new Condition.Compare(REF_50, Operator.LT, CONSTANT_70),
                new Condition.Compare(REF_20, Operator.LT, CONSTANT_70)));
        StrategyDefinition definition = new StrategyDefinition(entry, exit, SIZING);

        assertEquals(List.of(SMA_20, SMA_50, EMA_10, RSI_14), definition.requiredIndicatorSpecs());
    }

    @Test
    void orderDoesNotDependOnConditionTreeTraversalOrder() {
        StrategyDefinition authoredForward = new StrategyDefinition(
                new Condition.Compare(REF_20, Operator.GT, REF_50), EXIT, SIZING);
        StrategyDefinition authoredBackward = new StrategyDefinition(
                new Condition.Compare(REF_50, Operator.LT, REF_20), EXIT, SIZING);

        assertEquals(authoredForward.requiredIndicatorSpecs(), authoredBackward.requiredIndicatorSpecs());
    }

    @Test
    void repeatedCallsReturnEqualResults() {
        StrategyDefinition definition = new StrategyDefinition(ENTRY, EXIT, SIZING);

        assertEquals(definition.requiredIndicatorSpecs(), definition.requiredIndicatorSpecs());
    }

    @Test
    void returnedListIsUnmodifiable() {
        StrategyDefinition definition = new StrategyDefinition(ENTRY, EXIT, SIZING);
        List<IndicatorSpec> specs = definition.requiredIndicatorSpecs();

        assertThrows(UnsupportedOperationException.class, () -> specs.add(RSI_14));
    }

    @Test
    void separatelyConstructedEqualSpecsDedupe() {
        Condition entry = new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 20)),
                Operator.GT, new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 20)));
        StrategyDefinition definition = new StrategyDefinition(entry, EXIT, SIZING);

        assertEquals(1, definition.requiredIndicatorSpecs().stream().filter(SMA_20::equals).count());
    }

    @Test
    void orderMatchesIndicatorSnapshotCanonicalOrderForTheSameSpecs() {
        StrategyDefinition definition = new StrategyDefinition(
                new Condition.All(List.of(
                        new Condition.Compare(REF_RSI_14, Operator.GT, CONSTANT_70),
                        new Condition.Compare(REF_EMA_10, Operator.GT, CONSTANT_70),
                        new Condition.Compare(REF_50, Operator.GT, CONSTANT_70),
                        new Condition.Compare(REF_20, Operator.GT, CONSTANT_70))),
                EXIT, SIZING);
        List<IndicatorSpec> specs = definition.requiredIndicatorSpecs();

        Map<IndicatorSpec, Double> values = new HashMap<>();
        for (IndicatorSpec spec : specs) {
            values.put(spec, 1.0);
        }
        IndicatorSnapshot snapshot = new IndicatorSnapshot(LocalDate.of(2024, 1, 2), BigDecimal.TEN, values);

        assertEquals(specs, List.copyOf(snapshot.values().keySet()));
    }
}
