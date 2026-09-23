package in.vedchangani.parallax.engine.indicator;

import java.math.BigDecimal;

/**
 * The runtime calculation for one {@link IndicatorSpec}, for one backtest
 * run. An instance is mutable, single-use, and fed exactly one close at a
 * time; it never receives a {@code BarSeries} or a list of historical
 * prices, so it cannot see beyond what it has already been given.
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
     * Whether {@link #value()} has a meaningful result yet.
     */
    boolean isReady();

    /**
     * The current calculated value.
     *
     * @throws IllegalStateException if {@link #isReady()} is {@code false}
     */
    double value();
}
