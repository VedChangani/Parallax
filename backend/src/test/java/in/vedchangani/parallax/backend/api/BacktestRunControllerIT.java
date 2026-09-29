package in.vedchangani.parallax.backend.api;

import com.jayway.jsonpath.JsonPath;
import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.AdjustmentBasis;
import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.dataset.DatasetSummary;
import in.vedchangani.parallax.backend.security.AuthenticatedMockMvcConfig;
import in.vedchangani.parallax.backend.strategy.StrategyService;
import in.vedchangani.parallax.backend.strategy.StrategySummary;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.CurrentUser;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.Backtester;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-34 Batch 3 REST-boundary proof (MockMvc, real PostgreSQL via
 * Testcontainers): the full create/read request-response contract, strict
 * D-30 request parsing through HTTP, ownership, the error-mapping table,
 * and that no read endpoint re-invokes the engine or recomputes stored
 * metrics/benchmark values. Mirrors {@code StrategyControllerIT}/{@code
 * DatasetControllerIT}'s own style. Strategy/dataset setup uses {@link
 * StrategyService}/{@link DatasetService} directly (not their own REST
 * endpoints, already covered by their own controller tests) so this file
 * stays focused on {@link BacktestRunController} itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthenticatedMockMvcConfig.class})
class BacktestRunControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private DatasetService datasetService;

    @MockitoBean
    private CurrentUser currentUser;

    @MockitoSpyBean
    private Backtester backtester;

    private UserId owner;

    @BeforeEach
    void setUpOwner() {
        owner = TestUsers.create(jdbcTemplate, "brc");
        when(currentUser.id()).thenReturn(owner);
    }

    // --- fixtures ----------------------------------------------------------------

    /** Enters when SMA(1) (= close) > 102, exits when close < 98 - genuinely trades. */
    private static StrategyDefinition tradingStrategy() {
        return new StrategyDefinition(
                new Condition.Compare(new Operand.IndicatorRef(new IndicatorSpec(IndicatorType.SMA, 1)),
                        Operator.GT, new Operand.Constant(102)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(98)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    /** Always enters, never exits - used for the open-trade and zero-quantity-rejection scenarios. */
    private static StrategyDefinition alwaysEnterStrategy() {
        return new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(0)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    private static byte[] sixBarCsv() {
        return ("date,open,high,low,close,volume\n"
                + "2024-01-02,100,101,99,100,1000\n"
                + "2024-01-03,100,106,99,105,1000\n"
                + "2024-01-04,105,109,104,108,1000\n"
                + "2024-01-05,108,110,95,96,1000\n"
                + "2024-01-08,96,99,90,92,1000\n"
                + "2024-01-09,92,95,88,90,1000\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] twoBarCsv() {
        return ("date,open,high,low,close,volume\n"
                + "2024-01-02,100,105,99,104,1000\n"
                + "2024-01-03,104,110,103,108,2000\n").getBytes(StandardCharsets.US_ASCII);
    }

    private record OwnedRefs(long strategyId, long datasetId) {
    }

    private OwnedRefs createOwnedStrategyAndDataset(UserId asOwner, StrategyDefinition definition, byte[] csv) {
        StrategySummary strategy = strategyService.createStrategy(asOwner, "brc-strategy-" + System.nanoTime(), "",
                definition);
        DatasetSummary dataset = datasetService.createDataset(asOwner, "brc-dataset-" + System.nanoTime(), "AAPL");
        datasetService.createVersionFromCsv(asOwner, dataset.id(), csv, AdjustmentBasis.RAW, "bars.csv");
        return new OwnedRefs(strategy.id(), dataset.id());
    }

    private static String createRunJson(long strategyId, int strategyVersion, long datasetId, int datasetVersion,
                                         String initialCapital, String commissionPerFill, String slippageRate,
                                         String startDate, String endDate) {
        return ("{\"strategyId\":%d,\"strategyVersion\":%d,\"datasetId\":%d,\"datasetVersion\":%d,"
                + "\"config\":{\"initialCapital\":\"%s\",\"commissionPerFill\":\"%s\",\"slippageRate\":\"%s\","
                + "\"startDate\":\"%s\",\"endDate\":\"%s\"}}")
                .formatted(strategyId, strategyVersion, datasetId, datasetVersion, initialCapital, commissionPerFill,
                        slippageRate, startDate, endDate);
    }

    private static String tradingRunJson(OwnedRefs refs) {
        return createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "10000", "0", "0", "2024-01-02",
                "2024-01-09");
    }

    private static long idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    // --- A: create happy path -----------------------------------------------------

    @Test
    void createRunHappyPath() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());

        mockMvc.perform(post("/api/backtest-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/backtest-runs/")))
                .andExpect(jsonPath("$.strategyId").value(refs.strategyId()))
                .andExpect(jsonPath("$.strategyVersion").value(1))
                .andExpect(jsonPath("$.datasetId").value(refs.datasetId()))
                .andExpect(jsonPath("$.datasetVersion").value(1))
                .andExpect(jsonPath("$.engineSemanticsVersion").value(Backtester.SEMANTICS_VERSION))
                .andExpect(jsonPath("$.initialCapital").value("10000"))
                .andExpect(jsonPath("$.commissionPerFill").value("0"))
                .andExpect(jsonPath("$.slippageRate").value("0"))
                .andExpect(jsonPath("$.startDate").value("2024-01-02"))
                .andExpect(jsonPath("$.endDate").value("2024-01-09"))
                .andExpect(jsonPath("$.firstEvaluableDate").value("2024-01-02"))
                .andExpect(jsonPath("$.totalCommission").value("0"))
                .andExpect(jsonPath("$.metrics.closedTradeCount").value(1))
                .andExpect(jsonPath("$.metrics.cagr").doesNotExist())
                .andExpect(jsonPath("$.benchmark.quantity").exists())
                .andExpect(jsonPath("$.createdAt").exists());
    }

    // --- B: strict request parsing -------------------------------------------------

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPropertyIsBadRequest() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        String withOwnerId = "{\"strategyId\":%d,\"strategyVersion\":1,\"datasetId\":%d,\"datasetVersion\":1,"
                + "\"ownerId\":1,\"config\":{\"initialCapital\":\"10000\",\"commissionPerFill\":\"0\","
                + "\"slippageRate\":\"0\",\"startDate\":\"2024-01-02\",\"endDate\":\"2024-01-09\"}}";
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(withOwnerId.formatted(refs.strategyId(), refs.datasetId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("ownerId"));
    }

    @Test
    void numericDecimalFieldIsBadRequest() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        String withNumericCapital = "{\"strategyId\":%d,\"strategyVersion\":1,\"datasetId\":%d,\"datasetVersion\":1,"
                + "\"config\":{\"initialCapital\":10000,\"commissionPerFill\":\"0\","
                + "\"slippageRate\":\"0\",\"startDate\":\"2024-01-02\",\"endDate\":\"2024-01-09\"}}";
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(withNumericCapital.formatted(refs.strategyId(), refs.datasetId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedDecimalGrammarIsBadRequest() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "not-a-number", "0", "0",
                                "2024-01-02", "2024-01-09")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("config.initialCapital"));
    }

    // --- defensive numeric bounds (Phase 9 Batch 2b, D-36) ---------------------

    @Test
    void over100CharDecimalLiteralIsBadRequest() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        String text = "1." + "0".repeat(99); // 101 characters
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, text, "0", "0",
                                "2024-01-02", "2024-01-09")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("config.initialCapital"));
    }

    @Test
    void over18IntegerDigitsIsUnprocessable() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        String text = "1" + "0".repeat(18); // 19 integer digits, still grammar-valid and positive
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, text, "0", "0",
                                "2024-01-02", "2024-01-09")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("config.initialCapital"));
    }

    @Test
    void over18FractionalDigitsIsUnprocessable() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        String text = "0." + "9".repeat(19); // 19 fractional digits, still < 1
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "10000", "0", text,
                                "2024-01-02", "2024-01-09")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("config.slippageRate"));
    }

    @Test
    void missingRequiredFieldIsBadRequest() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        String withoutConfig = "{\"strategyId\":%d,\"strategyVersion\":1,\"datasetId\":%d,\"datasetVersion\":1}"
                .formatted(refs.strategyId(), refs.datasetId());
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON).content(withoutConfig))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonPositiveIdIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(0, 1, 1, 1, "10000", "0", "0", "2024-01-02", "2024-01-09")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void versionNumberLessThanOneIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(1, 0, 1, 1, "10000", "0", "0", "2024-01-02", "2024-01-09")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semanticallyInvalidConfigIsUnprocessable() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "0", "0", "0",
                                "2024-01-02", "2024-01-09")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rangeOutsideDatasetCoverageIsUnprocessable() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "10000", "0", "0",
                                "2023-01-01", "2024-01-09")))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- C: ownership ----------------------------------------------------------------

    @Test
    void anotherUsersStrategyIsNotFoundOnCreate() throws Exception {
        UserId other = TestUsers.create(jdbcTemplate, "brc-other-a");
        OwnedRefs otherStrategy = createOwnedStrategyAndDataset(other, tradingStrategy(), sixBarCsv());
        OwnedRefs ownDataset = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());

        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(otherStrategy.strategyId(), 1, ownDataset.datasetId(), 1, "10000",
                                "0", "0", "2024-01-02", "2024-01-09")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersDatasetIsNotFoundOnCreate() throws Exception {
        UserId other = TestUsers.create(jdbcTemplate, "brc-other-b");
        OwnedRefs otherDataset = createOwnedStrategyAndDataset(other, tradingStrategy(), sixBarCsv());
        OwnedRefs ownStrategy = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());

        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(ownStrategy.strategyId(), 1, otherDataset.datasetId(), 1, "10000",
                                "0", "0", "2024-01-02", "2024-01-09")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersRunAndChildEndpointsAreNotFound() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        UserId other = TestUsers.create(jdbcTemplate, "brc-other-c");
        when(currentUser.id()).thenReturn(other);

        mockMvc.perform(get("/api/backtest-runs/" + runId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/equity-curve")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/trades")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/rejections")).andExpect(status().isNotFound());
    }

    @Test
    void listContainsOnlyCurrentUsersRuns() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        UserId other = TestUsers.create(jdbcTemplate, "brc-other-d");
        OwnedRefs otherRefs = createOwnedStrategyAndDataset(other, tradingStrategy(), sixBarCsv());
        when(currentUser.id()).thenReturn(other);
        mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(otherRefs)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/backtest-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        when(currentUser.id()).thenReturn(owner);
        MvcResult listResult = mockMvc.perform(get("/api/backtest-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(runId))
                // I8 (Phase 9 Batch 1): the cheap list endpoint carries the run's date
                // range and returns, not only identity/hash fields.
                .andExpect(jsonPath("$[0].startDate").value("2024-01-02"))
                .andExpect(jsonPath("$[0].endDate").value("2024-01-09"))
                .andExpect(jsonPath("$[0].totalReturn").isNumber())
                .andExpect(jsonPath("$[0].benchmarkTotalReturn").isNumber())
                .andReturn();

        // Cross-check against the fully reconstructed detail response - same stored
        // doubles, read through the cheap list path and the verified detail path.
        String listJson = listResult.getResponse().getContentAsString();
        MvcResult detailResult = mockMvc.perform(get("/api/backtest-runs/" + runId))
                .andExpect(status().isOk())
                .andReturn();
        String detailJson = detailResult.getResponse().getContentAsString();

        Double listTotalReturn = JsonPath.read(listJson, "$[0].totalReturn");
        Double detailTotalReturn = JsonPath.read(detailJson, "$.metrics.totalReturn");
        assertEquals(detailTotalReturn, listTotalReturn);

        Double listBenchmarkReturn = JsonPath.read(listJson, "$[0].benchmarkTotalReturn");
        Double detailBenchmarkReturn = JsonPath.read(detailJson, "$.benchmark.totalReturn");
        assertEquals(detailBenchmarkReturn, listBenchmarkReturn);
    }

    @Test
    void missingRunIsNotFound() throws Exception {
        mockMvc.perform(get("/api/backtest-runs/99999999")).andExpect(status().isNotFound());
    }

    // --- D: GET detail -----------------------------------------------------------

    @Test
    void getRunDetailReturnsFullyPopulatedFields() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        mockMvc.perform(get("/api/backtest-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.definitionHash").isString())
                .andExpect(jsonPath("$.contentHash").isString())
                .andExpect(jsonPath("$.totalSlippageCost").value("0"))
                .andExpect(jsonPath("$.metrics.totalReturn").isNumber())
                .andExpect(jsonPath("$.metrics.maxDrawdown").isNumber())
                // One closed trade, a loss: winRate is present (0.0), not empty - winRate
                // is only empty when closedTradeCount == 0 (D-26).
                .andExpect(jsonPath("$.metrics.winRate").value(0.0))
                .andExpect(jsonPath("$.metrics.averageWin").doesNotExist())
                .andExpect(jsonPath("$.metrics.averageLoss").value(-855.0))
                .andExpect(jsonPath("$.benchmark.cash").isString());
    }

    // --- E: equity curve -----------------------------------------------------------

    @Test
    void equityCurveHasCorrectPointsAndDerivedValues() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        mockMvc.perform(get("/api/backtest-runs/" + runId + "/equity-curve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].date").value("2024-01-02"))
                .andExpect(jsonPath("$[0].cash").value("10000"))
                .andExpect(jsonPath("$[0].equity").value("10000"))
                .andExpect(jsonPath("$[5].date").value("2024-01-09"))
                .andExpect(jsonPath("$[2].date").value("2024-01-04"))
                .andExpect(jsonPath("$[2].cash").value("25"))
                .andExpect(jsonPath("$[2].quantity").value(95))
                .andExpect(jsonPath("$[2].costBasis").value("9975"))
                .andExpect(jsonPath("$[2].close").value("108"))
                .andExpect(jsonPath("$[2].marketValue").value("10260"))
                .andExpect(jsonPath("$[2].equity").value("10285"))
                .andExpect(jsonPath("$[2].unrealizedPnl").value("285"))
                .andExpect(jsonPath("$[2].benchmarkEquity").exists());
    }

    // --- V1.1 Batch 3: profit factor and drawdown series ---------------------------

    @Test
    void profitFactorIsZeroForARunWithOnlyALosingClosedTrade() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        // One closed trade, -855: gross profit 0 / |gross loss| 855 = 0.0 (present, not null).
        mockMvc.perform(get("/api/backtest-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.profitFactor").value(0.0))
                // Existing metrics are unaffected by the new field.
                .andExpect(jsonPath("$.metrics.winRate").value(0.0))
                .andExpect(jsonPath("$.metrics.averageLoss").value(-855.0));
    }

    @Test
    void profitFactorIsNullWhenThereIsNoLosingClosedTrade() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, alwaysEnterStrategy(), twoBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "10000", "0", "0",
                                "2024-01-02", "2024-01-03")))
                .andReturn();
        long runId = idFromLocation(created);

        // Only an open trade: no closed trade, so no losing closed trade - unavailable, never Infinity.
        String body = mockMvc.perform(get("/api/backtest-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.profitFactor").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertEquals(true, body.contains("\"profitFactor\":null"));
    }

    @Test
    void equityCurveDrawdownIsAlignedWithEquityAndUsesTheRunningPeak() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        String body = mockMvc.perform(get("/api/backtest-runs/" + runId + "/equity-curve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].drawdown").value(0.0))
                // 10285 is a new high over 10000: still exactly 0.
                .andExpect(jsonPath("$[2].equity").value("10285"))
                .andExpect(jsonPath("$[2].drawdown").value(0.0))
                .andReturn().getResponse().getContentAsString();

        // Independent recomputation from the response's own exact equity strings.
        java.util.List<String> equities = JsonPath.read(body, "$[*].equity");
        java.util.List<Number> drawdowns = JsonPath.read(body, "$[*].drawdown");
        assertEquals(equities.size(), drawdowns.size());
        BigDecimal peak = new BigDecimal(equities.get(0));
        double maxSeen = 0.0;
        for (int i = 0; i < equities.size(); i++) {
            BigDecimal equity = new BigDecimal(equities.get(i));
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            double expected = peak.subtract(equity).doubleValue() / peak.doubleValue();
            assertEquals(expected, drawdowns.get(i).doubleValue(), "drawdown at index " + i);
            maxSeen = Math.max(maxSeen, drawdowns.get(i).doubleValue());
        }
        // The losing trade must show up as a real drawdown, and the series
        // maximum is exactly the run's own maxDrawdown metric.
        assertEquals(true, maxSeen > 0.0);
        double maxDrawdownMetric = ((Number) JsonPath.read(
                mockMvc.perform(get("/api/backtest-runs/" + runId)).andReturn().getResponse().getContentAsString(),
                "$.metrics.maxDrawdown")).doubleValue();
        assertEquals(maxDrawdownMetric, maxSeen);
    }

    // --- V1.1 Batch 4: CSV export ----------------------------------------------------

    private String getCsv(String path, String expectedFilename) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + expectedFilename + "\""))
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void equityCurveCsvHasTheDocumentedHeaderAndExactValues() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        String csv = getCsv("/api/backtest-runs/" + runId + "/equity-curve.csv",
                "backtest-" + runId + "-equity-curve.csv");

        assertEquals(true, csv.endsWith("\r\n"));
        String[] lines = csv.split("\r\n", -1);
        assertEquals(8, lines.length); // header + 6 bars + the empty tail after the final CRLF
        assertEquals("", lines[7]);
        assertEquals("date,equity,cash,quantity,close,market_value,cost_basis,realized_pnl,unrealized_pnl,"
                + "benchmark_equity,drawdown", lines[0]);
        assertEquals(true, lines[1].startsWith("2024-01-02,10000,10000,0,100,0,0,0,0,"));
        assertEquals(true, lines[1].endsWith(",0"));
        // Bar 3 (2024-01-04): the exact strings the JSON view returns for this point.
        assertEquals(true, lines[3].startsWith("2024-01-04,10285,25,95,108,10260,9975,0,285,"));
        assertEquals(true, lines[3].endsWith(",0")); // 10285 is a new high over 10000
    }

    @Test
    void equityCurveCsvCellsAreCharacterForCharacterTheJsonApiValues() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        String json = mockMvc.perform(get("/api/backtest-runs/" + runId + "/equity-curve"))
                .andReturn().getResponse().getContentAsString();
        String[] lines = getCsv("/api/backtest-runs/" + runId + "/equity-curve.csv",
                "backtest-" + runId + "-equity-curve.csv").split("\r\n");

        String[] jsonFields = {"date", "equity", "cash", "quantity", "close", "marketValue", "costBasis",
                "realizedPnl", "unrealizedPnl", "benchmarkEquity"};
        for (int row = 0; row < 6; row++) {
            String[] cells = lines[row + 1].split(",", -1);
            assertEquals(11, cells.length);
            for (int col = 0; col < jsonFields.length; col++) {
                Object expected = JsonPath.read(json, "$[" + row + "]." + jsonFields[col]);
                assertEquals(String.valueOf(expected), cells[col], "row " + row + " " + jsonFields[col]);
            }
            double drawdown = ((Number) JsonPath.read(json, "$[" + row + "].drawdown")).doubleValue();
            assertEquals(drawdown, Double.parseDouble(cells[10]), "row " + row + " drawdown");
            // Plain decimal, never scientific notation.
            assertFalse(cells[10].contains("E") || cells[10].contains("e"), cells[10]);
        }
    }

    @Test
    void equityCurveCsvIsDeterministicAcrossRequests() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);
        String path = "/api/backtest-runs/" + runId + "/equity-curve.csv";
        String filename = "backtest-" + runId + "-equity-curve.csv";

        assertEquals(getCsv(path, filename), getCsv(path, filename));
    }

    @Test
    void tradesCsvHasOneRowPerClosedTradeWithExactValues() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        String csv = getCsv("/api/backtest-runs/" + runId + "/trades.csv", "backtest-" + runId + "-trades.csv");

        assertEquals("status,quantity,entry_order_id,entry_date,entry_price,entry_commission,exit_order_id,"
                + "exit_date,exit_price,exit_commission,realized_pnl,total_commission,total_slippage_cost,"
                + "mark_date,mark_close,market_value,unrealized_pnl\r\n"
                + "CLOSED,95,1,2024-01-04,105,0,2,2024-01-08,96,0,-855,0,0,,,,\r\n", csv);
    }

    @Test
    void tradesCsvLeavesExitAndRealizedFieldsEmptyForAnOpenTrade() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, alwaysEnterStrategy(), twoBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "10000", "0", "0",
                                "2024-01-02", "2024-01-03")))
                .andReturn();
        long runId = idFromLocation(created);

        String csv = getCsv("/api/backtest-runs/" + runId + "/trades.csv", "backtest-" + runId + "-trades.csv");

        String[] lines = csv.split("\r\n");
        assertEquals(2, lines.length);
        String[] cells = lines[1].split(",", -1);
        assertEquals(17, cells.length);
        assertEquals("OPEN", cells[0]);
        assertEquals("96", cells[1]);
        assertEquals("104", cells[4]);
        for (int col = 6; col <= 10; col++) { // exit_* and realized_pnl are unavailable, not placeholders
            assertEquals("", cells[col], "column " + col);
        }
        assertEquals("2024-01-03", cells[13]);
        assertEquals("108", cells[14]);
        assertEquals("10368", cells[15]);
        assertEquals("384", cells[16]);
    }

    @Test
    void tradesCsvForARunWithNoTradesIsJustTheHeader() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, alwaysEnterStrategy(), twoBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "10", "100", "0",
                                "2024-01-02", "2024-01-03")))
                .andReturn();
        long runId = idFromLocation(created);

        String csv = getCsv("/api/backtest-runs/" + runId + "/trades.csv", "backtest-" + runId + "-trades.csv");

        assertEquals(BacktestCsv.TRADES_HEADER + "\r\n", csv);
        // The equity curve of the same run is still exported in full.
        String equityCsv = getCsv("/api/backtest-runs/" + runId + "/equity-curve.csv",
                "backtest-" + runId + "-equity-curve.csv");
        assertEquals(3, equityCsv.split("\r\n").length);
    }

    @Test
    void csvExportsOfAnotherUsersOrMissingRunAreNotFound() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        UserId other = TestUsers.create(jdbcTemplate, "brc-other-csv");
        when(currentUser.id()).thenReturn(other);

        mockMvc.perform(get("/api/backtest-runs/" + runId + "/equity-curve.csv")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/trades.csv")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/backtest-runs/999999999/equity-curve.csv")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/backtest-runs/999999999/trades.csv")).andExpect(status().isNotFound());
    }

    // --- F: trades -----------------------------------------------------------------

    @Test
    void closedTradeIsRepresentedWithEntryExitAndSignalIndicators() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        mockMvc.perform(get("/api/backtest-runs/" + runId + "/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CLOSED"))
                .andExpect(jsonPath("$[0].quantity").value(95))
                .andExpect(jsonPath("$[0].realizedPnl").value("-855"))
                .andExpect(jsonPath("$[0].totalCommission").value("0"))
                .andExpect(jsonPath("$[0].totalSlippageCost").value("0"))
                .andExpect(jsonPath("$[0].entry.orderId").value(1))
                .andExpect(jsonPath("$[0].entry.date").value("2024-01-04"))
                .andExpect(jsonPath("$[0].entry.fillPrice").value("105"))
                .andExpect(jsonPath("$[0].entry.signalType").value("ENTER"))
                .andExpect(jsonPath("$[0].entry.signalDate").value("2024-01-03"))
                .andExpect(jsonPath("$[0].entry.signalIndicators.length()").value(1))
                .andExpect(jsonPath("$[0].entry.signalIndicators[0].type").value("SMA"))
                .andExpect(jsonPath("$[0].entry.signalIndicators[0].period").value(1))
                .andExpect(jsonPath("$[0].entry.signalIndicators[0].value").value("105.0"))
                .andExpect(jsonPath("$[0].exit.orderId").value(2))
                .andExpect(jsonPath("$[0].exit.date").value("2024-01-08"))
                .andExpect(jsonPath("$[0].exit.fillPrice").value("96"))
                .andExpect(jsonPath("$[0].exit.signalType").value("EXIT"));
    }

    @Test
    void openTradeIsRepresentedWithFinalMarkAndNoExit() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, alwaysEnterStrategy(), twoBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "10000", "0", "0",
                                "2024-01-02", "2024-01-03")))
                .andReturn();
        long runId = idFromLocation(created);

        mockMvc.perform(get("/api/backtest-runs/" + runId + "/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].quantity").value(96))
                .andExpect(jsonPath("$[0].realizedPnl").doesNotExist())
                .andExpect(jsonPath("$[0].exit").doesNotExist())
                .andExpect(jsonPath("$[0].entry.fillPrice").value("104"))
                .andExpect(jsonPath("$[0].markDate").value("2024-01-03"))
                .andExpect(jsonPath("$[0].markClose").value("108"))
                .andExpect(jsonPath("$[0].marketValue").value("10368"))
                .andExpect(jsonPath("$[0].unrealizedPnl").value("384"));
    }

    // --- G: rejections ---------------------------------------------------------------

    @Test
    void zeroQuantityRejectionIsRepresentedWithNullOrderFields() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, alwaysEnterStrategy(), twoBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(createRunJson(refs.strategyId(), 1, refs.datasetId(), 1, "10", "100", "0",
                                "2024-01-02", "2024-01-03")))
                .andReturn();
        long runId = idFromLocation(created);

        mockMvc.perform(get("/api/backtest-runs/" + runId + "/rejections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].seq").value(1))
                .andExpect(jsonPath("$[0].reason").value("ZERO_QUANTITY"))
                .andExpect(jsonPath("$[0].orderId").doesNotExist())
                .andExpect(jsonPath("$[0].executionDate").doesNotExist())
                .andExpect(jsonPath("$[0].quantity").doesNotExist())
                .andExpect(jsonPath("$[0].requiredCash").doesNotExist())
                .andExpect(jsonPath("$[0].availableCash").doesNotExist())
                .andExpect(jsonPath("$[0].signalDate").value("2024-01-02"))
                .andExpect(jsonPath("$[0].signalClose").value("104"));
    }

    // --- H: integrity ------------------------------------------------------------

    private Map<String, Object> goldenRunValues(long strategyId, long datasetId) {
        String strategyHash = jdbcTemplate.queryForObject(
                "select definition_hash from strategy_version where strategy_id = ? and version_number = 1",
                String.class, strategyId);
        String datasetHash = jdbcTemplate.queryForObject(
                "select content_hash from dataset_version where dataset_id = ? and version_number = 1",
                String.class, datasetId);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("owner_id", owner.value());
        values.put("strategy_id", strategyId);
        values.put("strategy_version_number", 1);
        values.put("strategy_definition_hash", strategyHash);
        values.put("dataset_id", datasetId);
        values.put("dataset_version_number", 1);
        values.put("dataset_content_hash", datasetHash);
        values.put("engine_semantics_version", Backtester.SEMANTICS_VERSION);
        values.put("initial_capital", new BigDecimal("10000"));
        values.put("commission_per_fill", new BigDecimal("1"));
        values.put("slippage_rate", new BigDecimal("0.001"));
        values.put("start_date", LocalDate.of(2024, 1, 2));
        values.put("end_date", LocalDate.of(2024, 1, 3));
        values.put("first_evaluable_date", null);
        values.put("total_commission", BigDecimal.ZERO);
        values.put("total_slippage_cost", BigDecimal.ZERO);
        values.put("total_return", 0.0);
        values.put("cagr", null);
        values.put("volatility", null);
        values.put("sharpe_ratio", null);
        values.put("max_drawdown", 0.0);
        values.put("closed_trade_count", 0);
        values.put("win_rate", null);
        values.put("average_win", null);
        values.put("average_loss", null);
        values.put("benchmark_cash", new BigDecimal("100"));
        values.put("benchmark_quantity", 10L);
        values.put("benchmark_cost_basis", new BigDecimal("9900"));
        values.put("benchmark_total_return", 0.0);
        return values;
    }

    private long insertRun(Map<String, Object> values) {
        String columns = String.join(", ", values.keySet());
        String placeholders = values.keySet().stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = "insert into backtest_run (" + columns + ") values (" + placeholders + ") returning id";
        return jdbcTemplate.queryForObject(sql, Long.class, values.values().toArray());
    }

    private void insertEquityPoint(long runId, String date, String cash, long quantity, String costBasis,
                                    String realizedPnl, String close) {
        jdbcTemplate.update(
                "insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, "
                        + "close) values (?, ?, ?, ?, ?, ?, ?)",
                runId, LocalDate.parse(date), new BigDecimal(cash), quantity, new BigDecimal(costBasis),
                new BigDecimal(realizedPnl), new BigDecimal(close));
    }

    @Test
    void tamperedRunTotalCommissionIsInternalServerError() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), twoBarCsv());
        Map<String, Object> values = goldenRunValues(refs.strategyId(), refs.datasetId());
        values.put("total_commission", new BigDecimal("5")); // no fills exist, so 5 != sum(0)
        long runId = insertRun(values);
        insertEquityPoint(runId, "2024-01-02", "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, "2024-01-03", "10000", 0, "0", "0", "101");

        MvcResult result = mockMvc.perform(get("/api/backtest-runs/" + runId))
                .andExpect(status().isInternalServerError())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("Exception"));
        assertFalse(body.contains("in.vedchangani"));
    }

    @Test
    void tamperedEquityChildIsInternalServerErrorOnEveryReadEndpoint() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), twoBarCsv());
        long runId = insertRun(goldenRunValues(refs.strategyId(), refs.datasetId()));
        insertEquityPoint(runId, "2024-01-02", "10000", 0, "0", "0", "100");
        // quantity = 0 with a non-zero cost basis violates EquityPoint's own invariant.
        insertEquityPoint(runId, "2024-01-03", "9000", 0, "100", "0", "101");

        mockMvc.perform(get("/api/backtest-runs/" + runId)).andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/equity-curve"))
                .andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/trades"))
                .andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/rejections"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void tamperedBenchmarkCrossFieldMismatchIsInternalServerError() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), twoBarCsv());
        Map<String, Object> values = goldenRunValues(refs.strategyId(), refs.datasetId());
        values.put("benchmark_cash", new BigDecimal("50")); // 50 + 9900 != 10000
        long runId = insertRun(values);
        insertEquityPoint(runId, "2024-01-02", "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, "2024-01-03", "10000", 0, "0", "0", "101");

        mockMvc.perform(get("/api/backtest-runs/" + runId)).andExpect(status().isInternalServerError());
    }

    // --- I: no recomputation on read ------------------------------------------------

    @Test
    void readEndpointsNeverReinvokeTheEngine() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), sixBarCsv());
        MvcResult created = mockMvc.perform(post("/api/backtest-runs").contentType(MediaType.APPLICATION_JSON)
                        .content(tradingRunJson(refs)))
                .andReturn();
        long runId = idFromLocation(created);

        mockMvc.perform(get("/api/backtest-runs/" + runId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/equity-curve")).andExpect(status().isOk());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/trades")).andExpect(status().isOk());
        mockMvc.perform(get("/api/backtest-runs/" + runId + "/rejections")).andExpect(status().isOk());
        mockMvc.perform(get("/api/backtest-runs")).andExpect(status().isOk());

        // Exactly the one invocation from the POST that created the run - never again
        // for any subsequent read, no matter how many times or which endpoint.
        verify(backtester, times(1)).run(any(BarSeries.class), any(StrategyDefinition.class),
                any(BacktestConfig.class));
    }

    /**
     * Phase 9 Batch 2c revises D-34 Batch 2's never-recompute-on-read policy:
     * {@code getRun} now recomputes {@code PerformanceMetrics} and replays
     * the benchmark, comparing both exactly against the stored values. This
     * baseline is fully self-consistent under every OTHER check (ledger
     * replay, benchmark cash+costBasis identity) - a flat, no-trade, no
     * price-change two-point curve 517 days apart, so {@code totalReturn},
     * {@code cagr} (present and exactly {@code 0.0}, since the span exceeds
     * 365 days and the ratio is exactly 1), and the true benchmark return
     * ({@code (100 + 10*100 - 10000) / 10000 = -0.89} for the reference
     * state below) are all genuinely correct - ONLY the tampered field
     * differs from what recomputation would produce, isolating exactly what
     * this test means to prove.
     */
    @Test
    void tamperedMetricIsInternalServerError() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), twoBarCsv());
        Map<String, Object> values = goldenRunValues(refs.strategyId(), refs.datasetId());
        values.put("start_date", LocalDate.of(2020, 1, 1));
        values.put("end_date", LocalDate.of(2021, 6, 1));
        values.put("cagr", 0.123456); // true recomputed value is exactly 0.0
        values.put("benchmark_total_return", new BigDecimal("-0.89").doubleValue()); // true value - left correct
        long runId = insertRun(values);
        insertEquityPoint(runId, "2020-01-01", "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, "2021-06-01", "10000", 0, "0", "0", "100");

        mockMvc.perform(get("/api/backtest-runs/" + runId)).andExpect(status().isInternalServerError());
    }

    @Test
    void tamperedBenchmarkTotalReturnIsInternalServerError() throws Exception {
        OwnedRefs refs = createOwnedStrategyAndDataset(owner, tradingStrategy(), twoBarCsv());
        Map<String, Object> values = goldenRunValues(refs.strategyId(), refs.datasetId());
        values.put("start_date", LocalDate.of(2020, 1, 1));
        values.put("end_date", LocalDate.of(2021, 6, 1));
        values.put("cagr", 0.0); // true recomputed value - left correct
        values.put("benchmark_total_return", 0.777); // true value is exactly -0.89
        long runId = insertRun(values);
        insertEquityPoint(runId, "2020-01-01", "10000", 0, "0", "0", "100");
        insertEquityPoint(runId, "2021-06-01", "10000", 0, "0", "0", "100");

        mockMvc.perform(get("/api/backtest-runs/" + runId)).andExpect(status().isInternalServerError());
    }
}
