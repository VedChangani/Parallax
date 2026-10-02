package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompareTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);
    private static final BigDecimal CLOSE = new BigDecimal("101.50");
    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);
    private static final IndicatorSpec SMA_50 = new IndicatorSpec(IndicatorType.SMA, 50);

    private static IndicatorSnapshot snapshot(double sma20, double sma50) {
        return new IndicatorSnapshot(DATE, CLOSE, Map.of(SMA_20, sma20, SMA_50, sma50));
    }

    private static final Operand REF_20 = new Operand.IndicatorRef(SMA_20);
    private static final Operand REF_50 = new Operand.IndicatorRef(SMA_50);

    @Test
    void gtTrue() {
        Condition.Compare compare = new Condition.Compare(REF_20, Operator.GT, REF_50);

        assertTrue(compare.evaluate(snapshot(100.0, 95.0)));
    }

    @Test
    void gtFalse() {
        Condition.Compare compare = new Condition.Compare(REF_20, Operator.GT, REF_50);

        assertFalse(compare.evaluate(snapshot(90.0, 95.0)));
    }

    @Test
    void ltTrue() {
        Condition.Compare compare = new Condition.Compare(REF_20, Operator.LT, REF_50);

        assertTrue(compare.evaluate(snapshot(90.0, 95.0)));
    }

    @Test
    void ltFalse() {
        Condition.Compare compare = new Condition.Compare(REF_20, Operator.LT, REF_50);

        assertFalse(compare.evaluate(snapshot(100.0, 95.0)));
    }

    @Test
    void equalValuesGtFalse() {
        Condition.Compare compare = new Condition.Compare(REF_20, Operator.GT, REF_50);

        assertFalse(compare.evaluate(snapshot(95.0, 95.0)));
    }

    @Test
    void equalValuesLtFalse() {
        Condition.Compare compare = new Condition.Compare(REF_20, Operator.LT, REF_50);

        assertFalse(compare.evaluate(snapshot(95.0, 95.0)));
    }

    @Test
    void indicatorVsIndicator() {
        Condition.Compare compare = new Condition.Compare(REF_20, Operator.GT, REF_50);

        assertTrue(compare.evaluate(snapshot(100.0, 95.0)));
    }

    @Test
    void indicatorVsConstant() {
        Condition.Compare compare = new Condition.Compare(REF_20, Operator.LT, new Operand.Constant(70.0));

        assertTrue(compare.evaluate(snapshot(60.0, 95.0)));
    }

    @Test
    void constantVsIndicator() {
        Condition.Compare compare = new Condition.Compare(new Operand.Constant(70.0), Operator.GT, REF_20);

        assertTrue(compare.evaluate(snapshot(60.0, 95.0)));
    }

    @Test
    void closeVsConstant() {
        Condition.Compare compare = new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(100.0));

        assertTrue(compare.evaluate(snapshot(0.0, 0.0)));
    }

    @Test
    void constantVsClose() {
        Condition.Compare compare = new Condition.Compare(new Operand.Constant(100.0), Operator.LT, new Operand.Close());

        assertTrue(compare.evaluate(snapshot(0.0, 0.0)));
    }

    @Test
    void closeAndConstantWithTheSameDecimalValueAreEqualAsDoubles() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(DATE, new BigDecimal("100.10"), Map.of());
        Operand.Constant threshold = new Operand.Constant(100.1);

        assertFalse(new Condition.Compare(new Operand.Close(), Operator.GT, threshold).evaluate(snapshot));
        assertFalse(new Condition.Compare(new Operand.Close(), Operator.LT, threshold).evaluate(snapshot));
    }

    @Test
    void nullSnapshotRejectedEvenWhenNoOperandReadsIt() {
        Condition.Compare compare =
                new Condition.Compare(new Operand.Constant(1.0), Operator.GT, new Operand.Constant(0.0));

        assertThrows(NullPointerException.class, () -> compare.evaluate(null));
    }

    @Test
    void constantVsConstant() {
        Condition.Compare compare =
                new Condition.Compare(new Operand.Constant(1.0), Operator.LT, new Operand.Constant(2.0));

        assertTrue(compare.evaluate(snapshot(0.0, 0.0)));
    }

    @Test
    void identicalOperands() {
        Condition.Compare gt = new Condition.Compare(REF_20, Operator.GT, REF_20);
        Condition.Compare lt = new Condition.Compare(REF_20, Operator.LT, REF_20);

        assertFalse(gt.evaluate(snapshot(100.0, 95.0)));
        assertFalse(lt.evaluate(snapshot(100.0, 95.0)));
    }

    @Test
    void nullLeftRejected() {
        assertThrows(NullPointerException.class, () -> new Condition.Compare(null, Operator.GT, REF_50));
    }

    @Test
    void nullOperatorRejected() {
        assertThrows(NullPointerException.class, () -> new Condition.Compare(REF_20, null, REF_50));
    }

    @Test
    void nullRightRejected() {
        assertThrows(NullPointerException.class, () -> new Condition.Compare(REF_20, Operator.GT, null));
    }
}
