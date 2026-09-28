package in.vedchangani.parallax.backend.security;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-37 real-session proof, against a real socket (like {@code
 * DatasetUploadSizeLimitIT}) rather than {@code MockMvc}: this is what
 * actually makes the {@code JSESSIONID}/{@code XSRF-TOKEN} cookie
 * attributes (HttpOnly, SameSite) assertable at all. {@code MockMvc}
 * never emits a real {@code Set-Cookie} header for a container-managed
 * session cookie — only for a cookie an application explicitly calls
 * {@code response.addCookie(...)} for (which is exactly what {@code
 * CookieCsrfTokenRepository} does, but not what session tracking does) —
 * so it cannot prove this batch's session-cookie invariants.
 *
 * <p>This test never overrides {@link
 * in.vedchangani.parallax.backend.user.CurrentUser} and never imports
 * {@code AuthenticatedMockMvcConfig}: every request runs through the real
 * {@code SecurityConfig} filter chain, the real {@code
 * AppUserDetailsService}, and the real {@code AuthenticatedCurrentUser}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthenticationIT {

    private static final String PASSWORD = "authentication-it-password";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // --- unauthenticated access ------------------------------------------------

    @Test
    void unauthenticatedRequestToProtectedEndpointIsRejectedWith401() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(uri("/api/strategies"))
                .GET());

        assertEquals(401, response.statusCode());
        assertTrue(contentType(response).startsWith("application/problem+json"));
        assertTrue(response.headers().firstValue("WWW-Authenticate").isEmpty());
        assertTrue(response.headers().firstValue("Location").isEmpty());
        assertTrue(response.headers().allValues("Set-Cookie").stream().noneMatch(c -> c.startsWith("JSESSIONID=")));
    }

    @Test
    void getLoginIsNotAGeneratedHtmlLoginPage() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder().uri(uri("/login")).GET());

        assertEquals(401, response.statusCode());
        assertTrue(contentType(response).startsWith("application/problem+json"));
        assertFalse(response.body().toLowerCase().contains("<html"));
    }

    @Test
    void actuatorHealthIsPubliclyReadable() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder().uri(uri("/actuator/health")).GET());

        assertEquals(200, response.statusCode());
    }

    // --- login success -----------------------------------------------------------

    @Test
    void successfulLoginReturnsUsernameAndSetsCookiesWithTheRightAttributes() throws Exception {
        String username = createClaimedUser("login-ok");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);

        HttpResponse<String> response = send(loginRequest(username, PASSWORD, cookies));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"" + username + "\""));

        String sessionCookieHeader = firstSetCookieHeader(response, "JSESSIONID");
        assertNotNull(sessionCookieHeader, "login must set a JSESSIONID cookie");
        assertTrue(sessionCookieHeader.contains("HttpOnly"));
        assertTrue(sessionCookieHeader.toLowerCase().contains("samesite=lax"));

        String csrfCookieHeader = firstSetCookieHeader(response, "XSRF-TOKEN");
        assertNotNull(csrfCookieHeader, "login must (re)set the XSRF-TOKEN cookie");
        assertFalse(csrfCookieHeader.contains("HttpOnly"), "XSRF-TOKEN must be readable by frontend JavaScript");
    }

    @Test
    void meReturnsUsernameWhenAuthenticatedAndNeverExposesAPassword() throws Exception {
        String username = createClaimedUser("me-ok");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, PASSWORD, cookies);

        HttpResponse<String> response = send(authenticated(HttpRequest.newBuilder().uri(uri("/api/auth/me")).GET(),
                cookies));

        assertEquals(200, response.statusCode());
        String body = response.body().toLowerCase();
        assertTrue(body.contains(username.toLowerCase()));
        assertFalse(body.contains("password"));
        assertFalse(body.contains("hash"));
    }

    @Test
    void loginDoesNotAdoptAnAttackerSuppliedSessionId() throws Exception {
        String username = createClaimedUser("fixation");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        String attackerChosenSessionId = "attacker-chosen-session-id-0123456789";
        cookies.put("JSESSIONID", attackerChosenSessionId);

        HttpResponse<String> response = send(loginRequest(username, PASSWORD, cookies));

        assertEquals(200, response.statusCode());
        String newSessionId = extractCookieValue(firstSetCookieHeader(response, "JSESSIONID"));
        assertNotNull(newSessionId);
        assertNotEquals(attackerChosenSessionId, newSessionId,
                "authentication must never adopt a pre-existing/attacker-supplied session id");
    }

    // --- login failure --------------------------------------------------------

    @Test
    void wrongPasswordUnknownUserPasswordlessDevAndOverLongPasswordAllReturnTheSameGenericFailure()
            throws Exception {
        String username = createClaimedUser("wrong-pw");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);

        String wrongPassword = attemptFailedLoginBody(username, "not-the-right-password", cookies);
        String unknownUser = attemptFailedLoginBody("no-such-user-" + System.nanoTime(), PASSWORD, cookies);
        String passwordlessDev = attemptFailedLoginBody("dev", "anything-at-all-15-chars", cookies);
        String overLongPassword = attemptFailedLoginBody(username, "x".repeat(100), cookies);

        assertEquals(wrongPassword, unknownUser);
        assertEquals(wrongPassword, passwordlessDev);
        assertEquals(wrongPassword, overLongPassword);
    }

    @Test
    void loginWithoutCsrfIsRejectedWith403() throws Exception {
        String username = createClaimedUser("no-csrf-login");
        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8);

        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(uri("/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)));

        assertEquals(403, response.statusCode());
        assertTrue(contentType(response).startsWith("application/problem+json"));
    }

    // --- authenticated writes require CSRF ----------------------------------------

    @Test
    void authenticatedPostWithoutCsrfIsForbiddenAndWithCsrfSucceeds() throws Exception {
        String username = createClaimedUser("csrf-write");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, PASSWORD, cookies);

        String body = strategyRequestBody();

        HttpResponse<String> withoutCsrf = send(HttpRequest.newBuilder()
                .uri(uri("/api/strategies"))
                .header("Content-Type", "application/json")
                .header("Cookie", cookieHeader(cookies))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
        assertEquals(403, withoutCsrf.statusCode());
        assertTrue(contentType(withoutCsrf).startsWith("application/problem+json"));

        HttpResponse<String> withCsrf = send(authenticated(HttpRequest.newBuilder()
                .uri(uri("/api/strategies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)), cookies));
        assertEquals(201, withCsrf.statusCode());
    }

    // --- logout ------------------------------------------------------------------

    @Test
    void logoutRequiresCsrfInvalidatesTheSessionAndReturns204() throws Exception {
        String username = createClaimedUser("logout");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, PASSWORD, cookies);

        // Without CSRF: rejected, and the session is still valid afterwards.
        HttpResponse<String> withoutCsrf = send(HttpRequest.newBuilder()
                .uri(uri("/api/auth/logout"))
                .header("Cookie", cookieHeader(cookies))
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertEquals(403, withoutCsrf.statusCode());

        HttpResponse<String> stillIn = send(authenticated(HttpRequest.newBuilder().uri(uri("/api/auth/me")).GET(),
                cookies));
        assertEquals(200, stillIn.statusCode());

        // With CSRF: succeeds, and the old session no longer authenticates.
        HttpResponse<String> logout = send(authenticated(HttpRequest.newBuilder()
                .uri(uri("/api/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody()), cookies));
        assertEquals(204, logout.statusCode());

        HttpResponse<String> afterLogout = send(authenticated(HttpRequest.newBuilder().uri(uri("/api/auth/me")).GET(),
                cookies));
        assertEquals(401, afterLogout.statusCode());
    }

    // --- helpers -------------------------------------------------------------

    /** A tiny per-test cookie jar: name -> value, updated from each response's raw {@code Set-Cookie} headers. */
    private static final class CookieJar {
        private final Map<String, String> values = new HashMap<>();

        void put(String name, String value) {
            values.put(name, value);
        }

        String get(String name) {
            return values.get(name);
        }

        void absorb(HttpResponse<?> response) {
            for (String setCookie : response.headers().allValues("Set-Cookie")) {
                String pair = setCookie.split(";", 2)[0];
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    values.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }
    }

    private String createClaimedUser(String label) {
        UserId id = TestUsers.create(jdbcTemplate, label);
        String username = jdbcTemplate.queryForObject(
                "select username from app_user where id = ?", String.class, id.value());
        jdbcTemplate.update("update app_user set password_hash = ? where id = ?",
                passwordEncoder.encode(PASSWORD), id.value());
        return username;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** GET /api/auth/me anonymously to obtain the bootstrap XSRF-TOKEN cookie (D-37). */
    private void bootstrapCsrf(CookieJar cookies) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder().uri(uri("/api/auth/me")).GET());
        cookies.absorb(response);
        assertNotNull(cookies.get("XSRF-TOKEN"), "GET /api/auth/me must set the bootstrap XSRF-TOKEN cookie");
    }

    private HttpRequest.Builder loginRequest(String username, String password, CookieJar cookies) {
        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        return authenticated(HttpRequest.newBuilder()
                .uri(uri("/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)), cookies);
    }

    private void login(String username, String password, CookieJar cookies) throws Exception {
        HttpResponse<String> response = send(loginRequest(username, password, cookies));
        cookies.absorb(response);
        assertEquals(200, response.statusCode(), "login must succeed for this test's fixture to work at all");
    }

    private String attemptFailedLoginBody(String username, String password, CookieJar cookies) throws Exception {
        HttpResponse<String> response = send(loginRequest(username, password, cookies));
        assertEquals(401, response.statusCode());
        return response.body();
    }

    /** Attaches the accumulated cookies and the CSRF header to a request builder. */
    private HttpRequest.Builder authenticated(HttpRequest.Builder builder, CookieJar cookies) {
        builder.header("Cookie", cookieHeader(cookies));
        String csrfToken = cookies.get("XSRF-TOKEN");
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        return builder;
    }

    private static String cookieHeader(CookieJar cookies) {
        return cookies.values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    private static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    private static String firstSetCookieHeader(HttpResponse<?> response, String cookieName) {
        List<String> headers = response.headers().allValues("Set-Cookie");
        return headers.stream()
                .filter(h -> h.startsWith(cookieName + "="))
                .findFirst()
                .orElse(null);
    }

    private static String extractCookieValue(String setCookieHeader) {
        if (setCookieHeader == null) {
            return null;
        }
        String pair = setCookieHeader.split(";", 2)[0];
        int eq = pair.indexOf('=');
        return eq > 0 ? pair.substring(eq + 1) : null;
    }

    private static String strategyRequestBody() {
        return "{\"name\":\"auth-it-" + System.nanoTime() + "\",\"description\":\"\","
                + "\"definition\":{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},"
                + "\"operator\":\"GT\",\"right\":{\"type\":\"constant\",\"value\":\"0\"}},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"LT\","
                + "\"right\":{\"type\":\"constant\",\"value\":\"0\"}},"
                + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"}}}";
    }
}
