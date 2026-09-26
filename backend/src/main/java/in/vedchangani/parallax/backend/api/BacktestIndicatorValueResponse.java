package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;

import java.util.List;
import java.util.Map;

/**
 * One indicator value from a {@link IndicatorSnapshot} that triggered a
 * fill or rejection (D-34 Batch 3), shared by {@link BacktestFillResponse}
 * and {@link BacktestRejectionResponse}. {@code value} is a JSON string
 * produced by {@link Double#toString(double)} — never a JSON number — the
 * same exactness precedent D-30/D-34 use throughout, so it round-trips
 * exactly for every finite {@code double}.
 */
public record BacktestIndicatorValueResponse(IndicatorType type, int period, String value) {

    /**
     * {@code snapshot.values()} is already canonically ordered (D-17:
     * indicator type declaration order, then period ascending); this
     * preserves that order exactly, never reordering it.
     */
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
