package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestRunDetail;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.Trade;

import java.time.LocalDate;

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
