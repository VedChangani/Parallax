package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.execution.RejectionReason;
import in.vedchangani.parallax.engine.strategy.SignalEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record BacktestRejectionResponse(int seq, RejectionReason reason, Integer orderId, LocalDate executionDate,
                                         Long quantity, String requiredCash, String availableCash,
                                         LocalDate signalDate, String signalClose,
                                         List<BacktestIndicatorValueResponse> signalIndicators) {

    static List<BacktestRejectionResponse> listOf(List<OrderRejection> rejections) {
        List<BacktestRejectionResponse> result = new ArrayList<>(rejections.size());
        for (int i = 0; i < rejections.size(); i++) {
            result.add(of(i + 1, rejections.get(i)));
        }
        return result;
    }

    private static BacktestRejectionResponse of(int seq, OrderRejection rejection) {
        SignalEvent signal = rejection.signal();
        String signalClose = signal.snapshot().close().toPlainString();
        List<BacktestIndicatorValueResponse> indicators = BacktestIndicatorValueResponse.listOf(signal.snapshot());

        return switch (rejection) {
            case OrderRejection.ZeroQuantity zeroQuantity -> new BacktestRejectionResponse(seq,
                    zeroQuantity.reason(), null, null, null, null, null, signal.date(), signalClose, indicators);
            case OrderRejection.InsufficientCash insufficientCash -> new BacktestRejectionResponse(seq,
                    insufficientCash.reason(), insufficientCash.orderId(), insufficientCash.date(),
                    insufficientCash.quantity(), insufficientCash.requiredCash().toPlainString(),
                    insufficientCash.availableCash().toPlainString(), signal.date(), signalClose, indicators);
        };
    }
}
