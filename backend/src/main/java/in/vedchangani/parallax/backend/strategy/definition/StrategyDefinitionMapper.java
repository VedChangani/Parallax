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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps between the backend's JSON-transport DTO tree ({@code
 * strategy.definition}) and the engine's {@code StrategyDefinition}
 * (D-30). Stateless.
 *
 * <p><strong>DTO → engine</strong> is a recursive, depth-first,
 * path-tracked traversal. It owns exactly two syntax rules that the engine
 * has no vocabulary for — the decimal grammar for {@code Constant.value}
 * and {@code CashFraction.fraction}, and rejecting a nonzero literal that
 * underflows to {@code 0.0} as a {@code double} — and delegates every
 * other rule (finiteness, the {@code -0.0} fold, non-empty condition
 * groups, indicator period bounds, {@code CashFraction} bounds and
 * canonicalization) to the engine constructors, which remain the sole
 * source of truth for them. An engine {@link IllegalArgumentException} is
 * caught and rethrown as {@link InvalidStrategyDefinitionException} with
 * the path of the DTO node that produced it; nothing else is caught,
 * because a strictly-parsed DTO tree cannot present the engine with a null
 * it would reject with {@link NullPointerException} — such an NPE is a
 * backend bug, and propagates unchanged.
 *
 * <p><strong>Engine → DTO</strong> is total: it never throws for a
 * validly-constructed engine object.
 */
@Component
public final class StrategyDefinitionMapper {

    /** {@code -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?} — the approved D-30 decimal grammar. */
    static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

    // --- DTO -> engine -------------------------------------------------------

    public StrategyDefinition toEngine(StrategyDefinitionDto dto) {
        Objects.requireNonNull(dto, "dto must not be null");

        Condition entry = toEngineCondition(dto.entryCondition(), "entryCondition");
        Condition exit = toEngineCondition(dto.exitCondition(), "exitCondition");
        PositionSizing sizing = toEnginePositionSizing(dto.positionSizing(), "positionSizing");

        return construct(() -> new StrategyDefinition(entry, exit, sizing), "");
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
        };
    }

    /**
     * Parses {@code text} against the D-30 decimal grammar, then rejects a
     * nonzero literal that rounds to exactly {@code 0.0}/{@code -0.0} as a
     * {@code double} (a mapper-owned semantic rule — underflow, not a
     * grammar failure). Overflow (a literal that rounds to
     * {@code Infinity}) is deliberately let through: the engine's own
     * finiteness check rejects it.
     *
     * <p>The zero-vs-underflow distinction is made from the grammar's own
     * capture groups (the integer and fractional digit runs), never from a
     * {@code new BigDecimal(text)} construction — an extreme exponent (for
     * example {@code "1e-9999999999"}) is grammar-valid and handled
     * correctly by {@link Double#parseDouble} (which saturates to
     * {@code 0.0}/{@code Infinity} rather than throwing), but {@link
     * BigDecimal}'s scale is a 32-bit {@code int} and throws for such an
     * exponent. This keeps a raw {@link NumberFormatException} from ever
     * escaping constant parsing.
     */
    private double parseConstant(String text, String path) {
        Matcher m = matchDecimal(text, path);
        double parsed = Double.parseDouble(text);
        if (parsed == 0.0 && !isZeroMantissa(m)) {
            throw new InvalidStrategyDefinitionException(path,
                    "value underflows to zero as a double, was " + text);
        }
        return parsed;
    }

    /** Whether the matched literal's digits (ignoring any exponent) are all zero. */
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

    /**
     * Parses {@code text} against the D-30 decimal grammar directly into a
     * {@link BigDecimal}, never through a double. A grammar-valid literal
     * whose exponent falls outside {@code BigDecimal}'s representable
     * range (its scale is a 32-bit {@code int}) throws {@link
     * NumberFormatException} or {@link ArithmeticException} from the
     * constructor itself; both are treated as malformed client input, with
     * the field path, rather than allowed to escape raw.
     */
    private BigDecimal parseFraction(String text, String path) {
        matchDecimal(text, path);
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new MalformedStrategyDefinitionException(path,
                    "exponent is outside the representable range, was " + quote(text));
        }
    }

    private Matcher matchDecimal(String text, String path) {
        if (text == null) {
            throw new MalformedStrategyDefinitionException(path,
                    "must match the decimal grammar " + DECIMAL.pattern() + ", was null");
        }
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

    // --- engine -> DTO -------------------------------------------------------

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
        };
    }
}
