package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;

/**
 * Exponential moving average over {@code period} closes.
 *
 * <p>Not ready until {@code period} closes have been received. On the
 * {@code period}-th close, the EMA is seeded with the simple moving
 * average of those first {@code period} closes — not the first close, not
 * the last close, and not a running recurrence applied from the start.
 * Every close after the seed applies:
 *
 * <pre>ema = alpha * close + (1 - alpha) * previousEma</pre>
 *
 * with {@code alpha = 2 / (period + 1)}.
 *
 * <p>During warm-up, a fixed-size buffer of the first {@code period}
 * closes is kept to compute the seed — O(period) memory. Once seeded, the
 * buffer is discarded and only the current EMA value is retained — O(1)
 * memory thereafter.
 */
public final class ExponentialMovingAverage implements Indicator {

    private final int period;
    private final double alpha;

    private double[] seedBuffer;
    private int seedCount;
    private double seedSum;

    private boolean seeded;
    private double ema;

    public ExponentialMovingAverage(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1, was " + period);
        }
        this.period = period;
        this.alpha = 2.0 / (period + 1);
        this.seedBuffer = new double[period];
    }

    @Override
    public void update(BigDecimal close) {
        double value = close.doubleValue();

        if (!seeded) {
            seedBuffer[seedCount] = value;
            seedSum += value;
            seedCount++;

            if (seedCount == period) {
                ema = seedSum / period;
                seeded = true;
                seedBuffer = null;
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
