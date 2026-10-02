package in.vedchangani.parallax.backend.backtest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record CreateBacktestRunRequest(
        @Positive long strategyId,
        @Min(1) int strategyVersion,
        @Positive long datasetId,
        @Min(1) int datasetVersion,
        BacktestConfigRequest config) {
}
