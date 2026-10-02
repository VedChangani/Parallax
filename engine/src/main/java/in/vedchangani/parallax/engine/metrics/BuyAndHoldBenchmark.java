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

    public double totalReturn() {
        BigDecimal finalEquity = equityCurve.get(equityCurve.size() - 1).equity();
        return finalEquity.subtract(initialCapital).doubleValue() / initialCapital.doubleValue();
    }
}
