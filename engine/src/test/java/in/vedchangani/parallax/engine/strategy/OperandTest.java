package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperandTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);
    private static final BigDecimal CLOSE = new BigDecimal("101.50");
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);
    private static final IndicatorSpec SMA_50 = new IndicatorSpec(IndicatorType.SMA, 50);

    private static IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(DATE, CLOSE, Map.of(SMA_20, 100.0, SMA_50, 95.0));
    }

    @Test
    void indicatorRefResolvesCorrectly() {
        Operand.IndicatorRef ref = new Operand.IndicatorRef(SMA_20);

        assertEquals(100.0, ref.resolve(snapshot()));
    }

    @Test
    void separatelyCreatedEqualSpecResolvesCorrectly() {
        Operand.IndicatorRef ref = new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 50));

        assertEquals(95.0, ref.resolve(snapshot()));
    }

    @Test
    void closeResolvesCorrectly() {
        Operand.Close close = new Operand.Close();

        assertEquals(101.5, close.resolve(snapshot()));
    }

    @Test
    void constantResolvesCorrectly() {
        Operand.Constant constant = new Operand.Constant(70.0);

        assertEquals(70.0, constant.resolve(snapshot()));
    }

    @Test
    void missingIndicatorSpecPropagatesSnapshotFailure() {
        IndicatorSpec missing = new IndicatorSpec(IndicatorType.RSI, 14);
        Operand.IndicatorRef ref = new Operand.IndicatorRef(missing);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ref.resolve(snapshot()));
        assertTrue(ex.getMessage().contains(missing.toString()));
    }

    @Test
    void nullSpecRejected() {
        assertThrows(NullPointerException.class, () -> new Operand.IndicatorRef(null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void constantNonFiniteRejected(double nonFinite) {
        assertThrows(IllegalArgumentException.class, () -> new Operand.Constant(nonFinite));
    }

    @Test
    void constantNegativeZeroEqualsPositiveZero() {
        Operand.Constant negativeZero = new Operand.Constant(-0.0);
        Operand.Constant positiveZero = new Operand.Constant(0.0);

        assertEquals(positiveZero, negativeZero);
        assertEquals(positiveZero.hashCode(), negativeZero.hashCode());
        assertEquals(0.0, negativeZero.value());
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(negativeZero.value()));
    }
}
