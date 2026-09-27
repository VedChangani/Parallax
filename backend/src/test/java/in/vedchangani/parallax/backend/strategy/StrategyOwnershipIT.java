package in.vedchangani.parallax.backend.strategy;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-31 ownership proof (D-31 §10), against real PostgreSQL: every strategy
 * operation is owner-scoped, using two independent {@code app_user} rows
 * created via {@link TestUsers} — never {@code @MockitoBean CurrentUser}
 * here, since {@link StrategyService} takes an explicit {@link UserId} per
 * call rather than reading a request-scoped current user.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StrategyOwnershipIT {

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerCanAccessOwnStrategyAndVersion() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        StrategySummary strategy = strategyService.createStrategy(a, name(), "", StrategyFixtures.simple());

        assertEquals(strategy.id(), strategyService.getStrategy(a, strategy.id()).id());
        assertEquals(StrategyFixtures.simple(), strategyService.getVersion(a, strategy.id(), 1).definition());
    }

    @Test
    void anotherOwnerCannotGetTheStrategy() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        StrategySummary strategy = strategyService.createStrategy(a, name(), "", StrategyFixtures.simple());

        assertThrows(StrategyNotFoundException.class, () -> strategyService.getStrategy(b, strategy.id()));
    }

    @Test
    void anotherOwnerCannotGetTheVersion() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        StrategySummary strategy = strategyService.createStrategy(a, name(), "", StrategyFixtures.simple());

        assertThrows(StrategyVersionNotFoundException.class,
                () -> strategyService.getVersion(b, strategy.id(), 1));
    }

    @Test
    void anotherOwnerCannotListVersions() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        StrategySummary strategy = strategyService.createStrategy(a, name(), "", StrategyFixtures.simple());

        assertThrows(StrategyNotFoundException.class, () -> strategyService.listVersions(b, strategy.id()));
    }

    @Test
    void anotherOwnerCannotUpdateMetadata() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        StrategySummary strategy = strategyService.createStrategy(a, name(), "original", StrategyFixtures.simple());

        assertThrows(StrategyNotFoundException.class,
                () -> strategyService.updateStrategyMetadata(b, strategy.id(), "hijacked", "hijacked"));

        StrategySummary unchanged = strategyService.getStrategy(a, strategy.id());
        assertEquals("original", unchanged.description());
    }

    @Test
    void anotherOwnerCannotCreateAVersion() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        StrategySummary strategy = strategyService.createStrategy(a, name(), "", StrategyFixtures.simple());

        assertThrows(StrategyNotFoundException.class,
                () -> strategyService.createVersion(b, strategy.id(), StrategyFixtures.alternative()));

        // No version was consumed by the rejected attempt.
        StrategySummary unchanged = strategyService.getStrategy(a, strategy.id());
        assertEquals(1, unchanged.latestVersionNumber());
    }

    @Test
    void listsExcludeAnotherOwnersStrategies() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        StrategySummary strategyA = strategyService.createStrategy(a, name(), "", StrategyFixtures.simple());
        strategyService.createStrategy(b, name(), "", StrategyFixtures.simple());

        List<StrategySummary> aList = strategyService.listStrategies(a);
        assertEquals(1, aList.size());
        assertEquals(strategyA.id(), aList.get(0).id());
    }

    @Test
    void theSameStrategyNameIsAllowedForDifferentOwners() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        String sharedName = name();

        StrategySummary strategyA = strategyService.createStrategy(a, sharedName, "", StrategyFixtures.simple());
        StrategySummary strategyB = strategyService.createStrategy(b, sharedName, "", StrategyFixtures.simple());

        assertTrue(strategyA.id() != strategyB.id());
        assertEquals(sharedName, strategyService.getStrategy(a, strategyA.id()).name());
        assertEquals(sharedName, strategyService.getStrategy(b, strategyB.id()).name());
    }

    private static String name() {
        return "own-it-" + System.nanoTime();
    }
}
