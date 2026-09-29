package in.vedchangani.parallax.engine.metrics;

import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.portfolio.Portfolio;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuyAndHoldBenchmarkStructureTest {

    private static final Set<Class<?>> FORBIDDEN =
            Set.of(BacktestResult.class, BarSeries.class, StrategyDefinition.class, Portfolio.class,
                    Backtester.class);

    @Test
    void isARecord() {
        assertTrue(BuyAndHoldBenchmark.class.isRecord());
    }

    @Test
    void componentsAreExactlyInitialCapitalAndEquityCurve() {
        RecordComponent[] components = BuyAndHoldBenchmark.class.getRecordComponents();

        assertEquals(2, components.length);
        assertEquals("initialCapital", components[0].getName());
        assertEquals(BigDecimal.class, components[0].getType());
        assertEquals("equityCurve", components[1].getName());
        assertEquals(List.class, components[1].getType());
    }

    @Test
    void noComponentHoldsAForbiddenType() {
        for (RecordComponent component : BuyAndHoldBenchmark.class.getRecordComponents()) {
            for (Class<?> forbidden : FORBIDDEN) {
                assertFalse(forbidden.isAssignableFrom(component.getType()),
                        "BuyAndHoldBenchmark." + component.getName() + " must not hold a " + forbidden);
            }
        }
    }

    @Test
    void noStaticMutableFields() {
        for (Field field : BuyAndHoldBenchmark.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) && !field.isSynthetic()) {
                assertTrue(Modifier.isFinal(mods), "static field " + field.getName() + " must be final");
            }
        }
    }
}
