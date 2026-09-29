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

    /**
     * {@code -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?} — the approved
     * D-30 decimal grammar. Public so a second, unrelated JSON boundary
     * (D-34's {@code BacktestConfigMapper}) can reuse the exact same syntax
     * rule rather than duplicating it; this is the only D-30 source change
     * D-34 makes.
     */
    public static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

    /**
     * Phase 9 Batch 2b (D-36) defensive bound: the maximum raw decimal
     * literal length accepted, checked before any {@link BigDecimal} or
     * {@code double} parsing is attempted — a resource-bound input check,
     * not a semantic rule. A literal this size (100 characters) is already
     * far beyond any real monetary value or trading fraction; the bound
     * exists to keep an adversarial literal (an enormous digit run, or an
     * exponent with an enormous digit count) from ever reaching a parser.
     * Public, following the {@link #DECIMAL} precedent, so {@code
     * BacktestConfigMapper} shares the exact same bound rather than
     * duplicating it.
     */
    public static final int MAX_DECIMAL_LITERAL_LENGTH = 100;

    /**
     * Phase 9 Batch 2b (D-36) defensive bound: the maximum number of
     * integer digits a decimal literal's <em>canonical</em> value (after
     * {@link BigDecimal#stripTrailingZeros()} — the same canonicalization
     * {@code BacktestConfig}/{@code CashFraction} already apply) may have.
     * Checked only after the literal is already known to satisfy {@link
     * #DECIMAL} and {@link #MAX_DECIMAL_LITERAL_LENGTH}; this is the
     * canonical-<em>magnitude</em> bound (18 integer + 18 fractional = 36
     * significant digits at most), distinct from and layered on top of the
     * raw-length bound above.
     */
    public static final int MAX_INTEGER_DIGITS = 18;

    /**
     * Phase 9 Batch 2b (D-36) defensive bound: the maximum number of
     * fractional digits a decimal literal's canonical value may have. See
     * {@link #MAX_INTEGER_DIGITS}.
     */
    public static final int MAX_FRACTION_DIGITS = 18;

    /**
     * Phase 9 Batch 2b (D-36): rejects {@code text} before it is ever
     * passed to {@link BigDecimal} or {@link Double#parseDouble}
     * construction if it exceeds {@link #MAX_DECIMAL_LITERAL_LENGTH} — a
     * defensive parser-input bound, always a <em>malformed</em>-class
     * (400) failure, never a semantic one, regardless of what the literal
     * would otherwise parse to. {@code text} is assumed non-null (the
     * caller's own null check runs first, so the "was null" message stays
     * with the grammar check, not this one).
     */
    public static void requireBoundedLength(String text, String path,
                                             BiFunction<String, String, ? extends RuntimeException> malformed) {
        if (text.length() > MAX_DECIMAL_LITERAL_LENGTH) {
            throw malformed.apply(path, "decimal literal exceeds the maximum length of "
                    + MAX_DECIMAL_LITERAL_LENGTH + " characters, was " + text.length());
        }
    }

    /**
     * Phase 9 Batch 2b (D-36): rejects {@code value} — already
     * successfully parsed from a grammar-valid, length-bounded literal —
     * if its <em>canonical</em> form (after {@link
     * BigDecimal#stripTrailingZeros()}; deliberately <strong>not</strong>
     * {@code BacktestConfig}'s further {@code setScale(0)} floor, which
     * would materialize a huge digit string for an astronomically
     * negative scale — exactly the resource cost this bound exists to
     * avoid) needs more than {@link #MAX_INTEGER_DIGITS} integer digits or
     * {@link #MAX_FRACTION_DIGITS} fractional digits. {@link
     * BigDecimal#precision()}/{@link BigDecimal#scale()} are O(1)-ish
     * metadata reads (proportional only to the already length-bounded
     * literal's own digit count), so this never materializes a large
     * number even when {@code scale} itself is astronomically large (for
     * example {@code "1e2147483648"}, whose canonical integer-digit count
     * — computed here as {@code precision - scale}, in {@code long}
     * arithmetic to avoid overflowing {@code int} — is far beyond the
     * bound without ever building a 2-billion-digit value).
     *
     * <p>Always a semantic-class (422) failure: the literal parsed fine,
     * its magnitude is simply outside the accepted domain — the same
     * classification as an existing semantic rule (D-30/D-34), never a
     * malformed/400 one.
     */
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

    // --- DTO -> engine -------------------------------------------------------

    public StrategyDefinition toEngine(StrategyDefinitionDto dto) {
        return toEngine(dto, "");
    }

    /**
     * The D-31 REST envelope boundary (D-31 §8): identical mapping to
     * {@link #toEngine(StrategyDefinitionDto)}, with every path prefixed by
     * {@code rootPath} — for example {@code "definition"} when the
     * definition arrives nested inside a {@code CreateStrategyRequest}
     * envelope, so a semantic-validation error names the exact envelope
     * field (e.g. {@code "definition.entryCondition.left"}). {@code
     * rootPath = ""} (used by the single-argument overload) reproduces the
     * original, unprefixed D-30 paths exactly — no other mapping behavior
     * changes.
     */
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
     *
     * <p><strong>Phase 9 Batch 2b (D-36) note:</strong> only the raw-length
     * bound ({@link #requireBoundedLength}, applied inside {@link
     * #matchDecimal} above, uniformly for every decimal literal this
     * mapper parses) applies here — deliberately <strong>not</strong>
     * {@link #requireWithinCanonicalPrecisionBounds}. That bound exists to
     * cap the cost of exact {@link BigDecimal} arithmetic, and {@code
     * Constant} is specifically the one D-30 field that never becomes a
     * {@link BigDecimal} at all (this method's own long-standing design,
     * documented above): it is a {@code double}, evaluated once per bar at
     * O(1) cost regardless of magnitude. Applying the 18/18 canonical
     * bound here would conflict with D-30's explicit, tested guarantee
     * that the <em>full</em> {@code double} range — down to {@code
     * Double.MIN_VALUE} (~4.9E-324) and up to {@code Double.MAX_VALUE}
     * (~1.8E308) — round-trips exactly through this codec ({@code
     * StrategyDefinitionCodecTest#constantCanonicalFormsMatchDoubleToString}).
     * The length bound alone is the correct, non-conflicting defense for
     * this field: it still rejects a pathologically long literal (the
     * actual resource risk for a field that is parsed once and never
     * stored as an arbitrary-precision value), without narrowing the
     * legitimate double domain D-30 already commits to.
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
     *
     * <p>Phase 9 Batch 2b (D-36): once the {@link BigDecimal} is
     * successfully constructed, its canonical precision is bounded by
     * {@link #requireWithinCanonicalPrecisionBounds} — a separate,
     * semantic-class (422) check, layered after this method's own
     * malformed-class (400) ones. {@code CashFraction}'s further {@code 0
     * < fraction <= 1} range check remains the engine constructor's own
     * concern (unchanged), so a value can fail either check independently.
     */
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
        // Phase 9 Batch 2b (D-36): the raw-length bound runs before the grammar
        // regex itself - the cheapest possible guard, ahead of everything else.
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
            case ATR -> IndicatorTypeDto.ATR;
            case ROC -> IndicatorTypeDto.ROC;
        };
    }
}
