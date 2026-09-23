package in.vedchangani.parallax.engine.indicator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorSnapshotTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);
    private static final BigDecimal CLOSE = new BigDecimal("101.50");

    private static final IndicatorSpec SMA_5 = new IndicatorSpec(IndicatorType.SMA, 5);
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);
    private static final IndicatorSpec EMA_10 = new IndicatorSpec(IndicatorType.EMA, 10);
    private static final IndicatorSpec RSI_14 = new IndicatorSpec(IndicatorType.RSI, 14);

    private static Map<IndicatorSpec, Double> fixtureValues() {
        Map<IndicatorSpec, Double> values = new LinkedHashMap<>();
        values.put(RSI_14, 60.0);
        values.put(SMA_5, 100.0);
        values.put(EMA_10, 101.0);
        values.put(SMA_20, 95.0);
        return values;
    }

    @Test
    void validSnapshotExposesDateCloseAndValues() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());

        assertEquals(DATE, snapshot.date());
        assertEquals(CLOSE, snapshot.close());
        assertEquals(100.0, snapshot.value(SMA_5));
        assertEquals(95.0, snapshot.value(SMA_20));
        assertEquals(101.0, snapshot.value(EMA_10));
        assertEquals(60.0, snapshot.value(RSI_14));
    }

    @Test
    void lookupDistinguishesSma5AndSma20() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());

        assertNotEquals(snapshot.value(SMA_5), snapshot.value(SMA_20));
        assertEquals(100.0, snapshot.value(SMA_5));
        assertEquals(95.0, snapshot.value(SMA_20));
    }

    @Test
    void equalButSeparatelyCreatedSpecWorksAsLookupKey() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());

        IndicatorSpec separatelyBuilt = new IndicatorSpec(IndicatorType.SMA, 5);
        assertEquals(100.0, snapshot.value(separatelyBuilt));
    }

    @Test
    void missingSpecThrowsIllegalArgumentException() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());

        IndicatorSpec missing = new IndicatorSpec(IndicatorType.RSI, 2);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> snapshot.value(missing));
        assertTrue(ex.getMessage().contains(missing.toString()));
    }

    @Test
    void nullSpecThrowsNullPointerException() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());

        assertThrows(NullPointerException.class, () -> snapshot.value(null));
    }

    @Test
    void emptyValuesMapIsValid() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, Map.of());

        assertTrue(snapshot.values().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> snapshot.value(SMA_5));
    }

    @Test
    void nullDateRejected() {
        assertThrows(NullPointerException.class,
                () -> new IndicatorSnapshot(null, CLOSE, fixtureValues()));
    }

    @Test
    void nullCloseRejected() {
        assertThrows(NullPointerException.class,
                () -> new IndicatorSnapshot(DATE, null, fixtureValues()));
    }

    @Test
    void nullMapRejected() {
        assertThrows(NullPointerException.class,
                () -> new IndicatorSnapshot(DATE, CLOSE, null));
    }

    @Test
    void nullKeyRejected() {
        Map<IndicatorSpec, Double> values = new HashMap<>();
        values.put(null, 1.0);

        assertThrows(NullPointerException.class,
                () -> new IndicatorSnapshot(DATE, CLOSE, values));
    }

    @Test
    void nullValueRejected() {
        Map<IndicatorSpec, Double> values = new HashMap<>();
        values.put(SMA_5, null);

        assertThrows(NullPointerException.class,
                () -> new IndicatorSnapshot(DATE, CLOSE, values));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void nonFiniteValuesRejected(double nonFinite) {
        Map<IndicatorSpec, Double> values = new HashMap<>();
        values.put(SMA_5, nonFinite);

        assertThrows(IllegalArgumentException.class,
                () -> new IndicatorSnapshot(DATE, CLOSE, values));
    }

    @Test
    void defensiveCopyIsIndependentOfCallerMap() {
        Map<IndicatorSpec, Double> source = new HashMap<>();
        source.put(SMA_5, 100.0);

        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, source);
        source.put(SMA_20, 999.0);
        source.put(SMA_5, -1.0);

        assertEquals(100.0, snapshot.value(SMA_5));
        assertThrows(IllegalArgumentException.class, () -> snapshot.value(SMA_20));
    }

    @Test
    void valuesIsUnmodifiable() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());
        Map<IndicatorSpec, Double> values = snapshot.values();

        assertThrows(UnsupportedOperationException.class, () -> values.put(EMA_10, 1.0));
        assertThrows(UnsupportedOperationException.class, () -> values.remove(SMA_5));
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    void updatingTheOriginalRuntimeIndicatorAfterSnapshotCreationCannotChangeTheSnapshot() {
        SimpleMovingAverage sma = new SimpleMovingAverage(2);
        sma.update(new BigDecimal("10"));
        sma.update(new BigDecimal("20"));
        assertTrue(sma.isReady());

        Map<IndicatorSpec, Double> values = new HashMap<>();
        values.put(SMA_5, sma.value());
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, values);
        assertEquals(15.0, snapshot.value(SMA_5));

        sma.update(new BigDecimal("40"));

        assertEquals(15.0, snapshot.value(SMA_5));
    }

    @Test
    void insertionOrderDoesNotAffectEqualityOrHashCode() {
        Map<IndicatorSpec, Double> first = new LinkedHashMap<>();
        first.put(SMA_5, 100.0);
        first.put(SMA_20, 95.0);

        Map<IndicatorSpec, Double> second = new LinkedHashMap<>();
        second.put(SMA_20, 95.0);
        second.put(SMA_5, 100.0);

        IndicatorSnapshot a = new IndicatorSnapshot(DATE, CLOSE, first);
        IndicatorSnapshot b = new IndicatorSnapshot(DATE, CLOSE, second);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void insertionOrderDoesNotAffectToString() {
        Map<IndicatorSpec, Double> first = new LinkedHashMap<>();
        first.put(SMA_5, 100.0);
        first.put(SMA_20, 95.0);

        Map<IndicatorSpec, Double> second = new LinkedHashMap<>();
        second.put(SMA_20, 95.0);
        second.put(SMA_5, 100.0);

        IndicatorSnapshot a = new IndicatorSnapshot(DATE, CLOSE, first);
        IndicatorSnapshot b = new IndicatorSnapshot(DATE, CLOSE, second);

        assertEquals(a.toString(), b.toString());
    }

    @Test
    void canonicalIterationOrderIsDeterministic() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());

        List<IndicatorSpec> expectedOrder = List.of(SMA_5, SMA_20, EMA_10, RSI_14);
        assertEquals(expectedOrder, List.copyOf(snapshot.values().keySet()));
    }

    @Test
    void snapshotsDifferWhenDateDiffers() {
        IndicatorSnapshot a = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());
        IndicatorSnapshot b = new IndicatorSnapshot(DATE.plusDays(1), CLOSE, fixtureValues());

        assertNotEquals(a, b);
    }

    @Test
    void snapshotsDifferWhenCloseDiffers() {
        IndicatorSnapshot a = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());
        IndicatorSnapshot b = new IndicatorSnapshot(DATE, new BigDecimal("102.50"), fixtureValues());

        assertNotEquals(a, b);
    }

    @Test
    void snapshotsDifferWhenOneIndicatorValueDiffers() {
        Map<IndicatorSpec, Double> changed = fixtureValues();
        changed.put(SMA_5, 999.0);

        IndicatorSnapshot a = new IndicatorSnapshot(DATE, CLOSE, fixtureValues());
        IndicatorSnapshot b = new IndicatorSnapshot(DATE, CLOSE, changed);

        assertNotEquals(a, b);
    }

    @Test
    void bigDecimalScaleFollowsNormalRecordEquality() {
        IndicatorSnapshot a = new IndicatorSnapshot(DATE, new BigDecimal("100.0"), Map.of());
        IndicatorSnapshot b = new IndicatorSnapshot(DATE, new BigDecimal("100.00"), Map.of());

        assertFalse(a.close().equals(b.close()));
        assertNotEquals(a, b);
    }

    @Test
    void recordComponentsContainOnlyDateCloseAndMap() {
        RecordComponent[] components = IndicatorSnapshot.class.getRecordComponents();

        assertEquals(3, components.length);
        assertEquals(LocalDate.class, components[0].getType());
        assertEquals(BigDecimal.class, components[1].getType());
        assertEquals(Map.class, components[2].getType());

        for (RecordComponent component : components) {
            assertFalse(Indicator.class.isAssignableFrom(component.getType()));
        }
    }
}
