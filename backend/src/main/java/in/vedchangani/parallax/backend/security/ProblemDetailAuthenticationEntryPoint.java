package in.vedchangani.parallax.backend.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * The 401 boundary for every unauthenticated request to a protected path
 * (D-37): a fixed {@link org.springframework.http.ProblemDetail} body,
 * never a redirect, a {@code WWW-Authenticate} challenge, or a generated
 * HTML page — this backend is a pure JSON API with no browser-facing login
 * page for Spring Security to send a client to.
 */
final class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ProblemDetailWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "authentication is required");
    }
}
