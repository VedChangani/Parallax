package in.vedchangani.parallax.backend.strategy.definition;

import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyDefinitionMapperTest {

    private final StrategyDefinitionMapper mapper = new StrategyDefinitionMapper();

    private static OperandDto.Constant constant(String value) {
        return new OperandDto.Constant(value);
    }

    private static OperandDto.Indicator indicator(IndicatorTypeDto type, int period) {
        return new OperandDto.Indicator(type, period);
    }

    private static ConditionDto.Compare compare(OperandDto left, OperatorDto op, OperandDto right) {
        return new ConditionDto.Compare(left, op, right);
    }

    private static PositionSizingDto.CashFraction fraction(String value) {
        return new PositionSizingDto.CashFraction(value);
    }

    private static StrategyDefinitionDto definition(ConditionDto entry, ConditionDto exit, PositionSizingDto sizing) {
        return new StrategyDefinitionDto(entry, exit, sizing);
    }

    private static final ConditionDto ALWAYS_TRUE = compare(constant("1"), OperatorDto.GT, constant("0"));
    private static final ConditionDto ALWAYS_FALSE = compare(constant("0"), OperatorDto.GT, constant("1"));
    private static final PositionSizingDto FULL = fraction("1");

    @Test
    void toEngineMapsAllThreeOperandKinds() {
        ConditionDto entry = compare(indicator(IndicatorTypeDto.SMA, 20), OperatorDto.GT, constant("50"));
        ConditionDto exit = compare(new OperandDto.Close(), OperatorDto.LT, indicator(IndicatorTypeDto.RSI, 14));
        StrategyDefinition def = mapper.toEngine(definition(entry, exit, fraction("0.5")));

        Condition.Compare entryCompare = (Condition.Compare) def.entryCondition();
        assertEquals(new Operand.IndicatorRef(new in.vedchangani.parallax.engine.indicator.IndicatorSpec(
                in.vedchangani.parallax.engine.indicator.IndicatorType.SMA, 20)), entryCompare.left());
        assertEquals(Operator.GT, entryCompare.operator());
        assertEquals(new Operand.Constant(50.0), entryCompare.right());

        Condition.Compare exitCompare = (Condition.Compare) def.exitCondition();
        assertEquals(new Operand.Close(), exitCompare.left());
        assertEquals(Operator.LT, exitCompare.operator());
        assertEquals(new Operand.IndicatorRef(new in.vedchangani.parallax.engine.indicator.IndicatorSpec(
                in.vedchangani.parallax.engine.indicator.IndicatorType.RSI, 14)), exitCompare.right());

        assertEquals(new PositionSizing.CashFraction(new BigDecimal("0.5")), def.positionSizing());
    }

    @Test
    void toEngineMapsAllAndAnyPreservingOrder() {
        ConditionDto entry = new ConditionDto.All(List.of(
                compare(constant("1"), OperatorDto.GT, constant("0")),
                new ConditionDto.Any(List.of(
                        compare(constant("2"), OperatorDto.GT, constant("1")),
                        compare(constant("3"), OperatorDto.GT, constant("2"))))));
        StrategyDefinition def = mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL));

        Condition.All all = (Condition.All) def.entryCondition();
        assertEquals(2, all.conditions().size());
        assertTrue(all.conditions().get(1) instanceof Condition.Any);
        Condition.Any any = (Condition.Any) all.conditions().get(1);
        assertEquals(2, any.conditions().size());
    }

    @Test
    void toDtoIsInverseOfToEngineForANestedDefinition() {
        StrategyDefinition original = new StrategyDefinition(
                new Condition.All(List.of(
                        new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(1.5)),
                        new Condition.Compare(
                                new Operand.IndicatorRef(new in.vedchangani.parallax.engine.indicator.IndicatorSpec(
                                        in.vedchangani.parallax.engine.indicator.IndicatorType.EMA, 10)),
                                Operator.LT, new Operand.Constant(-3.25)))),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(new BigDecimal("0.25")));

        StrategyDefinitionDto dto = mapper.toDto(original);
        StrategyDefinition roundTripped = mapper.toEngine(dto);

        assertEquals(original, roundTripped);
    }

    @Test
    void toEngineMapsAtrAndRocIndicators() {
        ConditionDto entry = compare(indicator(IndicatorTypeDto.ATR, 14), OperatorDto.GT, constant("2"));
        ConditionDto exit = compare(indicator(IndicatorTypeDto.ROC, 12), OperatorDto.LT, constant("-5"));

        StrategyDefinition def = mapper.toEngine(definition(entry, exit, FULL));

        assertEquals(new Operand.IndicatorRef(new in.vedchangani.parallax.engine.indicator.IndicatorSpec(
                in.vedchangani.parallax.engine.indicator.IndicatorType.ATR, 14)),
                ((Condition.Compare) def.entryCondition()).left());
        assertEquals(new Operand.IndicatorRef(new in.vedchangani.parallax.engine.indicator.IndicatorSpec(
                in.vedchangani.parallax.engine.indicator.IndicatorType.ROC, 12)),
                ((Condition.Compare) def.exitCondition()).left());
    }

    @Test
    void toDtoIsInverseOfToEngineForAtrAndRoc() {
        StrategyDefinitionDto original = definition(
                compare(indicator(IndicatorTypeDto.ATR, 14), OperatorDto.GT, indicator(IndicatorTypeDto.ROC, 1)),
                compare(indicator(IndicatorTypeDto.ROC, 12), OperatorDto.LT, constant("-5.0")),
                FULL);

        assertEquals(original, mapper.toDto(mapper.toEngine(original)));
    }

    @Test
    void atrAndRocPeriodOneIsValid() {
        ConditionDto entry = compare(indicator(IndicatorTypeDto.ATR, 1), OperatorDto.GT,
                indicator(IndicatorTypeDto.ROC, 1));

        StrategyDefinition def = mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL));

        assertEquals(entry, mapper.toDto(def).entryCondition());
    }

    @Test
    void atrPeriodZeroIsInvalid() {
        ConditionDto entry = compare(indicator(IndicatorTypeDto.ATR, 0), OperatorDto.GT, constant("0"));
        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));
        assertEquals("entryCondition.left", e.path());
    }

    @Test
    void rocPeriodZeroIsInvalid() {
        ConditionDto entry = compare(constant("1"), OperatorDto.GT, indicator(IndicatorTypeDto.ROC, 0));
        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));
        assertEquals("entryCondition.right", e.path());
    }

    @Test
    void pathNamesTheNestedOperandThatFailed() {
        ConditionDto entry = new ConditionDto.All(List.of(
                compare(constant("1"), OperatorDto.GT, constant("0")),
                compare(indicator(IndicatorTypeDto.RSI, 1), OperatorDto.GT, constant("0"))));

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));

        assertEquals("entryCondition.conditions[1].left", e.path());
    }

    @Test
    void pathNamesTheGroupThatIsEmpty() {
        ConditionDto entry = new ConditionDto.All(List.of());

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));

        assertEquals("entryCondition", e.path());
    }

    @Test
    void pathNamesPositionSizingOnRangeFailure() {
        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction("0"))));

        assertEquals("positionSizing", e.path());
    }

    @Test
    void firstFailureWinsEntryBeforeExitBeforeSizing() {
        ConditionDto badEntry = compare(indicator(IndicatorTypeDto.RSI, 1), OperatorDto.GT, constant("0"));

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(badEntry, ALWAYS_FALSE, fraction("0"))));

        assertEquals("entryCondition.left", e.path());
    }

    @Test
    void leftBeforeRightWithinACompare() {
        ConditionDto badEntry = compare(constant("1e-400"), OperatorDto.GT, constant("1e400"));

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(badEntry, ALWAYS_FALSE, FULL)));

        assertEquals("entryCondition.left", e.path());
    }

    @Test
    void constantOverflowIsInvalidAtEnginesNonFiniteCheck() {
        ConditionDto entry = compare(constant("1e400"), OperatorDto.GT, constant("0"));

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));

        assertEquals("entryCondition.left", e.path());
        assertTrue(e.getMessage().toLowerCase().contains("finite"));
    }

    @Test
    void constantUnderflowIsInvalid() {
        ConditionDto entry = compare(constant("1e-400"), OperatorDto.GT, constant("0"));

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));

        assertEquals("entryCondition.left", e.path());
        assertTrue(e.getMessage().contains("underflow"));
    }

    @Test
    void constantNegativeZeroIsAcceptedAndNormalized() {
        ConditionDto entry = compare(constant("-0.0"), OperatorDto.GT, constant("0"));
        StrategyDefinition def = mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL));

        Condition.Compare c = (Condition.Compare) def.entryCondition();
        assertEquals(new Operand.Constant(0.0), c.left());
    }

    @Test
    void constantGrammarRejectsNonDecimalText() {
        for (String bad : List.of("NaN", "Infinity", "+1", " 1", "1 ", "0x1p3", "1d", "1_000", "1,5", "")) {
            ConditionDto entry = compare(constant(bad), OperatorDto.GT, constant("0"));
            MalformedStrategyDefinitionException e = assertThrows(MalformedStrategyDefinitionException.class,
                    () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)),
                    "expected malformed for: " + bad);
            assertEquals("entryCondition.left", e.path());
        }
    }

    @Test
    void constantGrammarAcceptsEquivalentSpellingsWithTheSameEngineValue() {
        for (String text : List.of("0.1000", "1e-1", "1E-1", "0.1")) {
            ConditionDto entry = compare(constant(text), OperatorDto.GT, constant("0"));
            StrategyDefinition def = mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL));
            Condition.Compare c = (Condition.Compare) def.entryCondition();
            assertEquals(new Operand.Constant(0.1), c.left(), "for input " + text);
        }
    }

    @Test
    void constantExtremeNegativeExponentUnderflowsSafely() {
        ConditionDto entry = compare(constant("1e-9999999999"), OperatorDto.GT, constant("0"));

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));

        assertEquals("entryCondition.left", e.path());
        assertTrue(e.getMessage().contains("underflow"));
    }

    @Test
    void constantExtremePositiveExponentOverflowsSafely() {
        ConditionDto entry = compare(constant("1e9999999999"), OperatorDto.GT, constant("0"));

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));

        assertEquals("entryCondition.left", e.path());
    }

    @Test
    void constantExtremeExponentWithZeroMantissaIsGenuineZeroNotUnderflow() {
        ConditionDto entry = compare(constant("0e9999999999"), OperatorDto.GT, constant("0"));

        StrategyDefinition def = mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL));

        Condition.Compare c = (Condition.Compare) def.entryCondition();
        assertEquals(new Operand.Constant(0.0), c.left());
    }

    @Test
    void fractionAtBigDecimalScaleBoundaryConstructsAndIsRejectedByRangeNotByAnException() {
        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction("1e2147483648"))));
        assertEquals("positionSizing", e.path());
    }

    @Test
    void fractionOneExponentDigitBeyondTheBoundaryIsMalformedNotNumberFormatException() {
        MalformedStrategyDefinitionException e = assertThrows(MalformedStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction("1e2147483649"))));
        assertEquals("positionSizing", e.path());
    }

    @Test
    void fractionExtremeNegativeExponentIsMalformedNotNumberFormatException() {
        MalformedStrategyDefinitionException e = assertThrows(MalformedStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction("1e-2147483648"))));
        assertEquals("positionSizing", e.path());
    }

    @Test
    void fractionGrammarAcceptsExponentAndCanonicalizesScale() {
        for (String text : List.of("0.5", "0.500", "5E-1", "5e-1")) {
            StrategyDefinition def = mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction(text)));
            PositionSizing.CashFraction cf = (PositionSizing.CashFraction) def.positionSizing();
            assertEquals(0, new BigDecimal("0.5").compareTo(cf.fraction()), "for input " + text);
        }
    }

    @Test
    void fractionOutOfRangeIsInvalid() {
        for (String text : List.of("0", "1.0001", "-0.5")) {
            InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                    () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction(text))),
                    "expected invalid for: " + text);
            assertEquals("positionSizing", e.path());
        }
    }

    @Test
    void fractionGrammarRejectsNonDecimalText() {
        for (String bad : List.of("+0.5", "NaN", "", " 1")) {
            MalformedStrategyDefinitionException e = assertThrows(MalformedStrategyDefinitionException.class,
                    () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction(bad))),
                    "expected malformed for: " + bad);
            assertEquals("positionSizing", e.path());
        }
    }

    @Test
    void fractionAtExactlyTheLengthBoundIsAcceptedBecauseItsCanonicalValueIsTiny() {
        String text = "0.5" + "0".repeat(97);
        assertEquals(100, text.length());

        StrategyDefinition def = mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction(text)));
        PositionSizing.CashFraction cf = (PositionSizing.CashFraction) def.positionSizing();
        assertEquals(0, new BigDecimal("0.5").compareTo(cf.fraction()));
    }

    @Test
    void fractionOneCharacterBeyondTheLengthBoundIsMalformedNotInvalid() {
        String text = "0.5" + "0".repeat(98);
        assertEquals(101, text.length());

        MalformedStrategyDefinitionException e = assertThrows(MalformedStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction(text))));
        assertEquals("positionSizing", e.path());
    }

    @Test
    void fractionWithExactlyEighteenFractionalDigitsIsAccepted() {
        String text = "0." + "9".repeat(18);
        StrategyDefinition def = mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction(text)));
        PositionSizing.CashFraction cf = (PositionSizing.CashFraction) def.positionSizing();
        assertEquals(0, new BigDecimal(text).compareTo(cf.fraction()));
    }

    @Test
    void fractionWithNineteenFractionalDigitsIsInvalidNotMalformed() {
        String text = "0." + "9".repeat(19);
        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction(text))));
        assertEquals("positionSizing", e.path());
    }

    @Test
    void fractionWithNineteenIntegerDigitsIsInvalid() {
        String text = "1" + "0".repeat(18);
        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction(text))));
        assertEquals("positionSizing", e.path());
    }

    @Test
    void constantLiteralOverTheLengthBoundIsMalformed() {
        String text = "1." + "0".repeat(99);
        ConditionDto entry = compare(constant(text), OperatorDto.GT, constant("0"));

        MalformedStrategyDefinitionException e = assertThrows(MalformedStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));
        assertEquals("entryCondition.left", e.path());
    }

    @Test
    void constantFullDoubleRangeRemainsAcceptedNotBoundedByMagnitude() {
        for (String text : List.of("1e300", "1e-300", "1e-323")) {
            ConditionDto entry = compare(constant(text), OperatorDto.GT, constant("0"));
            StrategyDefinition def = mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL));
            Condition.Compare c = (Condition.Compare) def.entryCondition();
            assertEquals(Double.parseDouble(text), ((Operand.Constant) c.left()).value(), "for input " + text);
        }
    }

    @Test
    void rsiPeriodOneIsInvalid() {
        ConditionDto entry = compare(indicator(IndicatorTypeDto.RSI, 1), OperatorDto.GT, constant("0"));
        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));
        assertEquals("entryCondition.left", e.path());
    }

    @Test
    void smaPeriodZeroIsInvalid() {
        ConditionDto entry = compare(indicator(IndicatorTypeDto.SMA, 0), OperatorDto.GT, constant("0"));
        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL)));
        assertEquals("entryCondition.left", e.path());
    }

    @Test
    void nullArgumentsThrowNpe() {
        assertThrows(NullPointerException.class, () -> mapper.toEngine(null));
        assertThrows(NullPointerException.class, () -> mapper.toDto(null));
    }

    @Test
    void toEngineWithEmptyRootPathReproducesTheUnprefixedOverloadsPaths() {
        ConditionDto entry = compare(indicator(IndicatorTypeDto.RSI, 1), OperatorDto.GT, constant("0"));

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL), ""));

        assertEquals("entryCondition.left", e.path());
    }

    @Test
    void toEngineWithRootPathPrefixesAConditionFailurePath() {
        ConditionDto entry = compare(indicator(IndicatorTypeDto.RSI, 1), OperatorDto.GT, constant("0"));

        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(entry, ALWAYS_FALSE, FULL), "definition"));

        assertEquals("definition.entryCondition.left", e.path());
    }

    @Test
    void toEngineWithRootPathPrefixesAPositionSizingFailurePath() {
        InvalidStrategyDefinitionException e = assertThrows(InvalidStrategyDefinitionException.class,
                () -> mapper.toEngine(definition(ALWAYS_TRUE, ALWAYS_FALSE, fraction("0")), "definition"));

        assertEquals("definition.positionSizing", e.path());
    }

    @Test
    void toEngineWithRootPathProducesTheSameEngineObjectAsTheUnprefixedOverload() {
        StrategyDefinitionDto dto = definition(ALWAYS_TRUE, ALWAYS_FALSE, FULL);
        assertEquals(mapper.toEngine(dto), mapper.toEngine(dto, ""));
    }

    @Test
    void singleArgumentToEngineStillDelegatesToTheTwoArgumentOverloadUnchanged() {
        StrategyDefinitionDto dto = definition(ALWAYS_TRUE, ALWAYS_FALSE, FULL);
        assertEquals(mapper.toEngine(dto, ""), mapper.toEngine(dto));
    }

    @Test
    void toEngineWithRootPathRejectsNullArguments() {
        StrategyDefinitionDto dto = definition(ALWAYS_TRUE, ALWAYS_FALSE, FULL);
        assertThrows(NullPointerException.class, () -> mapper.toEngine(null, ""));
        assertThrows(NullPointerException.class, () -> mapper.toEngine(dto, null));
    }
}
