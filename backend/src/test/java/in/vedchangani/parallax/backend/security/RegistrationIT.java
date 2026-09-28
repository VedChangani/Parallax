package in.vedchangani.parallax.backend.security;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-38 self-registration, against a real socket like {@link
 * AuthenticationIT} — CSRF is a real cookie/header round trip here, not a
 * {@code MockMvc} shortcut, and {@code POST /api/auth/register} needs no
 * {@code @MockitoBean CurrentUser} at all: it never resolves an owner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RegistrationIT {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // --- success ---------------------------------------------------------------

    @Test
    void successfulRegistrationReturns201WithUsernameOnly() throws Exception {
        String username = uniqueUsername("register-ok");

        HttpResponse<String> response = register(username, "a-perfectly-fine-password");

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"username\""));
        assertTrue(response.body().contains(username));
    }

    @Test
    void newlyRegisteredUserCanLogIn() throws Exception {
        String username = uniqueUsername("register-login");
        String password = "a-perfectly-fine-password";
        assertEquals(201, register(username, password).statusCode());

        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        HttpResponse<String> login = send(loginRequest(username, password, cookies));

        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("\"" + username + "\""));
    }

    @Test
    void newlyRegisteredUserInitiallySeesEmptyOwnedResourceLists() throws Exception {
        String username = uniqueUsername("register-empty");
        String password = "a-perfectly-fine-password";
        assertEquals(201, register(username, password).statusCode());

        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(username, password, cookies);

        HttpResponse<String> strategies = send(authenticated(HttpRequest.newBuilder()
                .uri(uri("/api/strategies")).GET(), cookies));
        HttpResponse<String> datasets = send(authenticated(HttpRequest.newBuilder()
                .uri(uri("/api/datasets")).GET(), cookies));

        assertEquals(200, strategies.statusCode());
        assertEquals("[]", strategies.body().trim());
        assertEquals(200, datasets.statusCode());
        assertEquals("[]", datasets.body().trim());
    }

    @Test
    void responseNeverContainsPasswordOrPasswordHash() throws Exception {
        HttpResponse<String> response = register(uniqueUsername("register-noleak"), "a-perfectly-fine-password");

        String body = response.body().toLowerCase();
        assertFalse(body.contains("password"));
        assertFalse(body.contains("hash"));
    }

    // --- duplicates --------------------------------------------------------------

    @Test
    void duplicateUsernameReturns409() throws Exception {
        String username = uniqueUsername("register-dup");
        assertEquals(201, register(username, "a-perfectly-fine-password").statusCode());

        HttpResponse<String> second = register(username, "another-perfectly-fine-pw");

        assertEquals(409, second.statusCode());
        assertTrue(contentType(second).startsWith("application/problem+json"));
    }

    @Test
    void registeringDevReturns409() throws Exception {
        HttpResponse<String> response = register("dev", "a-perfectly-fine-password");

        assertEquals(409, response.statusCode());
    }

    @Test
    void concurrentDuplicateRegistrationOnlyOneSucceeds() throws Exception {
        String username = uniqueUsername("register-race");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> raceRegister(username, ready, go)),
                    executor.submit(() -> raceRegister(username, ready, go)));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();

            AtomicInteger created = new AtomicInteger();
            AtomicInteger conflicted = new AtomicInteger();
            for (Future<Integer> future : futures) {
                int status = future.get(10, TimeUnit.SECONDS);
                if (status == 201) {
                    created.incrementAndGet();
                } else if (status == 409) {
                    conflicted.incrementAndGet();
                }
            }

            assertEquals(1, created.get(), "exactly one concurrent registration must succeed");
            assertEquals(1, conflicted.get(), "exactly one concurrent registration must be rejected as a duplicate");
        } finally {
            executor.shutdownNow();
        }
    }

    private int raceRegister(String username, CountDownLatch ready, CountDownLatch go) throws Exception {
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        ready.countDown();
        go.await();
        return send(registerRequest(username, "a-perfectly-fine-password", cookies)).statusCode();
    }

    // --- username validation -----------------------------------------------------

    @Test
    void invalidUsernamesReturn400() throws Exception {
        List<String> invalid = List.of(
                "ab", // too short (< 3 chars)
                "Uppercase1", // uppercase not allowed
                "a".repeat(65), // too long (> 64 chars)
                "has space", // invalid character
                "_leadingunderscore" // must start with [a-z0-9]
        );

        for (String username : invalid) {
            HttpResponse<String> response = register(username, "a-perfectly-fine-password");
            assertEquals(400, response.statusCode(), "expected 400 for username: " + username);
        }
    }

    // --- password validation -------------------------------------------------------

    @Test
    void fourteenCharacterPasswordReturns400() throws Exception {
        HttpResponse<String> response = register(uniqueUsername("register-short-pw"), "x".repeat(14));
        assertEquals(400, response.statusCode());
    }

    @Test
    void overSeventyTwoUtf8BytePasswordReturns400() throws Exception {
        HttpResponse<String> response = register(uniqueUsername("register-long-pw"), "x".repeat(73));
        assertEquals(400, response.statusCode());
    }

    @Test
    void exactlyFifteenCharacterPasswordIsAccepted() throws Exception {
        HttpResponse<String> response = register(uniqueUsername("register-min-pw"), "x".repeat(15));
        assertEquals(201, response.statusCode());
    }

    @Test
    void exactlySeventyTwoUtf8BytePasswordIsAccepted() throws Exception {
        HttpResponse<String> response = register(uniqueUsername("register-max-pw"), "x".repeat(72));
        assertEquals(201, response.statusCode());
    }

    // --- request shape -------------------------------------------------------------

    @Test
    void unknownFieldIsRejectedWith400() throws Exception {
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        String body = "{\"username\":\"" + uniqueUsername("register-unknown")
                + "\",\"password\":\"a-perfectly-fine-password\",\"ownerId\":1}";

        HttpResponse<String> response = send(authenticated(HttpRequest.newBuilder()
                .uri(uri("/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)), cookies));

        assertEquals(400, response.statusCode());
    }

    // --- CSRF ------------------------------------------------------------------

    @Test
    void missingOrInvalidCsrfReturns403() throws Exception {
        String body = registrationBody(uniqueUsername("register-no-csrf"), "a-perfectly-fine-password");

        HttpResponse<String> withoutCsrf = send(HttpRequest.newBuilder()
                .uri(uri("/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
        assertEquals(403, withoutCsrf.statusCode());

        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        HttpResponse<String> invalidCsrf = send(HttpRequest.newBuilder()
                .uri(uri("/api/auth/register"))
                .header("Content-Type", "application/json")
                .header("Cookie", cookieHeader(cookies))
                .header("X-XSRF-TOKEN", "not-the-real-token")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
        assertEquals(403, invalidCsrf.statusCode());
    }

    // --- registering while already authenticated -----------------------------------

    @Test
    void registeringWhileLoggedInDoesNotChangeTheCurrentIdentity() throws Exception {
        String usernameA = uniqueUsername("register-already-a");
        String passwordA = "a-perfectly-fine-password";
        assertEquals(201, register(usernameA, passwordA).statusCode());

        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        login(usernameA, passwordA, cookies);

        String usernameB = uniqueUsername("register-already-b");
        HttpResponse<String> registerB = send(registerRequest(usernameB, "another-perfectly-fine-pw", cookies));
        assertEquals(201, registerB.statusCode());

        HttpResponse<String> me = send(authenticated(HttpRequest.newBuilder().uri(uri("/api/auth/me")).GET(),
                cookies));
        assertEquals(200, me.statusCode());
        assertTrue(me.body().contains(usernameA));
        assertFalse(me.body().contains(usernameB));
    }

    // --- helpers -------------------------------------------------------------

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

    private static String registrationBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private HttpRequest.Builder registerRequest(String username, String password, CookieJar cookies) {
        return authenticated(HttpRequest.newBuilder()
                .uri(uri("/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(registrationBody(username, password),
                        StandardCharsets.UTF_8)), cookies);
    }

    /** A self-contained register call: bootstraps its own CSRF cookie, since most tests only need one attempt. */
    private HttpResponse<String> register(String username, String password) throws Exception {
        CookieJar cookies = new CookieJar();
        bootstrapCsrf(cookies);
        return send(registerRequest(username, password, cookies));
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

    private static String uniqueUsername(String label) {
        return (label + "-" + System.nanoTime()).toLowerCase();
    }
}
