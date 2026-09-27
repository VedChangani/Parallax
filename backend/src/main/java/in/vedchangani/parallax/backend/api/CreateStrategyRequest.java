package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The {@code POST /api/strategies} request envelope (D-31). Read by exactly
 * one strict parser — {@code StrategyDefinitionCodec.parseRequest(String,
 * Class)} — never Spring's global JSON binding (D-31 §8), so the nested
 * {@code definition} document is subject to the same D-30 strict rules
 * (unknown/duplicate/null properties, no numeric coercion, strings-only
 * {@code Constant}/{@code CashFraction}) as every other strategy-definition
 * request. There is no {@code ownerId} field: the owner always comes from
 * {@code CurrentUser}, never from the request body.
 *
 * <p>{@code name} and {@code description} are already required by the
 * strict reader itself (missing/null creator properties fail before Bean
 * Validation runs); {@link NotBlank}/{@link Size} add the constraints the
 * strict JSON reader has no vocabulary for (blank text, length bounds). The
 * recursive {@code definition} tree is deliberately not Bean-Validated —
 * D-30 remains solely responsible for its semantics.
 */
public record CreateStrategyRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        StrategyDefinitionDto definition) {
}
