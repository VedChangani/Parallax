package in.vedchangani.parallax.engine.result;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderSide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A trade derived after the run from the executed {@link Fill}s. A
 * {@link Closed} trade has both an entry (BUY) and an exit (SELL) fill;
 * an {@link Open} trade has only its entry fill, because the position
 * was still held when the run ended — it is never force-liquidated
 * (D-8). All P&amp;L and cost figures are derived from the stored fills,
 * never stored separately, so they cannot drift from {@code Portfolio}'s
 * own accounting.
 */
public sealed interface Trade {

    /**
     * The BUY fill that opened this trade.
     */
    Fill entry();

    /**
     * The quantity of this trade, equal to {@code entry().quantity()}.
     */
    default long quantity() {
        return entry().quantity();
    }

    /**
     * A trade still open at the end of the run: an entry fill with no
     * exit. Its mark-to-market is represented by the run's final
     * {@code EquityPoint}, not by anything on this type.
     */
    record Open(Fill entry) implements Trade {

        public Open {
            Objects.requireNonNull(entry, "entry must not be null");
            if (entry.side() != OrderSide.BUY) {
                throw new IllegalArgumentException("entry must be a BUY fill, was " + entry.side());
            }
        }
    }

    /**
     * A completed trade: a BUY fill followed by a SELL fill for the same
     * quantity. {@link #realizedPnl()} is derived to match
     * {@code Portfolio}'s accounting exactly: {@code Portfolio} sets
     * {@code costBasis = entry.quantity * entry.fillPrice +
     * entry.commission} on the BUY, then on the SELL adds
     * {@code proceeds - costBasis} where
     * {@code proceeds = exit.quantity * exit.fillPrice -
     * exit.commission} — the same two expressions computed here.
     */
    record Closed(Fill entry, Fill exit) implements Trade {

        public Closed {
            Objects.requireNonNull(entry, "entry must not be null");
            Objects.requireNonNull(exit, "exit must not be null");
            if (entry.side() != OrderSide.BUY) {
                throw new IllegalArgumentException("entry must be a BUY fill, was " + entry.side());
            }
            if (exit.side() != OrderSide.SELL) {
                throw new IllegalArgumentException("exit must be a SELL fill, was " + exit.side());
            }
            if (entry.quantity() != exit.quantity()) {
                throw new IllegalArgumentException(
                        "entry quantity (%d) must equal exit quantity (%d)"
                                .formatted(entry.quantity(), exit.quantity()));
            }
            if (!exit.date().isAfter(entry.date())) {
                throw new IllegalArgumentException(
                        "exit date (%s) must be after entry date (%s)".formatted(exit.date(), entry.date()));
            }
            if (exit.orderId() <= entry.orderId()) {
                throw new IllegalArgumentException(
                        "exit orderId (%d) must be greater than entry orderId (%d)"
                                .formatted(exit.orderId(), entry.orderId()));
            }
        }

        /**
         * {@code (exit.quantity * exit.fillPrice - exit.commission) -
         * (entry.quantity * entry.fillPrice + entry.commission)},
         * matching {@code Portfolio}'s realized P&amp;L for this trade
         * exactly.
         */
        public BigDecimal realizedPnl() {
            BigDecimal proceeds = exit.fillPrice().multiply(BigDecimal.valueOf(exit.quantity()))
                    .subtract(exit.commission());
            BigDecimal costBasis = entry.fillPrice().multiply(BigDecimal.valueOf(entry.quantity()))
                    .add(entry.commission());
            return proceeds.subtract(costBasis);
        }

        /**
         * {@code entry.commission + exit.commission}.
         */
        public BigDecimal totalCommission() {
            return entry.commission().add(exit.commission());
        }

        /**
         * {@code entry.slippageCost() + exit.slippageCost()}.
         */
        public BigDecimal totalSlippageCost() {
            return entry.slippageCost().add(exit.slippageCost());
        }
    }

    /**
     * Parses a chronological sequence of execution fills into trades:
     * each BUY paired with the next SELL becomes a {@link Closed} trade,
     * and a trailing unpaired BUY becomes an {@link Open} trade. This is
     * a parser of an already-ordered, already-valid fill sequence — it
     * does not sort, repair, or skip invalid input.
     *
     * @throws NullPointerException     if {@code fills} or any element is null
     * @throws IllegalArgumentException if the sequence does not alternate
     *                                   BUY, SELL, BUY, SELL, ... starting
     *                                   with a BUY
     */
    static List<Trade> fromFills(List<Fill> fills) {
        Objects.requireNonNull(fills, "fills must not be null");
        for (Fill fill : fills) {
            Objects.requireNonNull(fill, "fills must not contain a null element");
        }

        List<Trade> trades = new ArrayList<>();
        Fill pendingEntry = null;

        for (Fill fill : fills) {
            if (pendingEntry == null) {
                if (fill.side() != OrderSide.BUY) {
                    throw new IllegalArgumentException(
                            "expected a BUY fill to open a trade, found " + fill.side() + " at orderId "
                                    + fill.orderId());
                }
                pendingEntry = fill;
            } else {
                if (fill.side() != OrderSide.SELL) {
                    throw new IllegalArgumentException(
                            "expected a SELL fill to close the open trade (entry orderId "
                                    + pendingEntry.orderId() + "), found " + fill.side() + " at orderId "
                                    + fill.orderId());
                }
                trades.add(new Closed(pendingEntry, fill));
                pendingEntry = null;
            }
        }

        if (pendingEntry != null) {
            trades.add(new Open(pendingEntry));
        }

        return List.copyOf(trades);
    }
}
