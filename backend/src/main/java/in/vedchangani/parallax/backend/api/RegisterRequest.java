package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.user.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

/**
 * The {@code POST /api/auth/register} request envelope (D-38). Read by the
 * same strict D-30 reader every other request body uses ({@code
 * StrategyDefinitionCodec.parseRequest(String, Class)}), never Spring's
 * global JSON binding — an unknown property (e.g. an attempted {@code id},
 * {@code passwordHash}, or {@code ownerId}) fails before Bean Validation
 * ever runs, which is what rules out mass assignment. There is no
 * {@code ownerId} field: registration only ever creates the account
 * itself, never an owned resource.
 *
 * <p>Username (D-43): surrounding whitespace is trimmed with {@link
 * String#trim()} — deliberately the same trim Spring Security's login
 * filter applies to the submitted username, so the stored name is always
 * one a login can actually send — and the trimmed value must be non-blank
 * and at most {@value AppUser#USERNAME_MAX_LENGTH} characters (code points,
 * as the {@code varchar(64)} column counts them). Case and every other
 * character are kept exactly as entered; the only excluded character is NUL,
 * which PostgreSQL cannot store.
 *
 * <p>{@code password}'s length is validated separately by {@code
 * PasswordPolicy} (byte-length, not {@code @Size}-expressible) rather than
 * here — see {@code UserRegistrationService}.
 */
public record RegisterRequest(
        @NotBlank(message = "username must not be blank")
        @CodePointLength(max = AppUser.USERNAME_MAX_LENGTH,
                message = "username must be at most " + AppUser.USERNAME_MAX_LENGTH + " characters")
        @Pattern(regexp = "^[^\\u0000]*$", message = "username must not contain NUL characters")
        String username,
        String password) {

    public RegisterRequest {
        username = username == null ? null : username.trim();
    }
}
