package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.dataset.Dataset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDatasetRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Pattern(regexp = Dataset.SYMBOL_PATTERN) String symbol) {
}
