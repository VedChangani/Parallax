package in.vedchangani.parallax.engine.indicator;

import in.vedchangani.parallax.engine.data.Bar;

import java.math.BigDecimal;

/**
 * Average True Range over {@code period} bars, using Wilder smoothing.
 *
 * <p>{@code TR = max(high - low, |high - previousClose|, |low - previousClose|)}.
 * The first bar has no previous close, so its true range is
 * {@code high - low}. Every bar contributes one true range; ATR(period)
 * therefore becomes ready after {@code period} bars.
 *
 * <p>The initial ATR is the simple mean of the first {@code period} true
 * ranges. Every bar after that applies Wilder smoothing (the same recurrence
 * as {@link RelativeStrengthIndex}):
 *
 * <pre>
 * atr = (previousAtr * (period - 1) + tr) / period
 * </pre>
 *
 * <p>ATR needs high, low and close, so it is fed through
 * {@link #update(Bar)}; {@link #update(BigDecimal)} is unsupported.
 *
 * <p>Only the running TR sum (warm-up) or running ATR (after warm-up) is
 * kept, plus the previous close — O(1) memory throughout.
 */
public final class AverageTrueRange implements Indicator {

    private final int period;

    private boolean havePreviousClose;
    private double previousClose;

    private int observations;
    private double trSum;

    private boolean seeded;
    private double atr;

    public AverageTrueRange(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1, was " + period);
        }
        this.period = period;
    }

    @Override
    public void update(Bar bar) {
        double high = bar.high().doubleValue();
        double low = bar.low().doubleValue();
        double close = bar.close().doubleValue();

        double trueRange = high - low;
        if (havePreviousClose) {
            trueRange = Math.max(trueRange, Math.max(Math.abs(high - previousClose), Math.abs(low - previousClose)));
        }
        previousClose = close;
        havePreviousClose = true;

        if (!seeded) {
            trSum += trueRange;
            observations++;

            if (observations == period) {
                atr = trSum / period;
                seeded = true;
            }
        } else {
            atr = (atr * (period - 1) + trueRange) / period;
        }
    }

    /**
     * Unsupported: ATR cannot be computed from a close alone.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void update(BigDecimal close) {
        throw new UnsupportedOperationException("ATR requires high, low and close; use update(Bar)");
    }

    @Override
    public boolean isReady() {
        return seeded;
    }

    @Override
    public double value() {
        if (!seeded) {
            throw new IllegalStateException(
                    "ATR(%d) is not ready: observed %d of %d true ranges".formatted(period, observations, period));
        }
        return atr;
    }
}
