package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStrategyRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        StrategyDefinitionDto definition) {
}
