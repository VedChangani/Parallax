package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FillStructureTest {

    @Test
    void fillComponentsAreOrderIdDateQuantityPricesAndSignal() {
        RecordComponent[] components = Fill.class.getRecordComponents();

        assertEquals(7, components.length);
        assertEquals(int.class, components[0].getType());
        assertEquals(LocalDate.class, components[1].getType());
        assertEquals(long.class, components[2].getType());
        assertEquals(BigDecimal.class, components[3].getType());
        assertEquals(BigDecimal.class, components[4].getType());
        assertEquals(BigDecimal.class, components[5].getType());
        assertEquals(SignalEvent.class, components[6].getType());
    }

    @Test
    void fillHasNoOrderOrOrderSideComponent() {
        Set<Class<?>> componentTypes = Arrays.stream(Fill.class.getRecordComponents())
                .map(RecordComponent::getType)
                .collect(java.util.stream.Collectors.toSet());

        assertFalse(componentTypes.contains(Order.class));
        assertFalse(componentTypes.contains(OrderSide.class));
    }

    @Test
    void fillHasNoStoredSlippageCostComponentButHasTheDerivedMethod() throws NoSuchMethodException {
        for (RecordComponent component : Fill.class.getRecordComponents()) {
            assertFalse(component.getName().toLowerCase().contains("slippage"));
        }

        var method = Fill.class.getMethod("slippageCost");
        assertEquals(BigDecimal.class, method.getReturnType());
    }
}
