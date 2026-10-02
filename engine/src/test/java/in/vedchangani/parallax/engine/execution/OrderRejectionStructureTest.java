package in.vedchangani.parallax.engine.execution;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.indicator.Indicator;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRejectionStructureTest {

    @Test
    void orderRejectionPermitsExactlyZeroQuantityAndInsufficientCash() {
        assertTrue(OrderRejection.class.isSealed());
        assertEquals(Set.of(OrderRejection.ZeroQuantity.class, OrderRejection.InsufficientCash.class),
                Set.of(OrderRejection.class.getPermittedSubclasses()));
    }

    @Test
    void zeroQuantityHasExactlyOneSignalEventComponent() {
        RecordComponent[] components = OrderRejection.ZeroQuantity.class.getRecordComponents();

        assertEquals(1, components.length);
        assertEquals(SignalEvent.class, components[0].getType());
    }

    @Test
    void insufficientCashComponentsAreOrderIdDateQuantityCashFieldsAndSignal() {
        RecordComponent[] components = OrderRejection.InsufficientCash.class.getRecordComponents();

        assertEquals(6, components.length);
        assertEquals(int.class, components[0].getType());
        assertEquals(LocalDate.class, components[1].getType());
        assertEquals(long.class, components[2].getType());
        assertEquals(BigDecimal.class, components[3].getType());
        assertEquals(BigDecimal.class, components[4].getType());
        assertEquals(SignalEvent.class, components[5].getType());
    }

    @Test
    void neitherPermittedRecordLeaksForbiddenTypes() {
        for (Class<?> permitted : OrderRejection.class.getPermittedSubclasses()) {
            for (RecordComponent component : permitted.getRecordComponents()) {
                Class<?> type = component.getType();
                assertFalse(Order.class.isAssignableFrom(type),
                        permitted.getSimpleName() + "." + component.getName() + " must not hold an Order");
                assertFalse(Bar.class.isAssignableFrom(type),
                        permitted.getSimpleName() + "." + component.getName() + " must not hold a Bar");
                assertFalse(BarSeries.class.isAssignableFrom(type),
                        permitted.getSimpleName() + "." + component.getName() + " must not hold a BarSeries");
                assertFalse(Indicator.class.isAssignableFrom(type),
                        permitted.getSimpleName() + "." + component.getName()
                                + " must not hold a runtime Indicator");
                assertFalse(java.util.Collection.class.isAssignableFrom(type),
                        permitted.getSimpleName() + "." + component.getName() + " must not hold a collection");
            }
        }
    }

    @Test
    void orderSideHasExactlyBuyAndSell() {
        assertEquals(Set.of(OrderSide.BUY, OrderSide.SELL), Set.of(OrderSide.values()));
    }
}
