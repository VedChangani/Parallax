package in.vedchangani.parallax.backend.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.util.Map;

/**
 * A successful {@code POST /api/auth/login} returns {@code 200
 * {"username": "..."}} (D-37) — never a redirect. {@link
 * Authentication#getName()} on a {@code UsernamePasswordAuthenticationToken}
 * backed by a {@code UserDetails} principal returns {@code
 * UserDetails#getUsername()}, i.e. exactly the submitted username, not the
 * internal numeric id.
 */
final class JsonAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;

    JsonAuthenticationSuccessHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws java.io.IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("username", authentication.getName()));
    }
}
