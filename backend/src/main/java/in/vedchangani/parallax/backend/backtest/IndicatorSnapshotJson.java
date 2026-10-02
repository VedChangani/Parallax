package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class IndicatorSnapshotJson {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .build();

    private IndicatorSnapshotJson() {
    }

    static String write(IndicatorSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        boolean first = true;
        for (Map.Entry<IndicatorSpec, Double> entry : snapshot.values().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            IndicatorSpec spec = entry.getKey();
            sb.append("{\"type\":\"").append(spec.type().name()).append('"');
            sb.append(",\"period\":").append(spec.period());
            sb.append(",\"value\":\"");
            appendCanonicalToken(sb, Double.toString(entry.getValue()));
            sb.append("\"}");
        }
        sb.append(']');
        return sb.toString();
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

    static IndicatorSnapshot read(String json, LocalDate date, BigDecimal close) {
        Objects.requireNonNull(json, "json must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(close, "close must not be null");

        IndicatorEntry[] entries;
        try {
            entries = JSON_MAPPER.readValue(json, IndicatorEntry[].class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("malformed indicator snapshot JSON: " + e.getMessage(), e);
        }

        Map<IndicatorSpec, Double> values = new LinkedHashMap<>();
        for (IndicatorEntry entry : entries) {
            IndicatorType type;
            try {
                type = IndicatorType.valueOf(entry.type());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown indicator type: " + entry.type(), e);
            }
            double value;
            try {
                value = Double.parseDouble(entry.value());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed indicator value: " + entry.value(), e);
            }
            IndicatorSpec spec = new IndicatorSpec(type, entry.period());
            if (values.containsKey(spec)) {
                throw new IllegalArgumentException("duplicate indicator spec in stored snapshot: " + spec);
            }
            values.put(spec, value);
        }

        return new IndicatorSnapshot(date, close, values);
    }

    private record IndicatorEntry(String type, int period, String value) {
    }
}
