package in.vedchangani.parallax.engine.strategy;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyDefinitionStructureTest {

    @Test
    void strategyDefinitionComponentsAreConditionConditionPositionSizing() {
        RecordComponent[] components = StrategyDefinition.class.getRecordComponents();

        assertEquals(3, components.length);
        assertEquals(Condition.class, components[0].getType());
        assertEquals(Condition.class, components[1].getType());
        assertEquals(PositionSizing.class, components[2].getType());
    }

    @Test
    void positionSizingPermitsExactlyCashFraction() {
        assertTrue(PositionSizing.class.isSealed());
        assertEquals(Set.of(PositionSizing.CashFraction.class), Set.of(PositionSizing.class.getPermittedSubclasses()));
    }

    @Test
    void cashFractionHasExactlyOneBigDecimalComponent() {
        RecordComponent[] components = PositionSizing.CashFraction.class.getRecordComponents();

        assertEquals(1, components.length);
        assertEquals(BigDecimal.class, components[0].getType());
    }

    @Test
    void requiredIndicatorSpecsReturnsAListOfIndicatorSpec() throws NoSuchMethodException {
        var method = StrategyDefinition.class.getMethod("requiredIndicatorSpecs");

        assertEquals(List.class, method.getReturnType());
    }
}
