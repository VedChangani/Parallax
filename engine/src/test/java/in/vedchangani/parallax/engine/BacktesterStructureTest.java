package in.vedchangani.parallax.engine;

import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.execution.Order;
import in.vedchangani.parallax.engine.indicator.Indicator;
import in.vedchangani.parallax.engine.portfolio.Portfolio;
import in.vedchangani.parallax.engine.result.BacktestResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the D-25 shape: {@code Backtester} is stateless, and no mutable
 * runtime object (a {@code Portfolio}, a pending {@code Order}, a runtime
 * {@code Indicator}, or the supplied {@code BarSeries}) can be reached
 * from a {@code BacktestResult}.
 */
class BacktesterStructureTest {

    @Test
    void backtesterHasNoInstanceFields() {
        Field[] fields = Backtester.class.getDeclaredFields();

        long instanceFields = java.util.Arrays.stream(fields)
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .count();

        assertEquals(0, instanceFields);
    }

    @Test
    void backtesterHasNoStaticMutableFields() {
        for (Field field : Backtester.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        field.getName() + " must be final if static");
            }
        }
    }

    @Test
    void runIsAPrivateStaticFinalNestedClassOwningTheMutableState() throws ClassNotFoundException {
        Class<?> runClass = Class.forName(Backtester.class.getName() + "$Run");

        assertTrue(Modifier.isPrivate(runClass.getModifiers()));
        assertTrue(Modifier.isStatic(runClass.getModifiers()));
        assertTrue(Modifier.isFinal(runClass.getModifiers()));

        boolean hasPortfolioField = java.util.Arrays.stream(runClass.getDeclaredFields())
                .anyMatch(f -> f.getType() == Portfolio.class);
        assertTrue(hasPortfolioField, "Run must own the Portfolio");

        boolean hasPendingOrderField = java.util.Arrays.stream(runClass.getDeclaredFields())
                .anyMatch(f -> f.getType() == Order.class);
        assertTrue(hasPendingOrderField, "Run must own the pending Order field");
    }

    @Test
    void backtestResultLeaksNoMutableRuntimeObject() {
        for (RecordComponent component : BacktestResult.class.getRecordComponents()) {
            Class<?> type = component.getType();
            assertFalse(Portfolio.class.isAssignableFrom(type),
                    "BacktestResult." + component.getName() + " must not hold a Portfolio");
            assertFalse(Order.class.isAssignableFrom(type),
                    "BacktestResult." + component.getName() + " must not hold an Order");
            assertFalse(Indicator.class.isAssignableFrom(type),
                    "BacktestResult." + component.getName() + " must not hold a runtime Indicator");
            assertFalse(BarSeries.class.isAssignableFrom(type),
                    "BacktestResult." + component.getName() + " must not hold a BarSeries");
            assertFalse(Backtester.class.isAssignableFrom(type),
                    "BacktestResult." + component.getName() + " must not hold a Backtester");
        }
    }
}
