package in.vedchangani.parallax.engine.result;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.execution.OrderSide;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The complete immutable output of one backtest run. It retains the
 * inputs needed to explain what was run ({@code symbol}, {@code strategy},
 * {@code config}) plus everything produced ({@code equityCurve},
 * {@code fills}, {@code rejections}), but never the supplied
 * {@code BarSeries} itself — dataset identity/provenance is a backend
 * concern (D-15) — and never any mutable runtime object
 * ({@code Portfolio}, a runtime {@code Indicator}, a pending
 * {@code Order}, or the {@code Backtester} that produced this result).
 *
 * <p>{@code trades()} and {@code finalPoint()} are derived, not stored:
 * trades are parsed from {@code fills} on every call, and the final
 * state is simply the last equity point.
 */
public record BacktestResult(String symbol, StrategyDefinition strategy, BacktestConfig config,
                              Optional<LocalDate> firstEvaluableDate, List<EquityPoint> equityCurve,
                              List<Fill> fills, List<OrderRejection> rejections) {

    public BacktestResult {
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(firstEvaluableDate, "firstEvaluableDate must not be null");
        Objects.requireNonNull(equityCurve, "equityCurve must not be null");
        Objects.requireNonNull(fills, "fills must not be null");
        Objects.requireNonNull(rejections, "rejections must not be null");

        equityCurve = List.copyOf(equityCurve);
        fills = List.copyOf(fills);
        rejections = List.copyOf(rejections);

        if (equityCurve.isEmpty()) {
            throw new IllegalArgumentException("equityCurve must not be empty");
        }
        LocalDate previousEquityDate = null;
        for (EquityPoint point : equityCurve) {
            if (previousEquityDate != null && !point.date().isAfter(previousEquityDate)) {
                throw new IllegalArgumentException(
                        "equityCurve dates must be strictly ascending; %s is not after %s"
                                .formatted(point.date(), previousEquityDate));
            }
            if (point.date().isBefore(config.startDate()) || point.date().isAfter(config.endDate())) {
                throw new IllegalArgumentException(
                        "equity point date %s is outside the config range [%s, %s]"
                                .formatted(point.date(), config.startDate(), config.endDate()));
            }
            previousEquityDate = point.date();
        }

        LocalDate previousFillDate = null;
        OrderSide expectedSide = OrderSide.BUY;
        for (Fill fill : fills) {
            if (previousFillDate != null && !fill.date().isAfter(previousFillDate)) {
                throw new IllegalArgumentException(
                        "fill dates must be strictly ascending; %s is not after %s"
                                .formatted(fill.date(), previousFillDate));
            }
            if (fill.side() != expectedSide) {
                throw new IllegalArgumentException(
                        "fills must alternate BUY/SELL starting with BUY; expected %s but found %s at orderId %d"
                                .formatted(expectedSide, fill.side(), fill.orderId()));
            }
            previousFillDate = fill.date();
            expectedSide = (expectedSide == OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY;
        }

        LocalDate previousRejectionDate = null;
        for (OrderRejection rejection : rejections) {
            if (previousRejectionDate != null && rejection.date().isBefore(previousRejectionDate)) {
                throw new IllegalArgumentException(
                        "rejection dates must be non-decreasing; %s is before %s"
                                .formatted(rejection.date(), previousRejectionDate));
            }
            previousRejectionDate = rejection.date();
        }
    }

    /**
     * The trades derived from {@link #fills()}, parsed on every call via
     * {@link Trade#fromFills(List)}. Validation in this constructor
     * (strictly ascending dates, alternating sides starting with BUY)
     * guarantees this can never throw for a successfully constructed
     * result.
     */
    public List<Trade> trades() {
        return Trade.fromFills(fills);
    }

    /**
     * The final portfolio state, equal to the last element of
     * {@link #equityCurve()}. There is no separate stored final-state
     * type.
     */
    public EquityPoint finalPoint() {
        return equityCurve.getLast();
    }
}
