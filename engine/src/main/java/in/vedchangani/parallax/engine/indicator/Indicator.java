package in.vedchangani.parallax.engine.indicator;

import in.vedchangani.parallax.engine.data.Bar;

import java.math.BigDecimal;
import java.util.Objects;

public interface Indicator {

    void update(BigDecimal close);

    default void update(Bar bar) {
        update(bar.close());
    }

    boolean isReady();

    double value();

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
