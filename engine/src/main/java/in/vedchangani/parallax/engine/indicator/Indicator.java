package in.vedchangani.parallax.engine.indicator;

import in.vedchangani.parallax.engine.data.Bar;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The runtime calculation for one {@link IndicatorSpec}, for one backtest
 * run. An instance is mutable, single-use, and fed exactly one bar (or
 * close) at a time; it never receives a {@code BarSeries} or a list of
 * historical prices, so it cannot see beyond what it has already been given.
 *
 * <p>Before {@link #isReady()} returns {@code true}, {@link #value()} has
 * no meaningful result and must throw {@link IllegalStateException} rather
 * than return a sentinel such as {@code 0} or {@code NaN}. This keeps a
 * caller that forgets to check readiness fail immediately instead of
 * silently computing on a meaningless value.
 */
public interface Indicator {

    /**
     * Feeds the next close price into the calculation, in chronological
     * order. Must be called at most once per bar.
     */
    void update(BigDecimal close);

    /**
     * Feeds the next bar, in chronological order. This is what the
     * backtester calls: once per bar, in place of
     * {@link #update(BigDecimal)}. The default uses only the close, so
     * close-based indicators are unaffected; an indicator that needs
     * high/low (ATR) overrides it.
     */
    default void update(Bar bar) {
        update(bar.close());
    }

    /**
     * Whether {@link #value()} has a meaningful result yet.
     */
    boolean isReady();

    /**
     * The current calculated value.
     *
     * @throws IllegalStateException if {@link #isReady()} is {@code false}
     */
    double value();

    /**
     * Creates a fresh runtime {@code Indicator} for {@code spec}: the
     * only mapping from an immutable {@link IndicatorSpec} definition to
     * a new, independent, per-run instance. Every call returns a new
     * instance; nothing is cached, shared, or held in static state.
     *
     * <p>The {@code switch} is exhaustive over {@link IndicatorType} with
     * no {@code default} branch, so adding a new indicator type without
     * updating this method is a compile error, not a silently missing
     * mapping.
     *
     * @throws NullPointerException if {@code spec} is null
     */
    static Indicator create(IndicatorSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        return switch (spec.type()) {
            case SMA -> new SimpleMovingAverage(spec.period());
            case EMA -> new ExponentialMovingAverage(spec.period());
            case RSI -> new RelativeStrengthIndex(spec.period());
            case ATR -> new AverageTrueRange(spec.period());
            case ROC -> new RateOfChange(spec.period());
        };
    }
}
