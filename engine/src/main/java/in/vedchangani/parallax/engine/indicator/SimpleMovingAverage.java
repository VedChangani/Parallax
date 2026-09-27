package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Simple moving average over the most recent {@code period} closes.
 *
 * <p>Maintains a circular buffer of the last {@code period} closes plus
 * their running sum, so each {@link #update(BigDecimal)} is O(1). The
 * buffer starts small and grows (doubling, capped at {@code period}) only
 * as closes are actually received, so total memory is
 * {@code O(min(period, closes received))} rather than {@code O(period)} —
 * constructing {@code SimpleMovingAverage(Integer.MAX_VALUE)} allocates
 * nothing large up front. Once {@code period} closes have been received
 * the buffer is exactly {@code period}-sized and behaves exactly as a
 * fixed-size circular buffer would from that point on: this is a memory
 * optimization only, with no change to the sum accumulation order, the
 * rolling-window eviction order, or readiness semantics.
 *
 * <p>Not ready until {@code period} closes have been received; ready
 * immediately after the {@code period}-th close.
 */
public final class SimpleMovingAverage implements Indicator {

    private final int period;
    private double[] window;
    private int index;
    private int count;
    private double sum;

    public SimpleMovingAverage(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1, was " + period);
        }
        this.period = period;
        this.window = new double[1];
    }

    @Override
    public void update(BigDecimal close) {
        double value = close.doubleValue();

        if (count < period) {
            ensureCapacity(count + 1);
            window[index] = value;
            sum += value;
            count++;
        } else {
            sum -= window[index];
            window[index] = value;
            sum += value;
        }
        index = (index + 1) % period;
    }

    /**
     * Grows {@code window} (doubling, capped at {@code period}) only when
     * the next write index would fall outside it. Never touches
     * {@code sum}/{@code index}/{@code count} or the values already
     * written — purely a backing-storage resize.
     */
    private void ensureCapacity(int required) {
        if (required > window.length) {
            int grown = Math.min(period, Math.max(required, window.length * 2));
            window = Arrays.copyOf(window, grown);
        }
    }

    @Override
    public boolean isReady() {
        return count >= period;
    }

    @Override
    public double value() {
        if (!isReady()) {
            throw new IllegalStateException("SMA(%d) is not ready: received %d of %d closes"
                    .formatted(period, count, period));
        }
        return sum / period;
    }
}
