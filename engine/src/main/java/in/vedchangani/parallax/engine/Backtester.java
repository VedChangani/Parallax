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

/**
 * The V1 chronological run loop (D-25): {@code run(...)} processes a
 * validated {@link BarSeries} bar by bar and produces an immutable
 * {@link BacktestResult}. This class is stateless and holds no fields of
 * its own — every mutable per-run value lives in a fresh, private
 * {@link Run} instance that is discarded once the run completes.
 *
 * <p>No look-ahead: a bar's own values are read only at that bar's own
 * step, execution always uses the <em>next</em> bar's open, and no data
 * from a later bar is ever consulted early.
 */
public final class Backtester {

    /**
     * Runs one backtest.
     *
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if {@code series} has no bar within
     *                                   {@code [config.startDate(),
     *                                   config.endDate()]}
     */
    public BacktestResult run(BarSeries series, StrategyDefinition strategy, BacktestConfig config) {
        Objects.requireNonNull(series, "series must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(config, "config must not be null");

        return new Run(series, strategy, config).execute();
    }

    /**
     * The D-23 ENTER sizing arithmetic, as a pure function: reserves one
     * {@code commission} in cash for the eventual exit, then sizes the
     * whole-share BUY from what remains. Whole-share truncation uses
     * {@link BigDecimal#divideToIntegralValue}, which is exact — no
     * {@link java.math.MathContext} and no rounding mode are needed.
     *
     * @throws ArithmeticException if the resulting quantity does not fit
     *                              in a {@code long}
     */
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

    /**
     * Owns every piece of mutable state for exactly one run. Never
     * shared, never reused, and discarded once {@link #execute()}
     * returns.
     */
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

            // LinkedHashMap over requiredIndicatorSpecs()'s canonical order:
            // deterministic construction and iteration order, one instance
            // per distinct spec.
            this.indicators = new LinkedHashMap<>();
            for (IndicatorSpec spec : strategy.requiredIndicatorSpecs()) {
                indicators.put(spec, Indicator.create(spec));
            }
        }

        BacktestResult execute() {
            LocalDate lastInRangeDate = lastInRangeDate();

            for (Bar bar : series.bars()) {
                LocalDate date = bar.date();

                // 0. Bars after endDate are never processed.
                if (date.isAfter(config.endDate())) {
                    break;
                }

                // 1. Execute any pending order at this bar's open, before
                //    anything else touches this bar.
                if (pendingOrder != null) {
                    executeOrder(pendingOrder, bar);
                    pendingOrder = null;
                }

                // 2. Update every indicator with this bar's close —
                //    lookback and in-range bars alike.
                for (Indicator indicator : indicators.values()) {
                    indicator.update(bar.close());
                }

                // 3. Lookback bars stop here: no equity, no evaluation.
                if (date.isBefore(config.startDate())) {
                    continue;
                }

                // 4. Record this bar's equity point: after any open-time
                //    execution, marked to this bar's close.
                equityCurve.add(portfolio.markToMarket(date, bar.close()));

                // 5. Readiness, and firstEvaluableDate — independent of
                //    whether evaluation happens on this bar.
                boolean allReady = indicators.values().stream().allMatch(Indicator::isReady);
                if (allReady && firstEvaluableDate.isEmpty()) {
                    firstEvaluableDate = Optional.of(date);
                }

                // 6. Not evaluated: indicators not ready, or the last
                //    in-range bar (D-8 — no signal may originate there).
                if (!allReady || date.equals(lastInRangeDate)) {
                    continue;
                }

                // 7. Invariant: a bar this far into the loop must never
                //    still have a pending order (every evaluated bar has
                //    a following in-range bar that consumes it in step 1).
                if (pendingOrder != null) {
                    throw new IllegalStateException(
                            "pendingOrder must be null before evaluation on " + date);
                }

                // 8-9. Build the snapshot and evaluate exactly one
                //    condition, chosen by position state.
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

        // --- ENTER / EXIT: bar N close -----------------------------------

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

        // --- execution: bar N+1 open -------------------------------------

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

            // D-23: the actual entry commission, plus one reserved exit
            // commission — the reserve is never charged to Portfolio.
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

            // No affordability check: D-23 proves a SELL following a
            // D-23-sized BUY can never leave cash negative. Portfolio's
            // own guard remains only as a defensive backstop — if it
            // fires, that is an engine bug and must surface, not be
            // silently turned into a rejection.
            Fill fill = new Fill(order.id(), bar.date(), order.quantity(), bar.open(), fillPrice, commission,
                    order.signal());
            fills.add(fill);
            portfolio.apply(fill);
        }
    }
}
