package in.vedchangani.parallax.backend.marketdata.alphavantage;

import in.vedchangani.parallax.backend.marketdata.DailyBars;
import in.vedchangani.parallax.backend.marketdata.HistoryDepth;
import in.vedchangani.parallax.backend.marketdata.MarketDataProvider;
import in.vedchangani.parallax.backend.marketdata.MarketDataResponseException;
import in.vedchangani.parallax.backend.marketdata.MarketDataUnavailableException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Blocking JDK {@link HttpClient} adapter fetching Alpha Vantage {@code
 * TIME_SERIES_DAILY} data. This class owns HTTP transport and response
 * classification only — {@link AlphaVantageDailyParser} remains the sole
 * authority for parsing/validating provider JSON (D-33 Batch 1); its
 * exceptions propagate unchanged, never wrapped.
 *
 * <p>No retries, no throttling, no async behavior — a single blocking
 * request/response cycle per call, matching the D-33 Batch 2 contract.
 * Redirects are never followed: an Alpha Vantage redirect is not a
 * documented success path, and following one silently would risk sending
 * the API key to an unintended host.
 */
public final class AlphaVantageMarketDataProvider implements MarketDataProvider {

    private static final String FUNCTION = "TIME_SERIES_DAILY";
    private static final String OUTPUT_SIZE_COMPACT = "compact";
    private static final String OUTPUT_SIZE_FULL = "full";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final AlphaVantageProperties properties;
    private final HttpClient httpClient;

    public AlphaVantageMarketDataProvider(AlphaVantageProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    /** Test seam: injects a caller-built {@link HttpClient} (e.g. shorter timeouts). */
    AlphaVantageMarketDataProvider(AlphaVantageProperties properties, HttpClient httpClient) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public DailyBars fetchDailyBars(String symbol, HistoryDepth depth) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(depth, "depth must not be null");

        String apiKey = properties.apiKey();
        if (apiKey.isBlank()) {
            throw new MarketDataUnavailableException("alpha vantage api key is not configured");
        }

        HttpRequest request = buildRequest(symbol, depth, apiKey);
        HttpResponse<InputStream> response = send(request);

        try (InputStream body = response.body()) {
            int status = response.statusCode();
            if (status == HTTP_TOO_MANY_REQUESTS) {
                throw new MarketDataUnavailableException("alpha vantage rate limit exceeded");
            }
            if (status < 200 || status >= 300) {
                throw new MarketDataResponseException("alpha vantage returned HTTP " + status);
            }
            String json = readBounded(body, properties.maxResponseSize().toBytes());
            return AlphaVantageDailyParser.parse(symbol, depth, json);
        } catch (IOException e) {
            throw new MarketDataResponseException("alpha vantage response could not be read");
        }
    }

    private HttpRequest buildRequest(String symbol, HistoryDepth depth, String apiKey) {
        URI uri = buildUri(symbol, depth, apiKey);
        try {
            return HttpRequest.newBuilder(uri)
                    .timeout(properties.requestTimeout())
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new MarketDataResponseException("alpha vantage request could not be built");
        }
    }

    /**
     * Builds the request URI by concatenating the already percent-encoded
     * query onto {@code baseUrl} directly, rather than via the multi-argument
     * {@link URI} constructor: that constructor treats its {@code query}
     * argument as unencoded text and quotes it again, which would corrupt an
     * already-encoded {@code %XX} sequence (e.g. turning a symbol's encoded
     * {@code &} into a literal one).
     */
    private URI buildUri(String symbol, HistoryDepth depth, String apiKey) {
        String outputSize = depth == HistoryDepth.COMPACT ? OUTPUT_SIZE_COMPACT : OUTPUT_SIZE_FULL;
        String query = "function=" + FUNCTION
                + "&symbol=" + encode(symbol)
                + "&outputsize=" + outputSize
                + "&datatype=json"
                + "&apikey=" + encode(apiKey);
        try {
            return new URI(properties.baseUrl() + "?" + query);
        } catch (URISyntaxException e) {
            throw new MarketDataResponseException("alpha vantage request URI could not be built");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new MarketDataResponseException("alpha vantage request failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketDataResponseException("alpha vantage request was interrupted");
        }
    }

    /**
     * Reads at most {@code maxBytes + 1} bytes so an oversized response is
     * rejected deterministically without buffering an unbounded body.
     */
    private static String readBounded(InputStream in, long maxBytes) throws IOException {
        long limit = maxBytes + 1;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while (total < limit
                && (read = in.read(chunk, 0, (int) Math.min(chunk.length, limit - total))) != -1) {
            buffer.write(chunk, 0, read);
            total += read;
        }
        if (total > maxBytes) {
            throw new MarketDataResponseException("alpha vantage response exceeded maximum size");
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
