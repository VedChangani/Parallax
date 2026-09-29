package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;

import java.util.List;
import java.util.Map;

public record BacktestIndicatorValueResponse(IndicatorType type, int period, String value) {

    static List<BacktestIndicatorValueResponse> listOf(IndicatorSnapshot snapshot) {
        return snapshot.values().entrySet().stream()
                .map(BacktestIndicatorValueResponse::of)
                .toList();
    }

    private static BacktestIndicatorValueResponse of(Map.Entry<IndicatorSpec, Double> entry) {
        IndicatorSpec spec = entry.getKey();
        return new BacktestIndicatorValueResponse(spec.type(), spec.period(), Double.toString(entry.getValue()));
    }
}
