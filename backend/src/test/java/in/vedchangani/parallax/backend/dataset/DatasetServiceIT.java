package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.dataset.csv.MalformedCsvException;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.data.Bar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatasetServiceIT {

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId createUser() {
        return TestUsers.create(jdbcTemplate, "dsvc");
    }

    @Test
    void createDatasetStartsAtLatestVersionZeroWithNoVersions() {
        UserId owner = createUser();
        DatasetSummary dataset = datasetService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");

        assertEquals(0, dataset.latestVersionNumber());
        assertEquals("AAPL", dataset.symbol());
        assertTrue(datasetService.listVersions(owner, dataset.id()).isEmpty());
    }

    @Test
    void firstVersionIsNumberOneAndSecondIsNumberTwo() {
        UserId owner = createUser();
        DatasetSummary dataset = datasetService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");

        DatasetVersionSummary v1 = datasetService.createVersionFromCsv(owner, dataset.id(),
                DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW, "a.csv");
        assertEquals(1, v1.versionNumber());
        assertEquals(1, datasetService.getDataset(owner, dataset.id()).latestVersionNumber());

        DatasetVersionSummary v2 = datasetService.createVersionFromCsv(owner, dataset.id(),
                DatasetFixtures.alternativeCsv(), AdjustmentBasis.RAW, "b.csv");
        assertEquals(2, v2.versionNumber());
        assertEquals(2, datasetService.getDataset(owner, dataset.id()).latestVersionNumber());
    }

    @Test
    void versionHistoryRemainsOneToNWithNoGaps() {
        UserId owner = createUser();
        DatasetSummary dataset = datasetService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");
        datasetService.createVersionFromCsv(owner, dataset.id(), DatasetFixtures.alternativeCsv(),
                AdjustmentBasis.RAW, "b.csv");
        datasetService.createVersionFromCsv(owner, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "c.csv");

        List<DatasetVersionSummary> versions = datasetService.listVersions(owner, dataset.id());
        assertEquals(List.of(1, 2, 3), versions.stream().map(DatasetVersionSummary::versionNumber).toList());
    }

    @Test
    void concurrentVersionCreationGivesExactlyTwoThroughNineWithNoGapsOrDuplicates() throws Exception {
        UserId owner = createUser();
        DatasetSummary dataset = datasetService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "seed.csv");

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Callable<Integer>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Integer>>mapToObj(i -> () -> {
                        ready.countDown();
                        go.await();
                        return datasetService.createVersionFromCsv(owner, dataset.id(),
                                DatasetFixtures.alternativeCsv(), AdjustmentBasis.RAW, "concurrent-" + i + ".csv")
                                .versionNumber();
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
            assertEquals(9, datasetService.getDataset(owner, dataset.id()).latestVersionNumber());
            assertEquals(9, datasetService.listVersions(owner, dataset.id()).size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void outOfBandVersionNumberConflictRollsBackTheWholeAttempt() {
        UserId owner = createUser();
        DatasetSummary dataset = datasetService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");

        jdbcTemplate.update("""
                insert into dataset_version
                    (dataset_id, version_number, symbol, source, source_detail, adjustment_basis,
                     bar_count, first_date, last_date, content_hash)
                values (?, 2, 'AAPL', 'CSV_UPLOAD', 'manual.csv', 'RAW', 1, '2024-01-02', '2024-01-02', ?)
                """, dataset.id(), "0".repeat(64));

        assertThrows(DatasetVersionConflictException.class, () -> datasetService.createVersionFromCsv(owner,
                dataset.id(), DatasetFixtures.alternativeCsv(), AdjustmentBasis.RAW, "b.csv"));

        assertEquals(1, datasetService.getDataset(owner, dataset.id()).latestVersionNumber());
        Integer versionRowCount = jdbcTemplate.queryForObject(
                "select count(*) from dataset_version where dataset_id = ?", Integer.class, dataset.id());
        assertEquals(2, versionRowCount);
        Long orphanBarCount = jdbcTemplate.queryForObject(
                "select count(*) from dataset_bar b join dataset_version v on v.id = b.dataset_version_id "
                        + "where v.dataset_id = ? and v.version_number = 2", Long.class, dataset.id());
        assertEquals(0L, orphanBarCount);
    }

    @Test
    void aFailedCsvParseWritesNothingAndLeavesLatestUnchanged() {
        UserId owner = createUser();
        DatasetSummary dataset = datasetService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");
        byte[] badCsv = "not,a,valid,csv,header,at,all\n".getBytes(StandardCharsets.US_ASCII);

        assertThrows(MalformedCsvException.class, () -> datasetService.createVersionFromCsv(owner, dataset.id(),
                badCsv, AdjustmentBasis.RAW, "bad.csv"));

        assertEquals(0, datasetService.getDataset(owner, dataset.id()).latestVersionNumber());
        assertTrue(datasetService.listVersions(owner, dataset.id()).isEmpty());
    }

    @Test
    void duplicateDatasetNameForOneOwnerIsRejected() {
        UserId owner = createUser();
        String name = DatasetFixtures.uniqueName();
        datasetService.createDataset(owner, name, "AAPL");

        assertThrows(DuplicateDatasetNameException.class, () -> datasetService.createDataset(owner, name, "MSFT"));
    }

    @Test
    void concurrentDuplicateDatasetNameCreationGivesExactlyOneSuccess() throws Exception {
        UserId owner = createUser();
        String sharedName = DatasetFixtures.uniqueName();

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
                            datasetService.createDataset(owner, sharedName, "AAPL");
                            successes.incrementAndGet();
                        } catch (DuplicateDatasetNameException e) {
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
    void sameDatasetNameUnderDifferentOwnersIsAllowed() {
        UserId a = createUser();
        UserId b = createUser();
        String sharedName = DatasetFixtures.uniqueName();

        datasetService.createDataset(a, sharedName, "AAPL");
        DatasetSummary bDataset = datasetService.createDataset(b, sharedName, "AAPL");

        assertEquals(sharedName, bDataset.name());
    }

    @Test
    void sameSymbolAcrossMultipleDatasetsForOneOwnerIsAllowed() {
        UserId owner = createUser();
        DatasetSummary first = datasetService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");
        DatasetSummary second = datasetService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");

        assertEquals("AAPL", first.symbol());
        assertEquals("AAPL", second.symbol());
    }

    @Test
    void getVerifiedSeriesRoundTripsExactlyAndRetainsEveryUploadedBar() {
        UserId owner = createUser();
        DatasetSummary dataset = datasetService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");
        datasetService.createVersionFromCsv(owner, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");

        VerifiedDatasetVersion verified = datasetService.getVerifiedSeries(owner, dataset.id(), 1);
        List<Bar> bars = verified.series().bars();

        assertEquals(2, bars.size());
        assertEquals(LocalDate.of(2024, 1, 2), bars.get(0).date());
        assertEquals(new BigDecimal("100"), bars.get(0).open());
        assertEquals(LocalDate.of(2024, 1, 3), bars.get(1).date());
        assertEquals("AAPL", verified.series().symbol());
        assertEquals(verified.summary().contentHash(), DatasetContent.of(verified.series()).contentHash());
    }
}
