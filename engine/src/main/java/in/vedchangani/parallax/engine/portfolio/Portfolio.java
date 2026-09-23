package in.vedchangani.parallax.engine.portfolio;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The single mutable source of truth for financial state during one
 * backtest run: cash, position quantity, total cost basis, and cumulative
 * realized P&amp;L. It is per-run, not thread-safe, and never shared
 * between runs.
 *
 * <p>V1 is long-only with a single position and no pyramiding (D-16), so
 * {@code Portfolio} enforces a strict flat/long state machine: a BUY is
 * accepted only while flat, and a SELL only for the full held quantity.
 * There is no {@code Position} object, no lots, and no FIFO/LIFO — one
 * quantity and one cost basis fully describe the position.
 *
 * <p>Average cost is deliberately not exposed here (D-13/D-22): it is a
 * display concern for a later reporting layer, and computing it would be
 * the engine's only division. {@link #apply(Fill)} is the only method
 * that mutates state, and it is all-or-nothing — every precondition is
 * checked before any field changes, so a rejected operation leaves the
 * portfolio completely unchanged. {@link #markToMarket} never mutates.
 */
public final class Portfolio {

    private BigDecimal cash;
    private long quantity;
    private BigDecimal costBasis;
    private BigDecimal realizedPnl;

    public Portfolio(BigDecimal initialCash) {
        Objects.requireNonNull(initialCash, "initialCash must not be null");
        if (initialCash.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("initialCash must be >= 0, was " + initialCash);
        }
        this.cash = initialCash;
        this.quantity = 0;
        this.costBasis = BigDecimal.ZERO;
        this.realizedPnl = BigDecimal.ZERO;
    }

    public BigDecimal cash() {
        return cash;
    }

    public long quantity() {
        return quantity;
    }

    public BigDecimal costBasis() {
        return costBasis;
    }

    public BigDecimal realizedPnl() {
        return realizedPnl;
    }

    public boolean isFlat() {
        return quantity == 0;
    }

    /**
     * Applies {@code fill} to this portfolio: a BUY (only while flat) or
     * a full-position SELL (only while long, for exactly the held
     * quantity). Every precondition is validated before any field is
     * changed, so a thrown exception leaves the portfolio exactly as it
     * was.
     *
     * @throws NullPointerException  if {@code fill} is null
     * @throws IllegalStateException if the fill is inconsistent with the
     *                                current position (BUY while long,
     *                                SELL while flat or for other than
     *                                the full held quantity), if a BUY
     *                                costs more than available cash, or
     *                                if a SELL would leave cash negative
     */
    public void apply(Fill fill) {
        Objects.requireNonNull(fill, "fill must not be null");

        if (fill.side() == OrderSide.BUY) {
            applyBuy(fill);
        } else {
            applySell(fill);
        }
    }

    private void applyBuy(Fill fill) {
        if (!isFlat()) {
            throw new IllegalStateException(
                    "cannot BUY while already long (quantity=" + quantity + "); V1 has no pyramiding");
        }

        BigDecimal totalCost = fill.fillPrice().multiply(BigDecimal.valueOf(fill.quantity()))
                .add(fill.commission());
        if (totalCost.compareTo(cash) > 0) {
            throw new IllegalStateException(
                    "BUY cost (%s) exceeds available cash (%s)".formatted(totalCost, cash));
        }

        this.cash = cash.subtract(totalCost);
        this.quantity = fill.quantity();
        this.costBasis = totalCost;
        // realizedPnl unchanged
    }

    private void applySell(Fill fill) {
        if (isFlat()) {
            throw new IllegalStateException("cannot SELL while flat");
        }
        if (fill.quantity() != quantity) {
            throw new IllegalStateException(
                    "SELL quantity (%d) must equal the full held quantity (%d); V1 has no partial exits"
                            .formatted(fill.quantity(), quantity));
        }

        BigDecimal proceeds = fill.fillPrice().multiply(BigDecimal.valueOf(fill.quantity()))
                .subtract(fill.commission());
        BigDecimal newCash = cash.add(proceeds);
        if (newCash.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "SELL would leave cash negative (%s); commission/slippage configuration must prevent this"
                            .formatted(newCash));
        }

        BigDecimal newRealizedPnl = realizedPnl.add(proceeds).subtract(costBasis);

        this.cash = newCash;
        this.quantity = 0;
        this.costBasis = BigDecimal.ZERO;
        this.realizedPnl = newRealizedPnl;
    }

    /**
     * Returns an immutable observation of the current portfolio state
     * marked to {@code close} on {@code date}. This does not mutate the
     * portfolio; the current close is never stored.
     *
     * @throws NullPointerException     if {@code date} or {@code close}
     *                                   is null
     * @throws IllegalArgumentException if {@code close} is not positive
     */
    public EquityPoint markToMarket(LocalDate date, BigDecimal close) {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(close, "close must not be null");
        if (close.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("close must be > 0, was " + close);
        }

        return new EquityPoint(date, cash, quantity, costBasis, realizedPnl, close);
    }
}
