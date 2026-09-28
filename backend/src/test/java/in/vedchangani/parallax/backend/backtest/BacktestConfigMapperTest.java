package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.result.BacktestConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link BacktestConfigMapper} (D-34 Batch 2): the D-30
 * decimal grammar reused for the three monetary/rate fields, exact
 * {@link BigDecimal} preservation, ISO-8601 date parsing, and the
 * malformed-vs-invalid distinction (mirroring {@code
 * StrategyDefinitionMapperTest}'s own style).
 */
class BacktestConfigMapperTest {

    private final BacktestConfigMapper mapper = new BacktestConfigMapper();

    private static BacktestConfigRequest request(String initialCapital, String commissionPerFill,
                                                  String slippageRate, String startDate, String endDate) {
        return new BacktestConfigRequest(initialCapital, commissionPerFill, slippageRate, startDate, endDate);
    }

    private static BacktestConfigRequest valid() {
        return request("10000", "1.00", "0.0005", "2020-01-02", "2023-12-29");
    }

    // --- happy path: exact BigDecimal preservation ----------------------------

    @Test
    void mapsAValidRequestToAnEquivalentEngineConfig() {
        BacktestConfig config = mapper.toEngine(valid());

        assertEquals(0, new BigDecimal("10000").compareTo(config.initialCapital()));
        assertEquals(new BigDecimal("1.00").stripTrailingZeros(), config.commissionPerFill());
        assertEquals(new BigDecimal("0.0005"), config.slippageRate());
        assertEquals(LocalDate.of(2020, 1, 2), config.startDate());
        assertEquals(LocalDate.of(2023, 12, 29), config.endDate());
    }

    @Test
    void preservesExactScaleBeforeEngineCanonicalization() {
        // "0.0010" parses to a BigDecimal with scale 4 before BacktestConfig's own
        // stripTrailingZeros() canonicalizes it - proving the mapper never routes
        // this value through a double, which could not represent trailing zeros at all.
        BacktestConfig config = mapper.toEngine(request("100", "0", "0.0010", "2020-01-02", "2020-01-03"));
        assertEquals(new BigDecimal("0.001"), config.slippageRate());
    }

    // --- malformed syntax: 400-class, distinguishable from semantic errors ----

    @Test
    void rejectsANonDecimalInitialCapitalAsMalformed() {
        MalformedBacktestConfigException e = assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("not-a-number", "1", "0.001", "2020-01-02", "2020-01-03")));
        assertEquals("initialCapital", e.path());
    }

    @Test
    void rejectsALeadingZeroDecimalAsMalformed() {
        // The D-30 grammar rejects leading zeros like "01" - matching StrategyDefinitionMapperTest's
        // own coverage of the shared grammar.
        assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("01", "1", "0.001", "2020-01-02", "2020-01-03")));
    }

    @Test
    void rejectsATrailingCommaOrWhitespaceDecimalAsMalformed() {
        assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("100 ", "1", "0.001", "2020-01-02", "2020-01-03")));
    }

    @Test
    void rejectsNullDecimalFieldAsMalformed() {
        assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request(null, "1", "0.001", "2020-01-02", "2020-01-03")));
    }

    @Test
    void rejectsAMalformedDateAsMalformed() {
        MalformedBacktestConfigException e = assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("100", "1", "0.001", "02/01/2020", "2020-01-03")));
        assertEquals("startDate", e.path());
    }

    @Test
    void rejectsNullDateFieldAsMalformed() {
        assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("100", "1", "0.001", "2020-01-02", null)));
    }

    @Test
    void anExtremeExponentThatOverflowsBigDecimalScaleIsMalformedNotInvalid() {
        // Grammar-valid, but "1e2147483649" needs a scale one below Integer.MIN_VALUE,
        // so new BigDecimal(text) itself throws NumberFormatException("Exponent overflow.")
        // - malformed input, not a semantic BacktestConfig failure (mirrors
        // StrategyDefinitionMapperTest's identical coverage of parseFraction).
        assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("1e2147483649", "1", "0.001", "2020-01-02", "2020-01-03")));
    }

    // --- semantically invalid: 422-class, engine BacktestConfig remains authoritative ---

    @Test
    void rejectsNonPositiveInitialCapitalAsInvalid() {
        // BacktestConfig validates all five fields together in one constructor (unlike
        // StrategyDefinition, which is built from individually-constructed engine
        // objects each with their own path) - so a semantic failure's path is the
        // whole config, not a specific field name.
        InvalidBacktestConfigException e = assertThrows(InvalidBacktestConfigException.class,
                () -> mapper.toEngine(request("0", "1", "0.001", "2020-01-02", "2020-01-03")));
        assertEquals("", e.path());
    }

    @Test
    void rejectsNegativeCommissionAsInvalid() {
        assertThrows(InvalidBacktestConfigException.class,
                () -> mapper.toEngine(request("100", "-1", "0.001", "2020-01-02", "2020-01-03")));
    }

    @Test
    void rejectsSlippageRateOfOneOrMoreAsInvalid() {
        assertThrows(InvalidBacktestConfigException.class,
                () -> mapper.toEngine(request("100", "1", "1", "2020-01-02", "2020-01-03")));
    }

    @Test
    void rejectsStartDateAfterEndDateAsInvalid() {
        InvalidBacktestConfigException e = assertThrows(InvalidBacktestConfigException.class,
                () -> mapper.toEngine(request("100", "1", "0.001", "2020-01-05", "2020-01-02")));
        assertEquals("", e.path());
    }

    // --- defensive numeric bounds (Phase 9 Batch 2b, D-36) --------------------

    @Test
    void decimalAtExactlyTheLengthBoundIsAcceptedBecauseItsCanonicalValueIsTiny() {
        // "1" padded with trailing zeros after the decimal point to exactly 100 raw
        // characters - canonically (stripTrailingZeros) this is just "1", 1 integer
        // digit, 0 fractional digits, far inside the 18/18 precision bound.
        String text = "1." + "0".repeat(98);
        assertEquals(100, text.length());

        BacktestConfig config = mapper.toEngine(request(text, "0", "0", "2020-01-02", "2020-01-03"));
        assertEquals(0, new BigDecimal("1").compareTo(config.initialCapital()));
    }

    @Test
    void decimalOneCharacterBeyondTheLengthBoundIsMalformedNotInvalid() {
        String text = "1." + "0".repeat(99); // 101 characters
        assertEquals(101, text.length());

        MalformedBacktestConfigException e = assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request(text, "0", "0", "2020-01-02", "2020-01-03")));
        assertEquals("initialCapital", e.path());
    }

    @Test
    void initialCapitalWithExactlyEighteenIntegerDigitsIsAccepted() {
        String text = "1" + "0".repeat(17); // 10^17, 18 integer digits
        BacktestConfig config = mapper.toEngine(request(text, "0", "0", "2020-01-02", "2020-01-03"));
        assertEquals(0, new BigDecimal(text).compareTo(config.initialCapital()));
    }

    @Test
    void initialCapitalWithNineteenIntegerDigitsIsInvalidNotMalformed() {
        // Still grammar-valid and still a genuinely positive capital (so it would
        // have passed BacktestConfig's own initialCapital > 0 check) - isolates the
        // NEW precision bound: must fail specifically on precision, as
        // InvalidBacktestConfigException (422), never Malformed (400).
        String text = "1" + "0".repeat(18); // 10^18, 19 integer digits
        InvalidBacktestConfigException e = assertThrows(InvalidBacktestConfigException.class,
                () -> mapper.toEngine(request(text, "0", "0", "2020-01-02", "2020-01-03")));
        assertEquals("initialCapital", e.path());
    }

    @Test
    void slippageRateWithExactlyEighteenFractionalDigitsIsAccepted() {
        String text = "0." + "9".repeat(18); // < 1, satisfies slippageRate's [0,1) range too
        BacktestConfig config = mapper.toEngine(request("100", "0", text, "2020-01-02", "2020-01-03"));
        assertEquals(0, new BigDecimal(text).compareTo(config.slippageRate()));
    }

    @Test
    void slippageRateWithNineteenFractionalDigitsIsInvalidNotMalformed() {
        String text = "0." + "9".repeat(19); // still < 1 - isolates precision from the range check
        InvalidBacktestConfigException e = assertThrows(InvalidBacktestConfigException.class,
                () -> mapper.toEngine(request("100", "0", text, "2020-01-02", "2020-01-03")));
        assertEquals("slippageRate", e.path());
    }

    @Test
    void commissionAtExactlyEighteenIntegerAndEighteenFractionalDigitsIsAccepted() {
        // Both bounds hit simultaneously (18 + 18 = 36 significant digits at most).
        String text = "1".repeat(18) + "." + "9".repeat(18);
        BacktestConfig config = mapper.toEngine(request("100", text, "0", "2020-01-02", "2020-01-03"));
        assertEquals(0, new BigDecimal(text).compareTo(config.commissionPerFill()));
    }

    // --- root path prefixing (mirrors StrategyDefinitionMapper's own overload) ---

    @Test
    void prefixesFieldPathsWithTheSuppliedRootPath() {
        MalformedBacktestConfigException e = assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("bad", "1", "0.001", "2020-01-02", "2020-01-03"), "config"));
        assertEquals("config.initialCapital", e.path());
    }
}
