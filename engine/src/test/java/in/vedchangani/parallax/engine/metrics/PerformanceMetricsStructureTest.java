package in.vedchangani.parallax.engine.metrics;

import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.indicator.Indicator;
import in.vedchangani.parallax.engine.portfolio.Portfolio;
import in.vedchangani.parallax.engine.result.BacktestResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the D-26 shape of {@link PerformanceMetrics}: a plain immutable
 * record with exactly the nine approved components, no reference to
 * {@link BacktestResult} or any other mutable runtime object, and exactly
 * the two approved public constants.
 */
class PerformanceMetricsStructureTest {

    private static final Set<Class<?>> FORBIDDEN =
            Set.of(BacktestResult.class, Portfolio.class, BarSeries.class, Indicator.class, Backtester.class);

    @Test
    void isARecord() {
        assertTrue(PerformanceMetrics.class.isRecord());
    }

    @Test
    void componentsAreExactlyTheNineApprovedFieldsInOrder() {
        RecordComponent[] components = PerformanceMetrics.class.getRecordComponents();

        assertEquals(9, components.length);
        assertEquals("totalReturn", components[0].getName());
        assertEquals(double.class, components[0].getType());
        assertEquals("cagr", components[1].getName());
        assertEquals(OptionalDouble.class, components[1].getType());
        assertEquals("volatility", components[2].getName());
        assertEquals(OptionalDouble.class, components[2].getType());
        assertEquals("sharpeRatio", components[3].getName());
        assertEquals(OptionalDouble.class, components[3].getType());
        assertEquals("maxDrawdown", components[4].getName());
        assertEquals(double.class, components[4].getType());
        assertEquals("closedTradeCount", components[5].getName());
        assertEquals(int.class, components[5].getType());
        assertEquals("winRate", components[6].getName());
        assertEquals(OptionalDouble.class, components[6].getType());
        assertEquals("averageWin", components[7].getName());
        assertEquals(OptionalDouble.class, components[7].getType());
        assertEquals("averageLoss", components[8].getName());
        assertEquals(OptionalDouble.class, components[8].getType());
    }

    @Test
    void noComponentHoldsMutableRuntimeStateOrTheSourceResult() {
        for (RecordComponent component : PerformanceMetrics.class.getRecordComponents()) {
            for (Class<?> forbidden : FORBIDDEN) {
                assertFalse(forbidden.isAssignableFrom(component.getType()),
                        "PerformanceMetrics." + component.getName() + " must not hold a " + forbidden);
            }
            assertFalse(Collection.class.isAssignableFrom(component.getType()),
                    "PerformanceMetrics." + component.getName() + " must not be a collection");
            assertFalse(Map.class.isAssignableFrom(component.getType()),
                    "PerformanceMetrics." + component.getName() + " must not be a map");
        }
    }

    @Test
    void publicConstantsAreExactlyTheTwoApprovedValues() throws Exception {
        Field[] fields = PerformanceMetrics.class.getDeclaredFields();

        int publicStaticFinalCount = 0;
        for (Field field : fields) {
            int mods = field.getModifiers();
            if (Modifier.isPublic(mods) && Modifier.isStatic(mods) && Modifier.isFinal(mods)) {
                publicStaticFinalCount++;
            }
        }
        assertEquals(2, publicStaticFinalCount);

        Field tradingDays = PerformanceMetrics.class.getField("TRADING_DAYS_PER_YEAR");
        assertEquals(int.class, tradingDays.getType());
        assertEquals(252, tradingDays.get(null));

        Field daysPerYear = PerformanceMetrics.class.getField("DAYS_PER_YEAR");
        assertEquals(int.class, daysPerYear.getType());
        assertEquals(365, daysPerYear.get(null));
    }

    @Test
    void noStaticMutableFields() {
        for (Field field : PerformanceMetrics.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) && !field.isSynthetic()) {
                assertTrue(Modifier.isFinal(mods), "static field " + field.getName() + " must be final");
            }
        }
    }
}
