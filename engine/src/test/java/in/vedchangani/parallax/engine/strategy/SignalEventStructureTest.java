package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the D-20 shape: {@code SignalEvent}'s components are exactly a
 * {@code SignalType} and an {@code IndicatorSnapshot} — no separately
 * stored date, price, or runtime state.
 */
class SignalEventStructureTest {

    @Test
    void signalEventComponentsAreSignalTypeAndIndicatorSnapshot() {
        RecordComponent[] components = SignalEvent.class.getRecordComponents();

        assertEquals(2, components.length);
        assertEquals(SignalType.class, components[0].getType());
        assertEquals(IndicatorSnapshot.class, components[1].getType());
    }
}
