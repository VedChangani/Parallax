package in.vedchangani.parallax.backend.security;

import jakarta.servlet.http.Cookie;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

import java.util.Arrays;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestConfiguration(proxyBeanMethods = false)
public class AuthenticatedMockMvcConfig {

    private static final String CSRF_TOKEN_VALUE = "test-csrf-token-" + UUID.randomUUID();

    @Bean
    MockMvcBuilderCustomizer authenticatedByDefaultMockMvcCustomizer() {
        return (ConfigurableMockMvcBuilder<?> builder) ->
                builder.defaultRequest(get("/").with(user("mock-current-user")).with(csrfCookieAndHeader()));
    }

    private static RequestPostProcessor csrfCookieAndHeader() {
        return (MockHttpServletRequest request) -> {
            Cookie csrfCookie = new Cookie("XSRF-TOKEN", CSRF_TOKEN_VALUE);
            Cookie[] existing = request.getCookies();
            Cookie[] merged = existing == null ? new Cookie[]{csrfCookie}
                    : Arrays.copyOf(existing, existing.length + 1);
            if (existing != null) {
                merged[existing.length] = csrfCookie;
            }
            request.setCookies(merged);
            request.addHeader("X-XSRF-TOKEN", CSRF_TOKEN_VALUE);
            return request;
        };
    }
}
