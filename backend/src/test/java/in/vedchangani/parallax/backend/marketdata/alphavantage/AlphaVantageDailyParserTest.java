package in.vedchangani.parallax.backend.marketdata.alphavantage;

import in.vedchangani.parallax.backend.marketdata.DailyBars;
import in.vedchangani.parallax.backend.marketdata.HistoryDepth;
import in.vedchangani.parallax.backend.marketdata.InvalidMarketDataException;
import in.vedchangani.parallax.backend.marketdata.MarketDataCapabilityException;
import in.vedchangani.parallax.backend.marketdata.MarketDataRequestRejectedException;
import in.vedchangani.parallax.backend.marketdata.MarketDataResponseException;
import in.vedchangani.parallax.backend.marketdata.MarketDataUnavailableException;
import in.vedchangani.parallax.engine.data.Bar;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D-33 Batch 1 {@link AlphaVantageDailyParser} tests: plain Java, no Spring
 * context, no real Alpha Vantage call and no API key. Covers the success
 * shape, provider control-response classification, and every JSON
 * strictness/semantic failure category the parser is responsible for.
 */
class AlphaVantageDailyParserTest {

    private static String fixture(String name) {
        try (InputStream in = AlphaVantageDailyParserTest.class.getResourceAsStream("/alphavantage/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing test fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String document(String symbol, String outputSize, String timeSeriesBody) {
        return "{\"Meta Data\":{\"1. Information\":\"Daily Prices\",\"2. Symbol\":\"" + symbol
                + "\",\"3. Last Refreshed\":\"2024-01-08\",\"4. Output Size\":\"" + outputSize
                + "\",\"5. Time Zone\":\"US/Eastern\"},\"Time Series (Daily)\":" + timeSeriesBody + "}";
    }

    private static String bar(String open, String high, String low, String close, String volume) {
        return "{\"1. open\":" + open + ",\"2. high\":" + high + ",\"3. low\":" + low
                + ",\"4. close\":" + close + ",\"5. volume\":" + volume + "}";
    }

    private static String q(String raw) {
        return "\"" + raw + "\"";
    }

    private static String validBar() {
        return bar(q("100.00"), q("105.00"), q("99.00"), q("104.00"), q("1000"));
    }

    private static String singleBarDocument(String timeSeriesBody) {
        return document("IBM", "Compact", timeSeriesBody);
    }

    // --- SUCCESS -----------------------------------------------------------

    @Test
    void parsesNormalFiveDayCompactResponseIntoAscendingBars() {
        DailyBars result = AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, fixture("success_compact.json"));

        List<Bar> bars = result.bars();
        assertEquals(5, bars.size());
        assertEquals(LocalDate.of(2024, 1, 2), bars.get(0).date());
        assertEquals(LocalDate.of(2024, 1, 3), bars.get(1).date());
        assertEquals(LocalDate.of(2024, 1, 4), bars.get(2).date());
        assertEquals(LocalDate.of(2024, 1, 5), bars.get(3).date());
        assertEquals(LocalDate.of(2024, 1, 8), bars.get(4).date());

        Bar first = bars.get(0);
        assertEquals(new BigDecimal("158.0000"), first.open());
        assertEquals(new BigDecimal("159.8000"), first.high());
        assertEquals(new BigDecimal("157.6000"), first.low());
        assertEquals(new BigDecimal("159.4000"), first.close());
        assertEquals(4488000L, first.volume());

        assertEquals("TIME_SERIES_DAILY;outputsize=compact", result.sourceDetail());
    }

    @Test
    void parsesFullResponseAndReportsFullSourceDetail() {
        DailyBars result = AlphaVantageDailyParser.parse("IBM", HistoryDepth.FULL, fixture("success_full.json"));

        assertEquals(5, result.bars().size());
        assertEquals("TIME_SERIES_DAILY;outputsize=full", result.sourceDetail());
    }

    // --- META VALIDATION -----------------------------------------------------

    @Test
    void rejectsMismatchedSymbol() {
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("MSFT", HistoryDepth.COMPACT, fixture("success_compact.json")));
    }

    @Test
    void rejectsCompactRequestAgainstFullMetaAsMismatch() {
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, fixture("success_full.json")));
    }

    @Test
    void fullRequestAgainstCompactMetaProvesCapabilityLimitation() {
        assertThrows(MarketDataCapabilityException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.FULL, fixture("success_compact.json")));
    }

    @Test
    void rejectsMissingMetaProperty() {
        String json = "{\"Meta Data\":{\"4. Output Size\":\"Compact\"},"
                + "\"Time Series (Daily)\":{\"2024-01-02\":" + validBar() + "}}";
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsUnexpectedMetaProperty() {
        String json = "{\"Meta Data\":{\"1. Information\":\"Daily Prices\",\"2. Symbol\":\"IBM\","
                + "\"3. Last Refreshed\":\"2024-01-08\",\"4. Output Size\":\"Compact\","
                + "\"5. Time Zone\":\"US/Eastern\",\"6. Extra\":\"unexpected\"},"
                + "\"Time Series (Daily)\":{\"2024-01-02\":" + validBar() + "}}";
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    // --- SHAPE FAILURES ------------------------------------------------------

    @Test
    void rejectsMalformedJson() {
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, "{ not json "));
    }

    @Test
    void rejectsTrailingTokens() {
        String json = singleBarDocument("{\"2024-01-02\":" + validBar() + "}") + " garbage";
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsDuplicateDateKey() {
        String timeSeries = "{\"2024-01-02\":" + validBar() + ",\"2024-01-02\":" + validBar() + "}";
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, singleBarDocument(timeSeries)));
    }

    @Test
    void rejectsDuplicateNestedBarField() {
        String duplicated = "{\"1. open\":\"100.00\",\"1. open\":\"100.00\",\"2. high\":\"105.00\","
                + "\"3. low\":\"99.00\",\"4. close\":\"104.00\",\"5. volume\":\"1000\"}";
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT,
                        singleBarDocument("{\"2024-01-02\":" + duplicated + "}")));
    }

    @Test
    void rejectsUnexpectedTopLevelProperty() {
        String json = "{\"Meta Data\":{\"2. Symbol\":\"IBM\",\"4. Output Size\":\"Compact\"},"
                + "\"Time Series (Daily)\":{\"2024-01-02\":" + validBar() + "},\"Extra\":\"x\"}";
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsMissingTopLevelProperty() {
        String json = "{\"Meta Data\":{\"2. Symbol\":\"IBM\",\"4. Output Size\":\"Compact\"}}";
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsMissingBarField() {
        String incomplete = "{\"1. open\":\"100.00\",\"2. high\":\"105.00\","
                + "\"3. low\":\"99.00\",\"4. close\":\"104.00\"}";
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT,
                        singleBarDocument("{\"2024-01-02\":" + incomplete + "}")));
    }

    @Test
    void rejectsExtraBarField() {
        String extra = "{\"1. open\":\"100.00\",\"2. high\":\"105.00\",\"3. low\":\"99.00\","
                + "\"4. close\":\"104.00\",\"5. volume\":\"1000\",\"6. extra\":\"x\"}";
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT,
                        singleBarDocument("{\"2024-01-02\":" + extra + "}")));
    }

    @Test
    void rejectsJsonNumberWhereStringIsRequired() {
        String withNumber = bar("100.00", q("105.00"), q("99.00"), q("104.00"), q("1000"));
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT,
                        singleBarDocument("{\"2024-01-02\":" + withNumber + "}")));
    }

    @Test
    void rejectsNullBarField() {
        String withNull = bar("null", q("105.00"), q("99.00"), q("104.00"), q("1000"));
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT,
                        singleBarDocument("{\"2024-01-02\":" + withNull + "}")));
    }

    @Test
    void emptyTimeSeriesIsInvalidNotEmptyDailyBars() {
        String json = singleBarDocument("{}");
        assertThrows(InvalidMarketDataException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    // --- DATE FAILURES ---------------------------------------------------------

    @Test
    void rejectsImpossibleDate() {
        String json = singleBarDocument("{\"2024-02-30\":" + validBar() + "}");
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsWrongDateFormat() {
        String json = singleBarDocument("{\"01-02-2024\":" + validBar() + "}");
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsNonDescendingTimeSeriesOrderAsInvalidMarketData() {
        // Alpha Vantage documents dates newest-first; an older date before a newer
        // one violates that documented order and is a market-data problem (D-33),
        // not a shape problem.
        String timeSeries = "{\"2024-01-02\":" + validBar() + ",\"2024-01-08\":" + validBar() + "}";
        InvalidMarketDataException e = assertThrows(InvalidMarketDataException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, singleBarDocument(timeSeries)));
        assertEquals(LocalDate.of(2024, 1, 8), e.date().orElseThrow());
    }

    // --- NUMERIC FAILURES --------------------------------------------------

    @Test
    void rejectsMalformedDecimal() {
        assertPriceRejected("abc");
    }

    @Test
    void rejectsExponentNotation() {
        assertPriceRejected("1e10");
    }

    @Test
    void rejectsLeadingPlus() {
        assertPriceRejected("+100.00");
    }

    @Test
    void rejectsLeadingZero() {
        assertPriceRejected("0100.00");
    }

    @Test
    void rejectsMissingIntegerPart() {
        assertPriceRejected(".5");
    }

    @Test
    void rejectsMissingFractionalDigits() {
        assertPriceRejected("5.");
    }

    @Test
    void rejectsTooManyIntegerDigits() {
        assertPriceRejected("9999999999999999999");
    }

    private void assertPriceRejected(String malformedOpen) {
        String json = singleBarDocument("{\"2024-01-02\":"
                + bar(q(malformedOpen), q("105.00"), q("99.00"), q("104.00"), q("1000")) + "}");
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsMalformedVolume() {
        String json = singleBarDocument("{\"2024-01-02\":"
                + bar(q("100.00"), q("105.00"), q("99.00"), q("104.00"), q("abc")) + "}");
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsVolumeOverflow() {
        String json = singleBarDocument("{\"2024-01-02\":"
                + bar(q("100.00"), q("105.00"), q("99.00"), q("104.00"), q("9999999999999999999")) + "}");
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    // --- SEMANTIC FAILURES (Bar is the authority) -------------------------------

    @Test
    void rejectsZeroPriceAsInvalidMarketData() {
        String json = singleBarDocument("{\"2024-01-02\":"
                + bar(q("0.00"), q("105.00"), q("99.00"), q("104.00"), q("1000")) + "}");
        InvalidMarketDataException e = assertThrows(InvalidMarketDataException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
        assertEquals(LocalDate.of(2024, 1, 2), e.date().orElseThrow());
    }

    @Test
    void rejectsNegativePriceAsInvalidMarketData() {
        String json = singleBarDocument("{\"2024-01-02\":"
                + bar(q("-5.00"), q("105.00"), q("99.00"), q("104.00"), q("1000")) + "}");
        assertThrows(InvalidMarketDataException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsInvalidHighLowAsInvalidMarketData() {
        String json = singleBarDocument("{\"2024-01-02\":"
                + bar(q("100.00"), q("99.00"), q("90.00"), q("101.00"), q("1000")) + "}");
        assertThrows(InvalidMarketDataException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    @Test
    void rejectsNegativeVolumeAsInvalidMarketData() {
        String json = singleBarDocument("{\"2024-01-02\":"
                + bar(q("100.00"), q("105.00"), q("99.00"), q("104.00"), q("-1000")) + "}");
        assertThrows(InvalidMarketDataException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, json));
    }

    // --- ERROR CLASSIFICATION ---------------------------------------------------

    @Test
    void errorMessageIsClassifiedAsRequestRejected() {
        assertThrows(MarketDataRequestRejectedException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, fixture("error_message.json")));
    }

    @Test
    void premiumEndpointInformationAtFullDepthIsClassifiedAsCapability() {
        assertThrows(MarketDataCapabilityException.class, () -> AlphaVantageDailyParser.parse(
                "IBM", HistoryDepth.FULL, fixture("information_capability_full.json")));
    }

    @Test
    void uppercasePremiumInformationAtFullDepthIsClassifiedAsCapability() {
        // The approved rule is a plain case-insensitive substring match on "premium",
        // not the narrower phrase "premium endpoint".
        String json = "{\"Information\":\"This response requires a PREMIUM subscription for full history.\"}";
        assertThrows(MarketDataCapabilityException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.FULL, json));
    }

    @Test
    void rateLimitInformationWithoutPremiumIsClassifiedAsUnavailableEvenAtFullDepth() {
        // A rate-limit message that never mentions "premium" at all must remain
        // Unavailable, not Capability, even when FULL was requested.
        assertThrows(MarketDataUnavailableException.class, () -> AlphaVantageDailyParser.parse(
                "IBM", HistoryDepth.FULL, fixture("information_rate_limit.json")));
    }

    @Test
    void noteIsClassifiedAsUnavailable() {
        assertThrows(MarketDataUnavailableException.class, () -> AlphaVantageDailyParser.parse(
                "IBM", HistoryDepth.COMPACT, fixture("note_rate_limit.json")));
    }

    @Test
    void unrecognizedResponseIsClassifiedAsResponseException() {
        assertThrows(MarketDataResponseException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, "{\"Foo\":\"bar\"}"));
    }

    // --- SECURITY -----------------------------------------------------------

    @Test
    void noFixtureContainsAnApiKeyParameter() {
        for (String name : List.of("success_compact.json", "success_full.json", "error_message.json",
                "information_capability_full.json", "information_rate_limit.json", "note_rate_limit.json")) {
            String content = fixture(name).toLowerCase(java.util.Locale.ROOT);
            assertFalse(content.contains("apikey"), name + " must not contain an API key");
        }
    }

    @Test
    void exceptionMessagesNeverContainRawProviderText() {
        MarketDataRequestRejectedException rejected = assertThrows(MarketDataRequestRejectedException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, fixture("error_message.json")));
        assertFalse(rejected.getMessage().contains("Invalid API call"));

        MarketDataUnavailableException unavailable = assertThrows(MarketDataUnavailableException.class,
                () -> AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, fixture("note_rate_limit.json")));
        assertFalse(unavailable.getMessage().contains("alphavantage.co"));
        assertFalse(unavailable.getMessage().contains("Thank you for using Alpha Vantage"));
    }

    // --- IMMUTABILITY --------------------------------------------------------

    @Test
    void dailyBarsRejectsNullBars() {
        assertThrows(NullPointerException.class, () -> new DailyBars(null, "TIME_SERIES_DAILY;outputsize=compact"));
    }

    @Test
    void dailyBarsRejectsEmptyBars() {
        assertThrows(IllegalArgumentException.class,
                () -> new DailyBars(List.of(), "TIME_SERIES_DAILY;outputsize=compact"));
    }

    @Test
    void returnedBarsListIsUnmodifiable() {
        DailyBars result = AlphaVantageDailyParser.parse("IBM", HistoryDepth.COMPACT, fixture("success_compact.json"));
        assertThrows(UnsupportedOperationException.class, () -> result.bars().add(result.bars().get(0)));
    }
}
