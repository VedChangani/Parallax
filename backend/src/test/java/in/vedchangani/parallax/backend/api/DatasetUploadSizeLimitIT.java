package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.DatasetFixtures;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.CurrentUser;
import in.vedchangani.parallax.backend.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * D-32 §3: a single upload above {@code
 * spring.servlet.multipart.max-file-size} is rejected with 413. This needs
 * a real HTTP request (MockMvc bypasses multipart size enforcement), so it
 * runs against a real random port using the JDK's own {@link HttpClient} —
 * no additional test dependency required.
 *
 * <p>Since D-37, every {@code /api/**} request also needs a real
 * authenticated session and CSRF token — {@code @MockitoBean CurrentUser}
 * only substitutes which owner a controller resolves, it does not satisfy
 * Spring Security's filter chain for a real socket-level request the way
 * it does for a {@code MockMvc} request (see {@code
 * AuthenticatedMockMvcConfig}). {@link #login()} performs the real
 * bootstrap-CSRF-cookie / login round trip once per test, directly setting
 * this owner's {@code password_hash} rather than going through the
 * (not-yet-implemented) registration endpoint or the startup claim runner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DatasetUploadSizeLimitIT {

    private static final String TEST_PASSWORD = "upload-limit-test-password";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private CurrentUser currentUser;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, String> cookies = new HashMap<>();

    @BeforeEach
    void setUpOwner() throws Exception {
        UserId owner = TestUsers.create(jdbcTemplate, "upload-limit");
        when(currentUser.id()).thenReturn(owner);

        String username = jdbcTemplate.queryForObject(
                "select username from app_user where id = ?", String.class, owner.value());
        jdbcTemplate.update("update app_user set password_hash = ? where id = ?",
                passwordEncoder.encode(TEST_PASSWORD), owner.value());

        login(username);
    }

    @Test
    void uploadLargerThanTheConfiguredLimitIsRejectedWith413() throws Exception {
        long datasetId = createDataset();

        String boundary = "----D32Boundary" + System.nanoTime();
        byte[] oversized = new byte[5 * 1024 * 1024 + 1]; // one byte over the 5MB limit

        HttpRequest request = authenticated(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/datasets/" + datasetId + "/versions"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary))
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        multipartBody(boundary, oversized)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(413, response.statusCode());
    }

    /**
     * Establishes the CSRF cookie anonymously via {@code GET /api/auth/me}
     * (D-37: the bootstrap request), then logs in — capturing the rotated
     * session and CSRF cookies from the login response for every
     * subsequent request in this test.
     */
    private void login(String username) throws Exception {
        HttpRequest bootstrap = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/me"))
                .GET()
                .build();
        HttpResponse<String> bootstrapResponse = httpClient.send(bootstrap, HttpResponse.BodyHandlers.ofString());
        captureCookies(bootstrapResponse);

        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(TEST_PASSWORD, StandardCharsets.UTF_8);
        HttpRequest loginRequest = authenticated(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded"))
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> loginResponse = httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        captureCookies(loginResponse);
        assertEquals(200, loginResponse.statusCode(), "login must succeed for this test's fixture to work at all");
    }

    /** Attaches the accumulated session/CSRF cookies and the CSRF header to a request builder. */
    private HttpRequest.Builder authenticated(HttpRequest.Builder builder) {
        String cookieHeader = cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
        builder.header("Cookie", cookieHeader);
        String csrfToken = cookies.get("XSRF-TOKEN");
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        return builder;
    }

    private void captureCookies(HttpResponse<?> response) {
        for (String setCookie : response.headers().allValues("Set-Cookie")) {
            String pair = setCookie.split(";", 2)[0];
            int eq = pair.indexOf('=');
            if (eq > 0) {
                cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
    }

    private long createDataset() throws Exception {
        String body = "{\"name\":\"" + DatasetFixtures.uniqueName() + "\",\"symbol\":\"AAPL\"}";
        HttpRequest request = authenticated(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/datasets"))
                .header("Content-Type", "application/json"))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String location = response.headers().firstValue("Location").orElseThrow();
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private static byte[] multipartBody(String boundary, byte[] fileContent) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";

        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.US_ASCII));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"oversized.csv\"" + crlf)
                .getBytes(StandardCharsets.US_ASCII));
        out.write(("Content-Type: text/csv" + crlf + crlf).getBytes(StandardCharsets.US_ASCII));
        out.write(fileContent);
        out.write(crlf.getBytes(StandardCharsets.US_ASCII));

        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.US_ASCII));
        out.write(("Content-Disposition: form-data; name=\"adjustmentBasis\"" + crlf + crlf)
                .getBytes(StandardCharsets.US_ASCII));
        out.write("RAW".getBytes(StandardCharsets.US_ASCII));
        out.write(crlf.getBytes(StandardCharsets.US_ASCII));

        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }
}
