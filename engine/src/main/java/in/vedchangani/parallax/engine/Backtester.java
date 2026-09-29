package in.vedchangani.parallax.engine;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.Order;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.execution.OrderSide;
import in.vedchangani.parallax.engine.indicator.Indicator;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.portfolio.Portfolio;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Backtester {

    public static final int SEMANTICS_VERSION = 1;

    public BacktestResult run(BarSeries series, StrategyDefinition strategy, BacktestConfig config) {
        Objects.requireNonNull(series, "series must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(config, "config must not be null");

        return new Run(series, strategy, config).execute();
    }

    static long enterQuantity(BigDecimal cash, BigDecimal fraction, BigDecimal close,
                               BigDecimal commission, BigDecimal slippageRate) {
        BigDecimal spendable = cash.multiply(fraction).min(cash.subtract(commission));
        BigDecimal numerator = spendable.subtract(commission);
        if (numerator.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        BigDecimal perShare = close.multiply(BigDecimal.ONE.add(slippageRate));
        return numerator.divideToIntegralValue(perShare).longValueExact();
    }

    private static final class Run {

        private final BarSeries series;
        private final StrategyDefinition strategy;
        private final BacktestConfig config;

        private final Portfolio portfolio;
        private final Map<IndicatorSpec, Indicator> indicators;

        private Order pendingOrder;
        private int nextOrderId = 1;
        private Optional<LocalDate> firstEvaluableDate = Optional.empty();

        private final List<EquityPoint> equityCurve = new ArrayList<>();
        private final List<Fill> fills = new ArrayList<>();
        private final List<OrderRejection> rejections = new ArrayList<>();

        Run(BarSeries series, StrategyDefinition strategy, BacktestConfig config) {
            this.series = series;
            this.strategy = strategy;
            this.config = config;

            boolean hasInRangeBar = series.bars().stream()
                    .anyMatch(bar -> !bar.date().isBefore(config.startDate())
                            && !bar.date().isAfter(config.endDate()));
            if (!hasInRangeBar) {
                throw new IllegalArgumentException(
                        "series has no bar within [%s, %s]".formatted(config.startDate(), config.endDate()));
            }

            this.portfolio = new Portfolio(config.initialCapital());

            this.indicators = new LinkedHashMap<>();
            for (IndicatorSpec spec : strategy.requiredIndicatorSpecs()) {
                indicators.put(spec, Indicator.create(spec));
            }
        }

        BacktestResult execute() {
            LocalDate lastInRangeDate = lastInRangeDate();

            for (Bar bar : series.bars()) {
                LocalDate date = bar.date();

                if (date.isAfter(config.endDate())) {
                    break;
                }

                if (pendingOrder != null) {
                    executeOrder(pendingOrder, bar);
                    pendingOrder = null;
                }

                for (Indicator indicator : indicators.values()) {
                    indicator.update(bar);
                }

                if (date.isBefore(config.startDate())) {
                    continue;
                }

                equityCurve.add(portfolio.markToMarket(date, bar.close()));

                boolean allReady = indicators.values().stream().allMatch(Indicator::isReady);
                if (allReady && firstEvaluableDate.isEmpty()) {
                    firstEvaluableDate = Optional.of(date);
                }

                if (!allReady || date.equals(lastInRangeDate)) {
                    continue;
                }

                if (pendingOrder != null) {
                    throw new IllegalStateException(
                            "pendingOrder must be null before evaluation on " + date);
                }

                IndicatorSnapshot snapshot = buildSnapshot(date, bar.close());
                if (portfolio.isFlat()) {
                    if (strategy.entryCondition().evaluate(snapshot)) {
                        onEnter(snapshot);
                    }
                } else {
                    if (strategy.exitCondition().evaluate(snapshot)) {
                        onExit(snapshot);
                    }
                }
            }

            if (pendingOrder != null) {
                throw new IllegalStateException(
                        "pendingOrder must be null at the end of the run, found " + pendingOrder);
            }

            return new BacktestResult(series.symbol(), strategy, config, firstEvaluableDate,
                    equityCurve, fills, rejections);
        }

        private LocalDate lastInRangeDate() {
            LocalDate last = null;
            for (Bar bar : series.bars()) {
                LocalDate date = bar.date();
                if (!date.isBefore(config.startDate()) && !date.isAfter(config.endDate())) {
                    last = date;
                }
            }
            return last;
        }

        private IndicatorSnapshot buildSnapshot(LocalDate date, BigDecimal close) {
            Map<IndicatorSpec, Double> values = new LinkedHashMap<>();
            for (Map.Entry<IndicatorSpec, Indicator> entry : indicators.entrySet()) {
                values.put(entry.getKey(), entry.getValue().value());
            }
            return new IndicatorSnapshot(date, close, values);
        }

        private void onEnter(IndicatorSnapshot snapshot) {
            SignalEvent signal = new SignalEvent(SignalType.ENTER, snapshot);

            BigDecimal fraction = ((PositionSizing.CashFraction) strategy.positionSizing()).fraction();
            long quantity = enterQuantity(portfolio.cash(), fraction, snapshot.close(),
                    config.commissionPerFill(), config.slippageRate());

            if (quantity <= 0) {
                rejections.add(new OrderRejection.ZeroQuantity(signal));
                return;
            }

            pendingOrder = new Order(nextOrderId, quantity, signal);
            nextOrderId++;
        }

        private void onExit(IndicatorSnapshot snapshot) {
            SignalEvent signal = new SignalEvent(SignalType.EXIT, snapshot);
            pendingOrder = new Order(nextOrderId, portfolio.quantity(), signal);
            nextOrderId++;
        }

        private void executeOrder(Order order, Bar bar) {
            if (order.side() == OrderSide.BUY) {
                executeBuy(order, bar);
            } else {
                executeSell(order, bar);
            }
        }

        private void executeBuy(Order order, Bar bar) {
            BigDecimal commission = config.commissionPerFill();
            BigDecimal fillPrice = bar.open().multiply(BigDecimal.ONE.add(config.slippageRate()));

            BigDecimal requiredCash = BigDecimal.valueOf(order.quantity()).multiply(fillPrice)
                    .add(commission).add(commission);
            BigDecimal availableCash = portfolio.cash();

            if (requiredCash.compareTo(availableCash) > 0) {
                rejections.add(new OrderRejection.InsufficientCash(order.id(), bar.date(), order.quantity(),
                        requiredCash, availableCash, order.signal()));
                return;
            }

            Fill fill = new Fill(order.id(), bar.date(), order.quantity(), bar.open(), fillPrice, commission,
                    order.signal());
            fills.add(fill);
            portfolio.apply(fill);
        }

        private void executeSell(Order order, Bar bar) {
            BigDecimal commission = config.commissionPerFill();
            BigDecimal fillPrice = bar.open().multiply(BigDecimal.ONE.subtract(config.slippageRate()));

            Fill fill = new Fill(order.id(), bar.date(), order.quantity(), bar.open(), fillPrice, commission,
                    order.signal());
            fills.add(fill);
            portfolio.apply(fill);
        }
    }
}
