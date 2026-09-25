package in.vedchangani.parallax.backend.strategy.definition;

/**
 * The canonical, hashable encoding of a validated engine {@code
 * StrategyDefinition} (D-30): the fixed-format UTF-8 JSON text (no
 * whitespace, fixed property order, D-30 grammar) and the lowercase
 * 64-character hex SHA-256 of that exact text. {@code schemaVersion} is
 * also the value embedded as the document's own leading property, so it is
 * part of the hashed bytes.
 */
public record CanonicalStrategyDefinition(int schemaVersion, String json, String sha256) {
}
