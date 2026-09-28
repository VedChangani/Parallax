package in.vedchangani.parallax.backend.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

/**
 * Writes a {@link ProblemDetail} body directly to a servlet response
 * (D-37) — shared by the three filter-level handlers below, which run
 * outside Spring MVC's dispatcher and so cannot rely on {@code
 * @RestControllerAdvice}/{@code HttpMessageConverter}s the way {@code
 * ApiExceptionHandler} does. Every handler produces the exact same
 * {@code application/problem+json} shape that class uses, so a client
 * cannot tell whether a given error response came from Spring Security or
 * from Spring MVC.
 */
final class ProblemDetailWriter {

    private ProblemDetailWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String title,
                       String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
