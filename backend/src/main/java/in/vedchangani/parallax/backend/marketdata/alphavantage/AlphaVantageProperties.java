package in.vedchangani.parallax.backend.marketdata.alphavantage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable Alpha Vantage HTTP configuration, bound from {@code
 * parallax.alphavantage.*}. {@code apiKey} is deliberately unvalidated here
 * — a blank key must never fail application startup (D-33 Batch 2); {@link
 * AlphaVantageMarketDataProvider} is the sole place that rejects a blank key,
 * at request time.
 *
 * <p>Every other property is validated eagerly in the compact constructor so
 * a misconfigured deployment fails at startup rather than on the first
 * request.
 */
@ConfigurationProperties(prefix = "parallax.alphavantage")
public record AlphaVantageProperties(
        String apiKey,
        String baseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        DataSize maxResponseSize) {

    public AlphaVantageProperties {
        apiKey = apiKey == null ? "" : apiKey;
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        Objects.requireNonNull(maxResponseSize, "maxResponseSize must not be null");

        validateBaseUrl(baseUrl);
        if (!connectTimeout.isPositive()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (!requestTimeout.isPositive()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        if (maxResponseSize.toBytes() <= 0) {
            throw new IllegalArgumentException("maxResponseSize must be positive");
        }
    }

    private static void validateBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("baseUrl must be a valid URI", e);
        }
        String scheme = uri.getScheme();
        boolean httpScheme = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        if (!httpScheme || uri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must be an absolute http(s) URL");
        }
    }

    /**
     * Redacts {@link #apiKey} — the record's default, generated {@code
     * toString()} would otherwise include it verbatim, which risks leaking
     * it into a log line or an unrelated error/diagnostic message that
     * happens to print this configuration object (D-33's own contract is
     * that the key is "never logged, never included in an exception
     * message" — this closes the one place a record's own defaults would
     * have quietly broken that). A blank key (the default; D-33 Batch 2)
     * is shown as blank, not falsely reported as redacted, since there is
     * nothing there to hide.
     */
    @Override
    public String toString() {
        String redactedApiKey = apiKey.isEmpty() ? "" : "***REDACTED***";
        return "AlphaVantageProperties[apiKey=" + redactedApiKey + ", baseUrl=" + baseUrl + ", connectTimeout="
                + connectTimeout + ", requestTimeout=" + requestTimeout + ", maxResponseSize=" + maxResponseSize
                + "]";
    }
}
