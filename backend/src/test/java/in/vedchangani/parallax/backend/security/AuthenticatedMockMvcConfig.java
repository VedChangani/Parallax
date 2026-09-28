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

/**
 * D-37 test infrastructure: makes every {@code MockMvc} request in an
 * importing test authenticated and CSRF-exempt by default, so the
 * pre-existing D-31/D-32/D-34 controller tests keep proving what they
 * proved before Spring Security existed - ownership, request/response
 * shape, and error mapping via {@code @MockitoBean CurrentUser} - without
 * every test file also having to become a real-session login test.
 *
 * <p>{@code .with(user(...))} places a fully-formed, unrelated {@code
 * UserDetails} into the security context for every request; this is safe
 * only because every such test overrides the real {@link
 * in.vedchangani.parallax.backend.user.CurrentUser} bean with {@code
 * @MockitoBean} — the identity Spring Security believes it authenticated
 * is never the identity a controller actually resolves.
 *
 * <p>CSRF is satisfied with a hand-written {@link RequestPostProcessor}
 * (not {@code SecurityMockMvcRequestPostProcessors.csrf()}) for two
 * reasons specific to this app's CSRF setup: {@code
 * CookieCsrfTokenRepository} is a pure double-submit cookie — it never
 * binds the token to the session, so any self-consistent cookie/header
 * pair validates, with no repository/session coordination needed. And
 * {@code csrf()}'s two modes both misfire here — its default (parameter)
 * mode adds a {@code _csrf} form field, which D-32's dataset-upload
 * endpoint then rejects as an unexpected multipart part; its {@code
 * .asHeader()} mode sends the *encoded* token value (produced for {@code
 * SecurityConfig}'s SPA {@code CsrfTokenRequestHandler}) in the header,
 * where this app's handler expects the *raw* value instead. Real-session,
 * real-CSRF authentication behavior is covered separately by {@code
 * AuthenticationIT}, which does not import this configuration.
 */
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
