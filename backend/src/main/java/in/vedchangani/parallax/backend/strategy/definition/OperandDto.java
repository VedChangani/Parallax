package in.vedchangani.parallax.backend.strategy.definition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
