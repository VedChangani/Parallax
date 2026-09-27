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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * D-32 §3: a single upload above {@code
 * spring.servlet.multipart.max-file-size} is rejected with 413. This needs
 * a real HTTP request (MockMvc bypasses multipart size enforcement), so it
 * runs against a real random port using the JDK's own {@link HttpClient} —
 * no additional test dependency required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DatasetUploadSizeLimitIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CurrentUser currentUser;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUpOwner() {
        UserId owner = TestUsers.create(jdbcTemplate, "upload-limit");
        when(currentUser.id()).thenReturn(owner);
    }

    @Test
    void uploadLargerThanTheConfiguredLimitIsRejectedWith413() throws Exception {
        long datasetId = createDataset();

        String boundary = "----D32Boundary" + System.nanoTime();
        byte[] oversized = new byte[5 * 1024 * 1024 + 1]; // one byte over the 5MB limit

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/datasets/" + datasetId + "/versions"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        multipartBody(boundary, oversized)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(413, response.statusCode());
    }

    private long createDataset() throws Exception {
        String body = "{\"name\":\"" + DatasetFixtures.uniqueName() + "\",\"symbol\":\"AAPL\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/datasets"))
                .header("Content-Type", "application/json")
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
