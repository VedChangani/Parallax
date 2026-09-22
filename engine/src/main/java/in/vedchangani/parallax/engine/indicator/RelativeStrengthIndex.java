package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;

/**
 * Relative Strength Index over {@code period} price changes, using Wilder
 * smoothing.
 *
 * <p>The first close only establishes {@code previousClose}; it is not a
 * price change and is never counted toward warm-up. RSI(period) therefore
 * becomes ready after {@code period + 1} closes: the first close plus
 * {@code period} subsequent changes.
 *
 * <p>The initial average gain and average loss are the simple means of the
 * first {@code period} gains and losses. Every change after that applies
 * Wilder smoothing:
 *
 * <pre>
 * avg = (previousAvg * (period - 1) + current) / period
 * </pre>
 *
 * <p>{@code RSI = 100 - 100 / (1 + avgGain / avgLoss)}, with the approved
 * edge cases: {@code avgLoss = 0} and {@code avgGain > 0} gives 100;
 * {@code avgGain = 0} and {@code avgLoss = 0} gives 50. The case
 * {@code avgGain = 0} and {@code avgLoss > 0} needs no special handling —
 * the formula already yields 0.
 *
 * <p>Only the running gain/loss sums (warm-up) or running averages
 * (after warm-up) are kept, plus the previous close — O(1) memory
 * throughout, with no unbounded history.
 */
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
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1, was " + period);
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
