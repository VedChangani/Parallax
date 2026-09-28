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

/**
 * Reconstructs a {@link BacktestRunDetail} from a persisted {@link
 * BacktestRun}, its stored child rows, and its referenced (but not
 * reloaded-as-bars) {@link StrategyDefinition}/dataset symbol identity, and
 * verifies it end to end (Phase 9 Batch 2c): every row reconstructs through
 * its own engine constructor; an actual immutable {@link BacktestResult} is
 * built via the engine's own constructor (D-24), which performs every
 * cross-list structural check (ascending/alternating fills, non-decreasing
 * rejections, equity-curve range containment) as a checked fact rather than
 * a duplicated one; the persisted equity ledger is independently replayed
 * from the fills through {@link Portfolio} — the same engine type the
 * original run used, never re-implemented here; every stored fill is
 * checked against the persisted {@link BacktestConfig}'s own slippage/
 * commission formulas; {@code firstEvaluableDate} is checked against the
 * reconstructed result's own fills/rejections; order ids are checked to
 * form the exact contiguous {@code {1..n}} range D-21 guarantees; {@link
 * PerformanceMetrics} is <strong>recomputed</strong> via {@link
 * PerformanceMetrics#of(BacktestResult)} and compared exactly (record
 * equality — bit-exact for every {@code double}/{@code OptionalDouble}
 * field, D-26) against the stored metrics; and the persisted buy-and-hold
 * benchmark reference state is independently recomputed into a real {@link
 * BuyAndHoldBenchmark} (D-28) and its {@code totalReturn()} compared
 * bit-exact against the stored value.
 *
 * <p><strong>Phase 10 Batch 1</strong> closes the causal-verification gap
 * identified by the Phase 9 final audit: every fill and rejection's own
 * signal is now checked against the rest of the persisted result, not only
 * against the ledger and the config. Every one of these is provable from
 * data already loaded, uses no {@code DatasetBar}, and never calls {@code
 * Backtester.run(...)} — see each method's own Javadoc:
 * {@link #verifySnapshotIndicatorSpecs}, {@link #verifySignalCloseMatchesEquity},
 * {@link #verifyConditionTruth}, {@link #verifyFillCausalTiming}, {@link
 * #verifyInsufficientCashExecutionTiming}, {@link #verifyInsufficientCashAvailableCash},
 * and {@link #verifyPositionStateAtSignal}.
 *
 * <p><strong>Never reloads the original {@code DatasetVersion}'s bars, and
 * never calls {@code Backtester.run(...)}, {@code MarketDataProvider}, or
 * anything that would re-run the experiment</strong> — this is read-time
 * verification of an already-completed, persisted result, bounded by
 * exactly what that result and its immutable referenced identities already
 * contain (CLAUDE.md; see the class-level boundary note on each verify
 * method below for what specifically cannot be proven without the original
 * bars). {@code strategyDefinition} and {@code datasetSymbol} are supplied
 * by the caller ({@code BacktestRunService}), already owner-scoped and
 * integrity-verified through the existing {@code StrategyService}/{@code
 * DatasetService} read paths — this class never touches a repository or a
 * service itself, staying a pure, stateless, testable reconstruction.
 *
 * <p>Every failure — from an engine constructor's own {@link
 * IllegalArgumentException}, from {@link Portfolio}'s own {@link
 * IllegalStateException} during ledger replay, from a malformed stored
 * {@link IndicatorSnapshot} JSON, or from one of this class's own
 * cross-field checks — is caught in exactly one place and rethrown as
 * {@link BacktestResultIntegrityException}.
 */
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
            // A: an unsupported/future engine semantics version must never be
            // silently interpreted with today's semantics. SEMANTICS_VERSION's
            // scope (Phase 9 Batch 2a) covers every result-affecting convention:
            // the run loop, indicator formulas, sizing/execution, portfolio
            // accounting, trading-cost derivation, PerformanceMetrics, and
            // BuyAndHoldBenchmark - so this single check is what makes every
            // recomputation below meaningful: recomputing under formulas that
            // may have since changed would not prove anything about the past.
            if (run.engineSemanticsVersion() != Backtester.SEMANTICS_VERSION) {
                throw new IllegalArgumentException(
                        "unsupported engine semantics version: " + run.engineSemanticsVersion());
            }

            BacktestConfig config = reconstructConfig(run);
            List<EquityPoint> equityCurve = reconstructEquityCurve(equityRows);
            List<Fill> fills = reconstructFills(fillRows);
            List<OrderRejection> rejections = reconstructRejections(rejectionRows);

            // D-24's own constructor performs every cross-list structural check
            // (equity curve non-empty/strictly-ascending/range-contained, fills
            // strictly-ascending and alternating BUY/SELL from BUY, rejections
            // non-decreasing) - never duplicated here. This is "reconstruct an
            // actual immutable BacktestResult" (not a parallel verified-result
            // model): every later step in this method operates on this one
            // engine-constructed object.
            BacktestResult result = new BacktestResult(datasetSymbol, strategyDefinition, config,
                    run.firstEvaluableDate(), equityCurve, fills, rejections);

            verifyOrderIdContinuity(fills, rejections);
            verifyFirstEvaluableDateSemantics(result);

            // Phase 10 Batch 1: every signal's own recorded shape must agree with
            // the rest of the persisted result before the ledger/config/metric
            // checks below even run.
            verifySnapshotIndicatorSpecs(strategyDefinition, fills, rejections);
            verifySignalCloseMatchesEquity(equityCurve, fills, rejections);
            verifyConditionTruth(strategyDefinition, fills, rejections);

            verifyFillsMatchConfig(fills, config);
            verifyFillCausalTiming(equityCurve, fills);
            verifyInsufficientCashExecutionTiming(equityCurve, rejections);
            replayLedger(config, equityCurve, fills);
            verifyInsufficientCashAvailableCash(config, fills, rejections);
            verifyPositionStateAtSignal(config, fills, rejections);

            PerformanceMetrics storedMetrics = reconstructMetrics(run);
            PerformanceMetrics recomputedMetrics = PerformanceMetrics.of(result);
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

    // --- structural row reconstruction (per-row engine validation only; every
    // cross-list ordering/range invariant is left to BacktestResult's own
    // constructor, called once in reconstruct() above) -------------------------

    private static BacktestConfig reconstructConfig(BacktestRun run) {
        BacktestConfig config = new BacktestConfig(run.initialCapital(), run.commissionPerFill(),
                run.slippageRate(), run.startDate(), run.endDate());
        // BacktestResult validates every equity/fill/rejection date against this
        // range, but not firstEvaluableDate itself - that check stays here.
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

    private static PerformanceMetrics reconstructMetrics(BacktestRun run) {
        return new PerformanceMetrics(run.totalReturn(), run.cagr(), run.volatility(), run.sharpeRatio(),
                run.maxDrawdown(), run.closedTradeCount(), run.winRate(), run.averageWin(), run.averageLoss());
    }

    // --- order id continuity (D-21: {fill ids} u {InsufficientCash ids} = {1..n}) ---

    private static void verifyOrderIdContinuity(List<Fill> fills, List<OrderRejection> rejections) {
        int expectedCount = fills.size();
        for (OrderRejection rejection : rejections) {
            if (rejection instanceof OrderRejection.InsufficientCash) {
                expectedCount++;
            }
        }
        boolean[] consumed = new boolean[expectedCount + 1]; // 1-indexed; index 0 unused
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

    // --- firstEvaluableDate semantics (D-25: no signal before indicators are ready) ---

    /**
     * <strong>Boundary:</strong> without the original bars, this cannot prove
     * {@code firstEvaluableDate} is the bar where indicators <em>actually</em>
     * became ready (that requires replaying indicator warm-up against real
     * closes). What it can and does prove from the persisted result alone:
     * a signal can never exist before indicators are ready (D-25), so an
     * empty {@code firstEvaluableDate} is incompatible with any fill/
     * rejection existing at all, and a present one is incompatible with any
     * signal dated earlier than it, or with a date that is not even one of
     * the result's own equity-curve dates.
     */
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

    // --- signal shape verification (Phase 10 Batch 1) ---------------------------

    /**
     * Every fill/rejection's stored signal snapshot must reference exactly
     * the indicator specs {@code strategy.requiredIndicatorSpecs()}
     * declares, in the same canonical order — never more, fewer, or a
     * different spec. A stored spec set that merely happens to be the same
     * <em>size</em> as expected is not enough (a corrupted snapshot could
     * substitute one spec for another of the same period/type count); exact
     * list equality is checked instead. This closes a silent-collapse gap
     * in {@link IndicatorSnapshotJson#read}: a duplicate stored spec is now
     * rejected there directly, during row reconstruction, so it can never
     * first collapse into one entry and then coincidentally match here.
     */
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

    /**
     * Every fill/rejection's signal snapshot {@code close} must equal the
     * result's own equity-curve close on that same signal date exactly — a
     * signal and the equity point recorded for the same bar are two
     * persisted views of one close price, and a genuine run can never
     * disagree with itself about it.
     */
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

    /**
     * Every fill/rejection's stored signal must actually satisfy the
     * strategy's own condition, evaluated fresh against its own stored
     * snapshot — an ENTER signal against {@code entryCondition}, an EXIT
     * signal against {@code exitCondition}. Every rejection is already
     * structurally guaranteed to carry an ENTER signal (D-21's own engine
     * constructors reject any other signal type when the row is
     * reconstructed), so only {@code entryCondition} ever applies to one.
     * This is a direct re-evaluation of the frozen {@link Condition} tree —
     * no engine run, no market data, nothing beyond what is already loaded.
     */
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

    // --- fill <-> config verification (D-7/D-23 execution formulas) ------------

    /**
     * <strong>Boundary:</strong> this proves each fill's {@code fillPrice}
     * is exactly {@code referenceOpen} adjusted by the persisted config's
     * own slippage rate, and {@code commission} exactly equals the
     * persisted {@code commissionPerFill} - both provable from the stored
     * result and config alone. It cannot prove {@code referenceOpen} itself
     * matches the original dataset bar's actual open (that requires the
     * bars), nor can it re-derive {@code ZeroQuantity} sizing math (D-23's
     * {@code enterQuantity}, a function of the signal-bar close, which is
     * not the reference open stored on a fill).
     */
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

    // --- next-bar-open causal timing (D-7, Phase 10 Batch 1) --------------------

    /**
     * Maps each equity-curve date to its position in {@code equityCurve} —
     * shared by {@link #verifyFillCausalTiming} and {@link
     * #verifyInsufficientCashExecutionTiming} to check that an execution
     * (a fill, or an {@code InsufficientCash} rejection) lands on the
     * equity-curve bar <em>immediately following</em> its own signal date,
     * not merely some later one (D-7: next-bar-open, no same-bar execution,
     * no skipped bar).
     */
    private static Map<LocalDate, Integer> indexEquityCurveDates(List<EquityPoint> equityCurve) {
        Map<LocalDate, Integer> index = new HashMap<>();
        for (int i = 0; i < equityCurve.size(); i++) {
            index.put(equityCurve.get(i).date(), i);
        }
        return index;
    }

    /**
     * <strong>Boundary:</strong> proves every fill executes strictly after
     * its own signal date, and on the equity-curve bar immediately
     * following it — the next-bar-open rule (D-7) — using only the
     * persisted result's own dates. It cannot prove that date was the
     * <em>only</em> possible next bar (that would need the original
     * dataset bars); it proves the stored fill date is not an arbitrary
     * later (or non-later) date, which is exactly what a tampered
     * {@code fill_date}/{@code signal_date} pair would produce.
     */
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

    /**
     * The {@code InsufficientCash} counterpart to {@link
     * #verifyFillCausalTiming}: its {@code executionDate} must be strictly
     * after its own signal date, and on the equity-curve bar immediately
     * following it. {@code ZeroQuantity} has no execution date at all — no
     * order was ever created (D-21) — so it is not checked here.
     */
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

    // --- ledger replay (D-22 Portfolio, the central integrity check) -----------

    /**
     * Replays {@link Portfolio} accounting from {@code initialCapital}
     * through {@code fills} in chronological order, marking to each equity
     * point's own stored {@code close} exactly where the original run did
     * (D-25: one {@code EquityPoint} per bar, after that bar's own fill if
     * any) - never calling {@code Backtester.run(...)}, only replaying the
     * same engine {@link Portfolio} the original run used. Every persisted
     * equity point must equal, by exact record equality (BigDecimal
     * {@code equals}, scale-sensitive), the point produced by this replay;
     * every fill must be consumed by exactly one equity point along the
     * way. {@code fills} and {@code equityCurve} are both already known
     * strictly ascending here ({@link BacktestResult}'s own constructor),
     * and {@code backtest_fill} carries a unique {@code (run_id, fill_date)}
     * constraint, so at most one fill can share any one date.
     */
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

    // --- InsufficientCash cash/state verification (Phase 10 Batch 1) -----------

    /**
     * The {@code InsufficientCash} counterpart to {@link #replayLedger}:
     * {@code availableCash} must equal the portfolio's actual cash,
     * replayed from {@code initialCapital} through every fill executed
     * strictly before this rejection's own execution date — the cash the
     * engine would genuinely have had on hand at the moment this order was
     * rejected. Independent of {@link #replayLedger}, which only walks
     * equity points and never reads a rejection's own stored fields.
     */
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

    /**
     * Verifies the flat/long position-state rule at every signal, in
     * chronological signal-date order: an ENTER signal (from a BUY fill, or
     * any rejection — both structurally guaranteed ENTER by D-21) must
     * occur only while the replayed portfolio is flat, and an EXIT signal
     * (from a SELL fill) only while long — mirroring exactly which
     * condition {@code Backtester.Run} itself would have evaluated at that
     * point (architecture.md §3, steps 6-9). A fill executed at or before
     * this signal's own bar (including a same-bar SELL immediately followed
     * by a new ENTER evaluation once flat again) is applied before the
     * check; a fill still pending — this signal's own eventual outcome —
     * is not, since {@code fill.date()} is always strictly after its own
     * signal date.
     */
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

    // --- metric recomputation (D-26; exact, no tolerance) -----------------------

    private static void verifyMetricsMatch(PerformanceMetrics stored, PerformanceMetrics recomputed) {
        if (!recomputed.equals(stored)) {
            throw new IllegalArgumentException(
                    "stored performance metrics (%s) do not match metrics recomputed from the reconstructed result (%s)"
                            .formatted(stored, recomputed));
        }
    }

    // --- trading-cost totals (D-27; exact BigDecimal sums, now via BacktestResult) ---

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

    // --- benchmark recomputation (D-28) -----------------------------------------

    /**
     * <strong>Boundary:</strong> only the benchmark's persisted reference
     * point ({@code cash}/{@code quantity}/{@code costBasis}, fixed once at
     * entry per D-28) was ever stored - its full equity curve was not. This
     * reconstructs a synthetic curve pairing that fixed reference state with
     * the (already fill/ledger-verified) result's own equity-curve dates and
     * closes, and builds a real {@link BuyAndHoldBenchmark} from it: its own
     * constructor proves {@code cash + costBasis == initialCapital} and a
     * non-negative, non-decreasing-date curve, and {@link
     * BuyAndHoldBenchmark#totalReturn()} recomputed from it is compared
     * bit-exact against the stored {@code benchmarkTotalReturn}. This
     * cannot independently prove the entry quantity/cost basis were
     * correctly derived from the original first-bar opening price - that
     * requires the original bars (D-28's own option-A entry rule) and is
     * out of this verification's reach by design.
     */
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
