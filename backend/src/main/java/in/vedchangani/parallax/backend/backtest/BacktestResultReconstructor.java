package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.Trade;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reconstructs a {@link BacktestRunDetail} from a persisted {@link
 * BacktestRun} and its stored child rows, and verifies it (D-34 Batch 2
 * §7-9): structural checks (every row reconstructs through its engine
 * constructor; equity/fill dates strictly ascending; rejection {@code seq}
 * preserves append order) and cross-field checks (stored commission/
 * slippage totals equal the exact sum over reconstructed fills; the
 * reconstructed trade/closed-trade count agrees with the stored metrics;
 * the stored benchmark state is internally consistent; the persisted
 * engine semantics version is one this codebase still understands).
 *
 * <p>Never decodes the original {@code StrategyVersion} and never reloads
 * the original {@code DatasetVersion} bars — the persisted run is a
 * historical result snapshot (CLAUDE.md) — and never calls {@code
 * PerformanceMetrics.of(...)}, {@code BuyAndHoldBenchmark.of(...)}, or
 * {@code Backtester.run(...)}: every stored metric/benchmark value is
 * trusted as a historical fact once it passes the checks below, never
 * recomputed.
 *
 * <p>Every failure — from an engine constructor's own {@link
 * IllegalArgumentException}, from a malformed stored {@link
 * IndicatorSnapshot} JSON, or from one of this class's own cross-field
 * checks — is caught in exactly one place and rethrown as {@link
 * BacktestResultIntegrityException}.
 */
final class BacktestResultReconstructor {

    private BacktestResultReconstructor() {
    }

    static BacktestRunDetail reconstruct(BacktestRun run, List<BacktestEquityPointRow> equityRows,
                                          List<BacktestFillRow> fillRows, List<BacktestRejectionRow> rejectionRows) {
        Objects.requireNonNull(run, "run must not be null");
        Objects.requireNonNull(equityRows, "equityRows must not be null");
        Objects.requireNonNull(fillRows, "fillRows must not be null");
        Objects.requireNonNull(rejectionRows, "rejectionRows must not be null");

        try {
            // H: an unsupported/future engine semantics version must never be
            // silently interpreted with today's semantics.
            if (run.engineSemanticsVersion() != Backtester.SEMANTICS_VERSION) {
                throw new IllegalArgumentException(
                        "unsupported engine semantics version: " + run.engineSemanticsVersion());
            }

            BacktestConfig config = reconstructConfig(run);
            List<EquityPoint> equityCurve = reconstructEquityCurve(equityRows, config);
            List<Fill> fills = reconstructFills(fillRows);
            List<OrderRejection> rejections = reconstructRejections(rejectionRows);
            PerformanceMetrics metrics = reconstructMetrics(run);

            verifyCrossFieldIntegrity(run, config, fills, metrics);

            return new BacktestRunDetail(BacktestRunSummary.of(run), config, run.firstEvaluableDate(), equityCurve,
                    fills, rejections, metrics, run.totalCommission(), run.totalSlippageCost(), run.benchmarkCash(),
                    run.benchmarkQuantity(), run.benchmarkCostBasis(), run.benchmarkTotalReturn());
        } catch (IllegalArgumentException e) {
            throw new BacktestResultIntegrityException(
                    "stored backtest run " + run.id() + " failed integrity verification: " + e.getMessage(), e);
        }
    }

    // --- structural reconstruction --------------------------------------------

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

    private static List<EquityPoint> reconstructEquityCurve(List<BacktestEquityPointRow> rows,
                                                              BacktestConfig config) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("equityCurve must not be empty");
        }
        List<EquityPoint> points = new ArrayList<>(rows.size());
        LocalDate previous = null;
        for (BacktestEquityPointRow row : rows) {
            EquityPoint point = new EquityPoint(row.barDate(), row.cash(), row.quantity(), row.costBasis(),
                    row.realizedPnl(), row.close());
            if (previous != null && !point.date().isAfter(previous)) {
                throw new IllegalArgumentException(
                        "equity point dates must be strictly ascending; %s is not after %s"
                                .formatted(point.date(), previous));
            }
            if (point.date().isBefore(config.startDate()) || point.date().isAfter(config.endDate())) {
                throw new IllegalArgumentException(
                        "equity point date %s is outside the run's range [%s, %s]"
                                .formatted(point.date(), config.startDate(), config.endDate()));
            }
            previous = point.date();
            points.add(point);
        }
        return List.copyOf(points);
    }

    private static List<Fill> reconstructFills(List<BacktestFillRow> rows) {
        List<Fill> fills = new ArrayList<>(rows.size());
        LocalDate previous = null;
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
            Fill fill = new Fill(row.orderId(), row.fillDate(), row.quantity(), row.referenceOpen(), row.fillPrice(),
                    row.commission(), signal);
            if (previous != null && !fill.date().isAfter(previous)) {
                throw new IllegalArgumentException(
                        "fill dates must be strictly ascending; %s is not after %s".formatted(fill.date(), previous));
            }
            previous = fill.date();
            fills.add(fill);
        }
        return List.copyOf(fills);
    }

    private static List<OrderRejection> reconstructRejections(List<BacktestRejectionRow> rows) {
        List<OrderRejection> rejections = new ArrayList<>(rows.size());
        int expectedSeq = 1;
        LocalDate previousDate = null;
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

            // BacktestResult's own invariant: rejection dates are non-decreasing in
            // append order (never sorted or repaired here - a violation is tampering).
            if (previousDate != null && rejection.date().isBefore(previousDate)) {
                throw new IllegalArgumentException(
                        "rejection dates must be non-decreasing; %s is before %s"
                                .formatted(rejection.date(), previousDate));
            }
            previousDate = rejection.date();

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

    private static PerformanceMetrics reconstructMetrics(BacktestRun run) {
        return new PerformanceMetrics(run.totalReturn(), run.cagr(), run.volatility(), run.sharpeRatio(),
                run.maxDrawdown(), run.closedTradeCount(), run.winRate(), run.averageWin(), run.averageLoss());
    }

    // --- cross-field integrity (D-34 Batch 2 §8) -------------------------------

    private static void verifyCrossFieldIntegrity(BacktestRun run, BacktestConfig config, List<Fill> fills,
                                                    PerformanceMetrics metrics) {
        // A: commission — exact BigDecimal value+scale equality, matching how
        // BacktestResult.totalCommission() itself sums over the same fills.
        BigDecimal commissionSum = BigDecimal.ZERO;
        for (Fill fill : fills) {
            commissionSum = commissionSum.add(fill.commission());
        }
        if (!run.totalCommission().equals(commissionSum)) {
            throw new IllegalArgumentException(
                    "stored totalCommission (%s) does not equal the sum of fill commissions (%s)"
                            .formatted(run.totalCommission(), commissionSum));
        }

        // B: slippage — same exact-equality policy as A.
        BigDecimal slippageSum = BigDecimal.ZERO;
        for (Fill fill : fills) {
            slippageSum = slippageSum.add(fill.slippageCost());
        }
        if (!run.totalSlippageCost().equals(slippageSum)) {
            throw new IllegalArgumentException(
                    "stored totalSlippageCost (%s) does not equal the sum of fill slippage costs (%s)"
                            .formatted(run.totalSlippageCost(), slippageSum));
        }

        // C: fill/trade structure — Trade.fromFills must succeed, and the
        // reconstructed closed-trade count must match the stored metric.
        List<Trade> trades = Trade.fromFills(fills);
        long closedCount = trades.stream().filter(Trade.Closed.class::isInstance).count();
        if (closedCount != metrics.closedTradeCount()) {
            throw new IllegalArgumentException(
                    "stored closedTradeCount (%d) does not equal the reconstructed closed trade count (%d)"
                            .formatted(metrics.closedTradeCount(), closedCount));
        }

        // D: trade statistics — PerformanceMetrics's own constructor already
        // enforces winRate emptiness iff closedTradeCount == 0; averageWin/
        // averageLoss have no such built-in cross-check, so it is added here.
        if (metrics.closedTradeCount() == 0
                && (metrics.averageWin().isPresent() || metrics.averageLoss().isPresent())) {
            throw new IllegalArgumentException(
                    "averageWin/averageLoss must be empty when closedTradeCount is 0");
        }

        // G: benchmark — the stored reference point's own accounting identity,
        // never a reconstructed BuyAndHoldBenchmark (its equity curve was never
        // persisted).
        BigDecimal benchmarkTotal = run.benchmarkCash().add(run.benchmarkCostBasis());
        if (benchmarkTotal.compareTo(run.initialCapital()) != 0) {
            throw new IllegalArgumentException(
                    "benchmark cash + costBasis (%s) does not equal initialCapital (%s)"
                            .formatted(benchmarkTotal, run.initialCapital()));
        }
        if (run.benchmarkQuantity() < 0) {
            throw new IllegalArgumentException("benchmark quantity must be >= 0, was " + run.benchmarkQuantity());
        }
        if (!Double.isFinite(run.benchmarkTotalReturn())) {
            throw new IllegalArgumentException(
                    "benchmark totalReturn must be finite, was " + run.benchmarkTotalReturn());
        }
    }
}
