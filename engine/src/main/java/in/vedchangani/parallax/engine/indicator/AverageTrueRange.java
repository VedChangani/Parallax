package in.vedchangani.parallax.engine.indicator;

import in.vedchangani.parallax.engine.data.Bar;

import java.math.BigDecimal;

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
