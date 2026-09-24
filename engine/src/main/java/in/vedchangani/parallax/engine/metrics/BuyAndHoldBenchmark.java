package in.vedchangani.parallax.engine.metrics;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The V1 passive buy-and-hold benchmark (D-28): "what would the same
 * starting capital have produced by passively holding the asset over the
 * requested backtest period?" This is an independent post-run
 * calculation, not a simulation — it never calls {@link
 * in.vedchangani.parallax.engine.Backtester#run}, never evaluates a
 * {@code StrategyDefinition} or signal, and never uses {@code Portfolio},
 * {@code Order}, {@code Fill}, or the D-23 strategy sizing/reserve
 * machinery. It buys once, at the open of the first in-range bar, and
 * never sells.
 *
 * <p>{@code initialCapital} is carried as its own component because,
 * unlike a strategy's equity curve, this benchmark's first point is
 * already marked to that bar's close — it generally does not equal
 * {@code initialCapital} — so the return base cannot be read off the
 * curve.
 */
public record BuyAndHoldBenchmark(BigDecimal initialCapital, List<EquityPoint> equityCurve) {

    public BuyAndHoldBenchmark {
        Objects.requireNonNull(initialCapital, "initialCapital must not be null");
        if (initialCapital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("initialCapital must be > 0, was " + initialCapital);
        }
        Objects.requireNonNull(equityCurve, "equityCurve must not be null");
        equityCurve = List.copyOf(equityCurve);
        if (equityCurve.isEmpty()) {
            throw new IllegalArgumentException("equityCurve must not be empty");
        }

        EquityPoint reference = equityCurve.get(0);
        BigDecimal expectedTotal = reference.cash().add(reference.costBasis());
        if (expectedTotal.compareTo(initialCapital) != 0) {
            throw new IllegalArgumentException(
                    "cash + costBasis (%s) must equal initialCapital (%s)"
                            .formatted(expectedTotal, initialCapital));
        }

        LocalDate previousDate = null;
        for (EquityPoint point : equityCurve) {
            if (previousDate != null && !point.date().isAfter(previousDate)) {
                throw new IllegalArgumentException(
                        "equityCurve dates must be strictly ascending; %s is not after %s"
                                .formatted(point.date(), previousDate));
            }
            previousDate = point.date();

            if (point.cash().compareTo(reference.cash()) != 0) {
                throw new IllegalArgumentException(
                        "every point must share the same cash (%s), found %s on %s"
                                .formatted(reference.cash(), point.cash(), point.date()));
            }
            if (point.quantity() != reference.quantity()) {
                throw new IllegalArgumentException(
                        "every point must share the same quantity (%d), found %d on %s"
                                .formatted(reference.quantity(), point.quantity(), point.date()));
            }
            if (point.costBasis().compareTo(reference.costBasis()) != 0) {
                throw new IllegalArgumentException(
                        "every point must share the same costBasis (%s), found %s on %s"
                                .formatted(reference.costBasis(), point.costBasis(), point.date()));
            }
            if (point.realizedPnl().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException(
                        "realizedPnl must be zero (the benchmark never sells), found %s on %s"
                                .formatted(point.realizedPnl(), point.date()));
            }
        }
    }

    /**
     * Builds the benchmark from {@code series}'s bar opens and
     * {@code result}'s config and in-range dates/closes.
     *
     * <p>Entry: one BUY at the open of the first in-range bar,
     * {@code fillPrice = open × (1 + slippageRate)}, whole shares sized by
     * {@code q = floor((initialCapital - commission) / fillPrice)}
     * (exact via {@link BigDecimal#divideToIntegralValue}), paying one
     * commission. There is no rejection path: the BUY is always
     * affordable by construction. If {@code initialCapital - commission
     * <= 0} or the fill price leaves {@code q == 0}, no trade occurs and
     * no commission is charged. The benchmark never sells: no exit
     * transaction, no exit commission or slippage, no D-23 reserve, and
     * residual cash earns nothing. Slippage is represented only through
     * the changed fill price, never as a separate cash flow.
     *
     * <p>Only {@code result.symbol()}, {@code result.config()} and
     * {@code result.equityCurve()} are read — never {@code strategy()},
     * {@code fills()}, {@code rejections()}, {@code trades()}, or
     * {@code firstEvaluableDate()} — so two different strategies over the
     * same series and config produce identical benchmarks.
     *
     * @throws NullPointerException     if {@code series} or {@code result} is null
     * @throws IllegalArgumentException if {@code series.symbol()} does not
     *                                   equal {@code result.symbol()}, or
     *                                   if {@code series}'s in-range bars
     *                                   (by {@code result.config()}'s
     *                                   date range) do not have exactly
     *                                   the same dates and closes, in
     *                                   order, as {@code
     *                                   result.equityCurve()}
     */
    public static BuyAndHoldBenchmark of(BarSeries series, BacktestResult result) {
        Objects.requireNonNull(series, "series must not be null");
        Objects.requireNonNull(result, "result must not be null");

        if (!series.symbol().equals(result.symbol())) {
            throw new IllegalArgumentException(
                    "series symbol (%s) must equal result symbol (%s)"
                            .formatted(series.symbol(), result.symbol()));
        }

        BacktestConfig config = result.config();
        List<Bar> inRangeBars = new ArrayList<>();
        for (Bar bar : series.bars()) {
            LocalDate date = bar.date();
            if (!date.isBefore(config.startDate()) && !date.isAfter(config.endDate())) {
                inRangeBars.add(bar);
            }
        }

        List<EquityPoint> resultCurve = result.equityCurve();
        if (inRangeBars.size() != resultCurve.size()) {
            throw new IllegalArgumentException(
                    "series has %d in-range bar(s) but result has %d equity point(s)"
                            .formatted(inRangeBars.size(), resultCurve.size()));
        }
        for (int i = 0; i < inRangeBars.size(); i++) {
            Bar bar = inRangeBars.get(i);
            EquityPoint point = resultCurve.get(i);
            if (!bar.date().equals(point.date())) {
                throw new IllegalArgumentException(
                        "in-range bar date %s at index %d does not match equity point date %s"
                                .formatted(bar.date(), i, point.date()));
            }
            if (bar.close().compareTo(point.close()) != 0) {
                throw new IllegalArgumentException(
                        "in-range bar close %s on %s does not match equity point close %s"
                                .formatted(bar.close(), bar.date(), point.close()));
            }
        }

        BigDecimal capital = config.initialCapital();
        BigDecimal commission = config.commissionPerFill();
        BigDecimal slippageRate = config.slippageRate();

        Bar entryBar = inRangeBars.get(0);
        BigDecimal fillPrice = entryBar.open().multiply(BigDecimal.ONE.add(slippageRate));

        long quantity;
        BigDecimal cash;
        BigDecimal costBasis;

        BigDecimal spendable = capital.subtract(commission);
        if (spendable.compareTo(BigDecimal.ZERO) <= 0) {
            quantity = 0L;
        } else {
            quantity = spendable.divideToIntegralValue(fillPrice).longValueExact();
        }

        if (quantity > 0) {
            costBasis = fillPrice.multiply(BigDecimal.valueOf(quantity)).add(commission);
            cash = capital.subtract(costBasis);
        } else {
            costBasis = BigDecimal.ZERO;
            cash = capital;
        }

        List<EquityPoint> curve = new ArrayList<>(inRangeBars.size());
        for (Bar bar : inRangeBars) {
            curve.add(new EquityPoint(bar.date(), cash, quantity, costBasis, BigDecimal.ZERO, bar.close()));
        }

        return new BuyAndHoldBenchmark(capital, curve);
    }

    /**
     * {@code (finalEquity - initialCapital) / initialCapital}, the same
     * D-26 total-return convention: an exact {@code BigDecimal}
     * subtraction, converted to {@code double} only at this statistical
     * boundary.
     */
    public double totalReturn() {
        BigDecimal finalEquity = equityCurve.get(equityCurve.size() - 1).equity();
        return finalEquity.subtract(initialCapital).doubleValue() / initialCapital.doubleValue();
    }
}
