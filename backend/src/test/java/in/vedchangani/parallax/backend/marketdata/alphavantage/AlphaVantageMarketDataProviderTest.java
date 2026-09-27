package in.vedchangani.parallax.backend.marketdata.alphavantage;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import in.vedchangani.parallax.backend.marketdata.DailyBars;
import in.vedchangani.parallax.backend.marketdata.HistoryDepth;
import in.vedchangani.parallax.backend.marketdata.MarketDataRequestRejectedException;
import in.vedchangani.parallax.backend.marketdata.MarketDataResponseException;
import in.vedchangani.parallax.backend.marketdata.MarketDataUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-33 Batch 2 {@link AlphaVantageMarketDataProvider} tests: a local JDK
 * {@link HttpServer} fixture, never the real Alpha Vantage service. Focused
 * on HTTP/configuration behavior (request shape, status classification,
 * transport failures, response-size enforcement); {@link
 * AlphaVantageDailyParserTest} already covers provider-JSON parsing, so
 * success-case fixtures here are minimal single-bar bodies.
 */
class AlphaVantageMarketDataProviderTest {

    private static final String API_KEY = "test-api-key-0xDEADBEEF";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final DataSize MAX_RESPONSE_SIZE = DataSize.ofMegabytes(8);

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private HttpServer startServer(HttpHandler handler) {
        try {
            HttpServer newServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            newServer.createContext("/query", handler);
            newServer.start();
            this.server = newServer;
            return newServer;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/query";
    }

    private static AlphaVantageProperties defaultProperties(String baseUrl) {
        return new AlphaVantageProperties(API_KEY, baseUrl, CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE);
    }

    private static String fixture(String name) {
        try (InputStream in = AlphaVantageMarketDataProviderTest.class.getResourceAsStream("/alphavantage/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing test fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String successBody(String symbol, String outputSizeLabel) {
        return "{\"Meta Data\":{\"1. Information\":\"Daily Prices\",\"2. Symbol\":\"" + symbol
                + "\",\"3. Last Refreshed\":\"2024-01-08\",\"4. Output Size\":\"" + outputSizeLabel
                + "\",\"5. Time Zone\":\"US/Eastern\"},\"Time Series (Daily)\":{\"2024-01-08\":{"
                + "\"1. open\":\"100.00\",\"2. high\":\"101.00\",\"3. low\":\"99.00\","
                + "\"4. close\":\"100.50\",\"5. volume\":\"1000\"}}}";
    }

    private static void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static HttpHandler fixedResponse(int status, String body, AtomicInteger requestCount) {
        return exchange -> {
            requestCount.incrementAndGet();
            writeResponse(exchange, status, body);
        };
    }

    /** Captures the raw query and echoes the requested symbol back in a minimal success body. */
    private static HttpHandler echoingHandler(AtomicReference<String> capturedQuery, String outputSizeLabel) {
        return exchange -> {
            String rawQuery = exchange.getRequestURI().getRawQuery();
            capturedQuery.set(rawQuery);
            String symbol = parseQuery(rawQuery).get("symbol");
            writeResponse(exchange, 200, successBody(symbol, outputSizeLabel));
        };
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    // --- success ------------------------------------------------------------

    @Test
    void successfulCompactRequestParsesResponse() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        HttpServer testServer = startServer(echoingHandler(capturedQuery, "Compact"));
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(baseUrl(testServer)));

        DailyBars result = provider.fetchDailyBars("IBM", HistoryDepth.COMPACT);

        assertEquals(1, result.bars().size());
        assertTrue(capturedQuery.get().contains("outputsize=compact"));
    }

    @Test
    void successfulFullRequestParsesResponse() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        HttpServer testServer = startServer(echoingHandler(capturedQuery, "Full size"));
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(baseUrl(testServer)));

        DailyBars result = provider.fetchDailyBars("IBM", HistoryDepth.FULL);

        assertEquals(1, result.bars().size());
        assertTrue(capturedQuery.get().contains("outputsize=full"));
    }

    @Test
    void queryContainsExactExpectedParameters() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        HttpServer testServer = startServer(echoingHandler(capturedQuery, "Compact"));
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(baseUrl(testServer)));

        provider.fetchDailyBars("IBM", HistoryDepth.COMPACT);

        Map<String, String> params = parseQuery(capturedQuery.get());
        assertEquals(Set.of("function", "symbol", "outputsize", "datatype", "apikey"), params.keySet());
        assertEquals("TIME_SERIES_DAILY", params.get("function"));
        assertEquals("IBM", params.get("symbol"));
        assertEquals("compact", params.get("outputsize"));
        assertEquals("json", params.get("datatype"));
        assertEquals(API_KEY, params.get("apikey"));
    }

    @Test
    void symbolWithSpecialCharactersIsEncodedOnTheWireAndDecodedOnTheServer() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        HttpServer testServer = startServer(echoingHandler(capturedQuery, "Compact"));
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(baseUrl(testServer)));

        String symbol = "BRK A&B";
        DailyBars result = provider.fetchDailyBars(symbol, HistoryDepth.COMPACT);

        assertEquals(1, result.bars().size());
        assertFalse(capturedQuery.get().contains("BRK A&B"), "raw query must not contain the unencoded symbol");
        assertEquals(symbol, parseQuery(capturedQuery.get()).get("symbol"));
    }

    // --- API key handling -----------------------------------------------------

    @Test
    void blankApiKeyThrowsBeforeSendingRequest() {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer testServer = startServer(fixedResponse(200, successBody("IBM", "Compact"), requestCount));
        AlphaVantageProperties properties =
                new AlphaVantageProperties("", baseUrl(testServer), CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_RESPONSE_SIZE);
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(properties);

        MarketDataUnavailableException ex = assertThrows(MarketDataUnavailableException.class,
                () -> provider.fetchDailyBars("IBM", HistoryDepth.COMPACT));

        assertEquals(0, requestCount.get());
        assertFalse(ex.getMessage().contains(API_KEY));
    }

    // --- status classification ------------------------------------------------

    @Test
    void http429ThrowsMarketDataUnavailable() {
        HttpServer testServer = startServer(fixedResponse(429, "{\"note\":\"slow down\"}", new AtomicInteger()));
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(baseUrl(testServer)));

        MarketDataUnavailableException ex = assertThrows(MarketDataUnavailableException.class,
                () -> provider.fetchDailyBars("IBM", HistoryDepth.COMPACT));
        assertFalse(ex.getMessage().contains(API_KEY));
    }

    @Test
    void http500ThrowsMarketDataResponseException() {
        HttpServer testServer = startServer(fixedResponse(500, "internal error", new AtomicInteger()));
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(baseUrl(testServer)));

        MarketDataResponseException ex = assertThrows(MarketDataResponseException.class,
                () -> provider.fetchDailyBars("IBM", HistoryDepth.COMPACT));
        assertFalse(ex.getMessage().contains(API_KEY));
    }

    @Test
    void redirectIsNotFollowed() {
        HttpServer testServer = startServer(exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(baseUrl(testServer)));

        assertThrows(MarketDataResponseException.class, () -> provider.fetchDailyBars("IBM", HistoryDepth.COMPACT));
    }

    // --- transport failures -----------------------------------------------------

    @Test
    void connectionFailureThrowsMarketDataResponseException() throws IOException {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        String deadUrl = "http://127.0.0.1:" + deadPort + "/query";
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(deadUrl));

        assertThrows(MarketDataResponseException.class, () -> provider.fetchDailyBars("IBM", HistoryDepth.COMPACT));
    }

    @Test
    void requestTimeoutThrowsMarketDataResponseException() {
        HttpServer testServer = startServer(exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writeResponse(exchange, 200, successBody("IBM", "Compact"));
        });
        AlphaVantageProperties properties = new AlphaVantageProperties(
                API_KEY, baseUrl(testServer), CONNECT_TIMEOUT, Duration.ofMillis(200), MAX_RESPONSE_SIZE);
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(properties);

        assertThrows(MarketDataResponseException.class, () -> provider.fetchDailyBars("IBM", HistoryDepth.COMPACT));
    }

    @Test
    void interruptedRequestRestoresInterruptFlagAndThrowsResponseException() throws InterruptedException {
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer testServer = startServer(exchange -> {
            requestReceived.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writeResponse(exchange, 200, successBody("IBM", "Compact"));
        });
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(baseUrl(testServer)));

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<Boolean> interruptedFlag = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                provider.fetchDailyBars("IBM", HistoryDepth.COMPACT);
            } catch (Throwable t) {
                thrown.set(t);
                interruptedFlag.set(Thread.currentThread().isInterrupted());
            }
        });

        worker.start();
        assertTrue(requestReceived.await(2, TimeUnit.SECONDS));
        worker.interrupt();
        worker.join(2000);

        assertTrue(thrown.get() instanceof MarketDataResponseException,
                "expected MarketDataResponseException but got " + thrown.get());
        assertTrue(interruptedFlag.get(), "worker thread interrupt status must be restored");
    }

    // --- response size enforcement -----------------------------------------------

    @Test
    void responseLargerThanConfiguredMaxThrowsMarketDataResponseException() {
        String oversizedBody = "x".repeat(1000);
        HttpServer testServer = startServer(fixedResponse(200, oversizedBody, new AtomicInteger()));
        AlphaVantageProperties properties =
                new AlphaVantageProperties(API_KEY, baseUrl(testServer), CONNECT_TIMEOUT, REQUEST_TIMEOUT, DataSize.ofBytes(10));
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(properties);

        assertThrows(MarketDataResponseException.class, () -> provider.fetchDailyBars("IBM", HistoryDepth.COMPACT));
    }

    // --- parser classification pass-through -----------------------------------

    @Test
    void parserRejectionPropagatesWithItsOriginalClassification() {
        HttpServer testServer = startServer(fixedResponse(200, fixture("error_message.json"), new AtomicInteger()));
        AlphaVantageMarketDataProvider provider = new AlphaVantageMarketDataProvider(defaultProperties(baseUrl(testServer)));

        assertThrows(MarketDataRequestRejectedException.class, () -> provider.fetchDailyBars("IBM", HistoryDepth.COMPACT));
    }
}
