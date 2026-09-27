package in.vedchangani.parallax.backend.strategy.definition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The backend transport/canonical representation of the engine's sealed
 * {@code PositionSizing} hierarchy (D-30). Discriminated by a
 * {@code "type"} property: {@code "cashFraction"}.
 *
 * <p>{@link CashFraction#fraction()} is a JSON string, never a JSON
 * number — see {@code StrategyDefinitionMapper} for why (exact
 * {@code BigDecimal} round-trip, no double-rounding, no reformatting by
 * PostgreSQL {@code jsonb}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PositionSizingDto.CashFraction.class, name = "cashFraction")
})
public sealed interface PositionSizingDto {

    record CashFraction(String fraction) implements PositionSizingDto {
    }
}
