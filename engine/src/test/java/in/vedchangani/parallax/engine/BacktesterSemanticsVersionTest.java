package in.vedchangani.parallax.engine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
