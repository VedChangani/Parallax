package in.vedchangani.parallax.backend.api;

/**
 * The {@code POST /api/auth/password} request envelope (D-40). Read by the
 * same strict D-30 reader every other request body uses ({@code
 * StrategyDefinitionCodec.parseRequest(String, Class)}) — an unknown
 * property fails before either password is ever inspected. There is no
 * {@code username}/{@code userId} field: the account to change always
 * comes from {@code CurrentUser}, never from the request body.
 *
 * <p>Neither field carries a Bean Validation annotation: a missing/null
 * value already fails in the strict codec itself, and {@code newPassword}'s
 * length is validated separately by {@code PasswordPolicy} (byte-length,
 * not {@code @Size}-expressible) — see {@code PasswordChangeService}.
 */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
