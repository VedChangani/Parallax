package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;

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
 * construction here, never left to a mapper's own defaults.
 */
final class IndicatorSnapshotJson {

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
}
