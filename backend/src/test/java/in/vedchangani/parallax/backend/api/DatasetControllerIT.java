package in.vedchangani.parallax.backend.api;

import com.jayway.jsonpath.JsonPath;
import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.DatasetFixtures;
import in.vedchangani.parallax.backend.security.AuthenticatedMockMvcConfig;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.CurrentUser;
import in.vedchangani.parallax.backend.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthenticatedMockMvcConfig.class})
class DatasetControllerIT {

    private static final String SIMPLE_CSV = "date,open,high,low,close,volume\n"
            + "2024-01-02,100,105,99,104,1000\n"
            + "2024-01-03,104,110,103,108,2000\n";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CurrentUser currentUser;

    private UserId owner;

    @BeforeEach
    void setUpOwner() {
        owner = TestUsers.create(jdbcTemplate, "ctrl");
        when(currentUser.id()).thenReturn(owner);
    }

    @Test
    void fullCreateListGetAndVersionLifecycle() throws Exception {
        String name = DatasetFixtures.uniqueName();

        MvcResult created = mockMvc.perform(post("/api/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDatasetJson(name, "AAPL")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/datasets/")))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.latestVersionNumber").value(0))
                .andReturn();
        long id = idFromLocation(created);

        mockMvc.perform(get("/api/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        mockMvc.perform(get("/api/datasets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        MvcResult versionCreated = mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "prices.csv", "text/csv",
                                SIMPLE_CSV.getBytes(StandardCharsets.US_ASCII)))
                        .param("adjustmentBasis", "RAW"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/datasets/" + id + "/versions/1"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.source").value("CSV_UPLOAD"))
                .andExpect(jsonPath("$.sourceDetail").value("prices.csv"))
                .andExpect(jsonPath("$.adjustmentBasis").value("RAW"))
                .andExpect(jsonPath("$.barCount").value(2))
                .andExpect(jsonPath("$.contentHash").value(matchesPattern("[0-9a-f]{64}")))
                .andReturn();
        String contentHash = JsonPath.read(versionCreated.getResponse().getContentAsString(), "$.contentHash");

        mockMvc.perform(get("/api/datasets/" + id + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/datasets/" + id + "/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentHash").value(contentHash));

        MvcResult bars = mockMvc.perform(get("/api/datasets/" + id + "/versions/1/bars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentHash").value(contentHash))
                .andExpect(jsonPath("$.bars.length()").value(2))
                .andExpect(jsonPath("$.bars[0].open").value("100"))
                .andExpect(jsonPath("$.bars[0].volume").value(1000))
                .andReturn();

        String barsJson = bars.getResponse().getContentAsString();
        String rebuiltCsv = rebuildCsvFromBarsResponse(barsJson);
        mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "roundtrip.csv", "text/csv",
                                rebuiltCsv.getBytes(StandardCharsets.US_ASCII)))
                        .param("adjustmentBasis", "RAW"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentHash").value(contentHash));
    }

    @Test
    void unknownFieldInCreateRequestIsRejected() throws Exception {
        String body = "{\"name\":\"" + DatasetFixtures.uniqueName() + "\",\"symbol\":\"AAPL\",\"extra\":\"x\"}";
        mockMvc.perform(post("/api/datasets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lowercaseSymbolIsRejected() throws Exception {
        mockMvc.perform(post("/api/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDatasetJson(DatasetFixtures.uniqueName(), "aapl")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateDatasetNameIsRejected() throws Exception {
        String name = DatasetFixtures.uniqueName();
        mockMvc.perform(post("/api/datasets").contentType(MediaType.APPLICATION_JSON)
                        .content(createDatasetJson(name, "AAPL")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/datasets").contentType(MediaType.APPLICATION_JSON)
                        .content(createDatasetJson(name, "MSFT")))
                .andExpect(status().isConflict());
    }

    @Test
    void missingFilePartIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(multipart("/api/datasets/" + id + "/versions").param("adjustmentBasis", "RAW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAdjustmentBasisIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "prices.csv", "text/csv",
                                SIMPLE_CSV.getBytes(StandardCharsets.US_ASCII))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lowercaseAdjustmentBasisIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "prices.csv", "text/csv",
                                SIMPLE_CSV.getBytes(StandardCharsets.US_ASCII)))
                        .param("adjustmentBasis", "raw"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownExtraMultipartPartIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "prices.csv", "text/csv",
                                SIMPLE_CSV.getBytes(StandardCharsets.US_ASCII)))
                        .file(new MockMultipartFile("extra", "extra.csv", "text/csv", new byte[0]))
                        .param("adjustmentBasis", "RAW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankFilenameIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "", "text/csv",
                                SIMPLE_CSV.getBytes(StandardCharsets.US_ASCII)))
                        .param("adjustmentBasis", "RAW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jsonBodyToTheVersionsEndpointIsUnsupportedMediaType() throws Exception {
        long id = createDataset();
        mockMvc.perform(post("/api/datasets/" + id + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void malformedCsvReports400WithLine() throws Exception {
        long id = createDataset();
        byte[] badCsv = "wrong,header,shape\n1,2,3\n".getBytes(StandardCharsets.US_ASCII);
        mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv", badCsv))
                        .param("adjustmentBasis", "RAW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.line").value(1));
    }

    @Test
    void invalidCsvDataReports422WithLine() throws Exception {
        long id = createDataset();
        byte[] invalidCsv = ("date,open,high,low,close,volume\n2024-01-02,0,105,99,104,1000\n")
                .getBytes(StandardCharsets.US_ASCII);
        mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "invalid.csv", "text/csv", invalidCsv))
                        .param("adjustmentBasis", "RAW"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.line").value(2));
    }

    @Test
    void crossOwnerAccessIs404ForDatasetVersionAndBars() throws Exception {
        long id = createDataset();
        mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "prices.csv", "text/csv",
                                SIMPLE_CSV.getBytes(StandardCharsets.US_ASCII)))
                        .param("adjustmentBasis", "RAW"))
                .andExpect(status().isCreated());

        UserId other = TestUsers.create(jdbcTemplate, "other");
        when(currentUser.id()).thenReturn(other);

        mockMvc.perform(get("/api/datasets/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/datasets/" + id + "/versions/1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/datasets/" + id + "/versions/1/bars")).andExpect(status().isNotFound());
    }

    @Test
    void errorBodiesNeverLeakInternalDetails() throws Exception {
        long id = createDataset();
        byte[] badCsv = "wrong,header\n1,2\n".getBytes(StandardCharsets.US_ASCII);
        MvcResult result = mockMvc.perform(multipart("/api/datasets/" + id + "/versions")
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv", badCsv))
                        .param("adjustmentBasis", "RAW"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("in.vedchangani"));
        assertFalse(body.contains("Exception"));
        assertFalse(body.toLowerCase().contains("stacktrace"));
    }

    private long createDataset() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDatasetJson(DatasetFixtures.uniqueName(), "AAPL")))
                .andExpect(status().isCreated())
                .andReturn();
        return idFromLocation(created);
    }

    private static String createDatasetJson(String name, String symbol) {
        return "{\"name\":\"" + name + "\",\"symbol\":\"" + symbol + "\"}";
    }

    private static long idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @SuppressWarnings("unchecked")
    private static String rebuildCsvFromBarsResponse(String barsJson) {
        java.util.List<java.util.Map<String, Object>> bars = JsonPath.read(barsJson, "$.bars");
        StringBuilder sb = new StringBuilder("date,open,high,low,close,volume\n");
        for (java.util.Map<String, Object> bar : bars) {
            sb.append(bar.get("date")).append(',')
                    .append(bar.get("open")).append(',')
                    .append(bar.get("high")).append(',')
                    .append(bar.get("low")).append(',')
                    .append(bar.get("close")).append(',')
                    .append(bar.get("volume"))
                    .append('\n');
        }
        return sb.toString();
    }
}
