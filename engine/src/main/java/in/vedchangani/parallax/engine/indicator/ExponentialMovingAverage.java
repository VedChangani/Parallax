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
 * with {@code alpha = 2 / (period + 1)}, computed as
 * {@code 2.0 / (period + 1.0)} so the addition happens in {@code double}
 * arithmetic rather than {@code int} arithmetic — {@code period + 1} as an
 * {@code int} overflows for {@code period == Integer.MAX_VALUE}, silently
 * producing a negative alpha. {@code period + 1.0} promotes {@code period}
 * to {@code double} before the addition, which is exact for every
 * representable {@code int} and so is bit-for-bit identical to the old
 * expression for every period that did not already overflow.
 *
 * <p>During warm-up, only a running sum of the first {@code period} closes
 * is kept to compute the seed — O(1) memory, not O(period): no buffer of
 * the individual warm-up closes is ever allocated. Once seeded, only the
 * current EMA value is retained — O(1) memory thereafter, unchanged.
 */
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
