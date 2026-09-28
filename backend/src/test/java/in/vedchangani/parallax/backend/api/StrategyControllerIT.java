package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.security.AuthenticatedMockMvcConfig;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.CurrentUser;
import in.vedchangani.parallax.backend.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-31 REST-boundary proof (MockMvc, real PostgreSQL via Testcontainers):
 * the full request/response contract, the D-30 strict-parsing rules
 * enforced through HTTP, ownership, and the error-mapping table (D-31
 * §11). {@link CurrentUser} is overridden with {@code @MockitoBean} so each
 * test controls exactly which owner is making the request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthenticatedMockMvcConfig.class})
class StrategyControllerIT {

    private static final String SIMPLE_DEFINITION =
            "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\","
                    + "\"right\":{\"type\":\"constant\",\"value\":\"0\"}},"
                    + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"LT\","
                    + "\"right\":{\"type\":\"constant\",\"value\":\"0\"}},"
                    + "\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";

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

    // --- happy path ----------------------------------------------------------

    @Test
    void fullCreateListGetPatchAndVersionLifecycle() throws Exception {
        String name = uniqueName();

        MvcResult created = mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(name, "original")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/strategies/")))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value("original"))
                .andExpect(jsonPath("$.latestVersionNumber").value(1))
                .andReturn();
        long id = idFromLocation(created);

        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        mockMvc.perform(get("/api/strategies/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));

        mockMvc.perform(patch("/api/strategies/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "-v2\",\"description\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name + "-v2"))
                .andExpect(jsonPath("$.description").value("updated"));

        MvcResult versionCreated = mockMvc.perform(post("/api/strategies/" + id + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMPLE_DEFINITION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/strategies/" + id + "/versions/2"))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.definition.entryCondition.type").value("compare"))
                .andReturn();

        mockMvc.perform(get("/api/strategies/" + id + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(1))
                .andExpect(jsonPath("$[1].versionNumber").value(2));

        mockMvc.perform(get("/api/strategies/" + id + "/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.definition.positionSizing.fraction").value("1"));

        // No response body ever mentions an exception/package/internal type name.
        assertFalse(versionCreated.getResponse().getContentAsString().contains("Exception"));
    }

    @Test
    void aNonAsciiNameSurvivesTheRoundTrip() throws Exception {
        String name = "ストラテジー-" + System.nanoTime();

        MvcResult created = mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(name, "")))
                .andExpect(status().isCreated())
                .andReturn();
        long id = idFromLocation(created);

        mockMvc.perform(get("/api/strategies/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    void gettingAVersionAndPostingItBackProducesTheSameHash() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "")))
                .andReturn();
        long id = idFromLocation(created);

        MvcResult firstGet = mockMvc.perform(get("/api/strategies/" + id + "/versions/1"))
                .andExpect(status().isOk())
                .andReturn();
        String body = firstGet.getResponse().getContentAsString();
        String definition = body.substring(body.indexOf("\"definition\":") + "\"definition\":".length(),
                body.length() - 1);

        mockMvc.perform(post("/api/strategies/" + id + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(definition))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.definitionHash").value(hashFromBody(body)));
    }

    private static String hashFromBody(String body) {
        String marker = "\"definitionHash\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }

    // --- 400: malformed --------------------------------------------------

    @Test
    void unknownDefinitionFieldIsBadRequest() throws Exception {
        String bad = SIMPLE_DEFINITION.replace("\"type\":\"cashFraction\"",
                "\"type\":\"cashFraction\",\"bogus\":1");
        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "", bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("definition.positionSizing.bogus"));
    }

    @Test
    void duplicateKeyInDefinitionIsBadRequest() throws Exception {
        String bad = "{\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\","
                + "\"right\":{\"type\":\"close\"}},\"entryCondition\":{\"type\":\"compare\",\"left\":"
                + "{\"type\":\"close\"},\"operator\":\"GT\",\"right\":{\"type\":\"close\"}},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\","
                + "\"right\":{\"type\":\"close\"}},\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        mockMvc.perform(post("/api/strategies/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void numericConstantIsBadRequest() throws Exception {
        String bad = SIMPLE_DEFINITION.replace("\"value\":\"0\"", "\"value\":0");
        mockMvc.perform(post("/api/strategies/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void numericCashFractionIsBadRequest() throws Exception {
        String bad = SIMPLE_DEFINITION.replace("\"fraction\":\"1\"", "\"fraction\":1");
        mockMvc.perform(post("/api/strategies/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void schemaVersionInsideAVersionsBodyIsBadRequest() throws Exception {
        String withSchemaVersion = "{\"schemaVersion\":1," + SIMPLE_DEFINITION.substring(1);
        mockMvc.perform(post("/api/strategies/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withSchemaVersion))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trailingTokensAreBadRequest() throws Exception {
        mockMvc.perform(post("/api/strategies/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMPLE_DEFINITION + "   {}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEnvelopeFieldOnCreateIsBadRequest() throws Exception {
        String json = "{\"name\":\"" + uniqueName() + "\",\"description\":\"\",\"definition\":"
                + SIMPLE_DEFINITION + ",\"ownerId\":1}";
        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("ownerId"));
    }

    @Test
    void blankNameOnCreateIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson("   ", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void missingBodyOnCreateIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/strategies").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // --- 422: semantically invalid -----------------------------------------

    @Test
    void invalidIndicatorPeriodIsUnprocessable() throws Exception {
        String bad = SIMPLE_DEFINITION.replace(
                "\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"}",
                "\"entryCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"indicator\","
                        + "\"indicator\":\"SMA\",\"period\":0}");
        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "", bad)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("definition.entryCondition.left"));
    }

    @Test
    void outOfRangeCashFractionIsUnprocessable() throws Exception {
        String bad = SIMPLE_DEFINITION.replace("\"fraction\":\"1\"", "\"fraction\":\"1.5\"");
        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "", bad)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("definition.positionSizing"));
    }

    // --- defensive numeric bounds (Phase 9 Batch 2b, D-36) --------------------

    @Test
    void over100CharCashFractionLiteralIsBadRequest() throws Exception {
        String text = "0.5" + "0".repeat(98); // 101 characters
        String bad = SIMPLE_DEFINITION.replace("\"fraction\":\"1\"", "\"fraction\":\"" + text + "\"");

        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "", bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("definition.positionSizing"));
    }

    @Test
    void over18FractionalDigitCashFractionIsUnprocessable() throws Exception {
        String text = "0." + "9".repeat(19); // still < 1 - isolates precision from the range check
        String bad = SIMPLE_DEFINITION.replace("\"fraction\":\"1\"", "\"fraction\":\"" + text + "\"");

        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "", bad)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("definition.positionSizing"));
    }

    @Test
    void emptyAllGroupIsUnprocessable() throws Exception {
        String bad = "{\"entryCondition\":{\"type\":\"all\",\"conditions\":[]},"
                + "\"exitCondition\":{\"type\":\"compare\",\"left\":{\"type\":\"close\"},\"operator\":\"GT\","
                + "\"right\":{\"type\":\"close\"}},\"positionSizing\":{\"type\":\"cashFraction\",\"fraction\":\"1\"}}";
        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "", bad)))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- 409: conflicts ------------------------------------------------------

    @Test
    void duplicateStrategyNameOnCreateIsConflict() throws Exception {
        String name = uniqueName();
        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(name, "")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(name, "")))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateStrategyNameOnPatchIsConflict() throws Exception {
        String nameA = uniqueName();
        String nameB = uniqueName();
        mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(nameA, "")))
                .andExpect(status().isCreated());
        MvcResult createdB = mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(nameB, "")))
                .andReturn();
        long idB = idFromLocation(createdB);

        mockMvc.perform(patch("/api/strategies/" + idB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + nameA + "\",\"description\":\"\"}"))
                .andExpect(status().isConflict());
    }

    // --- 404: missing / cross-owner ----------------------------------------

    @Test
    void missingStrategyIsNotFound() throws Exception {
        mockMvc.perform(get("/api/strategies/99999999")).andExpect(status().isNotFound());
    }

    @Test
    void missingVersionIsNotFound() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "")))
                .andReturn();
        long id = idFromLocation(created);

        mockMvc.perform(get("/api/strategies/" + id + "/versions/99")).andExpect(status().isNotFound());
    }

    @Test
    void anotherOwnersStrategyIsNotFoundForEveryOperation() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "")))
                .andReturn();
        long id = idFromLocation(created);

        UserId other = TestUsers.create(jdbcTemplate, "ctrl-b");
        when(currentUser.id()).thenReturn(other);

        mockMvc.perform(get("/api/strategies/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/strategies/" + id + "/versions/1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/strategies/" + id + "/versions")).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/strategies/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"description\":\"\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/strategies/" + id + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMPLE_DEFINITION))
                .andExpect(status().isNotFound());
    }

    // --- 500: integrity failure ----------------------------------------------

    @Test
    void aStoredVersionWithAWrongHashIsAGenericInternalError() throws Exception {
        // strategy_version is immutable (no UPDATE reaches it, D-31 §12), so the failure
        // is simulated with a fresh out-of-band row carrying a deliberately wrong hash —
        // not by mutating the version the create call produced.
        MvcResult created = mockMvc.perform(post("/api/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(uniqueName(), "")))
                .andReturn();
        long id = idFromLocation(created);

        String storedDocument = "{\"schemaVersion\":1," + SIMPLE_DEFINITION.substring(1);
        jdbcTemplate.update(
                "insert into strategy_version (strategy_id, version_number, definition, "
                        + "definition_schema_version, definition_hash) values (?, 2, ?::jsonb, 1, ?)",
                id, storedDocument, "f".repeat(64));

        MvcResult result = mockMvc.perform(get("/api/strategies/" + id + "/versions/2"))
                .andExpect(status().isInternalServerError())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("Exception"));
        assertFalse(body.contains("in.vedchangani"));
    }

    // --- helpers -----------------------------------------------------------

    private static String createRequestJson(String name, String description) {
        return createRequestJson(name, description, SIMPLE_DEFINITION);
    }

    private static String createRequestJson(String name, String description, String definitionJson) {
        return "{\"name\":\"" + name + "\",\"description\":\"" + description + "\",\"definition\":"
                + definitionJson + "}";
    }

    private static long idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        String idPart = location.substring(location.lastIndexOf('/') + 1);
        return Long.parseLong(idPart);
    }

    private static String uniqueName() {
        return "ctrl-it-" + System.nanoTime();
    }
}
