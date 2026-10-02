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
