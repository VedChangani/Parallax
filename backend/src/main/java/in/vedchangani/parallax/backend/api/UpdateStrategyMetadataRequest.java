package in.vedchangani.parallax.backend.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStrategyMetadataRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description) {
}
