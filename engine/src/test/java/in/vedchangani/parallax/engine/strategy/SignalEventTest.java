package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.indicator.SimpleMovingAverage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignalEventTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);
    private static final BigDecimal CLOSE = new BigDecimal("101.50");
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);

    private static IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(DATE, CLOSE, Map.of(SMA_20, 100.0));
    }

    @Test
    void validConstructionExposesTypeAndSnapshot() {
        IndicatorSnapshot snapshot = snapshot();
        SignalEvent signal = new SignalEvent(SignalType.ENTER, snapshot);

        assertEquals(SignalType.ENTER, signal.type());
        assertEquals(snapshot, signal.snapshot());
    }

    @Test
    void dateEqualsSnapshotDate() {
        SignalEvent signal = new SignalEvent(SignalType.EXIT, snapshot());

        assertEquals(DATE, signal.date());
    }

    @Test
    void nullTypeRejected() {
        assertThrows(NullPointerException.class, () -> new SignalEvent(null, snapshot()));
    }

    @Test
    void nullSnapshotRejected() {
        assertThrows(NullPointerException.class, () -> new SignalEvent(SignalType.ENTER, null));
    }

    @Test
    void equalSignalsAreEqual() {
        SignalEvent a = new SignalEvent(SignalType.ENTER, snapshot());
        SignalEvent b = new SignalEvent(SignalType.ENTER, snapshot());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differingTypeMakesSignalsUnequal() {
        SignalEvent a = new SignalEvent(SignalType.ENTER, snapshot());
        SignalEvent b = new SignalEvent(SignalType.EXIT, snapshot());

        assertNotEquals(a, b);
    }

    @Test
    void differingSnapshotMakesSignalsUnequal() {
        IndicatorSnapshot other = new IndicatorSnapshot(DATE, CLOSE, Map.of(SMA_20, 999.0));
        SignalEvent a = new SignalEvent(SignalType.ENTER, snapshot());
        SignalEvent b = new SignalEvent(SignalType.ENTER, other);

        assertNotEquals(a, b);
    }

    @Test
    void exactSnapshotIsPreserved() {
        IndicatorSnapshot snapshot = snapshot();
        SignalEvent signal = new SignalEvent(SignalType.ENTER, snapshot);

        assertEquals(100.0, signal.snapshot().value(SMA_20));
    }

    @Test
    void signalTypeValuesIsExactlyEnterAndExit() {
        assertArrayEquals(new SignalType[] {SignalType.ENTER, SignalType.EXIT}, SignalType.values());
    }

    @Test
    void updatingTheOriginalRuntimeIndicatorAfterSignalCreationCannotChangeTheSignal() {
        SimpleMovingAverage sma = new SimpleMovingAverage(2);
        sma.update(new BigDecimal("10"));
        sma.update(new BigDecimal("20"));

        Map<IndicatorSpec, Double> values = new HashMap<>();
        values.put(SMA_20, sma.value());
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, values);
        SignalEvent signal = new SignalEvent(SignalType.ENTER, snapshot);
        assertEquals(15.0, signal.snapshot().value(SMA_20));

        sma.update(new BigDecimal("40"));

        assertEquals(15.0, signal.snapshot().value(SMA_20));
    }
}
