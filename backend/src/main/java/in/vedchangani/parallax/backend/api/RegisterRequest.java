package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.user.AppUser;
import jakarta.validation.constraints.Pattern;

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
 * <p>{@code password}'s length is validated separately by {@code
 * PasswordPolicy} (byte-length, not {@code @Size}-expressible) rather than
 * here — see {@code UserRegistrationService}.
 */
public record RegisterRequest(
        @Pattern(regexp = AppUser.USERNAME_PATTERN) String username,
        String password) {
}
