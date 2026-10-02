package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;

public final class ExponentialMovingAverage implements Indicator {

    private final int period;
    private final double alpha;

    private int seedCount;
    private double seedSum;

    private boolean seeded;
    private double ema;

    public ExponentialMovingAverage(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1, was " + period);
        }
        this.period = period;
        this.alpha = 2.0 / (period + 1.0);
    }

    @Override
    public void update(BigDecimal close) {
        double value = close.doubleValue();

        if (!seeded) {
            seedSum += value;
            seedCount++;

            if (seedCount == period) {
                ema = seedSum / period;
                seeded = true;
            }
        } else {
            ema = alpha * value + (1 - alpha) * ema;
        }
    }

    @Override
    public boolean isReady() {
        return seeded;
    }

    @Override
    public double value() {
        if (!seeded) {
            throw new IllegalStateException(
                    "EMA(%d) is not ready: received %d of %d closes".formatted(period, seedCount, period));
        }
        return ema;
    }
}
