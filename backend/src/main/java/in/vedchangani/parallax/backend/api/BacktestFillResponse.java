package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;

import java.time.LocalDate;
import java.util.List;

/**
 * The response shape for one reconstructed, integrity-verified engine
 * {@link Fill} (D-34 Batch 3), used as a backtest run's trade entry/exit.
 * Monetary fields are JSON strings ({@link java.math.BigDecimal#toPlainString()}),
 * never JSON numbers — the D-30 precedent, preserved here for the exact
 * canonical value the service returns.
 */
public record BacktestFillResponse(int orderId, LocalDate date, long quantity, String referenceOpen,
                                    String fillPrice, String commission, SignalType signalType, LocalDate signalDate,
                                    String signalClose, List<BacktestIndicatorValueResponse> signalIndicators) {

    static BacktestFillResponse of(Fill fill) {
        SignalEvent signal = fill.signal();
        return new BacktestFillResponse(fill.orderId(), fill.date(), fill.quantity(),
                fill.referenceOpen().toPlainString(), fill.fillPrice().toPlainString(),
                fill.commission().toPlainString(), signal.type(), signal.date(),
                signal.snapshot().close().toPlainString(), BacktestIndicatorValueResponse.listOf(signal.snapshot()));
    }
}
