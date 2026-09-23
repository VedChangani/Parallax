package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;

/**
 * Simple moving average over the most recent {@code period} closes.
 *
 * <p>Maintains a fixed-size circular buffer of the last {@code period}
 * closes plus their running sum, so each {@link #update(BigDecimal)} is
 * O(1) and total memory is O(period) — no unbounded history is kept.
 *
 * <p>Not ready until {@code period} closes have been received; ready
 * immediately after the {@code period}-th close.
 */
public final class SimpleMovingAverage implements Indicator {

    private final int period;
    private final double[] window;
    private int index;
    private int count;
    private double sum;

    public SimpleMovingAverage(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1, was " + period);
        }
        this.period = period;
        this.window = new double[period];
    }

    @Override
    public void update(BigDecimal close) {
        double value = close.doubleValue();

        if (count < period) {
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
