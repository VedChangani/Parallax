package in.vedchangani.parallax.engine.portfolio;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the D-22 shape: {@code Portfolio}'s declared instance fields are
 * exactly {@code (BigDecimal, long, BigDecimal, BigDecimal)}, with no
 * static mutable state and no leaked {@code Order}/{@code Fill}/
 * {@code Indicator}/{@code BarSeries}/collection/{@code Position} field.
 * {@code EquityPoint}'s components are exactly {@code (LocalDate,
 * BigDecimal, long, BigDecimal, BigDecimal, BigDecimal)}, with no stored
 * derived value.
 */
class PortfolioStructureTest {

    @Test
    void portfolioDeclaredFieldsAreExactlyCashQuantityCostBasisRealizedPnl() {
        Field[] fields = Portfolio.class.getDeclaredFields();

        assertEquals(4, fields.length);

        int bigDecimalCount = 0;
        int longCount = 0;
        for (Field field : fields) {
            assertFalse(Modifier.isStatic(field.getModifiers()),
                    field.getName() + " must not be static");
            if (field.getType() == BigDecimal.class) {
                bigDecimalCount++;
            } else if (field.getType() == long.class) {
                longCount++;
            } else {
                throw new AssertionError("unexpected field type: " + field.getType());
            }
        }
        assertEquals(3, bigDecimalCount);
        assertEquals(1, longCount);
    }

    @Test
    void portfolioHasNoStaticFields() {
        for (Field field : Portfolio.class.getDeclaredFields()) {
            assertFalse(Modifier.isStatic(field.getModifiers()));
        }
    }

    @Test
    void equityPointComponentsAreDateCashQuantityCostBasisRealizedPnlClose() {
        RecordComponent[] components = EquityPoint.class.getRecordComponents();

        assertEquals(6, components.length);
        assertEquals(LocalDate.class, components[0].getType());
        assertEquals(BigDecimal.class, components[1].getType());
        assertEquals(long.class, components[2].getType());
        assertEquals(BigDecimal.class, components[3].getType());
        assertEquals(BigDecimal.class, components[4].getType());
        assertEquals(BigDecimal.class, components[5].getType());
    }

    @Test
    void equityPointHasNoStoredDerivedOrForeignTypeComponent() {
        for (RecordComponent component : EquityPoint.class.getRecordComponents()) {
            String name = component.getName().toLowerCase();
            assertFalse(name.contains("marketvalue"));
            assertFalse(name.contains("equity"));
            assertFalse(name.contains("unrealized"));
            assertTrue(component.getType() == LocalDate.class
                    || component.getType() == BigDecimal.class
                    || component.getType() == long.class);
        }
    }
}
