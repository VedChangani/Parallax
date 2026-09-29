package in.vedchangani.parallax.backend.user;

import java.nio.charset.StandardCharsets;

/**
 * The V1 password strength policy (D-37, minimum revised by D-43): at least
 * 8 characters and at most 72 UTF-8 bytes (bcrypt silently truncates anything beyond this, so
 * a longer password would be accepted while its effective secret is
 * shorter than the user believes, with no indication). No composition
 * rules (uppercase/digit/symbol) beyond length — composition requirements
 * are well documented to push users toward predictable patterns rather
 * than stronger passwords, without this codebase needing to relitigate
 * that.
 *
 * <p>The single source of the rule for every place a password is set:
 * {@code PasswordClaimRunner}, {@code UserRegistrationService} and {@code
 * PasswordChangeService}.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    /**
     * @return {@code null} if {@code password} satisfies the policy,
     *     otherwise a client-safe description of the violated rule — never
     *     including the password itself.
     */
    public static String violation(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "password must be at least " + MIN_LENGTH + " characters";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            return "password must be at most " + MAX_UTF8_BYTES + " bytes";
        }
        return null;
    }

    public static boolean isValid(String password) {
        return violation(password) == null;
    }
}
