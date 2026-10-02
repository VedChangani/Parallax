package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.result.BacktestConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacktestConfigMapperTest {

    private final BacktestConfigMapper mapper = new BacktestConfigMapper();

    private static BacktestConfigRequest request(String initialCapital, String commissionPerFill,
                                                  String slippageRate, String startDate, String endDate) {
        return new BacktestConfigRequest(initialCapital, commissionPerFill, slippageRate, startDate, endDate);
    }

    private static BacktestConfigRequest valid() {
        return request("10000", "1.00", "0.0005", "2020-01-02", "2023-12-29");
    }

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
        BacktestConfig config = mapper.toEngine(request("100", "0", "0.0010", "2020-01-02", "2020-01-03"));
        assertEquals(new BigDecimal("0.001"), config.slippageRate());
    }

    @Test
    void rejectsANonDecimalInitialCapitalAsMalformed() {
        MalformedBacktestConfigException e = assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("not-a-number", "1", "0.001", "2020-01-02", "2020-01-03")));
        assertEquals("initialCapital", e.path());
    }

    @Test
    void rejectsALeadingZeroDecimalAsMalformed() {
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
        assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("1e2147483649", "1", "0.001", "2020-01-02", "2020-01-03")));
    }

    @Test
    void rejectsNonPositiveInitialCapitalAsInvalid() {
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

    @Test
    void decimalAtExactlyTheLengthBoundIsAcceptedBecauseItsCanonicalValueIsTiny() {
        String text = "1." + "0".repeat(98);
        assertEquals(100, text.length());

        BacktestConfig config = mapper.toEngine(request(text, "0", "0", "2020-01-02", "2020-01-03"));
        assertEquals(0, new BigDecimal("1").compareTo(config.initialCapital()));
    }

    @Test
    void decimalOneCharacterBeyondTheLengthBoundIsMalformedNotInvalid() {
        String text = "1." + "0".repeat(99);
        assertEquals(101, text.length());

        MalformedBacktestConfigException e = assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request(text, "0", "0", "2020-01-02", "2020-01-03")));
        assertEquals("initialCapital", e.path());
    }

    @Test
    void initialCapitalWithExactlyEighteenIntegerDigitsIsAccepted() {
        String text = "1" + "0".repeat(17);
        BacktestConfig config = mapper.toEngine(request(text, "0", "0", "2020-01-02", "2020-01-03"));
        assertEquals(0, new BigDecimal(text).compareTo(config.initialCapital()));
    }

    @Test
    void initialCapitalWithNineteenIntegerDigitsIsInvalidNotMalformed() {
        String text = "1" + "0".repeat(18);
        InvalidBacktestConfigException e = assertThrows(InvalidBacktestConfigException.class,
                () -> mapper.toEngine(request(text, "0", "0", "2020-01-02", "2020-01-03")));
        assertEquals("initialCapital", e.path());
    }

    @Test
    void slippageRateWithExactlyEighteenFractionalDigitsIsAccepted() {
        String text = "0." + "9".repeat(18);
        BacktestConfig config = mapper.toEngine(request("100", "0", text, "2020-01-02", "2020-01-03"));
        assertEquals(0, new BigDecimal(text).compareTo(config.slippageRate()));
    }

    @Test
    void slippageRateWithNineteenFractionalDigitsIsInvalidNotMalformed() {
        String text = "0." + "9".repeat(19);
        InvalidBacktestConfigException e = assertThrows(InvalidBacktestConfigException.class,
                () -> mapper.toEngine(request("100", "0", text, "2020-01-02", "2020-01-03")));
        assertEquals("slippageRate", e.path());
    }

    @Test
    void commissionAtExactlyEighteenIntegerAndEighteenFractionalDigitsIsAccepted() {
        String text = "1".repeat(18) + "." + "9".repeat(18);
        BacktestConfig config = mapper.toEngine(request("100", text, "0", "2020-01-02", "2020-01-03"));
        assertEquals(0, new BigDecimal(text).compareTo(config.commissionPerFill()));
    }

    @Test
    void prefixesFieldPathsWithTheSuppliedRootPath() {
        MalformedBacktestConfigException e = assertThrows(MalformedBacktestConfigException.class,
                () -> mapper.toEngine(request("bad", "1", "0.001", "2020-01-02", "2020-01-03"), "config"));
        assertEquals("config.initialCapital", e.path());
    }
}
