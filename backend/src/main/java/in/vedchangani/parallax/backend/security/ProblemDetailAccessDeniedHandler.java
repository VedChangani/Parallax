package in.vedchangani.parallax.backend.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * The 403 boundary (D-37): a missing/invalid CSRF token on a
 * state-changing request, or an authenticated request to a {@code
 * denyAll} path, both reach here as an {@link AccessDeniedException} and
 * both get the same fixed {@link org.springframework.http.ProblemDetail}
 * body. Deliberately generic — it never distinguishes "bad CSRF token"
 * from "not permitted here" in the response body.
 */
final class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        ProblemDetailWriter.write(response, objectMapper, HttpStatus.FORBIDDEN, "Forbidden",
                "the request was rejected");
    }
}
