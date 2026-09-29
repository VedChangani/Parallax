package in.vedchangani.parallax.backend.strategy;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.strategy.definition.CanonicalStrategyDefinition;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionCodec;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StrategySchemaIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private StrategyDefinitionCodec codec;

    @Autowired
    private StrategyVersionRepository versionRepository;

    @Test
    void updatingAStrategyVersionRowIsRejectedByTheDatabase() {
        long strategyId = createStrategyAndReturnId();
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update strategy_version set version_number = 999 where strategy_id = ?", strategyId));
    }

    @Test
    void deletingAStrategyVersionRowIsRejectedByTheDatabase() {
        long strategyId = createStrategyAndReturnId();
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "delete from strategy_version where strategy_id = ?", strategyId));
    }

    @Test
    void truncatingStrategyVersionIsRejectedByTheDatabase() {
        createStrategyAndReturnId();
        assertThrows(DataAccessException.class, () -> jdbcTemplate.execute("truncate table strategy_version"));
    }

    @Test
    void hashFormatCheckRejectsANonHexOrWrongLengthHash() {
        long strategyId = createStrategyAndReturnId();
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into strategy_version (strategy_id, version_number, definition, "
                        + "definition_schema_version, definition_hash) values (?, 999, "
                        + "'{\"schemaVersion\":1}'::jsonb, 1, ?)",
                strategyId, "not-a-valid-hash"));
    }

    @Test
    void schemaVersionCheckRejectsAColumnDocumentDisagreement() {
        long strategyId = createStrategyAndReturnId();
        String sha256Zeros = "0".repeat(64);
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into strategy_version (strategy_id, version_number, definition, "
                        + "definition_schema_version, definition_hash) values (?, 999, "
                        + "'{\"schemaVersion\":1}'::jsonb, 2, ?)",
                strategyId, sha256Zeros));
    }

    @Test
    void postgresJsonbReformattingStillVerifiesThroughD30Decode() {
        UserId owner = TestUsers.create(jdbcTemplate, "schema");
        StrategyDefinition definition = StrategyFixtures.simple();
        CanonicalStrategyDefinition canonical = codec.encode(definition);
        StrategySummary strategy = strategyService.createStrategy(owner, uniqueName(), "", definition);

        String reformatted = "{\n  \"positionSizing\": " + extractField(canonical.json(), "positionSizing")
                + ",\n  \"exitCondition\": " + extractField(canonical.json(), "exitCondition")
                + ",\n  \"entryCondition\": " + extractField(canonical.json(), "entryCondition")
                + ",\n  \"schemaVersion\": 1\n}";

        jdbcTemplate.update(
                "insert into strategy_version (strategy_id, version_number, definition, "
                        + "definition_schema_version, definition_hash) values (?, 999, ?::jsonb, ?, ?)",
                strategy.id(), reformatted, canonical.schemaVersion(), canonical.sha256());

        StrategyVersion stored = versionRepository.findOwned(strategy.id(), owner.value(), 999).orElseThrow();

        StrategyDefinition decoded = codec.decode(stored.definitionSchemaVersion(), stored.definitionJson(),
                stored.definitionHash());
        assertEquals(definition, decoded);
    }

    private long createStrategyAndReturnId() {
        UserId owner = TestUsers.create(jdbcTemplate, "schema");
        StrategySummary summary = strategyService.createStrategy(owner, uniqueName(), "", StrategyFixtures.simple());
        return summary.id();
    }

    private static String uniqueName() {
        return "schema-it-" + System.nanoTime();
    }

    private static String extractField(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker) + marker.length();
        int depth = 0;
        int i = start;
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    i++;
                    break;
                }
            }
        }
        return json.substring(start, i);
    }
}
