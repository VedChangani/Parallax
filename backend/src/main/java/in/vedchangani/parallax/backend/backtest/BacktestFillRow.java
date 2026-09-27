package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.strategy.SignalEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The immutable row shape for one {@code backtest_fill} record (D-34
 * Batch 1). Deliberately does not carry {@code side} or {@code
 * slippageCost}: both are derived from the engine {@link Fill} (D-21),
 * exactly as {@code Fill} itself never stores them.
 */
record BacktestFillRow(int orderId, LocalDate fillDate, long quantity, BigDecimal referenceOpen,
                        BigDecimal fillPrice, BigDecimal commission, String signalType, LocalDate signalDate,
                        BigDecimal signalClose, String signalIndicatorsJson) {

    BacktestFillRow {
        Objects.requireNonNull(fillDate, "fillDate must not be null");
        Objects.requireNonNull(referenceOpen, "referenceOpen must not be null");
        Objects.requireNonNull(fillPrice, "fillPrice must not be null");
        Objects.requireNonNull(commission, "commission must not be null");
        Objects.requireNonNull(signalType, "signalType must not be null");
        Objects.requireNonNull(signalDate, "signalDate must not be null");
        Objects.requireNonNull(signalClose, "signalClose must not be null");
        Objects.requireNonNull(signalIndicatorsJson, "signalIndicatorsJson must not be null");
    }

    static BacktestFillRow of(Fill fill) {
        Objects.requireNonNull(fill, "fill must not be null");
        SignalEvent signal = fill.signal();
        String json = IndicatorSnapshotJson.write(signal.snapshot());
        return new BacktestFillRow(fill.orderId(), fill.date(), fill.quantity(), fill.referenceOpen(),
                fill.fillPrice(), fill.commission(), signal.type().name(), signal.date(), signal.snapshot().close(),
                json);
    }
}
