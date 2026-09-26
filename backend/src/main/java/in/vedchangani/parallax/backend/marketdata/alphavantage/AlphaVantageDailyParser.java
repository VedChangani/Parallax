package in.vedchangani.parallax.backend.marketdata.alphavantage;

import in.vedchangani.parallax.backend.marketdata.DailyBars;
import in.vedchangani.parallax.backend.marketdata.HistoryDepth;
import in.vedchangani.parallax.backend.marketdata.InvalidMarketDataException;
import in.vedchangani.parallax.backend.marketdata.MarketDataCapabilityException;
import in.vedchangani.parallax.backend.marketdata.MarketDataRequestRejectedException;
import in.vedchangani.parallax.backend.marketdata.MarketDataResponseException;
import in.vedchangani.parallax.backend.marketdata.MarketDataUnavailableException;
import in.vedchangani.parallax.engine.data.Bar;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses an Alpha Vantage {@code TIME_SERIES_DAILY} JSON response into
 * {@link DailyBars}. Plain Java, no Spring dependency, independently
 * unit-testable — this class knows nothing about HTTP, API keys, or the
 * {@code Dataset}/persistence model.
 *
 * <p>Before treating a document as a success body, it is classified against
 * Alpha Vantage's own control-response keys ({@code "Error Message"},
 * {@code "Information"}, {@code "Note"}). Only a document with none of
 * those keys, and exactly the two documented success properties, is parsed
 * as data.
 *
 * <p>The parser owns syntax and shape only. {@link Bar} remains the sole
 * semantic authority (positive prices, consistent high/low, non-negative
 * volume) for each bar; its {@link IllegalArgumentException} is caught and
 * rewrapped as {@link InvalidMarketDataException}, never duplicated here.
 * Strict descending date order is treated the same way: Alpha Vantage
 * documents dates newest-first, and a non-descending (including
 * equal/duplicate) date sequence is a market-data problem, not a shape
 * problem, so it is also reported as {@link InvalidMarketDataException} —
 * distinct from a true duplicate JSON key, which is a syntax failure caught
 * during parsing. Valid input is reversed once into the ascending order
 * {@link DailyBars} exposes — a provider-normalization step, not a general
 * sort.
 */
public final class AlphaVantageDailyParser {

    private static final String META_DATA_FIELD = "Meta Data";
    private static final String TIME_SERIES_FIELD = "Time Series (Daily)";
    private static final Set<String> SUCCESS_TOP_LEVEL_FIELDS = Set.of(META_DATA_FIELD, TIME_SERIES_FIELD);

    private static final String META_INFORMATION_FIELD = "1. Information";
    private static final String META_SYMBOL_FIELD = "2. Symbol";
    private static final String META_LAST_REFRESHED_FIELD = "3. Last Refreshed";
    private static final String META_OUTPUT_SIZE_FIELD = "4. Output Size";
    private static final String META_TIME_ZONE_FIELD = "5. Time Zone";
    private static final Set<String> META_FIELDS = Set.of(META_INFORMATION_FIELD, META_SYMBOL_FIELD,
            META_LAST_REFRESHED_FIELD, META_OUTPUT_SIZE_FIELD, META_TIME_ZONE_FIELD);
    private static final String OUTPUT_SIZE_COMPACT = "Compact";
    private static final String OUTPUT_SIZE_FULL = "Full size";

    private static final String BAR_OPEN_FIELD = "1. open";
    private static final String BAR_HIGH_FIELD = "2. high";
    private static final String BAR_LOW_FIELD = "3. low";
    private static final String BAR_CLOSE_FIELD = "4. close";
    private static final String BAR_VOLUME_FIELD = "5. volume";
    private static final Set<String> BAR_FIELDS =
            Set.of(BAR_OPEN_FIELD, BAR_HIGH_FIELD, BAR_LOW_FIELD, BAR_CLOSE_FIELD, BAR_VOLUME_FIELD);

    private static final Pattern DATE_GRAMMAR = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern PRICE_GRAMMAR = Pattern.compile("-?(0|[1-9][0-9]{0,17})(\\.[0-9]{1,18})?");
    private static final Pattern VOLUME_GRAMMAR = Pattern.compile("0|-?[1-9][0-9]{0,18}");

    /**
     * The approved D-33 signal for a FULL-history capability-limit {@code
     * "Information"} message: a case-insensitive substring match on
     * "premium", checked only when {@code FULL} was requested. A rate-limit
     * {@code "Information"} that happens to also pitch a premium plan is
     * therefore classified as a capability limitation too — an accepted,
     * approved trade-off, not a defect.
     */
    private static final String PREMIUM_MARKER = "premium";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private AlphaVantageDailyParser() {
    }

    /**
     * Parses {@code json} for a request that asked for {@code
     * requestedSymbol} at {@code requestedDepth}. Never sorts, repairs, or
     * silently skips a malformed document or row.
     *
     * @throws MarketDataRequestRejectedException the provider's {@code
     *                                              "Error Message"} response
     * @throws MarketDataCapabilityException      {@code requestedDepth ==
     *                                              FULL} but the response
     *                                              proves the account is
     *                                              limited to compact
     *                                              history
     * @throws MarketDataUnavailableException     a rate-limit/temporary
     *                                              {@code "Note"}/{@code
     *                                              "Information"} response
     * @throws InvalidMarketDataException         an empty time series, a
     *                                              non-descending (including
     *                                              equal/duplicate) provider
     *                                              date sequence, or a
     *                                              syntactically valid row
     *                                              that fails {@code Bar}'s
     *                                              semantics
     * @throws MarketDataResponseException        any other shape/format
     *                                              failure: malformed JSON,
     *                                              an unrecognized or
     *                                              incomplete document
     *                                              shape, or a meta mismatch
     *                                              that does not itself
     *                                              prove a capability
     *                                              limitation
     */
    public static DailyBars parse(String requestedSymbol, HistoryDepth requestedDepth, String json) {
        Objects.requireNonNull(requestedSymbol, "requestedSymbol must not be null");
        Objects.requireNonNull(requestedDepth, "requestedDepth must not be null");
        Objects.requireNonNull(json, "json must not be null");

        JsonNode root = parseJson(json);
        if (!root.isObject()) {
            throw new MarketDataResponseException("unexpected top-level JSON shape");
        }

        classifyControlResponse(root, requestedDepth);
        validateTopLevelShape(root);
        validateMeta(root.get(META_DATA_FIELD), requestedSymbol, requestedDepth);

        List<Bar> descending = parseTimeSeries(root.get(TIME_SERIES_FIELD));
        List<Bar> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);

        return new DailyBars(ascending, sourceDetailFor(requestedDepth));
    }

    private static String sourceDetailFor(HistoryDepth depth) {
        return depth == HistoryDepth.COMPACT
                ? "TIME_SERIES_DAILY;outputsize=compact"
                : "TIME_SERIES_DAILY;outputsize=full";
    }

    // --- top-level parse / classification ---------------------------------

    private static JsonNode parseJson(String json) {
        try {
            return JSON_MAPPER.readValue(json, JsonNode.class);
        } catch (JacksonException e) {
            throw classifyJacksonFailure(e);
        }
    }

    private static MarketDataResponseException classifyJacksonFailure(JacksonException e) {
        if (e instanceof StreamReadException sre) {
            String message = sre.getOriginalMessage();
            if (message != null && message.startsWith("Duplicate")) {
                return new MarketDataResponseException("duplicate key");
            }
            return new MarketDataResponseException("malformed JSON");
        }
        if (e instanceof MismatchedInputException mie) {
            String message = mie.getOriginalMessage();
            if (message != null && message.contains("Trailing token")) {
                return new MarketDataResponseException("trailing tokens");
            }
            return new MarketDataResponseException("malformed JSON");
        }
        return new MarketDataResponseException("malformed JSON");
    }

    /**
     * Recognizes Alpha Vantage's control-response keys before any
     * success-shape validation runs. A {@code "Information"} message that
     * mentions a premium plan while {@code FULL} was requested proves the
     * D-33 capability limitation; every other {@code "Information"}/{@code
     * "Note"} is treated as rate-limit/temporary. The provider's own
     * message text is never included in the thrown exception.
     */
    private static void classifyControlResponse(JsonNode root, HistoryDepth requestedDepth) {
        if (root.has("Error Message")) {
            throw new MarketDataRequestRejectedException("alpha vantage rejected the request");
        }
        if (root.has("Information")) {
            String text = textOrNull(root.get("Information"));
            if (requestedDepth == HistoryDepth.FULL && text != null
                    && text.toLowerCase(Locale.ROOT).contains(PREMIUM_MARKER)) {
                throw new MarketDataCapabilityException(
                        "alpha vantage full history requires a premium plan");
            }
            throw new MarketDataUnavailableException("alpha vantage is temporarily unavailable");
        }
        if (root.has("Note")) {
            throw new MarketDataUnavailableException("alpha vantage is temporarily unavailable");
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isTextual() ? node.asString() : null;
    }

    private static void validateTopLevelShape(JsonNode root) {
        for (String field : root.propertyNames()) {
            if (!SUCCESS_TOP_LEVEL_FIELDS.contains(field)) {
                throw new MarketDataResponseException("unexpected top-level property");
            }
        }
        if (!root.has(META_DATA_FIELD)) {
            throw new MarketDataResponseException("missing top-level property");
        }
        if (!root.has(TIME_SERIES_FIELD)) {
            throw new MarketDataResponseException("missing top-level property");
        }
        if (!root.get(META_DATA_FIELD).isObject()) {
            throw new MarketDataResponseException("wrong JSON type");
        }
        if (!root.get(TIME_SERIES_FIELD).isObject()) {
            throw new MarketDataResponseException("wrong JSON type");
        }
    }

    // --- Meta Data ----------------------------------------------------------

    /**
     * Validates {@code Meta Data} against the exact documented Alpha
     * Vantage shape — the same five properties every {@code
     * TIME_SERIES_DAILY} response carries, no more and no fewer — in the
     * same spirit as the top-level and per-bar strictness above. Only
     * {@code "2. Symbol"} and {@code "4. Output Size"} are semantically
     * checked; the other three are required to be present and textual, but
     * their content is not otherwise validated.
     */
    private static void validateMeta(JsonNode meta, String requestedSymbol, HistoryDepth requestedDepth) {
        for (String field : meta.propertyNames()) {
            if (!META_FIELDS.contains(field)) {
                throw new MarketDataResponseException("unexpected meta property");
            }
        }
        requiredMetaText(meta, META_INFORMATION_FIELD);
        requiredMetaText(meta, META_LAST_REFRESHED_FIELD);
        requiredMetaText(meta, META_TIME_ZONE_FIELD);

        String symbol = requiredMetaText(meta, META_SYMBOL_FIELD);
        if (!symbol.equals(requestedSymbol)) {
            throw new MarketDataResponseException("symbol mismatch");
        }

        String outputSize = requiredMetaText(meta, META_OUTPUT_SIZE_FIELD);
        String expected = requestedDepth == HistoryDepth.COMPACT ? OUTPUT_SIZE_COMPACT : OUTPUT_SIZE_FULL;
        if (outputSize.equals(expected)) {
            return;
        }
        if (requestedDepth == HistoryDepth.FULL && outputSize.equals(OUTPUT_SIZE_COMPACT)) {
            throw new MarketDataCapabilityException(
                    "alpha vantage full history requires a premium plan");
        }
        throw new MarketDataResponseException("output size mismatch");
    }

    private static String requiredMetaText(JsonNode meta, String field) {
        if (!meta.has(field)) {
            throw new MarketDataResponseException("missing meta property");
        }
        JsonNode value = meta.get(field);
        if (!value.isTextual()) {
            throw new MarketDataResponseException("wrong JSON type");
        }
        return value.asString();
    }

    // --- Time Series (Daily) -------------------------------------------------

    /** Returns bars in the same (newest-first) order the source document used. */
    private static List<Bar> parseTimeSeries(JsonNode timeSeries) {
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>(timeSeries.properties());
        if (entries.isEmpty()) {
            throw new InvalidMarketDataException("empty time series");
        }

        List<Bar> bars = new ArrayList<>(entries.size());
        LocalDate previousDate = null;

        for (Map.Entry<String, JsonNode> entry : entries) {
            LocalDate date = parseDate(entry.getKey());

            if (previousDate != null && !date.isBefore(previousDate)) {
                throw new InvalidMarketDataException(date, "non-descending time series order");
            }

            bars.add(parseBar(date, entry.getValue()));
            previousDate = date;
        }

        return bars;
    }

    private static LocalDate parseDate(String text) {
        if (!DATE_GRAMMAR.matcher(text).matches()) {
            throw new MarketDataResponseException("invalid date format");
        }
        try {
            return LocalDate.parse(text, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new MarketDataResponseException("invalid date");
        }
    }

    private static Bar parseBar(LocalDate date, JsonNode barNode) {
        if (!barNode.isObject()) {
            throw new MarketDataResponseException("wrong JSON type");
        }

        for (String field : barNode.propertyNames()) {
            if (!BAR_FIELDS.contains(field)) {
                throw new MarketDataResponseException("extra bar field");
            }
        }
        for (String field : BAR_FIELDS) {
            if (!barNode.has(field)) {
                throw new MarketDataResponseException("missing bar field");
            }
        }

        BigDecimal open = parsePrice(barNode.get(BAR_OPEN_FIELD));
        BigDecimal high = parsePrice(barNode.get(BAR_HIGH_FIELD));
        BigDecimal low = parsePrice(barNode.get(BAR_LOW_FIELD));
        BigDecimal close = parsePrice(barNode.get(BAR_CLOSE_FIELD));
        long volume = parseVolume(barNode.get(BAR_VOLUME_FIELD));

        try {
            return new Bar(date, open, high, low, close, volume);
        } catch (IllegalArgumentException e) {
            throw new InvalidMarketDataException(date, e.getMessage());
        }
    }

    private static BigDecimal parsePrice(JsonNode node) {
        String text = requiredScalarText(node);
        if (!PRICE_GRAMMAR.matcher(text).matches()) {
            throw new MarketDataResponseException("invalid price format");
        }
        return new BigDecimal(text);
    }

    private static long parseVolume(JsonNode node) {
        String text = requiredScalarText(node);
        if (!VOLUME_GRAMMAR.matcher(text).matches()) {
            throw new MarketDataResponseException("invalid volume format");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new MarketDataResponseException("volume out of range");
        }
    }

    private static String requiredScalarText(JsonNode node) {
        if (node.isNull()) {
            throw new MarketDataResponseException("null value");
        }
        if (!node.isTextual()) {
            throw new MarketDataResponseException("wrong JSON type");
        }
        return node.asString();
    }
}
