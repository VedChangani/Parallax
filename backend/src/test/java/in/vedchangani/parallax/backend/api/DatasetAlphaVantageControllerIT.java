package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.DatasetFixtures;
import in.vedchangani.parallax.backend.marketdata.DailyBars;
import in.vedchangani.parallax.backend.marketdata.HistoryDepth;
import in.vedchangani.parallax.backend.marketdata.InvalidMarketDataException;
import in.vedchangani.parallax.backend.marketdata.MarketDataCapabilityException;
import in.vedchangani.parallax.backend.marketdata.MarketDataProvider;
import in.vedchangani.parallax.backend.marketdata.MarketDataRequestRejectedException;
import in.vedchangani.parallax.backend.marketdata.MarketDataResponseException;
import in.vedchangani.parallax.backend.marketdata.MarketDataUnavailableException;
import in.vedchangani.parallax.backend.security.AuthenticatedMockMvcConfig;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.CurrentUser;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.data.Bar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-33 Batch 4 REST-boundary proof (MockMvc, real PostgreSQL via
 * Testcontainers): {@code POST /api/datasets/{id}/versions/alpha-vantage}'s
 * request/response contract, strict-JSON validation, ownership, and the
 * provider error-mapping table. {@link CurrentUser} and {@link
 * MarketDataProvider} are both overridden with {@code @MockitoBean} — the
 * real Alpha Vantage HTTP adapter is never invoked. Mirrors {@code
 * DatasetControllerIT}'s own style; CSV upload behavior itself is
 * unaffected and not retested here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthenticatedMockMvcConfig.class})
class DatasetAlphaVantageControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CurrentUser currentUser;

    @MockitoBean
    private MarketDataProvider marketDataProvider;

    private UserId owner;

    @BeforeEach
    void setUpOwner() {
        owner = TestUsers.create(jdbcTemplate, "avctrl");
        when(currentUser.id()).thenReturn(owner);
    }

    // --- happy path ------------------------------------------------------------

    @Test
    void compactImportReturns201WithExpectedMetadataAndPersistedBars() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(eq("AAPL"), eq(HistoryDepth.COMPACT)))
                .thenReturn(sampleDailyBars(HistoryDepth.COMPACT));

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPACT")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/datasets/" + id + "/versions/1"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.source").value("ALPHA_VANTAGE"))
                .andExpect(jsonPath("$.sourceDetail").value("TIME_SERIES_DAILY;outputsize=compact"))
                .andExpect(jsonPath("$.adjustmentBasis").value("RAW"))
                .andExpect(jsonPath("$.barCount").value(2))
                .andExpect(jsonPath("$.contentHash").value(matchesPattern("[0-9a-f]{64}")));

        mockMvc.perform(get("/api/datasets/" + id + "/versions/1/bars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bars.length()").value(2))
                .andExpect(jsonPath("$.bars[0].open").value("100"))
                .andExpect(jsonPath("$.bars[0].volume").value(1000));
    }

    // --- FULL --------------------------------------------------------------------

    @Test
    void fullImportReachesProviderWithFullDepthAndMatchingSourceDetail() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(eq("AAPL"), eq(HistoryDepth.FULL)))
                .thenReturn(sampleDailyBars(HistoryDepth.FULL));

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("FULL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceDetail").value("TIME_SERIES_DAILY;outputsize=full"));

        verify(marketDataProvider).fetchDailyBars("AAPL", HistoryDepth.FULL);
    }

    // --- validation ----------------------------------------------------------------

    @Test
    void missingHistoryDepthIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    void nullHistoryDepthIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historyDepth\":null}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    void unknownHistoryDepthValueIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historyDepth\":\"WEEKLY\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historyDepth\":"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    void unknownExtraPropertyIsRejected() throws Exception {
        long id = createDataset();
        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historyDepth\":\"COMPACT\",\"apiKey\":\"x\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(marketDataProvider);
    }

    // --- ownership ------------------------------------------------------------------

    @Test
    void anotherOwnersDatasetReturns404AndProviderIsNeverCalled() throws Exception {
        long id = createDataset();
        UserId other = TestUsers.create(jdbcTemplate, "avother");
        when(currentUser.id()).thenReturn(other);

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPACT")))
                .andExpect(status().isNotFound());

        verify(marketDataProvider, never()).fetchDailyBars(any(), any());
    }

    // --- provider/API failures ------------------------------------------------------

    @Test
    void missingApiKeyReturns503AndCreatesNoVersion() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(any(), any()))
                .thenThrow(new MarketDataUnavailableException("alpha vantage api key is not configured"));

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPACT")))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/datasets/" + id + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rateLimitReturns503() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(any(), any()))
                .thenThrow(new MarketDataUnavailableException("alpha vantage is temporarily unavailable"));

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPACT")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void transportFailureReturns502() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(any(), any()))
                .thenThrow(new MarketDataResponseException("alpha vantage response could not be read"));

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPACT")))
                .andExpect(status().isBadGateway());
    }

    @Test
    void invalidMarketDataReturns422() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(any(), any()))
                .thenThrow(new InvalidMarketDataException("empty time series"));

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPACT")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void requestRejectedReturns422() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(any(), any()))
                .thenThrow(new MarketDataRequestRejectedException("alpha vantage rejected the request"));

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPACT")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void fullCapabilityLimitReturns422WithNoFallbackToCompact() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(eq("AAPL"), eq(HistoryDepth.FULL)))
                .thenThrow(new MarketDataCapabilityException("alpha vantage full history requires a premium plan"));

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("FULL")))
                .andExpect(status().isUnprocessableEntity());

        verify(marketDataProvider, never()).fetchDailyBars(any(), eq(HistoryDepth.COMPACT));
    }

    // --- integrity ------------------------------------------------------------------

    @Test
    void persistedVersionIsVerifiableThroughBarsEndpoint() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(eq("AAPL"), eq(HistoryDepth.COMPACT)))
                .thenReturn(sampleDailyBars(HistoryDepth.COMPACT));

        mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPACT")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/datasets/" + id + "/versions/1/bars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bars.length()").value(2))
                .andExpect(jsonPath("$.contentHash").value(matchesPattern("[0-9a-f]{64}")));
    }

    // --- security: no internal/secret details leaked ---------------------------------

    @Test
    void errorResponseNeverExposesApiKeyOrInternalDetails() throws Exception {
        long id = createDataset();
        when(marketDataProvider.fetchDailyBars(any(), any()))
                .thenThrow(new MarketDataUnavailableException("alpha vantage api key is not configured"));

        MvcResult result = mockMvc.perform(post(alphaVantageUrl(id)).contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPACT")))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.toLowerCase().contains("apikey"));
        assertFalse(body.toLowerCase().contains("api_key"));
        assertFalse(body.contains("in.vedchangani"));
        assertFalse(body.contains("Exception"));
        assertFalse(body.toLowerCase().contains("stacktrace"));
    }

    // --- helpers ----------------------------------------------------------------

    private long createDataset() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + DatasetFixtures.uniqueName() + "\",\"symbol\":\"AAPL\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private static String alphaVantageUrl(long datasetId) {
        return "/api/datasets/" + datasetId + "/versions/alpha-vantage";
    }

    private static String requestJson(String historyDepth) {
        return "{\"historyDepth\":\"" + historyDepth + "\"}";
    }

    private static DailyBars sampleDailyBars(HistoryDepth depth) {
        String sourceDetail = depth == HistoryDepth.COMPACT
                ? "TIME_SERIES_DAILY;outputsize=compact"
                : "TIME_SERIES_DAILY;outputsize=full";
        List<Bar> bars = List.of(
                new Bar(LocalDate.of(2024, 1, 2), new BigDecimal("100"), new BigDecimal("105"),
                        new BigDecimal("99"), new BigDecimal("104"), 1000),
                new Bar(LocalDate.of(2024, 1, 3), new BigDecimal("104"), new BigDecimal("110"),
                        new BigDecimal("103"), new BigDecimal("108"), 2000));
        return new DailyBars(bars, sourceDetail);
    }
}
