package in.vedchangani.parallax.engine.data;

import java.util.List;
import java.util.Objects;

/**
 * A symbol and its chronologically ordered, validated bars.
 *
 * <p>{@code BarSeries} knows nothing about {@code BacktestConfig}, start/end
 * date semantics, strategies, indicators, execution, or data providers. It
 * may contain lookback bars before a requested start date and bars after a
 * requested end date; date-range semantics belong to the backtesting engine,
 * not to this type.
 */
public final class BarSeries {

    private final String symbol;
    private final List<Bar> bars;

    public BarSeries(String symbol, List<Bar> bars) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        Objects.requireNonNull(bars, "bars must not be null");
        if (bars.isEmpty()) {
            throw new IllegalArgumentException("bars must not be empty");
        }

        List<Bar> copy = List.copyOf(bars);

        for (int i = 1; i < copy.size(); i++) {
            var previous = copy.get(i - 1).date();
            var current = copy.get(i).date();
            if (!current.isAfter(previous)) {
                throw new IllegalArgumentException(
                        "bars must have strictly ascending dates; %s is not after %s".formatted(current, previous));
            }
        }

        this.symbol = symbol;
        this.bars = copy;
    }

    public String symbol() {
        return symbol;
    }

    /**
     * Returns the bars in chronological order. The returned list is
     * unmodifiable and is not a copy of the internally stored list.
     */
    public List<Bar> bars() {
        return bars;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BarSeries other)) {
            return false;
        }
        return symbol.equals(other.symbol) && bars.equals(other.bars);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, bars);
    }

    @Override
    public String toString() {
        return "BarSeries[symbol=%s, bars=%d]".formatted(symbol, bars.size());
    }
}
