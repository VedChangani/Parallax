package in.vedchangani.parallax.backend.strategy.definition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The backend transport/canonical representation of the engine's sealed
 * {@code Operand} hierarchy (D-30). Discriminated by a {@code "type"}
 * property: {@code "indicator"}, {@code "close"}, {@code "constant"}.
 *
 * <p>{@link Constant#value()} is a JSON string, never a JSON number — see
 * {@code StrategyDefinitionMapper} for why (exact {@code Double.toString}
 * round-trip, no double-rounding by a JSON library, and no reformatting
 * by PostgreSQL {@code jsonb}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OperandDto.Indicator.class, name = "indicator"),
        @JsonSubTypes.Type(value = OperandDto.Close.class, name = "close"),
        @JsonSubTypes.Type(value = OperandDto.Constant.class, name = "constant")
})
public sealed interface OperandDto {

    record Indicator(IndicatorTypeDto indicator, int period) implements OperandDto {
    }

    record Close() implements OperandDto {
    }

    record Constant(String value) implements OperandDto {
    }
}
