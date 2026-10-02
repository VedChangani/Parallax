package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyGrammarStructureTest {

    private static final Set<Class<?>> ALLOWED_COMPONENT_TYPES =
            Set.of(IndicatorSpec.class, double.class, Operand.class, Operator.class, List.class);

    @Test
    void operandPermitsExactlyIndicatorRefCloseAndConstant() {
        assertTrue(Operand.class.isSealed());
        assertEquals(
                Set.of(Operand.IndicatorRef.class, Operand.Close.class, Operand.Constant.class),
                Set.of(Operand.class.getPermittedSubclasses()));
    }

    @Test
    void conditionPermitsExactlyCompareAllAndAny() {
        assertTrue(Condition.class.isSealed());
        assertEquals(
                Set.of(Condition.Compare.class, Condition.All.class, Condition.Any.class),
                Set.of(Condition.class.getPermittedSubclasses()));
    }

    @Test
    void operatorIsExactlyGtAndLt() {
        assertArrayEquals(new Operator[] {Operator.GT, Operator.LT}, Operator.values());
    }

    @Test
    void everyGrammarRecordComponentHasAnApprovedType() {
        for (Class<?> sealedType : List.of(Operand.class, Condition.class)) {
            for (Class<?> permitted : sealedType.getPermittedSubclasses()) {
                assertTrue(permitted.isRecord(), permitted.getName() + " must be a record");
                for (RecordComponent component : permitted.getRecordComponents()) {
                    assertTrue(ALLOWED_COMPONENT_TYPES.contains(component.getType()),
                            permitted.getSimpleName() + "." + component.getName()
                                    + " has unapproved type " + component.getType().getName());
                }
            }
        }
    }

    @Test
    void listComponentsHoldConditions() {
        for (Class<?> permitted : Condition.class.getPermittedSubclasses()) {
            for (RecordComponent component : permitted.getRecordComponents()) {
                if (component.getType() != List.class) {
                    continue;
                }
                Type generic = component.getGenericType();
                assertTrue(generic instanceof ParameterizedType,
                        permitted.getSimpleName() + "." + component.getName() + " must be a parameterized List");
                Type[] arguments = ((ParameterizedType) generic).getActualTypeArguments();
                assertArrayEquals(new Type[] {Condition.class}, arguments,
                        permitted.getSimpleName() + "." + component.getName() + " must be List<Condition>");
            }
        }
    }
}
