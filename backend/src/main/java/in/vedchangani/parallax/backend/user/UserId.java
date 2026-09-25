package in.vedchangani.parallax.backend.user;

/**
 * A type-safe wrapper around an {@code app_user.id} value (D-31). Kept
 * distinct from a bare {@code long} so an owner id can never be confused
 * with a strategy id or a version number at a call site, and so an owner
 * identity never enters an engine object (CLAUDE.md: the engine must never
 * depend on persistence or web concerns).
 */
public record UserId(long value) {
}
