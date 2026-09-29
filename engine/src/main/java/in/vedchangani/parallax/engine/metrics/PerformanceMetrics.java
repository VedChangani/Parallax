package in.vedchangani.parallax.engine.metrics;

import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.result.Trade;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Post-run performance statistics for one {@link BacktestResult} (D-26).
 * This is analysis, not simulation: {@link #of(BacktestResult)} is a pure
 * function of an already-produced, immutable result. It never mutates the
 * result, the {@code Portfolio} that produced it (already discarded), or
 * anything reachable from either, and it participates in none of signal
 * generation, sizing, execution, or chronological processing (D-5).
 *
 * <p>Every metric that can be undefined for a given result — because too
 * little history exists, or because a ratio's denominator is zero — is an
 * {@link OptionalDouble} with exactly one documented emptiness condition,
 * never {@code NaN}, {@code Infinity}, or a sentinel value. The two metrics
 * that are always defined for any valid result ({@link #totalReturn()},
 * {@link #maxDrawdown()}) are plain {@code double}s, and
 * {@link #closedTradeCount()} is a plain {@code int}. {@link #profitFactor()}
 * is empty exactly when there is no losing closed trade.
 *
 * <p>Money figures ({@code equity}, {@code realizedPnl}) are summed and
 * differenced exactly in {@link BigDecimal}; every metric here converts to
 * {@code double} only at its own statistical calculation boundary (D-14:
 * ledger values are exact, derived statistics are {@code double}). Ratios
 * involving {@link Math#pow} or a square root use {@link StrictMath},
 * never {@link Math}, for bit-reproducible results across platforms
 * (D-15).
 */
public record PerformanceMetrics(double totalReturn, OptionalDouble cagr, OptionalDouble volatility,
                                  OptionalDouble sharpeRatio, double maxDrawdown, int closedTradeCount,
                                  OptionalDouble winRate, OptionalDouble averageWin, OptionalDouble averageLoss,
                                  OptionalDouble profitFactor) {

    /** The fixed V1 annualization convention for per-bar returns (D-26). */
    public static final int TRADING_DAYS_PER_YEAR = 252;

    /** The fixed ACT/365 convention for {@link #cagr()}'s observed span (D-26). */
    public static final int DAYS_PER_YEAR = 365;

    public PerformanceMetrics {
        Objects.requireNonNull(cagr, "cagr must not be null");
        Objects.requireNonNull(volatility, "volatility must not be null");
        Objects.requireNonNull(sharpeRatio, "sharpeRatio must not be null");
        Objects.requireNonNull(winRate, "winRate must not be null");
        Objects.requireNonNull(averageWin, "averageWin must not be null");
        Objects.requireNonNull(averageLoss, "averageLoss must not be null");
        Objects.requireNonNull(profitFactor, "profitFactor must not be null");

        if (!Double.isFinite(totalReturn)) {
            throw new IllegalArgumentException("totalReturn must be finite, was " + totalReturn);
        }
        if (!Double.isFinite(maxDrawdown)) {
            throw new IllegalArgumentException("maxDrawdown must be finite, was " + maxDrawdown);
        }
        if (maxDrawdown < 0.0 || maxDrawdown >= 1.0) {
            throw new IllegalArgumentException("maxDrawdown must be >= 0 and < 1, was " + maxDrawdown);
        }
        if (closedTradeCount < 0) {
            throw new IllegalArgumentException("closedTradeCount must be >= 0, was " + closedTradeCount);
        }

        requireFiniteWhenPresent(cagr, "cagr");
        requireFiniteWhenPresent(volatility, "volatility");
        if (volatility.isPresent() && volatility.getAsDouble() < 0.0) {
            throw new IllegalArgumentException("volatility must be >= 0 when present, was " + volatility.getAsDouble());
        }
        requireFiniteWhenPresent(sharpeRatio, "sharpeRatio");
        requireFiniteWhenPresent(winRate, "winRate");
        if (winRate.isPresent() && (winRate.getAsDouble() < 0.0 || winRate.getAsDouble() > 1.0)) {
            throw new IllegalArgumentException("winRate must be in [0,1] when present, was " + winRate.getAsDouble());
        }
        if (winRate.isEmpty() != (closedTradeCount == 0)) {
            throw new IllegalArgumentException(
                    "winRate must be empty iff closedTradeCount == 0 (closedTradeCount=" + closedTradeCount
                            + ", winRate=" + winRate + ")");
        }
        requireFiniteWhenPresent(averageWin, "averageWin");
        if (averageWin.isPresent() && averageWin.getAsDouble() <= 0.0) {
            throw new IllegalArgumentException("averageWin must be > 0 when present, was " + averageWin.getAsDouble());
        }
        requireFiniteWhenPresent(averageLoss, "averageLoss");
        if (averageLoss.isPresent() && averageLoss.getAsDouble() >= 0.0) {
            throw new IllegalArgumentException("averageLoss must be < 0 when present, was " + averageLoss.getAsDouble());
        }
        requireFiniteWhenPresent(profitFactor, "profitFactor");
        if (profitFactor.isPresent() && profitFactor.getAsDouble() < 0.0) {
            throw new IllegalArgumentException("profitFactor must be >= 0 when present, was " + profitFactor.getAsDouble());
        }
        // Defined exactly when a losing closed trade exists (its denominator).
        if (profitFactor.isPresent() != averageLoss.isPresent()) {
            throw new IllegalArgumentException(
                    "profitFactor must be present iff averageLoss is present (averageLoss=" + averageLoss
                            + ", profitFactor=" + profitFactor + ")");
        }
    }

    private static void requireFiniteWhenPresent(OptionalDouble value, String name) {
        if (value.isPresent() && !Double.isFinite(value.getAsDouble())) {
            throw new IllegalArgumentException(name + " must be finite when present, was " + value.getAsDouble());
        }
    }

    /**
     * Computes every V1 metric from {@code result} (D-26).
     *
     * <p>Two preconditions are enforced (never true of a genuine
     * {@code Backtester} run, but not structurally guaranteed by
     * {@link BacktestResult} itself): the first equity point's equity must
     * equal {@code result.config().initialCapital()} exactly, and every
     * equity point's equity must be strictly positive. Both make the
     * formulas below (return base, log-free ratios) mutually consistent
     * rather than silently assumed.
     *
     * @throws NullPointerException     if {@code result} is null
     * @throws IllegalArgumentException if either precondition above is violated
     */
    public static PerformanceMetrics of(BacktestResult result) {
        Objects.requireNonNull(result, "result must not be null");

        List<EquityPoint> curve = result.equityCurve();
        BigDecimal initialCapital = result.config().initialCapital();
        EquityPoint first = curve.get(0);
        EquityPoint last = curve.get(curve.size() - 1);

        if (first.equity().compareTo(initialCapital) != 0) {
            throw new IllegalArgumentException(
                    "first equity point's equity (%s) must equal initialCapital (%s)"
                            .formatted(first.equity(), initialCapital));
        }
        for (EquityPoint point : curve) {
            if (point.equity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "equity point on %s has non-positive equity (%s)".formatted(point.date(), point.equity()));
            }
        }

        double totalReturn = last.equity().subtract(initialCapital).doubleValue() / initialCapital.doubleValue();
        OptionalDouble cagr = cagr(first, last, initialCapital);

        double[] returns = periodicReturns(curve);
        OptionalDouble stdDev = sampleStdDev(returns);
        OptionalDouble volatility = annualizedVolatility(stdDev);
        OptionalDouble sharpe = sharpeRatio(returns, stdDev);

        double maxDrawdown = maxDrawdown(curve);

        // --- trade statistics: closed trades only (D-26 §9) -------------
        int closedCount = 0;
        int wins = 0;
        int losses = 0;
        BigDecimal winSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        for (Trade trade : result.trades()) {
            if (trade instanceof Trade.Closed closed) {
                closedCount++;
                BigDecimal pnl = closed.realizedPnl();
                int sign = pnl.signum();
                if (sign > 0) {
                    wins++;
                    winSum = winSum.add(pnl);
                } else if (sign < 0) {
                    losses++;
                    lossSum = lossSum.add(pnl);
                }
                // sign == 0: breakeven — counted in closedCount/winRate denominator only
            }
        }
        OptionalDouble winRate = closedCount == 0
                ? OptionalDouble.empty()
                : OptionalDouble.of((double) wins / closedCount);
        OptionalDouble averageWin = wins == 0
                ? OptionalDouble.empty()
                : OptionalDouble.of(winSum.doubleValue() / wins);
        OptionalDouble averageLoss = losses == 0
                ? OptionalDouble.empty()
                : OptionalDouble.of(lossSum.doubleValue() / losses);

        // Profit factor: gross profit / |gross loss| over closed trades. The
        // sums are exact BigDecimal; the single division happens in double
        // (D-14). Empty, never Infinity, when no closed trade lost money.
        OptionalDouble profitFactor = losses == 0
                ? OptionalDouble.empty()
                : OptionalDouble.of(winSum.doubleValue() / lossSum.negate().doubleValue());

        return new PerformanceMetrics(totalReturn, cagr, volatility, sharpe, maxDrawdown, closedCount, winRate,
                averageWin, averageLoss, profitFactor);
    }

    /**
     * ACT/365 CAGR over the observed span (first to last equity-point
     * date), empty for a span under 365 days — V1 never annualizes a
     * sub-year run (D-26).
     */
    private static OptionalDouble cagr(EquityPoint first, EquityPoint last, BigDecimal initialCapital) {
        long days = ChronoUnit.DAYS.between(first.date(), last.date());
        if (days < DAYS_PER_YEAR) {
            return OptionalDouble.empty();
        }
        double years = days / 365.0;
        double ratio = last.equity().doubleValue() / initialCapital.doubleValue();
        return OptionalDouble.of(StrictMath.pow(ratio, 1.0 / years) - 1.0);
    }

    /**
     * Simple arithmetic per-bar returns between consecutive equity points
     * only — {@code initialCapital} is never a separate observation
     * (D-26 §5). {@code n} equity points give {@code n-1} returns. A data
     * gap between two consecutive points is still exactly one return
     * observation; it is not calendar-gap adjusted.
     */
    private static double[] periodicReturns(List<EquityPoint> curve) {
        int n = curve.size();
        double[] returns = new double[Math.max(0, n - 1)];
        for (int i = 1; i < n; i++) {
            BigDecimal previous = curve.get(i - 1).equity();
            BigDecimal current = curve.get(i).equity();
            returns[i - 1] = current.subtract(previous).doubleValue() / previous.doubleValue();
        }
        return returns;
    }

    /**
     * Sample standard deviation (divisor {@code n-1}) of {@code returns},
     * empty for fewer than two observations. If every return is
     * bitwise-equal, the result is defined as exactly {@code 0.0} — mean
     * subtraction over identical doubles can otherwise leave ~1e-17 of
     * spurious floating-point dispersion (D-26 §6).
     */
    private static OptionalDouble sampleStdDev(double[] returns) {
        if (returns.length < 2) {
            return OptionalDouble.empty();
        }

        double first = returns[0];
        boolean allEqual = true;
        for (double r : returns) {
            if (r != first) {
                allEqual = false;
                break;
            }
        }
        if (allEqual) {
            return OptionalDouble.of(0.0);
        }

        double sum = 0.0;
        for (double r : returns) {
            sum += r;
        }
        double mean = sum / returns.length;

        double sumSquares = 0.0;
        for (double r : returns) {
            double diff = r - mean;
            sumSquares += diff * diff;
        }
        return OptionalDouble.of(StrictMath.sqrt(sumSquares / (returns.length - 1)));
    }

    /** {@code stdDev × √252} (D-26 §6, fixed 252 annualization). */
    private static OptionalDouble annualizedVolatility(OptionalDouble stdDev) {
        return stdDev.isPresent()
                ? OptionalDouble.of(stdDev.getAsDouble() * StrictMath.sqrt(TRADING_DAYS_PER_YEAR))
                : OptionalDouble.empty();
    }

    /**
     * {@code mean(returns) / stdDev × √252}, risk-free rate fixed at zero
     * (D-26 §7). Empty for fewer than two returns or zero volatility
     * (including a flat no-trade run, where the ratio is undefined) —
     * never {@code NaN} or {@code Infinity}.
     */
    private static OptionalDouble sharpeRatio(double[] returns, OptionalDouble stdDev) {
        if (stdDev.isEmpty() || stdDev.getAsDouble() == 0.0) {
            return OptionalDouble.empty();
        }
        double sum = 0.0;
        for (double r : returns) {
            sum += r;
        }
        double mean = sum / returns.length;
        return OptionalDouble.of(mean / stdDev.getAsDouble() * StrictMath.sqrt(TRADING_DAYS_PER_YEAR));
    }

    /**
     * The drawdown at every equity point, aligned index-for-index with
     * {@code curve}: {@code (runningPeak - equity) / runningPeak}, where the
     * running peak is the highest equity seen up to and including that point
     * (the first point's equity is the initial peak). Each value is a
     * fraction {@code >= 0}, e.g. {@code 0.25} for 25% below the peak, and
     * exactly {@code 0.0} at a new high. This is the single definition
     * {@link #maxDrawdown()} is the maximum of.
     *
     * <p>Assumes strictly positive equity, which {@link #of(BacktestResult)}
     * enforces for every result it accepts.
     *
     * @throws NullPointerException     if {@code curve} is null
     * @throws IllegalArgumentException if {@code curve} is empty
     */
    public static double[] drawdownSeries(List<EquityPoint> curve) {
        Objects.requireNonNull(curve, "curve must not be null");
        if (curve.isEmpty()) {
            throw new IllegalArgumentException("curve must not be empty");
        }
        double[] drawdowns = new double[curve.size()];
        BigDecimal peak = curve.get(0).equity();
        for (int i = 0; i < drawdowns.length; i++) {
            BigDecimal equity = curve.get(i).equity();
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            drawdowns[i] = peak.subtract(equity).doubleValue() / peak.doubleValue();
        }
        return drawdowns;
    }

    /**
     * Maximum close-to-close drawdown as a fraction of the running peak
     * equity, e.g. {@code 0.25} for a 25% decline (D-26 §8): the maximum of
     * {@link #drawdownSeries(List)}. Always {@code 0.0} or greater; the
     * series itself is not stored on this record.
     */
    private static double maxDrawdown(List<EquityPoint> curve) {
        double maxDd = 0.0;
        for (double dd : drawdownSeries(curve)) {
            if (dd > maxDd) {
                maxDd = dd;
            }
        }
        return maxDd;
    }
}
