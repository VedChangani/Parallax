package in.vedchangani.parallax.engine.result;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderSide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public sealed interface Trade {

    Fill entry();

    default long quantity() {
        return entry().quantity();
    }

    record Open(Fill entry) implements Trade {

        public Open {
            Objects.requireNonNull(entry, "entry must not be null");
            if (entry.side() != OrderSide.BUY) {
                throw new IllegalArgumentException("entry must be a BUY fill, was " + entry.side());
            }
        }
    }

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

        public BigDecimal realizedPnl() {
            BigDecimal proceeds = exit.fillPrice().multiply(BigDecimal.valueOf(exit.quantity()))
                    .subtract(exit.commission());
            BigDecimal costBasis = entry.fillPrice().multiply(BigDecimal.valueOf(entry.quantity()))
                    .add(entry.commission());
            return proceeds.subtract(costBasis);
        }

        public BigDecimal totalCommission() {
            return entry.commission().add(exit.commission());
        }

        public BigDecimal totalSlippageCost() {
            return entry.slippageCost().add(exit.slippageCost());
        }
    }

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
