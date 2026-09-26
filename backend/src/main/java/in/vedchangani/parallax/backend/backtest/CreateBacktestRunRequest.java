package in.vedchangani.parallax.backend.backtest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * The D-34 {@code POST} create-run request envelope (Batch 2 DTO layer;
 * REST wiring is Batch 3), read by the same strict D-30 reader as every
 * other request body ({@code StrategyDefinitionCodec.parseRequest(String,
 * Class)}). There is no {@code ownerId} field — the owner always comes from
 * {@code CurrentUser}, never from the request body (D-31/D-32 precedent).
 *
 * <p>{@code strategyId}/{@code datasetId}/{@code strategyVersion}/{@code
 * datasetVersion} are already required by the strict reader itself (missing
 * or null creator properties fail before Bean Validation runs); {@link
 * Positive}/{@link Min} add the numeric-range constraints the strict JSON
 * reader has no vocabulary for, mirroring {@code CreateStrategyRequest}'s
 * own {@code @NotBlank}/{@code @Size} precedent (D-31 §8). {@code config}
 * is deliberately not Bean-Validated here — {@link BacktestConfigMapper}
 * remains solely responsible for its syntax and semantics.
 */
public record CreateBacktestRunRequest(
        @Positive long strategyId,
        @Min(1) int strategyVersion,
        @Positive long datasetId,
        @Min(1) int datasetVersion,
        BacktestConfigRequest config) {
}
