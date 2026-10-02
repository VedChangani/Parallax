package in.vedchangani.parallax.backend.strategy.definition;

import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public final class StrategyDefinitionMapper {

    public static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

    public static final int MAX_DECIMAL_LITERAL_LENGTH = 100;

    public static final int MAX_INTEGER_DIGITS = 18;

    public static final int MAX_FRACTION_DIGITS = 18;

    public static void requireBoundedLength(String text, String path,
                                             BiFunction<String, String, ? extends RuntimeException> malformed) {
        if (text.length() > MAX_DECIMAL_LITERAL_LENGTH) {
            throw malformed.apply(path, "decimal literal exceeds the maximum length of "
                    + MAX_DECIMAL_LITERAL_LENGTH + " characters, was " + text.length());
        }
    }

    public static void requireWithinCanonicalPrecisionBounds(BigDecimal value, String path,
                                                               BiFunction<String, String, ? extends RuntimeException> invalid) {
        BigDecimal canonical = value.stripTrailingZeros();
        int precision = canonical.precision();
        int scale = canonical.scale();
        long fractionDigits = Math.max(scale, 0);
        long integerDigits = Math.max((long) precision - scale, 1);
        if (integerDigits > MAX_INTEGER_DIGITS || fractionDigits > MAX_FRACTION_DIGITS) {
            throw invalid.apply(path, "canonical value exceeds the maximum precision of "
                    + MAX_INTEGER_DIGITS + " integer digit(s) and " + MAX_FRACTION_DIGITS
                    + " fractional digit(s) (had " + integerDigits + " integer digit(s) and " + fractionDigits
                    + " fractional digit(s))");
        }
    }

    public StrategyDefinition toEngine(StrategyDefinitionDto dto) {
        return toEngine(dto, "");
    }

    public StrategyDefinition toEngine(StrategyDefinitionDto dto, String rootPath) {
        Objects.requireNonNull(dto, "dto must not be null");
        Objects.requireNonNull(rootPath, "rootPath must not be null");

        Condition entry = toEngineCondition(dto.entryCondition(), joinPath(rootPath, "entryCondition"));
        Condition exit = toEngineCondition(dto.exitCondition(), joinPath(rootPath, "exitCondition"));
        PositionSizing sizing = toEnginePositionSizing(dto.positionSizing(), joinPath(rootPath, "positionSizing"));

        return construct(() -> new StrategyDefinition(entry, exit, sizing), rootPath);
    }

    private static String joinPath(String rootPath, String field) {
        return rootPath.isEmpty() ? field : rootPath + "." + field;
    }

    private Condition toEngineCondition(ConditionDto dto, String path) {
        return switch (dto) {
            case ConditionDto.Compare c -> {
                Operand left = toEngineOperand(c.left(), path + ".left");
                Operator operator = toEngineOperator(c.operator());
                Operand right = toEngineOperand(c.right(), path + ".right");
                yield construct(() -> new Condition.Compare(left, operator, right), path);
            }
            case ConditionDto.All a -> {
                List<Condition> children = toEngineConditionList(a.conditions(), path);
                yield construct(() -> new Condition.All(children), path);
            }
            case ConditionDto.Any a -> {
                List<Condition> children = toEngineConditionList(a.conditions(), path);
                yield construct(() -> new Condition.Any(children), path);
            }
        };
    }

    private List<Condition> toEngineConditionList(List<ConditionDto> dtos, String parentPath) {
        List<Condition> result = new ArrayList<>(dtos.size());
        for (int i = 0; i < dtos.size(); i++) {
            result.add(toEngineCondition(dtos.get(i), parentPath + ".conditions[" + i + "]"));
        }
        return result;
    }

    private Operand toEngineOperand(OperandDto dto, String path) {
        return switch (dto) {
            case OperandDto.Indicator ind -> {
                IndicatorType type = toEngineIndicatorType(ind.indicator());
                int period = ind.period();
                yield construct(() -> new Operand.IndicatorRef(new IndicatorSpec(type, period)), path);
            }
            case OperandDto.Close ignored -> new Operand.Close();
            case OperandDto.Constant constant -> {
                double value = parseConstant(constant.value(), path);
                yield construct(() -> new Operand.Constant(value), path);
            }
        };
    }

    private PositionSizing toEnginePositionSizing(PositionSizingDto dto, String path) {
        return switch (dto) {
            case PositionSizingDto.CashFraction cf -> {
                BigDecimal fraction = parseFraction(cf.fraction(), path);
                yield construct(() -> new PositionSizing.CashFraction(fraction), path);
            }
        };
    }

    private Operator toEngineOperator(OperatorDto dto) {
        return switch (dto) {
            case GT -> Operator.GT;
            case LT -> Operator.LT;
        };
    }

    private IndicatorType toEngineIndicatorType(IndicatorTypeDto dto) {
        return switch (dto) {
            case SMA -> IndicatorType.SMA;
            case EMA -> IndicatorType.EMA;
            case RSI -> IndicatorType.RSI;
            case ATR -> IndicatorType.ATR;
            case ROC -> IndicatorType.ROC;
        };
    }

    private double parseConstant(String text, String path) {
        Matcher m = matchDecimal(text, path);
        double parsed = Double.parseDouble(text);
        if (parsed == 0.0 && !isZeroMantissa(m)) {
            throw new InvalidStrategyDefinitionException(path,
                    "value underflows to zero as a double, was " + text);
        }
        return parsed;
    }

    private static boolean isZeroMantissa(Matcher m) {
        if (!"0".equals(m.group(1))) {
            return false;
        }
        String fractional = m.group(2);
        if (fractional == null) {
            return true;
        }
        for (int i = 1; i < fractional.length(); i++) {
            if (fractional.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private BigDecimal parseFraction(String text, String path) {
        matchDecimal(text, path);
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new MalformedStrategyDefinitionException(path,
                    "exponent is outside the representable range, was " + quote(text));
        }
        requireWithinCanonicalPrecisionBounds(value, path, InvalidStrategyDefinitionException::new);
        return value;
    }

    private Matcher matchDecimal(String text, String path) {
        if (text == null) {
            throw new MalformedStrategyDefinitionException(path,
                    "must match the decimal grammar " + DECIMAL.pattern() + ", was null");
        }
        requireBoundedLength(text, path, MalformedStrategyDefinitionException::new);
        Matcher m = DECIMAL.matcher(text);
        if (!m.matches()) {
            throw new MalformedStrategyDefinitionException(path,
                    "must match the decimal grammar " + DECIMAL.pattern() + ", was " + quote(text));
        }
        return m;
    }

    private static String quote(String text) {
        return text == null ? "null" : "\"" + text + "\"";
    }

    private static <T> T construct(Supplier<T> constructor, String path) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException e) {
            throw new InvalidStrategyDefinitionException(path, e.getMessage());
        }
    }

    public StrategyDefinitionDto toDto(StrategyDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        return new StrategyDefinitionDto(
                toDtoCondition(definition.entryCondition()),
                toDtoCondition(definition.exitCondition()),
                toDtoPositionSizing(definition.positionSizing()));
    }

    private ConditionDto toDtoCondition(Condition condition) {
        return switch (condition) {
            case Condition.Compare c ->
                    new ConditionDto.Compare(toDtoOperand(c.left()), toDtoOperator(c.operator()), toDtoOperand(c.right()));
            case Condition.All a -> new ConditionDto.All(a.conditions().stream().map(this::toDtoCondition).toList());
            case Condition.Any a -> new ConditionDto.Any(a.conditions().stream().map(this::toDtoCondition).toList());
        };
    }

    private OperandDto toDtoOperand(Operand operand) {
        return switch (operand) {
            case Operand.IndicatorRef ref ->
                    new OperandDto.Indicator(toDtoIndicatorType(ref.spec().type()), ref.spec().period());
            case Operand.Close ignored -> new OperandDto.Close();
            case Operand.Constant constant -> new OperandDto.Constant(Double.toString(constant.value()));
        };
    }

    private PositionSizingDto toDtoPositionSizing(PositionSizing sizing) {
        return switch (sizing) {
            case PositionSizing.CashFraction cf -> new PositionSizingDto.CashFraction(cf.fraction().toPlainString());
        };
    }

    private OperatorDto toDtoOperator(Operator operator) {
        return switch (operator) {
            case GT -> OperatorDto.GT;
            case LT -> OperatorDto.LT;
        };
    }

    private IndicatorTypeDto toDtoIndicatorType(IndicatorType type) {
        return switch (type) {
            case SMA -> IndicatorTypeDto.SMA;
            case EMA -> IndicatorTypeDto.EMA;
            case RSI -> IndicatorTypeDto.RSI;
            case ATR -> IndicatorTypeDto.ATR;
            case ROC -> IndicatorTypeDto.ROC;
        };
    }
}
