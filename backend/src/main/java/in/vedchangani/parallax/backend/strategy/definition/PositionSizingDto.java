package in.vedchangani.parallax.backend.strategy.definition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PositionSizingDto.CashFraction.class, name = "cashFraction")
})
public sealed interface PositionSizingDto {

    record CashFraction(String fraction) implements PositionSizingDto {
    }
}
