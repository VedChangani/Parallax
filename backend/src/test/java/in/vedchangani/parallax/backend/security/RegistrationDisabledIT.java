package in.vedchangani.parallax.backend.security;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-38: {@code parallax.auth.registration-enabled=false} disables {@code
 * POST /api/auth/register} at the application level (403, from {@code
 * UserRegistrationService}), never at the Spring Security authorization
 * layer — the endpoint stays {@code permitAll} regardless, since an
 * anonymous caller must still reach the service to get this specific 403
 * body rather than a generic 401. A separate {@code @SpringBootTest}
 * property override needs its own Spring context, hence its own test
 * class rather than a method inside {@link RegistrationIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "parallax.auth.registration-enabled=false")
@Import(TestcontainersConfiguration.class)
class RegistrationDisabledIT {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void registrationDisabledReturns403() throws Exception {
        HttpResponse<String> bootstrap = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfCookie = bootstrap.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow();
        String token = csrfCookie.split(";", 2)[0].substring("XSRF-TOKEN=".length());
        assertNotNull(token);

        String body = "{\"username\":\"registration-disabled-" + System.nanoTime()
                + "\",\"password\":\"a-perfectly-fine-password\"}";
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + token)
                .header("X-XSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
    }
}
