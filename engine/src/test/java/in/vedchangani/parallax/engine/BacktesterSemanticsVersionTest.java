package in.vedchangani.parallax.engine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link Backtester#SEMANTICS_VERSION} (D-34): the persisted engine
 * identity a future {@code BacktestRun} row records alongside its inputs.
 * A future change to this value is a deliberate signal that engine
 * behavior changed, not an accidental regression.
 */
class BacktesterSemanticsVersionTest {

    @Test
    void semanticsVersionIsExactlyOne() {
        assertEquals(1, Backtester.SEMANTICS_VERSION);
    }

    @Test
    void semanticsVersionIsAPublicStaticFinalInt() throws NoSuchFieldException {
        Field field = Backtester.class.getField("SEMANTICS_VERSION");
        assertEquals(int.class, field.getType());
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }
}
