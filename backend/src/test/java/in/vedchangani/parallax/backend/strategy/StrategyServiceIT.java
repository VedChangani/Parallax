package in.vedchangani.parallax.backend.strategy;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionCodec;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-31 {@link StrategyService} behavior against real PostgreSQL
 * (Testcontainers): version allocation, its concurrency guarantees, and
 * metadata/version-immutability interplay. No test relies on transaction
 * rollback for isolation — every scenario commits real rows, so
 * concurrency scenarios observe genuinely committed state.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StrategyServiceIT {

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private StrategyVersionRepository versionRepository;

    @Autowired
    private StrategyDefinitionCodec codec;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createStrategyGivesVersionOneAndLatestOne() {
        UserId owner = user();
        StrategySummary summary = strategyService.createStrategy(owner, name(), "", StrategyFixtures.simple());

        assertEquals(1, summary.latestVersionNumber());
        List<StrategyVersionSummary> versions = strategyService.listVersions(owner, summary.id());
        assertEquals(1, versions.size());
        assertEquals(1, versions.get(0).versionNumber());
        assertEquals(codec.encode(StrategyFixtures.simple()).sha256(), versions.get(0).definitionHash());
    }

    @Test
    void createVersionGivesVersionTwoAndLatestTwo() {
        UserId owner = user();
        StrategySummary strategy = strategyService.createStrategy(owner, name(), "", StrategyFixtures.simple());

        StrategyVersionDetail v2 = strategyService.createVersion(owner, strategy.id(), StrategyFixtures.alternative());

        assertEquals(2, v2.summary().versionNumber());
        StrategySummary reloaded = strategyService.getStrategy(owner, strategy.id());
        assertEquals(2, reloaded.latestVersionNumber());
    }

    @Test
    void versionHistoryRemainsOneToNWithNoGaps() {
        UserId owner = user();
        StrategySummary strategy = strategyService.createStrategy(owner, name(), "", StrategyFixtures.simple());
        strategyService.createVersion(owner, strategy.id(), StrategyFixtures.alternative());
        strategyService.createVersion(owner, strategy.id(), StrategyFixtures.simple());

        List<StrategyVersionSummary> versions = strategyService.listVersions(owner, strategy.id());
        assertEquals(List.of(1, 2, 3), versions.stream().map(StrategyVersionSummary::versionNumber).toList());
    }

    @Test
    void versionRoundTripsExactly() {
        UserId owner = user();
        StrategyDefinition definition = StrategyFixtures.alternative();
        StrategySummary strategy = strategyService.createStrategy(owner, name(), "d", definition);

        StrategyVersionDetail detail = strategyService.getVersion(owner, strategy.id(), 1);

        assertEquals(definition, detail.definition());
    }

    @Test
    void equivalentDefinitionsProduceTheSameStoredHash() {
        UserId owner = user();
        StrategyDefinition a = new StrategyDefinition(StrategyFixtures.simple().entryCondition(),
                StrategyFixtures.simple().exitCondition(), new PositionSizing.CashFraction(new BigDecimal("0.50")));
        StrategyDefinition b = new StrategyDefinition(StrategyFixtures.simple().entryCondition(),
                StrategyFixtures.simple().exitCondition(), new PositionSizing.CashFraction(new BigDecimal("0.5")));

        StrategySummary strategy = strategyService.createStrategy(owner, name(), "", a);
        StrategyVersionDetail v2 = strategyService.createVersion(owner, strategy.id(), b);

        StrategyVersionDetail v1 = strategyService.getVersion(owner, strategy.id(), 1);
        assertEquals(v1.summary().definitionHash(), v2.summary().definitionHash());
    }

    @Test
    void concurrentVersionCreationGivesExactlyTwoThroughNineWithNoGapsOrDuplicates() throws Exception {
        UserId owner = user();
        StrategySummary strategy = strategyService.createStrategy(owner, name(), "", StrategyFixtures.simple());

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Callable<Integer>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Integer>>mapToObj(i -> () -> {
                        ready.countDown();
                        go.await();
                        return strategyService.createVersion(owner, strategy.id(), StrategyFixtures.alternative())
                                .summary().versionNumber();
                    })
                    .toList();

            List<Future<Integer>> futures = tasks.stream().map(pool::submit).toList();
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            List<Integer> versionNumbers = futures.stream().map(f -> {
                try {
                    return f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).sorted().toList();

            assertEquals(List.of(2, 3, 4, 5, 6, 7, 8, 9), versionNumbers);
            assertEquals(9, strategyService.getStrategy(owner, strategy.id()).latestVersionNumber());
            assertEquals(9, strategyService.listVersions(owner, strategy.id()).size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicateStrategyNameCreationGivesExactlyOneSuccess() throws Exception {
        UserId owner = user();
        String sharedName = name();

        int threadCount = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        try {
            List<Callable<Void>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        ready.countDown();
                        go.await();
                        try {
                            strategyService.createStrategy(owner, sharedName, "", StrategyFixtures.simple());
                            successes.incrementAndGet();
                        } catch (DuplicateStrategyNameException e) {
                            conflicts.incrementAndGet();
                        }
                        return null;
                    })
                    .toList();

            List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            for (Future<Void> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }

            assertEquals(1, successes.get());
            assertEquals(threadCount - 1, conflicts.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentPatchAndCreateVersionKeepLatestEqualToMaxVersionNumber() throws Exception {
        UserId owner = user();
        StrategySummary strategy = strategyService.createStrategy(owner, name(), "", StrategyFixtures.simple());

        int patchThreads = 3;
        int versionThreads = 3;
        int totalThreads = patchThreads + versionThreads;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Callable<Void>> tasks = IntStream.range(0, totalThreads)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        ready.countDown();
                        go.await();
                        if (i < patchThreads) {
                            strategyService.updateStrategyMetadata(owner, strategy.id(),
                                    name() + "-patched-" + i, "updated");
                        } else {
                            strategyService.createVersion(owner, strategy.id(), StrategyFixtures.alternative());
                        }
                        return null;
                    })
                    .toList();

            List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            for (Future<Void> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }

            int latest = strategyService.getStrategy(owner, strategy.id()).latestVersionNumber();
            Integer maxVersionNumber = jdbcTemplate.queryForObject(
                    "select max(version_number) from strategy_version where strategy_id = ?",
                    Integer.class, strategy.id());

            assertEquals(maxVersionNumber, latest);
            assertEquals(1 + versionThreads, latest);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aForcedVersionConflictRollsTheParentCounterBack() {
        UserId owner = user();
        StrategySummary strategy = strategyService.createStrategy(owner, name(), "", StrategyFixtures.simple());
        assertEquals(1, strategy.latestVersionNumber());

        // Out-of-band: insert version 2 directly, bypassing the service, so the parent
        // row's latest_version_number stays at 1 while version_number 2 already exists.
        var canonical = codec.encode(StrategyFixtures.alternative());
        jdbcTemplate.update(
                "insert into strategy_version (strategy_id, version_number, definition, "
                        + "definition_schema_version, definition_hash) values (?, 2, ?::jsonb, ?, ?)",
                strategy.id(), canonical.json(), canonical.schemaVersion(), canonical.sha256());

        assertThrows(StrategyVersionConflictException.class,
                () -> strategyService.createVersion(owner, strategy.id(), StrategyFixtures.simple()));

        // The failed transaction rolled back in full: latest_version_number is unchanged.
        StrategySummary reloaded = strategyService.getStrategy(owner, strategy.id());
        assertEquals(1, reloaded.latestVersionNumber());
    }

    @Test
    void metadataUpdateLeavesTheVersionRowByteForByteUnchanged() {
        UserId owner = user();
        StrategySummary strategy = strategyService.createStrategy(owner, name(), "original", StrategyFixtures.simple());
        StrategyVersion before = versionRepository.findOwned(strategy.id(), owner.value(), 1).orElseThrow();

        strategyService.updateStrategyMetadata(owner, strategy.id(), name() + "-renamed", "changed description");

        StrategyVersion after = versionRepository.findOwned(strategy.id(), owner.value(), 1).orElseThrow();
        assertEquals(before.definitionJson(), after.definitionJson());
        assertEquals(before.definitionHash(), after.definitionHash());
        assertEquals(before.definitionSchemaVersion(), after.definitionSchemaVersion());
        assertEquals(before.createdAt(), after.createdAt());

        StrategySummary updated = strategyService.getStrategy(owner, strategy.id());
        assertTrue(updated.name().endsWith("-renamed"));
        assertEquals("changed description", updated.description());
    }

    // --- fixtures ----------------------------------------------------------

    private static final AtomicLong NAME_COUNTER = new AtomicLong();

    private UserId user() {
        return TestUsers.create(jdbcTemplate, "svc");
    }

    private static String name() {
        return "svc-it-" + Instant.now().toEpochMilli() + "-" + NAME_COUNTER.incrementAndGet();
    }
}
