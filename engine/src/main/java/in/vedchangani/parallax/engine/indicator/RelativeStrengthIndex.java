package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;

public final class RelativeStrengthIndex implements Indicator {

    private final int period;

    private boolean havePreviousClose;
    private double previousClose;

    private int changeCount;
    private double gainSum;
    private double lossSum;

    private boolean seeded;
    private double averageGain;
    private double averageLoss;

    public RelativeStrengthIndex(int period) {
        if (period < 2) {
            throw new IllegalArgumentException("period must be >= 2, was " + period);
        }
        this.period = period;
    }

    @Override
    public void update(BigDecimal close) {
        double value = close.doubleValue();

        if (!havePreviousClose) {
            previousClose = value;
            havePreviousClose = true;
            return;
        }

        double change = value - previousClose;
        double gain = Math.max(change, 0.0);
        double loss = Math.max(-change, 0.0);
        previousClose = value;

        if (!seeded) {
            gainSum += gain;
            lossSum += loss;
            changeCount++;

            if (changeCount == period) {
                averageGain = gainSum / period;
                averageLoss = lossSum / period;
                seeded = true;
            }
        } else {
            averageGain = (averageGain * (period - 1) + gain) / period;
            averageLoss = (averageLoss * (period - 1) + loss) / period;
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
                    "RSI(%d) is not ready: observed %d of %d price changes".formatted(period, changeCount, period));
        }

        if (averageLoss == 0.0) {
            return averageGain > 0.0 ? 100.0 : 50.0;
        }

        double rs = averageGain / averageLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
}
