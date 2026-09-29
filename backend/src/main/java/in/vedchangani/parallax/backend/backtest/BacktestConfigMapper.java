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

@Component
public final class BacktestConfigMapper {

    public BacktestConfig toEngine(BacktestConfigRequest dto) {
        return toEngine(dto, "");
    }

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
