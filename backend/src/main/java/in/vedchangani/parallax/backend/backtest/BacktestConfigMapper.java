package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionMapper;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;

/**
 * Maps a {@link BacktestConfigRequest} (the JSON-transport shape) to the
 * engine's {@link BacktestConfig} (D-34 Batch 2), following the D-30/D-31
 * mapper precedent ({@code StrategyDefinitionMapper}) exactly: this class
 * owns only the syntax rules the engine has no vocabulary for — the shared
 * D-30 decimal grammar for the three monetary/rate fields, and the
 * ISO-8601 date grammar for {@code startDate}/{@code endDate} — and
 * delegates every semantic rule (positivity, bounds, {@code startDate <=
 * endDate}) to the {@link BacktestConfig} constructor, whose {@link
 * IllegalArgumentException} it rewraps as {@link
 * InvalidBacktestConfigException} with the offending field's path.
 *
 * <p>{@code initialCapital}, {@code commissionPerFill} and {@code
 * slippageRate} are parsed directly into {@link BigDecimal} — never through
 * a {@code double} — preserving the exact value the client supplied (D-14:
 * ledger values are exact). This mirrors {@code
 * StrategyDefinitionMapper#parseFraction}, not {@code #parseConstant}.
 */
@Component
public final class BacktestConfigMapper {

    public BacktestConfig toEngine(BacktestConfigRequest dto) {
        return toEngine(dto, "");
    }

    /**
     * Identical mapping to {@link #toEngine(BacktestConfigRequest)}, with
     * every path prefixed by {@code rootPath} — for example {@code
     * "config"} when this arrives nested inside a {@link
     * CreateBacktestRunRequest} envelope, mirroring {@code
     * StrategyDefinitionMapper#toEngine(StrategyDefinitionDto, String)}
     * (D-31 §8).
     */
    public BacktestConfig toEngine(BacktestConfigRequest dto, String rootPath) {
        Objects.requireNonNull(dto, "dto must not be null");
        Objects.requireNonNull(rootPath, "rootPath must not be null");

        BigDecimal initialCapital = parseDecimal(dto.initialCapital(), joinPath(rootPath, "initialCapital"));
        BigDecimal commissionPerFill = parseDecimal(dto.commissionPerFill(), joinPath(rootPath, "commissionPerFill"));
        BigDecimal slippageRate = parseDecimal(dto.slippageRate(), joinPath(rootPath, "slippageRate"));
        LocalDate startDate = parseDate(dto.startDate(), joinPath(rootPath, "startDate"));
        LocalDate endDate = parseDate(dto.endDate(), joinPath(rootPath, "endDate"));

        return construct(() -> new BacktestConfig(initialCapital, commissionPerFill, slippageRate, startDate, endDate),
                rootPath);
    }

    private static String joinPath(String rootPath, String field) {
        return rootPath.isEmpty() ? field : rootPath + "." + field;
    }

    /**
     * Parses {@code text} against the D-30 decimal grammar directly into a
     * {@link BigDecimal}, never through a {@code double} — the same
     * approach as {@code StrategyDefinitionMapper#parseFraction}. A
     * grammar-valid literal whose exponent falls outside {@code
     * BigDecimal}'s representable range (its scale is a 32-bit {@code int})
     * throws {@link NumberFormatException}/{@link ArithmeticException} from
     * the constructor itself; both are treated as malformed input, with the
     * field path, rather than allowed to escape raw.
     *
     * <p>Phase 9 Batch 2b (D-35): once parsed, the value's canonical
     * precision is bounded by {@code StrategyDefinitionMapper}'s shared
     * {@link StrategyDefinitionMapper#requireWithinCanonicalPrecisionBounds}
     * — a semantic-class (422) check, layered after this method's own
     * malformed-class (400) ones and before {@code BacktestConfig}'s own
     * semantic checks (positivity, {@code [0,1)}, {@code startDate <=
     * endDate}), which remain entirely unchanged.
     */
    private static BigDecimal parseDecimal(String text, String path) {
        requireDecimalGrammar(text, path);
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new MalformedBacktestConfigException(path,
                    "exponent is outside the representable range, was " + quote(text));
        }
        StrategyDefinitionMapper.requireWithinCanonicalPrecisionBounds(value, path, InvalidBacktestConfigException::new);
        return value;
    }

    private static void requireDecimalGrammar(String text, String path) {
        if (text == null) {
            throw new MalformedBacktestConfigException(path,
                    "must match the decimal grammar " + StrategyDefinitionMapper.DECIMAL.pattern() + ", was null");
        }
        // Phase 9 Batch 2b (D-35): the raw-length bound runs before the grammar
        // regex itself, shared with StrategyDefinitionMapper's identical check.
        StrategyDefinitionMapper.requireBoundedLength(text, path, MalformedBacktestConfigException::new);
        Matcher matcher = StrategyDefinitionMapper.DECIMAL.matcher(text);
        if (!matcher.matches()) {
            throw new MalformedBacktestConfigException(path,
                    "must match the decimal grammar " + StrategyDefinitionMapper.DECIMAL.pattern()
                            + ", was " + quote(text));
        }
    }

    private static LocalDate parseDate(String text, String path) {
        if (text == null) {
            throw new MalformedBacktestConfigException(path, "must be an ISO-8601 date (yyyy-MM-dd), was null");
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new MalformedBacktestConfigException(path,
                    "must be an ISO-8601 date (yyyy-MM-dd), was " + quote(text));
        }
    }

    private static String quote(String text) {
        return text == null ? "null" : "\"" + text + "\"";
    }

    private static <T> T construct(Supplier<T> constructor, String path) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException e) {
            throw new InvalidBacktestConfigException(path, e.getMessage());
        }
    }
}
