package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.execution.OrderSide;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.metrics.BuyAndHoldBenchmark;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.portfolio.Portfolio;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class BacktestResultReconstructor {

    private BacktestResultReconstructor() {
    }

    static BacktestRunDetail reconstruct(BacktestRun run, StrategyDefinition strategyDefinition, String datasetSymbol,
                                          List<BacktestEquityPointRow> equityRows, List<BacktestFillRow> fillRows,
                                          List<BacktestRejectionRow> rejectionRows) {
        Objects.requireNonNull(run, "run must not be null");
        Objects.requireNonNull(strategyDefinition, "strategyDefinition must not be null");
        Objects.requireNonNull(datasetSymbol, "datasetSymbol must not be null");
        Objects.requireNonNull(equityRows, "equityRows must not be null");
        Objects.requireNonNull(fillRows, "fillRows must not be null");
        Objects.requireNonNull(rejectionRows, "rejectionRows must not be null");

        try {
            if (run.engineSemanticsVersion() != Backtester.SEMANTICS_VERSION) {
                throw new IllegalArgumentException(
                        "unsupported engine semantics version: " + run.engineSemanticsVersion());
            }

            BacktestConfig config = reconstructConfig(run);
            List<EquityPoint> equityCurve = reconstructEquityCurve(equityRows);
            List<Fill> fills = reconstructFills(fillRows);
            List<OrderRejection> rejections = reconstructRejections(rejectionRows);

            BacktestResult result = new BacktestResult(datasetSymbol, strategyDefinition, config,
                    run.firstEvaluableDate(), equityCurve, fills, rejections);

            verifyOrderIdContinuity(fills, rejections);
            verifyFirstEvaluableDateSemantics(result);

            verifySnapshotIndicatorSpecs(strategyDefinition, fills, rejections);
            verifySignalCloseMatchesEquity(equityCurve, fills, rejections);
            verifyConditionTruth(strategyDefinition, fills, rejections);

            verifyFillsMatchConfig(fills, config);
            verifyFillCausalTiming(equityCurve, fills);
            verifyInsufficientCashExecutionTiming(equityCurve, rejections);
            replayLedger(config, equityCurve, fills);
            verifyInsufficientCashAvailableCash(config, fills, rejections);
            verifyPositionStateAtSignal(config, fills, rejections);

            PerformanceMetrics recomputedMetrics = PerformanceMetrics.of(result);
            PerformanceMetrics storedMetrics = reconstructMetrics(run, recomputedMetrics);
            verifyMetricsMatch(storedMetrics, recomputedMetrics);

            verifyCostTotals(run, result);
            verifyBenchmark(run, result);

            return new BacktestRunDetail(BacktestRunSummary.of(run), result.config(), result.firstEvaluableDate(),
                    result.equityCurve(), result.fills(), result.rejections(), recomputedMetrics,
                    result.totalCommission(), result.totalSlippageCost(), run.benchmarkCash(),
                    run.benchmarkQuantity(), run.benchmarkCostBasis(), run.benchmarkTotalReturn());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BacktestResultIntegrityException(
                    "stored backtest run " + run.id() + " failed integrity verification: " + e.getMessage(), e);
        }
    }

    private static BacktestConfig reconstructConfig(BacktestRun run) {
        BacktestConfig config = new BacktestConfig(run.initialCapital(), run.commissionPerFill(),
                run.slippageRate(), run.startDate(), run.endDate());
        run.firstEvaluableDate().ifPresent(date -> {
            if (date.isBefore(config.startDate()) || date.isAfter(config.endDate())) {
                throw new IllegalArgumentException(
                        "firstEvaluableDate (%s) is outside the run's range [%s, %s]"
                                .formatted(date, config.startDate(), config.endDate()));
            }
        });
        return config;
    }

    private static List<EquityPoint> reconstructEquityCurve(List<BacktestEquityPointRow> rows) {
        List<EquityPoint> points = new ArrayList<>(rows.size());
        for (BacktestEquityPointRow row : rows) {
            points.add(new EquityPoint(row.barDate(), row.cash(), row.quantity(), row.costBasis(),
                    row.realizedPnl(), row.close()));
        }
        return List.copyOf(points);
    }

    private static List<Fill> reconstructFills(List<BacktestFillRow> rows) {
        List<Fill> fills = new ArrayList<>(rows.size());
        for (BacktestFillRow row : rows) {
            SignalType signalType;
            try {
                signalType = SignalType.valueOf(row.signalType());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown fill signal type: " + row.signalType(), e);
            }
            IndicatorSnapshot snapshot =
                    IndicatorSnapshotJson.read(row.signalIndicatorsJson(), row.signalDate(), row.signalClose());
            SignalEvent signal = new SignalEvent(signalType, snapshot);
            fills.add(new Fill(row.orderId(), row.fillDate(), row.quantity(), row.referenceOpen(), row.fillPrice(),
                    row.commission(), signal));
        }
        return List.copyOf(fills);
    }

    private static List<OrderRejection> reconstructRejections(List<BacktestRejectionRow> rows) {
        List<OrderRejection> rejections = new ArrayList<>(rows.size());
        int expectedSeq = 1;
        for (BacktestRejectionRow row : rows) {
            if (row.seq() != expectedSeq) {
                throw new IllegalArgumentException(
                        "rejection seq must preserve append order starting at 1; expected %d but found %d"
                                .formatted(expectedSeq, row.seq()));
            }
            expectedSeq++;

            IndicatorSnapshot snapshot =
                    IndicatorSnapshotJson.read(row.signalIndicatorsJson(), row.signalDate(), row.signalClose());
            SignalEvent signal = new SignalEvent(SignalType.ENTER, snapshot);

            OrderRejection rejection = switch (row.reason()) {
                case "ZERO_QUANTITY" -> new OrderRejection.ZeroQuantity(signal);
                case "INSUFFICIENT_CASH" -> new OrderRejection.InsufficientCash(
                        requireField(row.orderId(), "orderId"),
                        requireField(row.executionDate(), "executionDate"),
                        requireField(row.quantity(), "quantity"),
                        requireField(row.requiredCash(), "requiredCash"),
                        requireField(row.availableCash(), "availableCash"),
                        signal);
                default -> throw new IllegalArgumentException("unknown rejection reason: " + row.reason());
            };

            rejections.add(rejection);
        }
        return List.copyOf(rejections);
    }

    private static <T> T requireField(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null for an INSUFFICIENT_CASH rejection");
        }
        return value;
    }

    private static PerformanceMetrics reconstructMetrics(BacktestRun run, PerformanceMetrics recomputed) {
        return new PerformanceMetrics(run.totalReturn(), run.cagr(), run.volatility(), run.sharpeRatio(),
                run.maxDrawdown(), run.closedTradeCount(), run.winRate(), run.averageWin(), run.averageLoss(),
                recomputed.profitFactor());
    }

    private static void verifyOrderIdContinuity(List<Fill> fills, List<OrderRejection> rejections) {
        int expectedCount = fills.size();
        for (OrderRejection rejection : rejections) {
            if (rejection instanceof OrderRejection.InsufficientCash) {
                expectedCount++;
            }
        }
        boolean[] consumed = new boolean[expectedCount + 1];
        for (Fill fill : fills) {
            markOrderIdConsumed(consumed, fill.orderId(), expectedCount);
        }
        for (OrderRejection rejection : rejections) {
            if (rejection instanceof OrderRejection.InsufficientCash insufficientCash) {
                markOrderIdConsumed(consumed, insufficientCash.orderId(), expectedCount);
            }
        }
        for (int id = 1; id <= expectedCount; id++) {
            if (!consumed[id]) {
                throw new IllegalArgumentException(
                        "order id %d was never consumed by any fill or InsufficientCash rejection, out of %d expected"
                                .formatted(id, expectedCount));
            }
        }
    }

    private static void markOrderIdConsumed(boolean[] consumed, int orderId, int expectedCount) {
        if (orderId < 1 || orderId > expectedCount) {
            throw new IllegalArgumentException(
                    "order id %d is outside the expected range [1, %d]".formatted(orderId, expectedCount));
        }
        if (consumed[orderId]) {
            throw new IllegalArgumentException("order id " + orderId + " is consumed more than once");
        }
        consumed[orderId] = true;
    }

    private static void verifyFirstEvaluableDateSemantics(BacktestResult result) {
        Optional<LocalDate> firstEvaluableDate = result.firstEvaluableDate();
        boolean hasAnySignal = !result.fills().isEmpty() || !result.rejections().isEmpty();

        if (firstEvaluableDate.isEmpty()) {
            if (hasAnySignal) {
                throw new IllegalArgumentException(
                        "firstEvaluableDate is empty, but the result has fills/rejections - "
                                + "no signal can exist before indicators are ready");
            }
            return;
        }

        LocalDate date = firstEvaluableDate.get();
        boolean isEquityCurveDate = result.equityCurve().stream().anyMatch(point -> point.date().equals(date));
        if (!isEquityCurveDate) {
            throw new IllegalArgumentException(
                    "firstEvaluableDate (%s) does not correspond to any date in the equity curve".formatted(date));
        }

        for (Fill fill : result.fills()) {
            if (fill.signal().date().isBefore(date)) {
                throw new IllegalArgumentException(
                        "fill signal date (%s) is before firstEvaluableDate (%s)"
                                .formatted(fill.signal().date(), date));
            }
        }
        for (OrderRejection rejection : result.rejections()) {
            if (rejection.signal().date().isBefore(date)) {
                throw new IllegalArgumentException(
                        "rejection signal date (%s) is before firstEvaluableDate (%s)"
                                .formatted(rejection.signal().date(), date));
            }
        }
    }

    private static void verifySnapshotIndicatorSpecs(StrategyDefinition strategyDefinition, List<Fill> fills,
                                                       List<OrderRejection> rejections) {
        List<IndicatorSpec> expected = strategyDefinition.requiredIndicatorSpecs();
        for (Fill fill : fills) {
            verifySnapshotSpecs(expected, fill.signal().snapshot(), "fill " + fill.orderId());
        }
        for (OrderRejection rejection : rejections) {
            verifySnapshotSpecs(expected, rejection.signal().snapshot(), "rejection at " + rejection.date());
        }
    }

    private static void verifySnapshotSpecs(List<IndicatorSpec> expected, IndicatorSnapshot snapshot, String label) {
        List<IndicatorSpec> actual = List.copyOf(snapshot.values().keySet());
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    (label + " signal snapshot indicator specs (%s) do not match the strategy's own "
                            + "requiredIndicatorSpecs() (%s)").formatted(actual, expected));
        }
    }

    private static void verifySignalCloseMatchesEquity(List<EquityPoint> equityCurve, List<Fill> fills,
                                                         List<OrderRejection> rejections) {
        Map<LocalDate, BigDecimal> closeByDate = new HashMap<>();
        for (EquityPoint point : equityCurve) {
            closeByDate.put(point.date(), point.close());
        }
        for (Fill fill : fills) {
            verifySignalClose(closeByDate, fill.signal(), "fill " + fill.orderId());
        }
        for (OrderRejection rejection : rejections) {
            verifySignalClose(closeByDate, rejection.signal(), "rejection at " + rejection.date());
        }
    }

    private static void verifySignalClose(Map<LocalDate, BigDecimal> closeByDate, SignalEvent signal, String label) {
        BigDecimal expected = closeByDate.get(signal.date());
        if (expected == null) {
            throw new IllegalArgumentException(
                    label + " signal date (" + signal.date() + ") is not one of the result's own equity-curve dates");
        }
        if (!expected.equals(signal.snapshot().close())) {
            throw new IllegalArgumentException(
                    (label + " signal snapshot close (%s) does not equal the equity-curve close on %s (%s)")
                            .formatted(signal.snapshot().close(), signal.date(), expected));
        }
    }

    private static void verifyConditionTruth(StrategyDefinition strategyDefinition, List<Fill> fills,
                                               List<OrderRejection> rejections) {
        for (Fill fill : fills) {
            SignalEvent signal = fill.signal();
            Condition condition = signal.type() == SignalType.ENTER
                    ? strategyDefinition.entryCondition()
                    : strategyDefinition.exitCondition();
            if (!condition.evaluate(signal.snapshot())) {
                throw new IllegalArgumentException(
                        "fill %d's %s signal does not satisfy the strategy's own %s condition"
                                .formatted(fill.orderId(), signal.type(), signal.type()));
            }
        }
        for (OrderRejection rejection : rejections) {
            SignalEvent signal = rejection.signal();
            if (!strategyDefinition.entryCondition().evaluate(signal.snapshot())) {
                throw new IllegalArgumentException(
                        "rejection at %s's ENTER signal does not satisfy the strategy's own entry condition"
                                .formatted(rejection.date()));
            }
        }
    }

    private static void verifyFillsMatchConfig(List<Fill> fills, BacktestConfig config) {
        BigDecimal buyMultiplier = BigDecimal.ONE.add(config.slippageRate());
        BigDecimal sellMultiplier = BigDecimal.ONE.subtract(config.slippageRate());
        for (Fill fill : fills) {
            if (!fill.commission().equals(config.commissionPerFill())) {
                throw new IllegalArgumentException(
                        "fill %d commission (%s) does not equal the configured commissionPerFill (%s)"
                                .formatted(fill.orderId(), fill.commission(), config.commissionPerFill()));
            }
            BigDecimal multiplier = fill.side() == OrderSide.BUY ? buyMultiplier : sellMultiplier;
            BigDecimal expectedFillPrice = fill.referenceOpen().multiply(multiplier);
            if (!fill.fillPrice().equals(expectedFillPrice)) {
                throw new IllegalArgumentException(
                        ("fill %d fillPrice (%s) does not equal referenceOpen (%s) adjusted by the configured "
                                + "slippageRate (expected %s)")
                                .formatted(fill.orderId(), fill.fillPrice(), fill.referenceOpen(), expectedFillPrice));
            }
        }
    }

    private static Map<LocalDate, Integer> indexEquityCurveDates(List<EquityPoint> equityCurve) {
        Map<LocalDate, Integer> index = new HashMap<>();
        for (int i = 0; i < equityCurve.size(); i++) {
            index.put(equityCurve.get(i).date(), i);
        }
        return index;
    }

    private static void verifyFillCausalTiming(List<EquityPoint> equityCurve, List<Fill> fills) {
        Map<LocalDate, Integer> equityIndex = indexEquityCurveDates(equityCurve);
        for (Fill fill : fills) {
            LocalDate signalDate = fill.signal().date();
            if (!fill.date().isAfter(signalDate)) {
                throw new IllegalArgumentException(
                        "fill %d executes on %s, not after its own signal date %s"
                                .formatted(fill.orderId(), fill.date(), signalDate));
            }
            Integer signalIndex = equityIndex.get(signalDate);
            if (signalIndex == null) {
                throw new IllegalArgumentException(
                        "fill %d signal date (%s) is not one of the result's own equity-curve dates"
                                .formatted(fill.orderId(), signalDate));
            }
            if (signalIndex + 1 >= equityCurve.size() || !equityCurve.get(signalIndex + 1).date().equals(fill.date())) {
                throw new IllegalArgumentException(
                        ("fill %d (date %s) does not execute on the equity-curve bar immediately following its "
                                + "signal date (%s)").formatted(fill.orderId(), fill.date(), signalDate));
            }
        }
    }

    private static void verifyInsufficientCashExecutionTiming(List<EquityPoint> equityCurve,
                                                                List<OrderRejection> rejections) {
        Map<LocalDate, Integer> equityIndex = indexEquityCurveDates(equityCurve);
        for (OrderRejection rejection : rejections) {
            if (!(rejection instanceof OrderRejection.InsufficientCash insufficientCash)) {
                continue;
            }
            LocalDate signalDate = insufficientCash.signal().date();
            LocalDate executionDate = insufficientCash.date();
            if (!executionDate.isAfter(signalDate)) {
                throw new IllegalArgumentException(
                        "InsufficientCash order %d executes on %s, not after its own signal date %s"
                                .formatted(insufficientCash.orderId(), executionDate, signalDate));
            }
            Integer signalIndex = equityIndex.get(signalDate);
            if (signalIndex == null) {
                throw new IllegalArgumentException(
                        "InsufficientCash order %d signal date (%s) is not one of the result's own equity-curve dates"
                                .formatted(insufficientCash.orderId(), signalDate));
            }
            if (signalIndex + 1 >= equityCurve.size()
                    || !equityCurve.get(signalIndex + 1).date().equals(executionDate)) {
                throw new IllegalArgumentException(
                        ("InsufficientCash order %d (execution date %s) does not execute on the equity-curve bar "
                                + "immediately following its signal date (%s)")
                                .formatted(insufficientCash.orderId(), executionDate, signalDate));
            }
        }
    }

    private static void replayLedger(BacktestConfig config, List<EquityPoint> equityCurve, List<Fill> fills) {
        Portfolio portfolio = new Portfolio(config.initialCapital());
        int fillIndex = 0;
        for (EquityPoint stored : equityCurve) {
            if (fillIndex < fills.size() && fills.get(fillIndex).date().equals(stored.date())) {
                portfolio.apply(fills.get(fillIndex));
                fillIndex++;
            }
            EquityPoint replayed = portfolio.markToMarket(stored.date(), stored.close());
            if (!replayed.equals(stored)) {
                throw new IllegalArgumentException(
                        "equity point on %s (%s) does not match the ledger state replayed from fills (%s)"
                                .formatted(stored.date(), stored, replayed));
            }
        }
        if (fillIndex != fills.size()) {
            throw new IllegalArgumentException(
                    "%d fill(s) were never consumed by any equity point during ledger replay"
                            .formatted(fills.size() - fillIndex));
        }
    }

    private static void verifyInsufficientCashAvailableCash(BacktestConfig config, List<Fill> fills,
                                                              List<OrderRejection> rejections) {
        List<OrderRejection.InsufficientCash> insufficientCashRejections = new ArrayList<>();
        for (OrderRejection rejection : rejections) {
            if (rejection instanceof OrderRejection.InsufficientCash insufficientCash) {
                insufficientCashRejections.add(insufficientCash);
            }
        }
        if (insufficientCashRejections.isEmpty()) {
            return;
        }
        insufficientCashRejections.sort(Comparator.comparing(OrderRejection.InsufficientCash::date));

        Portfolio portfolio = new Portfolio(config.initialCapital());
        int fillIndex = 0;
        for (OrderRejection.InsufficientCash rejection : insufficientCashRejections) {
            while (fillIndex < fills.size() && fills.get(fillIndex).date().isBefore(rejection.date())) {
                portfolio.apply(fills.get(fillIndex));
                fillIndex++;
            }
            if (!portfolio.cash().equals(rejection.availableCash())) {
                throw new IllegalArgumentException(
                        ("InsufficientCash order %d's stored availableCash (%s) does not equal the cash replayed "
                                + "from fills before its execution date %s (%s)")
                                .formatted(rejection.orderId(), rejection.availableCash(), rejection.date(),
                                        portfolio.cash()));
            }
        }
    }

    private static void verifyPositionStateAtSignal(BacktestConfig config, List<Fill> fills,
                                                      List<OrderRejection> rejections) {
        record SignalOccurrence(LocalDate date, SignalType type) {
        }
        List<SignalOccurrence> occurrences = new ArrayList<>();
        for (Fill fill : fills) {
            occurrences.add(new SignalOccurrence(fill.signal().date(), fill.signal().type()));
        }
        for (OrderRejection rejection : rejections) {
            occurrences.add(new SignalOccurrence(rejection.signal().date(), rejection.signal().type()));
        }
        occurrences.sort(Comparator.comparing(SignalOccurrence::date));

        Portfolio portfolio = new Portfolio(config.initialCapital());
        int fillIndex = 0;
        for (SignalOccurrence occurrence : occurrences) {
            while (fillIndex < fills.size() && !fills.get(fillIndex).date().isAfter(occurrence.date())) {
                portfolio.apply(fills.get(fillIndex));
                fillIndex++;
            }
            if (occurrence.type() == SignalType.ENTER && !portfolio.isFlat()) {
                throw new IllegalArgumentException(
                        "an ENTER signal on %s occurred while the replayed portfolio was long"
                                .formatted(occurrence.date()));
            }
            if (occurrence.type() == SignalType.EXIT && portfolio.isFlat()) {
                throw new IllegalArgumentException(
                        "an EXIT signal on %s occurred while the replayed portfolio was flat"
                                .formatted(occurrence.date()));
            }
        }
    }

    private static void verifyMetricsMatch(PerformanceMetrics stored, PerformanceMetrics recomputed) {
        if (!recomputed.equals(stored)) {
            throw new IllegalArgumentException(
                    "stored performance metrics (%s) do not match metrics recomputed from the reconstructed result (%s)"
                            .formatted(stored, recomputed));
        }
    }

    private static void verifyCostTotals(BacktestRun run, BacktestResult result) {
        if (!run.totalCommission().equals(result.totalCommission())) {
            throw new IllegalArgumentException(
                    "stored totalCommission (%s) does not equal the sum of reconstructed fill commissions (%s)"
                            .formatted(run.totalCommission(), result.totalCommission()));
        }
        if (!run.totalSlippageCost().equals(result.totalSlippageCost())) {
            throw new IllegalArgumentException(
                    "stored totalSlippageCost (%s) does not equal the sum of reconstructed fill slippage costs (%s)"
                            .formatted(run.totalSlippageCost(), result.totalSlippageCost()));
        }
    }

    private static void verifyBenchmark(BacktestRun run, BacktestResult result) {
        List<EquityPoint> benchmarkCurve = result.equityCurve().stream()
                .map(point -> new EquityPoint(point.date(), run.benchmarkCash(), run.benchmarkQuantity(),
                        run.benchmarkCostBasis(), BigDecimal.ZERO, point.close()))
                .toList();
        BuyAndHoldBenchmark benchmark = new BuyAndHoldBenchmark(run.initialCapital(), benchmarkCurve);

        if (Double.compare(benchmark.totalReturn(), run.benchmarkTotalReturn()) != 0) {
            throw new IllegalArgumentException(
                    ("stored benchmarkTotalReturn (%s) does not equal the value recomputed from the stored benchmark "
                            + "reference state (%s)").formatted(run.benchmarkTotalReturn(), benchmark.totalReturn()));
        }
    }
}
