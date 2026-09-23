package in.vedchangani.parallax.engine.result;

import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.Order;
import in.vedchangani.parallax.engine.indicator.Indicator;
import in.vedchangani.parallax.engine.portfolio.Portfolio;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the D-24 shapes: {@code BacktestConfig}, {@code Trade} (sealed to
 * exactly {@code Open}/{@code Closed}), and {@code BacktestResult}. None
 * of these types may hold a {@code Portfolio}, {@code Order},
 * {@code BarSeries}, runtime {@code Indicator}, or other mutable runtime
 * state.
 */
class ResultStructureTest {

    private static final Set<Class<?>> FORBIDDEN =
            Set.of(Portfolio.class, Order.class, BarSeries.class, Indicator.class);

    private static void assertNoForbiddenComponents(Class<?> type) {
        for (RecordComponent component : type.getRecordComponents()) {
            for (Class<?> forbidden : FORBIDDEN) {
                assertFalse(forbidden.isAssignableFrom(component.getType()),
                        type.getSimpleName() + "." + component.getName() + " must not hold a " + forbidden);
            }
        }
    }

    @Test
    void backtestConfigComponentsAreExactlyThreeBigDecimalsAndTwoDates() {
        RecordComponent[] components = BacktestConfig.class.getRecordComponents();

        assertEquals(5, components.length);
        assertEquals(BigDecimal.class, components[0].getType());
        assertEquals(BigDecimal.class, components[1].getType());
        assertEquals(BigDecimal.class, components[2].getType());
        assertEquals(LocalDate.class, components[3].getType());
        assertEquals(LocalDate.class, components[4].getType());
    }

    @Test
    void tradePermitsExactlyOpenAndClosed() {
        assertTrue(Trade.class.isSealed());
        assertEquals(Set.of(Trade.Open.class, Trade.Closed.class), Set.of(Trade.class.getPermittedSubclasses()));
    }

    @Test
    void openHasExactlyOneFillComponent() {
        RecordComponent[] components = Trade.Open.class.getRecordComponents();

        assertEquals(1, components.length);
        assertEquals(Fill.class, components[0].getType());
    }

    @Test
    void closedHasExactlyTwoFillComponents() {
        RecordComponent[] components = Trade.Closed.class.getRecordComponents();

        assertEquals(2, components.length);
        assertEquals(Fill.class, components[0].getType());
        assertEquals(Fill.class, components[1].getType());
    }

    @Test
    void backtestResultComponentsAreExactlyAsSpecified() {
        RecordComponent[] components = BacktestResult.class.getRecordComponents();

        assertEquals(7, components.length);
        assertEquals(String.class, components[0].getType());
        assertEquals(StrategyDefinition.class, components[1].getType());
        assertEquals(BacktestConfig.class, components[2].getType());
        assertEquals(Optional.class, components[3].getType());
        assertEquals(List.class, components[4].getType());
        assertEquals(List.class, components[5].getType());
        assertEquals(List.class, components[6].getType());
    }

    @Test
    void noResultTypeHoldsMutableRuntimeState() {
        assertNoForbiddenComponents(BacktestConfig.class);
        assertNoForbiddenComponents(Trade.Open.class);
        assertNoForbiddenComponents(Trade.Closed.class);
        assertNoForbiddenComponents(BacktestResult.class);
    }
}
