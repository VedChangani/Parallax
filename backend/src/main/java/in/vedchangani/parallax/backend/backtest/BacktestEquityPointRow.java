package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.portfolio.EquityPoint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The immutable row shape for one {@code backtest_equity_point} record
 * (D-34 Batch 1). Deliberately does not carry {@code marketValue}/{@code
 * equity}/{@code unrealizedPnl}: those remain derived by the engine's own
 * {@link EquityPoint}, exactly as D-22 designed it — storing them here
 * would duplicate state that can only ever be recomputed from {@code cash},
 * {@code quantity}, {@code costBasis} and {@code close} anyway.
 */
record BacktestEquityPointRow(LocalDate barDate, BigDecimal cash, long quantity, BigDecimal costBasis,
                               BigDecimal realizedPnl, BigDecimal close) {

    BacktestEquityPointRow {
        Objects.requireNonNull(barDate, "barDate must not be null");
        Objects.requireNonNull(cash, "cash must not be null");
        Objects.requireNonNull(costBasis, "costBasis must not be null");
        Objects.requireNonNull(realizedPnl, "realizedPnl must not be null");
        Objects.requireNonNull(close, "close must not be null");
    }

    static BacktestEquityPointRow of(EquityPoint point) {
        Objects.requireNonNull(point, "point must not be null");
        return new BacktestEquityPointRow(point.date(), point.cash(), point.quantity(), point.costBasis(),
                point.realizedPnl(), point.close());
    }
}
