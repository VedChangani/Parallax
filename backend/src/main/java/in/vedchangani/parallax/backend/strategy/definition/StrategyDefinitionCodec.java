package in.vedchangani.parallax.backend.strategy.definition;

import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The D-30 JSON boundary for {@link StrategyDefinition}: a strict request
 * reader, a deterministic canonical encoder with its SHA-256 hash, and a
 * verified decoder for stored data. Stateless except for its own private,
 * explicitly configured Jackson {@link JsonMapper} — never Spring's global
 * mapper, whose defaults are lenient.
 *
 * <p>The canonical text is never produced by Jackson serialization. It is
 * written by a hand-written recursive emitter over the sealed DTO tree
 * (exhaustive {@code switch}, no {@code default}), always starting from
 * {@link StrategyDefinitionMapper#toDto} of an already-validated engine
 * object — never from a raw client request — so canonical output never
 * depends on Jackson's field ordering, and two requests that describe the
 * same definition with different formatting produce byte-identical
 * canonical text and the same hash.
 */
@Component
public final class StrategyDefinitionCodec {

    /** The only schema version D-30 understands. Embedded as the document's own leading property (D-30 §6). */
    public static final int SCHEMA_VERSION = 1;

    private static final Pattern HASH_HEX = Pattern.compile("[0-9a-f]{64}");

    private final StrategyDefinitionMapper mapper;
    private final JsonMapper jsonMapper;

    public StrategyDefinitionCodec(StrategyDefinitionMapper mapper) {
        this.mapper = mapper;
        this.jsonMapper = buildStrictMapper();
    }

    private static JsonMapper buildStrictMapper() {
        return JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                // Reject number/boolean -> String coercion (e.g. Constant.value / CashFraction.fraction
                // must be JSON strings, never JSON numbers).
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                // Reject String/float -> int coercion (e.g. IndicatorRef.period must be a JSON integer).
                .withCoercionConfig(LogicalType.Integer, cfg -> cfg
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build();
    }

    // --- client request parsing ------------------------------------------------

    /**
     * Strictly parses a transport (request) document into a {@link
     * StrategyDefinitionDto}. Unknown/duplicate/missing/null properties,
     * trailing tokens, unknown type ids, wrong JSON types, and any
     * coercion all fail. A transport document must not carry {@code
     * schemaVersion} — since {@link StrategyDefinitionDto} has no such
     * property, {@code FAIL_ON_UNKNOWN_PROPERTIES} rejects it like any
     * other unknown field.
     *
     * @throws MalformedStrategyDefinitionException if {@code json} does not
     *                                                strictly match the DTO shape
     */
    public StrategyDefinitionDto parseRequest(String json) {
        return parseRequest(json, StrategyDefinitionDto.class);
    }

    /**
     * The D-31 REST envelope boundary (D-31 §8): the same strict reader and
     * the same {@link MalformedStrategyDefinitionException} path/reason
     * mapping as {@link #parseRequest(String)}, generalized to any request
     * record — so an envelope such as {@code CreateStrategyRequest}
     * (ordinary metadata fields alongside a nested {@link
     * StrategyDefinitionDto}) is read by exactly one strict parser, never
     * Spring's lenient global mapper. This is the only generalization: the
     * strictness configuration, canonical encoding, hashing, and
     * decode/integrity behavior are unchanged, and {@link
     * #parseRequest(String)} now delegates here with {@code
     * StrategyDefinitionDto.class}.
     *
     * @throws MalformedStrategyDefinitionException if {@code json} does not
     *                                                strictly match {@code requestType}'s shape
     */
    public <T extends Record> T parseRequest(String json, Class<T> requestType) {
        Objects.requireNonNull(json, "json must not be null");
        Objects.requireNonNull(requestType, "requestType must not be null");
        T value;
        try {
            value = jsonMapper.readValue(json, requestType);
        } catch (JacksonException e) {
            throw new MalformedStrategyDefinitionException(pathOf(e), reasonOf(e));
        }
        if (value == null) {
            // The JSON literal `null` deserializes to a Java null without Jackson
            // throwing — reject it explicitly rather than let it surface as an NPE.
            throw new MalformedStrategyDefinitionException("", "wrong JSON type");
        }
        return value;
    }

    // --- canonical encode --------------------------------------------------

    /**
     * Produces the canonical encoding of {@code definition} — always
     * generated from the validated engine object via {@link
     * StrategyDefinitionMapper#toDto}, never by reserializing a client
     * request. Deterministic: equal definitions always produce
     * byte-identical text and the same hash.
     */
    public CanonicalStrategyDefinition encode(StrategyDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        StrategyDefinitionDto dto = mapper.toDto(definition);
        String json = writeCanonicalDocument(dto);
        String hash = sha256Hex(json);
        return new CanonicalStrategyDefinition(SCHEMA_VERSION, json, hash);
    }

    // --- stored-data decode / integrity --------------------------------------

    /**
     * Decodes and verifies a stored strategy document (D-30 §8): the
     * schema version must be {@value #SCHEMA_VERSION} and must match the
     * document's own {@code schemaVersion}; the document must strictly
     * parse and semantically map to an engine {@link StrategyDefinition};
     * and re-encoding that definition canonically must hash to exactly
     * {@code expectedSha256}. The comparison is against the
     * <em>re-encoded</em> canonical text's hash, never against {@code
     * documentJson} byte-for-byte — a database's {@code jsonb} rendering
     * may legitimately reformat whitespace/property order without
     * affecting integrity.
     *
     * @throws StrategyDefinitionIntegrityException on any failure — an
     *                                                unsupported schema
     *                                                version, a malformed
     *                                                or semantically
     *                                                invalid stored
     *                                                document, a malformed
     *                                                expected hash, or a
     *                                                hash mismatch. Never a
     *                                                client-facing
     *                                                exception; this
     *                                                always means stored
     *                                                data corruption or a
     *                                                bug, not a request
     *                                                error.
     */
    public StrategyDefinition decode(int schemaVersion, String documentJson, String expectedSha256) {
        Objects.requireNonNull(documentJson, "documentJson must not be null");
        Objects.requireNonNull(expectedSha256, "expectedSha256 must not be null");

        if (schemaVersion != SCHEMA_VERSION) {
            throw new StrategyDefinitionIntegrityException(
                    "unsupported schema version: " + schemaVersion);
        }

        StoredStrategyDocument stored;
        try {
            stored = jsonMapper.readValue(documentJson, StoredStrategyDocument.class);
        } catch (JacksonException e) {
            throw new StrategyDefinitionIntegrityException("malformed stored strategy document", e);
        }
        if (stored == null) {
            // The JSON literal `null` deserializes to a Java null without Jackson
            // throwing — reject it explicitly rather than let it surface as an NPE.
            throw new StrategyDefinitionIntegrityException("malformed stored strategy document: JSON null");
        }

        if (stored.schemaVersion() != schemaVersion) {
            throw new StrategyDefinitionIntegrityException(
                    "stored document schemaVersion (%d) does not match the supplied schemaVersion (%d)"
                            .formatted(stored.schemaVersion(), schemaVersion));
        }

        StrategyDefinition definition;
        try {
            definition = mapper.toEngine(new StrategyDefinitionDto(
                    stored.entryCondition(), stored.exitCondition(), stored.positionSizing()));
        } catch (InvalidStrategyDefinitionException | MalformedStrategyDefinitionException e) {
            throw new StrategyDefinitionIntegrityException(
                    "stored strategy document is semantically invalid: " + e.getMessage(), e);
        }

        if (!HASH_HEX.matcher(expectedSha256).matches()) {
            throw new StrategyDefinitionIntegrityException(
                    "expected hash is not a lowercase 64-character hex string");
        }

        CanonicalStrategyDefinition recomputed = encode(definition);
        if (!recomputed.sha256().equals(expectedSha256)) {
            throw new StrategyDefinitionIntegrityException("stored strategy hash mismatch");
        }

        return definition;
    }

    // --- canonical JSON emitter -----------------------------------------------

    private String writeCanonicalDocument(StrategyDefinitionDto dto) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"schemaVersion\":").append(SCHEMA_VERSION);
        sb.append(",\"entryCondition\":");
        writeCondition(sb, dto.entryCondition());
        sb.append(",\"exitCondition\":");
        writeCondition(sb, dto.exitCondition());
        sb.append(",\"positionSizing\":");
        writePositionSizing(sb, dto.positionSizing());
        sb.append('}');
        return sb.toString();
    }

    private void writeCondition(StringBuilder sb, ConditionDto condition) {
        switch (condition) {
            case ConditionDto.Compare c -> {
                sb.append("{\"type\":\"compare\",\"left\":");
                writeOperand(sb, c.left());
                sb.append(",\"operator\":\"").append(c.operator().name()).append('"');
                sb.append(",\"right\":");
                writeOperand(sb, c.right());
                sb.append('}');
            }
            case ConditionDto.All a -> {
                sb.append("{\"type\":\"all\",\"conditions\":");
                writeConditionList(sb, a.conditions());
                sb.append('}');
            }
            case ConditionDto.Any a -> {
                sb.append("{\"type\":\"any\",\"conditions\":");
                writeConditionList(sb, a.conditions());
                sb.append('}');
            }
        }
    }

    private void writeConditionList(StringBuilder sb, List<ConditionDto> conditions) {
        sb.append('[');
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeCondition(sb, conditions.get(i));
        }
        sb.append(']');
    }

    private void writeOperand(StringBuilder sb, OperandDto operand) {
        switch (operand) {
            case OperandDto.Indicator ind -> {
                sb.append("{\"type\":\"indicator\",\"indicator\":\"").append(ind.indicator().name()).append('"');
                sb.append(",\"period\":").append(ind.period());
                sb.append('}');
            }
            case OperandDto.Close ignored -> sb.append("{\"type\":\"close\"}");
            case OperandDto.Constant constant -> {
                sb.append("{\"type\":\"constant\",\"value\":\"");
                appendCanonicalToken(sb, constant.value());
                sb.append("\"}");
            }
        }
    }

    private void writePositionSizing(StringBuilder sb, PositionSizingDto sizing) {
        switch (sizing) {
            case PositionSizingDto.CashFraction cf -> {
                sb.append("{\"type\":\"cashFraction\",\"fraction\":\"");
                appendCanonicalToken(sb, cf.fraction());
                sb.append("\"}");
            }
        }
    }

    /**
     * Appends a {@code Double.toString}/{@code BigDecimal.toPlainString}
     * token verbatim. Every character such a token can ever contain
     * ({@code -}, digits, {@code .}, {@code E}, {@code +}) requires no
     * JSON escaping; this asserts that invariant rather than silently
     * emitting an un-escaped character that did need escaping.
     */
    private static void appendCanonicalToken(StringBuilder sb, String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean safe = (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'E';
            if (!safe) {
                throw new IllegalStateException(
                        "canonical numeric token contains a character requiring JSON escaping: " + token);
            }
        }
        sb.append(token);
    }

    // --- hashing -----------------------------------------------------------

    private static String sha256Hex(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JLS platform guarantee); unreachable.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // --- Jackson exception -> path/reason -------------------------------------

    private static String pathOf(JacksonException e) {
        List<JacksonException.Reference> path = e.getPath();
        if (path.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference ref : path) {
            if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            } else if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            }
        }
        return sb.toString();
    }

    /**
     * Maps a Jackson parse failure to one of a fixed set of codec-owned,
     * stable reason categories — never {@code e.getOriginalMessage()} or
     * the exception's class name, either of which could expose Jackson's
     * own wording or parser-internal diagnostics (including the target
     * DTO class names Jackson's messages embed) directly to a client.
     */
    private static String reasonOf(JacksonException e) {
        if (e instanceof UnrecognizedPropertyException) {
            return "unknown property";
        }
        if (e instanceof InvalidTypeIdException ite) {
            String message = ite.getOriginalMessage();
            return message != null && message.contains("missing type id") ? "missing type id" : "unknown type";
        }
        if (e instanceof InvalidNullException) {
            return "null property";
        }
        if (e instanceof InvalidFormatException) {
            return "wrong JSON type";
        }
        if (e instanceof MismatchedInputException mie) {
            String message = mie.getOriginalMessage();
            if (message != null) {
                if (message.contains("Null value for creator property")) {
                    return "null property";
                }
                if (message.contains("Missing creator property")) {
                    return "missing property";
                }
                if (message.contains("Trailing token")) {
                    return "trailing tokens";
                }
                if (message.contains("Cannot coerce")) {
                    return "wrong JSON type";
                }
            }
            return "malformed JSON";
        }
        if (e instanceof StreamReadException sre) {
            String message = sre.getOriginalMessage();
            return message != null && message.startsWith("Duplicate") ? "duplicate key" : "malformed JSON";
        }
        return "malformed JSON";
    }
}
