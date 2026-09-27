package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.dataset.Dataset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The {@code POST /api/datasets} request envelope (D-32). Read by the same
 * strict D-30 reader used for every other backend request body —
 * {@code StrategyDefinitionCodec.parseRequest(String, Class)} — never
 * Spring's global JSON binding, so an unknown, duplicate, or null property
 * fails before Bean Validation ever runs. There is no {@code ownerId}
 * field: the owner always comes from {@code CurrentUser}.
 *
 * <p>{@code symbol} is validated against the exact V1 grammar (D-32); it is
 * never uppercased or otherwise normalized — a lowercase symbol is
 * rejected outright.
 */
public record CreateDatasetRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Pattern(regexp = Dataset.SYMBOL_PATTERN) String symbol) {
}
