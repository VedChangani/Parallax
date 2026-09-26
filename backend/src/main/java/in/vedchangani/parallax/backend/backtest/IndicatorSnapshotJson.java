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

/**
 * The smallest JSON mapping for a persisted {@link IndicatorSnapshot}
 * (D-34 Batch 1): a self-describing array of {@code {"type","period",
 * "value"}} entries, in the snapshot's own canonical order (D-17 —
 * {@code IndicatorSnapshot.values()} already iterates in that order).
 * Unlike D-30's strategy-definition codec, this JSON is never hashed and
 * carries no schema version: reading a stored fill/rejection later needs
 * no strategy decoding at all, since each entry already names its own
 * indicator type and period.
 *
 * <p>{@code value} is stored as a JSON string produced by {@link
 * Double#toString(double)} — the same exactness precedent as D-30's
 * {@code Constant}/{@code CashFraction} — so it round-trips exactly for
 * every finite {@code double}, including subnormal values.
 *
 * <p>The writer is a small hand-written emitter, following D-30's own
 * {@code StrategyDefinitionCodec} precedent, rather than generic Jackson
 * serialization: deterministic field and array order is guaranteed by
 * construction here, never left to a mapper's own defaults. {@link #read}
 * (D-34 Batch 2) is the read-side counterpart used by result-integrity
 * reconstruction; it uses a small private Jackson reader, since parsing
 * (unlike writing) gains nothing from a hand-rolled implementation.
 */
final class IndicatorSnapshotJson {

    /**
     * Strict, private, explicitly configured reader for the small
     * self-describing array {@link #write} produces — never Spring's
     * global mapper, following D-30's own precedent. Records deserialize
     * natively (Jackson 3 reads a canonical constructor's parameter
     * names), so no annotations are needed on {@link Entry}.
     */
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

    /**
     * Appends a {@code Double.toString} token verbatim. Every character
     * such a token can ever contain ({@code -}, digits, {@code .}, {@code
     * E}) requires no JSON escaping; this asserts that invariant rather
     * than silently emitting a character that did need escaping.
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

    /**
     * Parses a stored {@link #write}-shaped array back into an
     * {@link IndicatorSnapshot} (D-34 Batch 2's read-side reconstruction):
     * {@code date}/{@code close} come from the fill/rejection row that owns
     * this JSON, not from the array itself, since {@link #write} never
     * stores them. Every failure — malformed JSON, an unrecognized
     * indicator type, or a value that does not parse as a {@code double} —
     * is an {@link IllegalArgumentException} ({@link NumberFormatException}
     * included, since it is a subtype), so a caller doing read-time
     * integrity verification can catch one exception type for every
     * reconstruction step.
     *
     * @throws IllegalArgumentException if {@code json} does not parse into
     *                                    a valid {@link IndicatorSnapshot}
     */
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
            values.put(new IndicatorSpec(type, entry.period()), value);
        }

        return new IndicatorSnapshot(date, close, values);
    }

    private record IndicatorEntry(String type, int period, String value) {
    }
}
