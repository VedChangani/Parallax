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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PasswordChangeIT {

    private static final String CURRENT_PASSWORD = "the-current-password";
    private static final String NEW_PASSWORD = "a-brand-new-password";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void successfulChangeInvalidatesOldPasswordAndAcceptsNewPassword() throws Exception {
        String username = createClaimedUser("change-ok");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, CURRENT_PASSWORD, cookies);

        HttpResponse<String> response = send(changePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD, cookies));
        assertEquals(204, response.statusCode());
        assertTrue(response.body() == null || response.body().isEmpty());

        CookieJar freshCookies = new CookieJar();
        bootstrapCsrf(freshCookies);
        HttpResponse<String> oldPasswordAttempt = send(loginRequest(username, CURRENT_PASSWORD, freshCookies));
        assertEquals(401, oldPasswordAttempt.statusCode());

        HttpResponse<String> newPasswordAttempt = send(loginRequest(username, NEW_PASSWORD, freshCookies));
        assertEquals(200, newPasswordAttempt.statusCode());
    }

    @Test
    void sessionIdIsRotatedAndTheNewSessionStaysAuthenticated() throws Exception {
        String username = createClaimedUser("change-rotate");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, CURRENT_PASSWORD, cookies);
        String oldSessionId = cookies.get("JSESSIONID");
        assertNotNull(oldSessionId);

        HttpResponse<String> response = send(changePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD, cookies));
        assertEquals(204, response.statusCode());

        String newSessionCookieHeader = firstSetCookieHeader(response, "JSESSIONID");
        assertNotNull(newSessionCookieHeader, "password change must rotate the session id");
        String newSessionId = extractCookieValue(newSessionCookieHeader);
        assertNotEquals(oldSessionId, newSessionId);
        cookies.absorb(response);

        CookieJar oldSessionCookies = new CookieJar();
        oldSessionCookies.put("JSESSIONID", oldSessionId);
        HttpResponse<String> meWithOldSession = send(
                authenticated(HttpRequest.newBuilder().uri(uri("/api/auth/me")).GET(), oldSessionCookies));
        assertEquals(401, meWithOldSession.statusCode());

        HttpResponse<String> meWithNewSession = send(
                authenticated(HttpRequest.newBuilder().uri(uri("/api/auth/me")).GET(), cookies));
        assertEquals(200, meWithNewSession.statusCode());
        assertTrue(meWithNewSession.body().contains(username));
    }

    @Test
    void responseNeverExposesPasswordOrHash() throws Exception {
        String username = createClaimedUser("change-noleak");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, CURRENT_PASSWORD, cookies);

        HttpResponse<String> response = send(changePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD, cookies));

        assertEquals(204, response.statusCode());
        assertTrue(response.body() == null || response.body().isEmpty());
    }

    @Test
    void wrongCurrentPasswordFailsAndLeavesThePasswordUnchanged() throws Exception {
        String username = createClaimedUser("change-wrong-current");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, CURRENT_PASSWORD, cookies);

        HttpResponse<String> response = send(changePasswordRequest("not-the-current-password", NEW_PASSWORD, cookies));

        assertEquals(401, response.statusCode());
        assertTrue(contentType(response).startsWith("application/problem+json"));
        assertFalse(response.body().toLowerCase().contains("hash"));

        CookieJar freshCookies = new CookieJar();
        bootstrapCsrf(freshCookies);
        HttpResponse<String> stillWorks = send(loginRequest(username, CURRENT_PASSWORD, freshCookies));
        assertEquals(200, stillWorks.statusCode());
    }

    @Test
    void weakNewPasswordFailsWith400AndLeavesThePasswordUnchanged() throws Exception {
        String username = createClaimedUser("change-weak-new");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, CURRENT_PASSWORD, cookies);

        HttpResponse<String> response = send(changePasswordRequest(CURRENT_PASSWORD, "short-7", cookies));

        assertEquals(400, response.statusCode());

        CookieJar freshCookies = new CookieJar();
        bootstrapCsrf(freshCookies);
        HttpResponse<String> stillWorks = send(loginRequest(username, CURRENT_PASSWORD, freshCookies));
        assertEquals(200, stillWorks.statusCode());
    }

    @Test
    void changingPasswordWithoutCsrfIsRejectedWith403() throws Exception {
        String username = createClaimedUser("change-no-csrf");
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, CURRENT_PASSWORD, cookies);

        String body = "{\"currentPassword\":\"" + CURRENT_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}";
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(uri("/api/auth/password"))
                .header("Content-Type", "application/json")
                .header("Cookie", cookieHeader(cookies))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));

        assertEquals(403, response.statusCode());

        CookieJar freshCookies = new CookieJar();
        bootstrapCsrf(freshCookies);
        HttpResponse<String> stillWorks = send(loginRequest(username, CURRENT_PASSWORD, freshCookies));
        assertEquals(200, stillWorks.statusCode());
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        String body = "{\"currentPassword\":\"x\",\"newPassword\":\"y\"}";

        HttpResponse<String> response = send(authenticated(HttpRequest.newBuilder()
                .uri(uri("/api/auth/password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)), cookies));

        assertEquals(401, response.statusCode());
    }

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
                passwordEncoder.encode(CURRENT_PASSWORD), id.value());
        return username;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void bootstrapCsrf(CookieJar cookies) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder().uri(uri("/api/auth/me")).GET());
        cookies.absorb(response);
        assertNotNull(cookies.get("XSRF-TOKEN"));
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

    private HttpRequest.Builder changePasswordRequest(String currentPassword, String newPassword, CookieJar cookies) {
        String body = "{\"currentPassword\":\"" + currentPassword + "\",\"newPassword\":\"" + newPassword + "\"}";
        return authenticated(HttpRequest.newBuilder()
                .uri(uri("/api/auth/password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)), cookies);
    }

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
}
