package in.vedchangani.parallax.backend.strategy.definition;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.Nulls;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConditionDto.Compare.class, name = "compare"),
        @JsonSubTypes.Type(value = ConditionDto.All.class, name = "all"),
        @JsonSubTypes.Type(value = ConditionDto.Any.class, name = "any")
})
public sealed interface ConditionDto {

    record Compare(OperandDto left, OperatorDto operator, OperandDto right) implements ConditionDto {
    }

    record All(@JsonSetter(contentNulls = Nulls.FAIL) List<ConditionDto> conditions) implements ConditionDto {
    }

    record Any(@JsonSetter(contentNulls = Nulls.FAIL) List<ConditionDto> conditions) implements ConditionDto {
    }
}
