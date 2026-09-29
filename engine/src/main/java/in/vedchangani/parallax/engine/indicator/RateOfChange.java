package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;
import java.util.ArrayDeque;

/**
 * Rate of change over {@code period} bars, in percentage points:
 *
 * <pre>
 * ROC = ((currentClose / close[period bars ago]) - 1) * 100
 * </pre>
 *
 * <p>Needs the current close plus the close {@code period} bars earlier,
 * so ROC(period) becomes ready after {@code period + 1} closes.
 *
 * <p>Keeps at most {@code period + 1} closes (a sliding window), so memory
 * is {@code O(min(period + 1, closes received))} and constructing
 * {@code RateOfChange(Integer.MAX_VALUE)} allocates nothing large up front.
 */
public final class RateOfChange implements Indicator {

    private final int period;
    private final ArrayDeque<Double> window = new ArrayDeque<>();

    public RateOfChange(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1, was " + period);
        }
        this.period = period;
    }

    @Override
    public void update(BigDecimal close) {
        window.addLast(close.doubleValue());
        // size - 1 > period is size > period + 1 without int overflow.
        if (window.size() - 1 > period) {
            window.removeFirst();
        }
    }

    @Override
    public boolean isReady() {
        return window.size() > period;
    }

    @Override
    public double value() {
        if (!isReady()) {
            throw new IllegalStateException("ROC(%d) is not ready: received %d of %d closes"
                    .formatted(period, window.size(), period + 1L));
        }
        return (window.peekLast() / window.peekFirst() - 1.0) * 100.0;
    }
}
