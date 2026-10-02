package in.vedchangani.parallax.backend.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

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
