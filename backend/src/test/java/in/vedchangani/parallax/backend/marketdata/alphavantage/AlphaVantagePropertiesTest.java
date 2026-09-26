package in.vedchangani.parallax.backend.marketdata.alphavantage;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D-33 Batch 2 {@link AlphaVantageProperties} validation: plain Java, no
 * Spring context. Confirms a blank API key never fails construction (only
 * {@link AlphaVantageMarketDataProvider} rejects it, at request time) and
 * every other property is validated eagerly.
 */
class AlphaVantagePropertiesTest {

    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final DataSize MAX_RESPONSE_SIZE = DataSize.ofMegabytes(8);

    @Test
    void blankApiKeyDoesNotFailConstruction() {
        AlphaVantageProperties properties = assertDoesNotThrow(() ->
                new AlphaVantageProperties("", BASE_URL, CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE));
        assertEquals("", properties.apiKey());
    }

    @Test
    void nullApiKeyIsNormalizedToBlank() {
        AlphaVantageProperties properties =
                new AlphaVantageProperties(null, BASE_URL, CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE);
        assertEquals("", properties.apiKey());
    }

    @Test
    void rejectsNonHttpBaseUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlphaVantageProperties("key", "ftp://example.com", CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE));
    }

    @Test
    void rejectsMalformedBaseUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlphaVantageProperties("key", "not a url", CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE));
    }

    @Test
    void rejectsBaseUrlWithoutHost() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlphaVantageProperties("key", "https:///query", CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE));
    }

    @Test
    void rejectsZeroOrNegativeConnectTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlphaVantageProperties("key", BASE_URL, Duration.ZERO, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE));
        assertThrows(IllegalArgumentException.class, () ->
                new AlphaVantageProperties("key", BASE_URL, Duration.ofSeconds(-1), REQUEST_TIMEOUT, MAX_RESPONSE_SIZE));
    }

    @Test
    void rejectsZeroOrNegativeRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlphaVantageProperties("key", BASE_URL, CONNECT_TIMEOUT, Duration.ZERO, MAX_RESPONSE_SIZE));
        assertThrows(IllegalArgumentException.class, () ->
                new AlphaVantageProperties("key", BASE_URL, CONNECT_TIMEOUT, Duration.ofSeconds(-1), MAX_RESPONSE_SIZE));
    }

    @Test
    void rejectsZeroOrNegativeMaxResponseSize() {
        assertThrows(IllegalArgumentException.class, () ->
                new AlphaVantageProperties("key", BASE_URL, CONNECT_TIMEOUT, REQUEST_TIMEOUT, DataSize.ofBytes(0)));
        assertThrows(IllegalArgumentException.class, () ->
                new AlphaVantageProperties("key", BASE_URL, CONNECT_TIMEOUT, REQUEST_TIMEOUT, DataSize.ofBytes(-1)));
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () ->
                new AlphaVantageProperties("key", null, CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE));
        assertThrows(NullPointerException.class, () ->
                new AlphaVantageProperties("key", BASE_URL, null, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE));
        assertThrows(NullPointerException.class, () ->
                new AlphaVantageProperties("key", BASE_URL, CONNECT_TIMEOUT, null, MAX_RESPONSE_SIZE));
        assertThrows(NullPointerException.class, () ->
                new AlphaVantageProperties("key", BASE_URL, CONNECT_TIMEOUT, REQUEST_TIMEOUT, null));
    }
}
