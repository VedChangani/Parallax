package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.marketdata.DailyBars;
import in.vedchangani.parallax.backend.marketdata.HistoryDepth;
import in.vedchangani.parallax.backend.marketdata.MarketDataProvider;
import in.vedchangani.parallax.backend.marketdata.MarketDataUnavailableException;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.data.Bar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatasetAlphaVantageImportIT {

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetVersionRepository versionRepository;

    @Autowired
    private DatasetBarRepository barRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId createUser() {
        return TestUsers.create(jdbcTemplate, "avimport");
    }

    private DatasetService serviceWith(MarketDataProvider provider) {
        return new DatasetService(datasetRepository, versionRepository, barRepository, transactionManager, provider);
    }

    private DatasetService plainCsvService() {
        return serviceWith((symbol, depth) -> {
            throw new AssertionError("provider must not be called for a CSV-only test");
        });
    }

    @Test
    void compactImportCreatesVersionWithExpectedMetadataAndBars() {
        UserId owner = createUser();
        FakeMarketDataProvider provider = FakeMarketDataProvider.returning(sampleDailyBars());
        DatasetService service = serviceWith(provider);
        DatasetSummary dataset = service.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");

        DatasetVersionSummary version = service.createVersionFromAlphaVantage(owner, dataset.id(),
                HistoryDepth.COMPACT);

        assertEquals(1, version.versionNumber());
        assertEquals(DatasetSource.ALPHA_VANTAGE, version.source());
        assertEquals("TIME_SERIES_DAILY;outputsize=compact", version.sourceDetail());
        assertEquals(AdjustmentBasis.RAW, version.adjustmentBasis());
        assertEquals("AAPL", version.symbol());
        assertEquals(2, version.barCount());
        assertEquals(LocalDate.of(2024, 1, 2), version.firstDate());
        assertEquals(LocalDate.of(2024, 1, 3), version.lastDate());
        assertEquals(1, provider.callCount());
        assertEquals("AAPL", provider.lastSymbol());
        assertEquals(HistoryDepth.COMPACT, provider.lastDepth());

        List<Bar> bars = barRepository.findOwned(
                versionRepository.findOwned(dataset.id(), owner.value(), 1).orElseThrow().id(), owner.value());
        assertEquals(2, bars.size());
        assertEquals(LocalDate.of(2024, 1, 2), bars.get(0).date());
        assertEquals(new BigDecimal("100"), bars.get(0).open());
        assertEquals(LocalDate.of(2024, 1, 3), bars.get(1).date());
    }

    @Test
    void fullImportReachesProviderWithFullDepthAndMatchingSourceDetail() {
        UserId owner = createUser();
        AtomicReference<HistoryDepth> requestedDepth = new AtomicReference<>();
        MarketDataProvider provider = (symbol, depth) -> {
            requestedDepth.set(depth);
            return sampleDailyBars(depth);
        };
        DatasetService service = serviceWith(provider);
        DatasetSummary dataset = service.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");

        DatasetVersionSummary version = service.createVersionFromAlphaVantage(owner, dataset.id(),
                HistoryDepth.FULL);

        assertEquals(HistoryDepth.FULL, requestedDepth.get());
        assertEquals("TIME_SERIES_DAILY;outputsize=full", version.sourceDetail());
    }

    @Test
    void importingIntoAnotherUsersDatasetFailsWithoutCallingTheProvider() {
        UserId owner = createUser();
        UserId other = createUser();
        DatasetService ownerService = plainCsvService();
        DatasetSummary dataset = ownerService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");

        FakeMarketDataProvider provider = FakeMarketDataProvider.returning(sampleDailyBars());
        DatasetService otherService = serviceWith(provider);

        assertThrows(DatasetNotFoundException.class,
                () -> otherService.createVersionFromAlphaVantage(other, dataset.id(), HistoryDepth.COMPACT));
        assertEquals(0, provider.callCount());
        assertEquals(0, ownerService.getDataset(owner, dataset.id()).latestVersionNumber());
    }

    @Test
    void providerFailureCreatesNoVersionAndNoBars() {
        UserId owner = createUser();
        FakeMarketDataProvider provider = FakeMarketDataProvider.throwing(
                new MarketDataUnavailableException("alpha vantage is temporarily unavailable"));
        DatasetService service = serviceWith(provider);
        DatasetSummary dataset = service.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");

        assertThrows(MarketDataUnavailableException.class,
                () -> service.createVersionFromAlphaVantage(owner, dataset.id(), HistoryDepth.COMPACT));

        assertEquals(0, service.getDataset(owner, dataset.id()).latestVersionNumber());
        assertTrue(service.listVersions(owner, dataset.id()).isEmpty());
        Long barCount = jdbcTemplate.queryForObject(
                "select count(*) from dataset_bar b join dataset_version v on v.id = b.dataset_version_id "
                        + "where v.dataset_id = ?", Long.class, dataset.id());
        assertEquals(0L, barCount);
    }

    @Test
    void failedImportDoesNotConsumeAVersionNumberAndSucceedingImportContinuesTheSequence() {
        UserId owner = createUser();
        DatasetService successService = serviceWith(FakeMarketDataProvider.returning(sampleDailyBars()));
        DatasetSummary dataset = successService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");

        DatasetVersionSummary first = successService.createVersionFromAlphaVantage(owner, dataset.id(),
                HistoryDepth.COMPACT);
        assertEquals(1, first.versionNumber());

        DatasetService failingService = serviceWith(FakeMarketDataProvider.throwing(
                new MarketDataUnavailableException("alpha vantage is temporarily unavailable")));
        assertThrows(MarketDataUnavailableException.class,
                () -> failingService.createVersionFromAlphaVantage(owner, dataset.id(), HistoryDepth.COMPACT));
        assertEquals(1, successService.getDataset(owner, dataset.id()).latestVersionNumber());

        DatasetVersionSummary second = successService.createVersionFromAlphaVantage(owner, dataset.id(),
                HistoryDepth.COMPACT);
        assertEquals(2, second.versionNumber());
    }

    @Test
    void alphaVantageAndCsvImportsOfTheSameLogicalBarsProduceTheSameContentHash() {
        UserId owner = createUser();
        DatasetService csvService = plainCsvService();
        DatasetSummary csvDataset = csvService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");
        DatasetVersionSummary csvVersion = csvService.createVersionFromCsv(owner, csvDataset.id(),
                DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW, "a.csv");

        DatasetService avService = serviceWith(FakeMarketDataProvider.returning(sampleDailyBars()));
        DatasetSummary avDataset = avService.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");
        DatasetVersionSummary avVersion = avService.createVersionFromAlphaVantage(owner, avDataset.id(),
                HistoryDepth.COMPACT);

        assertEquals(csvVersion.contentHash(), avVersion.contentHash());
    }

    @Test
    void alphaVantageCreatedVersionRoundTripsThroughGetVerifiedSeries() {
        UserId owner = createUser();
        DatasetService service = serviceWith(FakeMarketDataProvider.returning(sampleDailyBars()));
        DatasetSummary dataset = service.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");
        service.createVersionFromAlphaVantage(owner, dataset.id(), HistoryDepth.COMPACT);

        VerifiedDatasetVersion verified = service.getVerifiedSeries(owner, dataset.id(), 1);

        assertEquals(2, verified.series().bars().size());
        assertEquals("AAPL", verified.series().symbol());
        assertEquals(verified.summary().contentHash(), DatasetContent.of(verified.series()).contentHash());
    }

    @Test
    void providerIsCalledBeforeAnyPersistenceTransactionOpens() {
        UserId owner = createUser();
        AtomicBoolean transactionActiveDuringFetch = new AtomicBoolean(true);
        MarketDataProvider provider = (symbol, depth) -> {
            transactionActiveDuringFetch.set(TransactionSynchronizationManager.isActualTransactionActive());
            return sampleDailyBars(depth);
        };
        DatasetService service = serviceWith(provider);
        DatasetSummary dataset = service.createDataset(owner, DatasetFixtures.uniqueName(), "AAPL");

        service.createVersionFromAlphaVantage(owner, dataset.id(), HistoryDepth.COMPACT);

        assertFalse(transactionActiveDuringFetch.get());
    }

    private static DailyBars sampleDailyBars() {
        return sampleDailyBars(HistoryDepth.COMPACT);
    }

    private static DailyBars sampleDailyBars(HistoryDepth depth) {
        String sourceDetail = depth == HistoryDepth.COMPACT
                ? "TIME_SERIES_DAILY;outputsize=compact"
                : "TIME_SERIES_DAILY;outputsize=full";
        return new DailyBars(sampleBars(), sourceDetail);
    }

    private static List<Bar> sampleBars() {
        return List.of(
                new Bar(LocalDate.of(2024, 1, 2), new BigDecimal("100.0000"), new BigDecimal("105.0000"),
                        new BigDecimal("99.0000"), new BigDecimal("104.0000"), 1000),
                new Bar(LocalDate.of(2024, 1, 3), new BigDecimal("104.0000"), new BigDecimal("110.0000"),
                        new BigDecimal("103.0000"), new BigDecimal("108.0000"), 2000));
    }

    private static final class FakeMarketDataProvider implements MarketDataProvider {
        private final AtomicInteger callCount = new AtomicInteger();
        private final Supplier<DailyBars> behavior;
        private volatile String lastSymbol;
        private volatile HistoryDepth lastDepth;

        private FakeMarketDataProvider(Supplier<DailyBars> behavior) {
            this.behavior = behavior;
        }

        static FakeMarketDataProvider returning(DailyBars bars) {
            return new FakeMarketDataProvider(() -> bars);
        }

        static FakeMarketDataProvider throwing(RuntimeException exception) {
            return new FakeMarketDataProvider(() -> {
                throw exception;
            });
        }

        @Override
        public DailyBars fetchDailyBars(String symbol, HistoryDepth depth) {
            callCount.incrementAndGet();
            lastSymbol = symbol;
            lastDepth = depth;
            return behavior.get();
        }

        int callCount() {
            return callCount.get();
        }

        String lastSymbol() {
            return lastSymbol;
        }

        HistoryDepth lastDepth() {
            return lastDepth;
        }
    }
}
