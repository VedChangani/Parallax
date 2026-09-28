package in.vedchangani.parallax.backend.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * The single login-failure response (D-37): an unknown username, a wrong
 * password, and a passwordless account (e.g. the unclaimed {@code dev}
 * user) all reach here as {@code BadCredentialsException} — {@code
 * DaoAuthenticationProvider}'s {@code hideUserNotFoundExceptions} default
 * already collapses "no such user" into the same exception type as "wrong
 * password" — and this handler collapses them further into one fixed
 * body, deliberately never inspecting {@link AuthenticationException#getMessage()}.
 * No redirect: {@code formLogin()} is configured with this handler
 * instead of a {@code failureUrl}.
 */
final class GenericAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    GenericAuthenticationFailureHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        ProblemDetailWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "invalid username or password");
    }
}
