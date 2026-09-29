package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;
import java.util.ArrayDeque;

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
