package in.vedchangani.parallax.backend.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The {@code PATCH /api/strategies/{id}} request body (D-31): a full
 * metadata replacement — both fields are required, and an empty {@code
 * description} is valid. Never modifies any {@code StrategyVersion}.
 */
public record UpdateStrategyMetadataRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description) {
}
