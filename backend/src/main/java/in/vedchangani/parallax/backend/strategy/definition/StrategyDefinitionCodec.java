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

@Component
public final class StrategyDefinitionCodec {

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
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Integer, cfg -> cfg
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build();
    }

    public StrategyDefinitionDto parseRequest(String json) {
        return parseRequest(json, StrategyDefinitionDto.class);
    }

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
            throw new MalformedStrategyDefinitionException("", "wrong JSON type");
        }
        return value;
    }

    public CanonicalStrategyDefinition encode(StrategyDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        StrategyDefinitionDto dto = mapper.toDto(definition);
        String json = writeCanonicalDocument(dto);
        String hash = sha256Hex(json);
        return new CanonicalStrategyDefinition(SCHEMA_VERSION, json, hash);
    }

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

    private static String sha256Hex(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

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
