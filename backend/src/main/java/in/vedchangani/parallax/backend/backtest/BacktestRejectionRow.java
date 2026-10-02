package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.strategy.SignalEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

record BacktestRejectionRow(int seq, String reason, Integer orderId, LocalDate executionDate, Long quantity,
                             BigDecimal requiredCash, BigDecimal availableCash, LocalDate signalDate,
                             BigDecimal signalClose, String signalIndicatorsJson) {

    BacktestRejectionRow {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(signalDate, "signalDate must not be null");
        Objects.requireNonNull(signalClose, "signalClose must not be null");
        Objects.requireNonNull(signalIndicatorsJson, "signalIndicatorsJson must not be null");
    }

    static BacktestRejectionRow of(int seq, OrderRejection rejection) {
        Objects.requireNonNull(rejection, "rejection must not be null");
        SignalEvent signal = rejection.signal();
        String json = IndicatorSnapshotJson.write(signal.snapshot());
        return switch (rejection) {
            case OrderRejection.ZeroQuantity zeroQuantity -> new BacktestRejectionRow(seq, "ZERO_QUANTITY",
                    null, null, null, null, null, signal.date(), signal.snapshot().close(), json);
            case OrderRejection.InsufficientCash insufficientCash -> new BacktestRejectionRow(seq,
                    "INSUFFICIENT_CASH", insufficientCash.orderId(), insufficientCash.date(),
                    insufficientCash.quantity(), insufficientCash.requiredCash(), insufficientCash.availableCash(),
                    signal.date(), signal.snapshot().close(), json);
        };
    }
}
