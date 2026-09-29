package in.vedchangani.parallax.engine.portfolio;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

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

    public EquityPoint markToMarket(LocalDate date, BigDecimal close) {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(close, "close must not be null");
        if (close.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("close must be > 0, was " + close);
        }

        return new EquityPoint(date, cash, quantity, costBasis, realizedPnl, close);
    }
}
