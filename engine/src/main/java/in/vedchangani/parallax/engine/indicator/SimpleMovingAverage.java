package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;
import java.util.Arrays;

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
