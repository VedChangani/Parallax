package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.strategy.SignalEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the D-20 shape: {@code Order}'s components are exactly
 * {@code (int, long, SignalEvent)} — no separately stored side, date,
 * price, or status. {@code side()} is confirmed to be a derived method,
 * not a record component.
 */
class OrderStructureTest {

    @Test
    void orderComponentsAreIdQuantityAndSignal() {
        RecordComponent[] components = Order.class.getRecordComponents();

        assertEquals(3, components.length);
        assertEquals(int.class, components[0].getType());
        assertEquals(long.class, components[1].getType());
        assertEquals(SignalEvent.class, components[2].getType());
    }

    @Test
    void orderHasNoOrderSideOrOtherUnapprovedComponent() {
        Set<Class<?>> componentTypes = Set.of(
                Arrays.stream(Order.class.getRecordComponents())
                        .map(RecordComponent::getType)
                        .toArray(Class<?>[]::new));

        assertFalse(componentTypes.contains(OrderSide.class));
    }
}
