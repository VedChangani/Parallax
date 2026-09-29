package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.user.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

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
