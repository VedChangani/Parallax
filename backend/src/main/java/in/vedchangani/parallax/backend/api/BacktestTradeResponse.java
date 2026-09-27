package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestRunDetail;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.Trade;

import java.time.LocalDate;

/**
 * The response shape for one engine-derived {@link Trade} (D-34 Batch 3),
 * produced only via {@link Trade#fromFills(java.util.List)} — never a
 * separate backend P&amp;L calculation. A {@link Trade.Closed} exposes its
 * own derived {@code realizedPnl()}/{@code totalCommission()}/
 * {@code totalSlippageCost()} unchanged; a {@link Trade.Open} has none of
 * those (D-24 — an open position is never counted as a completed trade), so
 * {@code realizedPnl} is {@code null} and {@code totalCommission}/{@code
 * totalSlippageCost} report only the entry fill's own derived {@code
 * commission()}/{@code slippageCost()}. The trailing {@code mark*} fields
 * are populated for an open trade only, from the run's own final {@link
 * EquityPoint} (already reconstructed and integrity-verified) — since V1 is
 * single-position, that point's mark is exactly this trade's mark. No new
 * arithmetic is performed anywhere in this class.
 */
public record BacktestTradeResponse(TradeStatus status, BacktestFillResponse entry, BacktestFillResponse exit,
                                     long quantity, String realizedPnl, String totalCommission,
                                     String totalSlippageCost, LocalDate markDate, String markClose,
                                     String marketValue, String unrealizedPnl) {

    public enum TradeStatus {
        OPEN,
        CLOSED
    }

    static BacktestTradeResponse of(Trade trade, BacktestRunDetail detail) {
        return switch (trade) {
            case Trade.Closed closed -> new BacktestTradeResponse(TradeStatus.CLOSED,
                    BacktestFillResponse.of(closed.entry()), BacktestFillResponse.of(closed.exit()),
                    closed.quantity(), closed.realizedPnl().toPlainString(), closed.totalCommission().toPlainString(),
                    closed.totalSlippageCost().toPlainString(), null, null, null, null);
            case Trade.Open open -> {
                EquityPoint finalPoint = detail.equityCurve().getLast();
                yield new BacktestTradeResponse(TradeStatus.OPEN, BacktestFillResponse.of(open.entry()), null,
                        open.quantity(), null, open.entry().commission().toPlainString(),
                        open.entry().slippageCost().toPlainString(), finalPoint.date(),
                        finalPoint.close().toPlainString(), finalPoint.marketValue().toPlainString(),
                        finalPoint.unrealizedPnl().toPlainString());
            }
        };
    }
}
